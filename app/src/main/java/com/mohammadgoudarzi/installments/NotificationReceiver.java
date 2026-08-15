
package com.mohammadgoudarzi.installments;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "installment_notifications";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MESSAGE = "message";

    @Override
    public void onReceive(Context context, Intent intent) {

        String title = intent.getStringExtra(EXTRA_TITLE);
        String message = intent.getStringExtra(EXTRA_MESSAGE);

        if (title == null) {
            title = "مدیریت اقساط";
        }

        if (message == null) {
            message = "یک قسط به سررسید نزدیک شده است.";
        }

        createNotificationChannel(context);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE
                );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(message)
                        )
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);

        manager.notify(
                (int) System.currentTimeMillis(),
                builder.build()
        );
    }

    private void createNotificationChannel(Context context) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "یادآوری اقساط",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "اعلان سررسید اقساط مدیریت اقساط"
            );

            NotificationManager manager =
                    (NotificationManager) context.getSystemService(
                            Context.NOTIFICATION_SERVICE
                    );

            manager.createNotificationChannel(channel);
        }
    }
}
