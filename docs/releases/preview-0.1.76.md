**Technical preview for trying Mycena.** Not on Google Play yet, not security-audited, and not a replacement for emergency services.

### What you can try

- End-to-end encrypted chat and groups, no account and no phone number
- Automatic connection to other Mycena phones on the same Wi-Fi, also without internet
- Bluetooth links between Android phones (validated on a Pixel 6 so far)
- Crisis mode with help requests, resource map and volunteer tasks
- Optional internet relay (`mycena-relay.fly.dev`) to find peers across networks

Store-and-forward delivery and direct Wi-Fi links are experimental prototypes. What comes next is in the [roadmap](https://github.com/mycena-project/Mycena/blob/main/docs/ROADMAP.md); known limits are in [KNOWN_LIMITATIONS.md](https://github.com/mycena-project/Mycena/blob/main/docs/KNOWN_LIMITATIONS.md).

### Install

Download `mycena-0.1.76-preview-android-debug.apk` below and open it on an Android 8+ phone (allow installing from this source), or run `adb install -r mycena-0.1.76-preview-android-debug.apk`.
The APK is signed with a debug key, so sharing the app phone-to-phone is not available in this build.
Verify the download: `shasum -a 256 -c SHA256SUMS.txt`

### License

AGPL-3.0-or-later. If you modify Mycena or the relay and run it as a network service, offer the corresponding source to its users.
