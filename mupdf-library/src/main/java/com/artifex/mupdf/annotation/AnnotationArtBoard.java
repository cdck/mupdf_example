package com.artifex.mupdf.annotation;

import static com.xlk.mupdf.library.MupdfConfig.TAG;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.artifex.mupdf.fitz.PDFAnnotation;
import com.artifex.mupdf.fitz.Point;
import com.artifex.mupdf.viewer.MuPDFCore;
import com.artifex.mupdf.viewer.ReaderView;
import com.xlk.mupdf.library.MupdfConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * @author Created by xlk on 2021/10/6.
 */
public class AnnotationArtBoard extends View {
    /**
     * 显示区域的宽高，是不变的
     */
    private int screenWidth, screenHeight;
    /**
     * 画板的宽高，会根据图片大小改变
     */
    private int artBoardWidth, artBoardHeight;
    /**
     * =true表示是通过构造器动态创建
     */
    private boolean isCreate;
    private Paint mPaint;
    /**
     * 画笔宽度
     */
    private float paintWidth = 2.0f;
    /**
     * 画笔默认颜色
     */
    private int paintColor = Color.RED;

    //设置画图样式
    public static final int DRAW_SLINE = 1;
    public static final int DRAW_CIRCLE = 2;
    public static final int DRAW_RECT = 3;
    public static final int DRAW_LINE = 4;
    public static final int DRAW_TEXT = 5;
    public static final int DRAW_ERASER = 6;
    /**
     * 当前画笔默认是曲线
     */
    private int currentDrawGraphics = DRAW_SLINE;
    private List<AnnotationArtBoard.DrawPath> pathList = new ArrayList<>();
    private Paint mBitmapPaint;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Path mPath;
    private DrawPath drawPath;
    private final int WRAP_WIDTH = 300;
    private final int WRAP_HEIGHT = 300;
    private DrawExitListener mDrawExitListener;
    /**
     * 是否拖动画板
     */
    public boolean drag = false;
    private final int DIFFERENCE = 1;
    private final List<Point> points = new ArrayList<>();
    private List<AnnotationBean> annotationBeans = new ArrayList<>();
    private MuPDFCore core;
    private ReaderView docView;
    private boolean isCancelAnnotation;

    public AnnotationArtBoard(Context context) {
        this(context, null);
    }

