package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
 *
 * v234 (his reports): thick clearly-visible slider with the percentage
 * BESIDE the line; the preview paints the real glass colours; the oval
 * behind the avatar and the back capsule track their sliders with the
 * icon/image staying dead-centre.
 */
public class MeeroTopBarSizesActivity extends BaseNekoSettingsActivity {

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
    private int sliderStart = -1;
    private int resetRow = -1;
    private LinearLayout pinnedBox;
    private MeeroTopBarPreview previewView;

    @Override
    protected void updateRows() {
        super.updateRows();
        masterRow = addRow();
        if (NekoConfig.meeroTopBarSizesOn.Bool()) {
            sliderStart = rowCount;
            for (int i = 0; i < SLIDERS; i++) {
                addRow();
            }
            resetRow = addRow();
        }
    }

    /**
     * v234 (his report: "اريد معاينة حية"): pin the header preview ABOVE the
     * list so it stays on screen while every slider is dragged. The box
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
            if (position >= sliderStart && position < sliderStart + SLIDERS) return TYPE_SLIDER;
            if (position == resetRow) return TYPE_SETTINGS;
            return TYPE_SHADOW;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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

    /* ---------------- slider row (v234: thick bar + % beside the line) ---------------- */

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

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            seekBar = new SeekBar(context);
            seekBar.setMax(MAX_SCALE - MIN_SCALE);
            final int accent = getThemedColor(Theme.key_windowBackgroundWhiteBlueText);
            seekBar.setProgressTintList(ColorStateList.valueOf(accent));
            seekBar.setThumbTintList(ColorStateList.valueOf(accent));

            // v234 (his report: "the progress bar is way too small"): a thick
            // iOS-style track + a big round thumb, impossible to miss.
            GradientDrawable track = new GradientDrawable();
            track.setCornerRadius(dp(3));
            track.setColor(getThemedColor(Theme.key_graySection));
            GradientDrawable progressFill = new GradientDrawable();
            progressFill.setCornerRadius(dp(3));
            progressFill.setColor(accent);
            ClipDrawable progressClip = new ClipDrawable(progressFill, Gravity.LEFT, ClipDrawable.HORIZONTAL);
            LayerDrawable layers = new LayerDrawable(new Drawable[]{track, progressClip});
            layers.setLayerHeight(0, dp(6));
            layers.setLayerGravity(0, Gravity.CENTER_VERTICAL);
            layers.setLayerHeight(1, dp(6));
            layers.setLayerGravity(1, Gravity.CENTER_VERTICAL);
            seekBar.setProgressDrawable(layers);
            GradientDrawable thumb = new GradientDrawable();
            thumb.setShape(GradientDrawable.OVAL);
            thumb.setSize(dp(20), dp(20));
            thumb.setColor(accent);
            seekBar.setThumb(thumb);
            seekBar.setPadding(dp(2), 0, dp(2), 0);

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
            row.addView(seekBar, new LinearLayout.LayoutParams(0, dp(32), 1f));

            // v234 (his report: "the percentage sits above the line"): the
            // value lives on the slider's own row now, right of the line.
            valueView = new TextView(context);
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            valueView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueText));
            row.addView(valueView, new LinearLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 38, 16, 12));
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

    /* ---------------- live header preview (v234: real glass colours) ---------------- */

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

            // back capsule + chevron (icon scales with the capsule, stays centred)
            rect.set(side, cy - backH / 2f, side + backH, cy + backH / 2f);
            drawCapsule(canvas, rect, backH / 2f);
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
            drawCapsule(canvas, rect, pillH / 2f);

            // avatar + its oval: the oval always hugs the picture (avatar + 16)
            float oval = dp(MeeroTopBar.avatarDp() + 16f);
            float ovalX0 = pillX0 + dp(2f);
            rect.set(ovalX0, cy - oval / 2f, ovalX0 + oval, cy + oval / 2f);
            fillPaint.setColor(glassColor());
            canvas.drawRoundRect(rect, dp(23f) * MeeroTopBar.avatarK(), dp(23f) * MeeroTopBar.avatarK(), fillPaint);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1f));
            strokePaint.setColor(glassStrokeColor());
            canvas.drawRoundRect(rect, dp(23f) * MeeroTopBar.avatarK(), dp(23f) * MeeroTopBar.avatarK(), strokePaint);

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
            canvas.drawText("اسم المحادثة", tx, cy - dp(2f), titlePaint);
            canvas.drawText("الحالة", tx, cy + dp(3f) - subPaint.ascent(), subPaint);

            // ---------- pinned strip ----------
            float py = dp(8f) + headerH + dp(5f);
            captionPaint.setTextSize(dp(10f));
            captionPaint.setColor(subColor);
            canvas.drawText("التثبيت", side, py + dp(10f), captionPaint);
            float pBarY = py + dp(15f);
            float pBarH = dp(MeeroTopBar.pinnedBarDp());
            rect.set(side, pBarY, w - side, pBarY + pBarH);
            drawCapsule(canvas, rect, Math.min(dp(18f), pBarH / 2f));
            linePaint.setColor(titleColor);
            linePaint.setTypeface(AndroidUtilities.bold());
            linePaint.setTextSize(dp(MeeroTopBar.pinnedCounterDp()));
            canvas.drawText("رسالة مثبتة", side + dp(23f), pBarY + pBarH * 0.45f, linePaint);
            linePaint.setTypeface(Typeface.DEFAULT);
            linePaint.setColor(subColor);
            linePaint.setTextSize(dp(MeeroTopBar.pinnedTextDp()));
            canvas.drawText("نص الرسالة المثبتة هنا…", side + dp(23f), pBarY + pBarH * 0.85f, linePaint);

            // ---------- music bar ----------
            float my = pBarY + pBarH + dp(5f);
            canvas.drawText("الموسيقى", side, my + dp(10f), captionPaint);
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
