# WBNoti

An Android app that buzzes your WB 4.0 or 5.0 band when you receive phone notifications — so you never miss a call, message, or calendar alert while wearing the band.

## Features

- **Incoming calls** — buzzes while the phone rings, stops when answered or rejected
- **SMS, WhatsApp, Slack, Google Calendar** — configurable buzz count per notification type
- **Pinned apps** — promote any app from the Apps list to the Notification Types section with its own toggle, buzz count, and test button
- **Any other app** — enable per-app buzzing from the Apps list
- **Configurable buzz count** — set 1–10 distinct pulses per notification type; tap Test to feel it before saving
- **Works with WB 4.0 and 5.0** — auto-detects which band is connected
- **Auto-connects** on startup and auto-reconnects if the connection drops
- **Samsung support** — includes an Accessibility Service fallback for Samsung devices, which block some notifications (e.g. WhatsApp) from reaching standard notification listeners

## Requirements

- Android 8.0 (API 26) or later
- WB 4.0 or 5.0 band already paired to your phone via the official WB app

## Installation

### Step 1 — Download the APK

Go to the [Releases](../../releases) page and download the latest `app-debug.apk` file to your Android phone.

### Step 2 — Allow installation from unknown sources

Because the app is not distributed via the Google Play Store, Android will ask you to allow installation from outside the store.

1. Open your phone's **Files** app and tap the downloaded APK
2. When prompted, tap **Settings** and enable **Allow from this source**
3. Go back and tap **Install**

> On Samsung devices this setting is under **Settings → Apps → Special app access → Install unknown apps**, then select the browser or Files app you used to download the APK.

### Step 3 — Grant Bluetooth permission

When the app opens for the first time it will ask for Bluetooth permission. Tap **Allow**.

On Android 12 and above, it will also ask for **Nearby devices** permission — tap **Allow** for both Scan and Connect.

### Step 4 — Grant Phone (call detection) permission

The app will ask for **Phone** permission so it can detect incoming calls. Tap **Allow**.

If you skip this, the band will not buzz for incoming calls (all other notifications will still work).

### Step 5 — Grant Notification access

This is the core permission that lets the app see your notifications.

1. The app will show a prompt — tap **Open Settings**
2. In the list, find **WBNoti** and tap it
3. Toggle **Allow notification access** on
4. Tap **Allow** on the confirmation dialog

> If the app does not prompt you automatically, go to **Settings → Apps → Special app access → Notification access** and enable WBNoti from there.

### Step 6 — Grant Accessibility access (Samsung / WhatsApp users)

This is required on Samsung devices for WhatsApp and SMS buzz support. It is also what enables WhatsApp call detection on all devices.

1. The app will show a prompt — tap **Open Settings**
2. Find **WBNoti** in the list and tap it
3. Toggle the service **On**
4. Tap **Allow** on the confirmation dialog

> If the app does not prompt you, go to **Settings → Accessibility → Installed apps** (or **Downloaded apps**) and enable WBNoti from there.

### Step 7 — Disable battery optimisation

Android will kill background services to save battery unless you exempt them.

1. Go to **Settings → Battery → Battery optimisation** (or **Settings → Apps → WBNoti → Battery**)
2. Select **WBNoti** and choose **Unrestricted** (or **Don't optimise**)

> On Samsung, this is under **Settings → Device care → Battery → Background usage limits** — add WBNoti to the **Never sleeping apps** list.

### Step 8 — Make sure your WB band is connected

Open the official WB app and confirm your band is connected over Bluetooth. WBNoti connects to whichever WB is already paired to your phone, so the WB app does not need to stay open.

---

## Using the app

### Notification Types

When you open WBNoti you will see the **Notification Types** section at the top, followed by an **Apps** list.

The built-in notification types are:

| Type | What it controls |
|------|-----------------|
| Phone Calls | Buzzes while the phone rings; stops when answered or rejected |
| SMS | Built-in messaging apps (Google Messages, Samsung Messages, etc.) |
| WhatsApp | WhatsApp messages and calls |
| Slack | Slack messages |
| Google Calendar Meetings | Calendar reminders and meeting alerts |

Each entry has:
- A **toggle** to enable or disable buzzing for that type
- A **Buzzes** counter (− and + buttons, range 1–10) — sets how many distinct pulses the band fires
- A **Test** button to feel the pattern on your band before saving
- A **Keyword filter** field (SMS, WhatsApp, and Slack only) — leave empty to buzz on all notifications, or type a word to only buzz when that word appears in the notification title or message (case-insensitive)

### Pinning apps to Notification Types

Any app in the **Apps** list can be promoted to the Notification Types section so it gets its own buzz count and test button:

1. Find the app in the **Apps** list at the bottom of the screen
2. Tap the **↑** button next to it
3. The app moves up into the Notification Types section with a toggle, buzz count stepper, test button, and a **×** remove button to send it back to the Apps list

### Apps list

The **Apps** list shows every other installed app. Each row has:
- A **toggle** to enable or disable buzzing for that app's notifications (1 buzz, no custom count)
- A **↑** button to pin it to Notification Types with a configurable count

### WB connection

The connection status card at the top shows whether WBNoti has found and connected to your WB band. If it shows **Disconnected**, make sure Bluetooth is on and the band is within range — the app will keep trying automatically. Tap **Connect** to retry immediately.

---

## Troubleshooting

**The band never buzzes**
- Check the connection status in the app — it should show **Ready**
- Make sure Bluetooth is enabled and the WB band is within range
- Confirm Notification access is granted (Step 5 above)
- On Samsung devices, confirm Accessibility access is granted (Step 6 above)
- Check that battery optimisation is disabled (Step 7 above)

**Calls don't buzz the band**
- Make sure Phone permission is granted (Step 4 above)
- On some devices, go to **Settings → Privacy → Permission manager → Phone** and confirm WBNoti is listed as **Allowed**

**WhatsApp notifications don't buzz the band (Samsung)**
- Confirm Accessibility access is granted (Step 6 above) — this is the Samsung-specific workaround

**The app stops working after a while**
- This is almost always battery optimisation killing the background service — repeat Step 7
- On Samsung, also check **Settings → Device care → Battery → Background usage limits**

**The band buzzes for the wrong things / too often**
- Use the toggles in Notification Types or the Apps list to turn off notifications for specific apps
- Lower the buzz count on noisy notification types using the − button
- For SMS, WhatsApp, or Slack, use the **Keyword filter** field to only buzz for messages containing a specific word — for example, type `urgent` to only buzz when that word appears in the notification

---

## How it works

WBNoti uses Android's `NotificationListenerService` to intercept notifications and `TelephonyManager` for incoming calls. It then sends haptic commands to the WB band over BLE using the band's protocol (reverse-engineered from the [Noop](https://github.com/suyashkumar/whoop) project, MIT licensed).

On Samsung devices, an `AccessibilityService` runs alongside the notification listener as a fallback, since Samsung's system blocks certain apps (WhatsApp, SMS) from reaching third-party listeners.

A deduplication window prevents the same notification triggering multiple buzzes when both services are running simultaneously.

---

## Building from source

```bash
git clone https://github.com/wbnoti/WBNoti.git
cd WBNoti
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

For a release build (requires a signing keystore):

```bash
./gradlew assembleRelease
```

---

## Disclaimer

This app uses an undocumented BLE protocol to communicate with WB bands. It is not affiliated with, endorsed by, or connected to WB in any way. Use at your own risk. The protocol may change in future firmware updates.

## License

MIT
