package org.telegram.messenger.voip;

import android.os.SystemClock;
import android.text.TextUtils;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.voip.VoIPHelper;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;

/** One active voice-chat broadcast per process. All state changes occur on the UI thread. */
public final class VoiceChatAudioBroadcast implements NotificationCenter.NotificationCenterDelegate {
    private static final VoiceChatAudioBroadcast INSTANCE = new VoiceChatAudioBroadcast();
    public static VoiceChatAudioBroadcast getInstance() { return INSTANCE; }
    private static final long MAX_FILE_SIZE = 128L * 1024 * 1024;
    private volatile int generation;
    private int account;
    private long dialogId;
    private int messageId;
    private MessageObject message;
    private String fileName;
    private WeakReference<ChatActivity> fragment;
    private byte[] pcm;
    private int offset;
    private long deadline;
    private boolean waitingFile;
    private Runnable timer;

    private VoiceChatAudioBroadcast() { }

    public boolean isActive(MessageObject object, int currentAccount) {
        return message != null && account == currentAccount && dialogId == object.getDialogId() && messageId == object.getId();
    }

    public void toggle(ChatActivity chat, TLRPC.Chat peer, MessageObject object) {
        if (isActive(object, chat.getCurrentAccount())) { stop(); return; }
        stop();
        TLRPC.Document document = object.getDocument();
        if (peer == null || document == null || document.size <= 0 || document.size >= MAX_FILE_SIZE) {
            notifyUser(chat, R.string.VoiceBroadcastInvalid);
            return;
        }
        account = chat.getCurrentAccount();
        dialogId = object.getDialogId();
        messageId = object.getId();
        message = object;
        fragment = new WeakReference<>(chat);
        fileName = FileLoader.getAttachFileName(document);
        File path = localPath();
        if (path != null && path.isFile() && path.length() >= document.size && !FileLoader.getInstance(account).isLoadingFile(fileName)) {
            decode(path, generation);
        } else {
            waitingFile = true;
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoaded);
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileLoadFailed);
            FileLoader.getInstance(account).loadFile(document, object, FileLoader.PRIORITY_HIGH, 0);
            notifyUser(chat, R.string.VoiceBroadcastPreparing);
        }
    }

    private File localPath() {
        if (message == null) return null;
        String attach = message.messageOwner.attachPath;
        if (!TextUtils.isEmpty(attach)) {
            File f = new File(attach);
            if (f.isFile() && f.length() > 0) return f;
        }
        return FileLoader.getInstance(account).getPathToMessage(message.messageOwner);
    }

    private void decode(File path, int operation) {
        new Thread(() -> {
            byte[] result = null;
            try { result = VoiceBroadcastDecoder.decode(path.getAbsolutePath(), () -> operation != generation); }
            catch (Exception e) { FileLog.e(e); }
            final byte[] decoded = result;
            AndroidUtilities.runOnUIThread(() -> {
                if (operation != generation || message == null) return;
                ChatActivity chat = fragment != null ? fragment.get() : null;
                if (decoded == null || chat == null || chat.getParentActivity() == null) { fail(); return; }
                pcm = decoded;
                TLRPC.Chat peer = chat.getCurrentChat();
                if (peer == null || -peer.id != dialogId) { fail(); return; }
                VoIPService service = VoIPService.getSharedInstance();
                if (service == null || service.getAccount() != account || service.getChat() == null || service.getChat().id != peer.id) {
                    VoIPHelper.startCall(peer, null, null, chat.getGroupCall() == null, chat.getParentActivity(), chat, chat.getAccountInstance());
                }
                deadline = SystemClock.elapsedRealtime() + 45000;
                waitForCall(operation);
            });
        }, "VoiceBroadcastDecode").start();
    }

    private void waitForCall(int operation) {
        if (operation != generation || message == null) return;
        VoIPService call = VoIPService.getSharedInstance();
        if (call != null && call.getAccount() == account && call.getChat() != null && -call.getChat().id == dialogId && call.groupCall != null && call.getCallState() == VoIPService.STATE_ESTABLISHED) {
            offset = 0;
            deadline = SystemClock.uptimeMillis() + 100;
            sendNext(operation, call);
            return;
        }
        if (SystemClock.elapsedRealtime() >= deadline) { fail(); return; }
        timer = () -> waitForCall(operation);
        AndroidUtilities.runOnUIThread(timer, 200);
    }

    private void sendNext(int operation, VoIPService call) {
        if (operation != generation || message == null) return;
        if (call != VoIPService.getSharedInstance() || call.getCallState() != VoIPService.STATE_ESTABLISHED || call.groupCall == null) { fail(); return; }
        if (pcm == null || offset >= pcm.length) { stop(); return; }
        int end = Math.min(pcm.length, offset + 960);
        byte[] data = Arrays.copyOfRange(pcm, offset, end);
        offset = end;
        if (!call.addExternalAudioData(data)) { fail(); return; }
        deadline += 10;
        timer = () -> sendNext(operation, call);
        AndroidUtilities.runOnUIThread(timer, Math.max(0, deadline - SystemClock.uptimeMillis()));
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (!waitingFile || this.account != account || args.length == 0 || !fileName.equals(args[0])) return;
        stopObserving();
        if (id == NotificationCenter.fileLoaded) {
            File path = localPath();
            if (path != null && path.isFile() && path.length() > 0) decode(path, generation); else fail();
        } else if (id == NotificationCenter.fileLoadFailed) fail();
    }

    private void stopObserving() {
        if (!waitingFile) return;
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoaded);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileLoadFailed);
        waitingFile = false;
    }

    private void fail() {
        ChatActivity chat = fragment != null ? fragment.get() : null;
        if (chat != null) notifyUser(chat, R.string.VoiceBroadcastFailed);
        stop();
    }

    public void stop() {
        generation++;
        stopObserving();
        if (timer != null) AndroidUtilities.cancelRunOnUIThread(timer);
        timer = null;
        message = null;
        pcm = null;
        fragment = null;
        offset = 0;
    }

    private static void notifyUser(ChatActivity chat, int resource) {
        if (chat.getParentActivity() != null) Toast.makeText(chat.getParentActivity(), resource, Toast.LENGTH_LONG).show();
    }
}
