package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.widget.LinearLayout;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.QuoteSpan;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ChatActivity;
import android.view.ActionMode;
import android.view.Menu;
import tw.nekomimi.nekogram.autopost.AutoPost;
import java.util.ArrayList;

/** Explicit, user-started in-process auto posting; no background restart. */
public class MeeroAutoPostActivity extends BaseNekoSettingsActivity {
    private final int page; // 0=hub, 1=selected, 2=global
    private int modeStart, chats, messages, delay, chatDelay, privacy, repeat, rounds, chatStart, chatEnd, msgStart, msgEnd, addMsg;
    private final ArrayList<Long> ids=new ArrayList<>();
    private final ArrayList<AutoPost.PostMessage> texts=new ArrayList<>();
    public MeeroAutoPostActivity(){this(0);}
    private MeeroAutoPostActivity(int page){this.page=page;}
    private boolean global(){return page==2;}
    private SharedPreferences prefs(){return AutoPost.prefs(currentAccount,global());}
    @Override protected String getActionBarTitle(){return getString(page==1?R.string.AutoPostSelected:page==2?R.string.AutoPostGlobal:R.string.AutoPostTitle);}
    @Override protected BaseListAdapter createAdapter(Context c){return new Adapter(c);}
    @Override protected void updateRows(){
        super.updateRows(); ids.clear(); texts.clear();
        if(page==0){modeStart=addRow();addRow();return;}
        if(!global())ids.addAll(AutoPost.selected(currentAccount));
        texts.addAll(AutoPost.messages(currentAccount,global()));
        modeStart=addRow(); chats=global()?-1:addRow();
        chatStart=rowCount; for(long ignored:ids)addRow(); chatEnd=rowCount;
        privacy=global()?addRow():-1; messages=addRow(); addMsg=addRow();
        msgStart=rowCount; for(AutoPost.PostMessage ignored:texts)addRow(); msgEnd=rowCount;
        delay=addRow(); chatDelay=addRow(); repeat=addRow(); rounds=prefs().getInt("repeat",0)==1?addRow():-1;
    }
    private void refresh(){updateRows(); if(listAdapter!=null)listAdapter.notifyDataSetChanged();}
    private String s(int res){return getString(res);}
    @Override public void onResume(){super.onResume();if(listAdapter!=null)refresh();}
    @Override protected void onItemClick(View view,int position,float x,float y){
        if(page==0){presentFragment(new MeeroAutoPostActivity(position==modeStart?1:2));return;}
        if(position==modeStart){
            if(AutoPost.isRunning(currentAccount)){AutoPost.stop(currentAccount);refresh();return;}
            new AlertDialog.Builder(getParentActivity()).setTitle(s(R.string.AutoPostStart)).setMessage(s(R.string.AutoPostStartWarning))
                .setPositiveButton(s(R.string.AutoPostStart),(d,w)->{int count=AutoPost.start(currentAccount,global()); if(count==0) new AlertDialog.Builder(getParentActivity()).setMessage(s(R.string.AutoPostNothing)).setPositiveButton(s(R.string.OK),null).show();refresh();})
                .setNegativeButton(s(R.string.Cancel),null).show();
        } else if(position==chats){pickChat();}
        else if(position>=chatStart&&position<chatEnd){long id=ids.get(position-chatStart); new AlertDialog.Builder(getParentActivity()).setMessage(s(R.string.AutoPostRemoveChat)).setPositiveButton(s(R.string.Delete),(d,w)->{ids.remove(id);AutoPost.saveSelected(currentAccount,ids);refresh();}).setNegativeButton(s(R.string.Cancel),null).show();}
        else if(position==privacy){prefs().edit().putBoolean("exclude_private",!prefs().getBoolean("exclude_private",true)).apply();refresh();}
        else if(position==addMsg){if(texts.size()<10)editText(-1); else alert(s(R.string.AutoPostMaxMessages));}
        else if(position>=msgStart&&position<msgEnd){int i=position-msgStart;new AlertDialog.Builder(getParentActivity()).setItems(new CharSequence[]{s(R.string.Edit),s(R.string.Delete)},(d,w)->{if(w==0)editText(i);else {texts.remove(i);AutoPost.saveMessages(currentAccount,global(),texts);refresh();}}).show();}
        else if(position==delay){number("message_delay",10,s(R.string.AutoPostMessageDelay));}
        else if(position==chatDelay){number("dialog_delay",30,s(R.string.AutoPostChatDelay));}
        else if(position==rounds){number("rounds",2,s(R.string.AutoPostRounds));}
        else if(position==repeat){new AlertDialog.Builder(getParentActivity()).setTitle(s(R.string.AutoPostRepeat)).setItems(new CharSequence[]{s(R.string.AutoPostOnce),s(R.string.AutoPostCount),s(R.string.AutoPostContinuous)},(d,w)->{prefs().edit().putInt("repeat",w).apply();refresh();}).show();}
    }
    private void alert(String text){new AlertDialog.Builder(getParentActivity()).setMessage(text).setPositiveButton(s(R.string.OK),null).show();}
    private void number(String key,int defaultValue,String title){
        EditText e=new EditText(getParentActivity());e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setText(String.valueOf(prefs().getInt(key,defaultValue)));
        new AlertDialog.Builder(getParentActivity()).setTitle(title).setView(e).setPositiveButton(s(R.string.Save),(d,w)->{
            int n;try{n=Integer.parseInt(e.getText().toString());}catch(Exception ex){alert(s(R.string.AutoPostInvalid));return;}
            if(n<0||n>86400||key.equals("rounds")&&n<1){alert(s(R.string.AutoPostInvalid));return;}
            Runnable save=()->{prefs().edit().putInt(key,n).apply();refresh();};
            if(key.equals("message_delay")&&n<7)new AlertDialog.Builder(getParentActivity()).setMessage(s(R.string.AutoPostRisk)).setPositiveButton(s(R.string.OK),(di,wi)->save.run()).setNegativeButton(s(R.string.Cancel),null).show();else save.run();
        }).setNegativeButton(s(R.string.Cancel),null).show();
    }
    private void editText(int index){
        Context context=getParentActivity();
        if(context==null)return;
        EditTextCaption editor=new EditTextCaption(context,resourcesProvider) {
            @Override protected void extendActionMode(ActionMode mode, Menu menu) {
                if (getSelectionStart() != getSelectionEnd() && menu.findItem(android.R.id.copy) != null) {
                    ChatActivity.fillActionModeMenu(menu, null, true, true);
                }
            }
        };
        editor.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setMinLines(3); editor.setMaxLines(8);
        editor.setAllowTextEntitiesIntersection(true);
        if(index>=0){
            AutoPost.PostMessage post=texts.get(index);
            SpannableStringBuilder value=new SpannableStringBuilder(post.text);
            MessageObject.addEntitiesToText(value,post.entities,false,false,false,false);
            Spannable withEmoji=MessageObject.replaceAnimatedEmoji(value,post.entities,editor.getPaint().getFontMetricsInt());
            editor.setText(withEmoji);
            QuoteSpan.normalizeQuotes(editor.getText());
            editor.setSelection(editor.length());
        }
        LinearLayout layout=new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL);
        int inset=AndroidUtilities.dp(16); layout.setPadding(inset,0,inset,0);
        layout.addView(editor,new LinearLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(context).setTitle(s(R.string.AutoPostMessage)).setView(layout).setPositiveButton(s(R.string.Save),(d,w)->{
            SpannableStringBuilder editableCopy=new SpannableStringBuilder(editor.getText());
            QuoteSpan.normalizeQuotes((Spannable)editableCopy);
            CharSequence[] input={AndroidUtilities.getTrimmedString(editableCopy)};
            ArrayList<TLRPC.MessageEntity> entities=MediaDataController.getInstance(currentAccount).getEntities(input,true,false);
            String text=input[0].toString();
            if(text.trim().isEmpty())return;
            AutoPost.PostMessage item=new AutoPost.PostMessage(text,entities);
            if(index<0)texts.add(item);else texts.set(index,item);
            AutoPost.saveMessages(currentAccount,global(),texts);refresh();
        }).setNegativeButton(s(R.string.Cancel),null).show();
    }
    private void pickChat(){Bundle b=new Bundle();b.putBoolean("onlySelect",true);b.putBoolean("allowGlobalSearch",false);b.putBoolean("checkCanWrite",true);DialogsActivity f=new DialogsActivity(b);
        f.setDelegate((fragment,dids,message,param,notify,scheduleDate,scheduleRepeatPeriod,topicsFragment)->{if(dids==null||dids.isEmpty())return false;long id=dids.get(0).dialogId;if(parentLayout!=null)parentLayout.removeFragmentFromStack(fragment,true);if(!AutoPost.eligible(currentAccount,id,false)){alert(s(R.string.AutoPostIneligible));return true;}if(!ids.contains(id)){ids.add(id);AutoPost.saveSelected(currentAccount,ids);}refresh();return true;});presentFragment(f);}
    private String name(long id){MessagesController mc=MessagesController.getInstance(currentAccount);if(id>0){TLRPC.User u=mc.getUser(id);return u==null?Long.toString(id):UserObject.getUserName(u);}TLRPC.Chat c=mc.getChat(-id);return c==null?Long.toString(id):c.title;}
    private class Adapter extends BaseListAdapter {
        Adapter(Context c){super(c);}
        @Override public boolean isEnabled(RecyclerView.ViewHolder h){return true;}
        @Override public int getItemViewType(int p){return TYPE_TEXT;}
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h,int pos,boolean payload){
            TextCell cell=(TextCell)h.itemView;String title="",value="";
            if(page==0)title=s(pos==modeStart?R.string.AutoPostSelected:R.string.AutoPostGlobal);
            else if(pos==modeStart)title=s(AutoPost.isRunning(currentAccount)?R.string.AutoPostStop:R.string.AutoPostStart);
            else if(pos==chats)title=s(R.string.AutoPostAddChat);
            else if(pos>=chatStart&&pos<chatEnd)title=name(ids.get(pos-chatStart));
            else if(pos==privacy){title=s(R.string.AutoPostExcludePrivate);value=s(prefs().getBoolean("exclude_private",true)?R.string.AutoPostOn:R.string.AutoPostOff);}
            else if(pos==messages){title=s(R.string.AutoPostMessages);value=texts.size()+" / 10";}
            else if(pos==addMsg)title=s(R.string.AutoPostAddMessage);
            else if(pos>=msgStart&&pos<msgEnd)title=texts.get(pos-msgStart).text.replace('\n',' ');
            else if(pos==delay){title=s(R.string.AutoPostMessageDelay);value=String.valueOf(prefs().getInt("message_delay",10));}
            else if(pos==chatDelay){title=s(R.string.AutoPostChatDelay);value=String.valueOf(prefs().getInt("dialog_delay",30));}
            else if(pos==repeat){title=s(R.string.AutoPostRepeat);int r=prefs().getInt("repeat",0);value=s(r==1?R.string.AutoPostCount:r==2?R.string.AutoPostContinuous:R.string.AutoPostOnce);}
            else if(pos==rounds){title=s(R.string.AutoPostRounds);value=String.valueOf(prefs().getInt("rounds",2));}
            cell.setTextAndValue(title,value,true);
        }
    }
}
