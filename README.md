# Postscribe

> A postscript to a discontinued e-reader.

Postscribe is a fork of [thypon/eLauncher](https://github.com/thypon/eLauncher) for old MobiScribe e-ink Android tablets. The original eLauncher is a minimal e-ink-friendly home screen that targets Android 7+; Postscribe ports it back to Android 4.4 (API 19) and adds a small set of tools that make these abandoned devices useful again.

The reference device is the MobiScribe E60QR2 (Netronix `ntx_6sl`, Android 4.4.2, 2014-era hardware) but the changes are generic to KitKat.

## Why

MobiScribe sold these devices for years and then stopped. They still work, the screen is still nice, but the stock launcher is awkward and there is no first-party path to anything modern. The hardware is too old for almost every "e-ink launcher" written in the last five years — they all require API 23+. So the choice was either to keep using a launcher you dislike or to backport one. This is the backport.

Once root and a working launcher were in place, the next two things missing were "get files onto the device" and "see what state the device is in". Both got built into the launcher rather than added as separate apps.

## What it does

Everything upstream eLauncher does, plus:

- **Runs on Android 4.4.** Upstream targets API 24; this branch ports it back to API 19.
- **Developer panel in settings.** Shows the Wi-Fi IP, all network interfaces, Android version and API level, device manufacturer/model, build fingerprint, and free/total storage.
- **Built-in HTTP file server.** Toggle on in settings, choose a folder to expose (default `/sdcard`), optional HTTP Basic password (username `elauncher`). Connect from any browser on the same Wi-Fi to browse, upload, download, and delete. The UI is a single-page app served from the launcher's assets.
- **Auto-resume across reboots and Wi-Fi changes.** If you leave the server on, it starts itself on boot, stops when Wi-Fi drops, and starts again when Wi-Fi comes back.
- **Status line on the home screen.** When the server is running, the launcher shows the URL at the bottom of the home screen.

## Backport changes from upstream

Build tooling:

- AGP 8.5.2 -> 7.4.2, Gradle 8.7 -> 7.5.
- `minSdk` 24 -> 19, `targetSdk`/`compileSdk` 34 -> 28.
- AndroidX downgraded: appcompat 1.3.1, recyclerview 1.2.1, preference 1.1.1, constraintlayout 2.0.4.

Code:

- API 21+ paths guarded behind `SDK_INT >= LOLLIPOP`: UsageStats lookups, `Window.setStatusBarColor`/`setNavigationBarColor`, `Settings.ACTION_USAGE_ACCESS_SETTINGS`. The Bigme HiBreak shim is a no-op here.
- Java 8 APIs not in KitKat replaced: `String.chars().mapToObj()` -> for loop, `ArrayList.sort(lambda)` -> `Collections.sort`, `Iterable.forEach(Consumer)` -> for loop, `TypedArray` try-with-resources -> try/finally.
- `?attr/...` in `res/drawable/search_bar_background.xml` replaced with literal colors. KitKat cannot resolve theme attrs inside drawable XML.

New code (not in upstream):

- `UploadServer` (NanoHTTPD-backed file server with JSON API).
- `UploadService` and `UploadAutostartReceiver` for lifecycle and Wi-Fi/boot persistence.
- `DevInfo` (network and device introspection helpers).
- Developer category in `res/xml/root_preferences.xml`.
- `app/src/main/assets/web/` SPA: `index.html`, `styles.css`, `app.js`.

## Build

```sh
ANDROID_HOME=/opt/android-sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

Needs SDK platform-19 and platform-28, build-tools 30.0.3, JDK 17.

## License and attribution

Postscribe is licensed under the **GNU GPL v3**, inherited from the upstream eLauncher project. The original `LICENSE.md` is preserved unchanged. The upstream project is:

- thypon/eLauncher — https://github.com/thypon/eLauncher

Per GPL v3 §5, this fork carries prominent notice of the modifications. The list of changes is documented above ("Backport changes from upstream" and "New code"), and the commit history on this branch is the authoritative record of every modification.

Source for any binary distributed from this repository is available in this same repository.

NanoHTTPD (used by the file server) is licensed under BSD-3-Clause and is pulled in as a regular Maven dependency; no NanoHTTPD source is bundled in this tree.

---

## Upstream README

The original README from thypon/eLauncher follows.

# eLauncher

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
