// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.miniapp

import android.app.AlertDialog
import android.content.Context
import network.mycelium.app.R
import org.json.JSONObject

private const val MAX_SINGLE_PAYMENT = 100_000_000L

internal interface MiniAppPaymentHost {
    fun paymentAddressFor(appId: String): String?
    fun walletConfirmedMuon(): Long
    fun sendPayment(toAddress: String, amountMuon: Long, memo: String?)
}

internal fun showMiniAppPaymentConfirmation(
    ctx: Context,
    appId: String,
    appDisplayName: String,
    result: JSONObject,
    host: MiniAppPaymentHost,
    resolveOk: (Any) -> Unit,
    resolveError: (String) -> Unit,
) {
    val amountMuon = result.optLong("amount_muon")
    val memo = result.optString("memo", "")
    val paymentAddress = host.paymentAddressFor(appId)

    if (paymentAddress.isNullOrBlank()) {
        resolveError("app has no payment address configured")
        return
    }
    if (amountMuon <= 0) {
        resolveError("invalid payment amount")
        return
    }
    if (amountMuon > MAX_SINGLE_PAYMENT) {
        resolveError("payment amount exceeds maximum allowed per transaction (100 MXC)")
        return
    }
    val balance = host.walletConfirmedMuon()
    if (balance < amountMuon) {
        resolveError("insufficient balance")
        return
    }

    val amountMxc = "%.4f MXC".format(amountMuon / 1_000_000.0)
    val addressPreview =
        if (paymentAddress.length > 16) {
            paymentAddress.take(16) + "…"
        } else {
            paymentAddress
        }
    val memoSuffix = if (memo.isNotBlank()) "\n\nMemo: $memo" else ""

    AlertDialog.Builder(ctx)
        .setTitle(ctx.getString(R.string.miniapp_payment_confirm_title))
        .setMessage(
            ctx.getString(
                R.string.miniapp_payment_confirm_message_detailed,
                appDisplayName,
                amountMxc,
                addressPreview,
                memoSuffix,
            ),
        )
        .setPositiveButton(R.string.miniapp_payment_confirm_yes) { _, _ ->
            try {
                host.sendPayment(paymentAddress, amountMuon, memo.ifBlank { null })
                resolveOk(
                    mapOf(
                        "status" to "submitted",
                        "amount_muon" to amountMuon,
                        "to" to paymentAddress,
                        "memo" to memo,
                    ),
                )
            } catch (e: Exception) {
                resolveError("payment failed: ${e.message}")
            }
        }
        .setNegativeButton(android.R.string.cancel) { _, _ ->
            resolveError("user cancelled payment")
        }
        .show()
}
