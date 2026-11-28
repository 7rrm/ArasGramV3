package tw.nekomimi.nekogram.settings;

import android.content.Context;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCell;
import tw.nekomimi.nekogram.MeeroWhisper;

/** Personal in-process bot features. */
public class MeeroUserBotActivity extends BaseNekoSettingsActivity {
    private int whisperRow;
    @Override protected String getActionBarTitle() { return "يوزر بوت"; }
    @Override protected void updateRows() { super.updateRows(); whisperRow=addRow(); }
    @Override protected BaseListAdapter createAdapter(Context c) { return new Adapter(c); }
    @Override protected void onItemClick(View view,int position,float x,float y) {
        if(position==whisperRow) MeeroWhisper.showSettings(this,currentAccount);
    }
    private class Adapter extends BaseListAdapter {
        Adapter(Context context) { super(context); }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public int getItemViewType(int position) { return TYPE_TEXT; }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,int position,boolean payload) {
            ((TextCell)holder.itemView).setTextAndIcon("الهمسة",R.drawable.input_reply_solar,false);
        }
    }
}
