// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Type stubs when `wasm-runtime` feature is disabled.

pub const DEFAULT_WASM_FUEL_PER_INVOCATION: u64 = 1_000_000_000;
pub const DEFAULT_WASM_MAX_MEMORY_BYTES: usize = 16 * 1024 * 1024;
pub const DEFAULT_WASM_EXECUTION_TIMEOUT_MS: u64 = 5_000;
pub const DEFAULT_WASM_ENTRY: &str = "guest.wasm";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct WasmSandboxLimits {
    pub fuel_per_invocation: u64,
    pub max_memory_bytes: usize,
    pub execution_timeout_ms: u64,
}

impl Default for WasmSandboxLimits {
    fn default() -> Self {
        Self {
            fuel_per_invocation: DEFAULT_WASM_FUEL_PER_INVOCATION,
            max_memory_bytes: DEFAULT_WASM_MAX_MEMORY_BYTES,
            execution_timeout_ms: DEFAULT_WASM_EXECUTION_TIMEOUT_MS,
        }
    }
}

#[derive(Debug, Clone, Default)]
pub struct WasmSandboxConfig {
    pub limits: WasmSandboxLimits,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum WasmSandboxError {
    Runtime(String),
}

impl std::fmt::Display for WasmSandboxError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Runtime(r) => write!(f, "wasm runtime: {r}"),
        }
    }
}
