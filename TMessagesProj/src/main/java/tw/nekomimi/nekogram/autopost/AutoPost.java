package tw.nekomimi.nekogram.autopost;

import android.content.SharedPreferences;
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
    public static ArrayList<String> messages(int account, boolean global) {
        ArrayList<String> out = new ArrayList<>();
        try { JSONArray a = new JSONArray(prefs(account,global).getString("messages", "[]"));
            for (int i=0;i<a.length() && i<10;i++) { String s=a.optString(i, ""); if (!s.trim().isEmpty()) out.add(s); }
        } catch (Exception ignored) {} return out;
    }
    public static void saveMessages(int account, boolean global, List<String> items) {
        JSONArray a = new JSONArray(); for (String s : items) a.put(s);
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
        ArrayList<String> texts=messages(account,global);
        if(ids.isEmpty() || texts.isEmpty()) return 0;
        int mode=p.getInt("repeat",0); AutoPostPlan.Repeat repeat=mode==2?AutoPostPlan.Repeat.CONTINUOUS:mode==1?AutoPostPlan.Repeat.COUNT:AutoPostPlan.Repeat.ONCE;
        AutoPostPlan plan=new AutoPostPlan(ids,texts,p.getInt("message_delay",10),p.getInt("dialog_delay",30),repeat,p.getInt("rounds",2));
        Session s=new Session(account,exclude,plan); sessions.put(account,s); s.advance(); return ids.size();
    }
    private static final class Session {
        final int account; final boolean exclude; final AutoPostPlan plan; boolean stopped; Runnable task;
        Session(int account,boolean exclude,AutoPostPlan plan){this.account=account;this.exclude=exclude;this.plan=plan;}
        void advance() {
            if(stopped || sessions.get(account)!=this) return;
            AutoPostPlan.Step step=plan.next();
            if(step==null) { sessions.remove(account); return; }
            task=() -> {
                if(stopped || sessions.get(account)!=this) return;
                try {
                    if(eligible(account,step.dialogId,exclude)) SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(step.message,step.dialogId));
                    advance();
                } catch (RuntimeException failure) {
                    stop(account);
                }
            };
            AndroidUtilities.runOnUIThread(task,step.delayMs);
        }
    }
}
