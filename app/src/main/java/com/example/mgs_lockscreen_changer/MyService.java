package com.example.mgs_lockscreen_changer;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static com.caverock.androidsvg.SVG.getFromInputStream;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.widget.Toast;


import androidx.core.app.NotificationCompat;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

public class MyService extends Service {
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    String birthBlock = null;
    String visibleCrop = null;
    String bothLockAndHome = null;
    String lockOrHome = null;


    public MyService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MGS Wallpaper Changer")
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);

        Bundle extras = intent.getExtras();
        if (extras == null) {
            stopSelf();

        } else {
            visibleCrop = (String) extras.get("_visibleCrop");
            birthBlock = (String) extras.get("_birthBlock");
            bothLockAndHome = (String) extras.get("_bothLockAndHome");
            lockOrHome = (String) extras.get("_LockOrHome");

        }
        // Start the AsyncTask to retrieve and set the image

        //Toast.makeText(MyService.this, "service running", Toast.LENGTH_SHORT).show();
        new MyService.SetWallpaperTask().execute("https://seeder.mutant.garden/api/mutant/" + birthBlock + "/raster/now");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, MyAlarm.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE);

        alarmManager.cancel(pendingIntent);
        //Toast.makeText(MyService.this, "alarm canceled", Toast.LENGTH_SHORT).show();

        super.onDestroy();
    }


    private class SetWallpaperTask extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... params) {
            // Get the image URL
            String imageUrl = params[0];

            try {
                InputStream inputStream = new URL(imageUrl).openStream();
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                Bitmap imageWithBG = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(),bitmap.getConfig());  // Create another image the same size
                imageWithBG.eraseColor(Color.WHITE);  // set the background to white
                Canvas canvas = new Canvas(imageWithBG);  // create a canvas to draw on the new image
                canvas.drawBitmap(bitmap, 0f, 0f, null); // draw old image on the background
                bitmap.recycle();  // clear out old image

                return imageWithBG;

            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            // Set the image as the wallpaper
            WallpaperManager wallpaperManager = WallpaperManager.getInstance(MyService.this);
            try {
                if (bitmap == null){
                    //Toast.makeText(MyService.this, "failed to download image", Toast.LENGTH_SHORT).show();
                }
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    //Set wallpaper on Lockscreen
                    //Toast.makeText(MyService.this, "image downloaded", Toast.LENGTH_SHORT).show();
                    if (Objects.equals(bothLockAndHome, "true")) {
                        wallpaperManager.setBitmap(bitmap, new Rect(Integer.parseInt(visibleCrop), 0, bitmap.getWidth(), bitmap.getHeight()), true );
                        //Toast.makeText(MyService.this, "both screens set successfully", Toast.LENGTH_SHORT).show();
                     }
                    else if (Objects.equals(bothLockAndHome, "false") && Objects.equals(lockOrHome, "false")){
                        wallpaperManager.setBitmap(bitmap, new Rect(Integer.parseInt(visibleCrop), 0, bitmap.getWidth(), bitmap.getHeight()), true, WallpaperManager.FLAG_LOCK );
                        //Toast.makeText(MyService.this, "lockscreen set successfully", Toast.LENGTH_SHORT).show();
                    }
                    else if (Objects.equals(bothLockAndHome, "false") && Objects.equals(lockOrHome, "true")){
                        wallpaperManager.setBitmap(bitmap, new Rect(Integer.parseInt(visibleCrop), 0, bitmap.getWidth(), bitmap.getHeight()), true, WallpaperManager.FLAG_SYSTEM );
                        //Toast.makeText(MyService.this, "homescreen set successfully", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                //Toast.makeText(MyService.this, "Set wallpaper failed", Toast.LENGTH_SHORT).show();
            }
        }

    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

}


