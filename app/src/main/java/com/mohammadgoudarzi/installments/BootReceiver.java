package com.mohammadgoudarzi.installments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(
            Context context,
            Intent intent
    ) {

        if (intent == null) {
            return;
        }

        String action = intent.getAction();

        /*
         * بعد از روشن شدن گوشی
         * یا نصب/به‌روزرسانی نسخه جدید برنامه،
         * آلارم یادآوری اقساط دوباره ساخته می‌شود.
         */
        if (
                Intent.ACTION_BOOT_COMPLETED.equals(action)
                        ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
        ) {

            /*
             * آلارم قبلی را دوباره تنظیم می‌کنیم.
             *
             * اطلاعات اقساط و تنظیمات یادآوری
             * قبلاً داخل SharedPreferences ذخیره شده‌اند.
             */
            NotificationReceiver.scheduleNextAlarm(
                    context
            );
        }
    }
}
