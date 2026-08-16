package com.mohammadgoudarzi.installments;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "installment_notifications";
    private static final int NOTIFICATION_ID = 4810;

    @Override
    public void onReceive(Context context, Intent intent) {
        createNotificationChannel(context);
        checkInstallments(context);
    }

    private void checkInstallments(Context context) {
        try {
            android.content.SharedPreferences prefs =
                    context.getSharedPreferences(
                            "installment_notifications",
                            Context.MODE_PRIVATE
                    );

            String json = prefs.getString("installments", "[]");
            JSONArray installments = new JSONArray(json);

            Calendar now = Calendar.getInstance();

            // دو روز بعد
            Calendar target = (Calendar) now.clone();
            target.add(Calendar.DAY_OF_YEAR, 2);

            String targetDate =
                    new SimpleDateFormat(
                            "yyyy/MM/dd",
                            Locale.US
                    ).format(target.getTime());

            for (int i = 0; i < installments.length(); i++) {

                JSONObject item =
                        installments.getJSONObject(i);

                String name =
                        item.optString(
                                "name",
                                "قسط"
                        );

                JSONArray dates =
                        item.optJSONArray("dates");

                if (dates == null)
                    continue;

                for (int j = 0; j < dates.length(); j++) {

                    JSONObject dateObject =
                            dates.getJSONObject(j);

                    if (
                            dateObject.optBoolean(
                                    "paid",
                                    false
                            )
                    )
                        continue;

                    String date =
                            dateObject.optString(
                                    "date",
                                    ""
                            );

                    if (date.isEmpty())
                        continue;

                    String normalizedDate =
                            normalizeDate(date);

                    if (
                            !targetDate.equals(
                                    normalizedDate
                            )
                    )
                        continue;

                    String notificationKey =
                            "notified_"
                                    + name
                                    + "_"
                                    + normalizedDate;

                    if (
                            !prefs.getBoolean(
                                    notificationKey,
                                    false
                            )
                    ) {

                        showNotification(
                                context,
                                name,
                                normalizedDate
                        );

                        prefs.edit()
                                .putBoolean(
                                        notificationKey,
                                        true
                                )
                                .apply();
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String normalizeDate(String date) {

        String[] formats = {
                "yyyy/MM/dd",
                "yyyy-MM-dd",
                "yyyy/MM/dd HH:mm",
                "yyyy-MM-dd HH:mm"
        };

        for (String format : formats) {

            try {

                SimpleDateFormat sdf =
                        new SimpleDateFormat(
                                format,
                                Locale.US
                        );

                sdf.setLenient(false);

                Date parsed =
                        sdf.parse(date);

                if (parsed != null) {

                    return new SimpleDateFormat(
                            "yyyy/MM/dd",
                            Locale.US
                    ).format(parsed);
                }

            } catch (Exception ignored) {
            }
        }

        return date.length() >= 10
                ? date.substring(0, 10)
                        .replace('-', '/')
                : date;
    }

    private void showNotification(
            Context context,
            String installmentName,
            String dueDate
    ) {

        Intent intent =
                new Intent(
                        context,
                        MainActivity.class
                );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        NOTIFICATION_ID,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                |
                        pendingIntentFlags()
                );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )

                .setSmallIcon(
                        android.R.drawable.ic_dialog_info
                )

                .setContentTitle(
                        "یادآوری قسط"
                )

                .setContentText(
                        "قسط «"
                                + installmentName
                                + "» دو روز دیگر سررسید می‌شود."
                )

                .setStyle(
                        new NotificationCompat.BigTextStyle()
                                .bigText(
                                        "قسط «"
                                                + installmentName
                                                + "»\n"
                                                + "تاریخ سررسید: "
                                                + dueDate
                                                + "\n\n"
                                                + "این یادآوری ۴۸ ساعت قبل، "
                                                + "رأس ساعت ۱۶:۰۰ است."
                                )
                )

                .setPriority(
                        NotificationCompat.PRIORITY_HIGH
                )

                .setAutoCancel(true)

                .setContentIntent(
                        pendingIntent
                );

        try {

            NotificationManagerCompat
                    .from(context)
                    .notify(
                            NOTIFICATION_ID,
                            builder.build()
                    );

        } catch (SecurityException e) {

            e.printStackTrace();
        }
    }

    private int pendingIntentFlags() {

        if (
                Build.VERSION.SDK_INT
                        >=
                Build.VERSION_CODES.M
        ) {

            return PendingIntent.FLAG_IMMUTABLE;
        }

        return 0;
    }

    private void createNotificationChannel(
            Context context
    ) {

        if (
                Build.VERSION.SDK_INT
                        >=
                Build.VERSION_CODES.O
        ) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "یادآوری اقساط",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "اعلان سررسید اقساط"
            );

            NotificationManager manager =
                    context.getSystemService(
                            NotificationManager.class
                    );

            if (manager != null) {

                manager.createNotificationChannel(
                        channel
                );
            }
        }
    }
}
