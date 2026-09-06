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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class NotificationReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID =
            "installment_notifications";

    private static final String PREFS_NAME =
            "installment_notifications";

    private static final String INSTALLMENTS_KEY =
            "installments";

    private static final String CHECKS_KEY =
            "checks_json";

    private static final String REMINDER_ENABLED =
            "reminder_enabled";

    private static final String REMINDER_DAYS =
            "reminder_days";

    private static final String REMINDER_TIME =
            "reminder_time";

    private static final String POSTED_NOTIFICATION_IDS =
            "posted_notification_ids";

    private static final String CHECK_ALARM_PREFIX =
            "check_alarm_";

    private static final int ALARM_REQUEST_CODE =
            7001;

    private static final int CHECK_REQUEST_BASE =
            810000;


    @Override
    public void onReceive(
            Context context,
            Intent intent
    ) {

        createNotificationChannel(context);

        if (intent == null) {
            scheduleNextAlarm(context);
            return;
        }

        String type =
                intent.getStringExtra("type");

        /*
         * اعلان اختصاصی چک
         */
        if ("check".equals(type)) {

            showCheckNotification(
                    context,
                    intent
            );

            return;
        }

        /*
         * اعلان قبلی اقساط
         */
        checkInstallments(context);

        /*
         * بعد از اجرای آلارم روزانه
         * آلارم بعدی ساخته می‌شود.
         */
        scheduleNextAlarm(context);
    }


    /* =========================================================
       CH CHECK REMINDERS
       ========================================================= */

    public static void syncCheckReminders(
            Context context,
            String json
    ) {

        Context app =
                context.getApplicationContext();

        createNotificationChannel(app);

        if (
                json == null ||
                json.trim().isEmpty()
        ) {

            json = "[]";
        }

        SharedPreferences prefs =
                app.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        prefs.edit()
                .putString(
                        CHECKS_KEY,
                        json
                )
                .apply();

        cancelAllCheckAlarms(app);

        try {

            JSONArray checks =
                    new JSONArray(json);

            for (
                    int i = 0;
                    i < checks.length();
                    i++
            ) {

                JSONObject check =
                        checks.optJSONObject(i);

                if (check == null) {
                    continue;
                }

                scheduleCheck(
                        app,
                        check
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private static void scheduleCheck(
            Context context,
            JSONObject check
    ) {

        try {

            String id =
                    check.optString(
                            "id",
                            ""
                    );

            if (id.isEmpty()) {
                return;
            }

            String status =
                    check.optString(
                            "status",
                            "issued"
                    );

            /*
             * فقط چک پرداخت‌نشده
             * باید اعلان داشته باشد.
             */
            if (!"issued".equals(status)) {
                return;
            }

            boolean enabled =
                    check.optBoolean(
                            "reminderEnabled",
                            true
                    );

            if (!enabled) {
                return;
            }

            String date =
                    check.optString(
                            "date",
                            ""
                    );

            if (date.isEmpty()) {
                return;
            }

            int daysBefore =
                    check.optInt(
                            "reminderDays",
                            1
                    );

            if (
                    daysBefore != 1 &&
                    daysBefore != 2
            ) {

                daysBefore = 1;
            }

            String time =
                    check.optString(
                            "reminderTime",
                            "09:00"
                    );

            Calendar due =
                    parseDate(date);

            if (due == null) {
                return;
            }

            Calendar trigger =
                    (Calendar) due.clone();

            trigger.add(
                    Calendar.DAY_OF_YEAR,
                    -daysBefore
            );

            int[] hm =
                    parseTime(time);

            trigger.set(
                    Calendar.HOUR_OF_DAY,
                    hm[0]
            );

            trigger.set(
                    Calendar.MINUTE,
                    hm[1]
            );

            trigger.set(
                    Calendar.SECOND,
                    0
            );

            trigger.set(
                    Calendar.MILLISECOND,
                    0
            );

            long triggerAt =
                    trigger.getTimeInMillis();

            /*
             * اگر زمان یادآوری گذشته باشد،
             * اعلان دیگر برای گذشته ساخته نمی‌شود.
             */
            if (
                    triggerAt <=
                    System.currentTimeMillis()
            ) {

                return;
            }

            int requestCode =
                    CHECK_REQUEST_BASE +
                    Math.abs(
                            id.hashCode()
                    ) % 100000;

            Intent intent =
                    new Intent(
                            context,
                            NotificationReceiver.class
                    );

            intent.putExtra(
                    "type",
                    "check"
            );

            intent.putExtra(
                    "checkId",
                    id
            );

            intent.putExtra(
                    "number",
                    check.optString(
                            "number",
                            ""
                    )
            );

            intent.putExtra(
                    "date",
                    date
            );

            intent.putExtra(
                    "amount",
                    check.optLong(
                            "amount",
                            0
                    )
            );

            intent.putExtra(
                    "payee",
                    check.optString(
                            "payee",
                            ""
                    )
            );

            intent.putExtra(
                    "daysBefore",
                    daysBefore
            );

            intent.putExtra(
                    "requestCode",
                    requestCode
            );

            PendingIntent pendingIntent =
                    PendingIntent.getBroadcast(
                            context,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT |
                            PendingIntent.FLAG_IMMUTABLE
                    );

            AlarmManager alarmManager =
                    (AlarmManager)
                            context.getSystemService(
                                    Context.ALARM_SERVICE
                            );

            if (alarmManager == null) {
                return;
            }

            if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S
            ) {

                if (
                        !alarmManager
                                .canScheduleExactAlarms()
                ) {

                    /*
                     * اگر آلارم دقیق مجاز نبود،
                     * آلارم معمولی استفاده می‌شود.
                     */

                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAt,
                            pendingIntent
                    );

                    return;
                }
            }

            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    private static Calendar parseDate(
            String date
    ) {

        try {

            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.US
                    );

            format.setLenient(false);

            Date parsed =
                    format.parse(date);

            if (parsed == null) {
                return null;
            }

            Calendar calendar =
                    Calendar.getInstance();

            calendar.setTime(parsed);

            calendar.set(
                    Calendar.HOUR_OF_DAY,
                    0
            );

            calendar.set(
                    Calendar.MINUTE,
                    0
            );

            calendar.set(
                    Calendar.SECOND,
                    0
            );

            calendar.set(
                    Calendar.MILLISECOND,
                    0
            );

            return calendar;

        } catch (Exception e) {

            return null;
        }
    }


    private static int[] parseTime(
            String time
    ) {

        int hour = 9;
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

            hour = 9;
            minute = 0;
        }

        if (hour < 0 || hour > 23) {
            hour = 9;
        }

        if (minute < 0 || minute > 59) {
            minute = 0;
        }

        return new int[]{
                hour,
                minute
        };
    }


    public static void cancelCheckReminder(
            Context context,
            String checkId
    ) {

        if (
                checkId == null ||
                checkId.isEmpty()
        ) {
            return;
        }

        AlarmManager alarmManager =
                (AlarmManager)
                        context.getSystemService(
                                Context.ALARM_SERVICE
                        );

        if (alarmManager == null) {
            return;
        }

        int requestCode =
                CHECK_REQUEST_BASE +
                Math.abs(
                        checkId.hashCode()
                ) % 100000;

        Intent intent =
                new Intent(
                        context,
                        NotificationReceiver.class
                );

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_NO_CREATE |
                        PendingIntent.FLAG_IMMUTABLE
                );

        if (pendingIntent != null) {

            alarmManager.cancel(
                    pendingIntent
            );

            pendingIntent.cancel();
        }

        NotificationManagerCompat
                .from(context)
                .cancel(
                        requestCode
                );
    }


    private static void cancelAllCheckAlarms(
            Context context
    ) {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        String json =
                prefs.getString(
                        CHECKS_KEY,
                        "[]"
                );

        try {

            JSONArray checks =
                    new JSONArray(json);

            for (
                    int i = 0;
                    i < checks.length();
                    i++
            ) {

                JSONObject check =
                        checks.optJSONObject(i);

                if (check == null) {
                    continue;
                }

                String id =
                        check.optString(
                                "id",
                                ""
                        );

                cancelCheckReminder(
                        context,
                        id
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    /* =========================================================
       CHECK NOTIFICATION
       ========================================================= */

    private void showCheckNotification(
            Context context,
            Intent intent
    ) {

        createNotificationChannel(context);

        if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                    context.checkSelfPermission(
                            "android.permission.POST_NOTIFICATIONS"
                    )
                    !=
                    PackageManager.PERMISSION_GRANTED
            ) {

                return;
            }
        }

        String number =
                intent.getStringExtra(
                        "number"
                );

        String date =
                intent.getStringExtra(
                        "date"
                );

        String payee =
                intent.getStringExtra(
                        "payee"
                );

        long amount =
                intent.getLongExtra(
                        "amount",
                        0
                );

        int daysBefore =
                intent.getIntExtra(
                        "daysBefore",
                        1
                );

        int requestCode =
                intent.getIntExtra(
                        "requestCode",
                        CHECK_REQUEST_BASE
                );

        Intent openIntent =
                new Intent(
                        context,
                        ChecksActivity.class
                );

        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        context,
                        requestCode,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
                );

        String beforeText;

        if (daysBefore == 2) {

            beforeText =
                    "۲ روز قبل";

        } else {

            beforeText =
                    "۱ روز قبل";
        }

        String title =
                "🔔 یادآوری چک";

        String shortText =
                "چک شماره "+
                number+
                " "+
                beforeText+
                " سررسید می‌شود.";

        String bigText =
                "چک شماره: "+
                number+
                "\n\n"+
                "تاریخ سررسید: "+
                formatDateForNotification(date)+
                "\n\n"+
                "مبلغ: "+
                formatNumber(amount)+
                " ریال"+
                "\n\n"+
                "در وجه: "+
                (
                        payee == null ||
                        payee.isEmpty()
                        ?
                        "—"
                        :
                        payee
                )+
                "\n\n"+
                "زمان یادآوری: "+
                beforeText;

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        context,
                        CHANNEL_ID
                )
                .setSmallIcon(
                        android.R.drawable.ic_dialog_info
                )
                .setContentTitle(
                        title
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
                            requestCode,
                            builder.build()
                    );

            addPostedNotificationId(
                    context,
                    requestCode
            );

        } catch (SecurityException e) {

            e.printStackTrace();
        }
    }


    /* =========================================================
       POSTED NOTIFICATIONS
       ========================================================= */

    private void addPostedNotificationId(
            Context context,
            int notificationId
    ) {

        SharedPreferences prefs =
                context.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );

        Set<String> ids =
                new HashSet<>(
                        prefs.getStringSet(
                                POSTED_NOTIFICATION_IDS,
                                new HashSet<>()
                        )
                );

        ids.add(
                String.valueOf(
                        notificationId
                )
        );

        prefs.edit()
                .putStringSet(
                        POSTED_NOTIFICATION_IDS,
                        ids
                )
                .apply();
    }


    public static void cancelAllPostedNotifications(
            Context context
    ) {

        try {

            SharedPreferences prefs =
                    context.getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE
                    );

            Set<String> ids =
                    prefs.getStringSet(
                            POSTED_NOTIFICATION_IDS,
                            new HashSet<>()
                    );

            NotificationManagerCompat manager =
                    NotificationManagerCompat
                            .from(context);

            for (String id : ids) {

                try {

                    manager.cancel(
                            Integer.parseInt(id)
                    );

                } catch (Exception ignored) {
                }
            }

            prefs.edit()
                    .remove(
                            POSTED_NOTIFICATION_IDS
                    )
                    .apply();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    /* =========================================================
       INSTALLMENT NOTIFICATIONS
       ========================================================= */

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
                            1
                    );

            if (
                    daysBefore != 1 &&
                    daysBefore != 2
            ) {

                daysBefore = 1;
            }

            String json =
                    prefs.getString(
                            INSTALLMENTS_KEY,
                            "[]"
                    );

            JSONArray installments =
                    new JSONArray(json);

            Calendar target =
                    Calendar.getInstance();

            target.add(
                    Calendar.DAY_OF_YEAR,
                    daysBefore
            );

            String targetDate =
                    getGregorianDate(
                            target
                    );

            for (
                    int i = 0;
                    i < installments.length();
                    i++
            ) {

                JSONObject item =
                        installments.optJSONObject(i);

                if (item == null) {
                    continue;
                }

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
                            dates.optJSONObject(j);

                    if (dateObject == null) {
                        continue;
                    }

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

                    if (
                            date.isEmpty()
                    ) {
                        continue;
                    }

                    /*
                     * سیستم قبلی اقساط ممکن است
                     * تاریخ شمسی داشته باشد.
                     *
                     * برای جلوگیری از خراب شدن
                     * قابلیت فعلی، منطق قبلی
                     * از NotificationScheduler
                     * همچنان مستقل باقی می‌ماند.
                     */
                }
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    /*
     * این متد همان API قبلی BootReceiver است.
     *
     * بنابراین بعد از روشن شدن گوشی:
     *
     * 1. آلارم روزانه اقساط ساخته می‌شود.
     * 2. یادآوری‌های چک دوباره ساخته می‌شوند.
     */
    public static void scheduleNextAlarm(
            Context context
    ) {

        try {

            SharedPreferences prefs =
                    context.getSharedPreferences(
                            PREFS_NAME,
                            Context.MODE_PRIVATE
                    );

            /*
             * اول یادآوری چک‌ها را بازیابی می‌کنیم.
             */
            String checksJson =
                    prefs.getString(
                            CHECKS_KEY,
                            "[]"
                    );

            try {

                JSONArray checks =
                        new JSONArray(
                                checksJson
                        );

                /*
                 * برای هر چک، آلارم را
                 * دوباره ایجاد می‌کنیم.
                 */
                for (
                        int i = 0;
                        i < checks.length();
                        i++
                ) {

                    JSONObject check =
                            checks.optJSONObject(i);

                    if (check != null) {

                        scheduleCheck(
                                context,
                                check
                        );
                    }
                }

            } catch (Exception ignored) {
            }


            /*
             * سیستم قبلی اقساط:
             *
             * تنظیم آلارم روزانه.
             */
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

            int[] hm =
                    parseTime(time);

            AlarmManager alarmManager =
                    (AlarmManager)
                            context.getSystemService(
                                    Context.ALARM_SERVICE
                            );

            if (alarmManager == null) {
                return;
            }

            Calendar now =
                    Calendar.getInstance();

            Calendar next =
                    Calendar.getInstance();

            next.set(
                    Calendar.HOUR_OF_DAY,
                    hm[0]
            );

            next.set(
                    Calendar.MINUTE,
                    hm[1]
            );

            next.set(
                    Calendar.SECOND,
                    0
            );

            next.set(
                    Calendar.MILLISECOND,
                    0
            );

            if (
                    next.getTimeInMillis()
                            <=
                    now.getTimeInMillis()
            ) {

                next.add(
                        Calendar.DAY_OF_YEAR,
                        1
                );
            }

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
                            PendingIntent.FLAG_UPDATE_CURRENT |
                            PendingIntent.FLAG_IMMUTABLE
                    );

            long triggerAt =
                    next.getTimeInMillis();

            if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S
            ) {

                if (
                        alarmManager
                                .canScheduleExactAlarms()
                ) {

                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAt,
                            pendingIntent
                    );

                } else {

                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAt,
                            pendingIntent
                    );
                }

            } else {

                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );
            }

        } catch (Exception e) {

            e.printStackTrace();
        }
    }


    /* =========================================================
       HELPERS
       ========================================================= */

    private static void createNotificationChannel(
            Context context
    ) {

        if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
        ) {

            NotificationManager manager =
                    (NotificationManager)
                            context.getSystemService(
                                    Context.NOTIFICATION_SERVICE
                            );

            if (manager == null) {
                return;
            }

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "یادآوری اقساط و چک‌ها",
                            NotificationManager
                                    .IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "یادآوری سررسید اقساط و چک‌ها"
            );

            manager.createNotificationChannel(
                    channel
            );
        }
    }


    private static String formatDateForNotification(
            String date
    ) {

        if (
                date == null ||
                date.length() < 10
        ) {
            return date;
        }

        String[] p =
                date.split("-");

        if (p.length == 3) {

            return p[2]+
                    " / "+
                    p[1]+
                    " / "+
                    p[0];
        }

        return date;
    }


    private static String formatNumber(
            long number
    ) {

        return String.format(
                Locale.US,
                "%,d",
                number
        );
    }


    private static String getGregorianDate(
            Calendar calendar
    ) {

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                );

        return format.format(
                calendar.getTime()
        );
    }
}
