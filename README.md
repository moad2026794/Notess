# Daily Notes (ملاحظاتي اليومية)

Android app (Kotlin + Jetpack Compose + Room). Offline, Arabic/English, RTL.

## Build
1. Open this folder in Android Studio (Koala or newer) and let Gradle sync
   (it downloads Gradle 8.7 and generates the wrapper automatically).
2. Run on a device, or: Build > Build Bundle(s)/APK(s) > Build APK(s)  → app/build/outputs/apk/debug/app-debug.apk
   For a signed release: Build > Generate Signed Bundle / APK.

## Features
- Daily view with a scrollable day strip (dots mark days that have notes) + date picker
- Create/edit with autosave, title, category, checklist item, pin, share, word count
- Search across title/content/category
- Swipe to delete with Undo, Trash with restore / delete forever
- Light / dark / system theme
- Daily reminder notification at a chosen time
- Export / import backup (JSON)
