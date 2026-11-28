package tw.nekomimi.nekogram;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.util.Base64;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
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
    private static final java.util.concurrent.atomic.AtomicInteger verification = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile String activeToken;
    private static volatile long ownerId;
    private static volatile String botName;
    private static volatile String connectionStatus = "غير مشغّل";
    private static volatile String connectionProblem = "";
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
    private static final class ApiFailure extends Exception {
        final int code;
        ApiFailure(int code) { super("Bot API HTTP " + code); this.code = code; }
    }
    private static JSONObject api(String token,String method,JSONObject body,int timeout) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("https://api.telegram.org/bot"+token+"/"+method).openConnection();
        try {
            c.setRequestMethod("POST");c.setConnectTimeout(10000);c.setReadTimeout(timeout);c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new ApiFailure(code);
            try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>1024*1024)throw new IllegalStateException();}
                JSONObject r=new JSONObject(out.toString("UTF-8"));
                if(!r.optBoolean("ok"))throw new ApiFailure(r.optInt("error_code", code));
                return r;
            }
        } finally {c.disconnect();}
    }
    /** Never display exception messages: URLs may contain the token. */
    private static String explain(Exception e) {
        if(e instanceof ApiFailure) {
            int code=((ApiFailure)e).code;
            if(code==401 || code==404) return "التوكن غير صالح أو تم إلغاؤه من BotFather.";
            if(code==409) return "يوجد تعارض: ربما البوت يعمل في تطبيق آخر أو يوجد Webhook مفعّل.";
            if(code==429) return "تيليغرام قيّد الطلبات مؤقتاً؛ انتظر وحاول مجدداً.";
            return "تعذر الاتصال بواجهة البوت (رمز HTTP " + code + ").";
        }
        if(e instanceof java.net.UnknownHostException) return "تعذر العثور على api.telegram.org؛ تحقق من DNS والاتصال.";
        if(e instanceof java.net.SocketTimeoutException) return "انتهت مهلة الاتصال بواجهة تيليغرام؛ تحقق من الإنترنت.";
        if(e instanceof javax.net.ssl.SSLException) return "تعذر إنشاء اتصال آمن بواجهة تيليغرام؛ تحقق من اتصالك ووقت الجهاز.";
        if(e instanceof java.io.IOException) return "تعذر الاتصال بواجهة تيليغرام. اتصال البوت مباشر وقد لا يستخدم بروكسي التطبيق.";
        return "تعذر إكمال التحقق أو فتح التخزين الآمن على الجهاز.";
    }
    public static void start() {
        if(Build.VERSION.SDK_INT<23)return;
        try {
            String v=prefs().getString("config",null);if(v==null)return;
            JSONObject j=new JSONObject(decrypt(v));
            if (j.optBoolean("inline",true) && !j.optBoolean("webhook",false)) {
                begin(j.getString("token"),j.getString("username"),j.getLong("owner"));
            }
        } catch(Exception e) { connectionStatus="مشكلة في التخزين الآمن";connectionProblem=explain(e); }
    }
    private static synchronized void begin(String token,String name,long owner) {
        if(token.equals(activeToken) && owner==ownerId && name.equals(botName))return;
        int g=++generation;activeToken=token;botName=name;ownerId=owner;offset=0;
        connectionStatus="جاري الاتصال";connectionProblem="";
        new Thread(()->poll(token,owner,g),"MeeroWhisperBot").start();
    }
    private static synchronized void stop() {generation++;activeToken=null;botName=null;ownerId=0;offset=0;pending.clear();connectionStatus="متوقف";connectionProblem="";}
    private static void poll(String token,long owner,int g) {
        long next=0;
        while(g==generation) {
            try {
                JSONObject request=new JSONObject().put("offset",next).put("timeout",20).put("allowed_updates",new JSONArray().put("inline_query").put("callback_query"));
                JSONArray updates=api(token,"getUpdates",request,30000).getJSONArray("result");
                if(g!=generation)return;
                connectionStatus="نشط";connectionProblem="";
                for(int i=0;i<updates.length();i++) {
                    JSONObject u=updates.getJSONObject(i);next=Math.max(next,u.getLong("update_id")+1);
                    JSONObject q=u.optJSONObject("inline_query"), cb=u.optJSONObject("callback_query");
                    if(q!=null)handleInline(token,owner,q);
                    if(cb!=null)handleCallback(token,owner,cb);
                }
            } catch(Exception e) {
                if(g!=generation)return;
                connectionStatus="مشكلة اتصال";connectionProblem=explain(e);
                try{Thread.sleep(4000);}catch(InterruptedException interrupted){return;}
            }
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
    private static JSONObject storedConfig() throws Exception {
        String value=prefs().getString("config",null);
        return value==null?null:new JSONObject(decrypt(value));
    }
    private static String settingsStatus(int account) {
        StringBuilder out=new StringBuilder();
        try {
            JSONObject config=storedConfig();
            if(config==null) out.append("التوكن: غير محفوظ\nالبوت: لا يوجد");
            else {
                out.append("التوكن: محفوظ (مخفي)\nالبوت: @").append(config.optString("username","—"));
                if(config.optLong("owner")!=UserConfig.getInstance(account).getClientUserId()) {
                    out.append("\nالحالة: هذا البوت مربوط بحساب آخر في النسخة.");
                } else if(!config.optBoolean("inline",true)) {
                    out.append("\nالحالة: التوكن صالح، لكن Inline Mode غير مفعّل. فعّله في BotFather ثم اضغط تحديث الحالة.");
                } else if(config.optBoolean("webhook",false)) {
                    out.append("\nالحالة: يوجد Webhook لهذا البوت. عطّله من خدمتك الأخرى قبل التشغيل، ثم اضغط تحديث الحالة.");
                } else {
                    out.append("\nحالة البوت: ").append(connectionStatus);
                    if(!connectionProblem.isEmpty())out.append("\nالمشكلة: ").append(connectionProblem);
                }
            }
        } catch(Exception e) {out.append("التوكن: تعذر فتح التخزين الآمن. ").append(explain(e));}
        String last=prefs().getString("last_setup_issue","");
        if(!last.isEmpty())out.append("\nآخر مشكلة بالتحقق: ").append(last);
        out.append("\n\nيعمل البوت ما دامت النسخة شغّالة. لا تشارك التوكن مع أحد.");
        return out.toString();
    }
    /** Validate before saving; a failing replacement must not erase the working bot. */
    private static void verifyToken(String token,long owner,TextView status,int account) {
        final int check=verification.incrementAndGet();
        status.setText("جاري التحقق من التوكن وحالة البوت…");
        new Thread(()->{
            try {
                JSONObject bot=api(token,"getMe",new JSONObject(),10000).getJSONObject("result");
                String name=bot.getString("username");
                boolean inline=bot.optBoolean("supports_inline_queries",false);
                JSONObject hook=api(token,"getWebhookInfo",new JSONObject(),10000).getJSONObject("result");
                boolean webhook=!hook.optString("url","").isEmpty();
                JSONObject config=new JSONObject().put("token",token).put("username",name).put("owner",owner).put("inline",inline).put("webhook",webhook);
                if(check!=verification.get())return;
                if(!prefs().edit().putString("config",encrypt(config.toString())).remove("last_setup_issue").commit())throw new IllegalStateException();
                if(inline && !webhook) begin(token,name,owner); else stop();
            } catch(Exception e) {
                if(check!=verification.get())return;
                prefs().edit().putString("last_setup_issue",explain(e)).apply();
            }
            AndroidUtilities.runOnUIThread(()->{if(check==verification.get())status.setText(settingsStatus(account));});
        },"WhisperSetup").start();
    }
    public static void showSettings(org.telegram.ui.ActionBar.BaseFragment fragment,int account) {
        Context context=fragment.getParentActivity();if(context==null)return;
        if(Build.VERSION.SDK_INT<23){alert(context,"هذه الميزة تتطلب أندرويد 6 أو أحدث.");return;}
        TextView status=new TextView(context);status.setTextSize(15);status.setTextColor(fragment.getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));status.setText(settingsStatus(account));
        EditText input=new EditText(context);input.setSingleLine(true);input.setHint("أدخل توكن جديداً أو اتركه فارغاً");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button refresh=new Button(context);refresh.setText("تحديث الحالة");
        refresh.setOnClickListener(v->{
            try {
                JSONObject config=storedConfig();
                if(config==null){status.setText(settingsStatus(account));return;}
                if(config.getLong("owner")!=UserConfig.getInstance(account).getClientUserId()){
                    status.setText(settingsStatus(account));return;
                }
                verifyToken(config.getString("token"),config.getLong("owner"),status,account);
            }catch(Exception e){prefs().edit().putString("last_setup_issue",explain(e)).apply();status.setText(settingsStatus(account));}
        });
        LinearLayout box=new LinearLayout(context);box.setOrientation(LinearLayout.VERTICAL);int pad=AndroidUtilities.dp(18);box.setPadding(pad,pad,pad,pad);
        box.addView(status);box.addView(refresh);box.addView(input);
        new AlertDialog.Builder(context).setTitle("بوت الهمسة").setView(box)
            .setPositiveButton("تحقق وحفظ",(d,w)->{
                String token=input.getText().toString().trim();long owner=UserConfig.getInstance(account).getClientUserId();
                if(!token.matches("[0-9]{5,20}:[A-Za-z0-9_-]{20,100}")||owner<=0){
                    prefs().edit().putString("last_setup_issue","صيغة التوكن أو الحساب غير صالحة.").apply();
                    showSettings(fragment,account);return;
                }
                // Reopen the status panel so progress and the result remain visible.
                showSettingsAndVerify(fragment,account,token,owner);
            })
            .setNegativeButton("إغلاق",null)
            .setNeutralButton("حذف التوكن",(d,w)->{verification.incrementAndGet();stop();prefs().edit().remove("config").remove("last_setup_issue").apply();showSettings(fragment,account);})
            .show();
        Runnable[] updater=new Runnable[1];
        updater[0]=()->{
            if(!status.isAttachedToWindow())return;
            // Don't replace the progress message while a manual check is running.
            if(!status.getText().toString().startsWith("جاري التحقق"))status.setText(settingsStatus(account));
            AndroidUtilities.runOnUIThread(updater[0],1500);
        };
        AndroidUtilities.runOnUIThread(updater[0],1500);
    }
    private static void showSettingsAndVerify(org.telegram.ui.ActionBar.BaseFragment fragment,int account,String token,long owner) {
        Context context=fragment.getParentActivity();if(context==null)return;
        TextView status=new TextView(context);status.setTextSize(15);int pad=AndroidUtilities.dp(20);status.setPadding(pad,pad,pad,pad);
        status.setTextColor(fragment.getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        new AlertDialog.Builder(context).setTitle("حالة بوت الهمسة").setView(status)
                .setPositiveButton("العودة للإعدادات",(d,w)->showSettings(fragment,account)).show();
        verifyToken(token,owner,status,account);
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
