// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
fn main() {
    if let Err(e) = uniffi::generate_scaffolding("src/mycelium.udl") {
        panic!("failed to generate uniffi scaffolding: {e}");
    }
}
