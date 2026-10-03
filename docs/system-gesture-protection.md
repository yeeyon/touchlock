# Protecting a video call from accidental system gestures

## Gesture protection in TouchLock 1.1.2

On Android 14+, **Block system gestures** uses the touchscreen-capture mechanism tested below. Enable **TouchLock gesture protection** in Accessibility, then enable the floating lock and open your call app. Capture begins only when you tap to lock and ends when you hold to unlock, stop the service, or turn the screen off. If accessibility disconnects or touch exploration becomes active while locked, TouchLock stops. The mode refuses to lock when protection is unavailable; it does not silently substitute app-area blocking.

Android 13 uses a separate `TouchInteractionController` callback, available from API 33. While locked, TouchLock requests touch exploration and handles raw touch events itself without delegating them to the rest of the input pipeline. Unlocking and stopping clear this temporary request. The capability is declared in accessibility metadata, but exploration is not enabled merely by turning on the service. Other services requesting exploration conflict with protection and must be off. Android 12 and older retain app-area blocking only. Version 1.1.0 did not implement this path and disabled the Android 13 switch.

Version 1.1.1 registered its controller before requesting exploration. Android 13 can discard that request while its input filter is absent, so normal input was blocked but the unlock control received no callbacks. Version 1.1.2 requests exploration first, waits for it to enable, and retries controller registration until motion-event delivery confirms success. Re-registering the same callback does not add duplicate listeners. Unlocking or stopping cancels queued retries. The regression test uses kernel touchscreen events on an Android 13 emulator; an injected `input swipe` bypasses the filter and cannot verify this bug.

The home-screen widget shows the floating control and restores its position if it is already running. It opens setup when required permissions are missing. Use **Add home-screen widget** in the app or the launcher's Widgets menu. This does not pin the current app or lock the home screen.

The signed release on `.209` passed these lifecycle checks: a two-second hold retained capture (`4098`), a full hold released it (`0`) and restored the floating control, accessibility disconnection removed both windows, reconnection allowed widget activation, and turning the screen off removed both windows and reset capture to zero. The blocker covered `[0,0][1200,2600]`. These checks verify activation, unlock, and recovery; injected input cannot verify the physical system-gesture filter.

## Personal-phone option without Accessibility

Use TouchLock to block app-area input, then pin the actual video-call app using Android's screen/app pinning feature. Require the device PIN, pattern, or password before unpinning. The two features solve different problems: the transparent overlay catches accidental touch inside the call; pinning keeps the phone in the selected app.

Pinning is not a guarantee that every gesture disappears. The system still provides a deliberate unpin path, and behavior differs between manufacturers. It can also restrict notification access, so the on-screen three-second unlock control must remain available. Test the combination with the chosen call app on the actual phone. TouchLock cannot silently pin WhatsApp on an ordinary unmanaged phone.

On Honor, search Settings for **Screen pinning** or **App pinning**. Honor's support material lists Security > More settings > Screen pinning on some models; the path can differ on current MagicOS devices. Select the call app in Recents and use its pin action. Pinning TouchLock's own setup screen would not protect a WhatsApp call.

## Why a larger overlay or immersive mode is insufficient

Android documents `TYPE_APPLICATION_OVERLAY` as above application activities but below critical system windows such as the status bar. Increasing its size does not give it priority over the notification shade.

The gesture-exclusion API allows selective handling of side Back gestures. It does not let ordinary apps opt out of the mandatory bottom Home and app-switching gestures. Immersive mode can hide system bars, but users can reveal them again with an edge swipe. It is not a complete navigation lock.

### Test on the phone ending in .209

On October 3, 2026, a separate test APK based on TouchLock was installed over wireless ADB on an Honor LNA-NX1 running Android 16 (API 36), with a 1200 × 2600 display. The production package was not replaced. Gestures were injected using `adb shell input swipe`; window frames, focused windows, and resumed activities were checked through `dumpsys`.

| Overlay configuration | Notification / Quick Settings pull-down | Home swipe | Side Back swipe |
| --- | --- | --- | --- |
| Current full-screen blocker | Still available | Still available | Still available |
| Oversized blocker | Still available | Still available | Still available |
| Oversized blocker with full-view gesture exclusion request | Still available | Still available | Still available at the tested height |
| Oversized, focusable blocker | Still available | Still available | Blocked in this test |

The normal blocker occupied `[0,121][1200,2528]`. The oversized blocker used `FLAG_LAYOUT_NO_LIMITS`, a negative Y offset, no fitted system-bar insets, and layout through display cutouts. Its confirmed frame was `[0,-312][1200,2912]`: it really extended above and below the physical screen. Both top-left and top-right pull-downs still opened System UI. Making the blocker focusable did not stop Home: its own window retained focus while the underlying resumed activity changed to the launcher.

These observations establish that resizing an ordinary overlay cannot provide the requested system-gesture protection on this phone. They do not establish compatibility with video calls, other devices, or every gesture position.

### ADB-assisted restrictions without accessibility

On the same phone, the privileged shell command below blocked the tested notification pull-downs, Quick Settings pull-downs, Home swipe, and Recents swipe:

```sh
adb -s DEVICE_SERIAL shell cmd statusbar send-disable-flag statusbar-expansion quick-settings home recents
```

Restore those shell restrictions with:

```sh
adb -s DEVICE_SERIAL shell cmd statusbar send-disable-flag none
```

