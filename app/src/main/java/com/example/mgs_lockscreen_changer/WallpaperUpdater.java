package com.example.mgs_lockscreen_changer;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Shared helper for downloading, cropping, storing and applying wallpapers.
 * Keeps WorkManager and Activity behaviour in sync.
 */
public final class WallpaperUpdater {
    private static final String WALLPAPER_FILE_NAME = "wallpaper.png";
    private static final int NETWORK_TIMEOUT_MS = 10_000;

    private WallpaperUpdater() {
    }

    public static void updateWallpaper(Context context,
                                       String imageUrl,
                                       int cropLeft,
                                       int cropRight,
                                       boolean applyToBoth,
                                       boolean applyToHomeOnly) throws IOException {
        Bitmap downloaded = downloadBitmap(imageUrl);
        Bitmap prepared = cropBitmap(downloaded, cropLeft, cropRight);
        File wallpaperFile = saveBitmapToFile(context, prepared);
        applyWallpaper(context, wallpaperFile, applyToBoth, applyToHomeOnly);
        saveLastUpdateTime(context);
    }

    public static File getCachedWallpaperFile(Context context) {
        return new File(context.getCacheDir(), WALLPAPER_FILE_NAME);
    }

    private static Bitmap downloadBitmap(String imageUrl) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(imageUrl).openConnection();
        connection.setConnectTimeout(NETWORK_TIMEOUT_MS);
        connection.setReadTimeout(NETWORK_TIMEOUT_MS);

        try (InputStream inputStream = connection.getInputStream()) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                throw new IOException("Failed to decode bitmap from " + imageUrl);
            }
            return bitmap;
        } finally {
            connection.disconnect();
        }
    }

    private static Bitmap cropBitmap(Bitmap bitmap, int cropLeft, int cropRight) {
        int safeLeft = Math.max(0, cropLeft);
        int safeRight = Math.max(0, cropRight);

        safeLeft = Math.min(safeLeft, bitmap.getWidth());
        safeRight = Math.min(safeRight, bitmap.getWidth() - safeLeft);

        Rect src = new Rect(safeLeft, 0, bitmap.getWidth() - safeRight, bitmap.getHeight());
        Bitmap.Config config = bitmap.getConfig() != null ? bitmap.getConfig() : Bitmap.Config.ARGB_8888;
        Bitmap imageWithBG = Bitmap.createBitmap(src.width(), src.height(), config);
        imageWithBG.eraseColor(Color.WHITE);
        Canvas canvas = new Canvas(imageWithBG);
        canvas.drawBitmap(bitmap, src, new Rect(0, 0, src.width(), src.height()), null);

        return imageWithBG;
    }

    private static File saveBitmapToFile(Context context, Bitmap bitmap) throws IOException {
        File wallpaperFile = getCachedWallpaperFile(context);

        try (FileOutputStream out = new FileOutputStream(wallpaperFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }

        return wallpaperFile;
    }

    private static void applyWallpaper(Context context,
                                       File wallpaperFile,
                                       boolean applyToBoth,
                                       boolean applyToHomeOnly) throws IOException {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(context.getApplicationContext());

        try (InputStream fileInputStream = new FileInputStream(wallpaperFile)) {
            if (applyToBoth || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                wallpaperManager.setStream(fileInputStream);
            } else if (applyToHomeOnly) {
                wallpaperManager.setStream(fileInputStream, null, true, WallpaperManager.FLAG_SYSTEM);
            } else {
                wallpaperManager.setStream(fileInputStream, null, true, WallpaperManager.FLAG_LOCK);
            }
        }
    }

    private static void saveLastUpdateTime(Context context) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault());
        String formatted = formatter.format(new Date());

        SharedPreferences sharedPreferences = context.getSharedPreferences("userPref", Context.MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();

        myEdit.putString("_lastUpdate", formatted);
        myEdit.apply();
    }
}
