package com.artifex.mupdf.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.View;

import androidx.core.content.FileProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <pre>
 *  author: Blankj
 *  blog  : http://blankj.com
 * </pre>
 */
public class Util {

    public static Bitmap view2Bitmap(final View view) {
        if (view == null) return null;
        boolean drawingCacheEnabled = view.isDrawingCacheEnabled();
        boolean willNotCacheDrawing = view.willNotCacheDrawing();
        view.setDrawingCacheEnabled(true);
        view.setWillNotCacheDrawing(false);
        Bitmap drawingCache = view.getDrawingCache();
        Bitmap bitmap;
        if (null == drawingCache || drawingCache.isRecycled()) {
            view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
            view.buildDrawingCache();
            drawingCache = view.getDrawingCache();
            if (null == drawingCache || drawingCache.isRecycled()) {
                bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.RGB_565);
                Canvas canvas = new Canvas(bitmap);
                view.draw(canvas);
            } else {
                bitmap = Bitmap.createBitmap(drawingCache);
            }
        } else {
            bitmap = Bitmap.createBitmap(drawingCache);
        }
        view.setWillNotCacheDrawing(willNotCacheDrawing);
        view.setDrawingCacheEnabled(drawingCacheEnabled);
        return bitmap;
    }

    public static Uri path2uri(Context context, String filePath) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                String authority = context.getApplicationInfo().packageName + ".fileprovider";
                return FileProvider.getUriForFile(context, authority, new File(filePath));
            }
        } catch (Exception e) {
            //出现异常
        }
        return Uri.fromFile(new File(filePath));
    }

    public static String uri2path(Context context,Uri uri){
        return null;
    }
    public static String getFilePathFromUri(Context context, Uri uri) {
        String filePath = "";
        // 判断Content URI是否为文件类型
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE)) {
            filePath = uri.getPath();
        } else if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                filePath = cursor.getString(index);
                cursor.close();
            }
        }
        return filePath;
    }

    /**
     * Return the name of file.
     *
     * @param file The file.
     * @return the name of file
     */
    public static String getFileName(final File file) {
        if (file == null) return "";
        return getFileName(file.getAbsolutePath());
    }

    /**
     * Return the name of file.
     *
     * @param filePath The path of file.
     * @return the name of file
     */
    public static String getFileName(final String filePath) {
        int lastSep = filePath.lastIndexOf(File.separator);
        return lastSep == -1 ? filePath : filePath.substring(lastSep + 1);
    }

    /**
     * Return the name of file without extension.
     *
     * @param file The file.
     * @return the name of file without extension
     */
    public static String getFileNameNoExtension(final File file) {
        if (file == null) return "";
        return getFileNameNoExtension(file.getPath());
    }

    /**
     * Return the name of file without extension.
     *
     * @param filePath The path of file.
     * @return the name of file without extension
     */
    public static String getFileNameNoExtension(final String filePath) {
        int lastPoi = filePath.lastIndexOf('.');
        int lastSep = filePath.lastIndexOf(File.separator);
        if (lastSep == -1) {
            return (lastPoi == -1 ? filePath : filePath.substring(0, lastPoi));
        }
        if (lastPoi == -1 || lastSep > lastPoi) {
            return filePath.substring(lastSep + 1);
        }
        return filePath.substring(lastSep + 1, lastPoi);
    }

    /**
     * Return the extension of file.
     *
     * @param file The file.
     * @return the extension of file
     */
    public static String getFileExtension(final File file) {
        if (file == null) return "";
        return getFileExtension(file.getPath());
    }

    public static String getFileExtension(final String filePath) {
        int lastPoi = filePath.lastIndexOf('.');
        int lastSep = filePath.lastIndexOf(File.separator);
        if (lastPoi == -1 || lastSep >= lastPoi) return "";
        return filePath.substring(lastPoi + 1);
    }

    public static boolean copyFile(File srcFile, File destFile) {
        return copyFile(srcFile, destFile, null, null);
    }

    public static boolean copyFile(File srcFile, File destFile, OnReplaceListener replaceListener, OnProgressUpdateListener progressUpdateListener) {
        if (srcFile == null || destFile == null) return false;
        // srcFile equals destFile then return false
        if (srcFile.equals(destFile)) return false;
        // srcFile doesn't exist or isn't a file then return false
        if (!srcFile.exists() || !srcFile.isFile()) return false;
        if (destFile.exists()) {
            if (replaceListener == null || replaceListener.onReplace(srcFile, destFile)) {// require delete the old file
                if (!destFile.delete()) {// unsuccessfully delete then return false
                    return false;
                }
            } else {
                return true;
            }
        }
        File parentFile = destFile.getParentFile();
        if (parentFile != null && (parentFile.exists() ? parentFile.isDirectory() : parentFile.mkdir())) {
            try {
                InputStream is = new FileInputStream(srcFile);
                OutputStream os = null;
                try {
                    os = new BufferedOutputStream(new FileOutputStream(destFile, false), 524288);
                    if (progressUpdateListener == null) {
                        byte[] data = new byte[524288];
                        for (int len; (len = is.read(data)) != -1; ) {
                            os.write(data, 0, len);
                        }
                    } else {
                        double totalSize = is.available();
                        int curSize = 0;
                        progressUpdateListener.onProgressUpdate(0);
                        byte[] data = new byte[524288];
                        for (int len; (len = is.read(data)) != -1; ) {
                            os.write(data, 0, len);
                            curSize += len;
                            progressUpdateListener.onProgressUpdate(curSize / totalSize);
                        }
                    }
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        if (os != null) {
                            os.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    public static boolean copyFile(File srcFile, Uri destDirUri, OnReplaceListener replaceListener, OnProgressUpdateListener progressUpdateListener) {

        return false;
    }

    /**
     * 将文件复制到外部私有目录，之后就可以操作复制后的文件了
     *
     * @param context     上下文
     * @param destDirPath 私有目录地址
     * @param srcFileUri  地址
     * @return 复制后的文件
     */
    public static File copyUri2File(Context context, String destDirPath, Uri srcFileUri) {
        try {
            LogUtils.i("copy2local srcFileUri=" + srcFileUri);
            ContentResolver contentResolver = context.getContentResolver();
            InputStream is = contentResolver.openInputStream(srcFileUri);
            File tempDir = new File(destDirPath);
            if (!tempDir.exists() && !tempDir.createNewFile()) {
                LogUtils.e("目录创建失败 destDirPath=" + destDirPath);
                return null;
            }
            String fileName = null;
            if (is != null && tempDir.exists()) {
                Cursor cursor = contentResolver.query(srcFileUri, new String[]{
                        MediaStore.MediaColumns.DISPLAY_NAME
                }, null, null, null);
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME));
                    LogUtils.i("fileName=" + fileName);
                }
                cursor.close();
            }
            if (fileName != null && !fileName.isEmpty()) {
                File file = new File(tempDir.getAbsolutePath() + File.separator + fileName);
                if (file.exists()) {
                    LogUtils.i("文件已经存在，无须再复制=" + file.getAbsolutePath());
                    return file;
                }
                FileOutputStream fos = new FileOutputStream(file);
                BufferedInputStream bis = new BufferedInputStream(is);
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                byte[] bytes = new byte[1024];
                int read = bis.read(bytes);
                while (read > 0) {
                    bos.write(bytes, 0, read);
                    bos.flush();
                    read = bis.read(bytes);
                }
                bos.close();
                bis.close();
                LogUtils.i("文件复制完毕=" + file.getAbsolutePath());
                return file;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        LogUtils.i("文件复制失败 srcFileUri=" + srcFileUri);
        return null;
    }

    public interface OnReplaceListener {
        boolean onReplace(File srcFile, File destFile);
    }

    public interface OnProgressUpdateListener {
        void onProgressUpdate(double progress);
    }
}
