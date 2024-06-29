package com.xlk.mupdf.library.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.artifex.mupdf.util.LogUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 专门用于签名的画板
 *
 * @author : Administrator
 * created on 2024/6/19 14:33
 */
public class SignatureBoard extends View {
    private final int WRAP_WIDTH = 300;
    private final int WRAP_HEIGHT = 300;
    private int artBoardWidth, artBoardHeight;
    private Paint mPaint;
    private Paint mBitmapPaint;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Path mPath;
    private final List<PointF> points = new ArrayList<>();
    private final List<DrawPath> drawPaths = new ArrayList<>();
    private int mPaintSize = 5;

    public void setPaintSize(int size) {
        mPaintSize = size;
        mPaint.setStrokeWidth(size);
        invalidate();
    }

    public static class DrawPath {
        public final PointF[] points;
        public final Path path;

        public DrawPath(PointF[] points, Path path) {
            this.points = points;
            this.path = path;
        }
    }

    public SignatureBoard(Context context) {
        this(context, null);
    }

    public SignatureBoard(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignatureBoard(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void initPaint() {
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(true);// 抗锯齿
        mPaint.setDither(true);// 防抖动
        mPaint.setStrokeJoin(Paint.Join.ROUND);// 设置线段连接处的样式为圆弧连接
        mPaint.setStrokeCap(Paint.Cap.ROUND);// 设置两端的线帽为圆的
        mPaint.setStrokeWidth(mPaintSize);// 画笔宽度
        mPaint.setColor(Color.BLACK);// 颜色
    }

    private void initCanvas() {
        mBitmapPaint = new Paint(Paint.DITHER_FLAG);
        mBitmap = Bitmap.createBitmap(artBoardWidth, artBoardHeight, Bitmap.Config.ARGB_8888);
        mBitmap.eraseColor(Color.TRANSPARENT);
        mCanvas = new Canvas(mBitmap);
        mCanvas.drawColor(Color.TRANSPARENT);//设置画布的颜色
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
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
        initPaint();
        initCanvas();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 在之前画板上画过得显示出来
        canvas.drawBitmap(mBitmap, 0, 0, mBitmapPaint);
        if (mPath != null) {
            canvas.drawPath(mPath, mPaint);//实时的显示
        }
    }

    /**
     * 临时坐标点
     */
    private float tempX, tempY;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mPath = new Path();
                mPath.moveTo(x, y);
                tempX = x;
                tempY = y;
                //添加按下时的点
                points.add(new PointF(x, y));
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                //添加移动时的点
                points.add(new PointF(x, y));
                float dx = Math.abs(x - tempX);
                float dy = Math.abs(tempY - y);
                if (dx >= 1 || dy >= 1) {
                    mPath.quadTo(tempX, tempY, (tempX + x) / 2, (tempY + y) / 2);
                }
                tempX = x;
                tempY = y;
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                PointF[] array = list2array(points);
                drawPaths.add(new DrawPath(array, mPath));
                mCanvas.drawPath(mPath, mPaint);
                mPath = null;
                invalidate();
                //不管有没有同屏都要删除
                points.clear();
                break;
            default:
                break;
        }
        return true;
    }

    private PointF[] list2array(List<PointF> points) {
        PointF[] array = new PointF[points.size()];
        for (int i = 0; i < points.size(); i++) {
            array[i] = points.get(i);
        }
        return array;
    }

    public Bitmap getCanvasBmp() {
        Bitmap srcBitmap = mBitmap;
        return srcBitmap;
    }

    /**
     * 获取签名所占领的区域
     */
    public RectF getRegionSize() {
        RectF size = new RectF();
        for (DrawPath drawPath : drawPaths) {
            for (PointF point : drawPath.points) {
                float x = point.x;
                float y = point.y;
                size.left = size.left == 0 ? x : Math.min(size.left, x);
                size.top = size.top == 0 ? y : Math.min(size.top, y);
                size.right = size.right == 0 ? x : Math.max(size.right, x);
                size.bottom = size.bottom == 0 ? y : Math.max(size.bottom, y);
            }
        }
        LogUtils.i("区域：" + size);
        return size;
    }

    public List<DrawPath> getDrawPaths() {
        return drawPaths;
    }

    public boolean isNotEmpty() {
        return drawPaths.size() > 0;
    }

    public void revoke() {
        if (!drawPaths.isEmpty()) {
            drawPaths.remove(drawPaths.size() - 1);
            initCanvas();
            for (DrawPath drawPath : drawPaths) {
                mCanvas.drawPath(drawPath.path, mPaint);
            }
            invalidate();
        }
    }

    public void clear() {
        initCanvas();
        drawPaths.clear();
        invalidate();
    }
}
