package com.artifex.mupdf.viewer;

import static com.xlk.mupdf.library.Config.TAG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.text.TextPaint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.artifex.mupdf.fitz.Cookie;
import com.artifex.mupdf.fitz.Link;
import com.artifex.mupdf.fitz.Quad;
import com.artifex.mupdf.util.LogUtils;
import com.xlk.mupdf.library.R;

/**
 * Make our ImageViews opaque to optimize redraw
 * 使我们的 ImageViews 不透明以优化重绘
 */
@SuppressLint("AppCompatCustomView")
class OpaqueImageView extends ImageView {
    private final TextPaint textPaint = new TextPaint();
    private String mWaterMark;

    public OpaqueImageView(Context context, String watermark) {
        super(context);
        if (watermark != null && !watermark.isEmpty()) {
            mWaterMark = watermark;
            textPaint.setTextSize(60);
            textPaint.setColor(Color.parseColor("#66F4511E"));
            textPaint.setAntiAlias(true);
            textPaint.setTextSkewX(-0.75f);//设置文本倾斜度，负值向左倾斜
        }
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mWaterMark != null && !mWaterMark.isEmpty()) {
            int width = getWidth();
            int height = getHeight();
            float textWidth = textPaint.measureText(mWaterMark);
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            float textHeight = fontMetrics.bottom - fontMetrics.top;

            // 计算文本的起始坐标
            float x = (width - textWidth) / 2;
            float y = (height + textHeight) / 2 - fontMetrics.bottom;

            canvas.drawText(mWaterMark, x, y, textPaint);
        }
    }
}

public class PageView extends ViewGroup {
    private final String APP = "MuPDF";
    private final MuPDFCore mCore;

    private static final int HIGHLIGHT_COLOR = 0x80cc6600;
    private static final int LINK_COLOR = 0x800066cc;
    private static final int BOX_COLOR = 0xFF4444FF;
    private static final int BACKGROUND_COLOR = 0xFFFFFFFF;
    private static final int PROGRESS_DIALOG_DELAY = 200;

    protected final Context mContext;
    private final String mWaterMark;

    protected int mPageNumber;
    private Point mParentSize;
    protected Point mSize;   // 最小缩放时的页面大小
    protected float mSourceScale;

    private ImageView mEntire; // Image rendered at minimum zoom
    private Bitmap mEntireBm;
    private Matrix mEntireMat;
    private AsyncTask<Void, Void, Link[]> mGetLinkInfo;
    private CancellableAsyncTask<Void, Boolean> mDrawEntire;

    private Point mPatchViewSize; // View size on the basis of which the patch was created
    private Rect mPatchArea;
    private ImageView mPatch;
    private Bitmap mPatchBm;
    private CancellableAsyncTask<Void, Boolean> mDrawPatch;
    private Quad mSearchBoxes[][];
    protected Link mLinks[];
    private View mSearchView;
    private boolean mIsBlank;
    private boolean mHighlightLinks;

    private ImageView mErrorIndicator;

    private ProgressBar mBusyIndicator;
    private final Handler mHandler = new Handler();

    public PageView(Context c, MuPDFCore core, Point parentSize, Bitmap sharedHqBm, String watermark) {
        super(c);
        mContext = c;
        mCore = core;
        mParentSize = parentSize;
        setBackgroundColor(BACKGROUND_COLOR);
        mEntireBm = Bitmap.createBitmap((int) parentSize.x, (int) parentSize.y, Config.ARGB_8888);
        mPatchBm = sharedHqBm;
        mEntireMat = new Matrix();
        mWaterMark = watermark;
    }

    public Bitmap getEntireBm() {
        return Bitmap.createBitmap(mEntireBm);
    }

    private void reinit() {
        // Cancel pending render task
        if (mDrawEntire != null) {
            mDrawEntire.cancel();
            mDrawEntire = null;
        }

        if (mDrawPatch != null) {
            mDrawPatch.cancel();
            mDrawPatch = null;
        }

        if (mGetLinkInfo != null) {
            mGetLinkInfo.cancel(true);
            mGetLinkInfo = null;
        }

        mIsBlank = true;
        mPageNumber = 0;

        if (mSize == null)
            mSize = mParentSize;

        if (mEntire != null) {
            mEntire.setImageBitmap(null);
            mEntire.invalidate();
        }

        if (mPatch != null) {
            mPatch.setImageBitmap(null);
            mPatch.invalidate();
        }

        mPatchViewSize = null;
        mPatchArea = null;

        mSearchBoxes = null;
        mLinks = null;

        clearRenderError();
    }

