package com.deendaily.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {
    private WebView web;
    private static final int REQ_FILE = 1001;
    private static final int REQ_LOC = 1002;
    private ValueCallback<Uri[]> fileCb;

    private class Bridge {
        @android.webkit.JavascriptInterface
        public void pickImage() {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            try { startActivityForResult(Intent.createChooser(i, "Pick image"), REQ_FILE); } catch (Exception ignored) {}
        }
        @android.webkit.JavascriptInterface
        public void savePdf(String b64, String fn) {
            try {
                File dir = new File(getExternalFilesDir(null), "reports");
                if (!dir.exists()) dir.mkdirs();
                String name = (fn == null || fn.length() == 0) ? "deen-daily-report.pdf" : fn;
                FileOutputStream fos = new FileOutputStream(new File(dir, name));
                fos.write(Base64.decode(b64, Base64.DEFAULT));
                fos.close();
                toast("PDF সেভ হয়েছে: " + name);
            } catch (Exception e) { toast("PDF save failed"); }
        }
        @android.webkit.JavascriptInterface
        public void sharePdf(String b64, String fn) {
            try {
                File dir = new File(getExternalFilesDir(null), "reports");
                if (!dir.exists()) dir.mkdirs();
                String name = (fn == null || fn.length() == 0) ? "deen-daily-report.pdf" : fn;
                File f = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(Base64.decode(b64, Base64.DEFAULT));
                fos.close();
                Uri uri = FileProvider.getUriForFile(MainActivity.this, "com.deendaily.app.fileprovider", f);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("application/pdf");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Share PDF"));
            } catch (Exception e) { toast("Share failed"); }
        }
        @android.webkit.JavascriptInterface
        public void shareText(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
            startActivity(Intent.createChooser(i, "Share"));
        }
        @android.webkit.JavascriptInterface
        public void getLocation() {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                sendLoc();
            } else {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
            }
        }
    }

    private void sendLoc() {
        try {
            android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
            String p = lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) ? android.location.LocationManager.NETWORK_PROVIDER : android.location.LocationManager.GPS_PROVIDER;
            android.location.Location l = lm.getLastKnownLocation(p);
            if (l != null) { locJs(l.getLatitude(), l.getLongitude()); return; }
            lm.requestSingleUpdate(p, new android.location.LocationListener() {
                public void onLocationChanged(android.location.Location loc) { locJs(loc.getLatitude(), loc.getLongitude()); }
            }, getMainLooper());
        } catch (Exception e) { locErrJs(); }
    }

    private void locJs(final double lat, final double lon) {
        runOnUiThread(new Runnable() { public void run() {
            web.evaluateJavascript("window.ddOnLocation&&window.ddOnLocation(" + lat + "," + lon + ")", null);
        }});
    }

    private void locErrJs() {
        runOnUiThread(new Runnable() { public void run() {
            web.evaluateJavascript("window.ddOnLocationError&&window.ddOnLocationError()", null);
        }});
    }

    private void toast(final String m) {
        runOnUiThread(new Runnable() { public void run() {
            android.widget.Toast.makeText(MainActivity.this, m, android.widget.Toast.LENGTH_SHORT).show();
        }});
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#051416"));
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setGeolocationEnabled(true);
        web.addJavascriptInterface(new Bridge(), "DeenDaily");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams fp) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try { startActivityForResult(fp.createIntent(), REQ_FILE); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    cb.invoke(origin, true, false);
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
                }
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        if (req == REQ_FILE && fileCb != null) {
            Uri[] results = null;
            if (res == RESULT_OK && data != null && data.getData() != null) results = new Uri[]{data.getData()};
            fileCb.onReceiveValue(results);
            fileCb = null;
            if (res == RESULT_OK && data != null && data.getData() != null) imgJs(data.getData());
        } else super.onActivityResult(req, res, data);
    }

    private void imgJs(Uri uri) {
        try {
            java.io.InputStream is = getContentResolver().openInputStream(uri);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            is.close();
            final String js = "window.ddOnImagePicked&&window.ddOnImagePicked('data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP) + "')";
            runOnUiThread(new Runnable() { public void run() { web.evaluateJavascript(js, null); }});
        } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req == REQ_LOC) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(new Runnable() { public void run() { sendLoc(); }});
            } else locErrJs();
        } else super.onRequestPermissionsResult(req, perms, res);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
