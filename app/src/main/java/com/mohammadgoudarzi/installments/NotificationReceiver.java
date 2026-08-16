package com.mohammadgoudarzi.installments;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID =
            "installment_notifications";

    private static final String PREFS_NAME =
            "installment_notifications";

    private static final String INSTALLMENTS_KEY =
            "installments";

    private static final String REMINDER_ENABLED =
            "reminder_enabled";

    private static final String REMINDER_DAYS =
            "reminder_days";

    private static final String REMINDER_TIME =
            "reminder_time";

    private static final int ALARM_REQUEST_CODE = 7001;

    @Override
    public void onReceive(
            Context context,
            Intent intent
    ) {

        createNotificationChannel(context);

        checkInstallments(context);

        /*
         * خیلی مهم:
         *
         * این آلارم یک‌بار مصرف است.
         * بعد از اجرای آن، آلارم روز بعد
         * دوباره تنظیم می‌شود.
         */
        scheduleNextAlarm(context);
    }

    /*
     * بررسی اقساطی که امروز باید
     * برای آنها اعلان ارسال شود.
     */
    private void checkInstallments(
            Context context
    ) {

        try {

            SharedPreferences prefs =
                    context.getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE
                    );

            boolean enabled =
                    prefs.getBoolean(
                            REMINDER_ENABLED,
                            true
                    );

            if (!enabled) {
                return;
            }

            int daysBefore =
                    prefs.getInt(
                            REMINDER_DAYS,
                            2
                    );

            if (
                    daysBefore != 1
                            &&
                    daysBefore != 2
            ) {
                daysBefore = 2;
            }

            String json =
                    prefs.getString(
                            INSTALLMENTS_KEY,
                            "[]"
                    );

            JSONArray installments =
                    new JSONArray(json);

            /*
             * امروز + تعداد روز انتخابی
             *
             * مثال:
             *
             * امروز ۱۰
             * ۱ روز قبل → دنبال قسط روز ۱۱
             * ۲ روز قبل → دنبال قسط روز ۱۲
             */
            Calendar target =
                    Calendar.getInstance();

            target.add(
                    Calendar.DAY_OF_YEAR,
                    daysBefore
            );

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

            for (
                    int i = 0;
                    i < installments.length();
                    i++
            ) {

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

                if (dates == null) {
                    continue;
                }

                for (
                        int j = 0;
                        j < dates.length();
                        j++
                ) {

                    JSONObject dateObject =
                            dates.getJSONObject(j);

                    /*
                     * قسط پرداخت شده
                     * نباید اعلان بدهد.
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

                    if (date.isEmpty()) {
                        continue;
                    }

                    String normalizedDate =
                            normalizeJalaliDate(
                                    date
                            );

                    /*
                     * فقط قسطی که دقیقاً
                     * به اندازه daysBefore
                     * روز دیگر سررسید دارد.
                     */
                    if (
                            !targetDate.equals(
                                    normalizedDate
                            )
                    ) {
                        continue;
                    }

                    /*
                     * جلوگیری از اعلان تکراری
                     */
                    String notificationKey =
                            "notified_"
                                    + itemId
                                    + "_"
                                    + normalizedDate
                                    + "_"
                                    + j
                                    + "_"
                                    + daysBefore;

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
                                    j,
                                    normalizedDate
                            );

                    showNotification(
                            context,
                            name,
                            normalizedDate,
                            daysBefore,
                            notificationId
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

    /*
     * ارسال Notification
     */
    private void showNotification(
            Context context,
            String installmentName,
            String dueDate,
            int daysBefore,
            int notificationId
    ) {

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                    context.checkSelfPermission(
                            "android.permission.POST_NOTIFICATIONS"
                    )
                            != PackageManager.PERMISSION_GRANTED
            ) {
                return;
            }
        }

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

        String daysText;

        if (daysBefore == 1) {
            daysText = "۱ روز قبل";
        } else {
            daysText = "۲ روز قبل";
        }

        String shortText =
                "قسط «"
                        + installmentName
                        + "» "
                        + daysText
                        + " سررسید می‌شود.";

        String bigText =
                "قسط «"
                        + installmentName
                        + "»\n\n"
                        + "تاریخ سررسید: "
                        + dueDate
                        + "\n\n"
                        + "زمان یادآوری: "
                        + daysText
                        + "\n"
                        + "یادآوری در ساعت انتخاب‌شده شما.";

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )

                .setSmallIcon(
                        android.R.drawable.ic_dialog_info
                )

                .setContentTitle(
                        "🔔 یادآوری قسط"
                )

                .setContentText(
                        shortText
                )

                .setStyle(
                        new NotificationCompat
                                .BigTextStyle()
                                .bigText(
                                        bigText
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

    /*
     * تنظیم آلارم روز بعد
     *
     * ساعت دقیقاً از تنظیمات کاربر
     * خوانده می‌شود.
     */
    private void scheduleNextAlarm(
            Context context
    ) {

        try {

            SharedPreferences prefs =
                    context.getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE
                    );

            boolean enabled =
                    prefs.getBoolean(
                            REMINDER_ENABLED,
                            true
                    );

            if (!enabled) {
                return;
            }

            String time =
                    prefs.getString(
                            REMINDER_TIME,
                            "16:00"
                    );

            int hour = 16;
            int minute = 0;

            try {

                String[] parts =
                        time.split(":");

                if (parts.length >= 2) {

                    hour =
                            Integer.parseInt(
                                    parts[0]
                            );

                    minute =
                            Integer.parseInt(
                                    parts[1]
                            );
                }

            } catch (Exception ignored) {

                hour = 16;
                minute = 0;
            }

            if (hour < 0 || hour > 23) {
                hour = 16;
            }

            if (minute < 0 || minute > 59) {
                minute = 0;
            }

            AlarmManager alarmManager =
                    (AlarmManager)
                            context.getSystemService(
                                    Context.ALARM_SERVICE
                            );

            if (alarmManager == null) {
                return;
            }

            /*
             * Android 12+
             */
            if (
                    Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.S
            ) {

                if (
                        !alarmManager
                                .canScheduleExactAlarms()
                ) {
                    return;
                }
            }

            Calendar next =
                    Calendar.getInstance();

            /*
             * همیشه روز بعد
             */
            next.add(
                    Calendar.DAY_OF_YEAR,
                    1
            );

            next.set(
                    Calendar.HOUR_OF_DAY,
                    hour
            );

            next.set(
                    Calendar.MINUTE,
                    minute
            );

            next.set(
                    Calendar.SECOND,
                    0
            );

            next.set(
                    Calendar.MILLISECOND,
                    0
            );

            Intent intent =
                    new Intent(
                            context,
                            NotificationReceiver.class
                    );

            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            context,
                            ALARM_REQUEST_CODE,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT
                                    |
                            pendingIntentFlags()
                    );

            long triggerAt =
                    next.getTimeInMillis();

            if (
                    Build.VERSION.SDK_INT >=
                            Build.VERSION_CODES.M
            ) {

                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );

            } else {

                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );
            }

        } catch (SecurityException e) {

            e.printStackTrace();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    private int createNotificationId(
            String itemId,
            int index,
            String date
    ) {

        int hash =
                (
                        itemId
                                + "_"
                                + index
                                + "_"
                                + date
                ).hashCode();

        return Math.abs(hash);
    }

    private int pendingIntentFlags() {

        if (
                Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.M
        ) {

            return PendingIntent.FLAG_IMMUTABLE;
        }

        return 0;
    }

    /*
     * ساخت Notification Channel
     */
    private void createNotificationChannel(
            Context context
    ) {

        if (
                Build.VERSION.SDK_INT >=
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

        for (
                int i = 1;
                i < gm;
                i++
        ) {

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

        if (date == null) {
            return "";
        }

        String value =
                date.trim()
                        .replace(
                                '-',
                                '/'
                        );

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
