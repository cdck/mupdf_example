package com.xlk.mupdf.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.artifex.mupdf.annotation.AnnotationArtBoard;
import com.artifex.mupdf.annotation.AnnotationBean;
import com.artifex.mupdf.fitz.PDFAnnotation;
import com.artifex.mupdf.fitz.Point;
import com.artifex.mupdf.fitz.SeekableInputStream;
import com.artifex.mupdf.util.LogUtils;
import com.artifex.mupdf.util.ScreenUtils;
import com.artifex.mupdf.util.Util;
import com.artifex.mupdf.viewer.ContentInputStream;
import com.artifex.mupdf.viewer.MuPDFCore;
import com.artifex.mupdf.viewer.OutlineActivity;
import com.artifex.mupdf.viewer.PageAdapter;
import com.artifex.mupdf.viewer.PageView;
import com.artifex.mupdf.viewer.Pallet;
import com.artifex.mupdf.viewer.ReaderView;
import com.artifex.mupdf.viewer.SearchTaskResult;
import com.xlk.mupdf.library.bus.MupdfBusType;
import com.xlk.mupdf.library.bus.MupdfEventMessage;
import com.xlk.mupdf.library.view.ArtBoardDialog;
import com.xlk.mupdf.library.view.ScalableView;
import com.xlk.mupdf.library.view.SignatureBoard;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <p>mupdf文档查看器</p>
 * 需要取消屏幕适配： AutoSizeConfig.getInstance().getExternalAdaptManager().addCancelAdaptOfActivity(MuPdfDocumentActivity.class);
 */
public class MuPdfDocumentActivity extends AppCompatActivity {
    private static final String TAG = "MuPdfDocumentActivity";
    private ImageButton mCloseButton, outlineButton, mAnnotationButton, refreshButton;
    private TextView mIvExit;
    private LinearLayout inkOperationLayout;
    private ImageButton revokeButton, deleteButton, penButton, inkSizeButton, lineButton, squareButton, screenshotButton, signatureButton, doneButton, exitButton;
    private String srcFilePath;
    private String mWatermark;
    private boolean mAnnotationVisible, mInkSizeViewVisible;
    private AnnotationArtBoard artBoard;
    private LinearLayout inkSizeLayout;
    private SeekBar inkSizeSeekBar;
    private TextView inkSizeTextView;
    private ProgressDialog progressDialog;
    private RelativeLayout mRootLayout;
    private final int default_ink_size = 3;
    private TextView tv_mark;
    private MuPdfDocumentActivity mContext;
    /**
     * 提交和取消签名布局
     */
    private LinearLayout ll_signature_layout;
    private TextView tv_submit_signature, tv_cancel_signature;
    /**
     * 签名自定义View
     */
    private ScalableView mScalableView;
    private PageView mCurPageView;
    /**
     * 是否正在手写签名，正在签名时需要进行拦截翻页、缩放、顶部功能菜单显示
     */
    private boolean isSigning;
    private SwitchCompat switch_upload;
    private Boolean deleteFileWhenExit;

    /* The core rendering instance */
    enum TopBarMode {Main, More}

    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private final int OUTLINE_REQUEST = 0;
    private MuPDFCore core;
    private String mDocTitle;
    private String mDocKey;
    private ReaderView mDocView;
    private RelativeLayout rootView;
    private View mButtonsView;
    private boolean mButtonsVisible;
    private EditText mPasswordView;
    private TextView mDocNameView;
    private LinearLayout mLlPageView;
    private TextView mPageNumberView, mPrePageView, mNextPageView;
    private ViewAnimator mTopBarSwitcher;
    private TopBarMode mTopBarMode = TopBarMode.Main;
    private AlertDialog.Builder mAlertBuilder;
    private ArrayList<OutlineActivity.Item> mFlatOutline;
    private boolean mReturnToLibraryActivity = false;

    protected int mDisplayDPI;
    private int mLayoutEM = 10;
    private int mLayoutW = 312;
    private int mLayoutH = 504;

    protected View mLayoutButton;
    protected PopupMenu mLayoutPopupMenu;
    public static List<AnnotationBean> inkAnnotations = new ArrayList<>();
    /**
     * 点击了保存按钮
     */
    private boolean saveWhenExit;
    /**
     * 有进行批注
     */
    private boolean hadAnnotation;

    private int currentPage = 0;
    public static final String mupdf_bundle_key = "mupdf_bundle";
    /**
     * 水印开关
     */
    public static final String bundle_key_watermark_enable = "watermark_enable";
    /**
     * 水印内容
     */
    public static final String bundle_key_watermark_content = "watermark_content";
    /**
     * 退出时是否删除文件
     */
    public static final String bundle_key_delete_file = "delete_file";
    /**
     * 文件路径
     */
    public static final String bundle_key_file_path = "filePath";

