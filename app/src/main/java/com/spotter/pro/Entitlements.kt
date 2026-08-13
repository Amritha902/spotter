package com.spotter.pro

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.models.StoreTransaction
import com.spotter.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this lifter has Spotter Pro.
 *
 * Two decisions are about where this app gets used rather than about billing:
 *
 * 1. **Entitlement is cached and trusted offline.** Gyms are basements. An app that locks a paid
 *    feature because it could not reach a server, while its owner is standing in front of a
 *    barbell, does not stay installed. The cache refreshes whenever the network allows.
 *
 * 2. **A missing key locks rather than crashes.** Anyone can clone this repo without credentials;
 *    billing then reports "not subscribed" and every coaching feature still runs.
 */
class Entitlements private constructor(private val prefs: SharedPreferences) {

    private val _isPro = MutableStateFlow(prefs.getBoolean(KEY_CACHED_PRO, false))

    /** Read from cache at construction, so the first frame after launch is already correct. */
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    val isConfigured: Boolean
        get() = BillingKey.isUsable(BuildConfig.REVENUECAT_KEY, BuildConfig.DEBUG)

    fun refresh() {
        if (!isConfigured) return
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) = update(customerInfo.isPro())
            override fun onError(error: PurchasesError) {
                // Staying with the cached answer is the point. A network failure is not evidence
                // that somebody stopped paying.
                Log.w(TAG, "Could not refresh entitlement, keeping cached: ${error.message}")
            }
        })
    }

    fun offerings(onLoaded: (Offerings?) -> Unit) {
        if (!isConfigured) return onLoaded(null)
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: Offerings) = onLoaded(offerings)
            override fun onError(error: PurchasesError) {
                Log.w(TAG, "Could not load offerings: ${error.message}")
                onLoaded(null)
            }
        })
    }

    /**
     * Starts a purchase.
     *
     * The result deliberately arrives through [update] via the SDK's own customer-info listener
     * rather than only through this callback. In a sibling project the purchase completed, the
     * money moved, and `onCompleted` never fired — leaving the paywall sitting on top of a
     * subscription the user had just paid for. Treating the listener as the source of truth makes
     * that specific humiliation impossible; this callback is only for reporting failure.
     */
    fun purchase(activity: Activity, pack: Package, onFailed: (String) -> Unit) {
        if (!isConfigured) return onFailed("Billing is not configured in this build.")

        Purchases.sharedInstance.purchase(
            PurchaseParams.Builder(activity, pack).build(),
            object : PurchaseCallback {
                override fun onCompleted(transaction: StoreTransaction, customerInfo: CustomerInfo) {
                    update(customerInfo.isPro())
                }

                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    // Cancelling is a choice, not a failure, and saying "purchase failed" to
                    // someone who changed their mind reads as a bug.
                    if (!userCancelled) onFailed(error.message)
                }
            },
        )
    }

    fun restore(onFailed: (String) -> Unit) {
        if (!isConfigured) return onFailed("Billing is not configured in this build.")
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) = update(customerInfo.isPro())
            override fun onError(error: PurchasesError) = onFailed(error.message)
        })
    }

    private fun update(pro: Boolean) {
        _isPro.value = pro
        prefs.edit().putBoolean(KEY_CACHED_PRO, pro).apply()
    }

    companion object {
        private const val TAG = "SpotterBilling"
        private const val PREFS = "spotter.pro"
        private const val KEY_CACHED_PRO = "cached_pro"

        /** Must match the entitlement identifier configured in the RevenueCat dashboard. */
        const val PRO_ENTITLEMENT = "pro"

        private fun CustomerInfo.isPro() = entitlements[PRO_ENTITLEMENT]?.isActive == true

        /**
         * Configures the SDK once per process, and hangs the durable listener off it.
         *
         * Safe to call with no key: it configures nothing and returns an instance that reports
         * "not subscribed" forever, which is what anyone building this repo without credentials
         * should get.
         */
        fun create(context: Context): Entitlements {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val entitlements = Entitlements(prefs)

            val key = BuildConfig.REVENUECAT_KEY
            BillingKey.warning(key, BuildConfig.DEBUG)?.let { Log.w(TAG, it) }

            if (!entitlements.isConfigured) return entitlements

            Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
            Purchases.configure(PurchasesConfiguration.Builder(context, key).build())

            // The durable path. Everything that can change an entitlement — a purchase, a restore,
            // a renewal, an expiry, a refund granted while the app was closed — arrives here.
            Purchases.sharedInstance.updatedCustomerInfoListener =
                com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener { info ->
                    entitlements.update(info.isPro())
                }

            entitlements.refresh()
            return entitlements
        }
    }
}
