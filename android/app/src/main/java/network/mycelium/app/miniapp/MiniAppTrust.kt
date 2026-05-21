package network.mycelium.app.miniapp

import android.content.Context
import network.mycelium.app.R

/** Maps FFI `trust_level` codes to localized install-trust labels. */
fun miniAppTrustLabel(context: Context, trustLevel: String?): String? {
    if (trustLevel.isNullOrBlank()) return null
    val resId =
        when (trustLevel) {
            "verified_listing" -> R.string.miniapp_trust_verified
            "matching_listing_hash" -> R.string.miniapp_trust_matching_unsigned
            "hash_mismatch" -> R.string.miniapp_trust_hash_mismatch
            "sideload_only" -> R.string.miniapp_trust_sideload
            else -> return trustLevel
        }
    return context.getString(resId)
}
