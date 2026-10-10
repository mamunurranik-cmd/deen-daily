package com.deendaily.app;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 101;
    private static final int REQ_IMG = 102;
    private static final int REQ_LOC = 103;
    private static final int REQ_NOTIF = 104;
    private static final String CHANNEL = "dd_reminders";

    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private boolean notifAsked = false;
    private String nativeJs = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#051416"));
        setContentView(web);

        try {
            InputStream is = getAssets().open("dd-native.js");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            nativeJs = bos.toString("UTF-8");
        } catch (Exception ignored) {
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);

        web.addJavascriptInterface(new Bridge(), "DeenDaily");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String sch = u.getScheme();
                if ("file".equals(sch)) {
                    return false;
                }
                if ("http".equals(sch) || "https".equals(sch) || "mailto".equals(sch) || "tel".equals(sch)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, u));
                    } catch (Exception ignored) {
                    }
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (nativeJs.length() > 0) {
                    view.evaluateJavascript(nativeJs, null);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) {
                    fileCb.onReceiveValue(null);
                }
                fileCb = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        web.loadUrl("file:///android_asset/index.html");
    }

    /* ---------- helpers ---------- */

    private void js(final String code) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (web != null) {
                    web.evaluateJavascript(code, null);
                }
            }
        });
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private String safeName(String n, String def) {
        if (n == null || n.trim().length() == 0) return def;
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private Uri saveBytes(byte[] data, String name, String mime) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new Exception("insert failed");
            OutputStream os = getContentResolver().openOutputStream(u);
            os.write(data);
            os.close();
            return u;
        } else {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            dir.mkdirs();
            File f = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(data);
            fos.close();
            return Uri.fromFile(f);
        }
    }

    private String savedMsg() {
        return Build.VERSION.SDK_INT >= 29
                ? "ফাইল Downloads ফোল্ডারে সেভ হয়েছে"
                : "ফাইল অ্যাপের ডাউনলোড ফোল্ডারে সেভ হয়েছে";
    }

    /* ---------- image ---------- */

    private void processImage(final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(is, null, o);
                    is.close();
                    int sample = 1;
                    while (Math.max(o.outWidth, o.outHeight) / sample > 1024) sample *= 2;
                    o.inJustDecodeBounds = false;
                    o.inSampleSize = sample;
                    is = getContentResolver().openInputStream(uri);
                    Bitmap bm = BitmapFactory.decodeStream(is, null, o);
                    is.close();

                    int rot = 0;
                    try {
                        InputStream is2 = getContentResolver().openInputStream(uri);
                        ExifInterface ex = new ExifInterface(is2);
                        int ori = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                        is2.close();
                        if (ori == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
                        else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
                        else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
                    } catch (Exception ignored) {
                    }
                    if (rot != 0) {
                        Matrix m = new Matrix();
                        m.postRotate(rot);
                        bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                    }

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    bm.compress(Bitmap.CompressFormat.JPEG, 85, bos);
                    String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                    js("window.ddOnImagePicked&&window.ddOnImagePicked('data:image/jpeg;base64," + b64 + "')");
                } catch (Exception e) {
                    js("window.ddOnImagePicked&&window.ddOnImagePicked('')");
                }
            }
        }).start();
    }

    /* ---------- location ---------- */

    private void locationError() {
        js("window.ddOnLocationError&&window.ddOnLocationError()");
    }

    private void deliverLocation(Location l) {
        js("window.ddOnLocation&&window.ddOnLocation(" + l.getLatitude() + "," + l.getLongitude() + ")");
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void doLocation() {
        try {
            final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null && System.currentTimeMillis() - best.getTime() < 30L * 60L * 1000L) {
                deliverLocation(best);
                return;
            }
            String prov = null;
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) prov = LocationManager.NETWORK_PROVIDER;
            else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) prov = LocationManager.GPS_PROVIDER;
            if (prov == null) {
                if (best != null) deliverLocation(best);
                else locationError();
                return;
            }
            final boolean[] done = {false};
            final LocationListener ls = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (done[0]) return;
                    done[0] = true;
                    deliverLocation(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            };
            lm.requestSingleUpdate(prov, ls, Looper.getMainLooper());
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!done[0]) {
                        done[0] = true;
                        try {
                            lm.removeUpdates(ls);
                        } catch (Exception ignored) {
                        }
                        locationError();
                    }
                }
            }, 20000);
        } catch (Exception e) {
            locationError();
        }
    }

    /* ---------- notifications ---------- */

    private String notifState() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                return "granted";
            }
            return notifAsked ? "denied" : "default";
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        return nm.areNotificationsEnabled() ? "granted" : "denied";
    }

    private void showNotif(String title, String body, String tag) {
        if (!"granted".equals(notifState())) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "রিমাইন্ডার", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CHANNEL);
        else b = new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify(Math.abs(tag.hashCode()), b.build());
    }

    /* ---------- JS bridge: window.DeenDaily ---------- */

    private class Bridge {

        @JavascriptInterface
        public void pickImage() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("image/*");
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    try {
                        startActivityForResult(Intent.createChooser(i, "ছবি বাছাই করুন"), REQ_IMG);
                    } catch (Exception e) {
                        js("window.ddOnImagePicked&&window.ddOnImagePicked('')");
                    }
                }
            });
        }

        @JavascriptInterface
        public void savePdf(String b64, String name) {
            saveFile(b64, name, "application/pdf");
        }

        @JavascriptInterface
        public void saveFile(String b64, String name, String mime) {
            try {
                byte[] d = Base64.decode(b64, Base64.DEFAULT);
                saveBytes(d, safeName(name, "deen-daily-file"), mime == null || mime.length() == 0 ? "application/octet-stream" : mime);
                toast(savedMsg());
            } catch (Exception e) {
                toast("ফাইল সেভ করা যায়নি");
            }
        }

        @JavascriptInterface
        public void sharePdf(String b64, String name) {
            try {
                byte[] d = Base64.decode(b64, Base64.DEFAULT);
                final Uri u = saveBytes(d, safeName(name, "deen-daily-report.pdf"), "application/pdf");
                if (Build.VERSION.SDK_INT >= 29) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Intent s = new Intent(Intent.ACTION_SEND);
                            s.setType("application/pdf");
                            s.putExtra(Intent.EXTRA_STREAM, u);
                            s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(s, "শেয়ার করুন"));
                        }
                    });
                } else {
                    toast(savedMsg());
                }
            } catch (Exception e) {
                toast("PDF শেয়ার করা যায়নি");
            }
        }

        @JavascriptInterface
        public void shareText(final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent s = new Intent(Intent.ACTION_SEND);
                    s.setType("text/plain");
                    s.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(s, "শেয়ার করুন"));
                }
            });
        }

        @JavascriptInterface
        public void getLocation() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (hasLocationPermission()) {
                        doLocation();
                    } else {
                        requestPermissions(new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
                    }
                }
            });
        }

        @JavascriptInterface
        public String notifPermission() {
            return notifState();
        }

        @JavascriptInterface
        public void requestNotifPermission() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifAsked = true;
                    if (Build.VERSION.SDK_INT >= 33
                            && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                    } else {
                        js("window.ddOnNotifPermission&&window.ddOnNotifPermission('" + notifState() + "')");
                    }
                }
            });
        }

        @JavascriptInterface
        public void showNotification(final String title, final String body, final String tag) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showNotif(title, body, tag);
                }
            });
        }
    }

    /* ---------- results ---------- */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            if (fileCb != null) {
                fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCb = null;
            }
            return;
        }
        if (requestCode == REQ_IMG) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                processImage(data.getData());
            } else {
                js("window.ddOnImagePicked&&window.ddOnImagePicked('')");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOC) {
            if (hasLocationPermission()) doLocation();
            else locationError();
        } else if (requestCode == REQ_NOTIF) {
            js("window.ddOnNotifPermission&&window.ddOnNotifPermission('" + notifState() + "')");
        }
    }
}
