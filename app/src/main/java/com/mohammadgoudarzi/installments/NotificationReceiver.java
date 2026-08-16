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

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID =
            "installment_notifications";

    private static final String PREFS_NAME =
            "installment_notifications";

    private static final String INSTALLMENTS_KEY =
            "installments";

    @Override
    public void onReceive(Context context, Intent intent) {

        createNotificationChannel(context);

        checkInstallments(context);
    }

    private void checkInstallments(Context context) {

        try {

            android.content.SharedPreferences prefs =
                    context.getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE
                    );

            String json =
                    prefs.getString(
                            INSTALLMENTS_KEY,
                            "[]"
                    );

            JSONArray installments =
                    new JSONArray(json);

            Calendar today =
                    Calendar.getInstance();

            /*
             * دو روز بعد
             */
            Calendar target =
                    (Calendar) today.clone();

            target.add(
                    Calendar.DAY_OF_YEAR,
                    2
            );

            /*
             * تبدیل تاریخ میلادی امروز به شمسی
             */
            int[] jalaliTarget =
                    gregorianToJalali(
                            target.get(Calendar.YEAR),
                            target.get(Calendar.MONTH) + 1,
                            target.get(Calendar.DAY_OF_MONTH)
                    );

            String targetDate =
                    formatJalaliDate(
                            jalaliTarget[0],
                            jalaliTarget[1],
                            jalaliTarget[2]
                    );

            Set<String> notifiedKeys =
                    new HashSet<>();

            for (int i = 0;
                 i < installments.length();
                 i++) {

                JSONObject item =
                        installments.getJSONObject(i);

                String name =
                        item.optString(
                                "name",
                                "قسط"
                        );

                String itemId =
                        item.optString(
                                "id",
                                String.valueOf(i)
                        );

                JSONArray dates =
                        item.optJSONArray(
                                "dates"
                        );

                if (dates == null)
                    continue;

                for (int j = 0;
                     j < dates.length();
                     j++) {

                    JSONObject dateObject =
                            dates.getJSONObject(j);

                    /*
                     * اگر قسط پرداخت شده باشد
                     * اعلان نده
                     */
                    if (
                            dateObject.optBoolean(
                                    "paid",
                                    false
                            )
                    ) {
                        continue;
                    }

                    String date =
                            dateObject.optString(
                                    "date",
                                    ""
                            );

                    if (date.isEmpty())
                        continue;

                    String normalizedDate =
                            normalizeJalaliDate(date);

                    if (
                            !targetDate.equals(
                                    normalizedDate
                            )
                    ) {
                        continue;
                    }

                    /*
                     * کلید یکتا برای جلوگیری
                     * از اعلان تکراری
                     */
                    String notificationKey =
                            "notified_"
                                    + itemId
                                    + "_"
                                    + normalizedDate
                                    + "_"
                                    + j;

                    if (
                            notifiedKeys.contains(
                                    notificationKey
                            )
                    ) {
                        continue;
                    }

                    if (
                            prefs.getBoolean(
                                    notificationKey,
                                    false
                            )
                    ) {
                        continue;
                    }

                    int notificationId =
                            createNotificationId(
                                    itemId,
                                    j
                            );

                    showNotification(
                            context,
                            name,
                            normalizedDate,
                            notificationId
                    );

                    notifiedKeys.add(
                            notificationKey
                    );

                    prefs.edit()
                            .putBoolean(
                                    notificationKey,
                                    true
                            )
                            .apply();
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private void showNotification(
            Context context,
            String installmentName,
            String dueDate,
            int notificationId
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
                        notificationId,
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
                        new NotificationCompat
                                .BigTextStyle()
                                .bigText(
                                        "قسط «"
                                                + installmentName
                                                + "»\n\n"
                                                + "تاریخ سررسید: "
                                                + dueDate
                                                + "\n\n"
                                                + "این قسط ۴۸ ساعت قبل "
                                                + "یادآوری می‌شود.\n"
                                                + "زمان یادآوری: ساعت ۱۶:۰۰"
                                )
                )

                .setPriority(
                        NotificationCompat
                                .PRIORITY_HIGH
                )

                .setAutoCancel(true)

                .setContentIntent(
                        pendingIntent
                );

        try {

            NotificationManagerCompat
                    .from(context)
                    .notify(
                            notificationId,
                            builder.build()
                    );

        } catch (SecurityException e) {

            e.printStackTrace();
        }
    }

    private int createNotificationId(
            String itemId,
            int index
    ) {

        int hash =
                (itemId + "_" + index)
                        .hashCode();

        return Math.abs(hash);
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
                            NotificationManager
                                    .IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "اعلان سررسید اقساط"
            );

            channel.enableVibration(true);

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

    /*
     * ==============================
     * تبدیل میلادی به شمسی
     * ==============================
     */

    private int[] gregorianToJalali(
            int gy,
            int gm,
            int gd
    ) {

        int jy;

        if (gy > 1600) {

            jy = 979;
            gy -= 1600;

        } else {

            jy = 0;
            gy -= 621;
        }

        int gy2 =
                gm > 2
                        ? gy + 1
                        : gy;

        int[] monthDays = {
                0,
                31,
                28,
                31,
                30,
                31,
                30,
                31,
                31,
                30,
                31,
                30,
                31
        };

        int days =
                365 * gy
                        + (gy2 + 3) / 4
                        - (gy2 + 99) / 100
                        + (gy2 + 399) / 400
                        - 80
                        + gd;

        for (int i = 1; i < gm; i++) {

            days += monthDays[i];
        }

        jy +=
                33 * (days / 12053);

        days %= 12053;

        jy +=
                4 * (days / 1461);

        days %= 1461;

        if (days > 365) {

            jy +=
                    (days - 1) / 365;

            days =
                    (days - 1) % 365;
        }

        int jm;

        if (days < 186) {

            jm =
                    1 + days / 31;

        } else {

            jm =
                    7
                            + (days - 186)
                            / 30;
        }

        int jd;

        if (days < 186) {

            jd =
                    1 + days % 31;

        } else {

            jd =
                    1
                            + (days - 186)
                            % 30;
        }

        return new int[]{
                jy,
                jm,
                jd
        };
    }

    private String formatJalaliDate(
            int year,
            int month,
            int day
    ) {

        return String.format(
                java.util.Locale.US,
                "%04d/%02d/%02d",
                year,
                month,
                day
        );
    }

    private String normalizeJalaliDate(
            String date
    ) {

        if (date == null)
            return "";

        String value =
                date.trim()
                        .replace('-', '/');

        if (value.length() >= 10) {

            value =
                    value.substring(
                            0,
                            10
                    );
        }

        return value;
    }
}
