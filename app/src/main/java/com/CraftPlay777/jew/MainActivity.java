package com.CraftPlay777.jew;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Color;
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
import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String HOME = "https://www.google.com/";
    private final ArrayList<WebView> tabs = new ArrayList<>();
    private int current = -1;
    private LinearLayout root, webHost;
    private EditText address;
    private ProgressBar progress;
    private Button tabsButton;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_REQUEST = 901;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(17,24,39));
        prefs = getSharedPreferences("jew", MODE_PRIVATE);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        makeToolbar();
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        webHost = new LinearLayout(this);
        root.addView(webHost, new LinearLayout.LayoutParams(-1,0,1));
        newTab(HOME);
    }

    private int dp(int n) { return (int) (getResources().getDisplayMetrics().density*n + 0.5f); }
    private Button button(String s, LinearLayout parent, View.OnClickListener cb) {
        Button b = new Button(this);
        b.setAllCaps(false); b.setText(s); b.setTextSize(15);
        b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(4),0,dp(4),0);
        parent.addView(b,new LinearLayout.LayoutParams(-2,dp(48)));
        b.setOnClickListener(cb); return b;
    }
    private void makeToolbar() {
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(52)));
        button("‹",top,v->back());
        button("›",top,v->{ if (active()!=null && active().canGoForward()) active().goForward(); });
        address = new EditText(this);
        address.setSingleLine(true); address.setTextSize(14);
        address.setHint("Buscar o escribir URL");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        top.addView(address,new LinearLayout.LayoutParams(0,dp(48),1));
        address.setOnEditorActionListener((v,action,event)-> {
            if(action==EditorInfo.IME_ACTION_GO || (event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER)) {
                navigate(address.getText().toString()); return true;
            } return false;
        });
        button("↻",top,v->{if(active()!=null)active().reload();});
        tabsButton = button("1",top,v->tabMenu());
        button("⋮",top,this::menu);
    }
    private WebView active() { return current>=0 && current<tabs.size()?tabs.get(current):null; }
    private void newTab(String url) {
        WebView web = new WebView(this);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest request) {
                Uri uri = request.getUrl(); String scheme=uri.getScheme();
                if("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); }
                catch(Exception ex){ Toast.makeText(MainActivity.this,"No se puede abrir el enlace",Toast.LENGTH_SHORT).show(); }
                return true;
            }
            @Override public void onPageFinished(WebView w,String url) { if(w==active())address.setText(url); }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView w,int p){
                if(w==active()){progress.setProgress(p);progress.setVisibility(p>=100?View.GONE:View.VISIBLE);}
            }
            @Override public boolean onShowFileChooser(WebView w,ValueCallback<Uri[]> cb,FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);
                fileCallback=cb;
                try {startActivityForResult(params.createIntent(),FILE_REQUEST);return true;}
                catch(Exception ex){ fileCallback=null;cb.onReceiveValue(null);return false; }
            }
        });
        web.setDownloadListener((downloadUrl,userAgent,contentDisposition,mimeType,contentLength)->{
            if(!url.startsWith("http://") && !url.startsWith("https://"))return;
            try {
                DownloadManager.Request r=new DownloadManager.Request(Uri.parse(downloadUrl));
                r.addRequestHeader("User-Agent", userAgent == null ? "" : userAgent);
                String cookie=CookieManager.getInstance().getCookie(url);
                if(cookie!=null)r.addRequestHeader("Cookie",cookie);
                r.setTitle(URLUtil.guessFileName(url,contentDisposition,mimeType));
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalFilesDir(MainActivity.this,Environment.DIRECTORY_DOWNLOADS,URLUtil.guessFileName(url,contentDisposition,mimeType));
                ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                Toast.makeText(MainActivity.this,"Descarga iniciada (carpeta de la app)",Toast.LENGTH_LONG).show();
            } catch(Exception e) {Toast.makeText(MainActivity.this,"No se pudo descargar",Toast.LENGTH_SHORT).show();}
        });
        tabs.add(web); switchTab(tabs.size()-1); web.loadUrl(url);
    }
    private void switchTab(int index){
        if(index<0||index>=tabs.size())return;
        current=index; webHost.removeAllViews();
        WebView web=active();
        if(web.getParent()!=null)((ViewGroup)web.getParent()).removeView(web);
        webHost.addView(web,new LinearLayout.LayoutParams(-1,-1));
        address.setText(web.getUrl()==null?"":web.getUrl());
        tabsButton.setText(String.valueOf(tabs.size()));
        progress.setVisibility(View.GONE);
    }
    private void closeTab(){
        if(active()==null)return;
        WebView web=active();webHost.removeView(web);tabs.remove(current);web.destroy();
        if(tabs.isEmpty()){current=-1;newTab(HOME);}else switchTab(Math.min(current,tabs.size()-1));
    }
    private void tabMenu(){
        PopupMenu menu=new PopupMenu(this,tabsButton);
        for(int i=0;i<tabs.size();i++){
            final int index=i; String title=tabs.get(i).getTitle();
            menu.getMenu().add((i==current?"● ":"")+(i+1)+": "+(title==null?"Nueva pestaña":title)).setOnMenuItemClickListener(item->{switchTab(index);return true;});
        }
        menu.getMenu().add("+ Nueva pestaña").setOnMenuItemClickListener(item->{newTab(HOME);return true;});
        menu.getMenu().add("Cerrar pestaña actual").setOnMenuItemClickListener(item->{closeTab();return true;});
        menu.show();
    }
    private String normalize(String entry){
        String q=entry.trim(); if(q.isEmpty())return HOME;
        if(q.matches("(?i)^https?://.+"))return q;
        if(q.matches("(?i)^(localhost|([a-z0-9-]+\\.)+[a-z]{2,})(:\\d+)?(/.*)?$") || q.matches("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$"))return "https://"+q;
        return "https://www.google.com/search?q="+Uri.encode(q);
    }
    private void navigate(String q){
        String url=normalize(q);if(active()!=null)active().loadUrl(url);
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
        address.clearFocus();
        addHistory(url);
    }
    private JSONArray stored(String key){try{return new JSONArray(prefs.getString(key,"[]"));}catch(Exception ex){return new JSONArray();}}
    private void addHistory(String url){
        JSONArray old=stored("history"), next=new JSONArray();next.put(url);
        for(int i=0;i<old.length()&&i<99;i++)if(!url.equals(old.optString(i)))next.put(old.optString(i));
        prefs.edit().putString("history",next.toString()).apply();
    }
    private void chooseList(String key){
        JSONArray arr=stored(key);
        if(arr.length()==0){Toast.makeText(this,"Sin elementos",Toast.LENGTH_SHORT).show();return;}
        String[] items=new String[arr.length()];for(int i=0;i<items.length;i++)items[i]=arr.optString(i);
        new android.app.AlertDialog.Builder(this).setTitle(key.equals("history")?"Historial":"Marcadores")
          .setItems(items,(d,index)->{if(active()!=null)active().loadUrl(items[index]);})
          .setNegativeButton("Cerrar",null)
          .setNeutralButton("Borrar todo",(d,index)->prefs.edit().remove(key).apply()).show();
    }
    private void bookmark(){
        if(active()==null||active().getUrl()==null)return;
        JSONArray arr=stored("bookmarks");String url=active().getUrl();
        for(int i=0;i<arr.length();i++)if(url.equals(arr.optString(i))){Toast.makeText(this,"Ya está guardado",Toast.LENGTH_SHORT).show();return;}
        arr.put(url);prefs.edit().putString("bookmarks",arr.toString()).apply();
        Toast.makeText(this,"Marcador guardado",Toast.LENGTH_SHORT).show();
    }
    private void menu(View anchor){
        PopupMenu m=new PopupMenu(this,anchor);
        String[] opts={"Inicio","Nueva pestaña","Cerrar pestaña","Guardar marcador","Ver marcadores","Ver historial","Compartir URL","Abrir en otra app"};
        for(String op:opts)m.getMenu().add(op);
        m.setOnMenuItemClickListener(item->{
            String op=item.getTitle().toString();WebView w=active();
            switch(op){
                case "Inicio":if(w!=null)w.loadUrl(HOME);break;
                case "Nueva pestaña":newTab(HOME);break;
                case "Cerrar pestaña":closeTab();break;
                case "Guardar marcador":bookmark();break;
                case "Ver marcadores":chooseList("bookmarks");break;
                case "Ver historial":chooseList("history");break;
                case "Compartir URL":if(w!=null&&w.getUrl()!=null){Intent in=new Intent(Intent.ACTION_SEND);in.setType("text/plain");in.putExtra(Intent.EXTRA_TEXT,w.getUrl());startActivity(Intent.createChooser(in,"Compartir enlace"));}break;
                case "Abrir en otra app":if(w!=null&&w.getUrl()!=null)try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(w.getUrl())));}catch(Exception ex){}break;
            }return true;
        });m.show();
    }
    private void back(){if(active()!=null&&active().canGoBack())active().goBack();else if(tabs.size()>1)closeTab();else new android.app.AlertDialog.Builder(this).setMessage("¿Salir de Jew?").setPositiveButton("Salir",(d,w)->finish()).setNegativeButton("Cancelar",null).show();}
    @Override public void onBackPressed(){back();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==FILE_REQUEST && fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}
    }
    @Override protected void onPause(){super.onPause();if(active()!=null)active().onPause();}
    @Override protected void onResume(){super.onResume();if(active()!=null)active().onResume();}
    @Override protected void onDestroy(){for(WebView w:tabs){w.stopLoading();w.destroy();}tabs.clear();super.onDestroy();}
}
