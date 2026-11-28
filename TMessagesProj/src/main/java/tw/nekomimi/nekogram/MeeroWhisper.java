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
import android.widget.CheckBox;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
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
    private static volatile int connectionStatus = R.string.HmsaNotRunning;
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
    private static String tr(int id) { return LocaleController.getString(id); }
    /** Store only stable error codes, never exception messages that may contain the token. */
    private static String issueKey(Exception e) {
        if(e instanceof ApiFailure) {
            int code=((ApiFailure)e).code;
            if(code==401 || code==404)return "bad_token";
            if(code==409)return "conflict";
            if(code==429)return "rate_limit";
            return "http:"+code;
        }
        if(e instanceof java.net.UnknownHostException)return "dns";
        if(e instanceof java.net.SocketTimeoutException)return "timeout";
        if(e instanceof javax.net.ssl.SSLException)return "ssl";
        if(e instanceof java.io.IOException)return "network";
        return "unknown";
    }
    private static String describeIssue(String issue) {
        switch(issue) {
            case "bad_token":return tr(R.string.HmsaBadToken);
            case "conflict":return tr(R.string.HmsaConflict);
            case "rate_limit":return tr(R.string.HmsaRateLimit);
            case "dns":return tr(R.string.HmsaDnsError);
            case "timeout":return tr(R.string.HmsaTimeoutError);
            case "ssl":return tr(R.string.HmsaSslError);
            case "network":return tr(R.string.HmsaNetworkError);
            case "invalid_format":return tr(R.string.HmsaInvalidFormat);
            case "unknown":return tr(R.string.HmsaUnknownError);
            default:
                if(issue.matches("http:[0-9]{1,3}"))return tr(R.string.HmsaHttpError).replace("{code}",issue.substring(5));
                return tr(R.string.HmsaOldIssue);
        }
    }
    public static void start() {
        if(Build.VERSION.SDK_INT<23)return;
        try {
            String v=prefs().getString("config",null);if(v==null)return;
            JSONObject j=new JSONObject(decrypt(v));
            if (j.optBoolean("inline",true) && !j.optBoolean("webhook",false)) {
                begin(j.getString("token"),j.getString("username"),j.getLong("owner"));
            }
        } catch(Exception e) { connectionStatus=R.string.HmsaStorageProblem;connectionProblem=issueKey(e); }
    }
    private static synchronized void begin(String token,String name,long owner) {
        if(token.equals(activeToken) && owner==ownerId && name.equals(botName))return;
        int g=++generation;activeToken=token;botName=name;ownerId=owner;offset=0;
        connectionStatus=R.string.HmsaConnecting;connectionProblem="";
        new Thread(()->poll(token,owner,g),"MeeroWhisperBot").start();
    }
    private static synchronized void stop() {generation++;activeToken=null;botName=null;ownerId=0;offset=0;pending.clear();connectionStatus=R.string.HmsaStopped;connectionProblem="";}
    private static void poll(String token,long owner,int g) {
        long next=0;
        while(g==generation) {
            try {
                JSONObject request=new JSONObject().put("offset",next).put("timeout",20).put("allowed_updates",new JSONArray().put("inline_query").put("callback_query"));
                JSONArray updates=api(token,"getUpdates",request,30000).getJSONArray("result");
                if(g!=generation)return;
                connectionStatus=R.string.HmsaActive;connectionProblem="";
                for(int i=0;i<updates.length();i++) {
                    JSONObject u=updates.getJSONObject(i);next=Math.max(next,u.getLong("update_id")+1);
                    JSONObject q=u.optJSONObject("inline_query"), cb=u.optJSONObject("callback_query");
                    if(q!=null)handleInline(token,owner,q);
                    if(cb!=null)handleCallback(token,owner,cb);
                }
            } catch(Exception e) {
                if(g!=generation)return;
                connectionStatus=R.string.HmsaConnectionProblem;connectionProblem=issueKey(e);
                try{Thread.sleep(4000);}catch(InterruptedException interrupted){return;}
            }
        }
    }
    private static String randomId() {
        byte[] bytes=new byte[18];random.nextBytes(bytes);
        return Base64.encodeToString(bytes,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
    }
    private static String escapeHtml(String value) {
        return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }
    private static String mention(long id, String username, String displayName) {
        if(username!=null && username.matches("[A-Za-z0-9_]{5,32}")) return "@"+username;
        String label=displayName==null || displayName.trim().isEmpty()?tr(R.string.HmsaUser):displayName;
        return "<a href=\"tg://user?id="+id+"\">"+escapeHtml(label)+"</a>";
    }
    private static void savePerson(long id, String username, String displayName) throws Exception {
        JSONObject person=new JSONObject().put("id",id).put("username",username==null?"":username)
                .put("name",displayName==null?"":displayName);
        if(!prefs().edit().putString("person_"+id,encrypt(person.toString())).commit())throw new IllegalStateException();
    }
    private static JSONObject getPerson(long id) throws Exception {
        String saved=prefs().getString("person_"+id,null);
        return saved==null?null:new JSONObject(decrypt(saved));
    }
    private static JSONObject article(String id,String title,String message,JSONObject button) throws Exception {
        JSONObject markup=new JSONObject().put("inline_keyboard",new JSONArray().put(new JSONArray().put(button)));
        JSONObject content=new JSONObject().put("message_text",message).put("parse_mode","HTML");
        return new JSONObject().put("type","article").put("id",id).put("title",title)
                .put("input_message_content",content).put("reply_markup",markup);
    }
    private static String header() { return tr(R.string.HmsaHeader); }
    private static void handleInline(String token,long owner,JSONObject q) throws Exception {
        String queryId=q.getString("id"), query=q.optString("query");
        long asker=q.getJSONObject("from").optLong("id");
        JSONArray results=new JSONArray();
        // The initial card is generated only for the account that owns this bot.
        JSONObject card=asker==owner?pending.get(query):null;
        if(card!=null && "card".equals(card.optString("kind"))) {
            long recipient=card.getLong("recipient");
            String target=mention(recipient,card.optString("username"),card.optString("name"));
            JSONObject button=new JSONObject().put("text",tr(R.string.HmsaPressHere))
                    .put("switch_inline_query_current_chat","secret "+recipient+" \n"+tr(R.string.HmsaSample));
            results.put(article(query,tr(R.string.HmsaSecretWhisper),header()+tr(R.string.HmsaInvitation)+target+" 💌",button));
        } else if(card!=null && "direct".equals(card.optString("kind"))) {
            long recipient=card.getLong("recipient");
            String target=mention(recipient,card.optString("username"),card.optString("name"));
            JSONObject button=new JSONObject().put("text",tr(R.string.HmsaOpen)).put("callback_data","w:"+query);
            results.put(article(query,tr(R.string.HmsaSecretWhisper),header()+tr(R.string.HmsaWhisperTo)+target+tr(R.string.HmsaOnlyRecipient),button));
        } else if(query.startsWith("secret ")) {
            // Inline text goes to Telegram and the bot, not the chat, unless the user sends it as an ordinary message.
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?s)^secret ([0-9]{1,20})\\s+(.+)$").matcher(query);
            if(m.matches() && asker>0) {
                long recipient;
                try {recipient=Long.parseLong(m.group(1));}catch(NumberFormatException e){recipient=0;}
                String text=m.group(2).trim();JSONObject person=recipient>0?getPerson(recipient):null;
                if(person!=null && !text.isEmpty() && text.length()<=180) {
                    JSONObject from=q.getJSONObject("from");
                    String senderName=from.optString("first_name","");
                    if(!from.optString("last_name","").isEmpty())senderName+=" "+from.optString("last_name");
                    savePerson(asker,from.optString("username",""),senderName);
                    String id=randomId();
                    JSONObject entry=new JSONObject().put("recipient",recipient).put("sender",asker).put("text",text).put("created",System.currentTimeMillis());
                    if(!prefs().edit().putString("w_"+id,encrypt(entry.toString())).commit())throw new IllegalStateException();
                    String target=mention(recipient,person.optString("username"),person.optString("name"));
                    JSONObject button=new JSONObject().put("text",tr(R.string.HmsaOpen)).put("callback_data","w:"+id);
                    results.put(article(id,tr(R.string.HmsaWhisperFor)+(person.optString("username").isEmpty()?person.optString("name",tr(R.string.HmsaUser)):"@"+person.optString("username")),
                            header()+tr(R.string.HmsaWhisperTo)+target+tr(R.string.HmsaOnlyRecipient),button));
                }
            }
        }
        api(token,"answerInlineQuery",new JSONObject().put("inline_query_id",queryId).put("results",results).put("cache_time",0).put("is_personal",true),10000);
    }
    private static void handleCallback(String token,long owner,JSONObject cb) throws Exception {
        String data=cb.optString("data"), answer=tr(R.string.HmsaUnavailable);
        JSONObject entry=null;long who=cb.getJSONObject("from").optLong("id");
        if(data.startsWith("w:")) {
            String id=data.substring(2);String encrypted=prefs().getString("w_"+id,null);
            if(encrypted!=null) {
                try {
                    entry=new JSONObject(decrypt(encrypted));
                    long sender=entry.optLong("sender",owner);
                    answer=(who==entry.getLong("recipient") || who==sender)?entry.getString("text"):tr(R.string.HmsaNotForYou);
                } catch(Exception ignored) {entry=null;}
            }
        }
        api(token,"answerCallbackQuery",new JSONObject().put("callback_query_id",cb.getString("id")).put("text",answer).put("show_alert",true).put("cache_time",0),10000);
        // The recipient's first read replaces the button, as in the supplied example.
        if(entry!=null && who==entry.getLong("recipient") && cb.has("inline_message_id")) {
            long sender=entry.optLong("sender",owner);JSONObject from=cb.getJSONObject("from");
            String name=from.optString("first_name","");
            if(!from.optString("last_name","").isEmpty())name+=" "+from.optString("last_name");
            String reader=mention(who,from.optString("username",""),name);
            String time=new java.text.SimpleDateFormat("h:mm a",java.util.Locale.getDefault()).format(new java.util.Date());
            JSONObject button=new JSONObject().put("text",tr(R.string.HmsaReply))
                    .put("switch_inline_query_current_chat","secret "+sender+" \n"+tr(R.string.HmsaSample));
            JSONObject markup=new JSONObject().put("inline_keyboard",new JSONArray().put(new JSONArray().put(button)));
            JSONObject edit=new JSONObject().put("inline_message_id",cb.getString("inline_message_id"))
                    .put("text",tr(R.string.HmsaReadPrefix)+reader+tr(R.string.HmsaReadTime)+escapeHtml(time))
                    .put("parse_mode","HTML").put("reply_markup",markup);
            try {api(token,"editMessageText",edit,10000);}catch(Exception ignored) { }
        }
    }
    private static JSONObject storedConfig() throws Exception {
        String value=prefs().getString("config",null);
        return value==null?null:new JSONObject(decrypt(value));
    }
    private static String settingsStatus(int account) {
        StringBuilder out=new StringBuilder();
        try {
            JSONObject config=storedConfig();
            if(config==null) out.append(tr(R.string.HmsaNoTokenStatus));
            else {
                out.append(tr(R.string.HmsaSavedTokenStatus)).append(config.optString("username","—"));
                if(config.optLong("owner")!=UserConfig.getInstance(account).getClientUserId()) {
                    out.append(tr(R.string.HmsaWrongAccountStatus));
                } else if(!config.optBoolean("inline",true)) {
                    out.append(tr(R.string.HmsaInlineDisabled));
                } else if(config.optBoolean("webhook",false)) {
                    out.append(tr(R.string.HmsaWebhookEnabled));
                } else {
                    out.append(tr(R.string.HmsaBotStatusPrefix)).append(tr(connectionStatus));
                    if(!connectionProblem.isEmpty())out.append(tr(R.string.HmsaProblemPrefix)).append(describeIssue(connectionProblem));
                }
            }
        } catch(Exception e) {out.append(tr(R.string.HmsaSecureStoreStatus)).append(describeIssue(issueKey(e)));}
        String last=prefs().getString("last_setup_issue","");
        if(!last.isEmpty())out.append(tr(R.string.HmsaLastIssuePrefix)).append(describeIssue(last));
        out.append(tr(R.string.HmsaUsageNote));
        return out.toString();
    }
    /** Validate before saving; a failing replacement must not erase the working bot. */
    private static void verifyToken(String token,long owner,TextView status,int account) {
        final int check=verification.incrementAndGet();
        status.setText(tr(R.string.HmsaChecking));
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
                prefs().edit().putString("last_setup_issue",issueKey(e)).apply();
            }
            AndroidUtilities.runOnUIThread(()->{if(check==verification.get())status.setText(settingsStatus(account));});
        },"WhisperSetup").start();
    }
    public static void showSettings(org.telegram.ui.ActionBar.BaseFragment fragment,int account) {
        Context context=fragment.getParentActivity();if(context==null)return;
        if(Build.VERSION.SDK_INT<23){alert(context,tr(R.string.HmsaAndroidRequired));return;}
        TextView status=new TextView(context);status.setTextSize(15);status.setTextColor(fragment.getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));status.setText(settingsStatus(account));
        EditText input=new EditText(context);input.setSingleLine(true);input.setHint(tr(R.string.HmsaTokenHint));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button refresh=new Button(context);refresh.setText(tr(R.string.HmsaRefresh));
        refresh.setOnClickListener(v->{
            try {
                JSONObject config=storedConfig();
                if(config==null){status.setText(settingsStatus(account));return;}
                if(config.getLong("owner")!=UserConfig.getInstance(account).getClientUserId()){
                    status.setText(settingsStatus(account));return;
                }
                verifyToken(config.getString("token"),config.getLong("owner"),status,account);
            }catch(Exception e){prefs().edit().putString("last_setup_issue",issueKey(e)).apply();status.setText(settingsStatus(account));}
        });
        CheckBox direct=new CheckBox(context);
        direct.setText(tr(R.string.HmsaDirectOption));
        direct.setTextColor(fragment.getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        direct.setChecked(prefs().getBoolean("direct_send",false));
        direct.setOnCheckedChangeListener((button,checked)->prefs().edit().putBoolean("direct_send",checked).apply());
        TextView directInfo=new TextView(context);directInfo.setTextSize(13);
        directInfo.setTextColor(fragment.getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        directInfo.setText(tr(R.string.HmsaDirectInfo));
        LinearLayout box=new LinearLayout(context);box.setOrientation(LinearLayout.VERTICAL);int pad=AndroidUtilities.dp(18);box.setPadding(pad,pad,pad,pad);
        box.addView(status);box.addView(direct);box.addView(directInfo);box.addView(refresh);box.addView(input);
        new AlertDialog.Builder(context).setTitle(tr(R.string.HmsaBotTitle)).setView(box)
            .setPositiveButton(tr(R.string.HmsaVerifySave),(d,w)->{
                String token=input.getText().toString().trim();long owner=UserConfig.getInstance(account).getClientUserId();
                if(!token.matches("[0-9]{5,20}:[A-Za-z0-9_-]{20,100}")||owner<=0){
                    prefs().edit().putString("last_setup_issue","invalid_format").apply();
                    showSettings(fragment,account);return;
                }
                // Reopen the status panel so progress and the result remain visible.
                showSettingsAndVerify(fragment,account,token,owner);
            })
            .setNegativeButton(tr(R.string.HmsaClose),null)
            .setNeutralButton(tr(R.string.HmsaDeleteToken),(d,w)->{verification.incrementAndGet();stop();prefs().edit().remove("config").remove("last_setup_issue").apply();showSettings(fragment,account);})
            .show();
        Runnable[] updater=new Runnable[1];
        updater[0]=()->{
            if(!status.isAttachedToWindow())return;
            // Don't replace the progress message while a manual check is running.
            if(!status.getText().toString().equals(tr(R.string.HmsaChecking)))status.setText(settingsStatus(account));
            AndroidUtilities.runOnUIThread(updater[0],1500);
        };
        AndroidUtilities.runOnUIThread(updater[0],1500);
    }
    private static void showSettingsAndVerify(org.telegram.ui.ActionBar.BaseFragment fragment,int account,String token,long owner) {
        Context context=fragment.getParentActivity();if(context==null)return;
        TextView status=new TextView(context);status.setTextSize(15);int pad=AndroidUtilities.dp(20);status.setPadding(pad,pad,pad,pad);
        status.setTextColor(fragment.getThemedColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        new AlertDialog.Builder(context).setTitle(tr(R.string.HmsaBotStatusTitle)).setView(status)
                .setPositiveButton(tr(R.string.HmsaBackSettings),(d,w)->showSettings(fragment,account)).show();
        verifyToken(token,owner,status,account);
    }
    private static void alert(Context c,String text){if(c!=null)new AlertDialog.Builder(c).setTitle(tr(R.string.HmsaWhisper)).setMessage(text).setPositiveButton(tr(R.string.HmsaOk),null).show();}
    public static boolean isCommand(String s){return s.matches("(?s)^/(همسه|همسة|hmsa)(?:\\s.*|$)");}
    public static boolean isUnsafeInlineDraft(String s) {
        String name=botName;
        return name!=null && s.regionMatches(true,0,"@"+name+" secret ",0,name.length()+9);
    }
    public static void warnInlineDraft(Context context) {
        alert(context,tr(R.string.HmsaUnsafeDraft));
    }
    private static final java.util.HashMap<String, SendGuard> sendingCards = new java.util.HashMap<>();
    /** Tracks this specific local message through Telegram's server acknowledgement. UI thread only. */
    private static final class SendGuard implements org.telegram.messenger.NotificationCenter.NotificationCenterDelegate {
        final int account;
        final long dialogId;
        final String key, token;
        final Context context;
        final ChatActivity fragment;
        final ChatActivityEnterView view;
        final String original;
        final org.telegram.messenger.NotificationCenter notifications;
        final AlertDialog progress;
        int outgoingId;
        boolean finished, submitted;
        final Runnable timeout = () -> finish(false, true);
        SendGuard(int account,long dialogId,String token,Context context,ChatActivity fragment,ChatActivityEnterView view,String original) {
            this.account=account;this.dialogId=dialogId;this.token=token;this.context=context;this.fragment=fragment;this.view=view;this.original=original;
            key=account+":"+dialogId;
            notifications=org.telegram.messenger.NotificationCenter.getInstance(account);
            progress=new AlertDialog.Builder(context).setMessage(tr(R.string.HmsaSending)).create();
            progress.setCancelable(false);progress.setCanceledOnTouchOutside(false);progress.setCanCancel(false);
        }
        void start() {
            sendingCards.put(key,this);
            notifications.addObserver(this,org.telegram.messenger.NotificationCenter.didReceiveNewMessages);
            notifications.addObserver(this,org.telegram.messenger.NotificationCenter.messageReceivedByServer);
            notifications.addObserver(this,org.telegram.messenger.NotificationCenter.messageSendError);
            progress.show();
            // Never trap the user indefinitely if the network goes away.
            AndroidUtilities.runOnUIThread(timeout,60000);
        }
        void finish(boolean success,boolean timedOut) {
            if(finished)return;
            finished=true;
            AndroidUtilities.cancelRunOnUIThread(timeout);
            notifications.removeObserver(this,org.telegram.messenger.NotificationCenter.didReceiveNewMessages);
            notifications.removeObserver(this,org.telegram.messenger.NotificationCenter.messageReceivedByServer);
            notifications.removeObserver(this,org.telegram.messenger.NotificationCenter.messageSendError);
            sendingCards.remove(key);
            pending.remove(token);
            try {progress.dismiss();}catch(Exception ignored) { }
            if(success) {
                if(view.getFieldText()!=null && original.contentEquals(view.getFieldText().toString().trim())) {
                    view.setFieldText("");
                    fragment.hideFieldPanel(false);
                }
            } else if(timedOut) {
                alert(context,submitted?tr(R.string.HmsaTimeoutSubmitted):tr(R.string.HmsaTimeoutPreparing));
            } else {
                alert(context,submitted?tr(R.string.HmsaSendFailed):tr(R.string.HmsaPrepareFailed));
            }
        }
        @Override public void didReceivedNotification(int id,int eventAccount,Object... args) {
            if(finished || eventAccount!=account)return;
            if(id==org.telegram.messenger.NotificationCenter.didReceiveNewMessages && args.length>1 && args[0] instanceof Long && ((Long)args[0])==dialogId && args[1] instanceof java.util.ArrayList) {
                for(Object item:(java.util.ArrayList<?>)args[1]) {
                    if(!(item instanceof MessageObject))continue;
                    MessageObject message=(MessageObject)item;
                    if(message.messageOwner.params!=null && token.equals(message.messageOwner.params.get("meero_whisper_send_token"))) {
                        outgoingId=message.getId();break;
                    }
                }
            } else if(id==org.telegram.messenger.NotificationCenter.messageReceivedByServer && outgoingId!=0 && args.length>3 && args[0] instanceof Integer && args[3] instanceof Long && ((Integer)args[0])==outgoingId && ((Long)args[3])==dialogId) {
                finish(true,false);
            } else if(id==org.telegram.messenger.NotificationCenter.messageSendError && outgoingId!=0 && args.length>0 && args[0] instanceof Integer && ((Integer)args[0])==outgoingId) {
                finish(false,false);
            }
        }
    }
    public static void send(ChatActivity fragment,ChatActivityEnterView view,int account,long dialogId,MessageObject reply,MessageObject top,ChatActivity.ReplyQuote quote,String original,boolean notify,long stars,long mono) {
        Context context=fragment.getParentActivity();if(context==null)return;
        String key=account+":"+dialogId;
        if(sendingCards.containsKey(key))return;
        String text=original.replaceFirst("(?s)^/(همسه|همسة|hmsa)\\s*","").trim();
        if(activeToken==null || botName==null || ownerId!=UserConfig.getInstance(account).getClientUserId()){alert(context,tr(R.string.HmsaConfigure));return;}
        if(reply==null || reply.isTopicMainMessage || reply.getSenderId()<=0){alert(context,tr(R.string.HmsaReplyRequired));return;}
        final boolean direct=prefs().getBoolean("direct_send",false);
        if(direct && (text.isEmpty() || text.length()>180)){alert(context,tr(R.string.HmsaDirectRequired));return;}
        if(!direct && !text.isEmpty()){alert(context,tr(R.string.HmsaCardRequired));return;}
        if(dialogId==0 || org.telegram.messenger.DialogObject.isEncryptedDialog(dialogId) || stars!=0){alert(context,tr(R.string.HmsaUnsupportedChat));return;}
        String id=randomId();
        SendGuard guard=new SendGuard(account,dialogId,id,context,fragment,view,original);
        try {
            guard.start();
            long recipient=reply.getSenderId();
            TLRPC.User targetUser=MessagesController.getInstance(account).getUser(recipient);
            String username=targetUser==null || targetUser.username==null?"":targetUser.username;
            String targetName=targetUser==null?tr(R.string.HmsaUser):org.telegram.messenger.UserObject.getUserName(targetUser);
            savePerson(recipient,username,targetName);
            JSONObject entry=new JSONObject().put("kind",direct?"direct":"card").put("recipient",recipient).put("username",username).put("name",targetName);
            if(direct) {
                long sender=UserConfig.getInstance(account).getClientUserId();
                TLRPC.User senderUser=UserConfig.getInstance(account).getCurrentUser();
                if(senderUser!=null)savePerson(sender,senderUser.username,org.telegram.messenger.UserObject.getUserName(senderUser));
                JSONObject secret=new JSONObject().put("recipient",recipient).put("sender",sender).put("text",text).put("created",System.currentTimeMillis());
                if(!prefs().edit().putString("w_"+id,encrypt(secret.toString())).commit())throw new IllegalStateException();
            }
            pending.put(id,entry);String name=botName;long owner=ownerId;
            TLRPC.TL_contacts_resolveUsername resolve=new TLRPC.TL_contacts_resolveUsername();resolve.username=name;
            ConnectionsManager.getInstance(account).sendRequest(resolve,(response,error)->AndroidUtilities.runOnUIThread(()->{
                if(guard.finished)return;
                if(owner!=ownerId || !name.equals(botName) || !(response instanceof TLRPC.TL_contacts_resolvedPeer)){guard.finish(false,false);return;}
                TLRPC.TL_contacts_resolvedPeer peer=(TLRPC.TL_contacts_resolvedPeer)response;
                if(peer.users.isEmpty()){guard.finish(false,false);return;}
                MessagesController mc=MessagesController.getInstance(account);mc.putUsers(peer.users,false);TLRPC.User bot=peer.users.get(0);
                TLRPC.TL_messages_getInlineBotResults req=new TLRPC.TL_messages_getInlineBotResults();req.bot=mc.getInputUser(bot);req.peer=mc.getInputPeer(dialogId);req.query=id;req.offset="";
                ConnectionsManager.getInstance(account).sendRequest(req,(result,err)->AndroidUtilities.runOnUIThread(()->{
                    if(guard.finished)return;
                    pending.remove(id);
                    if(owner!=ownerId || !name.equals(botName) || !(result instanceof TLRPC.TL_messages_botResults) || ((TLRPC.TL_messages_botResults)result).results.isEmpty()) {guard.finish(false,false);return;}
                    TLRPC.TL_messages_botResults results=(TLRPC.TL_messages_botResults)result;TLRPC.BotInlineResult item=results.results.get(0);item.query_id=results.query_id;
                    HashMap<String,String> params=new HashMap<>();params.put("id",item.id);params.put("query_id",Long.toString(item.query_id));params.put("bot",Long.toString(bot.id));params.put("bot_name",name);
                    params.put("meero_whisper_send_token",id);
                    try {
                        guard.submitted=true;
                        SendMessagesHelper.prepareSendingBotContextResult(fragment,org.telegram.messenger.AccountInstance.getInstance(account),item,params,dialogId,reply,top,null,quote,notify,0,0,fragment.getMessageChatSendParams(),0,mono);
                    } catch(Exception e) {guard.finish(false,false);}
                }));
            }));
        } catch(Exception e){guard.finish(false,false);}
    }

}
