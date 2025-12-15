package com.example.mgs_lockscreen_changer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

public class MyWorker extends Worker {

    public MyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String birthBlock = getInputData().getString("_birthBlock");
        if (birthBlock == null || birthBlock.isEmpty()) {
            return Result.failure();
        }

        int cropLeft = parseIntSafe(getInputData().getString("_cropLeft"));
        int cropRight = parseIntSafe(getInputData().getString("_cropRight"));
        int[] limitedCrops = limitTotalCrop(cropLeft, cropRight);

        boolean applyToBoth = getInputData().getBoolean("_bothLockAndHome", false);
        boolean applyToHome = getInputData().getBoolean("_lockOrHome",
                getInputData().getBoolean("_LockOrHome", false));

        try {
            WallpaperUpdater.updateWallpaper(
                    getApplicationContext(),
                    buildImageUrl(birthBlock),
                    limitedCrops[0],
                    limitedCrops[1],
                    applyToBoth,
                    applyToHome
            );
            return Result.success();
        } catch (IOException e) {
            e.printStackTrace();
            return Result.retry();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
    }

    private int parseIntSafe(String value) {
        try {
            return Math.max(Integer.parseInt(value), 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private int[] limitTotalCrop(int left, int right) {
        int safeLeft = Math.max(left, 0);
        int safeRight = Math.max(right, 0);
        int total = safeLeft + safeRight;
        if (total > 1000) {
            if (safeLeft >= 1000) {
                safeLeft = 1000;
                safeRight = 0;
            } else {
                safeRight = 1000 - safeLeft;
            }
        }
        return new int[]{safeLeft, safeRight};
    }

    private String buildImageUrl(String birthBlock) {
        return "https://seeder.mutant.garden/api/mutant/" + birthBlock + "/raster/now";
    }
}
