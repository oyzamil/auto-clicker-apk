# Open Clicker

Android auto-clicker (Kotlin, no third-party dependencies). Package: `com.oyzamil.openclicker`.

## Actions
Click, Hold (long press), Slide, Scroll (direction + distance), Pinch / zoom (2 fingers),
Wait, and system buttons (Back, Home, Recents, Notifications, Quick settings).

## Features
- Delay input with ms / sec / min segmented selector (auto-converted to ms), presets up to 5 min
- Start countdown (3-2-1) and auto-stop timer
- Repeat count + per-action repeat, live run counter and elapsed time on the panel
- Multi mode: record, add via the + menu, edit, reorder, duplicate, undo, save / load / export / import / share / paste
- Human-like slide and scroll (curved path, random speed), random position / interval
- Indicator size slider, lock indicators, hide indicators, panel opacity + size
- Volume Up + Down together = start / stop
- Quick Settings tile and a Stop notification
- Auto-show the floating panel (and load a script) when chosen apps open
- Kill switch: auto-stop when the screen turns off (default ON)
- English / Urdu, dark / light / system theme

## Keeping the accessibility service enabled
Android turns an accessibility service off after a force-stop ("Clear all", battery cleaners), some updates,
and marks it "Not working" if the app process crashes. The app protects against both:
- Crash guard + error log (Safety card > Share error log).
- One-time ADB permission so the app can switch its own service back on (also after reboot / update):
  `adb shell pm grant com.oyzamil.openclicker android.permission.WRITE_SECURE_SETTINGS`
  (the app shows this command in the "Stay enabled" card; the grant survives updates, not uninstall).

## Build
1. Push to GitHub. `.github/workflows/build.yml` builds the debug APK and publishes a Release.
2. Or open in Android Studio and run.
3. Enable *Open Clicker* in Settings > Accessibility (Android 13+ sideload: App info > ⋮ > Allow restricted settings first).

`app/debug.keystore` is a fixed debug key so every CI build has the same signature
(updates install over the old build and the accessibility permission survives).
