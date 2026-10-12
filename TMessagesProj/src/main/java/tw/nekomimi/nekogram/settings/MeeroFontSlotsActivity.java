package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextSettingsCell;

import tw.nekomimi.nekogram.MeeroFonts;
import tw.nekomimi.nekogram.NekoConfig;

/**
 * MeeroX v231: font slots screen - four rows (regular / bold / italic /
 * mono); each opens a picker so every style can carry its own face.
 * The send-text-style row lives here too (kept from the old Fonts page).
 */
public class MeeroFontSlotsActivity extends BaseNekoSettingsActivity {

    private static final String[] SLOTS = {
            MeeroFonts.SLOT_REGULAR,
            MeeroFonts.SLOT_BOLD,
            MeeroFonts.SLOT_ITALIC,
            MeeroFonts.SLOT_MONO
    };

    private int slotsStartRow;
    private int slotsEndRow;
    private int dividerRow;
    private int sendStyleRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        slotsStartRow = rowCount;
        for (int i = 0; i < SLOTS.length; i++) {
            addRow();
        }
        slotsEndRow = rowCount;
        dividerRow = addRow();
        sendStyleRow = addRow();
    }

    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return "الخط";
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position >= slotsStartRow && position < slotsEndRow) {
            presentFragment(new MeeroFontPickerActivity(SLOTS[position - slotsStartRow]));
        } else if (position == sendStyleRow) {
            showMeeroSendStyleDialog();
        }
    }

    // Kept verbatim from the old Fonts page so send-formatting stays put.
    private static final int[] MEERO_STYLE_RES = {
            R.string.MeeroStyleDefault,
            R.string.MeeroStyleBold,
            R.string.MeeroStyleItalic,
            R.string.MeeroStyleUnderline,
            R.string.MeeroStyleStrike,
            R.string.MeeroStyleSpoiler,
            R.string.MeeroStyleQuote,
            R.string.MeeroStyleMono,
            R.string.MeeroStyleCode
    };

    private int currentMeeroSendStyle() {
        try {
            final int v = NekoConfig.meeroSendTextStyle.Int();
            return v >= 0 && v < MEERO_STYLE_RES.length ? v : 0;
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private void showMeeroSendStyleDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final int current = currentMeeroSendStyle();
        final String[] names = new String[MEERO_STYLE_RES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = (i == current ? "✓ " : "") + getString(MEERO_STYLE_RES[i]);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(getString(R.string.MeeroSendStyleTitle));
        b.setItems(names, (d, which) -> {
            NekoConfig.meeroSendTextStyle.setConfigInt(which);
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(sendStyleRow);
            }
        });
        showDialog(b.create());
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            int type = holder.getItemViewType();
            if (type == TYPE_SETTINGS) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (position >= slotsStartRow && position < slotsEndRow) {
                    String slot = SLOTS[position - slotsStartRow];
                    cell.setTextAndValue(MeeroFonts.slotTitle(slot),
                            MeeroFonts.displayNameOf(MeeroFonts.getSlot(slot)),
                            position < slotsEndRow - 1);
                } else if (position == sendStyleRow) {
                    cell.setTextAndValue(getString(R.string.MeeroSendStyleTitle),
                            getString(MEERO_STYLE_RES[currentMeeroSendStyle()]), false);
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= slotsStartRow && position < slotsEndRow) return TYPE_SETTINGS;
            if (position == sendStyleRow) return TYPE_SETTINGS;
            return TYPE_SHADOW;
        }
    }
}
