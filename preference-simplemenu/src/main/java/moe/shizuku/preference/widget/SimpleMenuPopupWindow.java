package moe.shizuku.preference.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;

import java.util.Arrays;
import java.util.Comparator;

import moe.shizuku.preference.animation.SimpleMenuAnimation;
import moe.shizuku.preference.drawable.FixedBoundsDrawable;
import moe.shizuku.preference.simplemenu.R;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Extension of {@link PopupWindow} that implements
 * <a href="https://material.io/guidelines/components/menus.html#menus-simple-menus">Simple Menus</a>
 * in Material Design.
 */

public class SimpleMenuPopupWindow extends PopupWindow {

    public static final int POPUP_MENU = 0;
    public static final int DIALOG = 1;

    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    public interface OnItemClickListener {
        void onClick(int i);
    }

    protected final int[] elevation = new int[2];
    protected final int[][] margin = new int[2][2];
    protected final int[][] listPadding = new int[2][2];
    protected final int itemHeight;
    protected final int dialogMaxWidth;
    protected final int unit;
    protected final int maxUnits;

    private int mMode = POPUP_MENU;

    private boolean mRequestMeasure = true;

    private RecyclerView mList;
    private SimpleMenuListAdapter mAdapter;

    private OnItemClickListener mOnItemClickListener;
    private CharSequence[] mEntries;
    private int mSelectedIndex;

    private int mMeasuredWidth;

    public SimpleMenuPopupWindow(Context context) {
        this(context, null);
    }

    public SimpleMenuPopupWindow(Context context, AttributeSet attrs) {
        this(context, attrs, R.styleable.SimpleMenuPreference_popupStyle);
    }

