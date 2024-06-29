package com.xlk.mupdf.library.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Region;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * 可拖动缩放的签名控件
 */
public class ScalableView extends View implements View.OnTouchListener {
    private static final String TAG = "ScalableView";
    private final int parentW, parentH;
    private final int viewWidth, viewHeight;
    private Paint mPaint;
    private Paint dottedLinePaint;
    private List<SignatureBoard.DrawPath> drawPaths;
    private final Region scaleRegion;
    boolean isZoomEnabled = false;
    private float lastTouchX, lastTouchY;
    private final Path mPath = new Path();
    private final Path mAreaPath = new Path();
    private float tempX, tempY;
    /**
     * x和y轴的缩放比例
     */
    private float scaleFactorX = 1.0f, scaleFactorY = 1.0f;

    /**
     *
     * @param context 上下文
     * @param drawPaths 签名绘制的点集合
     * @param signatureLeft 签名各个方位的值
     * @param signatureTop
     * @param signatureRight
     * @param signatureBottom
     * @param l 未拖动时需要定义方位
     * @param t
     * @param r
     * @param b
     * @param parentW 父控件的宽
     * @param parentH 父控件的高
     */
    public ScalableView(Context context, List<SignatureBoard.DrawPath> drawPaths,
                        float signatureLeft, float signatureTop, float signatureRight, float signatureBottom,
                        int l, int t, int r, int b,
                        int parentW, int parentH) {
        super(context);
        this.parentW = parentW;
        this.parentH = parentH;
        this.drawPaths = drawPaths;
        // 坐标点，最左侧的x应该=0，最顶部的y应该=0；最右侧的x应该=signatureRight - signatureLeft，最底部的y应该=signatureBottom - signatureTop
        for (SignatureBoard.DrawPath drawPath : this.drawPaths) {
            //重新设置每个点的坐标
            for (PointF point : drawPath.points) {
                //最左和最顶部则变成（0,0），添加了10的间距
                point.x = point.x - signatureLeft + 10;
                point.y = point.y - signatureTop + 10;
            }
        }
        // 左上右下都分别添加10的间距
        this.viewWidth = (int) (signatureRight - signatureLeft) + 20;
        this.viewHeight = (int) (signatureBottom - signatureTop) + 20;
        this.l=l;
        this.t=t;
        this.r=r;
        this.b=b;
        Log.d(TAG, "ScalableView 宽高：" + viewWidth + "," + viewHeight);
        //定义可拖动缩放的区域
        scaleRegion = new Region(viewWidth - 50, viewHeight - 50, viewWidth, viewHeight);
        initPaint();
        setOnTouchListener(this);
    }

    private void initPaint() {
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(true);// 抗锯齿
        mPaint.setDither(true);// 防抖动
        mPaint.setStrokeJoin(Paint.Join.ROUND);// 设置线段连接处的样式为圆弧连接
        mPaint.setStrokeCap(Paint.Cap.ROUND);// 设置两端的线帽为圆的
        mPaint.setStrokeWidth(5);// 画笔宽度
        mPaint.setColor(Color.BLACK);// 颜色

        dottedLinePaint = new Paint();
        dottedLinePaint.setStyle(Paint.Style.STROKE);
        dottedLinePaint.setAntiAlias(true);// 抗锯齿
        dottedLinePaint.setDither(true);// 防抖动
        dottedLinePaint.setStrokeWidth(5);// 画笔宽度
        dottedLinePaint.setColor(Color.GREEN);
        //数组值10表示虚线长度，值20表示实现长度
        dottedLinePaint.setPathEffect(new DashPathEffect(new float[]{10, 20}, 0));
    }

    /**
     * 获取当前的绘制信息
     */
    public List<SignatureBoard.DrawPath> getDrawPaths() {
        for (SignatureBoard.DrawPath drawPath : drawPaths) {
            for (int i = 0; i < drawPath.points.length; i++) {
                drawPath.points[i].x = drawPath.points[i].x * scaleFactorX + l;
                drawPath.points[i].y = drawPath.points[i].y * scaleFactorY + t;
            }
        }
        return drawPaths;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //四周绘制虚线进行包裹
        drawBorder(canvas);
        //绘制路径
        drawPath(canvas);
        //绘制缩放拖动区域
        drawScaleArea(canvas);
    }

    private void drawBorder(Canvas canvas) {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        canvas.drawLine(0, 0, viewWidth, 0, dottedLinePaint);
        canvas.drawLine(0, 0, 0, viewHeight, dottedLinePaint);
        canvas.drawLine(viewWidth, 0, viewWidth, viewHeight, dottedLinePaint);
        canvas.drawLine(0, viewHeight, viewWidth, viewHeight, dottedLinePaint);
    }