    public void releaseResources() {
        reinit();

        if (mBusyIndicator != null) {
            removeView(mBusyIndicator);
            mBusyIndicator = null;
        }
        clearRenderError();
    }

    public void releaseBitmaps() {
        reinit();

        // recycle bitmaps before releasing them.

        if (mEntireBm != null)
            mEntireBm.recycle();
        mEntireBm = null;

        if (mPatchBm != null)
            mPatchBm.recycle();
        mPatchBm = null;
    }

    public void blank(int page) {
        reinit();
        mPageNumber = page;

        if (mBusyIndicator == null) {
            mBusyIndicator = new ProgressBar(mContext);
            mBusyIndicator.setIndeterminate(true);
            addView(mBusyIndicator);
        }

        setBackgroundColor(BACKGROUND_COLOR);
    }

    protected void clearRenderError() {
        if (mErrorIndicator == null)
            return;

        removeView(mErrorIndicator);
        mErrorIndicator = null;
        invalidate();
    }

    protected void setRenderError(String why) {
//        Log.i(TAG, "PageView.setRenderError: " + why);
        int page = mPageNumber;
        reinit();
        mPageNumber = page;

        if (mBusyIndicator != null) {
            removeView(mBusyIndicator);
            mBusyIndicator = null;
        }
        if (mSearchView != null) {
            removeView(mSearchView);
            mSearchView = null;
        }

        if (mErrorIndicator == null) {
            mErrorIndicator = new OpaqueImageView(mContext, "");
            mErrorIndicator.setScaleType(ImageView.ScaleType.CENTER);
            addView(mErrorIndicator);
            Drawable mErrorIcon = getResources().getDrawable(R.drawable.ic_error_red_24dp);
            mErrorIndicator.setImageDrawable(mErrorIcon);
            mErrorIndicator.setBackgroundColor(BACKGROUND_COLOR);
        }

        setBackgroundColor(Color.TRANSPARENT);
        mErrorIndicator.bringToFront();
        mErrorIndicator.invalidate();
    }

