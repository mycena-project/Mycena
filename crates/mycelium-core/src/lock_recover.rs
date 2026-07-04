// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Recover from [`std::sync::Mutex`] poisoning instead of panicking on the next lock.

use std::sync::{Arc, LazyLock, Mutex, MutexGuard};

/// Lock a mutex and recover from poisoning (see [`lock_or_recover`]).
pub trait LockRecover {
    /// Guarded value type.
    type Target;
    /// Lock and recover from poisoning.
    fn lock_recover<'a>(self, label: &'static str) -> MutexGuard<'a, Self::Target>
    where
        Self: 'a;
}

/// Lock `mutex`, logging and recovering when poisoned.
pub fn lock_mutex<'a, T>(mutex: &'a Mutex<T>, label: &'static str) -> MutexGuard<'a, T> {
    mutex.lock().unwrap_or_else(|err| {
        tracing::warn!("mutex poisoned ({label}); recovering guarded state");
        err.into_inner()
    })
}

impl<'a, T> LockRecover for &'a Mutex<T> {
    type Target = T;

    fn lock_recover<'b>(self, label: &'static str) -> MutexGuard<'b, T>
    where
        Self: 'b,
    {
        lock_mutex(self, label)
    }
}

impl<'a, T> LockRecover for &'a Arc<Mutex<T>> {
    type Target = T;

    fn lock_recover<'b>(self, label: &'static str) -> MutexGuard<'b, T>
    where
        Self: 'b,
    {
        lock_mutex(self.as_ref(), label)
    }
}

impl<'a, T> LockRecover for &'a LazyLock<Mutex<T>> {
    type Target = T;

    fn lock_recover<'b>(self, label: &'static str) -> MutexGuard<'b, T>
    where
        Self: 'b,
    {
        lock_mutex(&**self, label)
    }
}

/// Lock `mutex`, or if poisoned log and return the guarded data anyway.
///
/// Safe when the panicking holder did not leave the protected value in a
/// security-sensitive partial-update state (typically in-memory caches where
/// sled/DB writes happen after the guard is dropped).
pub fn lock_or_recover<'a, M>(mutex: M, label: &'static str) -> MutexGuard<'a, M::Target>
where
    M: LockRecover + 'a,
{
    mutex.lock_recover(label)
}

/// Lock `mutex` and recover from poisoning (see [`lock_or_recover`]).
#[macro_export]
macro_rules! lock_or_recover {
    ($mutex:expr) => {
        $crate::lock_recover::lock_or_recover(&$mutex, file!())
    };
    ($mutex:expr, $label:expr) => {
        $crate::lock_recover::lock_or_recover(&$mutex, $label)
    };
}

#[cfg(test)]
mod tests {
    #![allow(clippy::unwrap_used, clippy::expect_used)]

    use super::*;
    use std::sync::Arc;
    use std::thread;

    #[test]
    fn lock_or_recover_continues_after_poison() {
        let mutex = Arc::new(Mutex::new(0i32));
        let mutex2 = mutex.clone();
        let handle = thread::spawn(move || {
            let _guard = mutex2.lock().unwrap();
            panic!("deliberate poison in test");
        });
        assert!(handle.join().is_err());

        {
            let mut guard = lock_or_recover(&mutex, "test counter");
            *guard = 42;
        }
        assert_eq!(*lock_or_recover(&mutex, "test counter"), 42);
    }
}
