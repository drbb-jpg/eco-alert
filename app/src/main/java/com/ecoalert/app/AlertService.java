package com.ecoalert.app;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import com.google.firebase.messaging.*;
import java.util.Map;

public class AlertService extends FirebaseMessagingService {
    @Override public void onMessageReceived(RemoteMessage message) {
        var prefs=getSharedPreferences("eco", MODE_PRIVATE);
        Map<String,String> data=message.getData();
        boolean result="result".equals(data.get("kind"));
        boolean before="reminder".equals(data.get("kind"));
        if (!result && !before) return;
        if (result && !prefs.getBoolean("results",false)) return;
        if (before && (!prefs.getBoolean("reminders",false) ||
            !Integer.toString(prefs.getInt("minutes",15)).equals(data.get("minutes")))) return;
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return;
        String key=data.get("key");
        if (key==null || key.equals(prefs.getString("lastMessage",""))) return;
        long expires;
        try { expires=Long.parseLong(data.getOrDefault("expires","0")); } catch (NumberFormatException e) { return; }
        if (System.currentTimeMillis()>expires) return;
        prefs.edit().putString("lastMessage",key).apply();
        PendingIntent intent=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"economic_alerts").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(data.getOrDefault("title","Éco Alert")).setContentText(data.getOrDefault("body",""))
            .setStyle(new Notification.BigTextStyle().bigText(data.getOrDefault("body","")))
            .setContentIntent(intent).setAutoCancel(true).build();
        getSystemService(NotificationManager.class).notify(key.hashCode(),n);
    }
}
