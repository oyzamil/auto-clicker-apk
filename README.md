# Open Clicker

Original Android auto-clicker (Kotlin, no external deps beyond core-ktx).

- Single-target: one draggable crosshair, **custom delay in ms**
- Multi-target: numbered draggable targets, record taps/swipes, per-action delay (ms), edit/delete
- Random position (± px) + random interval (± ms)
- Save / load / export / import scripts (JSON)
- Floating panel, collapses to handle + start/stop

## Build
1. Open folder in Android Studio (Koala+), let Gradle sync.
2. Run on device (min Android 8.0 / API 26).
3. Enable *Open Clicker* in Settings > Accessibility. On Android 13+ sideloaded apps: App info > ⋮ > "Allow restricted settings" first.
4. Open app > Show floating panel.

Release APK: Build > Generate Signed Bundle / APK.
