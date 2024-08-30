package com.xlk.mupdf.library;

import android.os.Bundle;

/**
 * 宏开关
 *
 * @author : Administrator
 * @date : 2023/5/12 14:03
 */
public class MupdfConfig {
    private final String filePath;
    private final boolean watermarkEnable;
    private final String watermarkContent;
    private final boolean deleteSourceFile;
    private final boolean uploadEnable;
    private final int uploadDirId;
    private final boolean onlyPreview;

    public static final String TAG = "MuPDF";
    public static boolean delete_history_annotation = false;

    public MupdfConfig(MupdfConfig.Builder builder) {
        this.filePath = builder.filePath;
        this.watermarkEnable = builder.watermarkEnable;
        this.watermarkContent = builder.watermarkContent;
        this.deleteSourceFile = builder.deleteSourceFile;
        this.uploadEnable = builder.uploadEnable;
        this.uploadDirId = builder.uploadDirId;
        this.onlyPreview = builder.onlyPreview;
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isWatermarkEnable() {
        return watermarkEnable;
    }

    public String getWatermarkContent() {
        return watermarkContent;
    }

    public boolean isDeleteSourceFile() {
        return deleteSourceFile;
    }

    public boolean isUploadEnable() {
        return uploadEnable;
    }

    public int getUploadDirId() {
        return uploadDirId;
    }

    public boolean isOnlyPreview() {
        return onlyPreview;
    }


    public static class Builder {
        private String filePath;
        private boolean watermarkEnable;
        private String watermarkContent;
        private boolean deleteSourceFile;
        private boolean uploadEnable;
        private int uploadDirId;
        private boolean onlyPreview;

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder watermarkEnable(boolean watermarkEnable) {
            this.watermarkEnable = watermarkEnable;
            return this;
        }

        public Builder watermarkEnable(String watermark) {
            this.watermarkContent = watermark;
            return this;
        }

        public Builder deleteSourceFile(boolean deleteSourceFile) {
            this.deleteSourceFile = deleteSourceFile;
            return this;
        }

        public Builder uploadEnable(boolean uploadEnable) {
            this.uploadEnable = uploadEnable;
            return this;
        }

        public Builder uploadDirId(int uploadDirId) {
            this.uploadDirId = uploadDirId;
            return this;
        }

        public Builder onlyPreview(boolean onlyPreview) {
            this.onlyPreview = onlyPreview;
            return this;
        }

        public MupdfConfig build() {
            return new MupdfConfig(this);
        }
    }
}
