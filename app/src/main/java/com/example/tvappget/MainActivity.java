package com.example.tvappget;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(11, 15, 20);
    private static final int PANEL = Color.rgb(23, 31, 42);
    private static final int FOCUS = Color.rgb(0, 184, 148);
    private static final int TEXT = Color.rgb(236, 240, 241);
    private static final int MUTED = Color.rgb(155, 166, 178);
    private static final int BUTTON_BG = Color.rgb(34, 45, 59);
    private static final int DANGER = Color.rgb(185, 65, 72);

    private final GitHubSource source = new GitHubSource();
    private final ArrayList<TvApp> apps = new ArrayList<>();
    private final Map<String, DownloadItem> downloads = new LinkedHashMap<>();

    private LinearLayout listContainer;
    private LinearLayout categoryContainer;
    private LinearLayout downloadContainer;
    private TextView titleView;
    private TextView versionView;
    private TextView statusView;
    private TextView noteView;
    private TextView messageView;
    private Button refreshButton;
    private ProgressBar progressBar;
    private ImageView qrCodeView;
    private TvApp selected;
    private String currentCategory = "全部";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        generateQrCode();
        loadApps();
    }

    @Override
    protected void onDestroy() {
        for (DownloadItem item : downloads.values()) {
            if (item.task != null) {
                item.task.cancel(true);
            }
        }
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(22), dp(28), dp(22));
        root.setBackgroundColor(BG);

        LinearLayout headerBar = new LinearLayout(this);
        headerBar.setOrientation(LinearLayout.HORIZONTAL);
        headerBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView header = new TextView(this);
        header.setText("TV应用获取");
        header.setTextColor(TEXT);
        header.setTextSize(26);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        headerBar.addView(header, new LinearLayout.LayoutParams(0, dp(40), 1f));

        refreshButton = actionButton("刷新列表");
        refreshButton.setTextSize(14);
        refreshButton.setOnClickListener(v -> loadApps());
        LinearLayout.LayoutParams refreshBtnParams = new LinearLayout.LayoutParams(dp(120), dp(36));
        headerBar.addView(refreshButton, refreshBtnParams);

        root.addView(headerBar, new LinearLayout.LayoutParams(-1, dp(40)));

        messageView = new TextView(this);
        messageView.setTextColor(MUTED);
        messageView.setTextSize(13);
        messageView.setSingleLine(true);
        root.addView(messageView, new LinearLayout.LayoutParams(-1, dp(24)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setBaselineAligned(false);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout listPanel = new LinearLayout(this);
        listPanel.setOrientation(LinearLayout.VERTICAL);
        body.addView(listPanel, new LinearLayout.LayoutParams(0, -1, 1.35f));

        HorizontalScrollView categoryScroll = new HorizontalScrollView(this);
        categoryScroll.setHorizontalScrollBarEnabled(false);
        categoryContainer = new LinearLayout(this);
        categoryContainer.setOrientation(LinearLayout.HORIZONTAL);
        categoryScroll.addView(categoryContainer, new HorizontalScrollView.LayoutParams(-2, -1));
        listPanel.addView(categoryScroll, new LinearLayout.LayoutParams(-1, dp(44)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        listPanel.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(16), dp(14), dp(16), dp(14));
        detail.setBackgroundColor(PANEL);
        body.addView(detail, new LinearLayout.LayoutParams(0, -1, 1f));

        titleView = detailText(22, true, TEXT);
        titleView.setSingleLine(true);
        detail.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        versionView = detailText(14, false, MUTED);
        detail.addView(versionView, new LinearLayout.LayoutParams(-1, dp(26)));
        statusView = detailText(15, true, TEXT);
        detail.addView(statusView, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout qrSection = new LinearLayout(this);
        qrSection.setOrientation(LinearLayout.HORIZONTAL);
        qrSection.setGravity(Gravity.CENTER_VERTICAL);
        qrSection.setPadding(0, dp(4), 0, dp(4));
        qrSection.setBackgroundColor(PANEL);

        TextView qrLabel = detailText(12, false, MUTED);
        qrLabel.setText("手机扫码访问源网页\n" + GitHubSource.REPO);
        qrLabel.setLineSpacing(dp(2), 1.0f);
        qrSection.addView(qrLabel, new LinearLayout.LayoutParams(0, dp(130), 0.45f));

        qrCodeView = new ImageView(this);
        qrCodeView.setAdjustViewBounds(true);
        qrCodeView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrSection.addView(qrCodeView, new LinearLayout.LayoutParams(0, dp(120), 0.55f));

        detail.addView(qrSection, new LinearLayout.LayoutParams(-1, dp(140)));

        noteView = detailText(15, false, TEXT);
        noteView.setLineSpacing(dp(2), 1.0f);
        detail.addView(noteView, new LinearLayout.LayoutParams(-1, 0, 1f));

        ScrollView downloadScroll = new ScrollView(this);
        downloadScroll.setFillViewport(false);
        downloadContainer = new LinearLayout(this);
        downloadContainer.setOrientation(LinearLayout.VERTICAL);
        downloadScroll.addView(downloadContainer, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(-1, dp(88));
        downloadParams.topMargin = dp(6);
        detail.addView(downloadScroll, downloadParams);
        renderDownloads();

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        detail.addView(progressBar, new LinearLayout.LayoutParams(-1, dp(18)));

        setContentView(root);
    }

    private TextView detailText(int sp, boolean bold, int color) {
        TextView text = new TextView(this);
        text.setTextColor(color);
        text.setTextSize(sp);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return text;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setBackgroundColor(BUTTON_BG);
        button.setOnFocusChangeListener((v, hasFocus) -> v.setBackgroundColor(hasFocus ? FOCUS : BUTTON_BG));
        return button;
    }

    private void loadApps() {
        setBusy("正在从 GitHub 获取应用列表...");
        listContainer.removeAllViews();
        new AsyncTask<Void, Void, List<TvApp>>() {
            Exception error;

            @Override
            protected List<TvApp> doInBackground(Void... params) {
                try {
                    return source.fetchApps();
                } catch (Exception e) {
                    error = e;
                    return new ArrayList<>();
                }
            }

            @Override
            protected void onPostExecute(List<TvApp> result) {
                progressBar.setVisibility(View.GONE);
                apps.clear();
                apps.addAll(result);
                if (error != null) {
                    showMessage("加载失败：" + cleanError(error));
                    return;
                }
                showMessage("已加载 " + apps.size() + " 个应用。方向键选择，OK 键下载或查看版本。");
                renderCategories();
                renderCurrentCategory();
            }
        }.execute();
    }

    private void renderCategories() {
        categoryContainer.removeAllViews();
        addCategoryButton("全部");
        ArrayList<String> categories = new ArrayList<>();
        for (TvApp app : apps) {
            String category = empty(app.category, "未分类");
            if (!categories.contains(category)) {
                categories.add(category);
                addCategoryButton(category);
            }
        }
    }

    private void addCategoryButton(String category) {
        Button button = actionButton(category);
        button.setTextSize(13);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setOnClickListener(v -> {
            currentCategory = category;
            renderCategories();
            renderCurrentCategory();
        });
        button.setBackgroundColor(category.equals(currentCategory) ? FOCUS : BUTTON_BG);
        button.setOnFocusChangeListener((v, hasFocus) -> v.setBackgroundColor(hasFocus || category.equals(currentCategory) ? FOCUS : BUTTON_BG));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(36));
        params.rightMargin = dp(8);
        categoryContainer.addView(button, params);
    }

    private void renderCurrentCategory() {
        if ("全部".equals(currentCategory)) {
            renderList(apps);
            return;
        }
        ArrayList<TvApp> filtered = new ArrayList<>();
        for (TvApp app : apps) {
            if (currentCategory.equals(empty(app.category, "未分类"))) {
                filtered.add(app);
            }
        }
        renderList(filtered);
    }

    private void renderList(List<TvApp> data) {
        listContainer.removeAllViews();
        for (TvApp app : data) {
            TextView row = createRow(app);
            listContainer.addView(row, new LinearLayout.LayoutParams(-1, dp(60)));
        }
        if (!data.isEmpty()) {
            selected = data.get(0);
            updateDetail();
            View first = listContainer.getChildAt(0);
            if (first != null) {
                first.requestFocus();
            }
        } else {
            selected = null;
            titleView.setText("");
            versionView.setText("");
            statusView.setText("");
            noteView.setText("当前分类暂无应用");
        }
    }

    private TextView createRow(TvApp app) {
        TextView row = new TextView(this);
        row.setFocusable(true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(12), 0);
        row.setTextColor(TEXT);
        row.setTextSize(16);
        row.setSingleLine(false);
        row.setText(app.name + "\n" + app.version + "  " + app.status + "  " + (app.apk ? "APK" : "目录"));
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackgroundColor(hasFocus ? Color.rgb(28, 110, 94) : Color.TRANSPARENT);
            if (hasFocus) {
                selected = app;
                updateDetail();
            }
        });
        row.setOnClickListener(v -> {
            selected = app;
            runPrimaryAction();
        });
        row.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.getAction() == KeyEvent.ACTION_UP) {
                selected = app;
                runPrimaryAction();
                return true;
            }
            return false;
        });
        return row;
    }

    private void updateDetail() {
        if (selected == null) {
            return;
        }
        titleView.setText(selected.name);
        versionView.setText("分类：" + empty(selected.category, "未分类") + "  版本：" + empty(selected.version, "未知"));
        statusView.setText("状态：" + empty(selected.status, "未知"));
        noteView.setText(empty(selected.note, "暂无简介"));
    }

    private void runPrimaryAction() {
        if (selected == null) {
            return;
        }
        if (selected.apk) {
            downloadApk(selected);
        } else {
            loadVariants(selected);
        }
    }

    private void loadVariants(TvApp app) {
        setBusy("正在读取 " + app.name + " 的目录...");
        new AsyncTask<TvApp, Void, List<TvApp>>() {
            Exception error;

            @Override
            protected List<TvApp> doInBackground(TvApp... params) {
                try {
                    return source.fetchApksFromEntry(params[0]);
                } catch (Exception e) {
                    error = e;
                    return new ArrayList<>();
                }
            }

            @Override
            protected void onPostExecute(List<TvApp> result) {
                progressBar.setVisibility(View.GONE);
                if (error != null || result.isEmpty()) {
                    showMessage("没有找到 APK，可在项目页面查看该条目。");
                    return;
                }
                currentCategory = "全部";
                showMessage("找到 " + result.size() + " 个 APK，选择版本后按 OK 下载。按刷新列表返回总表。");
                categoryContainer.removeAllViews();
                addCategoryButton("全部");
                renderList(result);
            }
        }.execute(app);
    }

    private void downloadApk(TvApp app) {
        try {
            String key = downloadKey(app);
            DownloadItem existing = downloads.get(key);
            if (existing != null && existing.running) {
                showMessage("已在下载队列中：" + app.name);
                focusDownload(existing);
                return;
            }
            File dir = getExternalFilesDir("downloads");
            if (dir == null) {
                showMessage("无法访问下载目录。");
                return;
            }
            if (!dir.exists() && !dir.mkdirs()) {
                showMessage("无法创建下载目录。");
                return;
            }
            String fileName = safeFileName(app.name + "_" + app.version) + ".apk";
            File file = new File(dir, fileName);
            if (file.exists() && !file.delete()) {
                showMessage("旧安装包无法替换，请稍后重试。");
                return;
            }
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setIndeterminate(false);
            progressBar.setProgress(0);
            DownloadItem item = new DownloadItem(key, app, file);
            downloads.put(key, item);
            renderDownloads();
            item.task = new DownloadTask(key, item);
            item.task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            showMessage("已加入下载：" + app.name);
        } catch (Exception e) {
            showMessage("下载启动失败：" + cleanError(e));
        }
    }

    private void renderDownloads() {
        if (downloadContainer == null) {
            return;
        }
        downloadContainer.removeAllViews();
        if (downloads.isEmpty()) {
            TextView empty = detailText(15, false, MUTED);
            empty.setText("暂无下载任务");
            empty.setGravity(Gravity.CENTER_VERTICAL);
            downloadContainer.addView(empty, new LinearLayout.LayoutParams(-1, dp(32)));
            return;
        }
        for (DownloadItem item : downloads.values()) {
            downloadContainer.addView(createDownloadRow(item), new LinearLayout.LayoutParams(-1, dp(52)));
        }
    }

    private View createDownloadRow(DownloadItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView name = detailText(13, true, TEXT);
        name.setSingleLine(true);
        name.setText(item.app.name);
        TextView state = detailText(12, false, MUTED);
        state.setSingleLine(true);
        state.setText(item.statusText());
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(item.progress);
        bar.setIndeterminate(item.running && item.progress == 0);
        info.addView(name, new LinearLayout.LayoutParams(-1, dp(18)));
        info.addView(state, new LinearLayout.LayoutParams(-1, dp(17)));
        info.addView(bar, new LinearLayout.LayoutParams(-1, dp(12)));
        row.addView(info, new LinearLayout.LayoutParams(0, -1, 1f));

        Button action = actionButton(item.running ? "取消" : item.finished ? "安装" : "移除");
        action.setTextSize(13);
        action.setOnClickListener(v -> {
            if (item.running) {
                cancelDownload(item);
            } else if (item.finished) {
                installApk(item.file);
            } else {
                removeDownload(item);
            }
        });
        action.setOnFocusChangeListener((v, hasFocus) -> v.setBackgroundColor(hasFocus ? (item.running ? DANGER : FOCUS) : BUTTON_BG));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(68), dp(38));
        actionParams.leftMargin = dp(8);
        row.addView(action, actionParams);
        return row;
    }

    private void focusDownload(DownloadItem item) {
        int index = new ArrayList<>(downloads.values()).indexOf(item);
        if (index >= 0 && downloadContainer != null) {
            View row = downloadContainer.getChildAt(index);
            if (row instanceof ViewGroup) {
                View button = ((ViewGroup) row).getChildAt(1);
                button.requestFocus();
            }
        }
    }

    private void cancelDownload(DownloadItem item) {
        if (item.task != null) {
            item.task.cancel(true);
        }
        item.running = false;
        item.cancelled = true;
        item.progress = 0;
        if (item.file.exists()) {
            item.file.delete();
        }
        renderDownloads();
        showMessage("已取消下载：" + item.app.name);
    }

    private void removeDownload(DownloadItem item) {
        downloads.remove(item.key);
        renderDownloads();
        showMessage("已移除任务：" + item.app.name);
    }

    private void installApk(File file) {
        if (file == null || !file.exists()) {
            showMessage("安装包不存在。");
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "请允许本应用安装未知来源应用后再返回", Toast.LENGTH_LONG).show();
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            settings.setData(Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            return;
        }
        Uri uri;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT >= 24) {
            uri = new Uri.Builder()
                    .scheme("content")
                    .authority(getPackageName() + ApkProvider.AUTHORITY_SUFFIX)
                    .appendPath(file.getName())
                    .build();
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(file);
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void setBusy(String message) {
        progressBar.setVisibility(View.VISIBLE);
        showMessage(message);
        progressBar.setIndeterminate(true);
    }

    private void showMessage(String message) {
        progressBar.setIndeterminate(false);
        messageView.setText(message);
    }

    private static String safeFileName(String name) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (safe.length() > 60) {
            safe = safe.substring(0, 60);
        }
        return safe.length() == 0 ? "download" : safe;
    }

    private static String empty(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value;
    }

    private static String cleanError(Exception e) {
        String message = e.getMessage();
        return message == null || message.length() == 0 ? e.getClass().getSimpleName() : message;
    }

    private static String downloadKey(TvApp app) {
        return app.downloadUrl.length() == 0 ? app.name + "|" + app.version : app.downloadUrl;
    }

    private final class DownloadItem {
        final String key;
        final TvApp app;
        final File file;
        DownloadTask task;
        int progress;
        int lastRenderedProgress = -1;
        boolean running = true;
        boolean finished;
        boolean cancelled;
        Exception error;

        DownloadItem(String key, TvApp app, File file) {
            this.key = key;
            this.app = app;
            this.file = file;
        }

        String statusText() {
            if (finished) {
                return "已完成，按安装可再次打开";
            }
            if (cancelled) {
                return "已取消";
            }
            if (error != null) {
                return "失败：" + cleanError(error);
            }
            return progress > 0 ? "下载中 " + progress + "%" : "正在连接...";
        }
    }

    private final class DownloadTask extends AsyncTask<Void, Integer, File> {
        private static final int BUFFER_SIZE = 32 * 1024;

        private final String key;
        private final DownloadItem item;

        DownloadTask(String key, DownloadItem item) {
            this.key = key;
            this.item = item;
        }

        @Override
        protected void onPreExecute() {
            showMessage("正在应用内下载：" + item.app.name);
            progressBar.setIndeterminate(true);
        }

        @Override
        protected File doInBackground(Void... params) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(item.app.downloadUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "TVAppGet/1.0");

                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException("HTTP " + code);
                }

                int total = connection.getContentLength();
                byte[] buffer = new byte[BUFFER_SIZE];
                long downloaded = 0L;
                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     OutputStream output = new FileOutputStream(item.file)) {
                    int read;
                    while (!isCancelled() && (read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                        downloaded += read;
                        if (total > 0) {
                            publishProgress((int) Math.min(99, downloaded * 100 / total));
                        }
                    }
                    output.flush();
                }

                if (isCancelled()) {
                    throw new IllegalStateException("下载已取消");
                }
                if (item.file.length() == 0) {
                    throw new IllegalStateException("文件为空");
                }
                return item.file;
            } catch (Exception e) {
                item.error = e;
                if (item.file.exists()) {
                    item.file.delete();
                }
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (values.length == 0) {
                return;
            }
            int progress = values[0];
            item.progress = progress;
            progressBar.setIndeterminate(false);
            progressBar.setProgress(progress);
            if (item.lastRenderedProgress < 0 || progress - item.lastRenderedProgress >= 5 || progress >= 99) {
                item.lastRenderedProgress = progress;
                renderDownloads();
            }
            showMessage("正在下载：" + item.app.name + "  " + progress + "%");
        }

        @Override
        protected void onPostExecute(File result) {
            item.running = false;
            item.task = null;
            if (result == null) {
                progressBar.setVisibility(View.GONE);
                renderDownloads();
                showMessage("下载失败：" + item.app.name + "，" + cleanError(item.error));
                return;
            }
            item.finished = true;
            item.progress = 100;
            progressBar.setProgress(100);
            renderDownloads();
            showMessage("下载完成，正在打开安装器：" + item.app.name);
            installApk(result);
        }

        @Override
        protected void onCancelled() {
            DownloadItem latest = downloads.get(key);
            if (latest == item) {
                item.running = false;
                item.cancelled = true;
                item.task = null;
            }
            if (item.file.exists()) {
                item.file.delete();
            }
            renderDownloads();
        }
    }

    private void generateQrCode() {
        int size = 400;
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(GitHubSource.REPO, BarcodeFormat.QR_CODE, size, size, hints);

            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            qrCodeView.setImageBitmap(bitmap);
        } catch (WriterException ignored) {
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