    public SimpleMenuPopupWindow(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, R.style.Preference_SimpleMenuPreference_Popup);
    }

    @SuppressLint("InflateParams")
    public SimpleMenuPopupWindow(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        setFocusable(true);
        setOutsideTouchable(false);

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SimpleMenuPopup, defStyleAttr, defStyleRes);

        elevation[POPUP_MENU] = (int) a.getDimension(R.styleable.SimpleMenuPopup_listElevation, 4f);
        elevation[DIALOG] = (int) a.getDimension(R.styleable.SimpleMenuPopup_dialogElevation, 48f);
        margin[POPUP_MENU][HORIZONTAL] = (int) a.getDimension(R.styleable.SimpleMenuPopup_listMarginHorizontal, 0);
        margin[POPUP_MENU][VERTICAL] = (int) a.getDimension(R.styleable.SimpleMenuPopup_listMarginVertical, 0);
        margin[DIALOG][HORIZONTAL] = (int) a.getDimension(R.styleable.SimpleMenuPopup_dialogMarginHorizontal, 0);
        margin[DIALOG][VERTICAL] = (int) a.getDimension(R.styleable.SimpleMenuPopup_dialogMarginVertical, 0);
        listPadding[POPUP_MENU][HORIZONTAL] = (int) a.getDimension(R.styleable.SimpleMenuPopup_listItemPadding, 0);
        listPadding[DIALOG][HORIZONTAL] = (int) a.getDimension(R.styleable.SimpleMenuPopup_dialogItemPadding, 0);
        dialogMaxWidth  = (int) a.getDimension(R.styleable.SimpleMenuPopup_dialogMaxWidth, 0);
        unit = (int) a.getDimension(R.styleable.SimpleMenuPopup_unit, 0);
        maxUnits = a.getInteger(R.styleable.SimpleMenuPopup_maxUnits, 0);

        mList = (RecyclerView) LayoutInflater.from(context).inflate(R.layout.simple_menu_list, null);
        mList.setFocusable(true);
        mList.setLayoutManager(new LinearLayoutManager(context));
        mList.setItemAnimator(null);
        setContentView(mList);

        mAdapter = new SimpleMenuListAdapter(this);
        mList.setAdapter(mAdapter);

        a.recycle();

        // TODO do not hardcode
        itemHeight = (int) (context.getResources().getDisplayMetrics().density * 48);
        listPadding[POPUP_MENU][VERTICAL] = listPadding[DIALOG][VERTICAL] = (int) (context.getResources().getDisplayMetrics().density * 8);
    }

    public OnItemClickListener getOnItemClickListener() {
        return mOnItemClickListener;
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        mOnItemClickListener = onItemClickListener;
    }

    protected int getMode() {
        return mMode;
    }

    private void setMode(int mode) {
        mMode = mode;
    }

    protected CharSequence[] getEntries() {
        return mEntries;
    }

    public void setEntries(CharSequence[] entries) {
        mEntries = entries;
    }

    protected int getSelectedIndex() {
        return mSelectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        mSelectedIndex = selectedIndex;
    }

    @Override
    public RecyclerView getContentView() {
        return (RecyclerView) super.getContentView();
    }

    @Override
    public FixedBoundsDrawable getBackground() {
        return (FixedBoundsDrawable) super.getBackground();
    }

    @Override
    public void setBackgroundDrawable(Drawable background) {
        if (background == null) {
            throw new IllegalStateException("SimpleMenuPopupWindow must have a background");
        }

        if (!(background instanceof FixedBoundsDrawable)) {
            background = new FixedBoundsDrawable(background);
        }
        super.setBackgroundDrawable(background);
    }

    /**
     * Show the PopupWindow
     *
     * @param anchor View that will be used to calc the position of windows
     */
    public void show(View anchor) {
        View container = (View) anchor  // itemView
                .getParent();           // -> list (RecyclerView)

        int measuredWidth = measureWidth(anchor, mEntries);
        if (measuredWidth == -1) {
            setMode(DIALOG);
        } else if (measuredWidth != 0) {
            setMode(POPUP_MENU);

            mMeasuredWidth = measuredWidth;
        }

        mAdapter.notifyDataSetChanged();

        if (mMode == POPUP_MENU) {
            showPopupMenu(anchor, container, mMeasuredWidth);
        } else {
            showDialog(anchor, container);
        }
    }

    /**
     * Show popup window in dialog mode
     *
     * @param anchor View that will be used to calc the position of windows
     * @param container Container view that holds preference list
     */
    private void showDialog(View anchor, View container) {
        final int index = Math.max(0, mSelectedIndex);
        final int count = mEntries.length;

        getContentView().setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        getContentView().scrollToPosition(index);

        int width = Math.min(dialogMaxWidth, container.getWidth() - margin[DIALOG][HORIZONTAL] * 2);
        setWidth(width);
        setHeight(WRAP_CONTENT);
        setAnimationStyle(R.style.Animation_SimpleMenuCenter);
        setElevation(elevation[DIALOG]);

        super.showAtLocation(anchor, Gravity.CENTER_VERTICAL, 0, 0);

        getContentView().post(new Runnable() {
            @Override
            public void run() {
                int width = getContentView().getWidth();
                int height = getContentView().getHeight();
                Rect start = new Rect(width / 2, height / 2, width / 2, height / 2);

                SimpleMenuAnimation.startEnterAnimation(getContentView(), getBackground(),
                        width, height, width / 2, height / 2, start, itemHeight, elevation[DIALOG] / 4, index);
            }
        });

        getContentView().post(new Runnable() {
            @Override
            public void run() {
                // disable over scroll when no scroll
                LinearLayoutManager lm = (LinearLayoutManager) getContentView().getLayoutManager();
                if (lm.findFirstCompletelyVisibleItemPosition() == 0
                    && lm.findLastCompletelyVisibleItemPosition() == count - 1) {
                    getContentView().setOverScrollMode(View.OVER_SCROLL_NEVER);
                }
            }
        });
    }

    /**
     * Show popup window in popup mode
     *
     * @param anchor View that will be used to calc the position of windows
     * @param container Container view that holds preference list
     * @param width Measured width of this window
     */
    private void showPopupMenu(View anchor, View container, int width) {
        final int index = Math.max(0, mSelectedIndex);
        final int count = mEntries.length;

        final int anchorTop = anchor.getTop();
        final int anchorHeight = anchor.getHeight();
        final int measuredHeight = itemHeight * count + listPadding[POPUP_MENU][VERTICAL] * 2;

        int[] location = new int[2];
        container.getLocationInWindow(location);

        final int containerTopInWindow = location[1];
        final int containerHeight = container.getHeight();

        // popup should looks it its container
        final int maxHeight = containerHeight - margin[DIALOG][VERTICAL] * 2;

        int y;

        int height = measuredHeight;
        int elevation = this.elevation[POPUP_MENU];
        int centerX = listPadding[POPUP_MENU][HORIZONTAL];
        int centerY;
        int animItemHeight = itemHeight + listPadding[POPUP_MENU][VERTICAL] * 2;
        int animIndex = index;
        Rect animStartRect;

        if (height > maxHeight) {
            // too high, use scroll
            y = containerTopInWindow + margin[POPUP_MENU][VERTICAL];

            // scroll to select item
            final int scroll = itemHeight * index
                    - anchorTop + listPadding[POPUP_MENU][VERTICAL] + margin[POPUP_MENU][VERTICAL]
                    - anchorHeight / 2 + itemHeight / 2;

            getContentView().post(new Runnable() {
                @Override
                public void run() {
                    getContentView().scrollBy(0, -measuredHeight); // to top
                    getContentView().scrollBy(0, scroll);
                }
            });
            getContentView().setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

            height = containerHeight - margin[POPUP_MENU][VERTICAL] * 2;

            // TODO: should we align to anchor view ?
            centerY = scroll;
            animIndex = index;
        } else {
            // calc align to selected
            y = containerTopInWindow + anchorTop + anchorHeight / 2 - itemHeight / 2
                    - listPadding[POPUP_MENU][VERTICAL] - index * itemHeight;

            // make sure window is in parent view
            int maxY = containerTopInWindow + containerHeight
                    - measuredHeight - margin[POPUP_MENU][VERTICAL];
            y = Math.min(y, maxY);

            int minY = containerTopInWindow + margin[POPUP_MENU][VERTICAL];
            y = Math.max(y, minY);

            getContentView().setOverScrollMode(View.OVER_SCROLL_NEVER);

            // center of selected item
            centerY = (int) (listPadding[POPUP_MENU][VERTICAL] + index * itemHeight + itemHeight * 0.5);
        }

        setWidth(width);
        setHeight(height);
        setElevation(elevation);
        setAnimationStyle(R.style.Animation_SimpleMenuCenter);

        super.showAtLocation(anchor, Gravity.NO_GRAVITY, margin[POPUP_MENU][HORIZONTAL], y);

        int startTop = centerY - (int) (itemHeight * 0.2);
        int startBottom = centerY + (int) (itemHeight * 0.2);
        int startLeft = centerX;
        int startRight = centerX + (int) (width * 0.7);

        animStartRect = new Rect(startLeft, startTop, startRight, startBottom);

        int animElevation = (int) Math.round(elevation * 0.25);

        SimpleMenuAnimation.postStartEnterAnimation(getContentView(), getBackground(),
                width, height, centerX, centerY, animStartRect, animItemHeight, animElevation, animIndex);
    }

    /**
     * Request a measurement before next show, call this when entries changed.
     */
    public void requestMeasure() {
        mRequestMeasure = true;
    }

    /**
     * Measure window width
     *
     * @param parent PreferenceItemView, popup width (including margin) should not larger that its width
     *               or we will use dialog
     * @param entries Entries of preference hold this window
     * @return  0: skip
     *          -1: use dialog
     *          other: measuredWidth
     */
    public int measureWidth(View parent, CharSequence[] entries) {
        // skip if should not measure
        if (!mRequestMeasure) {
            return 0;
        }

        mRequestMeasure = false;

        entries = Arrays.copyOf(entries, entries.length);

        Arrays.sort(entries, new Comparator<CharSequence>() {
            @Override
            public int compare(CharSequence o1, CharSequence o2) {
                return o2.length() - o1.length();
            }
        });

        Context context = parent.getContext();
        int width = 0;

        int maxWidth = Math.min(unit * maxUnits,
                parent.getWidth() - margin[POPUP_MENU][HORIZONTAL] * 2);

        Rect bounds = new Rect();
        Paint textPaint = new TextPaint();
        // TODO do not hardcode
        textPaint.setTextSize(16 * context.getResources().getDisplayMetrics().scaledDensity);

        for (CharSequence chs : entries) {
            textPaint.getTextBounds(chs.toString(), 0, chs.length(), bounds);

            width = Math.max(width, bounds.width() + listPadding[POPUP_MENU][HORIZONTAL] * 2);

            // more than one line should use dialog
            if (width > maxWidth
                    || chs.toString().contains("\n")) {
                return -1;
            }
        }

        // width is a multiple of a unit
        int w = 0;
        while (width > w) {
            w += unit;
        }

        return w;
    }

    @Override
    public void showAtLocation(View parent, int gravity, int x, int y) {
        throw new UnsupportedOperationException("use show(anchor) to show the window");
    }

    @Override
    public void showAsDropDown(View anchor) {
        throw new UnsupportedOperationException("use show(anchor) to show the window");
    }

    @Override
    public void showAsDropDown(View anchor, int xoff, int yoff) {
        throw new UnsupportedOperationException("use show(anchor) to show the window");
    }

    @Override
    public void showAsDropDown(View anchor, int xoff, int yoff, int gravity) {
        throw new UnsupportedOperationException("use show(anchor) to show the window");
    }
}