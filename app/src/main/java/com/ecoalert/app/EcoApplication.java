package com.ecoalert.app;

import android.app.*;
import com.google.firebase.*;

public class EcoApplication extends Application {
    public static boolean pushReady() {
        return !BuildConfig.API_BASE_URL.trim().isEmpty() && !BuildConfig.FIREBASE_APP_ID.trim().isEmpty()
            && !BuildConfig.FIREBASE_API_KEY.trim().isEmpty() && !BuildConfig.FIREBASE_PROJECT_ID.trim().isEmpty()
            && !BuildConfig.FIREBASE_SENDER_ID.trim().isEmpty();
    }
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel("economic_alerts", "Annonces économiques", NotificationManager.IMPORTANCE_HIGH);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        if (pushReady() && FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this, new FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID).setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID).setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID).build());
        }
    }
}