    public static void jump(Context context, Bundle bundle) {
        Intent intent = new Intent(context, MuPdfDocumentActivity.class);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);//使用此标志后，进入画板返回时无法返回当前页面
        intent.setAction(Intent.ACTION_VIEW);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.putExtra(mupdf_bundle_key, bundle);
        context.startActivity(intent);
    }

    private String toHex(byte[] digest) {
        StringBuilder builder = new StringBuilder(2 * digest.length);
        for (byte b : digest)
            builder.append(String.format("%02x", b));
        return builder.toString();
    }

    private MuPDFCore openBuffer(byte buffer[], String magic) {
        try {
            core = new MuPDFCore(buffer, magic);
        } catch (Exception e) {
            LogUtils.e(TAG, "Error opening document buffer: " + e);
            return null;
        }
        return core;
    }

    private MuPDFCore openFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            LogUtils.e(TAG, "文件不存在");
            return null;
        }
        LogUtils.i(TAG, "Opening File " + filePath);
        try {
            core = new MuPDFCore(filePath);
        } catch (Exception e) {
            LogUtils.e(TAG, "Error opening document file: " + e);
            return null;
        }
        return core;
    }

    private MuPDFCore openStream(SeekableInputStream stm, String magic) {
        try {
            core = new MuPDFCore(stm, magic);
        } catch (Exception e) {
            LogUtils.e(TAG, "Error opening document stream: " + e);
            return null;
        }
        return core;
    }

    private MuPDFCore openCore(Uri uri, long size, String mimetype) throws IOException {
        ContentResolver cr = getContentResolver();

        LogUtils.i(TAG, "Opening document " + uri + ",mimetype=" + mimetype);

        InputStream is = cr.openInputStream(uri);
        byte[] buf = null;
        int used = -1;
        try {
            final int limit = 8 * 1024 * 1024;
            if (size < 0) { // size is unknown
                buf = new byte[limit];
                used = is.read(buf);
                boolean atEOF = is.read() == -1;
                if (used < 0 || (used == limit && !atEOF)) // no or partial data
                    buf = null;
            } else if (size <= limit) { // size is known and below limit
                buf = new byte[(int) size];
                used = is.read(buf);
                if (used < 0 || used < size) // no or partial data
                    buf = null;
            }
            if (buf != null && buf.length != used) {
                byte[] newbuf = new byte[used];
                System.arraycopy(buf, 0, newbuf, 0, used);
                buf = newbuf;
            }
        } catch (OutOfMemoryError e) {
            buf = null;
        } finally {
            is.close();
        }

        if (buf != null) {
            LogUtils.i(TAG, "  Opening document from memory buffer of size " + buf.length);
            return openBuffer(buf, mimetype);
        } else {
            LogUtils.i(TAG, "  Opening document from stream");
            return openStream(new ContentInputStream(cr, uri, size), mimetype);
        }
    }

    private void showCannotOpenDialog(String reason) {
        Resources res = getResources();
        AlertDialog alert = mAlertBuilder.create();
        setTitle(String.format(Locale.ROOT, res.getString(R.string.cannot_open_document_Reason), reason));
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
        alert.show();
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        mContext = this;
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mDisplayDPI = (int) metrics.densityDpi;

        mAlertBuilder = new AlertDialog.Builder(this);

        if (core == null) {
            if (savedInstanceState != null && savedInstanceState.containsKey("DocTitle")) {
                mDocTitle = savedInstanceState.getString("DocTitle");
            }
        }
        if (core == null) {
            Intent intent = getIntent();


            mReturnToLibraryActivity = intent.getIntExtra(getComponentName().getPackageName() + ".ReturnToLibraryActivity", 0) != 0;

            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                Bundle bundle = intent.getBundleExtra(mupdf_bundle_key);
                String mimetype = getIntent().getType();
                srcFilePath = bundle.getString(bundle_key_file_path, "");
                deleteFileWhenExit = bundle.getBoolean(bundle_key_delete_file, true);
                Uri uri = Uri.parse(new File(srcFilePath).toURI().toString());
                if (bundle.getBoolean(bundle_key_watermark_enable, false)) {
                    mWatermark = bundle.getString(bundle_key_watermark_content, "");
                }

                if (uri == null) {
                    showCannotOpenDialog("No document uri to open");
                    return;
                }

                mDocKey = uri.toString();

                LogUtils.i(TAG, "OPEN filePath " + srcFilePath);
                LogUtils.i(TAG, "OPEN URI " + uri);
                LogUtils.i(TAG, "OPEN mimetype " + mimetype);

                mDocTitle = null;
                long size = -1;
                Cursor cursor = null;

                try {
                    cursor = getContentResolver().query(uri, null, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int idx;

                        idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0 && cursor.getType(idx) == Cursor.FIELD_TYPE_STRING)
                            mDocTitle = cursor.getString(idx);

                        idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                        if (idx >= 0 && cursor.getType(idx) == Cursor.FIELD_TYPE_INTEGER)
                            size = cursor.getLong(idx);

                        if (size == 0)
                            size = -1;
                    }
                } catch (Exception x) {
                    // Ignore any exception and depend on default values for title
                    // and size (unless one was decoded
                } finally {
                    if (cursor != null)
                        cursor.close();
                }
                LogUtils.i(TAG, "  NAME " + mDocTitle);
                LogUtils.i(TAG, "  SIZE " + size);

                if (mimetype == null || mimetype.equals("application/octet-stream")) {
                    mimetype = getContentResolver().getType(uri);
                    LogUtils.i(TAG, "  MAGIC (Resolved) " + mimetype);
                }
                if (mimetype == null || mimetype.equals("application/octet-stream")) {
                    mimetype = mDocTitle;
                    LogUtils.i(TAG, "  MAGIC (Filename) " + mimetype);
                }
                if (srcFilePath != null && !srcFilePath.isEmpty()) {
                    mDocTitle = Util.getFileName(srcFilePath);
                    LogUtils.i(TAG, "  NAME " + mDocTitle);
                    try {
                        core = openFile(srcFilePath);
                        SearchTaskResult.set(null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (core == null) {
                    try {
                        core = openCore(uri, size, "application/pdf");
                        SearchTaskResult.set(null);
                    } catch (Exception x) {
                        showCannotOpenDialog(x.toString());
                        return;
                    }
                }
            }
            if (core != null && core.needsPassword()) {
                requestPassword(savedInstanceState);
                return;
            }
            if (core != null && core.countPages() == 0) {
                core = null;
            }
        }
        if (core == null) {
            AlertDialog alert = mAlertBuilder.create();
            alert.setTitle(R.string.cannot_open_document);
            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
            alert.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            });
            alert.show();
            return;
        }

        createUI(savedInstanceState);
    }

    public void requestPassword(final Bundle savedInstanceState) {
        mPasswordView = new EditText(this);
        mPasswordView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        mPasswordView.setTransformationMethod(new PasswordTransformationMethod());

        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(R.string.enter_password);
        alert.setView(mPasswordView);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.okay),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (core.authenticatePassword(mPasswordView.getText().toString())) {
                            createUI(savedInstanceState);
                        } else {
                            requestPassword(savedInstanceState);
                        }
                    }
                });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel),
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
        alert.show();
    }

    public void relayoutDocument() {
        int loc = core.layout(mDocView.mCurrent, mLayoutW, mLayoutH, mLayoutEM);
        mFlatOutline = null;
        mDocView.mHistory.clear();
        mDocView.refresh();
        mDocView.setDisplayedViewIndex(loc);
    }

    public void createUI(Bundle savedInstanceState) {
        if (core == null)
            return;
        // 计算宽度占满时的缩放比例
        PointF size = core.getPageSize(0);
        int screenWidth = ScreenUtils.getScreenWidth(this);
        int screenHeight = ScreenUtils.getScreenHeight(this);
        float mSourceScale = Math.min(screenWidth / size.x, screenHeight / size.y);
        android.graphics.Point newSize = new android.graphics.Point((int) (size.x * mSourceScale), (int) (size.y * mSourceScale));
        float fullWidthScale = screenWidth * 1.0f / (newSize.x * 1.0f);
        LogUtils.i(TAG, "MuPdfDocumentActivity.createUI: size=" + size + ",newSize=" + newSize + ",fullWidthScale=" + fullWidthScale);
        // Now create the UI.
        // First create the document view
        mDocView = new ReaderView(this, fullWidthScale) {
            @Override
            protected void onMoveToChild(int i) {
                if (core == null)
                    return;
                currentPage = i;
                mPageNumberView.setText(String.format(Locale.ROOT, "%d / %d", i + 1, core.countPages()));
                LogUtils.i(TAG, "MuPdfDocumentActivity.onMoveToChild: ");
                super.onMoveToChild(i);
            }

            @Override
            protected void onTapMainDocArea() {
                LogUtils.i(TAG, "MuPdfDocumentActivity.onTapMainDocArea: ");
                if (!mButtonsVisible) {
                    showButtons();
                } else {
                    if (mTopBarMode == TopBarMode.Main)
                        hideButtons();
                }
            }

            @Override
            protected void onDocMotion() {
                //LogUtils.i(TAG, "MuPdfDocumentActivity.onDocMotion: ");
                hideButtons();
            }

            @Override
            public void onSizeChanged(int w, int h, int oldw, int oldh) {
                LogUtils.i(TAG, "onSizeChanged: size:" + w + "," + h + ", old size:" + oldw + "," + oldh + ",core.isReflowable()=" + core.isReflowable());
                if (core.isReflowable()) {
                    mLayoutW = w * 72 / mDisplayDPI;
                    mLayoutH = h * 72 / mDisplayDPI;
                    relayoutDocument();
                } else {
                    refresh();
                }
            }
        };
        PageAdapter pageAdapter = new PageAdapter(this, core, fullWidthScale, mWatermark);
        mDocView.setAdapter(pageAdapter);

        // Make the buttons overlay, and store all its
        // controls in variables
        makeButtonsView();

        mDocNameView.setText(mDocTitle);

        // 2023/5/9 自定义组件
        mCloseButton.setOnClickListener(v -> {
            exit();
        });
        mIvExit.setOnClickListener(v -> {
            exit();
        });
        ibs.add(deleteButton);//删除
        ibs.add(penButton);//墨迹
        ibs.add(lineButton);//直线
        ibs.add(squareButton);//矩形
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            inkSizeSeekBar.setMin(1);
        }
        inkSizeSeekBar.setMax(100);
        inkSizeTextView.setText(String.valueOf(default_ink_size));
        inkSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress = Math.max(progress, 1);
                inkSizeTextView.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                progress = Math.max(progress, 1);
                inkSizeSeekBar.setProgress(progress);
                artBoard.setPaintWidth(progress);
                inkSizeTextView.setText(String.valueOf(progress));
            }
        });

        //刷新，重新加载当前页
        refreshButton.setOnClickListener(v -> {
            mDocView.setDisplayedViewIndex(mDocView.mCurrent);
            core.logAnnotations(0);
        });
        switch_upload.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveWhenExit = isChecked;
            Toast.makeText(mContext, saveWhenExit ? "将在退出时上传到批注目录" : "已取消退出时上传到批注目录", Toast.LENGTH_SHORT).show();
        });

        //签名
        signatureButton.setOnClickListener(v -> {
            hideButtons();
            new ArtBoardDialog(this, false, new ArtBoardDialog.SignatureListener() {
                @Override
                public void onSuccess(Object[] object) {
                    ll_signature_layout.setVisibility(View.VISIBLE);
                    isSigning = true;
                    mDocView.setSigning(true);
                    mCurPageView = (PageView) mDocView.getDisplayedView();
                    int width = mCurPageView.getWidth();
                    int height = mCurPageView.getHeight();
                    int top = mCurPageView.getTop();
                    LogUtils.i(TAG, "onSuccess 开启批注:(" + width + "," + height + ")" + top);

                    List<SignatureBoard.DrawPath> drawPaths = (List<SignatureBoard.DrawPath>) object[0];
                    RectF regionSize = (RectF) object[1];

                    int offset = 50;
                    int scalableViewWidth = (int) (regionSize.right - regionSize.left) + offset * 2;
                    int scalableViewHeight = (int) (regionSize.bottom - regionSize.top) + offset * 2;

                    int l = width / 2 - scalableViewWidth / 2;
                    int t = Math.abs(top) + 100;
                    int r = width / 2 + scalableViewWidth / 2;
                    int b = scalableViewHeight + Math.abs(top) + 100;

                    mScalableView = new ScalableView(mContext, drawPaths
                            , regionSize.left, regionSize.top
                            , regionSize.right, regionSize.bottom
                            , l, t, r, b
                            , width, height, offset);

                    ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(scalableViewWidth, scalableViewHeight);
                    mScalableView.setLayoutParams(params);
                    mCurPageView.addView(mScalableView);

                    mScalableView.layout(l, t, r, b);
                }
            }).show();
        });
        //提交签名
        tv_submit_signature.setOnClickListener(v -> {
            List<SignatureBoard.DrawPath> drawPaths = mScalableView.getDrawPaths();
            PageView pageView = (PageView) mDocView.getDisplayedView();
            int width = pageView.getWidth();
            int height = pageView.getHeight();
            for (SignatureBoard.DrawPath drawPath : drawPaths) {
                PointF[] points = drawPath.points;
                Point[] array = new Point[points.length];
                for (int i = 0; i < points.length; i++) {
                    float x = points[i].x;
                    float y = points[i].y;
                    array[i] = new Point(x, y);
                }
                core.addAnnotation(mDocView.mCurrent, width, height, PDFAnnotation.TYPE_INK, 5 / 3.0f, Color.RED, array);
                hadAnnotation = true;
            }

            mCurPageView.removeView(mScalableView);
            mScalableView = null;
            mCurPageView = null;
            ll_signature_layout.setVisibility(View.GONE);
            isSigning = false;
            mDocView.setSigning(false);
            mDocView.refresh();
        });
        //取消签名
        tv_cancel_signature.setOnClickListener(v -> {
            mCurPageView.removeView(mScalableView);
            mScalableView = null;
            mCurPageView = null;
            ll_signature_layout.setVisibility(View.GONE);
            isSigning = false;
            mDocView.setSigning(false);
        });

        //截图批注
        screenshotButton.setOnClickListener(v -> {
            hideButtons();
            mainHandler.postDelayed(() -> {
                EventBus.getDefault().post(new MupdfEventMessage.Builder().type(MupdfBusType.inform_screenshot).objects(mDocTitle, 0).build());
            }, 250);
        });
        //界面跳转
        mPageNumberView.setOnClickListener(v -> {
            AlertDialog alert = mAlertBuilder.create();
            alert.setTitle("调整到指定页");
            EditText editText = new EditText(this);
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
            alert.setView(editText);
            alert.setButton(AlertDialog.BUTTON_POSITIVE, "确定", (dialog, which) -> {
                String number = editText.getText().toString().trim();
                if (number.isEmpty()) {
                    Toast.makeText(mContext, "请输入页码", Toast.LENGTH_SHORT).show();
                    return;
                }
                int max = core.countPages();
                int value = Integer.parseInt(number);
                if (value < 1) {
                    Toast.makeText(mContext, "页码最小值为1", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (value > max) {
                    Toast.makeText(mContext, "不能大于最大页", Toast.LENGTH_SHORT).show();
                    return;
                }
                mDocView.setDisplayedViewIndex(value - 1);
                dialog.dismiss();
            });
            alert.setButton(AlertDialog.BUTTON_NEGATIVE, "取消", (dialog, which) -> dialog.dismiss());
            alert.setOnCancelListener(dialog -> dialog.dismiss());
            alert.show();
        });
        mPrePageView.setOnClickListener(v -> {
            if (currentPage > 0) {
                mDocView.setDisplayedViewIndex(currentPage - 1);
            }
        });
        mNextPageView.setOnClickListener(v -> {
            int countPages = core.countPages();
            if (currentPage <= countPages) {
                mDocView.setDisplayedViewIndex(currentPage + 1);
            }
        });

        if (core.isReflowable()) {
            mLayoutButton.setVisibility(View.VISIBLE);
            mLayoutPopupMenu = new PopupMenu(this, mLayoutButton);
            mLayoutPopupMenu.getMenuInflater().inflate(R.menu.layout_menu, mLayoutPopupMenu.getMenu());
            mLayoutPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    float oldLayoutEM = mLayoutEM;
                    int id = item.getItemId();
                    if (id == R.id.action_layout_6pt) mLayoutEM = 6;
                    else if (id == R.id.action_layout_7pt) mLayoutEM = 7;
                    else if (id == R.id.action_layout_8pt) mLayoutEM = 8;
                    else if (id == R.id.action_layout_9pt) mLayoutEM = 9;
                    else if (id == R.id.action_layout_10pt) mLayoutEM = 10;
                    else if (id == R.id.action_layout_11pt) mLayoutEM = 11;
                    else if (id == R.id.action_layout_12pt) mLayoutEM = 12;
                    else if (id == R.id.action_layout_13pt) mLayoutEM = 13;
                    else if (id == R.id.action_layout_14pt) mLayoutEM = 14;
                    else if (id == R.id.action_layout_15pt) mLayoutEM = 15;
                    else if (id == R.id.action_layout_16pt) mLayoutEM = 16;
                    if (oldLayoutEM != mLayoutEM)
                        relayoutDocument();
                    return true;
                }
            });
            mLayoutButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mLayoutPopupMenu.show();
                }
            });
        }

        if (core.hasOutline()) {
            outlineButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (mFlatOutline == null)
                        mFlatOutline = core.getOutline();
                    if (mFlatOutline != null) {
                        Intent intent = new Intent(MuPdfDocumentActivity.this, OutlineActivity.class);
                        Bundle bundle = new Bundle();
                        bundle.putInt("POSITION", mDocView.getDisplayedViewIndex());
                        bundle.putSerializable("OUTLINE", mFlatOutline);
                        intent.putExtra("PALLETBUNDLE", Pallet.sendBundle(bundle));
                        startActivityForResult(intent, OUTLINE_REQUEST);
                    }
                }
            });
        } else {
            outlineButton.setVisibility(View.GONE);
        }

        //开启批注
        mAnnotationButton.setOnClickListener(v -> {
            hideButtons();
            showAnnotationViews();
            PageView pageView = (PageView) mDocView.getDisplayedView();
            int width = pageView.getWidth();
            int height = pageView.getHeight();
            LogUtils.i(TAG, "开启批注:(" + width + "," + height + ")");
            chooseType(1);
            artBoard = new AnnotationArtBoard(this, core, mDocView, width, height, new AnnotationArtBoard.DrawExitListener() {
                @Override
                public void onDrawAnnotations(List<AnnotationBean> inkAnnotations) {
                    LogUtils.i(TAG, "onDrawAnnotations 将要绘制的批注数量： " + inkAnnotations.size());
                    if (!inkAnnotations.isEmpty()) {
                        for (AnnotationBean inkAnnotation : inkAnnotations) {
                            Point[] points = inkAnnotation.getPoints();
                            float paintSize = inkAnnotation.getPaintSize();
                            int paintColor = inkAnnotation.getPaintColor();
                            int type = inkAnnotation.getType();
                            paintSize = paintSize / 3.0f;
                            core.addAnnotation(mDocView.mCurrent, width, height, type, paintSize, paintColor, points);
                        }
                        mDocView.refresh();
                        //mDocView.setDisplayedViewIndex(mDocView.mCurrent);
                        hadAnnotation = true;
                    }
                }
            });
            artBoard.setPaintWidth(default_ink_size);
            pageView.addView(artBoard);
            artBoard.layout(0, 0, width, height);
        });
        revokeButton.setOnClickListener(v -> {
            if (artBoard != null) {
                artBoard.revoke();
            }
        });
        deleteButton.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_ERASER) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_ERASER);
                    chooseType(0);
                }
            }
        });
        penButton.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_SLINE) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_SLINE);
                    chooseType(1);
                }
            }
        });
        inkSizeButton.setOnClickListener(v -> {
            if (mInkSizeViewVisible) {
                hideInkSizeViews();
            } else {
                showInkSizeViews();
            }
        });
        lineButton.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_LINE) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_LINE);
                    chooseType(2);
                }
            }
        });
        squareButton.setOnClickListener(v -> {
            if (artBoard != null) {
                int drawType = artBoard.getDrawType();
                if (drawType != AnnotationArtBoard.DRAW_RECT) {
                    artBoard.setDrawType(AnnotationArtBoard.DRAW_RECT);
                    chooseType(3);
                }
            }
        });
        //提交批注
        doneButton.setOnClickListener(v -> {
            hideAnnotationViews();
        });
        //取消批注
        exitButton.setOnClickListener(v -> {
            artBoard.setCancelAnnotation();
            hideAnnotationViews();
        });

        if (core.isReflowable()) {
            mLayoutButton.setVisibility(View.VISIBLE);
            mLayoutPopupMenu = new PopupMenu(this, mLayoutButton);
            mLayoutPopupMenu.getMenuInflater().inflate(R.menu.layout_menu, mLayoutPopupMenu.getMenu());
            mLayoutPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    float oldLayoutEM = mLayoutEM;
                    int id = item.getItemId();
                    if (id == R.id.action_layout_6pt) mLayoutEM = 6;
                    else if (id == R.id.action_layout_7pt) mLayoutEM = 7;
                    else if (id == R.id.action_layout_8pt) mLayoutEM = 8;
                    else if (id == R.id.action_layout_9pt) mLayoutEM = 9;
                    else if (id == R.id.action_layout_10pt) mLayoutEM = 10;
                    else if (id == R.id.action_layout_11pt) mLayoutEM = 11;
                    else if (id == R.id.action_layout_12pt) mLayoutEM = 12;
                    else if (id == R.id.action_layout_13pt) mLayoutEM = 13;
                    else if (id == R.id.action_layout_14pt) mLayoutEM = 14;
                    else if (id == R.id.action_layout_15pt) mLayoutEM = 15;
                    else if (id == R.id.action_layout_16pt) mLayoutEM = 16;
                    if (oldLayoutEM != mLayoutEM)
                        relayoutDocument();
                    return true;
                }
            });
            mLayoutButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mLayoutPopupMenu.show();
                }
            });
        }

        // Reenstate last state if it was recorded
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        mDocView.setDisplayedViewIndex(prefs.getInt("page" + mDocKey, 0));

        if (savedInstanceState == null || !savedInstanceState.getBoolean("ButtonsHidden", false)) {
            showButtons();
        }

        // Stick the document view and the buttons overlay into a parent view
        mRootLayout = new RelativeLayout(this);
        mRootLayout.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mRootLayout.setBackgroundColor(Color.DKGRAY);
        mRootLayout.addView(mDocView);
        mRootLayout.addView(mButtonsView);
