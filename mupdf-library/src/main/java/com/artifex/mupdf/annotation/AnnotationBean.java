package com.artifex.mupdf.annotation;

import com.artifex.mupdf.fitz.Point;

/**
 * @author : Administrator
 * @date : 2023/5/10 14:16
 * @description :
 */
public class AnnotationBean {
    int key;
    /**
     * 批注类型 {@link com.artifex.mupdf.fitz.PDFAnnotation#TYPE_LINE}
     */
    int type;
    Point[] points;
    float paintSize;
    int paintColor;

    public AnnotationBean(int key, int type, Point[] points, float paintSize, int paintColor) {
        this.key = key;
        this.type = type;
        this.points = points;
        this.paintSize = paintSize;
        this.paintColor = paintColor;
    }

    public int getKey() {
        return key;
    }

    public int getType() {
        return type;
    }

    public Point[] getPoints() {
        return points;
    }

    public float getPaintSize() {
        return paintSize;
    }

    public int getPaintColor() {
        return paintColor;
    }
}
