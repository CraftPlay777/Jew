package com.CraftPlay777.jew;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import org.json.JSONArray;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String HOME = "jew://home";
    private final ArrayList<WebView> tabs = new ArrayList<>();
    private int current = -1;
    private LinearLayout root, top, webHost, bottom, addressBox;
    private EditText address;
    private ProgressBar progress;
    private TextView tabCount, themeButton;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_REQUEST = 901;
    private SharedPreferences prefs;
    private boolean dark;
    private String searchEngine;
    private int bg, panel, fg, muted, accent, line;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("jew", MODE_PRIVATE);
        dark = prefs.getBoolean("dark", false);
        searchEngine = prefs.getString("search_engine", "Google");
        applyPalette();
        buildInterface();
        newTab(HOME);
    }

    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density*n+0.5f); }
    private void applyPalette() {
        bg = Color.parseColor(dark ? "#10141D" : "#F4F6FA");
        panel = Color.parseColor(dark ? "#1B2230" : "#FFFFFF");
        fg = Color.parseColor(dark ? "#F2F5FA" : "#172033");
        muted = Color.parseColor(dark ? "#A6B0C2" : "#697386");
        accent = Color.parseColor(dark ? "#8AA8FF" : "#315FEA");
        line = Color.parseColor(dark ? "#30394A" : "#E2E7F0");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value,int size,int color) { TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);t.setIncludeFontPadding(false);return t; }
    private TextView action(String label,Runnable run) {
        TextView t=text(label,20,fg); t.setTypeface(null,Typeface.BOLD); t.setBackground(ripple(panel));
        t.setOnClickListener(v->run.run()); return t;
    }
    private android.graphics.drawable.Drawable ripple(int color) {
        android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(16));
        return shape;
    }
    private void buildInterface() {
        root=column();root.setBackgroundColor(bg);setContentView(root);
        top=row();top.setPadding(dp(10),dp(7),dp(10),dp(5));root.addView(top,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView back=action("‹",()->goBack());top.addView(back,new LinearLayout.LayoutParams(dp(38),dp(40)));
        TextView forward=action("›",()->{WebView w=active();if(w!=null&&w.canGoForward())w.goForward();});top.addView(forward,new LinearLayout.LayoutParams(dp(38),dp(40)));
        addressBox=row();addressBox.setPadding(dp(12),0,dp(8),0);addressBox.setBackground(ripple(panel));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(42),1);ap.setMargins(dp(7),0,dp(7),0);top.addView(addressBox,ap);
        TextView searchIcon=text("⌕",22,muted);addressBox.addView(searchIcon,new LinearLayout.LayoutParams(dp(25),-1));
        address=new EditText(this);address.setSingleLine(true);address.setTextSize(14);address.setTextColor(fg);address.setHintTextColor(muted);address.setHint("Search/URL");address.setBackgroundColor(Color.TRANSPARENT);address.setPadding(dp(4),0,dp(4),0);address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);address.setImeOptions(EditorInfo.IME_ACTION_GO);addressBox.addView(address,new LinearLayout.LayoutParams(0,-1,1));
        address.setOnEditorActionListener((v,a,event)->{if(a==EditorInfo.IME_ACTION_GO||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){navigate(address.getText().toString());return true;}return false;});
        TextView reload=action("↻",()->{WebView w=active();if(w!=null){if(isHome(w))showHome();else w.reload();}});top.addView(reload,new LinearLayout.LayoutParams(dp(40),dp(40)));
        tabCount=action("1",()->tabMenu());LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(dp(40),dp(40));tp.setMargins(dp(4),0,0,0);top.addView(tabCount,tp);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));
        webHost=column();root.addView(webHost,new LinearLayout.LayoutParams(-1,0,1));
        bottom=row();bottom.setPadding(dp(12),dp(5),dp(12),dp(7));bottom.setBackgroundColor(panel);root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(58)));
        bottomAction("⌂","Hogar",()->loadHome());bottomAction("☆","Guardar",()->bookmark());bottomAction("⌕","Buscar",()->focusSearch());bottomAction("▢","Pestañas",()->tabMenu());bottomAction("⋮","Más",()->menu(bottom.getChildAt(4)));
    }
    private void focusSearch(){address.requestFocus();address.setSelection(0,address.length());((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(address,InputMethodManager.SHOW_IMPLICIT);}
    private void bottomAction(String symbol,String label,Runnable run){LinearLayout item=column();item.setGravity(Gravity.CENTER);TextView icon=text(symbol,21,fg);TextView caption=text(label,10,muted);item.addView(icon);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(13));cp.topMargin=dp(2);item.addView(caption,cp);item.setOnClickListener(v->run.run());bottom.addView(item,new LinearLayout.LayoutParams(0,-1,1));}
    private WebView active(){return current>=0&&current<tabs.size()?tabs.get(current):null;}
    private boolean isHome(WebView w){return w!=null&&(HOME.equals(w.getUrl())||(w.getUrl()!=null&&w.getUrl().startsWith("https://jew.local/")));}
    private void newTab(String url){
        WebView web=new WebView(this);WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setSupportZoom(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);s.setLoadWithOverviewMode(true);s.setUseWideViewPort(true);s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setBackgroundColor(bg);if(android.os.Build.VERSION.SDK_INT>=29){try{s.setForceDark(dark?WebSettings.FORCE_DARK_ON:WebSettings.FORCE_DARK_OFF);}catch(Exception ignored){}}
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest request){Uri u=request.getUrl();String scheme=u.getScheme();if("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){Toast.makeText(MainActivity.this,"No se puede abrir el enlace",Toast.LENGTH_SHORT).show();}return true;}
            @Override public void onPageFinished(WebView w,String url){if(w==active()){address.setText(isHome(w)?"":url);}}
        });
        web.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView w,int p){if(w==active()){progress.setProgress(p);progress.setVisibility(p>=100?View.GONE:View.VISIBLE);}}@Override public boolean onShowFileChooser(WebView w,ValueCallback<Uri[]> cb,FileChooserParams params){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=cb;try{startActivityForResult(params.createIntent(),FILE_REQUEST);return true;}catch(Exception e){fileCallback=null;cb.onReceiveValue(null);return false;}}});
        web.setDownloadListener((downloadUrl,userAgent,contentDisposition,mimeType,contentLength)->{if(!downloadUrl.startsWith("http://")&&!downloadUrl.startsWith("https://"))return;try{DownloadManager.Request r=new DownloadManager.Request(Uri.parse(downloadUrl));r.addRequestHeader("User-Agent",userAgent==null?"":userAgent);String cookie=CookieManager.getInstance().getCookie(downloadUrl);if(cookie!=null)r.addRequestHeader("Cookie",cookie);String name=URLUtil.guessFileName(downloadUrl,contentDisposition,mimeType);r.setTitle(name);r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);r.setDestinationInExternalFilesDir(MainActivity.this,Environment.DIRECTORY_DOWNLOADS,name);((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(r);Toast.makeText(MainActivity.this,"Descarga iniciada",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(MainActivity.this,"No se pudo descargar",Toast.LENGTH_SHORT).show();}});
        tabs.add(web);switchTab(tabs.size()-1);if(HOME.equals(url))showHome();else web.loadUrl(url);
    }
    private void switchTab(int index){if(index<0||index>=tabs.size())return;current=index;webHost.removeAllViews();WebView w=active();if(w.getParent()!=null)((ViewGroup)w.getParent()).removeView(w);webHost.addView(w,new LinearLayout.LayoutParams(-1,-1));address.setText(isHome(w)?"":w.getUrl());tabCount.setText(String.valueOf(tabs.size()));progress.setVisibility(View.GONE);}
    private void showHome(){WebView w=active();if(w==null)return;String c=dark?"#10141D":"#F4F6FA",p=dark?"#1B2230":"#FFFFFF",f=dark?"#F2F5FA":"#172033",m=dark?"#A6B0C2":"#697386",a=dark?"#8AA8FF":"#315FEA";String html="<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>*{box-sizing:border-box}body{margin:0;padding:22px 18px;font-family:Arial;background:"+c+";color:"+f+"}.head{display:flex;align-items:center;justify-content:space-between;margin:8px 2px 18px;font-size:14px;color:"+m+"}.add{border:0;border-radius:12px;background:"+p+";color:"+a+";font-size:24px;width:44px;height:44px}.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.tile{min-height:86px;background:"+p+";border:1px solid "+(dark?"#30394A":"#E2E7F0")+";border-radius:17px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-decoration:none;color:"+f+";font-size:12px;overflow:hidden}.tile b{font-size:22px;color:"+a+";margin-bottom:8px}.empty{color:"+m+";font-size:13px;line-height:1.5;margin:12px 2px}</style><div class='head'><span>Accesos directos</span><button class='add' onclick=\"Jew.addShortcut()\">+</button></div><div class='grid' id='grid'></div><script>function render(){let a=JSON.parse(localStorage.getItem('jew.shortcuts')||'[]');document.getElementById('grid').innerHTML=a.map((x,i)=>`<a class=tile href='${x.url}'><b>${(x.title||x.url).substring(0,1).toUpperCase()}</b>${x.title||x.url}</a>`).join('');}render();</script>";w.addJavascriptInterface(new Object(){@android.webkit.JavascriptInterface public void addShortcut(){runOnUiThread(()->addShortcutDialog());}},"Jew");w.loadDataWithBaseURL("https://jew.local/",html,"text/html","UTF-8",null);address.setText("");}
    private void addShortcutDialog(){EditText name=new EditText(this);name.setHint("Nombre del sitio");EditText url=new EditText(this);url.setHint("https://ejemplo.com");LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),0);box.addView(name);box.addView(url);new AlertDialog.Builder(this).setTitle("Añadir acceso directo").setView(box).setPositiveButton("Guardar",(d,n)->{String u=url.getText().toString().trim();if(!u.startsWith("http://")&&!u.startsWith("https://"))u="https://"+u;if(u.length()>8){String title=name.getText().toString().trim();JSONArray a=stored("shortcuts");org.json.JSONObject o=new org.json.JSONObject();try{o.put("title",title.isEmpty()?u:title);o.put("url",u);a.put(o);prefs.edit().putString("shortcuts",a.toString()).apply();showHome();}catch(Exception ignored){}}}).setNegativeButton("Cancelar",null).show();}
    private void loadHome(){if(active()==null){newTab(HOME);}else showHome();}
    private String normalize(String entry){String q=entry.trim();if(q.isEmpty())return HOME;if(q.matches("(?i)^https?://.+"))return q;if(q.matches("(?i)^(localhost|([a-z0-9-]+\\.)+[a-z]{2,})(:\\d+)?(/.*)?$")||q.matches("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d+)?(/.*)?$"))return "https://"+q;String base=searchEngine.equals("DuckDuckGo")?"https://duckduckgo.com/?q=":searchEngine.equals("Personalizado")?prefs.getString("search_template","https://example.com/search?q={query}").replace("{query}",Uri.encode(q)):"https://www.google.com/search?q=";return searchEngine.equals("Personalizado")?base:base+Uri.encode(q);}
    private void navigate(String q){String url=normalize(q);if(active()==null)newTab(url);else active().loadUrl(url);((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);address.clearFocus();if(!HOME.equals(url))addHistory(url);}
    private JSONArray stored(String key){try{return new JSONArray(prefs.getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
    private void addHistory(String url){JSONArray old=stored("history"),next=new JSONArray();next.put(url);for(int i=0;i<old.length()&&i<99;i++)if(!url.equals(old.optString(i)))next.put(old.optString(i));prefs.edit().putString("history",next.toString()).apply();}
    private void bookmark(){WebView w=active();if(w==null||w.getUrl()==null||isHome(w)){Toast.makeText(this,"No hay página para guardar",Toast.LENGTH_SHORT).show();return;}JSONArray a=stored("bookmarks");for(int i=0;i<a.length();i++)if(w.getUrl().equals(a.optString(i))){Toast.makeText(this,"Ya está guardado",Toast.LENGTH_SHORT).show();return;}a.put(w.getUrl());prefs.edit().putString("bookmarks",a.toString()).apply();if(bottom!=null&&bottom.getChildCount()>1){TextView star=(TextView)((LinearLayout)bottom.getChildAt(1)).getChildAt(0);star.setText("★");star.setTextColor(Color.YELLOW);}Toast.makeText(this,"Marcador guardado",Toast.LENGTH_SHORT).show();}
    private void chooseList(String key){JSONArray a=stored(key);if(a.length()==0){Toast.makeText(this,"Sin elementos",Toast.LENGTH_SHORT).show();return;}String[] items=new String[a.length()];for(int i=0;i<items.length;i++)items[i]=a.optString(i);new AlertDialog.Builder(this).setTitle(key.equals("history")?"Historial":"Marcadores").setItems(items,(d,n)->{if(active()!=null)active().loadUrl(items[n]);}).setNeutralButton("Borrar todo",(d,n)->prefs.edit().remove(key).apply()).setNegativeButton("Cerrar",null).show();}
    private void showDownloads(){try{Intent i=new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);startActivity(i);}catch(Exception e){Toast.makeText(this,"Abre la carpeta Descargas del dispositivo",Toast.LENGTH_LONG).show();}}
    private void toggleTheme(){dark=!dark;prefs.edit().putBoolean("dark",dark).apply();applyPalette();rebuild();}
    private void rebuild(){WebView keep=active();String currentUrl=keep==null?HOME:(isHome(keep)?HOME:keep.getUrl());buildInterface();if(keep!=null){switchTab(current);if(HOME.equals(currentUrl))showHome();else address.setText(currentUrl);} }
    private void settings(){String[] engines={"Google","DuckDuckGo","Personalizado"};String saved=prefs.getString("search_engine","Google");int checked=saved.equals("DuckDuckGo")?1:saved.equals("Personalizado")?2:0;new AlertDialog.Builder(this).setTitle("Configurar buscador").setSingleChoiceItems(engines,checked,(d,n)->{if(n<2){searchEngine=engines[n];prefs.edit().putString("search_engine",searchEngine).remove("search_template").apply();d.dismiss();}else{EditText input=new EditText(this);input.setSingleLine(true);input.setText(prefs.getString("search_template","https://example.com/search?q={query}"));input.setHint("https://sitio.com/search?q={query}");new AlertDialog.Builder(this).setTitle("URL de búsqueda").setMessage("Usa {query} donde debe insertarse el texto").setView(input).setPositiveButton("Guardar",(x,y)->{String t=input.getText().toString().trim();if(t.contains("{query}")){searchEngine="Personalizado";prefs.edit().putString("search_engine",searchEngine).putString("search_template",t).apply();}else Toast.makeText(this,"La URL debe contener {query}",Toast.LENGTH_LONG).show();}).setNegativeButton("Cancelar",null).show();d.dismiss();}}).setNegativeButton("Cerrar",null).show();}
    private void tabMenu(){String[] items=new String[tabs.size()+2];for(int i=0;i<tabs.size();i++){String title=tabs.get(i).getTitle();items[i]=(i==current?"✓ ":"")+(title==null||title.isEmpty()?"Nueva pestaña":title);}items[tabs.size()]="+ Nueva pestaña";items[tabs.size()+1]="Cerrar pestaña actual";new AlertDialog.Builder(this).setTitle("Pestañas abiertas").setItems(items,(d,n)->{if(n<tabs.size())switchTab(n);else if(n==tabs.size())newTab(HOME);else closeTab();}).setNegativeButton("Cerrar",null).show();}
    private void tabMenu(){PopupMenu m=new PopupMenu(this,tabCount);for(int i=0;i<tabs.size();i++){final int n=i;String title=tabs.get(i).getTitle();m.getMenu().add((i==current?"● ":"")+(i+1)+" · "+(title==null||title.isEmpty()?"Nueva pestaña":title)).setOnMenuItemClickListener(it->{switchTab(n);return true;});}m.getMenu().add("+ Nueva pestaña").setOnMenuItemClickListener(it->{newTab(HOME);return true;});m.getMenu().add("Cerrar pestaña actual").setOnMenuItemClickListener(it->{closeTab();return true;});m.show();}
    private void closeTab(){if(active()==null)return;WebView w=active();webHost.removeView(w);tabs.remove(current);w.destroy();if(tabs.isEmpty()){current=-1;newTab(HOME);}else switchTab(Math.min(current,tabs.size()-1));}
    private void menu(View anchor){PopupMenu m=new PopupMenu(this,anchor);String[] opts={"Nueva pestaña","Favoritos","Descargas","Recargar página","Configuración"};for(String op:opts)m.getMenu().add(op);m.setOnMenuItemClickListener(it->{String op=it.getTitle().toString();WebView w=active();switch(op){case "Nueva pestaña":newTab(HOME);break;case "Favoritos":chooseList("bookmarks");break;case "Descargas":showDownloads();break;case "Recargar página":if(w!=null){if(isHome(w))showHome();else w.reload();}break;case "Configuración":settings();break;}return true;});m.show();}
    private void goBack(){WebView w=active();if(w!=null&&w.canGoBack())w.goBack();else if(tabs.size()>1)closeTab();else new AlertDialog.Builder(this).setMessage("¿Salir de Jew?").setPositiveButton("Salir",(d,n)->finish()).setNegativeButton("Cancelar",null).show();}
    @Override public void onBackPressed(){goBack();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==FILE_REQUEST&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}}
    @Override protected void onPause(){super.onPause();if(active()!=null)active().onPause();}
    @Override protected void onResume(){super.onResume();if(active()!=null)active().onResume();}
    @Override protected void onDestroy(){for(WebView w:tabs){w.stopLoading();w.destroy();}tabs.clear();super.onDestroy();}
}