//        if (!mWatermark.isEmpty()) {
//            tv_mark.setText(mWatermark);
//        }

        setContentView(mRootLayout);
        mainHandler.postDelayed(() -> {
//            mDocView.setDisplayedViewIndex(mDocView.mCurrent);
            mDocView.defaultScale(fullWidthScale);
            mDocView.requestLayout();
            mDocView.run();
        }, 500L);
        LogUtils.i(TAG, "MuPdfDocumentActivity.createUI: end");
    }

    private void exit() {
        LogUtils.i(TAG, "---exit---");
        if (saveWhenExit && hadAnnotation) {
            showLoading();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        long l = System.currentTimeMillis();
                        String savePath = core.save(srcFilePath, getExternalFilesDir("批注文件").getAbsolutePath());
                        LogUtils.i(TAG, "保存用时：" + (System.currentTimeMillis() - l) + ",savePath=" + savePath);
                        EventBus.getDefault().post(new MupdfEventMessage.Builder().type(MupdfBusType.inform_upload).objects(savePath).build());
                        hideLoading("");
                        MuPdfDocumentActivity.this.finish();
                    } catch (Exception e) {
                        LogUtils.e(TAG, e.toString());
                        e.printStackTrace();
                    }
                }
            }).start();
        } else {
            finish();
        }
    }

    private final List<ImageButton> ibs = new ArrayList<>();

    private void chooseType(int index) {
        for (int i = 0; i < ibs.size(); i++) {
            boolean selected = index == i;
            ImageButton imageButton = ibs.get(i);
            if (selected) {
                imageButton.getDrawable().setTint(Color.argb(255, 33, 150, 243));
            } else {
                imageButton.getDrawable().setTint(Color.argb(255, 255, 255, 255));
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case OUTLINE_REQUEST:
                if (resultCode >= RESULT_FIRST_USER && mDocView != null) {
//                    mDocView.pushHistory();//不注释的话，用户调用onBackPressed 会关闭当前页加载之前的页面
                    mDocView.setDisplayedViewIndex(resultCode - RESULT_FIRST_USER);
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mDocKey != null && mDocView != null) {
            if (mDocTitle != null)
                outState.putString("DocTitle", mDocTitle);

            // Store current page in the prefs against the file name,
            // so that we can pick it up each time the file is loaded
            // Other info is needed only for screen-orientation change,
            // so it can go in the bundle
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putInt("page" + mDocKey, mDocView.getDisplayedViewIndex());
            edit.apply();
        }

        if (!mButtonsVisible)
            outState.putBoolean("ButtonsHidden", true);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mDocKey != null && mDocView != null) {
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putInt("page" + mDocKey, mDocView.getDisplayedViewIndex());
            edit.apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogUtils.i("inkAnnotations:" + inkAnnotations.size());
        //退出批注画板时，如果批注过则inkAnnotations就不为空
        if (!inkAnnotations.isEmpty()) {
            PageView pageView = (PageView) mDocView.getDisplayedView();
            int width = pageView.getWidth();
            int height = pageView.getHeight();
            for (AnnotationBean inkAnnotation : inkAnnotations) {
                Point[] points = inkAnnotation.getPoints();
                float paintSize = inkAnnotation.getPaintSize();
                int paintColor = inkAnnotation.getPaintColor();
                int type = inkAnnotation.getType();
                paintSize = paintSize / 3.0f;
                core.addAnnotation(mDocView.mCurrent, width, height, type, paintSize, paintColor, points);
            }
            //绘制到pdf文件后清空
            inkAnnotations.clear();
            mDocView.setDisplayedViewIndex(mDocView.mCurrent);
        }
    }

    public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler = null;
        if (mDocView != null) {
            mDocView.applyToChildren(new ReaderView.ViewMapper() {
                @Override
                public void applyToView(View view) {
                    ((PageView) view).releaseBitmaps();
                }
            });
        }
        if (core != null)
            core.onDestroy();
        core = null;
        if (deleteFileWhenExit) {
            if (srcFilePath != null && !srcFilePath.isEmpty()) {
                File file = new File(srcFilePath);
                if (file.exists()) {
                    boolean delete = file.delete();
                    LogUtils.i("退出pdf预览时删除源文件：" + delete);
                }
            }
        }
        super.onDestroy();
    }

    private void showLoading() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("请稍后...");
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private void hideLoading(String savePath) {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
//        runOnUiThread(() ->
//                Toast.makeText(MuPdfDocumentActivity.this, "已保存:" + savePath, Toast.LENGTH_LONG).show()
//        );
    }

    private void showButtons() {
        if (isSigning) {
            LogUtils.e("当前正在签名中");
            return;
        }
        if (core == null)
            return;
        if (!mButtonsVisible) {
            mButtonsVisible = true;
            // Update page number text and slider
            int index = mDocView.getDisplayedViewIndex();
            updatePageNumView(index);

            Animation anim = new TranslateAnimation(0, 0, -mTopBarSwitcher.getHeight(), 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mTopBarSwitcher.setVisibility(View.VISIBLE);
                    mLlPageView.setVisibility(View.VISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                }
            });
            mTopBarSwitcher.startAnimation(anim);
        }
    }

    private void hideButtons() {
        if (mButtonsVisible) {
            mButtonsVisible = false;

            Animation anim = new TranslateAnimation(0, 0, 0, -mTopBarSwitcher.getHeight());
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mTopBarSwitcher.setVisibility(View.INVISIBLE);
                    mLlPageView.setVisibility(View.INVISIBLE);
                }
            });
            mTopBarSwitcher.startAnimation(anim);
        }
    }

    private void showAnnotationViews() {
        if (!mAnnotationVisible) {
            mAnnotationVisible = true;
            Animation anim = new TranslateAnimation(inkOperationLayout.getWidth(), 0, 0, 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mDocView.setAnnotation(true);
                    inkOperationLayout.setVisibility(View.VISIBLE);
//                    mInkLayout.setVisibility(View.VISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                }
            });
            inkOperationLayout.startAnimation(anim);
        }
    }

    private void hideAnnotationViews() {
        if (mAnnotationVisible) {
            mAnnotationVisible = false;
            Animation anim = new TranslateAnimation(0, inkOperationLayout.getWidth(), 0, 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mDocView.setAnnotation(false);
                    inkOperationLayout.setVisibility(View.INVISIBLE);
                    hideInkSizeViews();
                    inkSizeSeekBar.setProgress(default_ink_size);
                    if (artBoard != null) {
                        ((PageView) mDocView.getDisplayedView()).removeView(artBoard);
                        artBoard.clear();
                        artBoard.release();
                        artBoard = null;
                    }
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                }
            });
            inkOperationLayout.startAnimation(anim);
        }
    }

    private void showInkSizeViews() {
        if (!mInkSizeViewVisible) {
//            mInkSizeViewVisible = true;
//            inkSizeLayout.setVisibility(View.VISIBLE);
            Animation anim = new AlphaAnimation(0, 1f);
//            Animation anim = new TranslateAnimation(inkSizeLayout.getWidth(), 0, 0, 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    inkSizeLayout.setVisibility(View.VISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mInkSizeViewVisible = true;
                }
            });
            inkSizeLayout.startAnimation(anim);
        }
    }

    private void hideInkSizeViews() {
        if (mInkSizeViewVisible) {
//            mInkSizeViewVisible = false;
//            inkSizeLayout.setVisibility(View.GONE);
            Animation anim = new AlphaAnimation(1f, 0f);
//            Animation anim = new TranslateAnimation(0, inkSizeLayout.getWidth(), 0, 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mInkSizeViewVisible = false;
                    inkSizeLayout.setVisibility(View.GONE);
                }
            });
            inkSizeLayout.startAnimation(anim);
        }
    }

    private void updatePageNumView(int index) {
        if (core == null)
            return;
        currentPage = index;
        mPageNumberView.setText(String.format(Locale.ROOT, "%d / %d", index + 1, core.countPages()));
    }

    private void makeButtonsView() {
        mButtonsView = getLayoutInflater().inflate(R.layout.mupdf_document_activity, null);
        rootView = (RelativeLayout) mButtonsView.findViewById(R.id.rootView);
        mDocNameView = (TextView) mButtonsView.findViewById(R.id.docNameText);
        mLlPageView = (LinearLayout) mButtonsView.findViewById(R.id.ll_page_view);
        mPageNumberView = (TextView) mButtonsView.findViewById(R.id.pageNumber);//页码
        mPrePageView = (TextView) mButtonsView.findViewById(R.id.prePage);//上一页
        mNextPageView = (TextView) mButtonsView.findViewById(R.id.nextPage);//下一夜
        mTopBarSwitcher = (ViewAnimator) mButtonsView.findViewById(R.id.switcher);
        mLayoutButton = mButtonsView.findViewById(R.id.layoutButton);
        tv_mark = mButtonsView.findViewById(R.id.tv_mark);
        //提交签名、取消签名
        ll_signature_layout = mButtonsView.findViewById(R.id.ll_signature_layout);
        tv_submit_signature = mButtonsView.findViewById(R.id.tv_submit_signature);
        tv_cancel_signature = mButtonsView.findViewById(R.id.tv_cancel_signature);

        mTopBarSwitcher.setVisibility(View.INVISIBLE);
        mLlPageView.setVisibility(View.INVISIBLE);

        // 自定义组件
        mCloseButton = mButtonsView.findViewById(R.id.closeButton);
        switch_upload = mButtonsView.findViewById(R.id.switch_upload);
        refreshButton = mButtonsView.findViewById(R.id.refreshButton);
        screenshotButton = mButtonsView.findViewById(R.id.screenshotButton);
        signatureButton = mButtonsView.findViewById(R.id.signatureButton);
        mAnnotationButton = mButtonsView.findViewById(R.id.annotationButton);
        outlineButton = mButtonsView.findViewById(R.id.outlineButton);
        mIvExit = mButtonsView.findViewById(R.id.iv_exit);

        //批注控件
        inkOperationLayout = mButtonsView.findViewById(R.id.inkOperationLayout);
        doneButton = mButtonsView.findViewById(R.id.doneButton);
        revokeButton = mButtonsView.findViewById(R.id.revokeButton);
        deleteButton = mButtonsView.findViewById(R.id.deleteButton);
        penButton = mButtonsView.findViewById(R.id.penButton);
        inkSizeButton = mButtonsView.findViewById(R.id.inkSizeButton);
        lineButton = mButtonsView.findViewById(R.id.lineButton);
        squareButton = mButtonsView.findViewById(R.id.squareButton);
        exitButton = mButtonsView.findViewById(R.id.exitButton);
        inkSizeLayout = mButtonsView.findViewById(R.id.inkSizeLayout);
        inkSizeSeekBar = mButtonsView.findViewById(R.id.inkSizeSeekBar);
        inkSizeTextView = mButtonsView.findViewById(R.id.inkSizeTextView);
        inkOperationLayout.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onSearchRequested() {
//        if (mButtonsVisible && mTopBarMode == TopBarMode.Search) {
//            hideButtons();
//        } else {
//            showButtons();
//            searchModeOn();
//        }
        return super.onSearchRequested();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
//        if (mButtonsVisible && mTopBarMode != TopBarMode.Search) {
//            hideButtons();
//        } else {
//            showButtons();
//            searchModeOff();
//        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (saveWhenExit && hadAnnotation) {
            exit();
        } else {
            super.onBackPressed();
        }
//        if (mDocView == null || (mDocView != null && !mDocView.popHistory())) {
//            super.onBackPressed();
//            if (mReturnToLibraryActivity) {
//                Intent intent = getPackageManager().getLaunchIntentForPackage(getComponentName().getPackageName());
//                startActivity(intent);
//            }
//        }
    }
}

