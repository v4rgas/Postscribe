# eLauncher (KitKat fork)

Fork of [thypon/eLauncher](https://github.com/thypon/eLauncher) backported to **Android 4.4 (API 19)**. Tested on a MobiScribe E60QR2 (Netronix `ntx_6sl`).

## What changed from upstream

- AGP 8.5.2 → 7.4.2, Gradle 8.7 → 7.5.
- `minSdk` 24 → 19, `targetSdk`/`compileSdk` 34 → 28.
- AndroidX downgraded: appcompat 1.3.1, recyclerview 1.2.1, preference 1.1.1, constraintlayout 2.0.4.
- API 21+ paths guarded behind `SDK_INT >= LOLLIPOP`: UsageStats lookups, `Window.setStatusBarColor`/`setNavigationBarColor`, `Settings.ACTION_USAGE_ACCESS_SETTINGS`. The Bigme HiBreak shim is a no-op here.
- Java 8 APIs not in KitKat replaced: `String.chars().mapToObj()` → for loop, `ArrayList.sort(lambda)` → `Collections.sort`, `Iterable.forEach(Consumer)` → for loop, `TypedArray` try-with-resources → try/finally.
- `?attr/...` in `res/drawable/search_bar_background.xml` replaced with literal colors. KitKat cannot resolve theme attrs inside drawable XML.

## Build

```sh
ANDROID_HOME=/opt/android-sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

Needs SDK platform-19, platform-28, build-tools 30.0.3, JDK 17.

---

## Upstream README

eLauncher is an extremely lightweight and minimal launcher for Android, based on NoLauncher and inspired by [OLauncher Light](https://github.com/tanujnotes/Ultra/), and OLauncher in general. It is even more barebones than OLauncher Light, and aims to provide only the most basic features.

eLauncher favours easy readibility on eInk/ePaper devices, such as the Onyx Boox Note series, and the Bigme HiBreak.

## Features

- Extremely lightweight: only 708KB
- eInk friendly: uses a light theme by default, fix text size and weight
- Fuzzy Search: search for apps by typing their name
- Bottom search bar in app drawer

- Homescreen and app drawer: swipe up on homescreen to enter the app drawer
- Long press an app field on the homescreen to assign an app, app can be renamed
- Type to search in app drawer, if only one result is left, it is automatically launched (like OLauncher)
- Gestures: swipe down for notification center, left for browser app, right for phone app, double tap to open the original launcher
- Hold on empty space to change the number of apps on homescreen

## apk size differences with OLauncher Light

This might have been done on purpose, but OLauncher Light uses long deprecated APIs, like ListView to achieve its impressive 23 KB apk size. eLauncher uses RecyclerView, which is much better for performance and memory usage, and also uses many other newer APIs. Thus, the APK size is much larger than with OLauncher Light, but still really small - 1.8 MB.

## Download

You can download the apk file directly from the releases tab and install it manually.

## Contributing

Feel free to contribute if you found a bug or have a way to make the code more efficient or minimal, but please don't add massive new features. If you feel like adding a lot of customization options, widgets, etc. please start your own fork, as the scope of this project is to be as (reasonably) barebones of a launcher as possible.
