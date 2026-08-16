package com.mohammadgoudarzi.installments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null)
            return;

        String action = intent.getAction();

        if (
                Intent.ACTION_BOOT_COMPLETED.equals(action)
                        ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
        ) {

            /*
             * بعد از روشن شدن گوشی یا نصب نسخه جدید،
             * MainActivity اجرا نمی‌شود.
             *
             * اینجا فقط NotificationReceiver را
             * برای بررسی یادآوری‌ها فعال می‌کنیم.
             */

            Intent notificationIntent =
                    new Intent(
                            context,
                            NotificationReceiver.class
                    );

            context.sendBroadcast(
                    notificationIntent
            );
        }
    }
}
