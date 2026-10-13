package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
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
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

import tw.nekomimi.nekogram.MeeroTopBar;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;

/**
 * MeeroX v233: "مقاسات وحجم خطوط الشريط العلوي" - per-element sizing for the
 * chat top bar, with a live header preview drawn right in the settings.
 *
 * Master switch off (or the reset row) = the conversation-start originals,
 * as if this session's tuning never happened. On = the MeeroX-tuned sizes
 * as the 100% baseline, every element scalable 60%-160%.
 */
public class MeeroTopBarSizesActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_PREVIEW = 100;
    private static final int TYPE_SLIDER = 101;
    private static final int SLIDERS = 7;
    private static final int MIN_SCALE = 60;
    private static final int MAX_SCALE = 160;

    private static final String[] TITLES = {
            "الصورة (مع الدائرة اللي خلفها)",
            "اسم المحادثة",
            "الحالة",
            "كبسولة الاسم والحالة",
            "زر الرجوع (الأيقونة والكبسولة)",
            "خطوط التثبيت",
            "خطوط الموسيقى",
    };

    private static final ConfigItem[] ITEMS = {
            NekoConfig.meeroTopBarAvatarScale,
            NekoConfig.meeroTopBarTitleScale,
            NekoConfig.meeroTopBarStatusScale,
            NekoConfig.meeroTopBarCapsuleScale,
            NekoConfig.meeroTopBarBackScale,
            NekoConfig.meeroTopBarPinnedScale,
            NekoConfig.meeroTopBarMusicScale,
    };

    private int masterRow = -1;
    private int previewRow = -1;
    private int sliderStart = -1;
    private int resetRow = -1;

    @Override
    protected void updateRows() {
        super.updateRows();
        masterRow = addRow();
        if (NekoConfig.meeroTopBarSizesOn.Bool()) {
            previewRow = addRow();
            sliderStart = rowCount;
            for (int i = 0; i < SLIDERS; i++) {
                addRow();
            }
            resetRow = addRow();
        }
    }

    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return "مقاسات وحجم خطوط الشريط العلوي";
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == masterRow) {
            NekoConfig.meeroTopBarSizesOn.setConfigBool(!NekoConfig.meeroTopBarSizesOn.Bool());
            MeeroTopBar.notifyChanged();
            updateRows();
            listAdapter.notifyDataSetChanged();
        } else if (position == resetRow) {
            // back to the conversation-start originals: switch off + clean scales
            NekoConfig.meeroTopBarSizesOn.setConfigBool(false);
            for (ConfigItem item : ITEMS) {
                item.setConfigInt(100);
            }
            MeeroTopBar.notifyChanged();
            updateRows();
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
            if (position == previewRow) return TYPE_PREVIEW;
            if (position >= sliderStart && position < sliderStart + SLIDERS) return TYPE_SLIDER;
            if (position == resetRow) return TYPE_SETTINGS;
            return TYPE_SHADOW;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_PREVIEW) {
                return new RecyclerView.ViewHolder(new MeeroTopBarPreview(parent.getContext())) {
                };
            }
            if (viewType == TYPE_SLIDER) {
                return new RecyclerView.ViewHolder(new MeeroSliderCell(parent.getContext())) {
                };
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            int type = holder.getItemViewType();
            if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setTextAndCheck("مقاسات وحجم خطوط الشريط العلوي", NekoConfig.meeroTopBarSizesOn.Bool(), false);
            } else if (type == TYPE_SLIDER) {
                MeeroSliderCell cell = (MeeroSliderCell) holder.itemView;
                cell.bind(position - sliderStart);
            } else if (type == TYPE_SETTINGS) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setText("رجع المقاسات الأصلية", false);
            }
        }
    }

    /* ---------------- slider row ---------------- */

    private class MeeroSliderCell extends FrameLayout {

        private final TextView titleView;
        private final TextView valueView;
        private final SeekBar seekBar;
        private int index;

        public MeeroSliderCell(Context context) {
            super(context);
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 21, 13, 21, 0));

            valueView = new TextView(context);
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            valueView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
            addView(valueView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 21, 13, 21, 0));

            seekBar = new SeekBar(context);
            seekBar.setMax(MAX_SCALE - MIN_SCALE);
            int accent = getThemedColor(Theme.key_windowBackgroundWhiteBlueText);
            seekBar.setProgressTintList(ColorStateList.valueOf(accent));
            seekBar.setThumbTintList(ColorStateList.valueOf(accent));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) {
                        return;
                    }
                    int value = MIN_SCALE + progress;
                    ITEMS[index].setConfigInt(value);
                    valueView.setText(value + "%");
                    MeeroTopBar.notifyChanged();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 12, 40, 12, 8));
        }

        public void bind(int idx) {
            index = idx;
            titleView.setText(TITLES[idx]);
            int value = ITEMS[idx].Int();
            if (value < MIN_SCALE) value = MIN_SCALE;
            if (value > MAX_SCALE) value = MAX_SCALE;
            seekBar.setProgress(value - MIN_SCALE);
            valueView.setText(value + "%");
        }
    }

    /* ---------------- live header preview ---------------- */

    private class MeeroTopBarPreview extends View implements MeeroTopBar.Listener {

        private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint captionPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint linePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
            float total = 10f + header + 6f + 16f + MeeroTopBar.pinnedBarDp() + 6f + 16f + MeeroTopBar.musicBarDp() + 12f;
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(total));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final float w = getWidth();
            final float side = dp(12f);

            final int titleColor = getThemedColor(Theme.key_actionBarDefaultTitle);
            final int subColor = getThemedColor(Theme.key_actionBarDefaultSubtitle);
            final int fillColor = getThemedColor(Theme.key_graySection);

            // ---------- header bar ----------
            float pillH = dp(MeeroTopBar.pillDp() + 12f);
            float backH = dp(MeeroTopBar.backPillDp() + 12f);
            float headerH = Math.max(pillH, backH) + dp(8f);
            float cy = dp(10f) + headerH / 2f;

            // back capsule + chevron
            fillPaint.setColor(fillColor);
            rect.set(side, cy - backH / 2f, side + backH, cy + backH / 2f);
            canvas.drawRoundRect(rect, backH / 2f, backH / 2f, fillPaint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeWidth(dp(2.2f) * MeeroTopBar.backK());
            strokePaint.setColor(titleColor);
            float cx = side + backH / 2f;
            float chH = dp(13f) * MeeroTopBar.backK();
            float chW = dp(4.5f) * MeeroTopBar.backK();
            path.reset();
            path.moveTo(cx + chW, cy - chH / 2f);
            path.lineTo(cx - chW * 0.6f, cy);
            path.lineTo(cx + chW, cy + chH / 2f);
            canvas.drawPath(path, strokePaint);

            // name+status capsule
            float pillX0 = side + backH + dp(6f);
            rect.set(pillX0, cy - pillH / 2f, w - side, cy + pillH / 2f);
            canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, fillPaint);

            // avatar + its oval
            float avatar = dp(MeeroTopBar.avatarDp());
            float acx = pillX0 + dp(6f) + avatar / 2f;
            canvas.drawCircle(acx, cy, avatar / 2f, fillPaint);
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
            float tx = pillX0 + dp(6f) + avatar + dp(8f);
            canvas.drawText("اسم المحادثة", tx, cy - dp(2f), titlePaint);
            canvas.drawText("الحالة", tx, cy + dp(3f) - subPaint.ascent(), subPaint);

            // ---------- pinned strip ----------
            float py = dp(10f) + headerH + dp(6f);
            captionPaint.setTextSize(dp(10f));
            captionPaint.setColor(subColor);
            canvas.drawText("التثبيت", side, py + dp(11f), captionPaint);
            float pBarY = py + dp(16f);
            float pBarH = dp(MeeroTopBar.pinnedBarDp());
            rect.set(side, pBarY, w - side, pBarY + pBarH);
            canvas.drawRoundRect(rect, dp(8f), dp(8f), fillPaint);
            linePaint.setColor(titleColor);
            linePaint.setTypeface(AndroidUtilities.bold());
            linePaint.setTextSize(dp(MeeroTopBar.pinnedCounterDp()));
            canvas.drawText("رسالة مثبتة", side + dp(23f), pBarY + pBarH * 0.45f, linePaint);
            linePaint.setTypeface(Typeface.DEFAULT);
            linePaint.setColor(subColor);
            linePaint.setTextSize(dp(MeeroTopBar.pinnedTextDp()));
            canvas.drawText("نص الرسالة المثبتة هنا…", side + dp(23f), pBarY + pBarH * 0.85f, linePaint);

            // ---------- music bar ----------
            float my = pBarY + pBarH + dp(6f);
            canvas.drawText("الموسيقى", side, my + dp(11f), captionPaint);
            float mBarY = my + dp(16f);
            float mBarH = dp(MeeroTopBar.musicBarDp());
            rect.set(side, mBarY, w - side, mBarY + mBarH);
            float mr = Math.min(dp(18f), mBarH / 2f);
            canvas.drawRoundRect(rect, mr, mr, fillPaint);

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
                String t1 = "عنوان الأغنية";
                String t2 = "اسم الفنان";
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
                canvas.drawText("اسم الفنان - عنوان الأغنية", side + dp(6f) + ps + dp(6f), mcy - (linePaint.ascent() + linePaint.descent()) / 2f, linePaint);
            }
        }
    }
}
