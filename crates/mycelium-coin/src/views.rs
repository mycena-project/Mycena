// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Consistency-tagged ledger views (ADR 006) — prevents Strong / pending / remote
//! balance from authorizing spends.

use crate::ledger::{AccountState, Transaction};
use mycelium_l0_facade::consistency::{
    ConsistencyModel, EventuallyConsistentModel, SettlementRequiredModel, StateView,
};

/// Balance snapshot at a specific consistency level.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AccountBalanceView<C: ConsistencyModel> {
    pub address: String,
    confirmed_muon: u64,
    pending_muon: u64,
    _level: StateView<C>,
}

impl AccountBalanceView<EventuallyConsistentModel> {
    pub const OPERATION: StateView<EventuallyConsistentModel> = StateView::new();

    pub fn from_account(state: &AccountState) -> Self {
        Self {
            address: state.address.clone(),
            confirmed_muon: state.balance_muon,
            pending_muon: state.pending_received,
            _level: StateView::new(),
        }
    }

    pub fn confirmed_muon(&self) -> u64 {
        self.confirmed_muon
    }

    pub fn pending_muon(&self) -> u64 {
        self.pending_muon
    }
}

impl AccountBalanceView<SettlementRequiredModel> {
    pub const OPERATION: StateView<SettlementRequiredModel> = StateView::new();

    pub fn from_settled_account(state: &AccountState) -> Self {
        Self {
            address: state.address.clone(),
            confirmed_muon: state.balance_muon,
            pending_muon: state.pending_received,
            _level: StateView::new(),
        }
    }

    /// Witness-settled balance only — excludes `pending_received`.
    pub fn spendable_muon(&self) -> u64 {
        self.confirmed_muon
    }
}

/// Remote peer balance response — hint only; must not authorize spends.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PeerBalanceHint {
    pub view: AccountBalanceView<EventuallyConsistentModel>,
}

impl PeerBalanceHint {
    pub fn new(address: String, confirmed_muon: u64, pending_muon: u64) -> Self {
        Self {
            view: AccountBalanceView {
                address,
                confirmed_muon,
                pending_muon,
                _level: StateView::new(),
            },
        }
    }
}

/// Opaque proof that a spend was checked against settled local balance for `from`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SpendAuthorization {
    pub(crate) from: String,
    pub(crate) total_muon: u64,
    _level: StateView<SettlementRequiredModel>,
}

impl SpendAuthorization {
    pub(crate) fn new(from: String, total_muon: u64) -> Self {
        Self {
            from,
            total_muon,
            _level: StateView::new(),
        }
    }

    pub fn matches_transaction(&self, tx: &Transaction) -> bool {
        self.from == tx.from
            && tx
                .amount_muon
                .checked_add(tx.fee_muon)
                .is_some_and(|total| total == self.total_muon)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct InsufficientSettledFunds {
    pub have_settled: u64,
    pub need: u64,
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::ledger::AccountState;

    #[test]
    fn settled_view_excludes_pending_for_spend() {
        let state = AccountState {
            address: "mxc1test".into(),
            balance_muon: 100,
            pending_received: 500,
            next_nonce: 0,
        };
        let settled = AccountBalanceView::<SettlementRequiredModel>::from_settled_account(&state);
        assert_eq!(settled.spendable_muon(), 100);
        let eventual = AccountBalanceView::<EventuallyConsistentModel>::from_account(&state);
        assert_eq!(eventual.pending_muon(), 500);
    }
}
