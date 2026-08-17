package com.mohammadgoudarzi.installments;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;

import java.util.Collections;
import java.util.List;

public class BillingManager {

    public static final String PREMIUM_PRODUCT_ID =
            "premium_lifetime";

    private final Context context;

    private final PremiumManager premiumManager;

    private BillingClient billingClient;

    private ProductDetails premiumProduct;

    public BillingManager(Context context) {

        this.context =
                context.getApplicationContext();

        premiumManager =
                new PremiumManager(
                        this.context
                );

        billingClient =
                BillingClient.newBuilder(
                        this.context
                )
                        .setListener(
                                this::handlePurchases
                        )
                        .enablePendingPurchases()
                        .build();
    }

    /*
     * اتصال به Google Play
     */
    public void startConnection() {

        if (billingClient.isReady()) {

            queryPremiumProduct();

            return;
        }

        billingClient.startConnection(
                new BillingClientStateListener() {

                    @Override
                    public void onBillingSetupFinished(
                            @NonNull BillingResult billingResult
                    ) {

                        if (
                                billingResult.getResponseCode()
                                        ==
                                BillingClient.BillingResponseCode.OK
                        ) {

                            queryPremiumProduct();

                        }
                    }

                    @Override
                    public void onBillingServiceDisconnected() {

                        // دفعه بعد دوباره تلاش می‌کنیم.
                    }
                }
        );
    }

    /*
     * پیدا کردن محصول Premium
     */
    private void queryPremiumProduct() {

        QueryProductDetailsParams.Product product =
                QueryProductDetailsParams.Product
                        .newBuilder()
                        .setProductId(
                                PREMIUM_PRODUCT_ID
                        )
                        .setProductType(
                                BillingClient.ProductType.INAPP
                        )
                        .build();

        QueryProductDetailsParams params =
                QueryProductDetailsParams
                        .newBuilder()
                        .setProductList(
                                Collections.singletonList(
                                        product
                                )
                        )
                        .build();

        billingClient.queryProductDetailsAsync(
                params,
                (
                        billingResult,
                        productDetailsResult
                ) -> {

                    if (
                            billingResult.getResponseCode()
                                    !=
                            BillingClient.BillingResponseCode.OK
                    ) {
                        return;
                    }

                    List<ProductDetails> products =
                            productDetailsResult
                                    .getProductDetailsList();

                    if (
                            products == null ||
                            products.isEmpty()
                    ) {
                        return;
                    }

                    premiumProduct =
                            products.get(0);
                }
        );
    }

    /*
     * شروع خرید Premium
     */
    public void purchasePremium(
            Activity activity
    ) {

        if (premiumManager.isPremium()) {

            return;
        }

        if (!billingClient.isReady()) {

            startConnection();

            return;
        }

        if (premiumProduct == null) {

            queryPremiumProduct();

            return;
        }

        BillingFlowParams.ProductDetailsParams
                productDetailsParams =
                BillingFlowParams
                        .ProductDetailsParams
                        .newBuilder()
                        .setProductDetails(
                                premiumProduct
                        )
                        .build();

        BillingFlowParams billingFlowParams =
                BillingFlowParams
                        .newBuilder()
                        .setProductDetailsParamsList(
                                Collections.singletonList(
                                        productDetailsParams
                                )
                        )
                        .build();

        billingClient.launchBillingFlow(
                activity,
                billingFlowParams
        );
    }

    /*
     * بررسی نتیجه خرید
     */
    private void handlePurchases(
            BillingResult billingResult,
            List<Purchase> purchases
    ) {

        if (
                billingResult.getResponseCode()
                        !=
                BillingClient.BillingResponseCode.OK
        ) {
            return;
        }

        if (purchases == null) {
            return;
        }

        for (Purchase purchase : purchases) {

            if (
                    !purchase.getProducts()
                            .contains(
                                    PREMIUM_PRODUCT_ID
                            )
            ) {
                continue;
            }

            if (
                    purchase.getPurchaseState()
                            !=
                    Purchase.PurchaseState.PURCHASED
            ) {
                continue;
            }

            acknowledgePurchase(
                    purchase
            );
        }
    }

    /*
     * تأیید خرید و فعال کردن Premium
     */
    private void acknowledgePurchase(
            Purchase purchase
    ) {

        if (purchase.isAcknowledged()) {

            premiumManager.activate(
                    purchase.getPurchaseToken()
            );

            return;
        }

        AcknowledgePurchaseParams params =
                AcknowledgePurchaseParams
                        .newBuilder()
                        .setPurchaseToken(
                                purchase.getPurchaseToken()
                        )
                        .build();

        billingClient.acknowledgePurchase(
                params,
                billingResult -> {

                    if (
                            billingResult.getResponseCode()
                                    ==
                            BillingClient.BillingResponseCode.OK
                    ) {

                        premiumManager.activate(
                                purchase.getPurchaseToken()
                        );
                    }
                }
        );
    }

    /*
     * بازیابی خرید قبلی
     */
    public void restorePurchases() {

        if (!billingClient.isReady()) {

            startConnection();

            return;
        }

        billingClient.queryPurchasesAsync(
                BillingClient.QueryPurchasesParams
                        .newBuilder()
                        .setProductType(
                                BillingClient.ProductType.INAPP
                        )
                        .build(),
                (
                        billingResult,
                        purchases
                ) -> {

                    if (
                            billingResult.getResponseCode()
                                    !=
                            BillingClient.BillingResponseCode.OK
                    ) {
                        return;
                    }

                    handlePurchases(
                            billingResult,
                            purchases
                    );
                }
        );
    }

    public boolean isPremium() {

        return premiumManager.isPremium();
    }

    public void destroy() {

        if (billingClient != null) {

            billingClient.endConnection();
        }
    }
}
