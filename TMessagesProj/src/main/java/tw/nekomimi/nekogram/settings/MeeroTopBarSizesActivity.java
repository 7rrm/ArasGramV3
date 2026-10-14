package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;

import tw.nekomimi.nekogram.MeeroTopBar;
import tw.nekomimi.nekogram.NekoConfig;

/**
 * MeeroX v235: "مقاسات الشريط العلوي" - ONE switch, no sliders.
 *
 * ON  = the measurements we tuned and shrank (title 15, status 12, pinned
 *       12/12.5, two-line music 12/11 on a 30dp bar, translate 15).
 * OFF = the conversation-start originals - as if nothing was done.
 *
 * The live header preview stays pinned above the list so both looks can
 * be compared while flipping the switch.
 */
public class MeeroTopBarSizesActivity extends BaseNekoSettingsActivity {

    private int masterRow = -1;
    private LinearLayout pinnedBox;
    private MeeroTopBarPreview previewView;

    @Override
    protected void updateRows() {
        super.updateRows();
        masterRow = addRow();
    }

    /**
     * Pin the header preview ABOVE the list so it stays on screen. The box
     * takes the listView's exact place in the fragment, so the preview
     * lands exactly where the old first row was - no inset math.
     */
    @Override
    public View createView(Context context) {
        super.createView(context);
        if (previewView == null && listView != null && listView.getParent() == fragmentView) {
            ViewGroup parent = (ViewGroup) fragmentView;
            int index = parent.indexOfChild(listView);
            parent.removeView(listView);
            pinnedBox = new LinearLayout(context);
            pinnedBox.setOrientation(LinearLayout.VERTICAL);
            previewView = new MeeroTopBarPreview(context);
            previewView.setBackgroundDrawable(null);
            pinnedBox.addView(previewView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            pinnedBox.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            parent.addView(pinnedBox, index, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return fragmentView;
    }

    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroTopBarSizes);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == masterRow) {
            // ON = our tuned measurements, OFF = the conversation-start ones
            NekoConfig.meeroTopBarSizesOn.setConfigBool(!NekoConfig.meeroTopBarSizesOn.Bool());
            MeeroTopBar.notifyChanged();
            listAdapter.notifyDataSetChanged();
        }
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
            if (position == masterRow) return TYPE_CHECK;
            return TYPE_SHADOW;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            int type = holder.getItemViewType();
            if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setTextAndCheck(getString(R.string.MeeroTopBarSizes), NekoConfig.meeroTopBarSizesOn.Bool(), false);
            }
        }
    }

    /* ---------------- live header preview ---------------- */

    private class MeeroTopBarPreview extends View implements MeeroTopBar.Listener {

        private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint captionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint linePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();

        public MeeroTopBarPreview(Context context) {
            super(context);
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            MeeroTopBar.addListener(this);
        }

        @Override
        public void onMeeroTopBarChanged() {
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            float header = Math.max(MeeroTopBar.pillDp(), MeeroTopBar.backPillDp()) + 12f + 8f;
            float total = 8f + header + 5f + 15f + MeeroTopBar.pinnedBarDp() + 5f + 15f + MeeroTopBar.musicBarDp() + 10f;
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(total));
        }

        private int glassColor() {
            // the same recipe the real chat top-panel glass uses
            return Theme.multAlpha(getThemedColor(Theme.key_chat_topPanelBackground), 0.85f);
        }

        private int glassStrokeColor() {
            int bg = getThemedColor(Theme.key_chat_topPanelBackground);
            boolean dark = (Color.red(bg) * 0.299f + Color.green(bg) * 0.587f + Color.blue(bg) * 0.114f) < 60;
            return dark ? 0x38FFFFFF : 0x1F000000;
        }

        private void drawCapsule(Canvas canvas, RectF r, float radius) {
            fillPaint.setColor(glassColor());
            canvas.drawRoundRect(r, radius, radius, fillPaint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1f));
            strokePaint.setColor(glassStrokeColor());
            canvas.drawRoundRect(r, radius, radius, strokePaint);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final float w = getWidth();
            final float side = dp(12f);

            final int titleColor = getThemedColor(Theme.key_actionBarDefaultTitle);
            final int subColor = getThemedColor(Theme.key_actionBarDefaultSubtitle);

            // ---------- header bar ----------
            float pillH = dp(MeeroTopBar.pillDp() + 12f);
            float backH = dp(MeeroTopBar.backPillDp() + 12f);
            float headerH = Math.max(pillH, backH) + dp(6f);
            float cy = dp(8f) + headerH / 2f;

            // back capsule + chevron
            rect.set(side, cy - backH / 2f, side + backH, cy + backH / 2f);
            drawCapsule(canvas, rect, backH / 2f);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeWidth(dp(2.2f));
            strokePaint.setColor(titleColor);
            float cx = side + backH / 2f;
            path.reset();
            path.moveTo(cx + dp(4.5f), cy - dp(13f) / 2f);
            path.lineTo(cx - dp(4.5f) * 0.6f, cy);
            path.lineTo(cx + dp(4.5f), cy + dp(13f) / 2f);
            canvas.drawPath(path, strokePaint);

            // name+status capsule
            float pillX0 = side + backH + dp(6f);
            rect.set(pillX0, cy - pillH / 2f, w - side, cy + pillH / 2f);
            drawCapsule(canvas, rect, pillH / 2f);

            // avatar + its oval
            float oval = dp(MeeroTopBar.avatarDp() + 16f);
            float ovalX0 = pillX0 + dp(2f);
            rect.set(ovalX0, cy - oval / 2f, ovalX0 + oval, cy + oval / 2f);
            fillPaint.setColor(glassColor());
            canvas.drawRoundRect(rect, dp(23f), dp(23f), fillPaint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1f));
            strokePaint.setColor(glassStrokeColor());
            canvas.drawRoundRect(rect, dp(23f), dp(23f), strokePaint);

            float avatar = dp(MeeroTopBar.avatarDp());
            float acx = ovalX0 + oval / 2f;
            glyphPaint.setColor(subColor);
            canvas.drawCircle(acx, cy - avatar * 0.12f, avatar / 4.6f, glyphPaint);
            rect.set(acx - avatar * 0.32f, cy + avatar * 0.10f, acx + avatar * 0.32f, cy + avatar * 0.52f);
            canvas.drawOval(rect, glyphPaint);

            // name + status
            titlePaint.setTextSize(dp(MeeroTopBar.titleDp()));
            titlePaint.setTypeface(AndroidUtilities.bold());
            titlePaint.setColor(titleColor);
            subPaint.setTextSize(dp(MeeroTopBar.statusDp()));
            subPaint.setTypeface(Typeface.DEFAULT);
            subPaint.setColor(subColor);
            float tx = ovalX0 + oval + dp(7f);
            canvas.drawText(getString(R.string.MeeroTopBarPreviewChatName), tx, cy - dp(2f), titlePaint);
            canvas.drawText(getString(R.string.MeeroTopBarPreviewStatus), tx, cy + dp(3f) - subPaint.ascent(), subPaint);

            // ---------- pinned strip ----------
            float py = dp(8f) + headerH + dp(5f);
            captionPaint.setTextSize(dp(10f));
            captionPaint.setColor(subColor);
            canvas.drawText(getString(R.string.MeeroTopBarPreviewPinned), side, py + dp(10f), captionPaint);
            float pBarY = py + dp(15f);
            float pBarH = dp(MeeroTopBar.pinnedBarDp());
            rect.set(side, pBarY, w - side, pBarY + pBarH);
            drawCapsule(canvas, rect, Math.min(dp(18f), pBarH / 2f));
            linePaint.setColor(titleColor);
            linePaint.setTypeface(AndroidUtilities.bold());
            linePaint.setTextSize(dp(MeeroTopBar.pinnedCounterDp()));
            canvas.drawText(getString(R.string.MeeroTopBarPreviewPinnedMessage), side + dp(23f), pBarY + pBarH * 0.45f, linePaint);
            linePaint.setTypeface(Typeface.DEFAULT);
            linePaint.setColor(subColor);
            linePaint.setTextSize(dp(MeeroTopBar.pinnedTextDp()));
            canvas.drawText(getString(R.string.MeeroTopBarPreviewPinnedText), side + dp(23f), pBarY + pBarH * 0.85f, linePaint);

            // ---------- music bar ----------
            float my = pBarY + pBarH + dp(5f);
            canvas.drawText(getString(R.string.MeeroTopBarPreviewMusic), side, my + dp(10f), captionPaint);
            float mBarY = my + dp(15f);
            float mBarH = dp(MeeroTopBar.musicBarDp());
            rect.set(side, mBarY, w - side, mBarY + mBarH);
            drawCapsule(canvas, rect, Math.min(dp(18f), mBarH / 2f));

            // play triangle
            float ps = dp(MeeroTopBar.musicPlayDp()) * 0.55f;
            float mcy = mBarY + mBarH / 2f;
            path.reset();
            path.moveTo(side + dp(6f), mcy - ps / 2f);
            path.lineTo(side + dp(6f), mcy + ps / 2f);
            path.lineTo(side + dp(6f) + ps * 0.85f, mcy);
            path.close();
            glyphPaint.setColor(titleColor);
            canvas.drawPath(path, glyphPaint);

            if (MeeroTopBar.musicTwoLine()) {
                linePaint.setColor(titleColor);
                linePaint.setTypeface(Typeface.DEFAULT);
                linePaint.setTextSize(dp(MeeroTopBar.musicTitleDp()));
                String t1 = getString(R.string.MeeroTopBarPreviewSongTitle);
                String t2 = getString(R.string.MeeroTopBarPreviewArtist);
                float tw1 = linePaint.measureText(t1);
                float centerX = (side + dp(6f) + ps + w - side) / 2f;
                canvas.drawText(t1, centerX - tw1 / 2f, mBarY + mBarH * 0.44f, linePaint);
                linePaint.setColor(subColor);
                linePaint.setTextSize(dp(MeeroTopBar.musicSubDp()));
                float tw2 = linePaint.measureText(t2);
                canvas.drawText(t2, centerX - tw2 / 2f, mBarY + mBarH * 0.88f, linePaint);
            } else {
                linePaint.setColor(titleColor);
                linePaint.setTypeface(Typeface.DEFAULT);
                linePaint.setTextSize(dp(15f));
                canvas.drawText(String.format("%s - %s", getString(R.string.MeeroTopBarPreviewArtist), getString(R.string.MeeroTopBarPreviewSongTitle)), side + dp(6f) + ps + dp(6f), mcy - (linePaint.ascent() + linePaint.descent()) / 2f, linePaint);
            }
        }
    }
}
