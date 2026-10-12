package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;

import java.io.InputStream;
import java.util.ArrayList;

import tw.nekomimi.nekogram.MeeroFonts;
import tw.nekomimi.nekogram.MeeroStrings;

/**
 * MeeroX v231: per-slot font picker. Rows: from-files import, device font,
 * app default, then the bundled + user-added faces (each previews itself).
 */
public class MeeroFontPickerActivity extends BaseNekoSettingsActivity {

    private static final int PICK_FONT = 4712;

    private final String slot;
    private final ArrayList<MeeroFonts.Option> options = new ArrayList<>();

    private int addFontRow;
    private int deviceRow;
    private int defaultRow;
    private int dividerRow;
    private int fontsStartRow;
    private int fontsEndRow;

    public MeeroFontPickerActivity(String slot) {
        this.slot = slot;
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        addFontRow = addRow();
        deviceRow = addRow();
        defaultRow = addRow();
        dividerRow = addRow();

        options.clear();
        for (MeeroFonts.Option o : MeeroFonts.getOptions()) {
            if (!MeeroFonts.DEFAULT.equals(o.id)) {
                options.add(o);
            }
        }
        fontsStartRow = rowCount;
        for (int i = 0; i < options.size(); i++) {
            addRow();
        }
        fontsEndRow = rowCount;
    }

    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return MeeroFonts.slotTitle(slot);
    }

    private void apply(String id) {
        MeeroFonts.setSlot(slot, id);
        AndroidUtilities.clearTypefaceCache();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (parentLayout != null) {
            parentLayout.rebuildAllFragmentViews(true, true);
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == addFontRow) {
            pickFont();
        } else if (position == deviceRow) {
            apply(MeeroFonts.DEVICE);
        } else if (position == defaultRow) {
            apply(MeeroFonts.DEFAULT);
        } else if (position >= fontsStartRow && position < fontsEndRow) {
            apply(options.get(position - fontsStartRow).id);
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (position >= fontsStartRow && position < fontsEndRow) {
            MeeroFonts.Option o = options.get(position - fontsStartRow);
            if (o.id.startsWith(MeeroFonts.CUSTOM_PREFIX) && getParentActivity() != null) {
                AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                b.setTitle(o.title);
                b.setMessage(MeeroStrings.s(95));
                b.setPositiveButton(getString(R.string.Delete), (d, w) -> {
                    MeeroFonts.deleteCustom(o.id);
                    AndroidUtilities.clearTypefaceCache();
                    updateRows();
                    listAdapter.notifyDataSetChanged();
                });
                b.setNegativeButton(getString(R.string.Cancel), null);
                showDialog(b.create());
                return true;
            }
        }
        return false;
    }

    private void pickFont() {
        if (getParentActivity() == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(
                    Intent.createChooser(i, MeeroStrings.s(96)), PICK_FONT);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private String nameOf(Uri uri) {
        try {
            Cursor c = ApplicationLoader.applicationContext.getContentResolver()
                    .query(uri, null, null, null, null);
            if (c != null) {
                try {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0 && c.moveToFirst()) return c.getString(idx);
                } finally {
                    c.close();
                }
            }
        } catch (Throwable ignore) {}
        String p = uri.getLastPathSegment();
        if (p != null) {
            int s = p.lastIndexOf('/');
            return s >= 0 ? p.substring(s + 1) : p;
        }
        return "font.ttf";
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_FONT || resultCode != Activity.RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;

        String id = null;
        try (InputStream in = ApplicationLoader.applicationContext
                .getContentResolver().openInputStream(uri)) {
            if (in != null) {
                id = MeeroFonts.importFont(in, nameOf(uri));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }

        if (id == null) {
            if (getParentActivity() != null) {
                AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                b.setTitle("MeeroX");
                b.setMessage(MeeroStrings.s(94));
                b.setPositiveButton(getString(R.string.OK), null);
                showDialog(b.create());
            }
            return;
        }

        apply(id);
        updateRows();
        listAdapter.notifyDataSetChanged();
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
            String current = MeeroFonts.getSlot(slot);
            if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                if (position == deviceRow) {
                    cell.setTextAndCheck("خط الجهاز", MeeroFonts.DEVICE.equals(current), true);
                    cell.setTypeface(Typeface.DEFAULT);
                } else if (position == defaultRow) {
                    cell.setTextAndCheck("الافتراضي", MeeroFonts.DEFAULT.equals(current), true);
                    cell.setTypeface(Typeface.DEFAULT);
                } else {
                    MeeroFonts.Option o = options.get(position - fontsStartRow);
                    cell.setTextAndCheck(o.title, o.id.equals(current), position < fontsEndRow - 1);
                    Typeface tf = MeeroFonts.previewOf(o.id);
                    cell.setTypeface(tf != null ? tf : Typeface.DEFAULT);
                }
            } else if (type == TYPE_SETTINGS) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setText("من الملفات", false);
                cell.getTextView().setTypeface(Typeface.DEFAULT);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == addFontRow) return TYPE_SETTINGS;
            if (position == deviceRow || position == defaultRow) return TYPE_CHECK;
            if (position >= fontsStartRow && position < fontsEndRow) return TYPE_CHECK;
            return TYPE_SHADOW;
        }
    }
}
