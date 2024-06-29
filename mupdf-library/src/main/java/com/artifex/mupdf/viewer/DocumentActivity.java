package com.artifex.mupdf.viewer;

import com.artifex.mupdf.annotation.AnnotationArtBoard;
import com.artifex.mupdf.annotation.AnnotationBean;
import com.artifex.mupdf.fitz.Point;
import com.artifex.mupdf.fitz.SeekableInputStream;
import com.artifex.mupdf.util.Util;
import com.xlk.mupdf.library.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


//import com.artifex.mupdf.annotation.ReaderView;

public class DocumentActivity extends AppCompatActivity {
    private final String APP = "MuPDF";
    private ImageButton mCloseButton, mAnnotationButton, mSaveButton;
    private LinearLayout inkOperationLayout;
    private ImageButton revokeButton, deleteButton, penButton, inkSizeButton, lineButton, squareButton, screenshotButton, exitButton;
    private String srcFilePath;
    private boolean mAnnotationVisible, mInkSizeViewVisible;
    private RelativeLayout mRootLayout;
    private AnnotationArtBoard artBoard;
    private LinearLayout inkSizeLayout;
    private SeekBar inkSizeSeekBar;
    private TextView inkSizeTextView;
    private ProgressDialog progressDialog;

    /* The core rendering instance */
    enum TopBarMode {Main, Search, More}

    ;

    private final int OUTLINE_REQUEST = 0;
    private MuPDFCore core;
    private String mDocTitle;
    private String mDocKey;
    private ReaderView mDocView;
    private View mButtonsView;
    private boolean mButtonsVisible;
    private EditText mPasswordView;
    private TextView mDocNameView;
    private SeekBar mPageSlider;
    private int mPageSliderRes;
    private TextView mPageNumberView;
    private ImageButton mSearchButton;
    private ImageButton mOutlineButton;
    private ViewAnimator mTopBarSwitcher;
    private ImageButton mLinkButton;
    private TopBarMode mTopBarMode = TopBarMode.Main;
    private ImageButton mSearchBack;
    private ImageButton mSearchFwd;
    private ImageButton mSearchClose;
    private EditText mSearchText;
    private SearchTask mSearchTask;
    private AlertDialog.Builder mAlertBuilder;
    private boolean mLinkHighlight = false;
    private final Handler mHandler = new Handler();
    private boolean mAlertsActive = false;
    private AlertDialog mAlertDialog;
    private ArrayList<OutlineActivity.Item> mFlatOutline;
    private boolean mReturnToLibraryActivity = false;

    protected int mDisplayDPI;
    private int mLayoutEM = 10;
    private int mLayoutW = 312;
    private int mLayoutH = 504;

    protected View mLayoutButton;
    protected PopupMenu mLayoutPopupMenu;

    private String queryMimeType(Uri uri) {
        Cursor cursor = null;
        String mimeType = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{MediaStore.Video.Media.MIME_TYPE}, null, null, null);
            if (cursor != null && cursor.moveToNext()) {
                int idx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
                mimeType = cursor.getString(idx);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return mimeType;
    }

    private String queryFileName(Uri uri) {
        Cursor cursor = null;
        String mimeType = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{MediaStore.Video.Media.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToNext()) {
                int idx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
                mimeType = cursor.getString(idx);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return mimeType;
    }

    public static void jump(Context context, String filePath) {
        Intent intent = new Intent(context, DocumentActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.setAction(Intent.ACTION_VIEW);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.setData(Uri.parse(new File(filePath).toURI().toString()));
        intent.putExtra("filePath", filePath);
        context.startActivity(intent);
    }

    public static void jump(Context context, Uri uri) {
        Intent intent = new Intent(context, DocumentActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.setAction(Intent.ACTION_VIEW);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        intent.setData(uri);
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
            Log.e(APP, "Error opening document buffer: " + e);
            return null;
        }
        return core;
    }

    private MuPDFCore openFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            Log.e(APP, "文件不存在");
            return null;
        }
        Log.i(APP, "Opening File " + filePath);
        try {
            core = new MuPDFCore(filePath);
        } catch (Exception e) {
            Log.e(APP, "Error opening document file: " + e);
            return null;
        }
        return core;
    }

