package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import tw.nekomimi.nekogram.MeeroWhisper;

/** Whisper controls as a normal settings page, not a crowded dialog. */
public class MeeroWhisperSettingsActivity extends BaseNekoSettingsActivity {
    private int enableRow,directRow,nameRow,botRow,statusRow,problemRow,refreshRow,tokenRow,noteRow;
    private boolean resumed,checking;
    private String tr(int id){return LocaleController.getString(id);}
    @Override protected String getActionBarTitle(){return tr(R.string.HmsaWhisper);}
    @Override protected boolean meeroGlassScreen(){return true;}
    @Override protected void updateRows(){super.updateRows();enableRow=addRow();directRow=addRow();nameRow=addRow();botRow=addRow();statusRow=addRow();problemRow=addRow();refreshRow=addRow();tokenRow=addRow();noteRow=addRow();}
    @Override protected BaseListAdapter createAdapter(Context context){return new Adapter(context);}
    private void redraw(){if(listAdapter!=null)listAdapter.notifyDataSetChanged();}
    private final Runnable refreshState=new Runnable(){@Override public void run(){if(!resumed)return;redraw();AndroidUtilities.runOnUIThread(this,2500);}};
    @Override public void onResume(){super.onResume();resumed=true;AndroidUtilities.cancelRunOnUIThread(refreshState);AndroidUtilities.runOnUIThread(refreshState,2500);}
    @Override public void onPause(){resumed=false;AndroidUtilities.cancelRunOnUIThread(refreshState);super.onPause();}
    private void verify(String token){if(checking)return;checking=true;redraw();MeeroWhisper.verifyFromPage(token,currentAccount,()->{checking=false;redraw();});}
    private void tokenDialog(){
        Context c=getParentActivity();if(c==null)return;
        EditText input=new EditText(c);input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setHint(tr(R.string.HmsaTokenHint));
        AlertDialog.Builder b=new AlertDialog.Builder(c).setTitle(tr(R.string.HmsaTokenLabel)).setView(input)
            .setPositiveButton(tr(R.string.HmsaVerifySave),(d,w)->verify(input.getText().toString()))
            .setNegativeButton(tr(R.string.HmsaClose),null);
        if(MeeroWhisper.hasToken())b.setNeutralButton(tr(R.string.HmsaDeleteToken),(d,w)->{MeeroWhisper.removeToken();redraw();});
        showDialog(b.create());
    }
    @Override protected void onItemClick(View view,int pos,float x,float y){
        if(pos==enableRow){MeeroWhisper.setEnabled(!MeeroWhisper.isEnabled());redraw();}
        else if(pos==directRow){MeeroWhisper.setDirect(!MeeroWhisper.isDirect());redraw();}
        else if(pos==refreshRow){verify("");}
        else if(pos==tokenRow){tokenDialog();}
    }
    private class Adapter extends BaseListAdapter {
        Adapter(Context c){super(c);}
        @Override public int getItemViewType(int p){return p==enableRow||p==directRow?TYPE_CHECK:p==problemRow?TYPE_DETAIL_SETTINGS:p==noteRow?TYPE_INFO_PRIVACY:TYPE_SETTINGS;}
        @Override public boolean isEnabled(RecyclerView.ViewHolder h){int p=h.getAdapterPosition();return p==enableRow||p==directRow||p==refreshRow||p==tokenRow;}
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h,int p,boolean partial){
            if(p==enableRow||p==directRow){((TextCheckCell)h.itemView).setTextAndCheck(tr(p==enableRow?R.string.HmsaEnable:R.string.HmsaDirectOption),p==enableRow?MeeroWhisper.isEnabled():MeeroWhisper.isDirect(),true);return;}
            if(p==problemRow){((TextDetailSettingsCell)h.itemView).setTextAndValue(tr(R.string.HmsaProblemTitle),MeeroWhisper.displayProblem(currentAccount),true);return;}
            if(p==noteRow){((TextInfoPrivacyCell)h.itemView).setText(tr(R.string.HmsaPageNote));return;}
            TextSettingsCell c=(TextSettingsCell)h.itemView;
            if(p==nameRow)c.setTextAndValue(tr(R.string.HmsaName),MeeroWhisper.savedBotName(),true);
            else if(p==botRow)c.setTextAndValue(tr(R.string.HmsaBot),MeeroWhisper.savedBotUser(),true);
            else if(p==statusRow)c.setTextAndValue(tr(R.string.HmsaStatus),checking?tr(R.string.HmsaChecking):MeeroWhisper.displayStatus(currentAccount),true);
            else if(p==refreshRow)c.setText(tr(R.string.HmsaRefresh),true);
            else if(p==tokenRow)c.setTextAndValue(tr(R.string.HmsaTokenLabel),MeeroWhisper.hasToken()?tr(R.string.HmsaTokenSaved):tr(R.string.HmsaNone),false);
        }
    }
}