    public AnnotationArtBoard(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AnnotationArtBoard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * 删除旧批注
     */
    public AnnotationArtBoard(Context context, MuPDFCore core, ReaderView docView, int width, int height, DrawExitListener drawExitListener) {
        this(context);
        this.core = core;
        this.docView = docView;
        this.mDrawExitListener = drawExitListener;
        isCreate = true;
        screenWidth = width;
        screenHeight = height;
        artBoardWidth = width;
        artBoardHeight = height;
        initial();
    }

    private void initial() {
        initPaint();
        initCanvas();
    }

    public void setDrag(boolean drag) {
        this.drag = drag;
    }

    public void setDrawType(int type) {
        this.currentDrawGraphics = type;
        initPaint();
    }

    public int getDrawType() {
        return currentDrawGraphics;
    }

    public void setPaintWidth(float width) {
        paintWidth = width;
        initPaint();
    }

    public float getPaintWidth() {
        return paintWidth;
    }

    public void setPaintColor(int color) {
        paintColor = color;
        initPaint();
    }

    public int getPaintColor() {
        return paintColor;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!isCreate) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            if (widthMode == MeasureSpec.AT_MOST && heightMode == MeasureSpec.AT_MOST) {
                setMeasuredDimension(WRAP_WIDTH, WRAP_HEIGHT);
            } else if (widthMode == MeasureSpec.AT_MOST) {
                setMeasuredDimension(WRAP_WIDTH, height);
            } else if (heightMode == MeasureSpec.AT_MOST) {
                setMeasuredDimension(width, WRAP_HEIGHT);
            }
            artBoardWidth = width;
            artBoardHeight = height;
            initial();
        }
    }

    public void initCanvas() {
        mBitmapPaint = new Paint(Paint.DITHER_FLAG);
        mBitmap = Bitmap.createBitmap(artBoardWidth, artBoardHeight, Bitmap.Config.ARGB_8888);
        mBitmap.eraseColor(Color.TRANSPARENT);
        mCanvas = new Canvas(mBitmap);
        mCanvas.drawColor(Color.TRANSPARENT);//设置画布的颜色
    }

    private void initPaint() {
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(true);// 抗锯齿
        mPaint.setDither(true);// 防抖动
        mPaint.setStrokeJoin(Paint.Join.ROUND);// 设置线段连接处的样式为圆弧连接
        mPaint.setStrokeCap(Paint.Cap.ROUND);// 设置两端的线帽为圆的
        mPaint.setStrokeWidth(paintWidth);// 画笔宽度
        switch (currentDrawGraphics) {
            case DRAW_SLINE:
            case DRAW_CIRCLE:
            case DRAW_RECT:
            case DRAW_LINE:
            case DRAW_TEXT:
                mPaint.setColor(paintColor);// 颜色
                break;
            case DRAW_ERASER:
                mPaint.setColor(Color.WHITE);
                break;
        }
    }

    public Bitmap getCanvasBmp() {
        Bitmap srcBitmap = mBitmap;
        return srcBitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 在之前画板上画过得显示出来
        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        if (mPath != null) {
            canvas.drawPath(mPath, mPaint);//实时的显示
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (drag) {
            drag(event);
        } else {
            draw(event);
        }
        return true;
    }

    /**
     * 拖动时按下
     */
    private float downX, downY;
    private int l = 0, t = 0, r, b;

    private void drag(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float moveX = event.getX();
                float moveY = event.getY();
                //负数,说明是向左滑动
                float dx = moveX - downX;
                //负数,说明是向上滑动
                float dy = moveY - downY;
                int left = getLeft();
                int top = getTop();
                //左
                if (left == 0) {
                    if (dx < 0 && getRight() >= screenWidth) {
                        l = (int) (left + dx);
                    } else {
                        l = 0;
                    }
                } else if (left < 0) {
                    if (getRight() >= screenWidth) {
                        if (dx < 0) {
                            l = (int) (left + dx);
                        } else if (dx > 0) {
                            l = (int) (left + dx);
                        }
                    }
                } else {
                    l = 0;
                }
                //上
                if (top == 0) {
                    if (dy < 0 && getBottom() > screenHeight) {
                        t = (int) (top + dy);
                    } else {
                        t = 0;
                    }
                } else if (top < 0) {
                    if (dy < 0 && getBottom() > screenHeight) {
                        t = (int) (top + dy);
                    } else if (dy > 0) {
                        t = (int) (top + dy);
                    }
                } else {
                    t = 0;
                }
                //右
                r = artBoardWidth + l;
                int i1 = screenWidth - r;
                if (i1 > 0) {
                    r = screenWidth;
                    l += i1;
                }
                //下
                b = artBoardHeight + t;
                int i = screenHeight - b;
                if (i > 0) {
                    b = screenHeight;
                    t += i;
                }
                this.layout(l, t, r, b);
//                currentArtBoardWidth = r - l;
//                currentArtBoardHeight = b - t;
//                LogUtil.d(TAG, "drag -->" + "拖动后画板的宽高：" + currentArtBoardWidth + " , " + currentArtBoardHeight);
                break;
            default:
                break;
        }
    }

    private float startX, startY;
    /**
     * 临时坐标点
     */
    private float tempX, tempY;

    private void draw(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                mPath = new Path();
                mPath.moveTo(x, y);
                tempX = x;
                tempY = y;
                initPaint();
                //只有绘制曲线的时候才去添加
                if (currentDrawGraphics == DRAW_SLINE) {
                    //添加按下时的点
                    points.add(new Point(x, y));
                }
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                switch (currentDrawGraphics) {
                    //曲线
                    case DRAW_SLINE:
                        //添加移动时的点
                        points.add(new Point(x, y));
                        drawSLine(x, y);
                        break;
                    //圆
                    case DRAW_CIRCLE:
                        drawOval(x, y);
                        break;
                    //矩形
                    case DRAW_RECT:
                        drawRect(x, y);
                        break;
                    //直线
                    case DRAW_LINE:
                        drawLine(x, y);
                        break;
                    //橡皮搽
                    case DRAW_ERASER:
                        eraserPath(x, y);
                        if (MupdfConfig.delete_history_annotation && core != null && docView != null) {
                            core.deleteAnnotation(docView, screenWidth, screenHeight, x, y);
                        }
                        break;
                    default:
                        break;
                }
                tempX = x;
                tempY = y;
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                long utcstamp = System.currentTimeMillis();
                int operid = (int) (utcstamp / 10);
                if (currentDrawGraphics == DRAW_LINE) {
                    Point[] array = new Point[2];
                    array[0] = new Point(startX, startY);
                    array[1] = new Point(x, y);
                    AnnotationBean annotationBean = new AnnotationBean(operid, PDFAnnotation.TYPE_LINE, array, paintWidth, paintColor);
                    annotationBeans.add(annotationBean);
                } else if (currentDrawGraphics == DRAW_RECT) {
                    Point[] array = new Point[2];
                    array[0] = new Point(startX, startY);
                    array[1] = new Point(x, y);
                    AnnotationBean annotationBean = new AnnotationBean(operid, PDFAnnotation.TYPE_SQUARE, array, paintWidth, paintColor);
                    annotationBeans.add(annotationBean);
                } else {
                    Point[] array = list2array(points);
                    AnnotationBean annotationBean = new AnnotationBean(operid, PDFAnnotation.TYPE_INK, array, paintWidth, paintColor);
                    annotationBeans.add(annotationBean);
                }
                if (currentDrawGraphics != DRAW_TEXT && currentDrawGraphics != DRAW_ERASER) {
                    drawPath = new DrawPath();
                    drawPath.operid = operid;
                    drawPath.path = new Path(mPath);
                    drawPath.paint = new Paint(mPaint);
                    pathList.add(drawPath);
                }
                if (currentDrawGraphics != DRAW_ERASER) {
                    mCanvas.drawPath(mPath, mPaint);
                    mPath = null;
                    invalidate();
                }
                //不管有没有同屏都要删除
                points.clear();
                drawPath = null;
                break;
            default:
                break;
        }
    }

    private Point[] list2array(List<Point> points) {
        Point[] array = new Point[points.size()];
        for (int i = 0; i < points.size(); i++) {
            array[i] = points.get(i);
        }
        return array;
    }

    private void deleteAnnotationBean(int key) {
        for (AnnotationBean next : annotationBeans) {
            if (next.key == key) {
                next.setDeleted(true);
                return;
            }
        }
    }

    private void reAddAnnotationBean(int key) {
        for (AnnotationBean next : annotationBeans) {
            if (next.key == key) {
                next.setDeleted(false);
                return;
            }
        }
    }


    /**
     * 正常橡皮搽功能
     */
    private void eraser(float x, float y) {
        initPaint();
        drawSLine(x, y);
    }

    private void drawLine(float x, float y) {
        float dx = Math.abs(x - tempX);
        float dy = Math.abs(tempY - y);
        if (dx >= DIFFERENCE || dy >= DIFFERENCE) {
            mPath.reset();
            mPath.moveTo(startX, startY);
            mPath.lineTo(x, y);
        }
    }

    private void drawRect(float x, float y) {
        float dx = Math.abs(x - tempX);
        float dy = Math.abs(tempY - y);
        float sx = startX;//关键代码,
        float sy = startY;//每次进入都保存好起点坐标
        if (dx >= DIFFERENCE || dy >= DIFFERENCE) {
            mPath.reset();
            if (sx > x) {
                float a = sx;
                sx = x;
                x = a;
            }
            if (sy > y) {
                float b = sy;
                sy = y;
                y = b;
            }
            RectF rectF = new RectF(sx, sy, x, y);
            mPath.addRect(rectF, Path.Direction.CCW);
        }
    }

    private void drawOval(float x, float y) {
        float dx = Math.abs(x - tempX);
        float dy = Math.abs(tempY - y);
        if (dx >= DIFFERENCE || dy >= DIFFERENCE) {
            mPath.reset();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mPath.addOval(startX, startY, x, y, Path.Direction.CCW);
            } else {
                float r = Math.abs(y - startY) / 2;
                float rx = x - ((x - startX) / 2);//有可能相减是负数
                float ry = y - ((y - startY) / 2);
                mPath.addCircle(rx, ry, r, Path.Direction.CCW);
            }
        }
    }

    private void drawSLine(float x, float y) {
        float dx = Math.abs(x - tempX);
        float dy = Math.abs(tempY - y);
        if (dx >= DIFFERENCE || dy >= DIFFERENCE) {
            mPath.quadTo(tempX, tempY, (tempX + x) / 2, (tempY + y) / 2);
        }
    }


    /**
     * 设置画板大小
     */
    public void setCanvasSize(int w, int h) {
        if (w > artBoardWidth || h > artBoardHeight) {
            if (w > artBoardWidth) {
                artBoardWidth = w;
            }
            if (h > artBoardHeight) {
                artBoardHeight = h;
            }
        } else {
            return;
        }
        initCanvas();
        drawAgain(pathList);
    }

    /**
     * 清空
     */
    public void clear() {
        initCanvas();
        invalidate();
        //已经退出共享后，点击清除将所有操作清空
        pathList.clear();
    }

    /**
     * 共享中橡皮檫
     */
    private void eraserPath(float x, float y) {
        boolean isChanged = false;
        for (int i = 0; i < pathList.size(); i++) {
            DrawPath drawPath = pathList.get(i);
            if (drawPath.isDelete) continue;//已经设置成删除状态的进行过滤掉
            PathMeasure pm = new PathMeasure(drawPath.path, false);
            float length = pm.getLength();
            Path tempPath = new Path();
            pm.getSegment(0, length, tempPath, false);
            float[] fa = new float[2];
            float sc = 0;
            while (sc < 1) {
                sc += 0.001;
                pm.getPosTan(sc * length, fa, null);
                if (Math.abs((int) fa[0] - (int) x) <= 20 && Math.abs((int) fa[1] - (int) y) <= 20) {
                    sc = 1;//找到了退出while循环
                    isChanged = true;
                    initCanvas();
                    drawPath.setDeleted(true, i);
                    pathList.remove(drawPath);//删除指定drawpath
                    //也要进行删除待绘制集合中的数据
                    deleteAnnotationBean(drawPath.operid);
                    pathList.add(drawPath);
                }
            }
        }
        if (isChanged) {
            drawAgain(pathList);
        }
    }

    /**
     * 撤销,删除本机最后的一次操作
     */
    public void revoke() {
        if (!pathList.isEmpty()) {
            initCanvas();
            int index = pathList.size() - 1;
            DrawPath drawPath = pathList.get(index);
            if (drawPath.isDelete) {
                int deleteIndex = drawPath.deleteIndex;
                drawPath.setDeleted(false, -1);
                pathList.remove(index);
                pathList.add(deleteIndex, drawPath);
                reAddAnnotationBean(drawPath.operid);
            } else {
                pathList.remove(index);
                if (!annotationBeans.isEmpty()) {
                    annotationBeans.remove(annotationBeans.size() - 1);
                }
            }
            //将其他人的绘制信息也重新绘制
            drawAgain(pathList);
        }
    }

    /**
     * 存在一个问题，如果先绘制路径再绘制图片则会被图片覆盖掉
     *
     * @param pathList 存放绘制的信息
     */
    public void drawAgain(List<DrawPath> pathList) {
        for (DrawPath next : pathList) {
            if (next.isDelete) continue;
            if (next.paint != null) {
                if (next.path != null) {
                    mCanvas.drawPath(next.path, next.paint);
                } else if (next.text != null) {
                    if (next.lw < next.allTextWidth) {
                        funDraw(next.paint, next.height, next.cansee, (int) next.pointF.x, (int) next.pointF.y, next.text);
                    } else {
                        mCanvas.drawText(next.text, next.pointF.x, next.pointF.y, next.paint);
                    }
                }
            } /*else if (next.picdata != null) {
                drawAgain = true;
                byte[] bytes = next.picdata.toByteArray();
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                drawZoomBmp(bitmap);
            }*/
        }
        invalidate();
    }

    public void funDraw(Paint paint, float height, int canSee, float fx, float fy, String text) {
        if (text.length() > canSee) {
            String canSeeText = text.substring(0, canSee);
            mCanvas.drawText(canSeeText, fx, fy, paint);
            String substring = text.substring(canSee);//获得剩下无法显示的文本
            funDraw(paint, height, canSee, fx, fy + height, substring);
        } else {
            mCanvas.drawText(text, fx, fy, paint);
        }
    }


    public void setCancelAnnotation() {
        isCancelAnnotation = true;
    }

    public static class DrawPath {
        public boolean isDelete;//=true 表示橡皮擦操作
        public int deleteIndex;//进行删除操作的操作列表中的索引位
        public Paint paint; //画笔
        public Path path = null; //路径
        public int operid;//操作ID

        public String text = null;//绘制的文本
        public PointF pointF;//添加文本的起点（x,y）
        public int height;//文本的高度（每个字的高度）
        public int cansee;//可以显示的文本的个数
        public int lw;//可容许显示文本的区域宽度
        public int allTextWidth;//所有文本的宽度

        public void setDeleted(boolean value, int index) {
            isDelete = value;
            deleteIndex = index;
        }

        @Override
        public String toString() {
            return "DrawPath{" +
                    "isDelete=" + isDelete +
                    ", deleteIndex=" + deleteIndex +
                    ", paint=" + paint +
                    ", path=" + path +
                    ", operid=" + operid +
                    ", text='" + text + '\'' +
                    ", pointF=" + pointF +
                    ", height=" + height +
                    ", cansee=" + cansee +
                    ", lw=" + lw +
                    ", allTextWidth=" + allTextWidth +
                    '}';
        }
    }

    public void setDrawExitListener(DrawExitListener listener) {
        mDrawExitListener = listener;
    }

    public interface DrawExitListener {
        void onDrawAnnotations(List<AnnotationBean> inkAnnotations);
    }

    public List<AnnotationBean> getAnnotationBeans() {
        return annotationBeans;
    }

    public void release() {
        Log.i(TAG, "AnnotationArtBoard.release: mDrawExitListener是否为null：" + (mDrawExitListener == null));
        if (mDrawExitListener != null && !isCancelAnnotation) {
            //去掉删除状态的
            Iterator<AnnotationBean> iterator = annotationBeans.iterator();
            while (iterator.hasNext()) {
                AnnotationBean next = iterator.next();
                if (next.isDeleted()) {
                    iterator.remove();
                }
            }
            mDrawExitListener.onDrawAnnotations(annotationBeans);
        }
        if (mBitmap != null) {
            if (!mBitmap.isRecycled()) {
                mBitmap.recycle();
                Log.i(TAG, "AnnotationArtBoard.release:回收bitmap");
            }
        }
        if (mCanvas != null) {
            mCanvas = null;
        }
    }
}
