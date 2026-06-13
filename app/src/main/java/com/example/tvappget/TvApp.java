package com.example.tvappget;

final class TvApp {
    final String name;
    final String version;
    final String downloadUrl;
    final String status;
    final String note;
    final String category;
    final boolean apk;

    TvApp(String name, String version, String downloadUrl, String status, String note) {
        this(name, version, downloadUrl, status, note, "");
    }

    TvApp(String name, String version, String downloadUrl, String status, String note, String category) {
        this.name = clean(name);
        this.version = clean(version);
        this.downloadUrl = clean(downloadUrl);
        this.status = clean(status);
        this.note = clean(note);
        this.category = clean(category);
        this.apk = this.downloadUrl.toLowerCase().contains(".apk");
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("**", "").replace("`", "").trim();
    }
}
