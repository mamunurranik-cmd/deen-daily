package com.deendaily.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

final class AlarmHelper {
    private static final String PREFS = "dd_alarms";
    private static final String KEY = "jobs";

    private AlarmHelper() {
    }

    /** JS থেকে আসা সময়সূচি (JSON array) নিয়ে পুরনো অ্যালার্ম বাতিল করে নতুনগুলো বসায়। */
    static synchronized void schedule(Context c, String json) {
        try {
            JSONArray arr = new JSONArray(json);
            cancelAll(c);
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply();
            setAll(c, arr);
        } catch (Exception ignored) {
        }
    }

    /** রিবুট বা অ্যাপ আপডেটের পর সেভ করা সময়সূচি আবার বসায়। */
    static synchronized void rescheduleFromPrefs(Context c) {
        try {
            String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
            setAll(c, new JSONArray(s));
        } catch (Exception ignored) {
        }
    }

    private static void cancelAll(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray old = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);
                PendingIntent pi = PendingIntent.getBroadcast(c, o.getString("id").hashCode(),
                        new Intent(c, AlarmReceiver.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
                if (pi != null) {
                    am.cancel(pi);
                    pi.cancel();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void setAll(Context c, JSONArray arr) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        long now = System.currentTimeMillis();
        boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
        int max = Math.min(arr.length(), 400);
        for (int i = 0; i < max; i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                long t = o.getLong("t");
                if (t <= now) {
                    continue;
                }
                String id = o.getString("id");
                Intent in = new Intent(c, AlarmReceiver.class);
                in.putExtra("id", id);
                in.putExtra("title", o.optString("title", "দ্বীন ডেইলি"));
                in.putExtra("body", o.optString("body", ""));
                PendingIntent pi = PendingIntent.getBroadcast(c, id.hashCode(), in,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                try {
                    if (exact) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                    }
                } catch (SecurityException se) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
