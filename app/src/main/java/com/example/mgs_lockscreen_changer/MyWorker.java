package com.example.mgs_lockscreen_changer;

import static android.content.Context.MODE_PRIVATE;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.icu.text.SimpleDateFormat;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MyWorker extends Worker {
    String birthBlock = null;
    String cropLeft = null;
    String cropRight = null;
    String bothLockAndHome = null;
    String lockOrHome = null;
    Integer updateIntervalMillis;
    final Context context = getApplicationContext();


    public MyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String updateIntervalString = getInputData().getString("_updateInterval");
        if (updateIntervalString != null) {
            updateIntervalMillis = (Integer.parseInt(updateIntervalString));
        }
        cropLeft = getInputData().getString("_cropLeft");
        cropRight = getInputData().getString("_cropRight");
        birthBlock = getInputData().getString("_birthBlock");
        bothLockAndHome = getInputData().getString("_bothLockAndHome");
        lockOrHome = getInputData().getString("_LockOrHome");


    // Start the task to retrieve and set the image
        setWallpaperTask("https://seeder.mutant.garden/api/mutant/" + birthBlock + "/raster/now");
        return Result.success();
    }

    private void setWallpaperTask(String _imgURL) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                InputStream inputStream = new URL(_imgURL).openStream();
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                // Determine the amount to crop from the left and right sides
                int cropLeftInt = 0;
                if(cropLeft != null && !cropLeft.isEmpty()){
                    cropLeftInt = Integer.parseInt(cropLeft);
                }

                int cropRightInt = 0;
                if(cropRight != null && !cropRight.isEmpty()){
                    cropRightInt = Integer.parseInt(cropRight);
                }

                // Ensure cropLeft and cropRight are within the bitmaps width
                cropLeftInt = Math.min(cropLeftInt, bitmap.getWidth());
                cropRightInt = Math.min(cropRightInt, bitmap.getWidth() - cropLeftInt);

                // Create a source rectangle from cropLeft to the width minus cropRight
                Rect src = new Rect(cropLeftInt, 0, bitmap.getWidth() - cropRightInt, bitmap.getHeight());

                // Create another image of the size of the src rectangle with a white background
                Bitmap imageWithBG = Bitmap.createBitmap(src.width(), src.height(), bitmap.getConfig());
                imageWithBG.eraseColor(Color.WHITE);
                Canvas canvas = new Canvas(imageWithBG);
                // Draw only the portion of the bitmap defined by src, scaled to fit the new bitmap
                canvas.drawBitmap(bitmap, src, new Rect(0, 0, src.width(), src.height()), null);


                // Convert the bitmap to a file
                File wallpaperFile = saveBitmapToFile(imageWithBG);

                WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());

                // Set the image as the wallpaper based on user choice
                try (InputStream fileInputStream = new FileInputStream(wallpaperFile)) {
                    if (Objects.equals(bothLockAndHome, "true")) {
                        wallpaperManager.setStream(fileInputStream);
                    } else if (Objects.equals(lockOrHome, "true")) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            wallpaperManager.setStream(fileInputStream, null, true, WallpaperManager.FLAG_SYSTEM);
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            wallpaperManager.setStream(fileInputStream, null, true, WallpaperManager.FLAG_LOCK);
                        }
                    }
                    saveLastUpdateTime();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        executor.shutdown();
    }



    private File saveBitmapToFile(Bitmap bitmap) throws IOException {
        // Create a file in the app's cache directory
        File cacheDir = getApplicationContext().getCacheDir();
        File wallpaperFile = new File(cacheDir, "wallpaper.png");

        try (FileOutputStream out = new FileOutputStream(wallpaperFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // PNG is a lossless format, the compression factor (100) is ignored
        }

        return wallpaperFile;
    }



    private void saveLastUpdateTime() {
        SimpleDateFormat s;
        String format = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            s = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            format = s.format(new Date());
        }

        SharedPreferences sharedPreferences = context.getSharedPreferences("userPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();

        myEdit.putString("_lastUpdate", format);
        myEdit.apply();
    }

}
