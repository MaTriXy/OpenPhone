package org.openphone.assistant;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.openphone.assistant.watchers.OpenPhoneWatcherScheduler;

import java.io.IOException;
import java.io.InputStream;

public final class OpenPhoneBootReceiver extends BroadcastReceiver {
    private static final String TAG = "OpenPhoneBoot";
    private static final String PREFS = "openphone_assistant";
    private static final String PREF_WALLPAPER_VERSION = "openphone_wallpaper_version";
    private static final int WALLPAPER_VERSION = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }

        Intent serviceIntent = new Intent(context, OpenPhoneAssistantService.class);
        context.startService(serviceIntent);
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            applyOpenPhoneWallpaperIfNeeded(context);
        }
        OpenPhoneNotificationController.showReady(context);
        OpenPhoneNotificationListenerService.ensureEnabled(context);
        OpenPhoneWatcherScheduler.checkNow(context);
        Log.i(TAG, "Started OpenPhone assistant service for " + action);
    }

    public static void applyOpenPhoneWallpaperIfNeeded(Context context) {
        if (context == null) {
            return;
        }
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(PREF_WALLPAPER_VERSION, 0) >= WALLPAPER_VERSION) {
            return;
        }
        try (InputStream input = context.getResources()
                .openRawResource(R.drawable.openphone_sky)) {
            WallpaperManager.getInstance(context).setStream(input, null, true,
                    WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(PREF_WALLPAPER_VERSION, WALLPAPER_VERSION)
                    .apply();
            Log.i(TAG, "Applied OpenPhone system and lock wallpaper v"
                    + WALLPAPER_VERSION);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Failed to apply OpenPhone wallpaper", e);
        }
    }
}
