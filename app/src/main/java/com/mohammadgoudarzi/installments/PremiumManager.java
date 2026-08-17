package com.mohammadgoudarzi.installments;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

public class PremiumManager {

    private static final String PREFS = "premium_state";

    private static final String KEY_ACTIVE =
            "premium_active";

    private static final String KEY_TOKEN =
            "premium_purchase_token";

    private static final String KEY_ACCOUNT_ID =
            "stable_account_id";

    private final SharedPreferences prefs;

    public PremiumManager(Context context) {

        prefs =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );
    }

    public boolean isPremium() {

        return prefs.getBoolean(
                KEY_ACTIVE,
                false
        );
    }

    public void activate(
            String purchaseToken
    ) {

        prefs.edit()
                .putBoolean(
                        KEY_ACTIVE,
                        true
                )
                .putString(
                        KEY_TOKEN,
                        purchaseToken == null
                                ? ""
                                : purchaseToken
                )
                .apply();
    }

    public String getPurchaseToken() {

        return prefs.getString(
                KEY_TOKEN,
                ""
        );
    }

    public String getStableAccountId() {

        String id =
                prefs.getString(
                        KEY_ACCOUNT_ID,
                        null
                );

        if (
                id == null ||
                id.trim().isEmpty()
        ) {

            id =
                    UUID.randomUUID()
                            .toString()
                            .replace(
                                    "-",
                                    ""
                            );

            prefs.edit()
                    .putString(
                            KEY_ACCOUNT_ID,
                            id
                    )
                    .apply();
        }

        return id;
    }
}
