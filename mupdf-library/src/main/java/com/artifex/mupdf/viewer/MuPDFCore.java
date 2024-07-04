package com.artifex.mupdf.viewer;

import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_INK;
import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_LINE;
import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_SCREEN;
import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_SQUARE;
import static com.artifex.mupdf.fitz.PDFAnnotation.TYPE_WATERMARK;
import static com.xlk.mupdf.library.Config.TAG;

import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.graphics.RectF;

import com.artifex.mupdf.fitz.Cookie;
import com.artifex.mupdf.fitz.DisplayList;
import com.artifex.mupdf.fitz.Document;
import com.artifex.mupdf.fitz.Link;
import com.artifex.mupdf.fitz.Matrix;
import com.artifex.mupdf.fitz.Outline;
import com.artifex.mupdf.fitz.PDFAnnotation;
import com.artifex.mupdf.fitz.PDFDocument;
import com.artifex.mupdf.fitz.PDFPage;
import com.artifex.mupdf.fitz.Page;
import com.artifex.mupdf.fitz.Point;
import com.artifex.mupdf.fitz.Quad;
import com.artifex.mupdf.fitz.Rect;
import com.artifex.mupdf.fitz.RectI;
import com.artifex.mupdf.fitz.SeekableInputStream;
import com.artifex.mupdf.fitz.android.AndroidDrawDevice;
import com.artifex.mupdf.util.LogUtils;
import com.artifex.mupdf.util.Util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MuPDFCore {
    private int resolution;
    private Document doc;
    private Outline[] outline;
    private int pageCount = -1;
    private int currentPage;
    private Page page;
    private float pageWidth;
    private float pageHeight;
    private DisplayList displayList;

    /* Default to "A Format" pocket book size. */
    private int layoutW = 312;
    private int layoutH = 504;
    private int layoutEM = 10;

    private MuPDFCore(Document doc) {
        this.doc = doc;
        doc.layout(layoutW, layoutH, layoutEM);
        pageCount = doc.countPages();
        resolution = 160;
        currentPage = -1;
    }

    public MuPDFCore(byte buffer[], String magic) {
        this(Document.openDocument(buffer, magic));
    }

    public MuPDFCore(String filePath) {
        this(Document.openDocument(filePath));
    }

    public MuPDFCore(SeekableInputStream stm, String magic) {
        this(Document.openDocument(stm, magic));
    }

    public String getTitle() {
        return doc.getMetaData(Document.META_INFO_TITLE);
    }

    public int countPages() {
        return pageCount;
    }

    public synchronized boolean isReflowable() {
        return doc.isReflowable();
    }

    public synchronized int layout(int oldPage, int w, int h, int em) {
        if (w != layoutW || h != layoutH || em != layoutEM) {
//            LogUtils.i(TAG, "MuPDFCore.layout: " + w + "," + h + ",em:" + em);
            layoutW = w;
            layoutH = h;
            layoutEM = em;
            long mark = doc.makeBookmark(doc.locationFromPageNumber(oldPage));
            doc.layout(layoutW, layoutH, layoutEM);
            currentPage = -1;
            pageCount = doc.countPages();
            outline = null;
            try {
                outline = doc.loadOutline();
            } catch (Exception ex) {
                /* ignore error */
            }
            return doc.pageNumberFromLocation(doc.findBookmark(mark));
        }
        return oldPage;
    }

    private synchronized void gotoPage(int pageNum) {
        /* TODO: page cache */
        try {
            if (pageNum > pageCount - 1)
                pageNum = pageCount - 1;
            else if (pageNum < 0)
                pageNum = 0;
            if (pageNum != currentPage) {
                if (page != null) {
                    try {
                        page.destroy();
                    } catch (Exception e) {
                        LogUtils.e("page destroy 异常："+e);
                    }
                }
                page = null;
                if (displayList != null)
                    displayList.destroy();
                displayList = null;
                page = null;
                pageWidth = 0;
                pageHeight = 0;
                currentPage = -1;

                if (doc != null) {
                    page = doc.loadPage(pageNum);
                    Rect b = page.getBounds();
                    pageWidth = b.x1 - b.x0;
                    pageHeight = b.y1 - b.y0;
                    LogUtils.i(TAG, "gotoPage: pageNum:" + pageNum + ",pageWidth:" + pageWidth + ",pageHeight:" + pageHeight);
                }

                currentPage = pageNum;
            }
        } catch (Exception e) {
            LogUtils.e(e);
        }
    }

    public synchronized PointF getPageSize(int pageNum) {
        gotoPage(pageNum);
        return new PointF(pageWidth, pageHeight);
    }

    public synchronized void onDestroy() {
        LogUtils.i(TAG, "onDestroy: ");
        if (displayList != null)
            displayList.destroy();
        displayList = null;
        if (page != null)
            page.destroy();
        page = null;
        if (doc != null)
            doc.destroy();
        doc = null;
    }

    public synchronized void drawPage(Bitmap bm, int pageNum,
                                      int pageW, int pageH,
                                      int patchX, int patchY,
                                      int patchW, int patchH,
                                      Cookie cookie) {
        gotoPage(pageNum);

        if (displayList == null && page != null)
            try {
                displayList = page.toDisplayList();
            } catch (Exception ex) {
                displayList = null;
            }

        if (displayList == null || page == null)
            return;
        float zoom = resolution / 72;
        Matrix ctm = new Matrix(zoom, zoom);
        Rect bounds = page.getBounds();
        RectI bbox = new RectI(bounds.transform(ctm));
        float xscale = (float) pageW / (float) (bbox.x1 - bbox.x0);
        float yscale = (float) pageH / (float) (bbox.y1 - bbox.y0);
        ctm.scale(xscale, yscale);
        LogUtils.e(TAG, "MuPDFCore.drawPage: pageNum:" + pageNum
                + "\npageW:" + pageW + ",pageH:" + pageH
                + "\npatchW:" + patchW + ",patchH:" + patchH
                + "\npatchX:" + patchX + ",patchY:" + patchY
                + "\nbounds:" + bounds
                + "\nbbox:" + bbox
                + "\nctm:" + ctm
                + "\nxscale:" + xscale + ",yscale:" + yscale
                + "\nbm:" + bm.getWidth() + "," + bm.getHeight());
        try {
            throw new Exception("哪里调用");
        } catch (Exception e) {
            LogUtils.e(e);
        }
        AndroidDrawDevice dev = new AndroidDrawDevice(bm, patchX, patchY);
        try {
            displayList.run(dev, ctm, cookie);
            dev.close();
        } finally {
            dev.destroy();
        }
    }

    public synchronized void updatePage(Bitmap bm, int pageNum,
                                        int pageW, int pageH,
                                        int patchX, int patchY,
                                        int patchW, int patchH,
                                        Cookie cookie) {
        drawPage(bm, pageNum, pageW, pageH, patchX, patchY, patchW, patchH, cookie);
    }

    public synchronized Link[] getPageLinks(int pageNum) {
        gotoPage(pageNum);
        return page != null ? page.getLinks() : null;
    }

    public synchronized int resolveLink(Link link) {
        return doc.pageNumberFromLocation(doc.resolveLink(link));
    }

    public synchronized Quad[][] searchPage(int pageNum, String text) {
        gotoPage(pageNum);
        return page.search(text);
    }

    public synchronized boolean hasOutline() {
        if (outline == null) {
            try {
                outline = doc.loadOutline();
            } catch (Exception ex) {
                /* ignore error */
            }
        }
        return outline != null;
    }

    private void flattenOutlineNodes(ArrayList<OutlineActivity.Item> result, Outline list[], String indent) {
        for (Outline node : list) {
            if (node.title != null) {
                int page = doc.pageNumberFromLocation(doc.resolveLink(node));
                result.add(new OutlineActivity.Item(indent + node.title, page));
            }
            if (node.down != null)
                flattenOutlineNodes(result, node.down, indent + "    ");
        }
    }

    public synchronized ArrayList<OutlineActivity.Item> getOutline() {
        ArrayList<OutlineActivity.Item> result = new ArrayList<OutlineActivity.Item>();
        flattenOutlineNodes(result, outline, "");
        return result;
    }

    public synchronized boolean needsPassword() {
        return doc.needsPassword();
    }

    public synchronized boolean authenticatePassword(String password) {
        return doc.authenticatePassword(password);
    }

    public void addWaterMark(int pageNum) {
        Page page = doc.loadPage(pageNum);
//        Rect bounds = page.getBounds();
//        float realWidth = bounds.x1 - bounds.x0;
//        float realHeight = bounds.y1 - bounds.y0;
//        for (Point point : inkList) {
//            point.x = realWidth / width * point.x;
//            point.y = realHeight / height * point.y;
//        }
        PDFPage pdfPage = new PDFPage(page.pointer);
        PDFAnnotation annotation = pdfPage.createAnnotation(TYPE_WATERMARK);
        annotation.setBorderWidth(1f);
        annotation.setContents("测试水印");
        boolean update = annotation.update();
        boolean update1 = pdfPage.update();
        LogUtils.i(TAG, "MuPDFCore.addWaterMark 添加水印 update=" + update);
    }

    public void addFreeText(int pageNum, int width, int height, String text) {
        Page page = doc.loadPage(pageNum);
        Rect bounds = page.getBounds();
        float realWidth = bounds.x1 - bounds.x0;
        float realHeight = bounds.y1 - bounds.y0;
        PDFPage pdfPage = new PDFPage(page.pointer);
        PDFAnnotation pdfAnnotation = pdfPage.createAnnotation(PDFAnnotation.TYPE_FREE_TEXT);
        pdfAnnotation.setBorderWidth(1f);
    }

    public void addAnnotation(int pageNum, int width, int height, int type, float paintSize, int paintColor, Point[] inkList) {
        try {
            LogUtils.i(TAG, "MuPDFCore.addAnnotation: pageNum:" + pageNum + ",paintSize=" + paintSize);
            Page page = doc.loadPage(pageNum);
            Rect bounds = page.getBounds();
            float realWidth = bounds.x1 - bounds.x0;
            float realHeight = bounds.y1 - bounds.y0;
            for (Point point : inkList) {
                point.x = realWidth / width * point.x;
                point.y = realHeight / height * point.y;
            }
            PDFPage pdfPage = new PDFPage(page.pointer);
            PDFAnnotation pdfAnnotation = pdfPage.createAnnotation(type);
            pdfAnnotation.setBorderWidth(paintSize);
            if (type == TYPE_LINE) {//直线
                pdfAnnotation.setLine(new Point(inkList[0].x, inkList[0].y), new Point(inkList[1].x, inkList[1].y));
            } else if (type == TYPE_SQUARE) {
                pdfAnnotation.setRect(new Rect(inkList[0].x, inkList[0].y, inkList[1].x, inkList[1].y));
                Quad[] quads = new Quad[4];
                quads[0] = new Quad(new Rect(inkList[0].x, inkList[0].y, inkList[1].x, inkList[1].y));
                pdfAnnotation.setQuadPoints(quads);
            } else {
                pdfAnnotation.addInkList(inkList);
            }
            boolean update = pdfAnnotation.update();
            boolean update1 = pdfPage.update();
            LogUtils.i(TAG, "MuPDFCore.addAnnotation 添加批注 type=" + type + ",update=" + update + ",update1=" + update1);
        } catch (Exception e) {
            LogUtils.e(TAG, "MuPDFCore.addAnnotation Exception: " + e);
            e.printStackTrace();
        }
    }

    public void logAnnotations(int pageNum) {
        Page page = doc.loadPage(pageNum);
        PDFPage pdfPage = new PDFPage(page.pointer);
        PDFAnnotation[] annotations = pdfPage.getAnnotations();
        if (annotations == null) {
            return;
        }
        LogUtils.i(TAG, "MuPDFCore.logAnnotations: 批注数量：" + annotations.length);
        for (PDFAnnotation annotation : annotations) {
            boolean hasInkList = annotation.hasInkList();
            boolean hasLine = annotation.hasLine();
            LogUtils.i(TAG, "MuPDFCore.logAnnotations: hasInkList:" + hasInkList + ",hasLine:" + hasLine);
            if (hasInkList) {
                LogUtils.i(TAG, "MuPDFCore.logAnnotations: InkListCount：" + annotation.getInkListCount());
                Point[][] inkList = annotation.getInkList();
                for (Point[] points : inkList) {
                    LogUtils.d(TAG, "MuPDFCore.logAnnotations: 墨迹坐标数量：" + points.length);
                    System.out.println("换行");
                    for (Point point : points) {
                        System.out.print("  " + point.toString());
                    }
                }
            }
            if (hasLine) {
                Point[] line = annotation.getLine();
                LogUtils.d(TAG, "MuPDFCore.logAnnotations: 直线坐标数量：" + line.length);
                System.out.println("换行");
                for (Point point : line) {
                    System.out.print("  " + point.toString());
                }
            }
        }
        LogUtils.i(TAG, "MuPDFCore.logAnnotations end");
    }

    static class AnnotationPathBean {
        List<Path> paths;
        PDFAnnotation pdfAnnotation;

        public AnnotationPathBean(List<Path> paths, PDFAnnotation pdfAnnotation) {
            this.paths = paths;
            this.pdfAnnotation = pdfAnnotation;
        }

        public List<Path> getPaths() {
            return paths;
        }

        public PDFAnnotation getPdfAnnotation() {
            return pdfAnnotation;
        }
    }

    /**
     * 将一组组的点转换成 {@link Path} 对象
     *
     * @param annotations 当前页面所有的批注 {@link PDFAnnotation}
     * @return {@link AnnotationPathBean} 列表
     */
    public List<AnnotationPathBean> annotations2path(PDFAnnotation[] annotations) {
        List<AnnotationPathBean> annotationPathBeans = new ArrayList<>();
        List<Path> pathList = null;
        Path path = null;
        LogUtils.i(TAG, "MuPDFCore.annotations2path: 批注数量：" + annotations.length);
        for (PDFAnnotation annotation : annotations) {
            int type = annotation.getType();
            pathList = new ArrayList<>();
            if (type == TYPE_LINE) {//直线
                Point[] line = annotation.getLine();
                path = new Path();
                path.moveTo(line[0].x, line[0].y);
                path.lineTo(line[1].x, line[1].y);
                pathList.add(path);
            } else if (type == TYPE_INK) {//墨迹
                Point[][] inkList = annotation.getInkList();
                for (Point[] points : inkList) {
                    float tempX = 0, tempY = 0;
                    int length = points.length;
                    for (int j = 0; j < length; j++) {
                        Point point = points[j];
                        System.out.print(point + "  ");
                        float x = point.x, y = point.y;
                        if (j == 0) {
                            path = new Path();
                            path.moveTo(x, y);
                            tempX = x;
                            tempY = y;
                        } else {
                            float dx = Math.abs(x - tempX), dy = Math.abs(tempY - y);
                            if (dx >= 1 || dy >= 1) {
                                path.quadTo(tempX, tempY, (tempX + x) / 2, (tempY + y) / 2);
                            }
                            tempX = x;
                            tempY = y;
                            if (j == length - 1) {
                                pathList.add(path);
                            }
                        }
                    }
                }
            } else if (type == TYPE_SCREEN) {
                Rect rect = annotation.getRect();
                path = new Path();
                RectF rectF = new RectF(rect.x0, rect.y0, rect.x1, rect.y1);
                path.addRect(rectF, Path.Direction.CCW);
                pathList.add(path);
            }
            LogUtils.i(TAG, "MuPDFCore.annotations2path pathList=" + pathList.size());
            annotationPathBeans.add(new AnnotationPathBean(pathList, annotation));
        }
        LogUtils.i(TAG, "MuPDFCore.annotations2path annotationPathBeans=" + annotationPathBeans.size());
        return annotationPathBeans;
    }

    /**
     * 根据坐标点找到要删除的批注
     *
     * @param annotationPathBeans 当前页所有的批注,通过 {@link #annotations2path(PDFAnnotation[] annotations)} 方法获取
     * @param x                   转换后的x
     * @param y                   转换后的y
     * @return 要删除的批注 {@link PDFAnnotation}
     */
    public PDFAnnotation findShouldDeleteAnnotation(List<AnnotationPathBean> annotationPathBeans, float x, float y) {
        for (AnnotationPathBean annotationPathBean : annotationPathBeans) {
            PDFAnnotation pdfAnnotation = annotationPathBean.getPdfAnnotation();
            List<Path> pathList = annotationPathBean.getPaths();
            for (int i = 0; i < pathList.size(); i++) {
                Path path = pathList.get(i);
                PathMeasure pm = new PathMeasure(path, false);
                float length = pm.getLength();
                Path tempPath = new Path();
                pm.getSegment(0, length, tempPath, false);
                float[] fa = new float[2];
                float sc = 0;
                while (sc < 1) {
                    sc += 0.001;
                    pm.getPosTan(sc * length, fa, null);
                    if (Math.abs((int) fa[0] - (int) x) <= 20 && Math.abs((int) fa[1] - (int) y) <= 20) {
                        return pdfAnnotation;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 根据坐标点删除页面已有的批注
     *
     * @param docView {@link ReaderView} 主要用来获取当前页码
     * @param width   当前预览的宽
     * @param height  当前预览的高
     * @param x       当前宽高下所触摸的坐标x
     * @param y       当前宽高下所触摸的坐标y
     */
    public void deleteAnnotation(ReaderView docView, int width, int height, float x, float y) {
        try {
            Page page = doc.loadPage(docView.mCurrent);
            PDFPage pdfPage = new PDFPage(page.pointer);
            PDFAnnotation[] annotations = pdfPage.getAnnotations();
            if (annotations == null) {
                return;
            }
            Rect bounds = page.getBounds();
            float realWidth = bounds.x1 - bounds.x0;
            float realHeight = bounds.y1 - bounds.y0;
            x = realWidth / width * x;
            y = realHeight / height * y;
            List<AnnotationPathBean> annotationPathBeans = annotations2path(annotations);
            PDFAnnotation pdfAnnotation = findShouldDeleteAnnotation(annotationPathBeans, x, y);
            if (pdfAnnotation != null) {
                pdfPage.deleteAnnotation(pdfAnnotation);
//                boolean update = pdfAnnotation.update();
                LogUtils.d(TAG, "MuPDFCore.deleteAnnotation: 删除批注 ");
//                PageView pageView = (PageView) docView.getDisplayedView();
//                pageView.update();
//                pageView.setPage(pageView.getPage(), new PointF(realWidth, realHeight));
//                pdfPage.update();//不需要
//                docView.setDisplayedViewIndex(docView.mCurrent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String nowDate() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return format.format(date);
    }

    public String save(String srcPath, String saveDirPath) throws Exception {
        PDFDocument pdfDocument = new PDFDocument(doc.pointer);
        String fileNameNoExtension = Util.getFileNameNoExtension(srcPath);
        String destPath = saveDirPath + File.separator + fileNameNoExtension + "-" + nowDate() + "-批注.pdf";
        boolean copy = Util.copyFile(new File(srcPath), new File(destPath), new Util.OnReplaceListener() {
            @Override
            public boolean onReplace(File srcFile, File destFile) {
                LogUtils.i(TAG, "MuPDFCore.onReplace: " + destFile.getAbsolutePath() + " 已存在，需要删除才能继续");
                return destFile.delete();
            }
        }, new Util.OnProgressUpdateListener() {
            @Override
            public void onProgressUpdate(double progress) {
                LogUtils.i(TAG, "MuPDFCore.onProgressUpdate: " + progress);
            }
        });
        LogUtils.i(TAG, "MuPDFCore.srcPath:" + srcPath + ",destPath:" + destPath + ",copy:" + copy);
        pdfDocument.save(destPath, "incremental");
        return destPath;
    }

    public void getPdfPage(int pageNum) {
        Page page = doc.loadPage(pageNum);
        PDFPage pdfPage = new PDFPage(page.pointer);
        PDFAnnotation[] annotations = pdfPage.getAnnotations();
        List<AnnotationPathBean> annotationPathBeans = annotations2path(annotations);
        for (AnnotationPathBean annotationPathBean : annotationPathBeans) {
            List<Path> paths = annotationPathBean.paths;
        }
    }
}
