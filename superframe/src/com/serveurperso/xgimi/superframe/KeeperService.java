package com.serveurperso.xgimi.superframe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

// XGIMI's power service (com.xgimi.xrmservice) turns SuperFrame off when the
// projector boots and each time it goes to standby. This service remembers what
// the user chose while the projector is on, and applies it again after boot and
// after each wake, through the same vendor provider call as the settings menu.
public class KeeperService extends Service {
    private static final String TAG = "SuperFrameKeeper";

    private static final String KEY = "CFG_SUPER_FRAME";
    private static final int ON  = 1;
    private static final int OFF = 0;

    private static final Uri    PROVIDER = Uri.parse("content://com.mediatek.tv.settings.xgimi.provider");
    private static final String METHOD   = "setSuperFrame";
    private static final String ENABLE   = "true";

    // the vendor wake sequence takes about 6.5 s after SCREEN_ON; applying the
    // setting before it ends leaves the display pipeline half initialized
    private static final long RESTORE_DELAY_MS = 10000;

    private static final String PREFS       = "keeper";
    private static final String PREF_WANTED = "wanted";

    private static final String CHANNEL_ID      = "keeper";
    private static final int    NOTIFICATION_ID = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private PowerManager      power;

    private final Runnable restore = this::restore;

    private final ContentObserver observer = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
            int value = read();
            if (!power.isInteractive()) {
                Log.i(TAG, "standby set " + KEY + " to " + value);
                return;
            }
            prefs.edit().putBoolean(PREF_WANTED, value == ON).apply();
            Log.i(TAG, "user set " + KEY + " to " + value);
        }
    };

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "wake, " + KEY + " = " + read());
            schedule();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "SuperFrame Keeper", NotificationManager.IMPORTANCE_MIN));
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_rotate)
                .setContentTitle("SuperFrame Keeper")
                .build();
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        power = getSystemService(PowerManager.class);

        getContentResolver().registerContentObserver(Settings.Global.getUriFor(KEY), false, observer);
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));

        Log.i(TAG, "start, wanted = " + wanted() + ", " + KEY + " = " + read());
        schedule();
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(screenOn);
        getContentResolver().unregisterContentObserver(observer);
        handler.removeCallbacks(restore);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int read() {
        return Settings.Global.getInt(getContentResolver(), KEY, OFF);
    }

    private boolean wanted() {
        return prefs.getBoolean(PREF_WANTED, false);
    }

    private void schedule() {
        handler.removeCallbacks(restore);
        handler.postDelayed(restore, RESTORE_DELAY_MS);
    }

    private void restore() {
        if (!wanted() || !power.isInteractive() || read() == ON) {
            return;
        }
        Log.i(TAG, "restoring " + KEY);
        getContentResolver().call(PROVIDER, METHOD, ENABLE, null);
    }
}
