// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
use crate::ledger::Transaction;
use crate::refill::RefillRequest;
use crate::voucher::{PaymentVoucher, VoucherRedemption};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub enum CoinPayload {
    Transaction(Transaction),
    Witness {
        tx_id: String,
        peer_id: String,
    },
    BalanceQuery {
        address: String,
    },
    BalanceResponse {
        address: String,
        balance_muon: u64,
        pending_muon: u64,
        #[serde(default)]
        unsettled_voucher_muon: u64,
    },
    RefillRequest(RefillRequest),
    /// Issuer-signed offline payment promise.
    PaymentVoucher(PaymentVoucher),
    /// Redemption recorded by a recipient or relay (unsettled until merge).
    VoucherRedemption(VoucherRedemption),
    /// Relay broadcasts merged redemption set before settlement.
    VoucherSettlementSync {
        voucher_id: String,
        redemptions: Vec<VoucherRedemption>,
    },
}