    public void setPage(int page, PointF size) {
        // Cancel pending render task
        if (mDrawEntire != null) {
            mDrawEntire.cancel();
            mDrawEntire = null;
        }

        mIsBlank = false;
        // Highlights may be missing because mIsBlank was true on last draw
        if (mSearchView != null)
            mSearchView.invalidate();

        mPageNumber = page;

        if (size == null) {
            setRenderError("Error loading page");
            size = new PointF(612, 792);
        }
        // 计算适合屏幕限制的缩放尺寸
        // 这是最小缩放时的尺寸
        mSourceScale = Math.min(mParentSize.x / size.x, mParentSize.y / size.y);
        Point newSize = new Point((int) (size.x * mSourceScale), (int) (size.y * mSourceScale));
        mSize = newSize;
        Log.i(TAG, "PageView.setPage: page=" + page + ",size=" + size + ",mParentSize=" + mParentSize + ",mSourceScale=" + mSourceScale);

        if (mErrorIndicator != null)
            return;

        if (mEntire == null) {
            mEntire = new OpaqueImageView(mContext, mWaterMark);
            mEntire.setScaleType(ImageView.ScaleType.MATRIX);
            addView(mEntire);
        }

        mEntire.setImageBitmap(null);
        mEntire.invalidate();

        // 在后台获取链接信息
        /*mGetLinkInfo = new AsyncTask<Void, Void, Link[]>() {
            protected Link[] doInBackground(Void... v) {
                return getLinkInfo();
            }

            protected void onPostExecute(Link[] v) {
                mLinks = v;
                if (mSearchView != null)
                    mSearchView.invalidate();
            }
        };

        mGetLinkInfo.execute();*/

        // 在后台渲染页面
        mDrawEntire = new CancellableAsyncTask<Void, Boolean>(getDrawPageTask(mEntireBm, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y)) {

            @Override
            public void onPreExecute() {
                setBackgroundColor(BACKGROUND_COLOR);
                mEntire.setImageBitmap(null);
                mEntire.invalidate();

                if (mBusyIndicator == null) {
                    mBusyIndicator = new ProgressBar(mContext);
                    mBusyIndicator.setIndeterminate(true);
                    addView(mBusyIndicator);
                    mBusyIndicator.setVisibility(INVISIBLE);
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            if (mBusyIndicator != null)
                                mBusyIndicator.setVisibility(VISIBLE);
                        }
                    }, PROGRESS_DIALOG_DELAY);
                }
            }

            @Override
            public void onPostExecute(Boolean result) {
                removeView(mBusyIndicator);
                mBusyIndicator = null;
                if (result.booleanValue()) {
                    clearRenderError();
//                    Log.i(TAG, "PageView.onPostExecute:setPage mDrawEntire");
                    mEntire.setImageBitmap(mEntireBm);
                    mEntire.invalidate();
                } else {
                    setRenderError("Error rendering page");
                }
                setBackgroundColor(Color.TRANSPARENT);
            }
        };

        mDrawEntire.execute();

        /*if (mSearchView == null) {
            mSearchView = new View(mContext) {
                @Override
                protected void onDraw(final Canvas canvas) {
                    super.onDraw(canvas);
                    // Work out current total scale factor
                    // from source to view
                    final float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
                    final Paint paint = new Paint();

                    if (!mIsBlank && mSearchBoxes != null) {
                        paint.setColor(HIGHLIGHT_COLOR);
                        for (Quad[] searchBox : mSearchBoxes) {
                            for (Quad q : searchBox) {
                                Path path = new Path();
                                path.moveTo(q.ul_x * scale, q.ul_y * scale);
                                path.lineTo(q.ll_x * scale, q.ll_y * scale);
                                path.lineTo(q.lr_x * scale, q.lr_y * scale);
                                path.lineTo(q.ur_x * scale, q.ur_y * scale);
                                path.close();
                                canvas.drawPath(path, paint);
                            }
                        }
                    }

                    if (!mIsBlank && mLinks != null && mHighlightLinks) {
                        paint.setColor(LINK_COLOR);
                        for (Link link : mLinks)
                            canvas.drawRect(link.getBounds().x0 * scale, link.getBounds().y0 * scale,
                                    link.getBounds().x1 * scale, link.getBounds().y1 * scale,
                                    paint);
                    }
                }
            };

            addView(mSearchView);
        }*/
        requestLayout();
    }

    public void setSearchBoxes(Quad searchBoxes[][]) {
        mSearchBoxes = searchBoxes;
        if (mSearchView != null)
            mSearchView.invalidate();
    }

    public void setLinkHighlighting(boolean f) {
        mHighlightLinks = f;
        if (mSearchView != null)
            mSearchView.invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int x, y;
        switch (View.MeasureSpec.getMode(widthMeasureSpec)) {
            case View.MeasureSpec.UNSPECIFIED:
                x = (int) mSize.x;
                break;
            default:
                x = View.MeasureSpec.getSize(widthMeasureSpec);
        }
        switch (View.MeasureSpec.getMode(heightMeasureSpec)) {
            case View.MeasureSpec.UNSPECIFIED:
                y = (int) mSize.y;
                break;
            default:
                y = View.MeasureSpec.getSize(heightMeasureSpec);
        }

        setMeasuredDimension(x, y);

        if (mBusyIndicator != null) {
            int limit = (int) (Math.min(mParentSize.x, mParentSize.y) / 2);
            mBusyIndicator.measure(View.MeasureSpec.AT_MOST | limit, View.MeasureSpec.AT_MOST | limit);
        }
        if (mErrorIndicator != null) {
            int limit = (int) (Math.min(mParentSize.x, mParentSize.y) / 2);
            mErrorIndicator.measure(View.MeasureSpec.AT_MOST | limit, View.MeasureSpec.AT_MOST | limit);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int w = right - left;
        int h = bottom - top;
//        Log.i(TAG, "PageView.onLayout: changed:" + changed + ",left:" + left + ",top:" + top + ",right:" + right + ",bottom:" + bottom);
        if (mEntire != null) {
//            Log.i(TAG, "PageView.onLayout: mEntire:" + mEntire.getWidth() + ",mEntire:" + mEntire.getHeight());
            if (mEntire.getWidth() != w || mEntire.getHeight() != h) {
                mEntireMat.setScale(w / (float) mSize.x, h / (float) mSize.y);
                mEntire.setImageMatrix(mEntireMat);
                mEntire.invalidate();
            }
            mEntire.layout(0, 0, w, h);
        }

        if (mSearchView != null) {
            mSearchView.layout(0, 0, w, h);
        }

        if (mPatchViewSize != null) {
            if (mPatchViewSize.x != w || mPatchViewSize.y != h) {
                // Zoomed since patch was created
                mPatchViewSize = null;
                mPatchArea = null;
                if (mPatch != null) {
                    mPatch.setImageBitmap(null);
                    mPatch.invalidate();
                }
            } else {
                mPatch.layout(mPatchArea.left, mPatchArea.top, mPatchArea.right, mPatchArea.bottom);
            }
        }

        if (mBusyIndicator != null) {
            int bw = mBusyIndicator.getMeasuredWidth();
            int bh = mBusyIndicator.getMeasuredHeight();

            mBusyIndicator.layout((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
        }

        if (mErrorIndicator != null) {
            int bw = (int) (8.5 * mErrorIndicator.getMeasuredWidth());
            int bh = (int) (11 * mErrorIndicator.getMeasuredHeight());
            mErrorIndicator.layout((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2);
        }
    }

    public void updateHq(boolean update) {
        try {
            throw new Exception("哪里调用");
        } catch (Exception e) {
            LogUtils.e(e);
        }
        if (mErrorIndicator != null) {
            if (mPatch != null) {
                mPatch.setImageBitmap(null);
                mPatch.invalidate();
            }
            return;
        }

        Rect viewArea = new Rect(getLeft(), getTop(), getRight(), getBottom());
        Log.i(TAG, "PageView.updateHq: viewArea:" + viewArea + ",mSize:" + mSize);
//        if (viewArea.width() == mSize.x || viewArea.height() == mSize.y) {
//            // 如果 viewArea 的大小与未缩放的大小匹配，则不需要 hq 补丁
//            if (mPatch != null) {
//                mPatch.setImageBitmap(null);
//                mPatch.invalidate();
//            }
//        } else {
        final Point patchViewSize = new Point(viewArea.width(), viewArea.height());
        final Rect patchArea = new Rect(0, 0, (int) mParentSize.x, (int) mParentSize.y);
        Log.i(TAG, "PageView.updateHq: patchViewSize:" + patchViewSize + ",patchArea:" + patchArea);
        // 相交并测试是否有交叉点
        if (!patchArea.intersect(viewArea))
            return;

        // 相对于视图左上方的偏移贴片区域
        patchArea.offset(-viewArea.left, -viewArea.top);

        boolean area_unchanged = patchArea.equals(mPatchArea) && patchViewSize.equals(mPatchViewSize);

        // 如果被要求的区域与上次相同，并且不是因为更新，则不需要做什么。
        if (area_unchanged && !update)
            return;

        boolean completeRedraw = !(area_unchanged && update);

        // 如果仍在进行，则停止绘制之前的补丁
        if (mDrawPatch != null) {
            mDrawPatch.cancel();
            mDrawPatch = null;
        }

        // 创建并添加图像视图（如果尚未完成）。
        if (mPatch == null) {
            mPatch = new OpaqueImageView(mContext, mWaterMark);
            mPatch.setScaleType(ImageView.ScaleType.MATRIX);
            addView(mPatch);
            if (mSearchView != null)
                mSearchView.bringToFront();
        }

        CancellableTaskDefinition<Void, Boolean> task;
        Log.i(TAG, "PageView.updateHq: completeRedraw:" + completeRedraw + ",update:" + update);
        if (completeRedraw)
            task = getDrawPageTask(mPatchBm, patchViewSize.x, patchViewSize.y,
                    patchArea.left, patchArea.top,
                    patchArea.width(), patchArea.height());
        else
            task = getUpdatePageTask(mPatchBm, patchViewSize.x, patchViewSize.y,
                    patchArea.left, patchArea.top,
                    patchArea.width(), patchArea.height());

        mDrawPatch = new CancellableAsyncTask<Void, Boolean>(task) {

            public void onPostExecute(Boolean result) {
                if (result.booleanValue()) {
                    mPatchViewSize = patchViewSize;
                    mPatchArea = patchArea;
                    clearRenderError();
                    mPatch.setImageBitmap(mPatchBm);
                    mPatch.invalidate();
                    Log.i(TAG, "PageView.onPostExecute: updateHq mDrawPatch");
                    //requestLayout();
                    // 在这里调用requestLayout并不会导致后来对layout的调用。
                    // 不知道为什么，但显然其他人遇到了这个问题。
                    mPatch.layout(mPatchArea.left, mPatchArea.top, mPatchArea.right, mPatchArea.bottom);
                } else {
                    setRenderError("Error rendering patch");
                }
            }
        };

        mDrawPatch.execute();
//        }
    }

    public void update() {
        // 取消待定的渲染任务
        if (mDrawEntire != null) {
            mDrawEntire.cancel();
            mDrawEntire = null;
        }

        if (mDrawPatch != null) {
            mDrawPatch.cancel();
            mDrawPatch = null;
        }

        // 在后台渲染页面
        mDrawEntire = new CancellableAsyncTask<Void, Boolean>(getUpdatePageTask(mEntireBm, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y)) {

            public void onPostExecute(Boolean result) {
                if (result.booleanValue()) {
                    Log.i(TAG, "PageView.onPostExecute: update mDrawEntire");
                    clearRenderError();
                    mEntire.setImageBitmap(mEntireBm);
                    mEntire.invalidate();
                } else {
                    setRenderError("Error updating page");
                }
            }
        };

        mDrawEntire.execute();

        updateHq(true);
    }

    public void removeHq() {
        // 如果仍在进行，则停止绘制补丁
        if (mDrawPatch != null) {
            mDrawPatch.cancel();
            mDrawPatch = null;
        }

        // 并把它弄走
        mPatchViewSize = null;
        mPatchArea = null;
        if (mPatch != null) {
            mPatch.setImageBitmap(null);
            mPatch.invalidate();
        }
    }

    public int getPage() {
        return mPageNumber;
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    public int hitLink(Link link) {
        if (link.isExternal()) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link.getURI()));
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET); // API>=21: FLAG_ACTIVITY_NEW_DOCUMENT
            try {
                mContext.startActivity(intent);
            } catch (Exception x) {
                Log.e(APP, x.toString());
                Toast.makeText(getContext(), "Android does not allow following file:// link: " + link.getURI(), Toast.LENGTH_LONG).show();
            } catch (Throwable x) {
                Log.e(APP, x.toString());
                Toast.makeText(getContext(), x.getMessage(), Toast.LENGTH_LONG).show();
            }
            return 0;
        } else {
            return mCore.resolveLink(link);
        }
    }

    public int hitLink(float x, float y) {
        // Since link highlighting was implemented, the super class
        // PageView has had sufficient information to be able to
        // perform this method directly. Making that change would
        // make MuPDFCore.hitLinkPage superfluous.
        float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
        float docRelX = (x - getLeft()) / scale;
        float docRelY = (y - getTop()) / scale;

        if (mLinks != null)
            for (Link l : mLinks)
                if (l.getBounds().contains(docRelX, docRelY))
                    return hitLink(l);
        return 0;
    }

    protected CancellableTaskDefinition<Void, Boolean> getDrawPageTask(final Bitmap bm, final int sizeX, final int sizeY,
                                                                       final int patchX, final int patchY, final int patchWidth, final int patchHeight) {
        return new MuPDFCancellableTaskDefinition<Void, Boolean>() {
            @Override
            public Boolean doInBackground(Cookie cookie, Void... params) {
                if (bm == null)
                    return new Boolean(false);
                // Workaround bug in Android Honeycomb 3.x, where the bitmap generation count
                // is not incremented when drawing.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
                    bm.eraseColor(0);
                try {
//                    Log.i(TAG, "PageView.doInBackground: getDrawPageTask");
                    mCore.drawPage(bm, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
                    return new Boolean(true);
                } catch (RuntimeException e) {
                    return new Boolean(false);
                }
            }
        };

    }

    protected CancellableTaskDefinition<Void, Boolean> getUpdatePageTask(final Bitmap bm, final int sizeX, final int sizeY,
                                                                         final int patchX, final int patchY, final int patchWidth, final int patchHeight) {
        return new MuPDFCancellableTaskDefinition<Void, Boolean>() {
            @Override
            public Boolean doInBackground(Cookie cookie, Void... params) {
                if (bm == null)
                    return new Boolean(false);
                // Workaround bug in Android Honeycomb 3.x, where the bitmap generation count
                // is not incremented when drawing.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
                    bm.eraseColor(0);
                try {
//                    Log.i(TAG, "PageView.getUpdatePageTask: ");
                    mCore.updatePage(bm, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
                    return new Boolean(true);
                } catch (RuntimeException e) {
                    return new Boolean(false);
                }
            }
        };
    }

    protected Link[] getLinkInfo() {
        try {
            return mCore.getPageLinks(mPageNumber);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
