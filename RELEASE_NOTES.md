# TouchLock 1.0.1

A transparent Android touch blocker for accidental taps during video calls and playback.

## Included

- Open padlock while ready; closed padlock while touch is locked.
- Transparent app-area blocking for taps, swipes, and scrolling.
- A full three-second hold to unlock, with a circular progress indicator.
- Drag the control in either state. Dragging cancels unlocking.
- Notification actions to lock screen touch, show the control again, and stop TouchLock.
- Offline operation without Accessibility access, accounts, internet permission, analytics, or screen recording.

## Install

Download **TouchLock-1.0.1.apk** below. Requires Android 8.0+.

Grant **Display over other apps** and allow notifications, enable the floating control, then open your call or video. Tap the control or **Lock screen touch** in the notification to activate blocking. Hold the closed padlock still for three seconds to unlock.

## Limits

System navigation, the notification shade, and hardware buttons remain available. The recommended personal-phone setup is TouchLock plus Android screen pinning of the video-call app, with PIN-protected unpinning. Pinning can restrict notification access. Complete system UI restriction requires managed-device lock task mode, which this app does not implement.

Apps can deliberately hide overlays. Compatibility with individual video-call apps has not been certified. See the repository's system-gesture protection guide for the Android documentation and tradeoffs.

## Checks

Release build and four gesture timing tests passed. The signed APK was installed and smoke-tested on an Honor BKQ_N49 running Android 17: app-area taps/swipes were blocked, an early release stayed locked, and a full hold unlocked.

**Signing certificate SHA-256:** `399fe6bbb5a04a0a1a4f94a256ef6fac35aa9c28c293a476a350f2b367c76b16`

Verify the APK file against the attached `SHA256SUMS.txt`.
