# PageTurner · 轻翻页

[中文](README.md) · **English** · [日本語](README.ja.md)

A minimal auto page turner for Android and desktop browsers

Read comics, image galleries or long web pages with timed taps and scrolls — touch the screen to pause

Oh, and those times when your hands are busy… you know

**[Android APK](https://github.com/BsTso/page-turner-releases/releases/latest)** · **[Desktop extension](https://github.com/BsTso/page-turner-releases/releases/latest/download/PageTurner-PC.zip)** · [Report an issue](https://github.com/BsTso/page-turner-releases/issues) · [Build it yourself](BUILDING.md#build-on-windows)

<img src="docs/ui-example.jpg" width="300" alt="PageTurner interface in Chinese, with a large interval display and four reading modes">

## What it does

- Turns pages at a set interval, using next-page button detection or a chosen spot on the screen
- Slowly scrolls to the bottom in one pass or two parts with a pause halfway, then optionally turns the page
- Provides a floating dot: tap to start or pause, hold for controls, drag to move
- Remembers tap positions for different foldable screen sizes
- Pauses after two turns with no detected page change, with an option to disable this check
- Completely free, with no account or ads
- Supports Chinese, English and Japanese, with an in-app language switch
- Android can cover the area around the comic, while the desktop extension offers a clean image reader, restoring the original page on pause
- Handy for reading and those hands-busy moments

## How to use it

**Android**

1. Download the APK and install it on your Android phone
2. Follow the setup guide to enable the PageTurner accessibility service — no separate overlay permission is needed
3. Choose an interval and reading mode, then tap **Show dot**
4. Open a reading page, hold the dot to choose a spot, then tap the dot to start

Touch or scroll the page yourself to pause, then tap the dot to resume

Choose **All the way down** or **Pause halfway, then continue**, with 1–30 seconds per part; the large number sets the pause between parts and before turning the page

Enable **Focus on the image** in settings to cover the area around a detected comic while running; pause restores the page, and no detection leaves it unchanged, this is not ad blocking

**Desktop Chrome / Edge**

Extract the desktop ZIP, turn on Developer mode in your browser’s extensions page, then use **Load unpacked** to select the extracted folder

Open a comic page and start from the extension icon; the image-only reader is on by default, with manual image and next-button selection if needed, [full instructions](desktop/README.en.md)

## Languages

The app follows your phone's language preferences on first launch, falling back to English if none are supported

Open settings at the top right and choose **Language** to select 简体中文, English, 日本語 or System default

One APK includes all three languages, with no regional downloads, IP lookup or location access

## Before you start

Requires Android 8.0 or later, with your reading page in the foreground

Page turning pauses when you switch apps, lock the screen, fold the phone or rotate it

Halfway means half of the page’s scrollable distance from top to bottom, not half a screen; the page must report scroll information to locate it, and actual timing depends on page loading and the phone

If page length is unavailable, try **All the way down**; if the bottom still cannot be confirmed, the app pauses instead of scrolling endlessly or guessing a turn from a fixed count

Fixed-position taps cannot distinguish ads or page pop-ups, and touch-to-pause behavior may vary by phone

Android takes no screenshots or network requests during reading, connecting to GitHub only for update checks; the desktop reader displays images from the original site without uploading reading content, and the original page can still load ads or run scripts

## Help improve it

Found a problem? Open an issue with your phone model, Android version, browser or app, and what went wrong

Fixes, compatibility improvements and wording suggestions are welcome — run the build checks before submitting code

If you find it useful, a Star is welcome ⭐

## License

[MIT](LICENSE) · Copyright (c) 2026 JamieTso

Made by JamieTso, thanks for using it
