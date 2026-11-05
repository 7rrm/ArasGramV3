package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCheckCell;

import app.exteraless.appearance.AppearanceConfig;

/**
 * ArasGram: настройки перенесённого из exteraless плеера (Md3Player) и
 * карточки музыки в профиле. Один экран — четыре переключателя, все ключи
 * живут в {@link AppearanceConfig} (префиксы OEAppearance / OEPlayer).
 *
 * Переключатели применяются немедленно: плеер и мини-плеер читают значения
 * при каждом открытии/обновлении, карточка профиля — при пересчёте строк.
 */
public class MeeroPlayerSettingsActivity extends BaseNekoSettingsActivity {

    private int md3Row = -1;
    private int miniRow = -1;
    private int cardRow = -1;
    private int lrclibRow = -1;

    @Override
    protected void updateRows() {
        super.updateRows();
        // Ленивая загрузка AppearanceConfig: до неё геттеры показывают дефолты.
        AppearanceConfig.ensureLoaded();
        md3Row = addRow();
        miniRow = addRow();
        cardRow = addRow();
        lrclibRow = addRow();
    }

    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroPlayer);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == md3Row) {
            AppearanceConfig.md3Player.setConfigBool(!AppearanceConfig.md3Player.Bool());
        } else if (position == miniRow) {
            AppearanceConfig.md3MiniPlayer.setConfigBool(!AppearanceConfig.md3MiniPlayer.Bool());
        } else if (position == cardRow) {
            AppearanceConfig.profileMusicCard.setConfigBool(!AppearanceConfig.profileMusicCard.Bool());
        } else if (position == lrclibRow) {
            AppearanceConfig.lrclibAllowed.setConfigBool(!AppearanceConfig.lrclibAllowed.Bool());
        } else {
            return;
        }
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
        public int getItemViewType(int position) {
            if (position == md3Row || position == miniRow || position == cardRow || position == lrclibRow) {
                return TYPE_CHECK;
            }
            return TYPE_SHADOW;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            if (holder.getItemViewType() != TYPE_CHECK) {
                return;
            }
            final TextCheckCell cell = (TextCheckCell) holder.itemView;
            if (position == md3Row) {
                cell.setTextAndCheck(getString(R.string.MeeroPlayerMd3), AppearanceConfig.md3Player.Bool(), false);
            } else if (position == miniRow) {
                cell.setTextAndCheck(getString(R.string.MeeroPlayerMini), AppearanceConfig.md3MiniPlayer.Bool(), false);
            } else if (position == cardRow) {
                cell.setTextAndCheck(getString(R.string.MeeroProfileMusicCard), AppearanceConfig.profileMusicCard.Bool(), false);
            } else if (position == lrclibRow) {
                cell.setTextAndCheck(getString(R.string.MeeroPlayerLrclib), AppearanceConfig.lrclibAllowed.Bool(), false);
            }
        }
    }
}
