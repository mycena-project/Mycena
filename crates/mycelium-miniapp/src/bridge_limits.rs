// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Bridge call rate, execution time, and chain-budget limits (Cell C2 / H08).

use std::collections::HashMap;
use std::sync::{LazyLock, Mutex};

use mycelium_l0_facade::data::now_ms;

/// Sliding window for call count (per app).
pub const WINDOW_MS: u64 = 60_000;
/// Max bridge invocations per minute per app.
pub const MAX_CALLS_PER_MINUTE: u32 = 120;

/// Max wall time for a single native bridge handler (Rust async work).
pub const BRIDGE_CALL_TIMEOUT_MS: u64 = 5_000;

/// Rolling window for cumulative bridge execution time (per app).
pub const BRIDGE_CHAIN_WINDOW_MS: u64 = 30_000;
/// Max cumulative native bridge latency within [`BRIDGE_CHAIN_WINDOW_MS`].
pub const BRIDGE_CHAIN_BUDGET_MS: u64 = 30_000;

/// Guest-side promise timeout (JS); slightly above native timeout for clean errors.
pub const BRIDGE_JS_CALL_TIMEOUT_MS: u32 = 8_000;

static BRIDGE_LIMITS: LazyLock<Mutex<HashMap<String, (u64, u32)>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

static CHAIN_BUDGET: LazyLock<Mutex<HashMap<String, (u64, u64)>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

fn lock_err() -> String {
    "bridge limiter unavailable".to_string()
}

pub fn check_bridge_rate(app_id: &str) -> Result<(), String> {
    let now = now_ms();
    let mut guard = BRIDGE_LIMITS.lock().map_err(|_| lock_err())?;
    let entry = guard.entry(app_id.to_string()).or_insert((now, 0));
    if now.saturating_sub(entry.0) > WINDOW_MS {
        *entry = (now, 0);
    }
    if entry.1 >= MAX_CALLS_PER_MINUTE {
        return Err(format!(
            "bridge rate limit exceeded ({MAX_CALLS_PER_MINUTE}/min)"
        ));
    }
    entry.1 += 1;
    Ok(())
}

/// Reject when the next bridge call would exceed the rolling execution budget.
pub fn check_chain_budget(app_id: &str, projected_ms: u64) -> Result<(), String> {
    let now = now_ms();
    let mut guard = CHAIN_BUDGET.lock().map_err(|_| lock_err())?;
    let entry = guard.entry(app_id.to_string()).or_insert((now, 0));
    if now.saturating_sub(entry.0) > BRIDGE_CHAIN_WINDOW_MS {
        *entry = (now, 0);
    }
    if entry.1.saturating_add(projected_ms) > BRIDGE_CHAIN_BUDGET_MS {
        return Err(format!(
            "bridge execution budget exceeded ({BRIDGE_CHAIN_BUDGET_MS}ms per {}s)",
            BRIDGE_CHAIN_WINDOW_MS / 1000
        ));
    }
    Ok(())
}

/// Record measured native bridge handler latency for chain-budget accounting.
pub fn record_bridge_elapsed(app_id: &str, elapsed_ms: u64) {
    let Ok(mut guard) = CHAIN_BUDGET.lock() else {
        return;
    };
    let now = now_ms();
    let entry = guard.entry(app_id.to_string()).or_insert((now, 0));
    if now.saturating_sub(entry.0) > BRIDGE_CHAIN_WINDOW_MS {
        *entry = (now, 0);
    }
    entry.1 = entry.1.saturating_add(elapsed_ms);
}

#[cfg(test)]
mod tests {
    #![allow(clippy::unwrap_used, clippy::expect_used)]

    use super::*;

    #[test]
    fn chain_budget_resets_after_window() {
        let app = "chain-reset-test";
        record_bridge_elapsed(app, BRIDGE_CHAIN_BUDGET_MS);
        assert!(check_chain_budget(app, 1).is_err());
        {
            let mut guard = CHAIN_BUDGET.lock().unwrap();
            if let Some(entry) = guard.get_mut(app) {
                entry.0 = now_ms().saturating_sub(BRIDGE_CHAIN_WINDOW_MS + 1);
            }
        }
        assert!(check_chain_budget(app, 1_000).is_ok());
    }

    #[test]
    fn chain_budget_accumulates_within_window() {
        let app = "chain-accum-test";
        record_bridge_elapsed(app, 10_000);
        record_bridge_elapsed(app, 10_000);
        assert!(check_chain_budget(app, 5_000).is_ok());
        assert!(check_chain_budget(app, 15_000).is_err());
    }
}
