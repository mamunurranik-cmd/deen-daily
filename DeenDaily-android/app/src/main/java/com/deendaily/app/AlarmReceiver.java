package com.deendaily.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        String id = intent.getStringExtra("id");
        Notifier.show(context,
                title == null ? "দ্বীন ডেইলি" : title,
                body == null ? "" : body,
                id == null ? "dd" : id);
    }
}
