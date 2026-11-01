package com.CraftPlay777.jew;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String HOME = "jew://home";
    private static final String HOME_BASE = "https://jew.local/";
    private static final String SCREEN_NONE = "";
    private static final String SCREEN_WINDOWS = "windows";
    private static final String SCREEN_DOWNLOADS = "downloads";
    private static final String SCREEN_HISTORY = "history";
    private static final String SCREEN_BOOKMARKS = "bookmarks";
    private static final int FILE_REQUEST = 901;
    private static final int STORAGE_REQUEST = 902;

    private final ArrayList<WebView> tabs = new ArrayList<>();
    private final ArrayList<Bitmap> tabIcons = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int current = -1;
    private String currentScreen = SCREEN_NONE;
    private LinearLayout root, top, webHost, bottom, addressBox, downloadsContainer;
    private ScrollView downloadsScroll;
    private EditText address;
    private ProgressBar progress;
    private TextView bookmarkIcon;
    private ValueCallback<Uri[]> fileCallback;
    private SharedPreferences prefs;
    private DownloadManager downloadManager;
    private String searchEngine;
    private String themeMode;
    private boolean dark;
    private int bg, panel, fg, muted, accent, line;
    private PendingDownload pendingDownload;

    private static class PendingDownload {
        String url;
        String userAgent;
        String contentDisposition;
        String mimeType;
        PendingDownload(String url, String userAgent, String contentDisposition, String mimeType) {
            this.url = url;
            this.userAgent = userAgent;
            this.contentDisposition = contentDisposition;
            this.mimeType = mimeType;
        }
    }

    private final Runnable downloadRefresh = new Runnable() {
        @Override public void run() {
            if (SCREEN_DOWNLOADS.equals(currentScreen)) {
                refreshDownloads();
                handler.postDelayed(this, 900);
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        prefs = getSharedPreferences("jew", MODE_PRIVATE);
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        searchEngine = prefs.getString("search_engine", "Google");
        themeMode = prefs.getString("theme_mode", prefs.getBoolean("dark", false) ? "dark" : "light");
        applyPalette();
        buildInterface();
        newTab(HOME);
    }

    private int dp(int n) {
        return (int) (getResources().getDisplayMetrics().density * n + 0.5f);
    }

    private void applyPalette() {
        if ("system".equals(themeMode)) {
            int night = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            dark = night == Configuration.UI_MODE_NIGHT_YES;
        } else {
            dark = "dark".equals(themeMode);
        }
        bg = Color.parseColor(dark ? "#10141D" : "#F4F6FA");
        panel = Color.parseColor(dark ? "#1B2230" : "#FFFFFF");
        fg = Color.parseColor(dark ? "#F2F5FA" : "#172033");
        muted = Color.parseColor(dark ? "#A6B0C2" : "#697386");
        accent = Color.parseColor(dark ? "#8AA8FF" : "#315FEA");
        line = Color.parseColor(dark ? "#30394A" : "#E2E7F0");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        for (WebView web : tabs) {
            web.setBackgroundColor(bg);
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    web.getSettings().setForceDark(dark ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
                } catch (Exception ignored) { }
            }
        }
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView action(String symbol, String description, Runnable run) {
        TextView view = text(symbol, 21, fg);
        view.setTypeface(null, Typeface.BOLD);
        view.setContentDescription(description);
        view.setBackground(shape(panel, 14));
        view.setOnClickListener(v -> run.run());
        return view;
    }

    private void buildInterface() {
        root = column();
        root.setBackgroundColor(bg);
        setContentView(root);

        top = row();
        top.setPadding(dp(9), dp(7), dp(9), dp(5));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView back = action("‹", "Atrás", this::goBack);
        top.addView(back, new LinearLayout.LayoutParams(dp(38), dp(40)));
        TextView forward = action("›", "Adelante", () -> {
            WebView web = active();
            if (web != null && web.canGoForward()) web.goForward();
        });
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(38), dp(40));
        fp.leftMargin = dp(4);
        top.addView(forward, fp);

        addressBox = row();
        addressBox.setPadding(dp(11), 0, dp(8), 0);
        addressBox.setBackground(shape(panel, 16));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(42), 1);
        ap.setMargins(dp(7), 0, dp(7), 0);
        top.addView(addressBox, ap);
        TextView searchIcon = text("⌕", 22, muted);
        addressBox.addView(searchIcon, new LinearLayout.LayoutParams(dp(25), -1));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(14);
        address.setTextColor(fg);
        address.setHintTextColor(muted);
        address.setHint("Search/URL");
        address.setBackgroundColor(Color.TRANSPARENT);
        address.setPadding(dp(4), 0, dp(4), 0);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBox.addView(address, new LinearLayout.LayoutParams(0, -1, 1));
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(address.getText().toString());
                return true;
            }
            return false;
        });
        TextView reload = action("⟳", "Recargar", () -> {
            WebView web = active();
            if (web != null) {
                if (isHome(web)) showHome(); else web.reload();
            }
        });
        top.addView(reload, new LinearLayout.LayoutParams(dp(40), dp(40)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));

        webHost = column();
        root.addView(webHost, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = row();
        bottom.setPadding(dp(8), dp(5), dp(8), dp(7));
        bottom.setBackgroundColor(panel);
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(60)));
        bottomAction("⌂", "Inicio", this::loadHome);
        bottomAction("☆", "Guardar", this::bookmark);
        bottomAction("⌕", "Buscar", this::focusSearch);
        bottomAction("▦", "Ventanas", this::showWindows);
        bottomAction("⋮", "Más", () -> menu(bottom.getChildAt(4)));
        refreshBookmarkButton();
    }

    private void bottomAction(String symbol, String label, Runnable run) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        TextView icon = text(symbol, 21, fg);
        TextView caption = text(label, 10, muted);
        item.addView(icon);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(13));
        cp.topMargin = dp(2);
        item.addView(caption, cp);
        item.setOnClickListener(v -> run.run());
        bottom.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
        if ("Guardar".equals(label)) bookmarkIcon = icon;
    }

    private void focusSearch() {
        currentScreen = SCREEN_NONE;
        restoreActiveTab();
        address.requestFocus();
        address.setSelection(0, address.length());
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
    }

    private WebView active() {
        return current >= 0 && current < tabs.size() ? tabs.get(current) : null;
    }

    private boolean isHome(WebView web) {
        if (web == null) return false;
        String url = web.getUrl();
        return HOME.equals(url) || (url != null && url.startsWith(HOME_BASE));
    }

    private String displayUrl(WebView web) {
        if (web == null || isHome(web)) return "";
        String url = web.getUrl();
        return url == null ? "" : url;
    }

    private void updateAddress(WebView web) {
        if (address == null || web == null) return;
        address.setText(displayUrl(web));
        address.setHint("Search/URL");
    }

    private void refreshBookmarkButton() {
        if (bookmarkIcon == null) return;
        WebView web = active();
        boolean saved = web != null && web.getUrl() != null && isBookmarked(web.getUrl());
        bookmarkIcon.setText(saved ? "★" : "☆");
        bookmarkIcon.setTextColor(saved ? Color.YELLOW : fg);
    }

    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme();
        if ("jew-action".equalsIgnoreCase(scheme)) {
            if ("add-shortcut".equals(uri.getHost())) addShortcutDialog();
            return true;
        }
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "No se puede abrir el enlace", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void newTab(String url) {
        WebView web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setBackgroundColor(bg);
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                settings.setForceDark(dark ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
            } catch (Exception ignored) { }
        }
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!isHome(view)) addHistory(url, view.getTitle());
                if (view == active() && SCREEN_NONE.equals(currentScreen)) updateAddress(view);
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (view == active() && SCREEN_NONE.equals(currentScreen)) updateAddress(view);
                if (!isHome(view)) updateHistoryTitle(url, view.getTitle());
                if (view == active()) refreshBookmarkButton();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                if (view == active() && SCREEN_NONE.equals(currentScreen)) {
                    progress.setProgress(value);
                    progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
                }
            }
            @Override public void onReceivedIcon(WebView view, Bitmap icon) {
                int index = tabs.indexOf(view);
                if (index >= 0) {
                    while (tabIcons.size() < tabs.size()) tabIcons.add(null);
                    tabIcons.set(index, icon);
                }
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
            }
        });
        web.setDownloadListener((downloadUrl, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (downloadUrl == null || (!downloadUrl.startsWith("http://") && !downloadUrl.startsWith("https://"))) return;
            beginDownload(new PendingDownload(downloadUrl, userAgent, contentDisposition, mimeType));
        });
        tabs.add(web);
        tabIcons.add(null);
        switchTab(tabs.size() - 1);
        if (HOME.equals(url)) showHome(); else web.loadUrl(url);
    }

    private void switchTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        handler.removeCallbacks(downloadRefresh);
        current = index;
        currentScreen = SCREEN_NONE;
        webHost.removeAllViews();
        WebView web = active();
        if (web.getParent() != null) ((ViewGroup) web.getParent()).removeView(web);
        webHost.addView(web, new LinearLayout.LayoutParams(-1, -1));
        updateAddress(web);
        progress.setVisibility(View.GONE);
        refreshBookmarkButton();
    }

    private void restoreActiveTab() {
        WebView web = active();
        if (web == null) return;
        webHost.removeAllViews();
        if (web.getParent() != null) ((ViewGroup) web.getParent()).removeView(web);
        webHost.addView(web, new LinearLayout.LayoutParams(-1, -1));
        updateAddress(web);
        refreshBookmarkButton();
    }

    private void showHome() {
        WebView web = active();
        if (web == null) return;
        currentScreen = SCREEN_NONE;
        restoreActiveTab();
        String c = dark ? "#10141D" : "#F4F6FA";
        String p = dark ? "#1B2230" : "#FFFFFF";
        String f = dark ? "#F2F5FA" : "#172033";
        String m = dark ? "#A6B0C2" : "#697386";
        String a = dark ? "#8AA8FF" : "#315FEA";
        String border = dark ? "#30394A" : "#E2E7F0";
        String shortcuts = stored("shortcuts").toString();
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>*{box-sizing:border-box}body{margin:0;padding:22px 17px;font-family:Arial,sans-serif;background:" + c + ";color:" + f + "}" +
                ".head{display:flex;align-items:center;justify-content:space-between;margin:7px 2px 18px;color:" + m + ";font-size:14px}" +
                ".add{border:0;border-radius:13px;background:" + p + ";color:" + a + ";font-size:25px;width:44px;height:44px}" +
                ".grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:11px}" +
                ".tile{min-height:91px;padding:10px 4px;background:" + p + ";border:1px solid " + border + ";border-radius:17px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-decoration:none;color:" + f + ";font-size:11px;text-align:center;overflow:hidden}" +
                ".ico{width:32px;height:32px;object-fit:contain;margin-bottom:8px;border-radius:8px}.fallback{width:32px;height:32px;border-radius:10px;background:" + c + ";color:" + a + ";font-size:19px;display:flex;align-items:center;justify-content:center;margin-bottom:8px;font-weight:bold}" +
                ".empty{color:" + m + ";font-size:13px;line-height:1.5;margin:13px 2px}</style></head><body>" +
                "<div class='head'><span>Accesos directos</span><button class='add' onclick=\"location.href='jew-action://add-shortcut'\">＋</button></div>" +
                "<div class='grid' id='grid'></div><script>const items=" + shortcuts + ";const grid=document.getElementById('grid');" +
                "items.forEach(x=>{const a=document.createElement('a');a.className='tile';a.href=x.url;let host=x.url;try{host=new URL(x.url).hostname}catch(e){}const img=document.createElement('img');img.className='ico';img.alt='';img.src='https://www.google.com/s2/favicons?domain='+encodeURIComponent(host)+'&sz=96';" +
                "const fb=document.createElement('div');fb.className='fallback';fb.textContent=(x.title||host).substring(0,1).toUpperCase();img.onerror=()=>{img.replaceWith(fb)};a.appendChild(img);const label=document.createElement('span');label.textContent=x.title||host;a.appendChild(label);grid.appendChild(a)});" +
                "if(!items.length){const e=document.createElement('div');e.className='empty';e.textContent='Tus accesos directos aparecerán aquí.';document.body.appendChild(e)}</script></body></html>";
        web.loadDataWithBaseURL(HOME_BASE, html, "text/html", "UTF-8", null);
        address.setText("");
        address.setHint("Search/URL");
        refreshBookmarkButton();
    }

    private void addShortcutDialog() {
        EditText name = new EditText(this);
        name.setHint("Nombre del sitio");
        EditText url = new EditText(this);
        url.setHint("https://ejemplo.com");
        LinearLayout box = column();
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(name);
        box.addView(url);
        new AlertDialog.Builder(this).setTitle("Añadir acceso directo").setView(box)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    String value = url.getText().toString().trim();
                    if (!value.startsWith("http://") && !value.startsWith("https://")) value = "https://" + value;
                    if (value.length() > 8) {
                        String title = name.getText().toString().trim();
                        JSONArray items = stored("shortcuts");
                        try {
                            JSONObject object = new JSONObject();
                            object.put("title", title.isEmpty() ? Uri.parse(value).getHost() : title);
                            object.put("url", value);
                            items.put(object);
                            prefs.edit().putString("shortcuts", items.toString()).apply();
                            showHome();
                        } catch (Exception ignored) { }
                    }
                }).setNegativeButton("Cancelar", null).show();
    }

    private void loadHome() {
        if (active() == null) newTab(HOME); else showHome();
    }

    private String normalize(String entry) {
        String query = entry.trim();
        if (query.isEmpty()) return HOME;
        if (query.matches("(?i)^https?://.+")) return query;
        if (query.matches("(?i)^(localhost|([a-z0-9-]+\\.)+[a-z]{2,})(:\\d+)?(/.*)?$") || query.matches("^([0-9]{1,3}\\.){3}[0-9]{1,3}(:\\d+)?(/.*)?$")) return "https://" + query;
        String base = searchEngine.equals("DuckDuckGo") ? "https://duckduckgo.com/?q=" :
                searchEngine.equals("Personalizado") ? prefs.getString("search_template", "https://example.com/search?q={query}").replace("{query}", Uri.encode(query)) :
                        "https://www.google.com/search?q=";
        return searchEngine.equals("Personalizado") ? base : base + Uri.encode(query);
    }

    private void navigate(String value) {
        String url = normalize(value);
        if (active() == null) newTab(url);
        else {
            currentScreen = SCREEN_NONE;
            restoreActiveTab();
            if (HOME.equals(url)) showHome(); else active().loadUrl(url);
        }
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
        address.clearFocus();
    }

    private JSONArray stored(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private String historyUrl(JSONArray array, int index) {
        JSONObject object = array.optJSONObject(index);
        return object == null ? array.optString(index, "") : object.optString("url", "");
    }

    private String historyTitle(JSONArray array, int index) {
        JSONObject object = array.optJSONObject(index);
        if (object != null) return object.optString("title", "");
        String url = historyUrl(array, index);
        String host = Uri.parse(url).getHost();
        return host == null ? url : host;
    }

    private JSONObject historyItem(String url, String title) {
        JSONObject object = new JSONObject();
        try {
            object.put("url", url);
            object.put("title", title == null || title.trim().isEmpty() ? hostTitle(url) : title.trim());
            object.put("time", System.currentTimeMillis());
        } catch (Exception ignored) { }
        return object;
    }

    private String hostTitle(String url) {
        String host = Uri.parse(url).getHost();
        return host == null || host.isEmpty() ? url : host;
    }

    private void addHistory(String url, String title) {
        if (url == null || url.isEmpty() || url.startsWith(HOME_BASE) || HOME.equals(url)) return;
        JSONArray old = stored("history");
        JSONArray next = new JSONArray();
        next.put(historyItem(url, title));
        for (int i = 0; i < old.length() && next.length() < 100; i++) {
            String previous = historyUrl(old, i);
            if (previous.isEmpty() || previous.equals(url)) continue;
            JSONObject oldObject = old.optJSONObject(i);
            if (oldObject != null) next.put(oldObject);
            else next.put(historyItem(previous, historyTitle(old, i)));
        }
        prefs.edit().putString("history", next.toString()).apply();
    }

    private void updateHistoryTitle(String url, String title) {
        if (url == null || title == null || title.trim().isEmpty()) return;
        JSONArray old = stored("history");
        JSONArray next = new JSONArray();
        boolean changed = false;
        for (int i = 0; i < old.length(); i++) {
            String itemUrl = historyUrl(old, i);
            JSONObject item = old.optJSONObject(i);
            if (!changed && url.equals(itemUrl)) {
                next.put(historyItem(url, title));
                changed = true;
            } else if (item != null) next.put(item);
            else if (!itemUrl.isEmpty()) next.put(historyItem(itemUrl, historyTitle(old, i)));
        }
        if (changed) prefs.edit().putString("history", next.toString()).apply();
    }

    private boolean isBookmarked(String url) {
        JSONArray items = stored("bookmarks");
        for (int i = 0; i < items.length(); i++) if (url.equals(items.optString(i))) return true;
        return false;
    }

    private void bookmark() {
        WebView web = active();
        if (web == null || web.getUrl() == null || isHome(web)) {
            Toast.makeText(this, "No hay página para guardar", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = web.getUrl();
        JSONArray old = stored("bookmarks");
        JSONArray next = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < old.length(); i++) {
            String saved = old.optString(i);
            if (url.equals(saved)) removed = true;
            else next.put(saved);
        }
        if (!removed) next.put(url);
        prefs.edit().putString("bookmarks", next.toString()).apply();
        refreshBookmarkButton();
        Toast.makeText(this, removed ? "Quitado de favoritos" : "Guardado en favoritos", Toast.LENGTH_SHORT).show();
    }

    private void showWindows() {
        handler.removeCallbacks(downloadRefresh);
        currentScreen = SCREEN_WINDOWS;
        webHost.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(14), dp(14), dp(14), dp(20));
        TextView heading = text("Ventanas abiertas · " + tabs.size(), 18, fg);
        heading.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(dp(4), dp(5), 0, dp(15));
        content.addView(heading);
        for (int i = 0; i < tabs.size(); i += 2) {
            LinearLayout cardRow = row();
            cardRow.setGravity(Gravity.TOP);
            cardRow.addView(windowCard(i), new LinearLayout.LayoutParams(0, dp(158), 1));
            if (i + 1 < tabs.size()) {
                LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(158), 1);
                second.leftMargin = dp(10);
                cardRow.addView(windowCard(i + 1), second);
            } else {
                LinearLayout.LayoutParams empty = new LinearLayout.LayoutParams(0, dp(158), 1);
                empty.leftMargin = dp(10);
                View spacer = new View(this);
                cardRow.addView(spacer, empty);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(158));
            rp.bottomMargin = dp(10);
            content.addView(cardRow, rp);
        }
        LinearLayout newCard = column();
        newCard.setGravity(Gravity.CENTER);
        newCard.setBackground(shape(panel, 18));
        TextView plus = text("＋", 32, accent);
        TextView newLabel = text("Nueva ventana", 13, fg);
        newCard.addView(plus);
        newCard.addView(newLabel);
        newCard.setOnClickListener(v -> newTab(HOME));
        content.addView(newCard, new LinearLayout.LayoutParams(-1, dp(92)));
        scroll.addView(content);
        webHost.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        updateAddress(active());
        refreshBookmarkButton();
    }

    private View windowCard(int index) {
        WebView web = tabs.get(index);
        LinearLayout card = column();
        card.setPadding(dp(10), dp(9), dp(10), dp(8));
        card.setBackground(shape(panel, 18));
        LinearLayout header = row();
        Bitmap icon = index < tabIcons.size() ? tabIcons.get(index) : null;
        if (icon != null) {
            ImageView image = new ImageView(this);
            image.setImageBitmap(icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            header.addView(image, new LinearLayout.LayoutParams(dp(25), dp(25)));
        } else {
            TextView fallback = text("◎", 21, accent);
            header.addView(fallback, new LinearLayout.LayoutParams(dp(25), dp(25)));
        }
        TextView close = text("×", 22, muted);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        closeParams.leftMargin = dp(5);
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        header.addView(close, closeParams);
        close.setOnClickListener(v -> closeTabAt(index));
        card.addView(header);
        String title = web.getTitle();
        if (title == null || title.trim().isEmpty() || isHome(web)) title = "Nueva pestaña";
        TextView titleView = text(title, 13, fg);
        titleView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, 0, 1);
        titleParams.topMargin = dp(8);
        card.addView(titleView, titleParams);
        String url = isHome(web) ? "Search/URL" : web.getUrl();
        TextView urlView = text(url == null ? "" : url, 10, muted);
        urlView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        urlView.setMaxLines(2);
        card.addView(urlView, new LinearLayout.LayoutParams(-1, dp(34)));
        if (index == current) {
            TextView badge = text("ACTIVA", 9, accent);
            badge.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            badge.setTypeface(null, Typeface.BOLD);
            card.addView(badge);
        }
        card.setOnClickListener(v -> switchTab(index));
        return card;
    }

    private void closeTabAt(int index) {
        if (index < 0 || index >= tabs.size()) return;
        WebView web = tabs.remove(index);
        if (index < tabIcons.size()) tabIcons.remove(index);
        if (web.getParent() != null) ((ViewGroup) web.getParent()).removeView(web);
        web.stopLoading();
        web.destroy();
        if (tabs.isEmpty()) {
            current = -1;
            newTab(HOME);
            return;
        }
        if (index < current) current--;
        else if (index == current) current = Math.min(index, tabs.size() - 1);
        if (SCREEN_WINDOWS.equals(currentScreen)) showWindows();
        else switchTab(current);
    }

    private void chooseList(String key) {
        JSONArray items = stored(key);
        if (items.length() == 0) {
            Toast.makeText(this, "Sin elementos", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[items.length()];
        for (int i = 0; i < labels.length; i++) labels[i] = items.optString(i);
        new AlertDialog.Builder(this).setTitle("Favoritos").setItems(labels, (dialog, which) -> {
            if (active() != null) {
                currentScreen = SCREEN_NONE;
                restoreActiveTab();
                active().loadUrl(labels[which]);
            }
        }).setNeutralButton("Administrar", (dialog, which) -> showBookmarks()).setNegativeButton("Cerrar", null).show();
    }

    private void showBookmarks() {
        handler.removeCallbacks(downloadRefresh);
        currentScreen = SCREEN_BOOKMARKS;
        webHost.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(14), dp(14), dp(14), dp(18));
        LinearLayout header = row();
        TextView title = text("Favoritos", 19, fg);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView clear = action("⌫", "Borrar favoritos", () -> new AlertDialog.Builder(this)
                .setMessage("¿Borrar todos los favoritos?")
                .setPositiveButton("Borrar", (d, w) -> {
                    prefs.edit().remove("bookmarks").apply();
                    refreshBookmarkButton();
                    showBookmarks();
                }).setNegativeButton("Cancelar", null).show());
        header.addView(clear, new LinearLayout.LayoutParams(dp(42), dp(42)));
        content.addView(header);
        JSONArray items = stored("bookmarks");
        if (items.length() == 0) {
            TextView empty = text("Todavía no guardaste páginas.", 13, muted);
            empty.setGravity(Gravity.LEFT);
            empty.setPadding(dp(5), dp(22), dp(5), dp(15));
            content.addView(empty);
        }
        for (int i = 0; i < items.length(); i++) {
            String url = items.optString(i);
            if (!url.isEmpty()) content.addView(bookmarkRow(url));
        }
        scroll.addView(content);
        webHost.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        updateAddress(active());
    }

    private View bookmarkRow(String url) {
        LinearLayout item = row();
        item.setPadding(dp(9), dp(10), dp(8), dp(10));
        item.setBackground(shape(panel, 13));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        item.setLayoutParams(params);
        TextView icon = text("★", 22, Color.YELLOW);
        item.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(42)));
        LinearLayout labels = column();
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(hostTitle(url), 13, fg);
        name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(1);
        TextView addressLine = text(url, 10, muted);
        addressLine.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        addressLine.setMaxLines(1);
        labels.addView(name);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(-1, dp(19));
        urlParams.topMargin = dp(3);
        labels.addView(addressLine, urlParams);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.leftMargin = dp(5);
        item.addView(labels, labelParams);
        TextView remove = text("×", 22, muted);
        item.addView(remove, new LinearLayout.LayoutParams(dp(34), dp(42)));
        remove.setOnClickListener(v -> {
            JSONArray old = stored("bookmarks");
            JSONArray next = new JSONArray();
            for (int i = 0; i < old.length(); i++) if (!url.equals(old.optString(i))) next.put(old.optString(i));
            prefs.edit().putString("bookmarks", next.toString()).apply();
            refreshBookmarkButton();
            showBookmarks();
        });
        item.setOnClickListener(v -> {
            currentScreen = SCREEN_NONE;
            restoreActiveTab();
            if (active() != null) active().loadUrl(url);
        });
        return item;
    }

    private void showHistory() {
        handler.removeCallbacks(downloadRefresh);
        currentScreen = SCREEN_HISTORY;
        webHost.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(14), dp(14), dp(14), dp(18));
        LinearLayout header = row();
        TextView title = text("Historial", 19, fg);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView clear = action("⌫", "Borrar historial", () -> new AlertDialog.Builder(this)
                .setMessage("¿Borrar todo el historial?")
                .setPositiveButton("Borrar", (d, w) -> {
                    prefs.edit().remove("history").apply();
                    showHistory();
                }).setNegativeButton("Cancelar", null).show());
        header.addView(clear, new LinearLayout.LayoutParams(dp(42), dp(42)));
        content.addView(header);
        JSONArray items = stored("history");
        if (items.length() == 0) {
            TextView empty = text("Todavía no hay páginas en el historial.", 13, muted);
            empty.setGravity(Gravity.LEFT);
            empty.setPadding(dp(5), dp(22), dp(5), dp(15));
            content.addView(empty);
        }
        for (int i = 0; i < items.length(); i++) {
            final String url = historyUrl(items, i);
            if (url.isEmpty()) continue;
            String pageTitle = historyTitle(items, i);
            content.addView(historyRow(pageTitle, url));
        }
        scroll.addView(content);
        webHost.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        updateAddress(active());
    }

    private View historyRow(String title, String url) {
        LinearLayout item = row();
        item.setPadding(dp(9), dp(10), dp(8), dp(10));
        item.setBackground(shape(panel, 13));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        item.setLayoutParams(params);
        TextView icon = text("◎", 23, accent);
        item.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(42)));
        LinearLayout labels = column();
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(title, 13, fg);
        name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(1);
        TextView addressLine = text(url, 10, muted);
        addressLine.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        addressLine.setMaxLines(1);
        labels.addView(name);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(-1, dp(19));
        urlParams.topMargin = dp(3);
        labels.addView(addressLine, urlParams);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.leftMargin = dp(5);
        item.addView(labels, labelParams);
        item.setOnClickListener(v -> {
            currentScreen = SCREEN_NONE;
            restoreActiveTab();
            if (active() != null) active().loadUrl(url);
        });
        item.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setItems(new String[]{"Abrir", "Quitar del historial"}, (d, which) -> {
                if (which == 0) {
                    currentScreen = SCREEN_NONE;
                    restoreActiveTab();
                    if (active() != null) active().loadUrl(url);
                } else removeHistoryUrl(url);
            }).show();
            return true;
        });
        return item;
    }

    private void removeHistoryUrl(String url) {
        JSONArray old = stored("history");
        JSONArray next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            String itemUrl = historyUrl(old, i);
            if (!url.equals(itemUrl)) {
                JSONObject object = old.optJSONObject(i);
                if (object != null) next.put(object);
                else if (!itemUrl.isEmpty()) next.put(historyItem(itemUrl, historyTitle(old, i)));
            }
        }
        prefs.edit().putString("history", next.toString()).apply();
        showHistory();
    }


    private void beginDownload(PendingDownload download) {
        if (Build.VERSION.SDK_INT <= 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = download;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_REQUEST);
            return;
        }
        enqueueDownload(download);
    }

    private void enqueueDownload(PendingDownload download) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(download.url));
            if (download.userAgent != null) request.addRequestHeader("User-Agent", download.userAgent);
            String cookie = CookieManager.getInstance().getCookie(download.url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            String fileName = URLUtil.guessFileName(download.url, download.contentDisposition, download.mimeType);
            request.setTitle(fileName);
            request.setDescription("Descargando con Jew");
            if (download.mimeType != null && !download.mimeType.isEmpty()) request.setMimeType(download.mimeType);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            long id = downloadManager.enqueue(request);
            JSONArray ids = stored("downloads");
            ids.put(id);
            prefs.edit().putString("downloads", ids.toString()).apply();
            Toast.makeText(this, "Descarga iniciada en Descargas", Toast.LENGTH_SHORT).show();
            if (SCREEN_DOWNLOADS.equals(currentScreen)) refreshDownloads();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo iniciar la descarga: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == STORAGE_REQUEST) {
            PendingDownload download = pendingDownload;
            pendingDownload = null;
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && download != null) enqueueDownload(download);
            else Toast.makeText(this, "Se necesita permiso para guardar en Descargas", Toast.LENGTH_LONG).show();
        }
    }

    private JSONArray downloadIds() {
        return stored("downloads");
    }

    private void showDownloads() {
        currentScreen = SCREEN_DOWNLOADS;
        webHost.removeAllViews();
        downloadsScroll = new ScrollView(this);
        LinearLayout content = column();
        content.setPadding(dp(14), dp(13), dp(14), dp(18));
        LinearLayout header = row();
        TextView title = text("Descargas", 19, fg);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView folder = action("↗", "Abrir carpeta Descargas", () -> openDownloadsFolder());
        header.addView(folder, new LinearLayout.LayoutParams(dp(42), dp(42)));
        content.addView(header);
        downloadsContainer = column();
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(10);
        content.addView(downloadsContainer, cp);
        downloadsScroll.addView(content);
        webHost.addView(downloadsScroll, new LinearLayout.LayoutParams(-1, -1));
        refreshDownloads();
        handler.removeCallbacks(downloadRefresh);
        handler.postDelayed(downloadRefresh, 900);
        updateAddress(active());
    }

    private void refreshDownloads() {
        if (downloadsContainer == null || !SCREEN_DOWNLOADS.equals(currentScreen)) return;
        int oldScroll = downloadsScroll == null ? 0 : downloadsScroll.getScrollY();
        downloadsContainer.removeAllViews();
        JSONArray ids = downloadIds();
        if (ids.length() == 0) {
            TextView empty = text("Todavía no hay descargas. Los archivos que descargues aparecerán aquí y también en la carpeta Descargas del dispositivo.", 13, muted);
            empty.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            empty.setPadding(dp(8), dp(20), dp(8), dp(20));
            downloadsContainer.addView(empty);
            return;
        }
        for (int i = ids.length() - 1; i >= 0; i--) {
            long id = ids.optLong(i, -1);
            if (id >= 0) downloadsContainer.addView(downloadRow(id));
        }
        if (downloadsScroll != null) downloadsScroll.post(() -> downloadsScroll.scrollTo(0, oldScroll));
    }

    private long columnLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 ? 0 : cursor.getLong(index);
    }

    private String columnString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 ? "" : cursor.getString(index);
    }

    private View downloadRow(long id) {
        LinearLayout card = column();
        card.setPadding(dp(12), dp(11), dp(12), dp(10));
        card.setBackground(shape(panel, 15));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(9);
        Cursor cursor = null;
        String title = "Descarga";
        String statusText = "Estado desconocido";
        String mime = "application/octet-stream";
        int status = -1;
        long downloaded = 0;
        long total = 0;
        try {
            cursor = downloadManager.query(new DownloadManager.Query().setFilterById(id));
            if (cursor != null && cursor.moveToFirst()) {
                title = columnString(cursor, DownloadManager.COLUMN_TITLE);
                if (title.isEmpty()) title = "Archivo descargado";
                mime = columnString(cursor, DownloadManager.COLUMN_MEDIA_TYPE);
                status = (int) columnLong(cursor, DownloadManager.COLUMN_STATUS);
                downloaded = columnLong(cursor, DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                total = columnLong(cursor, DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                statusText = downloadStatus(status, cursor);
            } else statusText = "Registro no disponible";
        } catch (Exception ignored) {
            statusText = "No se pudo consultar el estado";
        } finally {
            if (cursor != null) cursor.close();
        }

        TextView name = text(title, 13, fg);
        name.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        name.setTypeface(null, Typeface.BOLD);
        name.setMaxLines(2);
        card.addView(name);
        TextView state = text(statusText, 11, muted);
        state.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(22));
        sp.topMargin = dp(3);
        card.addView(state, sp);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        boolean complete = status == DownloadManager.STATUS_SUCCESSFUL;
        boolean running = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED;
        if (total > 0) bar.setProgress((int) Math.min(100, downloaded * 100 / total));
        else bar.setIndeterminate(running);
        card.addView(bar, new LinearLayout.LayoutParams(-1, dp(5)));
        final String resolvedMime = mime == null || mime.isEmpty() ? "application/octet-stream" : mime;
        String size = total > 0 ? formatBytes(downloaded) + " / " + formatBytes(total) : (complete ? "Descarga completa" : "" );
        TextView sizeLabel = text(size, 10, muted);
        sizeLabel.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(-1, dp(20));
        sizeParams.topMargin = dp(2);
        card.addView(sizeLabel, sizeParams);
        LinearLayout actions = row();
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView open = smallAction("Abrir", () -> openDownload(id, resolvedMime));
        open.setEnabled(complete);
        open.setAlpha(complete ? 1f : 0.4f);
        TextView share = smallAction("Compartir", () -> shareDownload(id, resolvedMime));
        share.setEnabled(complete);
        share.setAlpha(complete ? 1f : 0.4f);
        TextView remove = smallAction(running ? "Cancelar" : "Eliminar", () -> removeDownload(id));
        actions.addView(open);
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(-2, dp(34));
        shareParams.leftMargin = dp(6);
        actions.addView(share, shareParams);
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(-2, dp(34));
        removeParams.leftMargin = dp(6);
        actions.addView(remove, removeParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(38));
        actionParams.topMargin = dp(5);
        card.addView(actions, actionParams);
        return card;
    }

    private String downloadStatus(int status, Cursor cursor) {
        switch (status) {
            case DownloadManager.STATUS_PENDING: return "En espera…";
            case DownloadManager.STATUS_RUNNING: {
                long total = columnLong(cursor, DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                long done = columnLong(cursor, DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                return total > 0 ? "Descargando · " + (done * 100 / total) + "%" : "Descargando…";
            }
            case DownloadManager.STATUS_PAUSED: return "En pausa";
            case DownloadManager.STATUS_SUCCESSFUL: return "Descarga completada";
            case DownloadManager.STATUS_FAILED: return "Error en la descarga";
            default: return "Estado desconocido";
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private TextView smallAction(String label, Runnable action) {
        TextView view = text(label, 10, accent);
        view.setPadding(dp(9), 0, dp(9), 0);
        view.setBackground(shape(bg, 10));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private Uri downloadUri(long id) {
        try { return downloadManager.getUriForDownloadedFile(id); }
        catch (Exception e) { return null; }
    }

    private void openDownload(long id, String mime) {
        Uri uri = downloadUri(id);
        if (uri == null) {
            Toast.makeText(this, "El archivo todavía no está disponible", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime == null || mime.isEmpty() ? "*/*" : mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No hay una aplicación para abrir este archivo", Toast.LENGTH_LONG).show();
        }
    }

    private void shareDownload(long id, String mime) {
        Uri uri = downloadUri(id);
        if (uri == null) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mime == null || mime.isEmpty() ? "*/*" : mime);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Compartir archivo"));
    }

    private void removeDownload(long id) {
        try { downloadManager.remove(id); } catch (Exception ignored) { }
        JSONArray old = downloadIds();
        JSONArray next = new JSONArray();
        for (int i = 0; i < old.length(); i++) if (old.optLong(i, -1) != id) next.put(old.optLong(i));
        prefs.edit().putString("downloads", next.toString()).apply();
        refreshDownloads();
    }

    private void openDownloadsFolder() {
        try {
            Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Busca la carpeta Descargas en Archivos", Toast.LENGTH_LONG).show();
        }
    }

    private void menu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        String[] options = {"Nueva pestaña", "Historial", "Favoritos", "Descargas", "Recargar página", "Configuración"};
        for (String option : options) popup.getMenu().add(option);
        popup.setOnMenuItemClickListener(item -> {
            String option = item.getTitle().toString();
            WebView web = active();
            switch (option) {
                case "Nueva pestaña": newTab(HOME); break;
                case "Historial": showHistory(); break;
                case "Favoritos": showBookmarks(); break;
                case "Descargas": showDownloads(); break;
                case "Recargar página":
                    if (web != null) {
                        currentScreen = SCREEN_NONE;
                        restoreActiveTab();
                        if (isHome(web)) showHome(); else web.reload();
                    }
                    break;
                case "Configuración": settings(); break;
            }
            return true;
        });
        popup.show();
    }

    private void settings() {
        String[] options = {"Apariencia", "Buscador"};
        new AlertDialog.Builder(this).setTitle("Configuración").setItems(options, (dialog, which) -> {
            if (which == 0) appearanceSettings(); else searchSettings();
        }).setNegativeButton("Cerrar", null).show();
    }

    private void appearanceSettings() {
        String[] themes = {"Según el sistema", "Claro", "Oscuro"};
        int checked = "system".equals(themeMode) ? 0 : "light".equals(themeMode) ? 1 : 2;
        new AlertDialog.Builder(this).setTitle("Tema de Jew").setSingleChoiceItems(themes, checked, (dialog, which) -> {
            themeMode = which == 0 ? "system" : which == 1 ? "light" : "dark";
            prefs.edit().putString("theme_mode", themeMode).putBoolean("dark", "dark".equals(themeMode)).apply();
            dialog.dismiss();
            String previousScreen = currentScreen;
            applyPalette();
            rebuildInterface(previousScreen);
        }).setNegativeButton("Cerrar", null).show();
    }

    private void searchSettings() {
        String[] engines = {"Google", "DuckDuckGo", "Personalizado"};
        int checked = "DuckDuckGo".equals(searchEngine) ? 1 : "Personalizado".equals(searchEngine) ? 2 : 0;
        new AlertDialog.Builder(this).setTitle("Buscador predeterminado").setSingleChoiceItems(engines, checked, (dialog, which) -> {
            if (which < 2) {
                searchEngine = engines[which];
                prefs.edit().putString("search_engine", searchEngine).remove("search_template").apply();
                dialog.dismiss();
            } else {
                EditText input = new EditText(this);
                input.setSingleLine(true);
                input.setText(prefs.getString("search_template", "https://example.com/search?q={query}"));
                input.setHint("https://sitio.com/search?q={query}");
                new AlertDialog.Builder(this).setTitle("URL de búsqueda")
                        .setMessage("Usa {query} donde debe insertarse el texto")
                        .setView(input)
                        .setPositiveButton("Guardar", (d, n) -> {
                            String template = input.getText().toString().trim();
                            if (template.contains("{query}")) {
                                searchEngine = "Personalizado";
                                prefs.edit().putString("search_engine", searchEngine).putString("search_template", template).apply();
                            } else Toast.makeText(this, "La URL debe contener {query}", Toast.LENGTH_LONG).show();
                        }).setNegativeButton("Cancelar", null).show();
                dialog.dismiss();
            }
        }).setNegativeButton("Cerrar", null).show();
    }

    private void rebuildInterface(String previousScreen) {
        buildInterface();
        if (active() == null) {
            newTab(HOME);
            return;
        }
        restoreActiveTab();
        if (SCREEN_WINDOWS.equals(previousScreen)) showWindows();
        else if (SCREEN_DOWNLOADS.equals(previousScreen)) showDownloads();
        else if (SCREEN_HISTORY.equals(previousScreen)) showHistory();
        else if (SCREEN_BOOKMARKS.equals(previousScreen)) showBookmarks();
    }

    private void goBack() {
        if (!SCREEN_NONE.equals(currentScreen)) {
            handler.removeCallbacks(downloadRefresh);
            currentScreen = SCREEN_NONE;
            restoreActiveTab();
            return;
        }
        WebView web = active();
        if (web != null && web.canGoBack()) web.goBack();
        else if (tabs.size() > 1) closeTabAt(current);
        else new AlertDialog.Builder(this).setMessage("¿Salir de Jew?")
                    .setPositiveButton("Salir", (dialog, which) -> finish())
                    .setNegativeButton("Cancelar", null).show();
    }

    @Override public void onBackPressed() { goBack(); }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FILE_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
        }
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(downloadRefresh);
        if (active() != null) active().onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (active() != null) active().onResume();
        if (SCREEN_DOWNLOADS.equals(currentScreen)) handler.postDelayed(downloadRefresh, 400);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(downloadRefresh);
        for (WebView web : tabs) {
            web.stopLoading();
            web.destroy();
        }
        tabs.clear();
        tabIcons.clear();
        super.onDestroy();
    }
}
