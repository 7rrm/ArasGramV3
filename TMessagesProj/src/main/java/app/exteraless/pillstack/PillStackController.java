package app.exteraless.pillstack;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;

import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.exteraless.pillstack.pills.BasePill;

/**
 * Вся склейка Pill Stack со строкой поиска.
 *
 * Наружу торчит один метод — {@link #attach(FrameLayout, EditText)}: контроллер сам создаёт,
 * пересобирает и убирает полосу пилюль, поэтому в экране-хосте правок почти нет.
 */
public class PillStackController implements PillStackEvents.Listener {

    private final FrameLayout container;
    private final EditText editText;
    private PillStackView stackView;
    private LinearLayout stackContainer;
    private TextView arrowView;
    private boolean attached;
    private int builtLayoutVersion = -1;

    /** Подключает полосу пилюль к контейнеру строки поиска. */
    public static PillStackController attach(FrameLayout container, EditText editText) {
        if (container == null) {
            return null;
        }
        // на случай, если init() ещё не вызвали из ApplicationLoader
        PillStackConfig.init();
        return new PillStackController(container, editText);
    }

    private PillStackController(FrameLayout container, EditText editText) {
        this.container = container;
        this.editText = editText;
        if (container instanceof org.telegram.ui.Components.FragmentSearchField) {
            ((org.telegram.ui.Components.FragmentSearchField) container).setPillStackController(this);
        }

        container.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                attached = true;
                PillStackEvents.addListener(PillStackController.this);
                if (stackView != null && stackContainer != null && stackContainer.getParent() == container
                        && builtLayoutVersion == PillStackEvents.getLayoutVersion()) {
                    stackView.updateColors();
                    updateVisibility();
                } else {
                    rebuild();
                }
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                attached = false;
                PillStackEvents.removeListener(PillStackController.this);
            }
        });

        if (editText != null) {
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    updateVisibility();
                }
            });
        }

        if (container.isAttachedToWindow()) {
            attached = true;
            PillStackEvents.addListener(this);
            rebuild();
        }
    }

    @Override
    public void onPillStackLayoutChanged() {
        rebuild();
    }

    /** Пересобирает полосу по текущей раскладке. */
    public void rebuild() {
        if (container instanceof org.telegram.ui.Components.FragmentSearchField) {
            ((org.telegram.ui.Components.FragmentSearchField) container).updatePillStack(false);
        } else {
            rebuildContents();
        }
    }

    public PillStackView getStackView() {
        return stackView;
    }

    public void rebuildContents() {
        builtLayoutVersion = PillStackEvents.getLayoutVersion();
        if (stackView != null && (stackContainer == null || stackContainer.getParent() != container)) {
            stackView.clearPills();
            stackView = null;
            stackContainer = null;
        }
        List<Integer> active = tw.nekomimi.nekogram.NekoConfig.meeroPillStack.Bool()
                ? new ArrayList<>(PillStackConfig.getActivePills()) : new ArrayList<>();
        if (active.isEmpty()) {
            if (stackView != null) {
                stackView.clearPills();
                container.removeView(stackContainer);
                stackContainer = null;
                stackView = null;
            }
            return;
        }
        if (stackView == null) {
            stackView = new PillStackView(container.getContext());
            stackContainer = new LinearLayout(container.getContext());
            stackContainer.setGravity(Gravity.CENTER_VERTICAL);
            // Keep the physical pill/arrow ordering explicit in RTL as well.
            stackContainer.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            arrowView = new TextView(container.getContext());
            arrowView.setText("⌄");
            arrowView.setTextSize(19);
            arrowView.setGravity(Gravity.CENTER);
            arrowView.setContentDescription(container.getContext().getString(org.telegram.messenger.R.string.PillStackNext));
            arrowView.setOnClickListener(v -> { if (stackView != null) stackView.showNextPill(); });
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(30), LayoutHelper.MATCH_PARENT);
            LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT);
            if (LocaleController.isRTL) {
                stackContainer.addView(stackView, pillParams);
                stackContainer.addView(arrowView, arrowParams);
            } else {
                stackContainer.addView(arrowView, arrowParams);
                stackContainer.addView(stackView, pillParams);
            }
            container.addView(stackContainer, LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT,
                    (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL,
                    6, 0, 6, 0));
        }
        arrowView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        arrowView.setVisibility(PillStackConfig.navigationMode.Int() != 0 && active.size() > 1 ? View.VISIBLE : View.GONE);
        stackView.clearPills();

        Theme.ResourcesProvider resourcesProvider = null;
        if (container instanceof Theme.ResourcesProvider) {
            resourcesProvider = (Theme.ResourcesProvider) container;
        }

        int lastActiveId = PillStackConfig.lastActivePillId();
        int selectedIndex = 0;
        for (int id : active) {
            BasePill pill = PillRegistry.createPill(id, container.getContext(), resourcesProvider);
            if (pill == null) {
                continue;
            }
            stackView.addPill(pill);
            if (id == lastActiveId) {
                selectedIndex = stackView.getPillsCount() - 1;
            }
        }
        if (stackView.getPillsCount() == 0) {
            container.removeView(stackContainer);
            stackContainer = null;
            stackView = null;
            return;
        }
        stackView.setCurrentIndex(selectedIndex);
        updateVisibility();
    }

    /** Пока в поиске что-то набрано, пилюли прячутся, чтобы не мешать тексту. */
    private void updateVisibility() {
        if (stackView == null) {
            return;
        }
        boolean hasText = editText != null && editText.getText() != null && editText.getText().length() > 0;
        stackView.setVisibilityFactor(hasText ? 0f : 1f);
        if (stackContainer != null) stackContainer.setVisibility(hasText ? View.GONE : View.VISIBLE);
    }

    public void updateColors() {
        if (stackView != null) {
            stackView.updateColors();
        }
    }

    public boolean isAttached() {
        return attached;
    }
}
