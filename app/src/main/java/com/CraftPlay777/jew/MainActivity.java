package com.CraftPlay777.jew;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
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
    private final ArrayList<WebView> tabs = new ArrayList<>();
    private int current = -1;
    private LinearLayout root, top, webHost, bottom, addressBox;
    private EditText address;
    private ProgressBar progress;
    private TextView tabCount;
    private IconView bookmarkIcon;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_REQUEST = 901;
    private SharedPreferences prefs;
    private boolean dark, downloadsOpen = false;
    private String searchEngine;
    private int bg, panel, fg, muted, accent, line;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("jew", MODE_PRIVATE);
        searchEngine = prefs.getString("search_engine", "Google");
        updateThemeMode();
        applyPalette();
        buildInterface();
        newTab(HOME);
    }

    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density*n+0.5f); }
    private void updateThemeMode() {
        String mode = prefs.getString("theme_mode", prefs.getBoolean("dark", false) ? "dark" : "light");
        dark = mode.equals("dark") || (mode.equals("system") &&
                (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
    }
    private void applyPalette() {
        setTheme(dark ? android.R.style.Theme_Material : android.R.style.Theme_Material_Light);
        bg = Color.parseColor(dark ? "#10141D" : "#F4F6FA");
        panel = Color.parseColor(dark ? "#1B2230" : "#FFFFFF");
        fg = Color.parseColor(dark ? "#F2F5FA" : "#172033");
        muted = Color.parseColor(dark ? "#A6B0C2" : "#697386");
        accent = Color.parseColor(dark ? "#9BB4FF" : "#315FEA");
        line = Color.parseColor(dark ? "#30394A" : "#E2E7F0");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(panel);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 :
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL); t.setIncludeFontPadding(false); return t;
    }
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private void buildInterface() {
        root=column(); root.setBackgroundColor(bg); setContentView(root);
        top=row(); top.setPadding(dp(8),dp(7),dp(8),dp(5)); root.addView(top,new LinearLayout.LayoutParams(-1,dp(58)));
        addTopIcon("back",()->goBack(),38);
        addTopIcon("forward",()->{WebView w=active();if(w!=null&&w.canGoForward())w.goForward();},38);
        addressBox=row(); addressBox.setPadding(dp(11),0,dp(8),0); addressBox.setBackground(shape(panel,18));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(42),1); ap.setMargins(dp(5),0,dp(5),0); top.addView(addressBox,ap);
        IconView search=new IconView(this,"search",muted); addressBox.addView(search,new LinearLayout.LayoutParams(dp(23),-1));
        address=new EditText(this); address.setSingleLine(true); address.setTextSize(14); address.setTextColor(fg);
        address.setHintTextColor(muted); address.setHint("Search/URL"); address.setBackgroundColor(Color.TRANSPARENT);
        address.setPadding(dp(4),0,dp(4),0); address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO); addressBox.addView(address,new LinearLayout.LayoutParams(0,-1,1));
        address.setOnEditorActionListener((v,a,event)->{
            if(a==EditorInfo.IME_ACTION_GO||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
                navigate(address.getText().toString()); return true;
            } return false;
        });
        addTopIcon("refresh",()->{WebView w=active();if(w!=null){if(isHome(w))showHome();else w.reload();}},40);
        tabCount=text("1",14,fg); tabCount.setGravity(Gravity.CENTER); tabCount.setBackground(shape(panel,14));
        tabCount.setOnClickListener(v->tabMenu()); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(dp(38),dp(40));
        tp.setMargins(dp(3),0,0,0); top.addView(tabCount,tp);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent)); progress.setVisibility(View.GONE);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));
        webHost=column(); root.addView(webHost,new LinearLayout.LayoutParams(-1,0,1));
        bottom=row(); bottom.setPadding(dp(8),dp(4),dp(8),dp(5)); bottom.setBackgroundColor(panel);
        root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(60)));
        bottomAction("home","Inicio",()->loadHome());
        bottomAction("star","Favoritos",()->bookmark());
        bottomAction("search","Buscar",()->focusSearch());
        bottomAction("tabs","Pestañas",()->tabMenu());
        bottomAction("more","Más",()->menu(bottom.getChildAt(4)));
        updateBookmarkIcon();
    }
    private void addTopIcon(String icon,Runnable run,int width) {
        IconView v=new IconView(this,icon,fg); v.setBackground(shape(panel,14));
        v.setOnClickListener(w->run.run()); top.addView(v,new LinearLayout.LayoutParams(dp(width),dp(40)));
    }
    private void bottomAction(String symbol,String label,Runnable run) {
        LinearLayout item=column(); item.setGravity(Gravity.CENTER); IconView icon=new IconView(this,symbol,fg);
        item.addView(icon,new LinearLayout.LayoutParams(-1,dp(29)));
        TextView caption=text(label,10,muted); caption.setGravity(Gravity.CENTER);
        item.addView(caption,new LinearLayout.LayoutParams(-1,dp(15)));
        item.setOnClickListener(v->run.run()); bottom.addView(item,new LinearLayout.LayoutParams(0,-1,1));
        if(symbol.equals("star")) bookmarkIcon=icon;
    }
    private void focusSearch() {
        if(downloadsOpen) closeDownloadsPage();
        address.requestFocus(); address.setSelection(0,address.length());
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(address,InputMethodManager.SHOW_IMPLICIT);
    }
    private WebView active(){return current>=0&&current<tabs.size()?tabs.get(current):null;}
    private boolean isHome(WebView w){return w!=null&&(HOME.equals(w.getUrl())||(w.getUrl()!=null&&w.getUrl().startsWith("https://jew.local/")));}
    private boolean isBookmarked(String url) {
        if(url==null)return false; JSONArray a=stored("bookmarks");
        for(int i=0;i<a.length();i++)if(url.equals(a.optString(i)))return true; return false;
    }
    private void updateBookmarkIcon() {
        if(bookmarkIcon!=null){boolean saved=active()!=null&&isBookmarked(active().getUrl());
            bookmarkIcon.setTint(saved?Color.rgb(255,193,7):fg); bookmarkIcon.setFilled(saved);}
    }
    private void newTab(String url) {
        if(downloadsOpen)closeDownloadsPage();
        WebView web=new WebView(this); WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setSupportZoom(true);
        s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false); s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true); s.setAllowFileAccess(false); s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setBackgroundColor(bg);
        if(android.os.Build.VERSION.SDK_INT>=29){try{s.setForceDark(dark?WebSettings.FORCE_DARK_ON:WebSettings.FORCE_DARK_OFF);}catch(Exception ignored){}}
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest request){
                Uri u=request.getUrl(); String scheme=u.getScheme();
                if("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))return false;
                try{startActivity(new Intent(Intent.ACTION_VIEW,u));}
                catch(Exception e){Toast.makeText(MainActivity.this,"No se puede abrir el enlace",Toast.LENGTH_SHORT).show();}
                return true;
            }
            @Override public void onPageFinished(WebView w,String url){
                if(w==active()){address.setText(isHome(w)?"":url);updateBookmarkIcon();}
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView w,int p){if(w==active()&&!downloadsOpen){progress.setProgress(p);progress.setVisibility(p>=100?View.GONE:View.VISIBLE);}}
            @Override public boolean onShowFileChooser(WebView w,ValueCallback<Uri[]> cb,FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null); fileCallback=cb;
                try{startActivityForResult(params.createIntent(),FILE_REQUEST);return true;}
                catch(Exception e){fileCallback=null;cb.onReceiveValue(null);return false;}
            }
        });
        web.setDownloadListener((downloadUrl,userAgent,contentDisposition,mimeType,contentLength)->{
            if(!downloadUrl.startsWith("http://")&&!downloadUrl.startsWith("https://"))return;
            try{
                DownloadManager.Request r=new DownloadManager.Request(Uri.parse(downloadUrl));
                r.addRequestHeader("User-Agent",userAgent==null?"":userAgent);
                String cookie=CookieManager.getInstance().getCookie(downloadUrl);if(cookie!=null)r.addRequestHeader("Cookie",cookie);
                String name=URLUtil.guessFileName(downloadUrl,contentDisposition,mimeType);r.setTitle(name);
                r.setDescription(downloadUrl);r.setMimeType(mimeType);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalFilesDir(MainActivity.this,Environment.DIRECTORY_DOWNLOADS,name);
                long id=((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(MainActivity.this,"Descarga iniciada · #"+id,Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(MainActivity.this,"No se pudo descargar",Toast.LENGTH_SHORT).show();}
        });
        tabs.add(web); switchTab(tabs.size()-1); if(HOME.equals(url))showHome();else web.loadUrl(url);
    }
    private void switchTab(int index) {
        if(index<0||index>=tabs.size())return; downloadsOpen=false; current=index;
        webHost.removeAllViews(); WebView w=active();
        if(w.getParent()!=null)((ViewGroup)w.getParent()).removeView(w);
        webHost.addView(w,new LinearLayout.LayoutParams(-1,-1));
        address.setText(isHome(w)?"":w.getUrl()); tabCount.setText(String.valueOf(tabs.size()));
        progress.setVisibility(View.GONE); updateBookmarkIcon();
    }
    private void showHome() {
        WebView w=active();if(w==null)return;
        String c=dark?"#10141D":"#F4F6FA",p=dark?"#1B2230":"#FFFFFF",f=dark?"#F2F5FA":"#172033",
                m=dark?"#A6B0C2":"#697386",a=dark?"#9BB4FF":"#315FEA";
        JSONArray shortcuts=stored("shortcuts"); StringBuilder tiles=new StringBuilder();
        for(int i=0;i<shortcuts.length();i++){
            JSONObject o=shortcuts.optJSONObject(i);if(o==null)continue;
            String title=htmlEscape(o.optString("title")),url=htmlEscape(o.optString("url"));
            String initial=htmlEscape((o.optString("title").isEmpty()?o.optString("url"):o.optString("title")).substring(0,1).toUpperCase());
            tiles.append("<a class='tile' href='").append(url).append("'><b>").append(initial).append("</b><span>").append(title).append("</span></a>");
        }
        String html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>"+
                "*{box-sizing:border-box}body{margin:0;padding:22px 18px;font-family:Arial;background:"+c+";color:"+f+"}"+
                ".head{display:flex;align-items:center;justify-content:space-between;margin:8px 2px 18px;font-size:14px;color:"+m+"}"+
                ".add{border:0;border-radius:14px;background:"+p+";color:"+a+";font-size:25px;width:46px;height:46px}"+
                ".grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}"+
                ".tile{min-height:88px;padding:8px 3px;background:"+p+";border:1px solid "+lineHex()+";border-radius:17px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-decoration:none;color:"+f+";font-size:11px;overflow:hidden}"+
                ".tile b{width:34px;height:34px;border-radius:12px;background:"+c+";display:flex;align-items:center;justify-content:center;font-size:17px;color:"+a+";margin-bottom:7px}.tile span{max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}"+
                "</style><div class='head'><span>Accesos directos</span><button class='add' onclick=\"Jew.addShortcut()\">+</button></div><div class='grid'>"+tiles+"</div>";
        w.addJavascriptInterface(new Object(){@android.webkit.JavascriptInterface public void addShortcut(){runOnUiThread(()->addShortcutDialog());}},"Jew");
        w.loadDataWithBaseURL("https://jew.local/",html,"text/html","UTF-8",null);address.setText("");updateBookmarkIcon();
    }
    private String lineHex(){return dark?"#30394A":"#E2E7F0";}
    private String htmlEscape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("'","&#39;").replace("\"","&quot;");}
    private void addShortcutDialog() {
        EditText name=new EditText(this);name.setHint("Nombre del sitio");
        EditText url=new EditText(this);url.setHint("https://ejemplo.com");
        LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),0);box.addView(name);box.addView(url);
        new AlertDialog.Builder(this).setTitle("Añadir acceso directo").setView(box)
                .setPositiveButton("Guardar",(d,n)->{
                    String u=url.getText().toString().trim();if(!u.startsWith("http://")&&!u.startsWith("https://"))u="https://"+u;
                    if(u.length()>8){String title=name.getText().toString().trim();JSONArray a=stored("shortcuts");JSONObject o=new JSONObject();
                        try{o.put("title",title.isEmpty()?u:title);o.put("url",u);a.put(o);prefs.edit().putString("shortcuts",a.toString()).apply();showHome();}
                        catch(Exception ignored){}}
                }).setNegativeButton("Cancelar",null).show();
    }
    private void loadHome(){if(active()==null)newTab(HOME);else{if(downloadsOpen)closeDownloadsPage();showHome();}}
    private String normalize(String entry) {
        String q=entry.trim();if(q.isEmpty())return HOME;if(q.matches("(?i)^https?://.+"))return q;
        if(q.matches("(?i)^(localhost|([a-z0-9-]+\\.)+[a-z]{2,})(:\\d+)?(/.*)?$")||q.matches("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$"))return "https://"+q;
        String base=searchEngine.equals("DuckDuckGo")?"https://duckduckgo.com/?q=":
                searchEngine.equals("Personalizado")?prefs.getString("search_template","https://example.com/search?q={query}").replace("{query}",Uri.encode(q)):
                "https://www.google.com/search?q=";
        return searchEngine.equals("Personalizado")?base:base+Uri.encode(q);
    }
    private void navigate(String q) {
        if(downloadsOpen)closeDownloadsPage();String url=normalize(q);
        if(active()==null)newTab(url);else active().loadUrl(url);
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
        address.clearFocus();if(!HOME.equals(url))addHistory(url);
    }
    private JSONArray stored(String key){try{return new JSONArray(prefs.getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
    private void addHistory(String url) {
        JSONArray old=stored("history"),next=new JSONArray();next.put(url);
        for(int i=0;i<old.length()&&i<99;i++)if(!url.equals(old.optString(i)))next.put(old.optString(i));
        prefs.edit().putString("history",next.toString()).apply();
    }
    private void bookmark() {
        WebView w=active();if(w==null||w.getUrl()==null||isHome(w)){Toast.makeText(this,"No hay página para guardar",Toast.LENGTH_SHORT).show();return;}
        String url=w.getUrl();JSONArray a=stored("bookmarks");JSONArray next=new JSONArray();boolean removed=false;
        for(int i=0;i<a.length();i++){String item=a.optString(i);if(item.equals(url)){removed=true;continue;}next.put(item);}
        if(!removed)next.put(url);prefs.edit().putString("bookmarks",next.toString()).apply();updateBookmarkIcon();
        Toast.makeText(this,removed?"Quitado de favoritos":"Guardado en favoritos",Toast.LENGTH_SHORT).show();
    }
    private void showFavorites() {
        JSONArray a=stored("bookmarks");if(a.length()==0){Toast.makeText(this,"Todavía no tienes favoritos",Toast.LENGTH_SHORT).show();return;}
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();list.setPadding(dp(14),dp(8),dp(14),dp(8));scroll.addView(list);
        final AlertDialog[] holder=new AlertDialog[1];
        for(int i=0;i<a.length();i++){
            final String url=a.optString(i);LinearLayout item=row();item.setPadding(dp(10),dp(9),dp(5),dp(9));
            TextView label=text(url,13,fg);item.addView(label,new LinearLayout.LayoutParams(0,dp(44),1));
            TextView remove=text("×",25,muted);remove.setGravity(Gravity.CENTER);item.addView(remove,new LinearLayout.LayoutParams(dp(42),dp(44)));
            remove.setOnClickListener(v->{removeBookmark(url);((ViewGroup)item.getParent()).removeView(item);});
            label.setOnClickListener(v->{if(downloadsOpen)closeDownloadsPage();if(active()!=null)active().loadUrl(url);if(holder[0]!=null)holder[0].dismiss();});
            item.setBackground(shape(panel,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.bottomMargin=dp(7);list.addView(item,lp);
        }
        holder[0]=new AlertDialog.Builder(this).setTitle("Favoritos").setView(scroll).setNegativeButton("Cerrar",null).create();
        holder[0].show();
    }
    private void removeBookmark(String url) {
        JSONArray a=stored("bookmarks"),next=new JSONArray();for(int i=0;i<a.length();i++)if(!url.equals(a.optString(i)))next.put(a.optString(i));
        prefs.edit().putString("bookmarks",next.toString()).apply();updateBookmarkIcon();
    }
    private void chooseList(String key) {
        JSONArray a=stored(key);if(a.length()==0){Toast.makeText(this,"Sin elementos",Toast.LENGTH_SHORT).show();return;}
        String[] items=new String[a.length()];for(int i=0;i<items.length;i++)items[i]=a.optString(i);
        new AlertDialog.Builder(this).setTitle("Historial").setItems(items,(d,n)->{if(active()!=null)active().loadUrl(items[n]);})
                .setNeutralButton("Borrar todo",(d,n)->prefs.edit().remove(key).apply()).setNegativeButton("Cerrar",null).show();
    }
    private void showDownloads() {
        downloadsOpen=true;webHost.removeAllViews();progress.setVisibility(View.GONE);
        ScrollView scroll=new ScrollView(this);LinearLayout page=column();page.setPadding(dp(15),dp(12),dp(15),dp(16));scroll.addView(page);
        LinearLayout heading=row();TextView title=text("Descargas",21,fg);title.setTypeface(null,Typeface.BOLD);
        heading.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        IconView refresh=new IconView(this,"refresh",accent);heading.addView(refresh,new LinearLayout.LayoutParams(dp(44),dp(44)));
        refresh.setOnClickListener(v->showDownloads());page.addView(heading);
        DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        Cursor cursor=null;int count=0;
        try{
            cursor=dm.query(new DownloadManager.Query().setFilterByStatus(
                    DownloadManager.STATUS_PENDING|DownloadManager.STATUS_RUNNING|DownloadManager.STATUS_PAUSED|
                    DownloadManager.STATUS_SUCCESSFUL|DownloadManager.STATUS_FAILED));
            if(cursor!=null)while(cursor.moveToNext()){
                count++;long id=cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
                String name=cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE));
                int status=cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                long soFar=cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total=cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                String desc=downloadStatus(status,soFar,total);LinearLayout card=column();card.setPadding(dp(13),dp(11),dp(13),dp(10));card.setBackground(shape(panel,15));
                TextView n=text(name==null?"Archivo":name,14,fg);n.setTypeface(null,Typeface.BOLD);card.addView(n);
                TextView st=text(desc,12,muted);LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,dp(25));sl.topMargin=dp(4);card.addView(st,sl);
                if(status==DownloadManager.STATUS_RUNNING||status==DownloadManager.STATUS_PENDING||status==DownloadManager.STATUS_PAUSED){
                    ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);
                    bar.setProgress(total>0?(int)(soFar*100/total):0);card.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));
                }
                LinearLayout actions=row();actions.setGravity(Gravity.RIGHT);
                if(status==DownloadManager.STATUS_SUCCESSFUL){
                    addDownloadAction(actions,"Abrir",()->openDownload(dm,id));
                    addDownloadAction(actions,"Compartir",()->shareDownload(dm,id));
                }
                if(status==DownloadManager.STATUS_PENDING||status==DownloadManager.STATUS_RUNNING||status==DownloadManager.STATUS_PAUSED)
                    addDownloadAction(actions,"Cancelar",()->{dm.remove(id);showDownloads();});
                else addDownloadAction(actions,"Eliminar",()->{dm.remove(id);showDownloads();});
                card.addView(actions);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(9);page.addView(card,cp);
            }
        }catch(Exception e){Toast.makeText(this,"No se pudieron consultar las descargas",Toast.LENGTH_SHORT).show();}
        finally{if(cursor!=null)cursor.close();}
        if(count==0){TextView empty=text("Aquí aparecerán tus archivos descargados.",14,muted);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(35),0,dp(35));page.addView(empty);}
        webHost.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
    }
    private String downloadStatus(int status,long got,long total) {
        if(status==DownloadManager.STATUS_SUCCESSFUL)return "Descarga completada · "+formatBytes(got);
        if(status==DownloadManager.STATUS_FAILED)return "Error en la descarga";
        if(status==DownloadManager.STATUS_PAUSED)return "En pausa · "+formatBytes(got)+(total>0?" de "+formatBytes(total):"");
        if(status==DownloadManager.STATUS_PENDING)return "En espera";
        return "Descargando · "+formatBytes(got)+(total>0?" de "+formatBytes(total):"");
    }
    private String formatBytes(long n) {
        if(n<1024)return n+" B";if(n<1024*1024)return String.format(java.util.Locale.US,"%.1f KB",n/1024.0);
        return String.format(java.util.Locale.US,"%.1f MB",n/(1024.0*1024.0));
    }
    private void addDownloadAction(LinearLayout row,String label,Runnable run) {
        TextView b=text(label,12,accent);b.setGravity(Gravity.CENTER);b.setPadding(dp(10),dp(7),dp(10),dp(7));
        b.setOnClickListener(v->run.run());row.addView(b);
    }
    private void openDownload(DownloadManager dm,long id) {
        try{Uri uri=dm.getUriForDownloadedFile(id);if(uri==null)throw new Exception();
            String type=getContentResolver().getType(uri);Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(uri,type==null?"*/*":type);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);
        }catch(Exception e){Toast.makeText(this,"No hay una aplicación para abrir este archivo",Toast.LENGTH_SHORT).show();}
    }
    private void shareDownload(DownloadManager dm,long id) {
        try{Uri uri=dm.getUriForDownloadedFile(id);if(uri==null)throw new Exception();Intent i=new Intent(Intent.ACTION_SEND);
            i.setType(getContentResolver().getType(uri)==null?"*/*":getContentResolver().getType(uri));i.putExtra(Intent.EXTRA_STREAM,uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Compartir archivo"));
        }catch(Exception e){Toast.makeText(this,"No se pudo compartir el archivo",Toast.LENGTH_SHORT).show();}
    }
    private void closeDownloadsPage() {
        downloadsOpen=false;webHost.removeAllViews();WebView w=active();
        if(w!=null){if(w.getParent()!=null)((ViewGroup)w.getParent()).removeView(w);webHost.addView(w,new LinearLayout.LayoutParams(-1,-1));}
        if(w!=null){address.setText(isHome(w)?"":w.getUrl());updateBookmarkIcon();}
    }
    private void toggleTheme(String mode) {
        prefs.edit().putString("theme_mode",mode).putBoolean("dark",mode.equals("dark")).apply();
        updateThemeMode();applyPalette();rebuild();
    }
    private void rebuild() {
        WebView keep=active();String currentUrl=keep==null?HOME:(isHome(keep)?HOME:keep.getUrl());
        downloadsOpen=false;buildInterface();
        if(keep!=null){switchTab(current);if(HOME.equals(currentUrl))showHome();else address.setText(currentUrl);}
    }
    private void settings() {
        String[] opts={"Apariencia","Buscador","Cerrar"};
        new AlertDialog.Builder(this).setTitle("Configuración").setItems(opts,(d,n)->{
            if(n==0)themeSettings();else if(n==1)searchSettings();
        }).show();
    }
    private void themeSettings() {
        String[] modes={"Según el sistema","Claro","Oscuro"};
        String mode=prefs.getString("theme_mode",prefs.getBoolean("dark",false)?"dark":"light");
        int selected=mode.equals("system")?0:mode.equals("light")?1:2;
        new AlertDialog.Builder(this).setTitle("Tema de Jew").setSingleChoiceItems(modes,selected,(d,n)->{
            toggleTheme(n==0?"system":n==1?"light":"dark");d.dismiss();
        }).setNegativeButton("Cancelar",null).show();
    }
    private void searchSettings() {
        String[] engines={"Google","DuckDuckGo","Personalizado"};
        String saved=prefs.getString("search_engine","Google");int checked=saved.equals("DuckDuckGo")?1:saved.equals("Personalizado")?2:0;
        new AlertDialog.Builder(this).setTitle("Buscador").setSingleChoiceItems(engines,checked,(d,n)->{
            if(n<2){searchEngine=engines[n];prefs.edit().putString("search_engine",searchEngine).remove("search_template").apply();d.dismiss();}
            else{
                EditText input=new EditText(this);input.setSingleLine(true);
                input.setText(prefs.getString("search_template","https://example.com/search?q={query}"));
                input.setHint("https://sitio.com/search?q={query}");
                new AlertDialog.Builder(this).setTitle("URL de búsqueda").setMessage("Usa {query} donde debe insertarse el texto")
                        .setView(input).setPositiveButton("Guardar",(x,y)->{
                            String t=input.getText().toString().trim();if(t.contains("{query}")){
                                searchEngine="Personalizado";prefs.edit().putString("search_engine",searchEngine).putString("search_template",t).apply();
                            }else Toast.makeText(this,"La URL debe contener {query}",Toast.LENGTH_LONG).show();
                        }).setNegativeButton("Cancelar",null).show();d.dismiss();
            }
        }).setNegativeButton("Cerrar",null).show();
    }
    private void tabMenu() {
        String[] items=new String[tabs.size()+2];
        for(int i=0;i<tabs.size();i++){String title=tabs.get(i).getTitle();items[i]=(i==current?"✓ ":"")+(title==null||title.isEmpty()?"Nueva pestaña":title);}
        items[tabs.size()]="＋ Nueva pestaña";items[tabs.size()+1]="Cerrar pestaña actual";
        new AlertDialog.Builder(this).setTitle("Pestañas abiertas").setItems(items,(d,n)->{
            if(n<tabs.size())switchTab(n);else if(n==tabs.size())newTab(HOME);else closeTab();
        }).setNegativeButton("Cerrar",null).show();
    }
    private void menu(View anchor) {
        PopupMenu m=new PopupMenu(this,anchor);
        String[] opts={"Nueva pestaña","Favoritos","Descargas","Historial","Recargar página","Configuración"};
        for(String op:opts)m.getMenu().add(op);
        m.setOnMenuItemClickListener(it->{String op=it.getTitle().toString();WebView w=active();
            switch(op){
                case "Nueva pestaña":newTab(HOME);break;
                case "Favoritos":showFavorites();break;
                case "Descargas":showDownloads();break;
                case "Historial":chooseList("history");break;
                case "Recargar página":if(w!=null){if(isHome(w))showHome();else w.reload();}break;
                case "Configuración":settings();break;
            }return true;
        });m.show();
    }
    private void closeTab() {
        if(active()==null)return;downloadsOpen=false;WebView w=active();webHost.removeView(w);tabs.remove(current);w.destroy();
        if(tabs.isEmpty()){current=-1;newTab(HOME);}else switchTab(Math.min(current,tabs.size()-1));
    }
    private void goBack() {
        if(downloadsOpen){closeDownloadsPage();return;}
        WebView w=active();if(w!=null&&w.canGoBack())w.goBack();
        else if(tabs.size()>1)closeTab();
        else new AlertDialog.Builder(this).setMessage("¿Salir de Jew?").setPositiveButton("Salir",(d,n)->finish())
                .setNegativeButton("Cancelar",null).show();
    }
    @Override public void onBackPressed(){goBack();}
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==FILE_REQUEST&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}
    }
    @Override protected void onPause(){super.onPause();if(active()!=null)active().onPause();}
    @Override protected void onResume(){super.onResume();if(active()!=null)active().onResume();}
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if("system".equals(prefs.getString("theme_mode","light"))){updateThemeMode();applyPalette();rebuild();}
    }
    @Override protected void onDestroy(){for(WebView w:tabs){w.stopLoading();w.destroy();}tabs.clear();super.onDestroy();}

    private class IconView extends View {
        private final String name; private int tint; private boolean filled=false;
        private final Paint p=new Paint(3);
        IconView(Context c,String n,int color){super(c);name=n;tint=color;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setTint(int color){tint=color;invalidate();}
        void setFilled(boolean value){filled=value;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float scale=Math.min(getWidth(),getHeight())/32f;
            canvas.save();canvas.translate((getWidth()-32*scale)/2f,(getHeight()-32*scale)/2f);canvas.scale(scale,scale);
            p.setColor(tint);p.setStrokeWidth(2.2f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            p.setStyle(Paint.Style.STROKE);Path path=new Path();
            switch(name){
                case "back":path.moveTo(19,7);path.lineTo(9,16);path.lineTo(19,25);canvas.drawPath(path,p);canvas.drawLine(9,16,25,16,p);break;
                case "forward":path.moveTo(13,7);path.lineTo(23,16);path.lineTo(13,25);canvas.drawPath(path,p);canvas.drawLine(7,16,23,16,p);break;
                case "refresh":canvas.drawArc(6,6,26,26,45,285,false,p);path.moveTo(22,5);path.lineTo(26,6);path.lineTo(25,11);canvas.drawPath(path,p);break;
                case "search":canvas.drawCircle(14,14,7,p);canvas.drawLine(19,19,26,26,p);break;
                case "home":path.moveTo(5,15);path.lineTo(16,5);path.lineTo(27,15);path.moveTo(8,13);path.lineTo(8,26);path.lineTo(24,26);path.lineTo(24,13);canvas.drawPath(path,p);canvas.drawRect(14,19,19,26,p);break;
                case "star":
                    path.moveTo(16,4);path.lineTo(19.5f,12);path.lineTo(28,13);path.lineTo(21.5f,19);path.lineTo(23.5f,28);path.lineTo(16,23);path.lineTo(8.5f,28);path.lineTo(10.5f,19);path.lineTo(4,13);path.lineTo(12.5f,12);path.close();
                    if(filled){p.setStyle(Paint.Style.FILL);canvas.drawPath(path,p);}else canvas.drawPath(path,p);break;
                case "tabs":canvas.drawRoundRect(5,7,22,25,3,3,p);canvas.drawRoundRect(10,4,27,22,3,3,p);break;
                case "more":p.setStyle(Paint.Style.FILL);canvas.drawCircle(16,7,2,p);canvas.drawCircle(16,16,2,p);canvas.drawCircle(16,25,2,p);break;
                case "download":path.moveTo(16,4);path.lineTo(16,21);path.moveTo(9,15);path.lineTo(16,22);path.lineTo(23,15);canvas.drawPath(path,p);canvas.drawLine(6,27,26,27,p);break;
                case "close":canvas.drawLine(8,8,24,24,p);canvas.drawLine(24,8,8,24,p);break;
            }
            canvas.restore();
        }
    }
}
