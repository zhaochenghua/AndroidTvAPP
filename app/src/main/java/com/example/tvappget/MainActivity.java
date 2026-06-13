package com.example.tvappget;

import android.app.Activity;
import android.content.Intent;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(11, 15, 20);
    private static final int PANEL = Color.rgb(23, 31, 42);
    private static final int FOCUS = Color.rgb(0, 184, 148);
    private static final int TEXT = Color.rgb(236, 240, 241);
    private static final int MUTED = Color.rgb(155, 166, 178);

    private final GitHubSource source = new GitHubSource();
    private final ArrayList<TvApp> apps = new ArrayList<>();

    private LinearLayout listContainer;
    private TextView titleView;
    private TextView versionView;
    private TextView statusView;
    private TextView noteView;
    private TextView messageView;
    private Button primaryButton;
    private Button refreshButton;
    private ProgressBar progressBar;
    private TvApp selected;
    private DownloadTask currentDownloadTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadApps();
    }

    @Override
    protected void onDestroy() {
        if (currentDownloadTask != null) {
            currentDownloadTask.cancel(true);
        }
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(22), dp(28), dp(22));
        root.setBackgroundColor(BG);

        TextView header = new TextView(this);
        header.setText("TV应用获取");
        header.setTextColor(TEXT);
        header.setTextSize(30);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(46)));

        messageView = new TextView(this);
        messageView.setTextColor(MUTED);
        messageView.setTextSize(15);
        messageView.setSingleLine(true);
        root.addView(messageView, new LinearLayout.LayoutParams(-1, dp(30)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setBaselineAligned(false);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        body.addView(scrollView, new LinearLayout.LayoutParams(0, -1, 1.35f));

        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(24), dp(20), dp(24), dp(20));
        detail.setBackgroundColor(PANEL);
        body.addView(detail, new LinearLayout.LayoutParams(0, -1, 1f));

        titleView = detailText(26, true, TEXT);
        detail.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        versionView = detailText(17, false, MUTED);
        detail.addView(versionView, new LinearLayout.LayoutParams(-1, dp(36)));
        statusView = detailText(18, true, TEXT);
        detail.addView(statusView, new LinearLayout.LayoutParams(-1, dp(38)));
        noteView = detailText(18, false, TEXT);
        noteView.setLineSpacing(dp(3), 1.0f);
        detail.addView(noteView, new LinearLayout.LayoutParams(-1, 0, 1f));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        detail.addView(progressBar, new LinearLayout.LayoutParams(-1, dp(28)));

        primaryButton = actionButton("下载并安装");
        primaryButton.setOnClickListener(v -> runPrimaryAction());
        detail.addView(primaryButton, new LinearLayout.LayoutParams(-1, dp(58)));

        refreshButton = actionButton("刷新列表");
        refreshButton.setOnClickListener(v -> loadApps());
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(-1, dp(54));
        refreshParams.topMargin = dp(12);
        detail.addView(refreshButton, refreshParams);

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
        button.setTextSize(18);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setBackgroundColor(Color.rgb(34, 45, 59));
        button.setOnFocusChangeListener((v, hasFocus) -> v.setBackgroundColor(hasFocus ? FOCUS : Color.rgb(34, 45, 59)));
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
                renderList(apps);
            }
        }.execute();
    }

    private void renderList(List<TvApp> data) {
        listContainer.removeAllViews();
        for (TvApp app : data) {
            TextView row = createRow(app);
            listContainer.addView(row, new LinearLayout.LayoutParams(-1, dp(74)));
        }
        if (!data.isEmpty()) {
            selected = data.get(0);
            updateDetail();
            View first = listContainer.getChildAt(0);
            if (first != null) {
                first.requestFocus();
            }
        }
    }

    private TextView createRow(TvApp app) {
        TextView row = new TextView(this);
        row.setFocusable(true);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(14), 0);
        row.setTextColor(TEXT);
        row.setTextSize(19);
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
        versionView.setText("版本：" + empty(selected.version, "未知"));
        statusView.setText("状态：" + empty(selected.status, "未知"));
        noteView.setText(empty(selected.note, "暂无简介"));
        primaryButton.setText(selected.apk ? "下载并安装" : "查看可下载版本");
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
                showMessage("找到 " + result.size() + " 个 APK，选择版本后按 OK 下载。按刷新列表返回总表。");
                renderList(result);
            }
        }.execute(app);
    }

    private void downloadApk(TvApp app) {
        try {
            if (currentDownloadTask != null) {
                showMessage("已有下载任务正在进行，请稍候。");
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
            String fileName = safeFileName(app.name) + ".apk";
            File file = new File(dir, fileName);
            if (file.exists() && !file.delete()) {
                showMessage("旧安装包无法替换，请稍后重试。");
                return;
            }
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setIndeterminate(false);
            progressBar.setProgress(0);
            currentDownloadTask = new DownloadTask(app, file);
            currentDownloadTask.execute();
        } catch (Exception e) {
            showMessage("下载启动失败：" + cleanError(e));
        }
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

    private final class DownloadTask extends AsyncTask<Void, Integer, File> {
        private static final int BUFFER_SIZE = 32 * 1024;

        private final TvApp app;
        private final File file;
        private Exception error;

        DownloadTask(TvApp app, File file) {
            this.app = app;
            this.file = file;
        }

        @Override
        protected void onPreExecute() {
            showMessage("正在应用内下载：" + app.name);
            progressBar.setIndeterminate(true);
        }

        @Override
        protected File doInBackground(Void... params) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(app.downloadUrl);
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
                     OutputStream output = new FileOutputStream(file)) {
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
                if (file.length() == 0) {
                    throw new IllegalStateException("文件为空");
                }
                return file;
            } catch (Exception e) {
                error = e;
                if (file.exists()) {
                    file.delete();
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
            progressBar.setIndeterminate(false);
            progressBar.setProgress(progress);
            showMessage("正在下载：" + app.name + "  " + progress + "%");
        }

        @Override
        protected void onPostExecute(File result) {
            currentDownloadTask = null;
            if (result == null) {
                progressBar.setVisibility(View.GONE);
                showMessage("下载失败：" + cleanError(error));
                return;
            }
            progressBar.setProgress(100);
            showMessage("下载完成，正在打开安装器：" + app.name);
            installApk(result);
        }

        @Override
        protected void onCancelled() {
            currentDownloadTask = null;
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
