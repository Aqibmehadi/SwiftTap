# SwiftTap

Lightweight auto tapper using AccessibilityService. Min Android 10 (API 29).

## Build
Actions tab -> "Build APK" -> Run workflow -> download "SwiftTap-debug-apk" artifact.

## Use
1. Install APK, open SwiftTap, enable it in Accessibility settings.
2. Android 13+: App info -> top-right menu -> "Allow restricted settings", then enable.
3. Tap "Show floating panel". Drag numbered circles onto the target buttons.
4. Play / pause / stop from the panel. SEQ = tap points 1, 2, 3... in order.

## Benchmark on a real phone
Run 30 s at 10, 25, 50, 100 taps/s. Compare "Requested" vs "Actual".
"Actual" = taps Android confirmed. Also check the target app's own counter:
that is the real result. Unit tests only check the interval math, not real touch injection.
