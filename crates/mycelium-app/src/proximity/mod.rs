mod matcher;
mod matches;
mod presence;
mod store;

pub use matcher::{MatchScore, ProximityMatcher};
pub use matches::{
    ProximityInbox, ProximityMatchState, ProximityNearbyEntry, ProximityReceivedMessage,
};
pub use presence::{
    PresenceProfile, PresenceSignal, ProximityDirectMessage, ProximityMatchIntent, PROXIMITY_SCOPE,
};
pub use store::ProximityStore;

#[cfg(test)]
mod tests;
