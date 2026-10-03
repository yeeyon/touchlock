# Protecting a video call from accidental system gestures

## Recommended approach for a personal phone

Use TouchLock to block app-area input, then pin the actual video-call app using Android's screen/app pinning feature. Require the device PIN, pattern, or password before unpinning. The two features solve different problems: the transparent overlay catches accidental touch inside the call; pinning keeps the phone in the selected app.

Pinning is not a guarantee that every gesture disappears. The system still provides a deliberate unpin path, and behavior differs between manufacturers. It can also restrict notification access, so the on-screen three-second unlock control must remain available. Test the combination with the chosen call app on the actual phone. TouchLock cannot silently pin WhatsApp on an ordinary unmanaged phone.

On Honor, search Settings for **Screen pinning** or **App pinning**. Honor's support material lists Security > More settings > Screen pinning on some models; the path can differ on current MagicOS devices. Select the call app in Recents and use its pin action. Pinning TouchLock's own setup screen would not protect a WhatsApp call.

## Why a larger overlay or immersive mode is insufficient

Android documents `TYPE_APPLICATION_OVERLAY` as above application activities but below critical system windows such as the status bar. Increasing its size does not give it priority over the notification shade.

The gesture-exclusion API allows selective handling of side Back gestures. It does not let ordinary apps opt out of the mandatory bottom Home and app-switching gestures. Immersive mode can hide system bars, but users can reveal them again with an edge swipe. It is not a complete navigation lock.

## Complete system UI restrictions

For a dedicated managed device, Android's lock task mode is the supported architecture. A device policy controller allowlists the call app and can disable Home, Overview, notifications, and other configurable system UI features. This requires appropriate device-management provisioning; it is not a permission that an ordinary APK can request after installation. TouchLock currently does not implement a device policy controller or provisioning.

A restricted mode should use an on-screen parent exit rather than a notification action, because blocking the notification shade removes that exit route. The current three-second hold is intended to prevent accidental input, not authenticate a parent.

## Sources

- [Android overlay window rules](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)
- [Android gesture navigation and mandatory bottom gestures](https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav)
- [Android immersive mode](https://developer.android.com/develop/ui/views/layout/immersive)
- [Android screen pinning instructions](https://support.google.com/android/answer/9455138?hl=en)
- [Android lock task mode and device policy](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode)
- [Honor support mentioning screen pinning](https://www.honor.com/ae-en/support/content/en-us00410040/)

Reviewed October 3, 2026. These are platform capabilities and recommendations, not a claim that every call app has been tested.
