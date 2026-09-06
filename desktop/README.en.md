# PageTurner · Desktop browser extension

[中文](README.md) · **English** · [日本語](README.ja.md)

Read comics in Chrome or Edge with a clean image layer that restores the original page when paused

## Install

1. Download `PageTurner-PC.zip` and extract it to a permanent folder
2. Open `chrome://extensions` or `edge://extensions` and turn on Developer mode
3. Click **Load unpacked** and select the folder containing `manifest.json`

Pin PageTurner from the browser’s extensions menu

## Read

Open a comic page, click the extension icon, choose your settings and click **Start**

- The large number controls pauses; slow-scroll the full page or pause halfway, with 1–30 seconds per part
- **Image only** uses a clean reader layer; turn it off to scroll the original page
- New pages start at the top; pausing and resuming keeps your reading position
- A gently breathing dot means running; a still dot means paused, and hovering shows the remaining seconds
- Click, wheel, key input or switching tabs pauses; the dot resumes and × closes the controls
- Use the dot at the top right to choose the main image or next-page button if automatic detection is wrong
- Each session stops after 30 minutes; restart manually to continue
- Chinese, English and Japanese, initially following the browser language

## Limits

This is a reader layer, not an ad blocker; the original page can still run scripts and requests

Intended for image-based comic pages, not browser settings, built-in PDF viewers or desktop applications; cross-origin frames, Canvas and custom controls may be unsupported

Tool-triggered navigation within the same site can continue; manual navigation or another site needs a fresh start, and you handle logins and verification yourself

Halfway is half of the readable content’s scrollable distance, affected by loading and window size

Settings stay local, reading sessions and selections are temporary, no content uploads, Cookie access or remote AI

This unpacked version needs manual updates: replace the folder contents with the new download, reload the extension and refresh your reading tab, with no store auto-updates

[MIT](LICENSE) · Made by JamieTso, thanks for using it
