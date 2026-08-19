package com.mohammadgoudarzi.installments;

import android.content.Context;
import android.content.SharedPreferences;

public class PremiumManager {

    private static final String PREFS_NAME =
            "premium_settings";

    private static final String PREMIUM_KEY =
            "premium_enabled";

    /*
     * نسخه رایگان:
     * فقط یک قسط
     * فقط یک چک
     */
    public static final int FREE_INSTALLMENT_LIMIT = 1;
    public static final int FREE_CHECK_LIMIT = 1;

    private final SharedPreferences prefs;

    public PremiumManager(Context context) {

        prefs = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );
    }

    /*
     * آیا Premium فعال است؟
     */
    public boolean isPremium() {

        return prefs.getBoolean(
                PREMIUM_KEY,
                false
        );
    }

    /*
     * فعال کردن Premium
     */
    public void activatePremium() {

        prefs.edit()
                .putBoolean(
                        PREMIUM_KEY,
                        true
                )
                .apply();
    }

    /*
     * غیرفعال کردن Premium
     *
     * برای تست داخلی برنامه استفاده می‌شود.
     */
    public void deactivatePremium() {

        prefs.edit()
                .putBoolean(
                        PREMIUM_KEY,
                        false
                )
                .apply();
    }

    /*
     * نسخه رایگان اجازه ثبت قسط جدید دارد؟
     */
    public boolean canAddInstallment(
            int currentInstallmentCount
    ) {

        if (isPremium()) {
            return true;
        }

        return currentInstallmentCount
                < FREE_INSTALLMENT_LIMIT;
    }

    /*
     * نسخه رایگان اجازه ثبت چک جدید دارد؟
     */
    public boolean canAddCheck(
            int currentCheckCount
    ) {

        if (isPremium()) {
            return true;
        }

        return currentCheckCount
                < FREE_CHECK_LIMIT;
    }

    /*
     * آیا محدودیت اقساط پر شده؟
     */
    public boolean installmentLimitReached(
            int currentInstallmentCount
    ) {

        return !isPremium()
                &&
                currentInstallmentCount
                        >=
                FREE_INSTALLMENT_LIMIT;
    }

    /*
     * آیا محدودیت چک پر شده؟
     */
    public boolean checkLimitReached(
            int currentCheckCount
    ) {

        return !isPremium()
                &&
                currentCheckCount
                        >=
                FREE_CHECK_LIMIT;
    }

    /*
     * تعداد اقساط مجاز
     *
     * Premium = نامحدود
     * Free = یک عدد
     */
    public int getInstallmentLimit() {

        if (isPremium()) {
            return Integer.MAX_VALUE;
        }

        return FREE_INSTALLMENT_LIMIT;
    }

    /*
     * تعداد چک مجاز
     *
     * Premium = نامحدود
     * Free = یک عدد
     */
    public int getCheckLimit() {

        if (isPremium()) {
            return Integer.MAX_VALUE;
        }

        return FREE_CHECK_LIMIT;
    }
}
