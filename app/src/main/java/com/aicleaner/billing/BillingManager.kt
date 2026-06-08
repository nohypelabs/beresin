package com.aicleaner.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams

class BillingManager(
    context: Context,
    private val onPremiumToken: (String) -> Unit,
    private val onStatus: (String) -> Unit = {}
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        private const val PREMIUM_PRODUCT_ID = "beresin_premium_monthly"
    }

    private var premiumProduct: ProductDetails? = null

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (billingClient.isReady) return

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPremiumProduct()
                } else {
                    onStatus("Billing belum siap: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                onStatus("Billing service disconnect. Coba lagi nanti.")
            }
        })
    }

    fun launchPremiumPurchase(activity: Activity) {
        val product = premiumProduct
        if (product == null) {
            onStatus("Product premium belum kebaca. Pastikan product ID ada di Play Console.")
            start()
            return
        }

        val offerToken = product.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken

        if (offerToken.isNullOrBlank()) {
            onStatus("Offer premium belum tersedia untuk device ini.")
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty()
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { purchase ->
                        onPremiumToken(purchase.purchaseToken)
                    }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> onStatus("Pembelian dibatalkan.")
            else -> onStatus("Billing error: ${result.debugMessage}")
        }
    }

    fun release() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    private fun queryPremiumProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PREMIUM_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onStatus("Gagal load premium product: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }

            premiumProduct = productDetailsResult.productDetailsList.firstOrNull()
            if (premiumProduct == null) {
                Log.w(TAG, "Premium product not found: $PREMIUM_PRODUCT_ID")
                onStatus("Product premium belum ditemukan di Play Console.")
            }
        }
    }
}