The test began with no status-bar disable flags, and the flags were verified to return to zero afterward. These commands do not disable Back or hardware buttons, and they are not tied to TouchLock's lock/unlock lifecycle. The APK cannot execute them with its existing overlay permission. A usable ADB-assisted app mode would need an authorized shell bridge and would need to restore restrictions when unlocking or stopping. Device-management lock task mode remains the supported option for a dedicated device.

## Accessibility and built-in pinning: further device tests

A second test APK added an accessibility service without window-content retrieval or touch exploration. Five `TYPE_ACCESSIBILITY_OVERLAY` variants were tested: full-screen, oversized, gesture exclusion, focusable, and focusable with gesture exclusion. All five blocked the tested notification and Quick Settings pull-downs. None blocked Home or Recents. The focusable variants intercepted the tested side Back gesture. Accessibility overlay permission alone therefore does not meet the requirement to stop all navigation gestures.

### Consuming touchscreen input through accessibility

Android's `AccessibilityServiceInfo.setMotionEventSources()` and `AccessibilityService.onMotionEvent()` APIs, introduced at API 34, provide another mechanism: selected input sources are withheld from the rest of the system. The test service requested `InputDevice.SOURCE_TOUCHSCREEN` while locked, forwarded copied events only to its own overlay and unlock control, and reset the source mask to zero when unlocking. A 90-second automatic timeout limited the device experiment.

On this Android 16 Honor, ADB-injected swipes produced **zero** motion-capture callbacks and still activated Home and Back. They were not valid tests of the accessibility input filter. During the subsequent physical-finger test, the service recorded **19** down/up callback events, including:

- Top-right pull-downs starting at approximately `(1064,20)` and `(1021,18)`.
- A top-left pull-down starting at `(185,17)`.
- Bottom-edge upward swipes starting at `(965,2575)` and `(573,2565)`.
- Side swipes starting at approximately `(29,1557)`, `(25,1512)`, and `(1200,1686)`.
- Touches on the right-center unlock control.

The focused window and resumed activity remained on the test app during the sampled physical-gesture checks. The blocker then disappeared after the final hold on the unlock control; accessibility settings were restored to their original values. This is evidence that physical touch capture operates on this device. The recorded bottom swipes were short, so they do not separately establish a sustained Recents gesture test. Version 1.1.0 now includes this mechanism with explicit permission setup and recovery on service disconnect or screen-off. Its signed APK was installed on the same phone, its accessibility blocker attached, and its widget was added and tested. Physical release testing of the complete gesture sequence, multi-touch, rotation, and an actual video call remains outstanding. Other Android versions and manufacturers have not been verified. Touch-exploration services can conflict with this API's touchscreen delivery.

### Android screen pinning on this phone

The test app and normal TouchLock overlay were also placed in Android's actual `PINNED` state using ADB's task-lock command. This was screen pinning, not managed-device `LOCKED` mode. Top-left and top-right pull-downs and a short Home swipe were blocked. A long bottom swipe exited pinning, after which Back could leave the app. The phone's existing pin-exit credential setting was unset, and this test did not demonstrate a PIN-protected exit.

For personal use, pin the video-call app and enable the manufacturer's option to require the lock-screen PIN/password when exiting. Verify that option with an actual unpin attempt. Pinning restricts leaving the app; the TouchLock overlay remains responsible for blocking touches inside the call.

### Choosing an approach

| Approach | What the tests or platform documentation establish | Requirement |
| --- | --- | --- |
| Ordinary oversized overlay | Does not stop system gestures on this phone | Overlay permission |
| Accessibility overlay | Stops tested top pull-downs; Home/Recents still work | Accessibility enabled |
| Accessibility touchscreen capture | Physical edge-swipes reached the consuming callback without leaving the test app | Supported Android implementation; further app and recovery testing |
| Screen pinning with TouchLock | Restricts navigation while pinned; the deliberate unpin path remains | Pin the actual call app; configure and verify credential-protected exit |
| ADB-assisted restrictions | Stops tested top pull-downs, Home, and Recents; no Back flag | Authorized shell bridge and explicit restoration |
| Managed lock task mode | Android's supported mechanism for disabling configured system UI and restricting apps | Device-policy provisioning; not tested on this personal phone |

## Complete system UI restrictions

For a dedicated managed device, Android's lock task mode is the supported architecture. A device policy controller allowlists the call app and can disable Home, Overview, notifications, and other configurable system UI features. This requires appropriate device-management provisioning; it is not a permission that an ordinary APK can request after installation. TouchLock currently does not implement a device policy controller or provisioning.

A restricted mode should use an on-screen parent exit rather than a notification action, because blocking the notification shade removes that exit route. The current three-second hold is intended to prevent accidental input, not authenticate a parent.

## Sources

- [Android overlay window rules](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)
- [Android gesture navigation and mandatory bottom gestures](https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav)
- [Android immersive mode](https://developer.android.com/develop/ui/views/layout/immersive)
- [Android screen pinning instructions](https://support.google.com/android/answer/9455138?hl=en)
- [Android lock task mode and device policy](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode)
- [Android status-bar shell commands](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/statusbar/StatusBarShellCommand.java)
- [Accessibility motion-event interception](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onMotionEvent(android.view.MotionEvent))
- [Selecting accessibility motion-event sources](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo#setMotionEventSources(int))
- [Android 13 touch interaction controller](https://developer.android.com/reference/android/accessibilityservice/TouchInteractionController)
- [Android 13 input-filter registration implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-13.0.0_r1/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java)
- [Honor support mentioning screen pinning](https://www.honor.com/ae-en/support/content/en-us00410040/)

Reviewed October 3, 2026. These are platform capabilities and recommendations, not a claim that every call app has been tested.
