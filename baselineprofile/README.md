# Baseline Profile

The kiosk ships a [Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles/overview):
the classes and methods used at startup and on the order journey are compiled ahead of time when the
APK is installed, so the first customer of the day doesn't get a janky menu while ART is still JIT-compiling.

- `BaselineProfileGenerator` drives the app (pair once, then attract → member → menu → option sheets →
  cart → payment method) and records the profile. It never places an order.
- `KioskBenchmarks` measures cold start and first use of the menu (grid + option sheet) with and without the profile.
- The generated files are checked in at `app/src/release/generated/baselineProfiles/`
  (`baseline-prof.txt`, `startup-prof.txt`). Release builds package them; `profileinstaller` installs them
  on sideloaded kiosks.

## Regenerating

Regenerate after big UI or dependency changes (navigation, menu, Compose upgrades).

You need:

- an emulator or device on API 28–36 (a non-rooted one must be API 33+), in Thai or English. Android 17
  (API 37) preview images fail with "never flushed profiles" on benchmark 1.4.1. The checked-in profile was
  generated on an API 35 tablet emulator (`medium_tablet`, `google_apis_playstore_tablet` image);
- a backend it can reach, with a staff account that may pair a kiosk. Turn the branch's screensaver off
  on pos-web (Kiosk Settings), or keep its idle time at 30 s or more: a screensaver over the first screen
  swallows the journey's taps.

Run `adb devices`. If more than one device is listed, choose yours with `ANDROID_SERIAL`, otherwise the
task installs and runs on every connected device:

```sh
ANDROID_SERIAL=emulator-5556 ./gradlew :app:generateBaselineProfile \
  -PkioskApiBaseUrl=http://10.0.2.2:8080/ \
  -Pandroid.testInstrumentationRunnerArguments.kioskEmail=staff@example.com \
  -Pandroid.testInstrumentationRunnerArguments.kioskPassword='...'
```

On a fresh install the generator pairs the kiosk with the first branch and any free kiosk terminal. It
works from test tags (`branch_option`, `menu_grid`, `product_card`, `option_dialog`) and from the Thai and
English button labels, so renaming either means updating `KioskJourney.kt`.

## Measuring

Run this on a paired kiosk, ideally a real tablet, since emulator numbers are only indicative:

```sh
ANDROID_SERIAL=<device> ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
  -PkioskApiBaseUrl=... \
  -Pandroid.testInstrumentationRunnerArguments.class=com.pos.kiosk.baselineprofile.KioskBenchmarks
```

Add `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR` on an emulator.