    private void drawPath(Canvas canvas) {
        //重置路径
        mPath.reset();
        mPaint.setColor(Color.RED);
        canvas.save();
        for (SignatureBoard.DrawPath drawPath : drawPaths) {
            for (int i = 0; i < drawPath.points.length; i++) {
                float x = drawPath.points[i].x * scaleFactorX;
                float y = drawPath.points[i].y * scaleFactorY;
                if (i == 0) {
                    //Log.i(TAG, "moveTo:" + x + "," + y + ",scaleFactor:" + scaleFactor);
                    mPath.moveTo(x, y);
                } else {
                    //Log.d(TAG, "quadTo:" + tempX + "," + tempY);
                    mPath.quadTo(tempX, tempY, (tempX + x) / 2, (tempY + y) / 2);
                }
                tempX = x;
                tempY = y;
                if (i == drawPath.points.length - 1) {
                    //Log.e(TAG, "---drawPath--- scaleFactor:" + scaleFactor);
                    canvas.drawPath(mPath, mPaint);
                }
            }
        }
    }

    private void drawScaleArea(Canvas canvas) {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        mAreaPath.reset();//重置路径
        canvas.save();
        //绘制缩放区域
        mPaint.setColor(isZoomEnabled ? Color.RED : Color.YELLOW);
        mAreaPath.moveTo(viewWidth, viewHeight - 40f);
        mAreaPath.lineTo(viewWidth, viewHeight);
        mAreaPath.lineTo(viewWidth - 40f, viewHeight);

        mAreaPath.moveTo(viewWidth - 20, viewHeight - 30f);
        mAreaPath.lineTo(viewWidth - 20, viewHeight - 20);
        mAreaPath.lineTo(viewWidth - 30f, viewHeight - 20);

        canvas.drawPath(mAreaPath, mPaint);
        canvas.restore();
    }

    private int l, t, r, b;

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                lastTouchX = event.getRawX();
                lastTouchY = event.getRawY();
                isZoomEnabled = scaleRegion.contains((int) event.getX(), (int) event.getY());
                l = getLeft();
                t = getTop();
                r = getRight();
                b = getBottom();
                Log.d(TAG, "是否缩放：" + isZoomEnabled + ",坐标：" + getX() + "," + getY());
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final float x = event.getRawX();
                final float y = event.getRawY();
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;
                if (isZoomEnabled) {
                    //等比缩放
                    if (dx != 0 || dy != 0) {
                        if (dx != dy) {
                            if (Math.abs(dx) > Math.abs(dy)) {
                                //拖动x轴多点
                                dy = dx;
                            } else {
                                //拖动y轴多点
                                dx = dy;
                            }
                        }
                    }
                    if (dx == dy) {
                        ViewGroup.LayoutParams layoutParams = (ViewGroup.LayoutParams) getLayoutParams();
                        float newWidth = layoutParams.width + dx;
                        float newHeight = layoutParams.height + dy;

                        // 计算缩放比例，要跟最开始的宽高对比
                        scaleFactorX = newWidth / viewWidth;
                        scaleFactorY = newHeight / viewHeight;

                        layoutParams.width = (int) newWidth;
                        layoutParams.height = (int) newHeight;
                        //实时更新大小
                        view.setLayoutParams(layoutParams);

                        //更新位置
                        float left = l;
                        float top = t;
                        float right = l + newWidth;
                        float bottom = t + newHeight;

                        if (right >= parentW) {
                            right = parentW;
                            left = parentW - newWidth;
                        }
                        if (bottom >= parentH) {
                            bottom = parentH;
                            top = parentH - newHeight;
                        }
                        l = (int) left;
                        t = (int) top;
                        r = (int) right;
                        b = (int) bottom;
                        view.layout(l, t, r, b);
                        view.requestLayout();
                    }
                } else {
                    //更新位置
                    float left = l + dx;
                    float top = t + dy;
                    float right = r + dx;
                    float bottom = b + dy;
                    if (left <= 0) {
                        left = 0;
                        right = getWidth();
                    }
                    if (top <= 0) {
                        top = 0;
                        bottom = getHeight();
                    }
                    if (right >= parentW) {
                        right = parentW;
                        left = parentW - getWidth();
                    }
                    if (bottom >= parentH) {
                        bottom = parentH;
                        top = parentH - getHeight();
                    }
                    l = (int) left;
                    t = (int) top;
                    r = (int) right;
                    b = (int) bottom;
                    view.layout(l, t, r, b);
                }

                //更新缩放区域
                scaleRegion.set(getWidth() - 50, getHeight() - 50,
                        getWidth(), getHeight());

                lastTouchX = x;
                lastTouchY = y;

                invalidate();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                isZoomEnabled = false;
                invalidate();
                break;
            }
        }
        return true;
    }
}