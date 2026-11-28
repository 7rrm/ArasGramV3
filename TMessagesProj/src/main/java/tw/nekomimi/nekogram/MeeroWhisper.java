package tw.nekomimi.nekogram;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.util.Base64;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatActivityEnterView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** A personal inline bot hosted in this app process. It is not an always-on service. */
public final class MeeroWhisper {
    private static final String ALIAS = "meero_whisper_key_v1";
    private static final ConcurrentHashMap<String, JSONObject> pending = new ConcurrentHashMap<>();
    private static volatile int generation;
    private static volatile String activeToken;
    private static volatile long ownerId;
    private static volatile String botName;
    private static long offset;
    private static final SecureRandom random = new SecureRandom();

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("meero_whisper_private", Context.MODE_PRIVATE);
    }
    private static synchronized SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }
    private static String encrypt(String value) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv=c.getIV(), body=c.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] all=new byte[iv.length+body.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(body,0,all,iv.length,body.length);
        return Base64.encodeToString(all, Base64.NO_WRAP);
    }
    private static String decrypt(String value) throws Exception {
        byte[] all=Base64.decode(value,Base64.NO_WRAP);if(all.length<29)throw new IllegalArgumentException();
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,all,0,12));
        return new String(c.doFinal(all,12,all.length-12),StandardCharsets.UTF_8);
    }
    private static JSONObject api(String token,String method,JSONObject body,int timeout) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.telegram.org/bot"+token+"/"+method).openConnection();
        try {
            c.setRequestMethod("POST");c.setConnectTimeout(10000);c.setReadTimeout(timeout);c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>1024*1024)throw new IllegalStateException();}
                JSONObject r=new JSONObject(out.toString("UTF-8"));if(!r.optBoolean("ok"))throw new IllegalStateException();return r;
            }
        } finally {c.disconnect();}
    }
    public static void start() {
        if(Build.VERSION.SDK_INT<23)return;
        try {
            String v=prefs().getString("config",null);if(v==null)return;
            JSONObject j=new JSONObject(decrypt(v));
            begin(j.getString("token"),j.getString("username"),j.getLong("owner"));
        } catch(Exception ignored) { }
    }
    private static synchronized void begin(String token,String name,long owner) {
        if(token.equals(activeToken) && owner==ownerId && name.equals(botName))return;
        int g=++generation;activeToken=token;botName=name;ownerId=owner;offset=0;
        new Thread(()->poll(token,owner,g),"MeeroWhisperBot").start();
    }
    private static synchronized void stop() {generation++;activeToken=null;botName=null;ownerId=0;offset=0;pending.clear();}
    private static void poll(String token,long owner,int g) {
        long next=0;
        while(g==generation) {
            try {
                JSONObject request=new JSONObject().put("offset",next).put("timeout",20).put("allowed_updates",new JSONArray().put("inline_query").put("callback_query"));
                JSONArray updates=api(token,"getUpdates",request,30000).getJSONArray("result");
                if(g!=generation)return;
                for(int i=0;i<updates.length();i++) {
                    JSONObject u=updates.getJSONObject(i);next=Math.max(next,u.getLong("update_id")+1);
                    JSONObject q=u.optJSONObject("inline_query"), cb=u.optJSONObject("callback_query");
                    if(q!=null)handleInline(token,owner,q);
                    if(cb!=null)handleCallback(token,owner,cb);
                }
            } catch(Exception ignored) {try{Thread.sleep(4000);}catch(InterruptedException e){return;}}
        }
    }
    private static void handleInline(String token,long owner,JSONObject q) throws Exception {
        String id=q.getString("id"), query=q.optString("query");
        JSONObject entry=q.getJSONObject("from").optLong("id")==owner?pending.get(query):null;
        JSONArray results=new JSONArray();
        if(entry!=null) {
            JSONObject button=new JSONObject().put("text","افتح الهمسة 🔒").put("callback_data","w:"+query);
            JSONObject markup=new JSONObject().put("inline_keyboard",new JSONArray().put(new JSONArray().put(button)));
            JSONObject content=new JSONObject().put("message_text","🔒 همسة خاصة للشخص المقصود\nاضغط الزر لقراءة الهمسة.");
            results.put(new JSONObject().put("type","article").put("id",query).put("title","همسة خاصة 🔒")
                    .put("input_message_content",content).put("reply_markup",markup));
        }
        api(token,"answerInlineQuery",new JSONObject().put("inline_query_id",id).put("results",results).put("cache_time",0).put("is_personal",true),10000);
    }
    private static void handleCallback(String token,long owner,JSONObject cb) throws Exception {
        String data=cb.optString("data"), answer="الهمسة غير متوفرة على هذا الجهاز.";
        if(data.startsWith("w:")) {
            String id=data.substring(2);String encrypted=prefs().getString("w_"+id,null);
            if(encrypted!=null) {
                try {
                    JSONObject entry=new JSONObject(decrypt(encrypted));long who=cb.getJSONObject("from").optLong("id");
                    answer=(who==entry.getLong("recipient") || who==owner)?entry.getString("text"):"هذه الهمسة ليست لك 🔒";
                } catch(Exception ignored) { }
            }
        }
        api(token,"answerCallbackQuery",new JSONObject().put("callback_query_id",cb.getString("id")).put("text",answer).put("show_alert",true).put("cache_time",0),10000);
    }
    public static void showSettings(org.telegram.ui.ActionBar.BaseFragment fragment,int account) {
        Context context=fragment.getParentActivity();if(context==null)return;
        if(Build.VERSION.SDK_INT<23){alert(context,"هذه الميزة تتطلب أندرويد 6 أو أحدث.");return;}
        EditText input=new EditText(context);input.setSingleLine(true);input.setHint("توكن البوت من BotFather");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout box=new LinearLayout(context);box.setOrientation(LinearLayout.VERTICAL);int pad=AndroidUtilities.dp(18);box.setPadding(pad,pad,pad,pad);box.addView(input);
        AlertDialog.Builder b=new AlertDialog.Builder(context).setTitle("بوت الهمسة")
                .setMessage("فعّل Inline Mode للبوت من BotFather. يعمل البوت ما دامت النسخة شغالة. لا تشارك التوكن مع أحد. إدخال توكن جديد يوقف التوكن السابق.")
                .setView(box).setPositiveButton("تحقق وحفظ",(d,w)->{
                    String token=input.getText().toString().trim();long owner=UserConfig.getInstance(account).getClientUserId();
                    if(!token.matches("[0-9]{5,20}:[A-Za-z0-9_-]{20,100}")||owner<=0){alert(context,"التوكن أو الحساب غير صالح.");return;}
                    new Thread(()->{
                        try {
                            JSONObject bot=api(token,"getMe",new JSONObject(),10000).getJSONObject("result");
                            if(!bot.optBoolean("supports_inline_queries"))throw new IllegalStateException("inline");
                            JSONObject hook=api(token,"getWebhookInfo",new JSONObject(),10000).getJSONObject("result");
                            if(!hook.optString("url","").isEmpty())throw new IllegalStateException("webhook");
                            String name=bot.getString("username");String encrypted=encrypt(new JSONObject().put("token",token).put("username",name).put("owner",owner).toString());
                            if(!prefs().edit().putString("config",encrypted).commit())throw new IllegalStateException();
                            begin(token,name,owner);AndroidUtilities.runOnUIThread(()->alert(context,"تم حفظ التوكن وتشغيل بوت الهمسة لهذا الحساب."));
                        } catch(Exception e){AndroidUtilities.runOnUIThread(()->alert(context,"تعذر تشغيل البوت. تأكد من التوكن والإنترنت وتفعيل Inline Mode، وأن البوت لا يستخدم Webhook أو تطبيقاً آخر."));}
                    },"WhisperSetup").start();
                }).setNegativeButton("إلغاء",null);
        b.setNeutralButton("حذف التوكن وإيقاف البوت",(d,w)->{stop();prefs().edit().remove("config").apply();alert(context,"تم إيقاف البوت وحذف التوكن من هذا الجهاز.");});b.show();
    }
    private static void alert(Context c,String text){if(c!=null)new AlertDialog.Builder(c).setTitle("الهمسة").setMessage(text).setPositiveButton("حسناً",null).show();}
    public static boolean isCommand(String s){return s.matches("(?s)^/(همسه|همسة|hmsa)(?:\\s.*|$)");}
    public static void send(ChatActivity fragment,ChatActivityEnterView view,int account,long dialogId,MessageObject reply,MessageObject top,ChatActivity.ReplyQuote quote,String original,boolean notify,long stars,long mono) {
        Context context=fragment.getParentActivity();String text=original.replaceFirst("(?s)^/(همسه|همسة|hmsa)\\s*","").trim();
        if(activeToken==null || botName==null || ownerId!=UserConfig.getInstance(account).getClientUserId()){alert(context,"ضع توكن البوت أولاً في إعدادات المحادثات، واستخدم الحساب الذي أضفته منه.");return;}
        if(reply==null || reply.isTopicMainMessage || reply.getSenderId()<=0){alert(context,"رد على رسالة شخص أولاً.");return;}
        if(text.isEmpty() || text.length()>180){alert(context,"اكتب نص الهمسة بعد الأمر، بحد أقصى 180 حرفاً.");return;}
        if(dialogId==0 || org.telegram.messenger.DialogObject.isEncryptedDialog(dialogId) || stars!=0){alert(context,"الهمسة غير مدعومة في هذه المحادثة أو عند إرسال الرسائل المدفوعة.");return;}
        try {
            byte[] bytes=new byte[18];random.nextBytes(bytes);String id=Base64.encodeToString(bytes,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
            JSONObject entry=new JSONObject().put("recipient",reply.getSenderId()).put("text",text).put("created",System.currentTimeMillis());
            if(!prefs().edit().putString("w_"+id,encrypt(entry.toString())).commit())throw new IllegalStateException();
            pending.put(id,entry);String name=botName;long owner=ownerId;
            TLRPC.TL_contacts_resolveUsername resolve=new TLRPC.TL_contacts_resolveUsername();resolve.username=name;
            ConnectionsManager.getInstance(account).sendRequest(resolve,(response,error)->AndroidUtilities.runOnUIThread(()->{
                if(owner!=ownerId || !name.equals(botName) || !(response instanceof TLRPC.TL_contacts_resolvedPeer)){pending.remove(id);alert(context,"تعذر الوصول إلى البوت؛ الهمسة لم تُرسل.");return;}
                TLRPC.TL_contacts_resolvedPeer peer=(TLRPC.TL_contacts_resolvedPeer)response;
                if(peer.users.isEmpty()){pending.remove(id);alert(context,"تعذر العثور على البوت؛ الهمسة لم تُرسل.");return;}
                MessagesController mc=MessagesController.getInstance(account);mc.putUsers(peer.users,false);TLRPC.User bot=peer.users.get(0);
                TLRPC.TL_messages_getInlineBotResults req=new TLRPC.TL_messages_getInlineBotResults();req.bot=mc.getInputUser(bot);req.peer=mc.getInputPeer(dialogId);req.query=id;req.offset="";
                ConnectionsManager.getInstance(account).sendRequest(req,(result,err)->AndroidUtilities.runOnUIThread(()->{
                    pending.remove(id);
                    if(owner!=ownerId || !name.equals(botName) || !(result instanceof TLRPC.TL_messages_botResults) || ((TLRPC.TL_messages_botResults)result).results.isEmpty()) {alert(context,"تعذر تجهيز الهمسة. تأكد أن البوت شغال وInline مفعّل؛ لم تُرسل.");return;}
                    TLRPC.TL_messages_botResults results=(TLRPC.TL_messages_botResults)result;TLRPC.BotInlineResult item=results.results.get(0);item.query_id=results.query_id;
                    HashMap<String,String> params=new HashMap<>();params.put("id",item.id);params.put("query_id",Long.toString(item.query_id));params.put("bot",Long.toString(bot.id));params.put("bot_name",name);
                    SendMessagesHelper.prepareSendingBotContextResult(fragment,org.telegram.messenger.AccountInstance.getInstance(account),item,params,dialogId,reply,top,null,quote,notify,0,0,fragment.getMessageChatSendParams(),0,mono);
                    if(view.getFieldText()!=null && original.contentEquals(view.getFieldText().toString().trim()))view.setFieldText("");
                }));
            }));
        } catch(Exception e){alert(context,"تعذر حفظ الهمسة بأمان؛ لم تُرسل.");}
    }
}
