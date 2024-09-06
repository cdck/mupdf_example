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
    boolean isDeleted = false;

    public AnnotationBean(int key, int type, Point[] points, float paintSize, int paintColor) {
        this.key = key;
        this.type = type;
        this.points = points;
        this.paintSize = paintSize;
        this.paintColor = paintColor;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
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