    private MuPDFCore openStream(SeekableInputStream stm, String magic) {
        try {
            core = new MuPDFCore(stm, magic);
        } catch (Exception e) {
            Log.e(APP, "Error opening document stream: " + e);
            return null;
        }
        return core;
    }

    private MuPDFCore openCore(Uri uri, long size, String mimetype) throws IOException {
        ContentResolver cr = getContentResolver();

        Log.i(APP, "Opening document " + uri + ",mimetype=" + mimetype);

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
            Log.i(APP, "  Opening document from memory buffer of size " + buf.length);
            return openBuffer(buf, mimetype);
        } else {
            Log.i(APP, "  Opening document from stream");
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
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

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
                Uri uri = intent.getData();
                String mimetype = getIntent().getType();
                srcFilePath = intent.getStringExtra("filePath");

                if (uri == null) {
                    showCannotOpenDialog("No document uri to open");
                    return;
                }

                mDocKey = uri.toString();

                Log.i(APP, "OPEN filePath " + srcFilePath);
                Log.i(APP, "OPEN URI " + uri.toString());
                Log.i(APP, "OPEN mimetype " + mimetype);

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
                Log.i(APP, "  NAME " + mDocTitle);
                Log.i(APP, "  SIZE " + size);

                if (mimetype == null || mimetype.equals("application/octet-stream")) {
                    mimetype = getContentResolver().getType(uri);
                    Log.i(APP, "  MAGIC (Resolved) " + mimetype);
                }
                if (mimetype == null || mimetype.equals("application/octet-stream")) {
                    mimetype = mDocTitle;
                    Log.i(APP, "  MAGIC (Filename) " + mimetype);
                }
                if (srcFilePath != null && !srcFilePath.isEmpty()) {
                    mDocTitle = new File(srcFilePath).getName();
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
            alert.setOnCancelListener(new OnCancelListener() {
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

        // Now create the UI.
        // First create the document view
        mDocView = new ReaderView(this) {
            @Override
            protected void onMoveToChild(int i) {
                if (core == null)
                    return;
                mPageNumberView.setText(String.format(Locale.ROOT, "%d / %d", i + 1, core.countPages()));
                mPageSlider.setMax((core.countPages() - 1) * mPageSliderRes);
                mPageSlider.setProgress(i * mPageSliderRes);
                super.onMoveToChild(i);
            }

            @Override
            protected void onTapMainDocArea() {
                if (!mButtonsVisible) {
                    showButtons();
                } else {
                    if (mTopBarMode == TopBarMode.Main)
                        hideButtons();
                }
            }

            @Override
            protected void onDocMotion() {
                hideButtons();
            }

            @Override
            public void onSizeChanged(int w, int h, int oldw, int oldh) {
                if (core.isReflowable()) {
                    mLayoutW = w * 72 / mDisplayDPI;
                    mLayoutH = h * 72 / mDisplayDPI;
                    relayoutDocument();
                } else {
                    refresh();
                }
            }
        };
        mDocView.setAdapter(new PageAdapter(this, core));

        mSearchTask = new SearchTask(this, core) {
            @Override
            protected void onTextFound(SearchTaskResult result) {
                SearchTaskResult.set(result);
                // Ask the ReaderView to move to the resulting page
                mDocView.setDisplayedViewIndex(result.pageNumber);
                // Make the ReaderView act on the change to SearchTaskResult
                // via overridden onChildSetup method.
                mDocView.resetupChildren();
            }
        };

        // Make the buttons overlay, and store all its
        // controls in variables
        makeButtonsView();

        // Set up the page slider
        int smax = Math.max(core.countPages() - 1, 1);
        mPageSliderRes = ((10 + smax - 1) / smax) * 2;

        // Set the file-name text
        String docTitle = core.getTitle();
        if (docTitle != null)
            mDocNameView.setText(docTitle);
        else
            mDocNameView.setText(mDocTitle);

        // Activate the seekbar
        mPageSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
                mDocView.pushHistory();
                mDocView.setDisplayedViewIndex((seekBar.getProgress() + mPageSliderRes / 2) / mPageSliderRes);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onProgressChanged(SeekBar seekBar, int progress,
                                          boolean fromUser) {
                updatePageNumView((progress + mPageSliderRes / 2) / mPageSliderRes);
            }
        });

        // Activate the search-preparing button
        mSearchButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                searchModeOn();
            }
        });

        mSearchClose.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                searchModeOff();
            }
        });

        // Search invoking buttons are disabled while there is no text specified
        mSearchBack.setEnabled(false);
        mSearchFwd.setEnabled(false);
        mSearchBack.setColorFilter(Color.argb(255, 128, 128, 128));
        mSearchFwd.setColorFilter(Color.argb(255, 128, 128, 128));

        // React to interaction with the text widget
        mSearchText.addTextChangedListener(new TextWatcher() {

            public void afterTextChanged(Editable s) {
                boolean haveText = s.toString().length() > 0;
                setButtonEnabled(mSearchBack, haveText);
                setButtonEnabled(mSearchFwd, haveText);

                // Remove any previous search results
                if (SearchTaskResult.get() != null && !mSearchText.getText().toString().equals(SearchTaskResult.get().txt)) {
                    SearchTaskResult.set(null);
                    mDocView.resetupChildren();
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before,
                                      int count) {
            }
        });

        //React to Done button on keyboard
        mSearchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    Log.i(APP, "EditorInfo.IME_ACTION_DONE");
                    search(1);
                }
                return false;
            }
        });

        mSearchText.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER)
                    search(1);
                return false;
            }
        });

        // Activate search invoking buttons
        mSearchBack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                search(-1);
            }
        });
        mSearchFwd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                search(1);
            }
        });

        mLinkButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setLinkHighlight(!mLinkHighlight);
            }
        });

        // TODO: 2023/5/9 自定义组件
        mCloseButton.setOnClickListener(v -> {
            onBackPressed();
        });
        ibs.add(deleteButton);//删除
        ibs.add(penButton);//墨迹
        ibs.add(lineButton);//直线
        inkSizeSeekBar.setMax(100);
        inkSizeTextView.setText(String.valueOf(1));
        inkSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                inkSizeTextView.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                inkSizeSeekBar.setProgress(progress);
                artBoard.setPaintWidth(progress);
                inkSizeTextView.setText(String.valueOf(progress));
            }
        });
        mSaveButton.setOnClickListener(v -> {
            String filePath = getExternalFilesDir("批注文件").getAbsolutePath();
            Uri uri = Util.path2uri(this, filePath);
            Log.i(APP, "选择目录: " + uri);
            launcher.launch(uri);
//            try {
//                long l = System.currentTimeMillis();
//                showLoading();
//                String savePath = core.save(srcFilePath, getExternalFilesDir("批注文件").getAbsolutePath());
//                new Timer().schedule(new TimerTask() {
//                    @Override
//                    public void run() {
//                        hideLoading(savePath);
//                    }
//                }, 2000);
//                Log.i(APP, "保存用时：" + (System.currentTimeMillis() - l));
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            }
        });
        screenshotButton.setOnClickListener(v -> {
            PageView pageView = (PageView) mDocView.getDisplayedView();
            Bitmap bitmap = Util.view2Bitmap(pageView);
            AlertDialog alert = mAlertBuilder.create();
            ImageView imageView = new ImageView(this);
            alert.setView(imageView);
            imageView.setImageBitmap(bitmap);
            alert.setTitle(R.string.cannot_open_document);
            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            alert.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    dialog.dismiss();
                }
            });
            alert.show();
        });
        //开启批注
        mAnnotationButton.setOnClickListener(v -> {
            hideButtons();
            showAnnotationViews();
            PageView pageView = (PageView) mDocView.getDisplayedView();
            int width = pageView.getWidth();
            int height = pageView.getHeight();
            Log.i(APP, "开启批注:(" + width + "," + height + ")");
            chooseType(1);
            artBoard = new AnnotationArtBoard(DocumentActivity.this, core, mDocView, width, height, new AnnotationArtBoard.DrawExitListener() {
                /**
                 * 退出画板的时候再绘制到pdf文件中
                 * @param inkAnnotations 绘制的所有批注
                 */
                @Override
                public void onDrawAnnotations(List<AnnotationBean> inkAnnotations) {
                    Log.i(APP, "onDrawAnnotations 将要绘制的批注数量： " + inkAnnotations.size());
                    if (!inkAnnotations.isEmpty()) {
                        for (AnnotationBean inkAnnotation : inkAnnotations) {
                            Point[] points = inkAnnotation.getPoints();
                            float paintSize = inkAnnotation.getPaintSize();
                            int paintColor = inkAnnotation.getPaintColor();
                            int type = inkAnnotation.getType();
                            core.addAnnotation(mDocView.mCurrent, width, height, type, paintSize, paintColor, points);
                        }
                    }
                    mDocView.setDisplayedViewIndex(mDocView.mCurrent);
                    //使用下面这方式，在第二次重新绘制后更新无法显示绘制的内容
//                    PageView pageView = (PageView) mDocView.getDisplayedView();
//                    pageView.update();
                }
            });
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
//            if (artBoard != null) {
//                artBoard.setDrawType(AnnotationArtBoard.DRAW_RECT);
//            }
        });

        exitButton.setOnClickListener(v -> {
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

        if (core.hasOutline()) {
            mOutlineButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (mFlatOutline == null)
                        mFlatOutline = core.getOutline();
                    if (mFlatOutline != null) {
                        Intent intent = new Intent(DocumentActivity.this, OutlineActivity.class);
                        Bundle bundle = new Bundle();
                        bundle.putInt("POSITION", mDocView.getDisplayedViewIndex());
                        bundle.putSerializable("OUTLINE", mFlatOutline);
                        intent.putExtra("PALLETBUNDLE", Pallet.sendBundle(bundle));
                        startActivityForResult(intent, OUTLINE_REQUEST);
                    }
                }
            });
        } else {
            mOutlineButton.setVisibility(View.GONE);
        }

        // Reenstate last state if it was recorded
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        mDocView.setDisplayedViewIndex(prefs.getInt("page" + mDocKey, 0));

        if (savedInstanceState == null || !savedInstanceState.getBoolean("ButtonsHidden", false))

            showButtons();

        if (savedInstanceState != null && savedInstanceState.getBoolean("SearchMode", false))

            searchModeOn();

        // Stick the document view and the buttons overlay into a parent view
        mRootLayout = new

                RelativeLayout(this);
        mRootLayout.setBackgroundColor(Color.DKGRAY);
        mRootLayout.addView(mDocView);
        mRootLayout.addView(mButtonsView);

        setContentView(mRootLayout);

    }

    private final List<ImageButton> ibs = new ArrayList<>();

    private void chooseType(int index) {
        for (int i = 0; i < ibs.size(); i++) {
            ibs.get(i).setSelected(index == i);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case OUTLINE_REQUEST:
                if (resultCode >= RESULT_FIRST_USER && mDocView != null) {
                    mDocView.pushHistory();
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

        if (mTopBarMode == TopBarMode.Search)
            outState.putBoolean("SearchMode", true);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mSearchTask != null)
            mSearchTask.stop();

        if (mDocKey != null && mDocView != null) {
            SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putInt("page" + mDocKey, mDocView.getDisplayedViewIndex());
            edit.apply();
        }
    }

    public void onDestroy() {
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
        super.onDestroy();
    }

    private void setButtonEnabled(ImageButton button, boolean enabled) {
        button.setEnabled(enabled);
        button.setColorFilter(enabled ? Color.argb(255, 255, 255, 255) : Color.argb(255, 128, 128, 128));
    }

    private void setLinkHighlight(boolean highlight) {
        mLinkHighlight = highlight;
        // LINK_COLOR tint
        mLinkButton.setColorFilter(highlight ? Color.argb(0xFF, 0x00, 0x66, 0xCC) : Color.argb(0xFF, 255, 255, 255));
        // Inform pages of the change.
        mDocView.setLinksEnabled(highlight);
    }

    private void showLoading() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("文件保存中...");
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private void hideLoading(String savePath) {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        runOnUiThread(() ->
                Toast.makeText(DocumentActivity.this, "已保存:" + savePath, Toast.LENGTH_LONG).show()
        );
    }

    private void showButtons() {
        if (core == null)
            return;
        if (!mButtonsVisible) {
            mButtonsVisible = true;
            // Update page number text and slider
            int index = mDocView.getDisplayedViewIndex();
            updatePageNumView(index);
            mPageSlider.setMax((core.countPages() - 1) * mPageSliderRes);
            mPageSlider.setProgress(index * mPageSliderRes);
            if (mTopBarMode == TopBarMode.Search) {
                mSearchText.requestFocus();
                showKeyboard();
            }

            Animation anim = new TranslateAnimation(0, 0, -mTopBarSwitcher.getHeight(), 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mTopBarSwitcher.setVisibility(View.VISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                }
            });
            mTopBarSwitcher.startAnimation(anim);

            anim = new TranslateAnimation(0, 0, mPageSlider.getHeight(), 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mPageSlider.setVisibility(View.VISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mPageNumberView.setVisibility(View.VISIBLE);
                }
            });
            mPageSlider.startAnimation(anim);
        }
    }

    private void hideButtons() {
        if (mButtonsVisible) {
            mButtonsVisible = false;
            hideKeyboard();

            Animation anim = new TranslateAnimation(0, 0, 0, -mTopBarSwitcher.getHeight());
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mTopBarSwitcher.setVisibility(View.INVISIBLE);
                }
            });
            mTopBarSwitcher.startAnimation(anim);

            anim = new TranslateAnimation(0, 0, 0, mPageSlider.getHeight());
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mPageNumberView.setVisibility(View.INVISIBLE);
                }

                public void onAnimationRepeat(Animation animation) {
                }

                public void onAnimationEnd(Animation animation) {
                    mPageSlider.setVisibility(View.INVISIBLE);
                }
            });
            mPageSlider.startAnimation(anim);
        }
    }

    private void showAnnotationViews() {
        if (!mAnnotationVisible) {
            mAnnotationVisible = true;
            Animation anim = new TranslateAnimation(-inkOperationLayout.getWidth(), 0, 0, 0);
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
            Animation anim = new TranslateAnimation(0, -inkOperationLayout.getWidth(), 0, 0);
            anim.setDuration(200);
            anim.setAnimationListener(new Animation.AnimationListener() {
                public void onAnimationStart(Animation animation) {
                    mDocView.setAnnotation(false);
                    inkOperationLayout.setVisibility(View.INVISIBLE);
                    hideInkSizeViews();
                    inkSizeSeekBar.setProgress(1);
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
            mInkSizeViewVisible = true;
            inkSizeLayout.setVisibility(View.VISIBLE);
        }
    }

    private void hideInkSizeViews() {
        if (mInkSizeViewVisible) {
            mInkSizeViewVisible = false;
            inkSizeLayout.setVisibility(View.GONE);
        }
    }

    private void searchModeOn() {
        if (mTopBarMode != TopBarMode.Search) {
            mTopBarMode = TopBarMode.Search;
            //Focus on EditTextWidget
            mSearchText.requestFocus();
            showKeyboard();
            mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
        }
    }

    private void searchModeOff() {
        if (mTopBarMode == TopBarMode.Search) {
            mTopBarMode = TopBarMode.Main;
            hideKeyboard();
            mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
            SearchTaskResult.set(null);
            // Make the ReaderView act on the change to mSearchTaskResult
            // via overridden onChildSetup method.
            mDocView.resetupChildren();
        }
    }

    private void updatePageNumView(int index) {
        if (core == null)
            return;
        mPageNumberView.setText(String.format(Locale.ROOT, "%d / %d", index + 1, core.countPages()));
    }

    private void makeButtonsView() {
        mButtonsView = getLayoutInflater().inflate(R.layout.document_activity, null);
        mDocNameView = (TextView) mButtonsView.findViewById(R.id.docNameText);
        mPageSlider = (SeekBar) mButtonsView.findViewById(R.id.pageSlider);
        mPageNumberView = (TextView) mButtonsView.findViewById(R.id.pageNumber);
        mSearchButton = (ImageButton) mButtonsView.findViewById(R.id.searchButton);
        mOutlineButton = (ImageButton) mButtonsView.findViewById(R.id.outlineButton);
        mTopBarSwitcher = (ViewAnimator) mButtonsView.findViewById(R.id.switcher);
        mSearchBack = (ImageButton) mButtonsView.findViewById(R.id.searchBack);
        mSearchFwd = (ImageButton) mButtonsView.findViewById(R.id.searchForward);
        mSearchClose = (ImageButton) mButtonsView.findViewById(R.id.searchClose);
        mSearchText = (EditText) mButtonsView.findViewById(R.id.searchText);
        mLinkButton = (ImageButton) mButtonsView.findViewById(R.id.linkButton);
        mLayoutButton = mButtonsView.findViewById(R.id.layoutButton);

        mTopBarSwitcher.setVisibility(View.INVISIBLE);
        mPageNumberView.setVisibility(View.INVISIBLE);

        mPageSlider.setVisibility(View.INVISIBLE);

        // TODO: 2023/5/9 自定义组件
        mCloseButton = mButtonsView.findViewById(R.id.closeButton);
        mSaveButton = mButtonsView.findViewById(R.id.saveButton);
        screenshotButton = mButtonsView.findViewById(R.id.screenshotButton);
        mAnnotationButton = mButtonsView.findViewById(R.id.annotationButton);

        inkOperationLayout = mButtonsView.findViewById(R.id.inkOperationLayout);
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

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.showSoftInput(mSearchText, 0);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), 0);
    }

    private void search(int direction) {
        hideKeyboard();
        int displayPage = mDocView.getDisplayedViewIndex();
        SearchTaskResult r = SearchTaskResult.get();
        int searchPage = r != null ? r.pageNumber : -1;
        mSearchTask.go(mSearchText.getText().toString(), direction, displayPage, searchPage);
    }

    @Override
    public boolean onSearchRequested() {
        if (mButtonsVisible && mTopBarMode == TopBarMode.Search) {
            hideButtons();
        } else {
            showButtons();
            searchModeOn();
        }
        return super.onSearchRequested();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mButtonsVisible && mTopBarMode != TopBarMode.Search) {
            hideButtons();
        } else {
            showButtons();
            searchModeOff();
        }
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
        if (mDocView == null || (mDocView != null && !mDocView.popHistory())) {
            super.onBackPressed();
            if (mReturnToLibraryActivity) {
                Intent intent = getPackageManager().getLaunchIntentForPackage(getComponentName().getPackageName());
                startActivity(intent);
            }
        }
    }

    private final ActivityResultLauncher<Uri> launcher = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), new ActivityResultCallback<Uri>() {
        @Override
        public void onActivityResult(Uri result) {
            try {
                boolean documentUri = false;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    documentUri = DocumentsContract.isTreeUri(result);
                    DocumentsContract.Path documentPath = null;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        documentPath = DocumentsContract.findDocumentPath(getContentResolver(), result);
                        List<String> path = documentPath.getPath();
                        for (String s : path) {
                            System.out.println("路径:"+s);
                        }
                    }
                }

                Log.i(APP, "onActivityResult: " + result + ",documentUri=" + documentUri);
                String saveDirPath = Util.getFilePathFromUri(getApplicationContext(), result);
                Log.i(APP, "onActivityResult filePathFromUri: " + saveDirPath);
//                String saveDirPath = Util.uri2path(DocumentActivity.this, result);
//                //String saveDirPath = getExternalFilesDir("批注文件").getAbsolutePath();
//                core.save(srcFilePath, saveDirPath);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    });
}
