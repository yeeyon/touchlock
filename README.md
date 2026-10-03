# TouchLock

**Let them watch the video call without tapping the screen.**

[Download the signed APK](https://github.com/yeeyon/touchlock/releases/latest) ? [Report a problem](https://github.com/yeeyon/touchlock/issues)

A small native Android app that blocks accidental taps and swipes over videos in WhatsApp and other apps. Kotlin, Android 8.0+ (API 26), targeting Android 15. Version 1.1.1 supports gesture protection on Android 13+ using an optional accessibility service. No internet permission, accounts, analytics, screen recording, or reading other apps' content.

## Features

- Transparent blocking of app-area taps, swipes, and scrolling.
- **Block system gestures** captures physical touchscreen input on Android 13+, including notification pull-down and touch navigation. Enable **TouchLock gesture protection** in Accessibility to use it. Android 13 temporarily requests touch exploration while locked; normal touch returns when unlocked.
- Home-screen widget shows the floating lock so you can open a call or video before locking.
- Open padlock = ready; closed padlock = touch locked.
- Tap the floating control or **Lock screen touch** in the notification to lock.
- Hold still for **3 full seconds** to unlock, with a circular progress indicator.
- Drag the control in either state; dragging cancels unlocking.
- **Show button** in the notification restores the control to a visible position without unlocking.
- No accounts, ads, internet permission, analytics, screen-content access, or screen recording. App-area mode works without Accessibility.

## Install and use

Build and install version 1.1.1 using the commands below, or install the supplied signed `TouchLock-1.1.1.apk`. Version 1.1.0's system-gesture switch was disabled on Android 13, even with Accessibility enabled. The previously published 1.0.1 APK in [Releases](https://github.com/yeeyon/touchlock/releases/latest) only blocks app-area touches. Android may ask you to allow installation from the app opening the APK.

1. Open TouchLock and grant **Display over other apps**. Return to TouchLock.
2. Allow notifications for the lock, show-button, and stop actions.
3. On Android 13+, keep **Block system gestures** on and enable **TouchLock gesture protection** in Accessibility. If Android shows **Restricted setting**, open TouchLock's app info and use its menu to **Allow restricted settings** first. Other touch-exploration services must be off. Turn **Block system gestures** off for app-area blocking alone.
4. Tap **Enable floating lock**, or add the widget using **Add home-screen widget** or your launcher's Widgets menu. The widget shows the floating lock; open your video or call next.
5. The open padlock means ready. Tap it once or use **Lock screen touch** in the notification to lock. Drag the control to reposition it, even while locked.
6. Hold one finger on the lock for **3 full seconds** to unlock. The ring shows progress. Early release, dragging, additional fingers, or interrupted input cancels the attempt. The floating button remains available after unlocking.
7. Use **Stop TouchLock** in the notification or the app when finished. Turning the screen off stops the service and releases touch capture. Use the power button if you need an alternative exit while notifications are blocked; enable TouchLock again after waking the phone.

In app-area mode, system navigation and the notification shade remain available. Gesture protection additionally consumes physical touchscreen input while locked; power and volume buttons remain available. The three-second hold prevents accidental input and is not parent authentication. Some manufacturers may restrict overlays or stop the service. The APK is release-signed for direct installation and is not distributed through the Play Store.

For use without Accessibility, combine app-area blocking with **PIN-protected screen pinning of the video-call app**. Pinning can restrict notification access, so retain the on-screen unlock control. [System gesture protection guide](docs/system-gesture-protection.md) explains the tested options and the managed-device alternative. Video-call compatibility varies; test with your call app before relying on it.

## Screenshot

<img src="docs/images/touchlock-locked.png" width="320" alt="TouchLock with its transparent blocker active and a hold-three-seconds unlock control" />

## Build

Install JDK 17 and the Android SDK with platform 35. Set `ANDROID_HOME` or put your SDK path in an ignored `local.properties` file (`sdk.dir=...`).

Windows:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

macOS/Linux:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Release signing

Signing material is stored outside this repository. Create `~/.android/touchlock-release.properties` for your own signing key:

```properties
storeFile=/absolute/path/to/your-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Run `./gradlew :app:assembleRelease :app:testReleaseUnitTest` (Windows: `.\gradlew.bat`). Without these properties, the release APK is unsigned. Do not commit keys or password files. An APK signed with your own key cannot update the official APK in place.

### Device check

Version 1.1.0 was installed on an Honor LNA-NX1 running Android 16 (API 36). Its accessibility service connected, the full-screen accessibility blocker attached, and the home-screen widget showed the floating control. A two-second hold stayed locked; a full hold restored the button and reset the capture source mask to zero. Screen-off and accessibility disconnection removed the blocker and floating button; reconnecting let the widget start again. The earlier physical-touch prototype test intercepted top, bottom, and side gestures on this phone. Final release testing of sustained Recents gestures and an actual video call still requires physical input; ADB-injected swipes bypass the accessibility input filter.

The signed release was installed on an Honor BKQ_N49 running Android 17 (API 37). App-area taps and swipes were blocked, a two-second hold stayed locked, and a full three-second hold restored the ready button. This is a device smoke check, not certification across all video-call apps or Android devices.

## Implementation

`MainActivity` handles setup and explicit user activation. `TouchLockService` owns a foreground service, a draggable `TYPE_APPLICATION_OVERLAY` control, and a transparent full-screen touch-consuming overlay. The windows use `FLAG_NOT_FOCUSABLE` to preserve the underlying app's focus, and never use `FLAG_NOT_TOUCHABLE`. Android 14+ uses the documented `specialUse` foreground-service type with its purpose declared in the manifest. No boot startup or automatic background restart is used.

`LockControlView` cancels queued animation callbacks when detached or cancelled. `HoldGesture` uses a monotonic clock and is unit-tested at the 2,999 ms / 3,000 ms boundary. Orientation changes reposition the control inside safe screen insets. Service shutdown removes both windows and the notification.

`TouchGuardService` supplies the accessibility window context and requests `SOURCE_TOUCHSCREEN` only while locked on Android 14+. Android 13 instead uses `TouchInteractionController` with a temporary `FLAG_REQUEST_TOUCH_EXPLORATION_MODE`; its callback consumes input without requesting delegation, dragging, or exploration. Motion events are copied into the existing blocker and hold/drag control. Unlock, stop, screen-off, and accessibility disconnection release capture and clear the temporary exploration flag. It does not request window-content retrieval. The widget starts the floating control and routes to setup when permissions are missing.

Android references: [overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY), [foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use), [foreground-service launch rules](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
