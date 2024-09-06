package com.xlk.mupdf_example;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.view.View;


import com.xlk.mupdf.library.MuPdfDocumentActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        File file = new File(getExternalCacheDir().getAbsolutePath() + File.separator + "abcde.pdf");
        if(!file.exists()) {
            copyAssets(this, "abcde.pdf");
        }
    }

    private void copyAssets(Context context, String fileName) {
        File file = new File(context.getExternalCacheDir().getAbsolutePath(), fileName);
        if (file.exists()) {
            return;
        }
        try {
            FileOutputStream fos = new FileOutputStream(file);
            InputStream is = context.getAssets().open(fileName);
            int len;
            byte[] b = new byte[2048];
            while ((len = is.read(b)) != -1) {
                fos.write(b, 0, len);
            }
            fos.close();
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void openAssetsFile(View view) {
        Bundle bundle = new Bundle();
        bundle.putString(MuPdfDocumentActivity.bundle_key_file_path, this.getExternalCacheDir().getAbsolutePath() + File.separator + "abcde.pdf");
        bundle.putBoolean(MuPdfDocumentActivity.bundle_key_delete_file, false);
        bundle.putBoolean(MuPdfDocumentActivity.bundle_key_only_preview, false);
        MuPdfDocumentActivity.jump(this, bundle);
    }
}