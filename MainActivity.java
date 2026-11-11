package com.tvlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 7;
    private WebView web;
    private ValueCallback<Uri[]> chooser;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String u) {
                return handle(u);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return handle(r.getUrl().toString());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                try {
                    startActivityForResult(i, REQ_FILE);
                } catch (Exception e) {
                    chooser = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
    }

    private boolean handle(String u) {
        if (u != null && u.startsWith("intent:")) {
            try {
                startActivity(Intent.parseUri(u, Intent.URI_INTENT_SCHEME));
            } catch (Exception e) {
                toast("Não consegui abrir o app");
            }
            return true;
        }
        return false;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int rc, int res, Intent d) {
        if (rc == REQ_FILE && chooser != null) {
            Uri[] r = null;
            if (res == RESULT_OK && d != null && d.getData() != null) {
                r = new Uri[]{d.getData()};
            }
            chooser.onReceiveValue(r);
            chooser = null;
        } else {
            super.onActivityResult(rc, res, d);
        }
    }

    // Launcher: o botão Voltar não fecha nada, só avisa a página.
    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.onBack&&window.onBack()", null);
    }

    public class Bridge {

        @JavascriptInterface
        public String getApps() {
            try {
                PackageManager pm = getPackageManager();
                Intent m = new Intent(Intent.ACTION_MAIN);
                m.addCategory(Intent.CATEGORY_LAUNCHER);
                Intent t = new Intent(Intent.ACTION_MAIN);
                t.addCategory("android.intent.category.LEANBACK_LAUNCHER");
                List<ResolveInfo> all = new ArrayList<>(pm.queryIntentActivities(m, 0));
                all.addAll(pm.queryIntentActivities(t, 0));

                HashMap<String, String> seen = new HashMap<>();
                for (ResolveInfo r : all) {
                    String p = r.activityInfo.packageName;
                    if (p.equals(getPackageName()) || seen.containsKey(p)) continue;
                    seen.put(p, String.valueOf(r.loadLabel(pm)));
                }
                List<Map.Entry<String, String>> es = new ArrayList<>(seen.entrySet());
                Collections.sort(es, (a, c) -> a.getValue().compareToIgnoreCase(c.getValue()));

                JSONArray arr = new JSONArray();
                for (Map.Entry<String, String> e : es) {
                    JSONObject o = new JSONObject();
                    o.put("name", e.getValue());
                    o.put("pkg", e.getKey());
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String getIcon(String pkg) {
            try {
                Drawable d = getPackageManager().getApplicationIcon(pkg);
                int sz = 72;
                Bitmap bm = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bm);
                d.setBounds(0, 0, sz, sz);
                d.draw(c);
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.PNG, 100, o);
                return "data:image/png;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void launch(final String pkg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        PackageManager pm = getPackageManager();
                        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
                        if (i != null) {
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                        } else {
                            toast("App não encontrado: " + pkg);
                        }
                    } catch (Exception e) {
                        toast("Não consegui abrir: " + pkg);
                    }
                }
            });
        }

        @JavascriptInterface
        public void uninstall(final String pkg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
                        startActivity(i);
                    } catch (Exception e) {
                        toast("Não consegui desinstalar");
                    }
                }
            });
        }

        @JavascriptInterface
        public void openUrl(final String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception e) {
                        toast("Nenhum navegador encontrado");
                    }
                }
            });
        }

        @JavascriptInterface
        public void openSettings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_SETTINGS);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception e) {
                        toast("Não consegui abrir as configurações");
                    }
                }
            });
        }
    }
}
