package tw.nekomimi.nekogram.autopost;

import android.content.SharedPreferences;
import android.util.Base64;
import org.json.JSONObject;
import org.telegram.tgnet.SerializedData;
import org.json.JSONArray;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Manual, in-process sending only. Never resumes after process death. */
public final class AutoPost {
    private static final HashMap<Integer, Session> sessions = new HashMap<>();
    private AutoPost() {}
    public static SharedPreferences prefs(int account, boolean global) {
        return ApplicationLoader.applicationContext.getSharedPreferences("auto_post_" + account + "_" + (global ? "global" : "selected"), 0);
    }
    /** A legacy string is still accepted, so existing message lists migrate on save. */
    public static final class PostMessage {
        public final String text;
        public final ArrayList<TLRPC.MessageEntity> entities;
        public PostMessage(String text, ArrayList<TLRPC.MessageEntity> entities) {
            this.text=text;
            this.entities=entities == null ? new ArrayList<>() : entities;
        }
    }
    private static String encodeEntities(List<TLRPC.MessageEntity> entities) {
        SerializedData data=new SerializedData();
        data.writeInt32(entities.size());
        for (TLRPC.MessageEntity entity:entities) entity.serializeToStream(data);
        String result=Base64.encodeToString(data.toByteArray(),Base64.NO_WRAP);
        data.cleanup();
        return result;
    }
    private static ArrayList<TLRPC.MessageEntity> decodeEntities(String encoded, String text) {
        ArrayList<TLRPC.MessageEntity> result=new ArrayList<>();
        if (encoded==null || encoded.isEmpty()) return result;
        SerializedData data=null;
        try {
            data=new SerializedData(Base64.decode(encoded,Base64.DEFAULT));
            int count=data.readInt32(true);
            if (count<0 || count>10000) throw new IllegalArgumentException("Invalid entities");
            for(int i=0;i<count;i++) {
                TLRPC.MessageEntity entity=TLRPC.MessageEntity.TLdeserialize(data,data.readInt32(true),true);
                if (entity==null || entity.offset<0 || entity.length<=0 || (long)entity.offset+entity.length>text.length()) throw new IllegalArgumentException("Invalid entity range");
                result.add(entity);
            }
            if (data.length()!=0) throw new IllegalArgumentException("Trailing data");
        } catch(Exception error) {
            // Never send a silently corrupted or partial set of entities.
            throw new IllegalStateException("Invalid saved formatting",error);
        } finally { if(data!=null)data.cleanup(); }
        return result;
    }
    public static ArrayList<PostMessage> messages(int account, boolean global) {
        ArrayList<PostMessage> out=new ArrayList<>();
        try {
            JSONArray a=new JSONArray(prefs(account,global).getString("messages","[]"));
            for(int i=0;i<a.length() && i<10;i++) {
                Object item=a.get(i);
                String text=item instanceof JSONObject ? ((JSONObject)item).optString("text","") : item instanceof String ? (String)item : "";
                if(text.trim().isEmpty()) continue;
                String encoded=item instanceof JSONObject ? ((JSONObject)item).optString("entities","") : "";
                out.add(new PostMessage(text,decodeEntities(encoded,text)));
            }
        } catch(org.json.JSONException error) { throw new IllegalStateException("Invalid saved messages",error); }
        return out;
    }
    public static void saveMessages(int account, boolean global, List<PostMessage> items) {
        JSONArray a=new JSONArray();
        try {
            for(PostMessage item:items) {
                JSONObject o=new JSONObject(); o.put("text",item.text);o.put("entities",encodeEntities(item.entities));a.put(o);
            }
        } catch(org.json.JSONException error) { throw new IllegalStateException(error); }
        prefs(account,global).edit().putString("messages",a.toString()).apply();
    }
    public static ArrayList<Long> selected(int account) {
        ArrayList<Long> out = new ArrayList<>();
        try { JSONArray a=new JSONArray(prefs(account,false).getString("dialogs","[]"));
            for(int i=0;i<a.length();i++) { long id=a.optLong(i); if(id!=0&&!out.contains(id)) out.add(id); }
        } catch(Exception ignored) {} return out;
    }
    public static void saveSelected(int account, List<Long> items) {
        JSONArray a=new JSONArray(); for(long id:items) a.put(id);
        prefs(account,false).edit().putString("dialogs",a.toString()).apply();
    }
    public static boolean eligible(int account, long id, boolean excludePrivate) {
        MessagesController mc=MessagesController.getInstance(account);
        if (mc.getSendPaidMessagesStars(id) > 0) return false;
        if(DialogObject.isUserDialog(id)) {
            TLRPC.User u=mc.getUser(id);
            return !excludePrivate && u!=null && !u.deleted && !UserObject.isBot(u) && !UserObject.isUserSelf(u) && id!=UserConfig.getInstance(account).getClientUserId();
        }
        if (!DialogObject.isChatDialog(id)) return false;
        TLRPC.Chat c=mc.getChat(-id);
        return c!=null && !c.broadcast && !c.gigagroup && !ChatObject.isChannelAndNotMegaGroup(c) && !ChatObject.isNotInChat(c) && ChatObject.canSendPlain(c);
    }
    public static boolean isRunning(int account) { return sessions.containsKey(account); }
    public static void stop(int account) { Session s=sessions.remove(account); if(s!=null) { s.stopped=true; if(s.task!=null) AndroidUtilities.cancelRunOnUIThread(s.task); } }
    public static int start(int account, boolean global) {
        stop(account);
        SharedPreferences p=prefs(account,global); boolean exclude=global && p.getBoolean("exclude_private",true);
        ArrayList<Long> ids=new ArrayList<>();
        if(global) { for(TLRPC.Dialog d:MessagesController.getInstance(account).getAllDialogs()) if(eligible(account,d.id,exclude)) ids.add(d.id); }
        else { for(long id:selected(account)) if(eligible(account,id,false)) ids.add(id); }
        ArrayList<PostMessage> items=messages(account,global);
        ArrayList<String> texts=new ArrayList<>();
        for(PostMessage item:items) texts.add(item.text);
        if(ids.isEmpty() || texts.isEmpty()) return 0;
        int mode=p.getInt("repeat",0); AutoPostPlan.Repeat repeat=mode==2?AutoPostPlan.Repeat.CONTINUOUS:mode==1?AutoPostPlan.Repeat.COUNT:AutoPostPlan.Repeat.ONCE;
        AutoPostPlan plan=new AutoPostPlan(ids,texts,p.getInt("message_delay",10),p.getInt("dialog_delay",30),repeat,p.getInt("rounds",2));
        Session s=new Session(account,exclude,plan,items); sessions.put(account,s); s.advance(); return ids.size();
    }
    private static final class Session {
        final int account; final boolean exclude; final AutoPostPlan plan; final ArrayList<PostMessage> items; boolean stopped; Runnable task;
        Session(int account,boolean exclude,AutoPostPlan plan,ArrayList<PostMessage> items){this.account=account;this.exclude=exclude;this.plan=plan;this.items=items;}
        void advance() {
            if(stopped || sessions.get(account)!=this) return;
            AutoPostPlan.Step step=plan.next();
            if(step==null) { sessions.remove(account); return; }
            task=() -> {
                if(stopped || sessions.get(account)!=this) return;
                try {
                    if(eligible(account,step.dialogId,exclude)) {
                        PostMessage item=items.get(step.messageIndex);
                        SendMessagesHelper.SendMessageParams params=SendMessagesHelper.SendMessageParams.of(item.text,step.dialogId);
                        params.entities=decodeEntities(encodeEntities(item.entities),item.text);
                        SendMessagesHelper.getInstance(account).sendMessage(params);
                    }
                    advance();
                } catch (RuntimeException failure) {
                    stop(account);
                }
            };
            AndroidUtilities.runOnUIThread(task,step.delayMs);
        }
    }
}
