package com.mohammadgoudarzi.installments;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public final class NotificationScheduler {

    private static final String PREFS = "installment_notifications";
    private static final String DATA_KEY = "installments_json";
    private static final String CHANNEL_ID = "installment_due_channel";
    private static final int BASE_REQUEST_CODE = 52000;

    private NotificationScheduler() {}

    public static void initialize(Context context) {

        Context app = context.getApplicationContext();

        createChannel(app);

        String json =
                app.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                ).getString(
                        DATA_KEY,
                        "[]"
                );

        sync(app, json);
    }

    public static void sync(
            Context context,
            String json
    ) {

        Context app =
                context.getApplicationContext();

        createChannel(app);

        if (
                json == null ||
                json.trim().isEmpty()
        ) {
            json = "[]";
        }

        app.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        )
        .edit()
        .putString(
                DATA_KEY,
                json
        )
        .apply();

        AlarmManager alarmManager =
                (AlarmManager)
                        app.getSystemService(
                                Context.ALARM_SERVICE
                        );

        if (alarmManager == null)
            return;

        Set<Integer> activeIds =
                new HashSet<>();

        try {

            JSONArray installments =
                    new JSONArray(json);

            for (
                    int i = 0;
                    i < installments.length();
                    i++
            ) {

                JSONObject item =
                        installments.optJSONObject(i);

                if (item == null)
                    continue;

                String name =
                        item.optString(
                                "name",
                                "قسط"
                        );

                JSONArray dates =
                        item.optJSONArray("dates");

                if (dates == null)
                    continue;

                for (
                        int j = 0;
                        j < dates.length();
                        j++
                ) {

                    JSONObject dateItem =
                            dates.optJSONObject(j);

                    if (dateItem == null)
                        continue;

                    if (
                            dateItem.optBoolean(
                                    "paid",
                                    false
                            )
                    ) {
                        continue;
                    }

                    String jalali =
                            dateItem
                                    .optString(
                                            "date",
                                            ""
                                    )
                                    .trim();

                    if (
                            !isValidJalali(
                                    jalali
                            )
                    ) {
                        continue;
                    }

                    int requestCode =
                            requestCodeFor(
                                    i,
                                    j,
                                    jalali
                            );

                    activeIds.add(
                            requestCode
                    );

                    long triggerAt =
                            notificationTimeFor(
                                    jalali
                            );

                    if (
                            triggerAt <=
                            System.currentTimeMillis()
                    ) {
                        continue;
                    }

                    Intent intent =
                            new Intent(
                                    app,
                                    InstallmentNotificationReceiver.class
                            );

                    intent.putExtra(
                            "name",
                            name
                    );

                    intent.putExtra(
                            "date",
                            jalali
                    );

                    intent.putExtra(
                            "requestCode",
                            requestCode
                    );

                    PendingIntent pendingIntent =
                            PendingIntent.getBroadcast(
                                    app,
                                    requestCode,
                                    intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT
                                            |
                                    PendingIntent.FLAG_IMMUTABLE
                            );

                    if (
                            canUseExactAlarm(
                                    alarmManager
                            )
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
                }
            }

        } catch (Exception ignored) {
        }

        cancelOldAlarms(
                app,
                activeIds
        );
    }

    private static void cancelOldAlarms(
            Context context,
            Set<Integer> activeIds
    ) {

        AlarmManager alarmManager =
                (AlarmManager)
                        context.getSystemService(
                                Context.ALARM_SERVICE
                        );

        if (alarmManager == null)
            return;

        String stored =
                context
                        .getSharedPreferences(
                                PREFS,
                                Context.MODE_PRIVATE
                        )
                        .getString(
                                "scheduled_ids",
                                ""
                        );

        if (
                stored != null &&
                !stored.isEmpty()
        ) {

            String[] parts =
                    stored.split(",");

            for (String part : parts) {

                try {

                    int id =
                            Integer.parseInt(
                                    part
                            );

                    if (
                            !activeIds.contains(id)
                    ) {

                        Intent intent =
                                new Intent(
                                        context,
                                        InstallmentNotificationReceiver.class
                                );

                        PendingIntent pi =
                                PendingIntent.getBroadcast(
                                        context,
                                        id,
                                        intent,
                                        PendingIntent.FLAG_NO_CREATE
                                                |
                                        PendingIntent.FLAG_IMMUTABLE
                                );

                        if (pi != null) {

                            alarmManager.cancel(pi);

                            pi.cancel();
                        }
                    }

                } catch (Exception ignored) {
                }
            }
        }

        StringBuilder ids =
                new StringBuilder();

        for (Integer id : activeIds) {

            if (ids.length() > 0)
                ids.append(',');

            ids.append(id);
        }

        context
                .getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                        "scheduled_ids",
                        ids.toString()
                )
                .apply();
    }

    private static boolean canUseExactAlarm(
            AlarmManager alarmManager
    ) {

        if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S
        ) {

            return alarmManager
                    .canScheduleExactAlarms();
        }

        return true;
    }

    private static void createChannel(
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
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "یادآوری ۴۸ ساعت قبل از سررسید اقساط"
            );

            NotificationManager manager =
                    (NotificationManager)
                            context.getSystemService(
                                    Context.NOTIFICATION_SERVICE
                            );

            if (manager != null)
                manager.createNotificationChannel(
                        channel
                );
        }
    }

    public static String channelId() {
        return CHANNEL_ID;
    }

    private static int requestCodeFor(
            int itemIndex,
            int dateIndex,
            String date
    ) {

        int hash =
                date.hashCode();

        return BASE_REQUEST_CODE
                +
                Math.abs(
                        (itemIndex * 100000)
                        +
                        (dateIndex * 1000)
                        +
                        hash % 1000
                );
    }

    private static long notificationTimeFor(
            String jalali
    ) {

        int[] g =
                jalaliToGregorian(
                        jalali
                );

        if (g == null)
            return -1;

        Calendar cal =
                Calendar.getInstance();

        cal.clear();

        cal.set(
                Calendar.YEAR,
                g[0]
        );

        cal.set(
                Calendar.MONTH,
                g[1] - 1
        );

        cal.set(
                Calendar.DAY_OF_MONTH,
                g[2]
        );

        /*
         * اعلان ساعت ۹ صبح،
         * دقیقاً ۴۸ ساعت قبل از سررسید.
         */
        cal.set(
                Calendar.HOUR_OF_DAY,
                9
        );

        cal.set(
                Calendar.MINUTE,
                0
        );

        cal.set(
                Calendar.SECOND,
                0
        );

        cal.set(
                Calendar.MILLISECOND,
                0
        );

        cal.add(
                Calendar.HOUR_OF_DAY,
                -48
        );

        return cal.getTimeInMillis();
    }

    private static boolean isValidJalali(
            String value
    ) {

        if (
                value == null ||
                !value.matches(
                        "\\d{4}/\\d{1,2}/\\d{1,2}"
                )
        ) {
            return false;
        }

        return jalaliToGregorian(
                value
        ) != null;
    }

    /*
     * تبدیل تاریخ شمسی به میلادی
     */
    private static int[] jalaliToGregorian(
            String value
    ) {

        try {

            String[] p =
                    value.split("/");

            int jy =
                    Integer.parseInt(p[0]);

            int jm =
                    Integer.parseInt(p[1]);

            int jd =
                    Integer.parseInt(p[2]);

            if (
                    jy < 1200 ||
                    jy > 1600 ||
                    jm < 1 ||
                    jm > 12 ||
                    jd < 1 ||
                    jd > 31
            ) {
                return null;
            }

            jy -= 979;

            int days =
                    365 * jy
                    +
                    (jy / 33) * 8
                    +
                    ((jy % 33) + 3) / 4
                    +
                    78
                    +
                    jd;

            if (jm < 7) {

                days +=
                        31 * (jm - 1);

            } else {

                days +=
                        30 * (jm - 1)
                        +
                        6;
            }

            int gy =
                    1600
                    +
                    400 *
                    (days / 146097);

            days %=
                    146097;

            if (days >= 36525) {

                days--;

                gy +=
                        100 *
                        (days / 36524);

                days %=
                        36524;

                if (days >= 365)
                    days++;
            }

            gy +=
                    4 *
                    (days / 1461);

            days %=
                    1461;

            if (days >= 366) {

                days--;

                gy +=
                        days / 365;

                days %=
                        365;
            }

            int[] monthDays = {
                    31, 28, 31, 30,
                    31, 30, 31, 31,
                    30, 31, 30, 31
            };

            boolean leap =
                    (
                            gy % 4 == 0 &&
                            gy % 100 != 0
                    )
                    ||
                    gy % 400 == 0;

            if (leap)
                monthDays[1] = 29;

            int gm = 0;

            while (
                    gm < 12 &&
                    days >= monthDays[gm]
            ) {

                days -=
                        monthDays[gm];

                gm++;
            }

            return new int[]{
                    gy,
                    gm + 1,
                    days + 1
            };

        } catch (Exception e) {

            return null;
        }
    }
}
