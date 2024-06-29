package com.xlk.mupdf.library.view;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.artifex.mupdf.util.ScreenUtils;
import com.xlk.mupdf.library.R;

/**
 * @author : Administrator
 * @date : 2024/6/19 14:20
 * @description :
 */
public class ArtBoardDialog extends Dialog {
    private final SignatureListener mListener;
    private final boolean retBitmap;

    /**
     * @param context
     * @param retBitmap =true 返回Bitmap,=false 返回画笔路径和坐标集合  List<SignatureBoard.DrawPath>
     * @param listener
     */
    public ArtBoardDialog(@NonNull Context context, boolean retBitmap, SignatureListener listener) {
        super(context);
        mListener = listener;
        this.retBitmap = retBitmap;
        init();
    }

    private void init() {
        View inflate = LayoutInflater.from(getContext()).inflate(R.layout.mupdf_dialog_signature_artboard, null);
        setContentView(inflate);
        SeekBar sb_ink_size = inflate.findViewById(R.id.sb_ink_size);
        TextView tv_ink_size = inflate.findViewById(R.id.tv_ink_size);
        SignatureBoard draw_board = inflate.findViewById(R.id.draw_board);
        sb_ink_size.setMax(100);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sb_ink_size.setMin(1);
        }
        sb_ink_size.setProgress(5);
        tv_ink_size.setText(String.valueOf(5));
        sb_ink_size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                if (progress < 1) progress = 1;
                draw_board.setPaintSize(progress);
                tv_ink_size.setText(String.valueOf(progress));
            }
        });
        inflate.findViewById(R.id.btn_revoke).setOnClickListener(v -> {
            if (draw_board != null) {
                draw_board.revoke();
            }
        });
        inflate.findViewById(R.id.btn_clear).setOnClickListener(v -> {
            if (draw_board != null) {
                draw_board.clear();
            }
        });
        inflate.findViewById(R.id.btn_ensure).setOnClickListener(v -> {
            if (draw_board.isNotEmpty()) {
                dismiss();
                if (retBitmap) {
                    mListener.onSuccess(draw_board.getCanvasBmp());
                } else {
                    mListener.onSuccess(draw_board.getDrawPaths(), draw_board.getRegionSize());
                }
            } else {
                Toast.makeText(getContext(), "请进行签名", Toast.LENGTH_SHORT).show();
            }
        });
        inflate.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        setCancelable(true);
        setCanceledOnTouchOutside(true);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.width = ScreenUtils.getScreenWidth(getContext()) / 2;
        attributes.height = ScreenUtils.getScreenHeight(getContext()) / 2;
        getWindow().setAttributes(attributes);
    }

    public interface SignatureListener {
        void onSuccess(Object... objects);
    }
}
