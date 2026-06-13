package com.example.tvappget;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GitHubSource {
    static final String README_URL = "https://raw.githubusercontent.com/youhunwl/TVAPP/main/README.md";
    private static final String REPO = "https://github.com/youhunwl/TVAPP";
    private static final Pattern LINK = Pattern.compile("\\[下载\\]\\(([^)]+)\\)");

    List<TvApp> fetchApps() throws Exception {
        String markdown = get(README_URL);
        ArrayList<TvApp> apps = new ArrayList<>();
        boolean inTable = false;
        String[] lines = markdown.split("\\r?\\n");
        for (String line : lines) {
            if (line.startsWith("## 接口源")) {
                break;
            }
            if (line.startsWith("| APP名称")) {
                inTable = true;
                continue;
            }
            if (!inTable || !line.startsWith("|") || line.contains("---")) {
                continue;
            }
            List<String> cells = splitMarkdownRow(line);
            if (cells.size() < 5) {
                continue;
            }
            Matcher matcher = LINK.matcher(cells.get(2));
            String url = matcher.find() ? resolveLink(matcher.group(1)) : "";
            if (cells.get(0).contains("...updating") || url.length() == 0) {
                continue;
            }
            apps.add(new TvApp(cells.get(0), cells.get(1), url, cells.get(3), stripMarkdownLinks(cells.get(4))));
        }
        return apps;
    }

    List<TvApp> fetchApksFromEntry(TvApp entry) throws Exception {
        String path = githubPath(entry.downloadUrl);
        if (path.length() == 0) {
            ArrayList<TvApp> fallback = new ArrayList<>();
            fallback.add(entry);
            return fallback;
        }
        ArrayList<TvApp> result = new ArrayList<>();
        collectApks(path, entry, result, 0);
        return result;
    }

    private void collectApks(String path, TvApp parent, List<TvApp> out, int depth) throws Exception {
        if (depth > 3 || out.size() >= 60) {
            return;
        }
        String api = "https://api.github.com/repos/youhunwl/TVAPP/contents/" + encodePath(path) + "?ref=main";
        String json = get(api);
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String type = item.optString("type");
            String name = item.optString("name");
            if ("file".equals(type) && name.toLowerCase().endsWith(".apk")) {
                String download = item.optString("download_url");
                out.add(new TvApp(parent.name + " / " + trimApkName(name), parent.version, download, parent.status, parent.note));
            } else if ("dir".equals(type) && depth < 2) {
                collectApks(item.optString("path"), parent, out, depth + 1);
            }
        }
    }

    private static String get(String urlString) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "TVAppGet/1.0 Android");
        conn.setRequestProperty("Accept", "application/vnd.github+json,text/plain,*/*");
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(stream);
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + body);
        }
        return body;
    }

    private static String readAll(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private static List<String> splitMarkdownRow(String line) {
        ArrayList<String> cells = new ArrayList<>();
        String row = line.trim();
        if (row.startsWith("|")) {
            row = row.substring(1);
        }
        if (row.endsWith("|")) {
            row = row.substring(0, row.length() - 1);
        }
        StringBuilder cell = new StringBuilder();
        int bracket = 0;
        int paren = 0;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c == '[') bracket++;
            if (c == ']' && bracket > 0) bracket--;
            if (c == '(') paren++;
            if (c == ')' && paren > 0) paren--;
            if (c == '|' && bracket == 0 && paren == 0) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
    }

    private static String resolveLink(String url) {
        if (url.startsWith("http")) {
            return encodeGitHubRawUrl(url);
        }
        if (url.startsWith("/")) {
            return REPO + "/raw/refs/heads/main" + Uri.encode(url, "/");
        }
        return REPO + "/raw/refs/heads/main/" + Uri.encode(url, "/");
    }

    private static String githubPath(String url) {
        String marker = "/raw/refs/heads/main/";
        int index = url.indexOf(marker);
        if (index < 0) {
            return "";
        }
        String encodedPath = url.substring(index + marker.length());
        if (encodedPath.endsWith(".apk")) {
            return "";
        }
        if (encodedPath.endsWith("/README.md")) {
            encodedPath = encodedPath.substring(0, encodedPath.length() - "/README.md".length());
        }
        return Uri.decode(encodedPath);
    }

    private static String encodePath(String path) throws Exception {
        String[] parts = path.split("/");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) builder.append('/');
            builder.append(URLEncoder.encode(parts[i], "UTF-8").replace("+", "%20"));
        }
        return builder.toString();
    }

    private static String encodeGitHubRawUrl(String url) {
        String marker = "/raw/refs/heads/main/";
        int index = url.indexOf(marker);
        if (index < 0) {
            return url;
        }
        String prefix = url.substring(0, index + marker.length());
        String path = Uri.decode(url.substring(index + marker.length()));
        try {
            return prefix + encodePath(path);
        } catch (Exception e) {
            return url;
        }
    }

    private static String stripMarkdownLinks(String input) {
        return input.replaceAll("\\[([^]]+)\\]\\([^)]+\\)", "$1");
    }

    private static String trimApkName(String name) {
        return name.replaceAll("(?i)\\.apk$", "");
    }
}
