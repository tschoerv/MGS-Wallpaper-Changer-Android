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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.Objects;


public class MyWorker extends Worker {
    String birthBlock = null;
    String visibleCrop = null;
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
        visibleCrop = getInputData().getString("_visibleCrop");
        birthBlock = getInputData().getString("_birthBlock");
        bothLockAndHome = getInputData().getString("_bothLockAndHome");
        lockOrHome = getInputData().getString("_LockOrHome");


    // Start the task to retrieve and set the image
        setWallpaperTask("https://seeder.mutant.garden/api/mutant/" + birthBlock + "/raster/now");
        return Result.success();
    }

    private void setWallpaperTask(String _imgURL) {

        try {
                InputStream inputStream = new URL(_imgURL).openStream();
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                Bitmap imageWithBG = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),bitmap.getConfig());  // Create another image the same size
                imageWithBG.eraseColor(Color.WHITE);  // set the background to white
                Canvas canvas = new Canvas(imageWithBG);  // create a canvas to draw on the new image
                canvas.drawBitmap(bitmap, 0f, 0f, null); // draw downloaded image on the background
                bitmap.recycle();  // clear out old image

                // Set the image as the wallpaper
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());

               if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    //Set wallpaper on both screens
                    if (Objects.equals(bothLockAndHome, "true")) {
                        wallpaperManager.setBitmap(imageWithBG, new Rect(Integer.parseInt(visibleCrop), 0, imageWithBG.getWidth(), imageWithBG.getHeight()), true );
                        saveLastUpdateTime();
                    }
                    //Set wallpaper on Lockscreen
                    else if (Objects.equals(bothLockAndHome, "false") && Objects.equals(lockOrHome, "false")){
                        wallpaperManager.setBitmap(imageWithBG, new Rect(Integer.parseInt(visibleCrop), 0, imageWithBG.getWidth(), imageWithBG.getHeight()), true, WallpaperManager.FLAG_LOCK );
                        saveLastUpdateTime();

                    }
                    //Set wallpaper on Home-screen
                    else if (Objects.equals(bothLockAndHome, "false") && Objects.equals(lockOrHome, "true")){
                        wallpaperManager.setBitmap(imageWithBG, new Rect(Integer.parseInt(visibleCrop), 0, imageWithBG.getWidth(), imageWithBG.getHeight()), true, WallpaperManager.FLAG_SYSTEM );
                        saveLastUpdateTime();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
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
