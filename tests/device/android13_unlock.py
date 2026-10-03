"""Exercise the accessibility input filter on a disposable Android 13 emulator.

Install the release APK on a Pixel 4 AVD (1080x2280, 440 dpi), allow overlays
and notifications, enable TouchLock's accessibility service, and enable gesture
navigation. Run: python tests/device/android13_unlock.py emulator-5556
Only this emulator is rooted to write kernel touch events. Production TouchLock
does not use root or ADB. `adb shell input` cannot reproduce this regression.
"""
import json
import re
import subprocess
import sys
import time

serial = sys.argv[1] if len(sys.argv) == 2 else ''
if not re.fullmatch(r'emulator-\d+', serial):
    raise SystemExit('Specify a disposable emulator serial; physical devices are not supported.')


def adb(*args):
    return subprocess.check_output(['adb', '-s', serial, *args], text=True, encoding='utf-8', errors='replace').strip()


def shell(*args):
    return adb('shell', *args)


def wait_for(check, message):
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        if check():
            return
        time.sleep(0.1)
    raise AssertionError(message)


def state():
    return shell('dumpsys', 'activity', 'service', 'com.yeeyon.touchlock/.TouchGuardService')


def count():
    return int(re.search(r'capturedTouchStarts=(\d+)', state()).group(1))


def navigation():
    activity = shell('dumpsys', 'activity', 'activities')
    window = shell('dumpsys', 'window')
    return (re.search(r'topResumedActivity=(.+)', activity).group(1),
            re.search(r'mCurrentFocus=(.+)', window).group(1))


def event(kind, code, value):
    return f'sendevent /dev/input/event2 {kind} {code} {value}'


def position(x, y):
    return [event(3, 53, round(x * 32767 / 1080)), event(3, 54, round(y * 32767 / 2280))]


def down(x, y, slot=0):
    return [event(3, 47, slot), event(3, 57, 100 + slot), *position(x, y),
            event(3, 48, 8), event(3, 58, 512), event(0, 0, 0)]


def up(slot=0):
    return [event(3, 47, slot), event(3, 57, -1), event(0, 0, 0)]


def hardware_hold(seconds, second_finger=False):
    commands = down(950, 1300)
    if second_finger:
        commands += down(970, 1330, 1)
    commands += [f'sleep {seconds}']
    if second_finger:
        commands += up(1)
    commands += up()
    shell('; '.join(commands))


def hardware_swipe(x1, y1, x2, y2, duration=0.5):
    commands = down(x1, y1)
    for step in range(1, 11):
        commands += [f'sleep {duration / 10}', *position(x1 + (x2-x1)*step/10,
                                                        y1 + (y2-y1)*step/10), event(0, 0, 0)]
    commands += up()
    shell('; '.join(commands))


def tap_floating_button():
    shell('; '.join(down(981, 1348) + ['sleep 0.1'] + up()))
    wait_for(lambda: 'capturing=true' in state(), 'Floating button did not receive normal hardware touch')
    time.sleep(0.3)


def lock():
    # Exercise the same lock action as the notification, independently of launcher animations.
    shell('am', 'start-foreground-service', '-n', 'com.yeeyon.touchlock/.TouchLockService',
          '-a', 'com.yeeyon.touchlock.LOCK')
    wait_for(lambda: 'capturing=true' in state(), 'Did not lock')
    wait_for(lambda: 'touchExplorationEnabled=true' in shell('dumpsys', 'accessibility'), 'Exploration did not enable')
    time.sleep(0.3)


assert shell('getprop', 'ro.build.version.sdk') == '33', 'Use Android 13'
assert '1080x2280' in shell('wm', 'size') and '440' in shell('wm', 'density'), 'Use the documented Pixel 4 fixture'
assert 'showing=false' in shell('dumpsys', 'window', 'policy'), 'Unlock the emulator first'
adb('root')
adb('wait-for-device')
assert shell('id', '-u') == '0', 'The test fixture must permit adb root'
assert 'virtio_input_multi_touch_1' in shell('getevent', '-lp', '/dev/input/event2'), 'Unexpected kernel input device'
results = {}
try:
    shell('am', 'start', '-n', 'com.yeeyon.touchlock/.MainActivity', '-a', 'android.intent.action.MAIN',
          '-f', '0x14000000')
    time.sleep(0.5)
    lock()
    expected_navigation = navigation()
    for name, coords in [
        ('top_left', (150, 1, 150, 1000, 0.5)),
        ('top_right', (950, 1, 950, 1000, 0.5)),
        ('home', (540, 2278, 540, 1500, 0.3)),
        ('recents', (540, 2278, 540, 1000, 1.5)),
        ('back_left', (1, 1100, 500, 1100, 0.5)),
        ('back_right', (1078, 1100, 600, 1100, 0.5)),
    ]:
        before = count()
        hardware_swipe(*coords)
        wait_for(lambda: count() == before + 1, f'{name}: kernel touch never reached the controller')
        assert 'capturing=true' in state() and navigation() == expected_navigation, f'{name}: gesture escaped'
        results[name] = 'controller consumed kernel touch; focus unchanged'
    hardware_hold(2)
    assert 'capturing=true' in state(), 'Short hold incorrectly unlocked'
    results['two_second_hold'] = 'stayed locked'
    hardware_hold(3.4, second_finger=True)
    assert 'capturing=true' in state(), 'Additional finger incorrectly unlocked'
    results['additional_finger'] = 'cancelled unlock'
    hardware_hold(3.4)
    wait_for(lambda: 'capturing=false' in state(), 'Full hardware hold did not unlock')
    assert 'touchExplorationRequested=false' in state(), 'Exploration request was retained'
    wait_for(lambda: 'touchExplorationEnabled=false' in shell('dumpsys', 'accessibility'), 'Exploration stayed enabled')
    results['full_hold'] = 'unlocked; exploration cleared'
    time.sleep(0.3)  # Client state acknowledgement precedes input-filter teardown.
    for cycle in range(3):
        tap_floating_button()
        hardware_hold(3.4)
        wait_for(lambda: 'capturing=false' in state(), f'Relock cycle {cycle} did not unlock')
        assert count() > 0 and 'touchExplorationRequested=false' in state()
        time.sleep(0.3)
    results['normal_touch_after_unlock'] = 'floating button received normal hardware taps'
    results['repeat_lock_unlock'] = '3 additional cycles passed'
    lock()
    shell('input', 'keyevent', 'KEYCODE_SLEEP')
    wait_for(lambda: 'capturing=false' in state(), 'Screen-off retained capture')
    time.sleep(0.5)  # Also cover pending registration retries after stop.
    assert 'touchExplorationRequested=false' in state()
    assert 'TouchLock touch blocker' not in shell('dumpsys', 'window', 'windows')
    results['screen_off_during_startup'] = 'released capture; no late registration'
finally:
    shell('; '.join(up(1) + up()))
    shell('input', 'keyevent', 'KEYCODE_SLEEP')
print(json.dumps(results, indent=2))
