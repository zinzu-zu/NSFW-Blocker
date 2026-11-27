# phone-kit

phone-kit puts the whole Android lock on a phone from your Mac, over a USB cable, and keeps it up to date. Your choices live in one file, `my-phone.conf`, so every phone gets the same setup. For the quick version, see [README-short.md](README-short.md).

What ends up on the phone:

- **OwnDroid** is the device owner. It holds the locks and opens only with your password or code.
- **Feed Guard** pauses other browsers and the Instagram and Reddit apps. It also pauses the apps on your blocklist, keeps the screen black and white, guards the Firefox add-on, runs banking mode and runs the morning news block.
- **Watch Limit** limits YouTube to a daily amount of screen time.
- **Private DNS** is locked to your NextDNS address. The date, time and time zone are automatic and locked.
- **Firefox** is the browser, with the **NSFW Blocker** add-on.
- **Restrictions:** no VPN, no new users, no safe boot, no network reset, and the default apps stay as they are. **Debug features** comes last, and it ends USB access.

A first setup takes about 20 minutes. About 10 taps stay yours, because Android and OwnDroid only take them on the phone's screen. The kit says what to tap, waits, and checks that it happened. An update takes about 10 minutes.

It was first used on a real phone on 20 Nov 2026: a Pixel on Android 16, set up from macOS.

## What's in the folder

| | |
|---|---|
| `phone-kit` | The tool itself. You don't need to open it |
| `Set up a phone.command` | Double-click this to set up or update the phone that's plugged in. It can sit next to `phone-kit` or in a folder inside it, such as "Important Files - Look up here" |
| `my-phone.conf` | Your settings. It's next to `Set up a phone.command` or next to `phone-kit`, and the first run makes it from `my-phone.conf.example` |
| `apks/` | The apps: Feed Guard, Watch Limit and OwnDroid. `apks/extra` takes any other APK |
| `HOW TO UPDATE.md`, `HOW TO UNLOCK.md` | Step-by-step guides for later |
| `test/` | The kit's own tests |

## Before the first phone

1. **adb**, the tool that talks to the phone over USB: `brew install android-platform-tools`. Or skip this step, because **Set up a phone.command** offers to download it from Google into the kit's folder.
2. **The apps** go in `apks`:
   - `FeedGuard-<version>.apk` and `WatchLimit-<version>.apk`. You can keep several versions side by side, and the kit installs the highest one.
   - OwnDroid: on the first run the kit downloads OwnDroid v8.4 from its GitHub release (never the testkey build). It keeps that copy and its fingerprint (`OwnDroid.apk.sha256`), so every phone gets the same one.
   - `apks/extra`: any other APK, such as Firefox. Each one is installed once on every phone.
3. **The add-on:** a signed `.xpi` of NSFW Blocker, in `apks` or in the folder that `ADDON_FILE` names. The kit reads the version inside each file and uses the newest signed one, so the file names don't matter. Firefox only takes copies that Mozilla signed.
4. **Your settings:** fill in `my-phone.conf` (see [Your settings](#your-settings)). Only the NextDNS address is required.
5. **Optional, the time-lock:** owndroid-key, installed as [its README](../owndroid-key/README.md) says. Without it, OwnDroid is locked with a password that you keep on the Mac.

## A new or reset phone

1. Factory-reset it (Settings › System › Reset options › Erase all data), or take it out of the box. In the setup screens, **don't sign in to Google** or add any account, because Android only accepts a device owner on a phone with no accounts. Do connect to Wi-Fi.
   - Not reset? Then remove every account in Settings › Passwords, passkeys & accounts. Also remove any other user or private space. Android 16 can have a hidden "Supervising" profile: list it with `adb shell pm list users`, and remove it with `adb shell pm remove-user NUMBER`.
2. Settings › About phone: tap **Build number** 7 times. Then Settings › System › Developer options: turn on **USB debugging**.
3. Plug the phone into the Mac, unlock it, and double-click **Set up a phone.command**. On the phone, tap **Allow** on "Allow USB debugging?".
4. Do what it says. Keep the phone unlocked and the cable in. When it stops for a tap, do the tap, then press Enter in its window.

If it stops for any reason (the cable came out, you closed the window, a check didn't pass), double-click it again. It goes on from where it was.

### The 15 steps

| | Step | Who |
|---|---|---|
| 1 | OwnDroid installed and made the device owner | the kit |
| 2 | The secure settings permission taken from every app but Feed Guard, since an app with it could switch Private DNS off | the kit |
| 3 | Feed Guard and Watch Limit installed (the newest in `apks`), with their accessibility access on and **Pause app activity if unused** off | the kit |
| 4 | Private DNS set to your NextDNS address, after checking it works. Automatic date, time and time zone on | the kit |
| 5 | Firefox installed and made the default browser | you install it from the Play Store, unless its APK is in `apks/extra` |
| 6 | In OwnDroid's settings: **Shortcuts** off and the **API** on | you, about 5 taps. The kit types the key |
| 7 | Feed Guard and Watch Limit made delegated admins with **Change package state** | you, in OwnDroid |
| 8 | Feed Guard's settings from `my-phone.conf` | the kit writes the file, and you import it in OwnDroid |
| 9 | Watch Limit's daily limit, locked | you |
| 10 | OwnDroid's restrictions, the hidden apps, and Block uninstall and Disable user control for Feed Guard, Watch Limit and Firefox | the kit, through OwnDroid's API |
| 11 | OwnDroid's API and Shortcuts off | you, and the kit checks |
| 12 | A check of everything above | the kit |
| 13 | In Firefox: the NextDNS test page, the add-on, then instagram.com | you. The kit copies the add-on to the phone's Downloads |
| 14 | **Debug features** on, the last switch, which ends USB access | you. The kit waits for the phone to disconnect |
| 15 | OwnDroid's lock | owndroid-key if you use it, otherwise your OwnDroid password |

Installing the add-on from the file: in Firefox, open ⋮ › Settings › **Install add-on from file**, pick `nsfw-blocker-<version>.xpi`, tap **Add**, and allow it in private tabs. If that menu item isn't there, go to Settings › About Firefox, tap the Firefox logo 5 times, and go back.

### The first double-click

macOS may refuse to open the file the first time, because it was downloaded:

- macOS 15 and newer: click **Done**, then go to System Settings › Privacy & Security, scroll down, and click **Open Anyway** next to "Set up a phone.command". Then double-click it again.
- macOS 14 and older: Control-click it, then choose **Open**, then **Open** again.

## Updating a phone that's already locked

[HOW TO UPDATE.md](HOW%20TO%20UPDATE.md) has the steps. In short:

1. Open OwnDroid with your password. In User restrictions, switch **Debug features** off.
2. Turn on **USB debugging** in Developer options and plug the phone in.
3. Double-click **Set up a phone.command**. It skips what's already in place, and it:
   - installs any newer Feed Guard or Watch Limit from `apks`
   - puts a newer add-on in the phone's Downloads and tells you to install it (each version is offered once)
   - writes Feed Guard's settings again if `my-phone.conf` changed, for you to import (the import replaces all of Feed Guard's settings, so make changes in the file, not in OwnDroid)
4. It ends with Debug features. Switch it back on, last. Then leave OwnDroid and open it again: it must ask for the password.

OwnDroid itself stays on v8.4 on purpose. Its fingerprint check stops a changed copy from being installed.

## Your settings

`my-phone.conf` is a plain text file in numbered sections. Each setting is `NAME=value`, and `#` starts a note. A list can be written on one line, separated by commas, or one entry per line under its name:

```
BLOCK_APPS=
    com.google.android.apps.photos     # Google Photos
    com.facebook.katana                # Facebook
```

Leave a space before each `#`. An app's package name is at the end of its Play Store link: `details?id=com.whatsapp` means `com.whatsapp`. After you change the file, run the kit again (see [Updating](#updating-a-phone-thats-already-locked)).

| Setting | If left empty | What it does |
|---|---|---|
| `NEXTDNS_HOST` | required | Your NextDNS address for Android, from my.nextdns.io › Setup › Android. Private DNS is set to it and locked |
| `ONLY_LISTED_APPS` | `no` | `no` = blocklist mode: every app runs except other browsers, Instagram and Reddit, and `BLOCK_APPS`. `yes` = allowlist mode: only `APP_LIST`, Firefox and the phone's basics run |
| `BLOCK_APPS` | nothing extra | The blocklist: apps that are always paused |
| `APP_LIST` | nothing | The allowlist: apps never paused. It only matters in allowlist mode. Other browsers and Instagram or Reddit apps can't be on it |
| `HIDE_APPS` | nothing | Apps OwnDroid hides completely. The example hides Chrome, Firefox Focus and NextDNS |
| `PROTECT_APPS` | nothing extra | More apps that can't be uninstalled, stopped or cleared. Feed Guard, Watch Limit and Firefox always are |
| `LOCK_DEFAULT_APPS` | same as `ONLY_LISTED_APPS` | `yes` locks the default browser, home screen and phone app (Android 14 and newer) |
| `BLACK_AND_WHITE` | `yes` | The screen stays black and white |
| `COLOR_APPS` | the maps apps | Apps that still show colour. `none` = no colour anywhere |
| `BANK_APPS` | Nubank | Bank apps that refuse to open while the lock is on. Opening one through **Open bank app** switches Feed Guard and Watch Limit off for 10 minutes. `none` = banking mode off |
| `NEWS_SITES` | Feed Guard's list | Sites blocked in Firefox for 2.5 hours after you wake up. Each one covers its subdomains. `none` = no sites |
| `NEWS_APPS` | Feed Guard's list | News apps paused during that time, besides apps that call themselves news apps |
| `ADDON_FILE` | only `apks` | A signed `.xpi`, or a folder of them. The newest signed version goes on the phone |
| `ADDON_PAGE` | nothing | Only when there's no `.xpi`: the add-on's page for the kit to open in Firefox |
| `USE_OWNDROID_API` | `yes` | `no` = you switch the step 10 restrictions on in OwnDroid yourself, from the list the kit shows |

Feed Guard gets these through OwnDroid's managed configuration, as [its README](../feed-guard/README.md) explains. The morning block's timing (`news_minutes`, `news_gap_hours` and the hours of the day) is set only in OwnDroid.

## Checking a phone

`phone-kit check` (in Terminal, in the kit's folder) lists what's in place on the plugged-in phone and what isn't, without changing anything. Like setup, it only reaches a phone while Debug features is off.

## How OwnDroid's API is used

OwnDroid's API lets an app with a key change OwnDroid's settings without opening OwnDroid. The kit uses it only during setup, with a new random key for each phone. The kit doesn't finish while the API answers, and it deletes the key once Debug features is on. OwnDroid's Shortcuts work like the API without a key, so the kit has you turn them off and checks again at the end. To check whether either still answers, it switches the harmless **Set user icon** restriction on and back off. If the phone shows "Shortcuts are disabled" during setup, that was the kit checking.

## What it doesn't do

- Some steps stay taps: delegated admins, importing Feed Guard's settings, OwnDroid's own settings and Debug features. Android only accepts them on OwnDroid's screen.
- You do these yourself: signing in to Google, installing from the Play Store, Watch Limit's limit and installing the add-on in Firefox.
- Android 14 and older don't report Block uninstall and Disable user control, so on those phones the kit asks you to check them in OwnDroid.
- While Debug features is on, nothing reaches the phone over USB, the kit included. That's the point of the lock.
- A factory reset removes the whole lock, and setup starts again from step 1.

## Tests

`bash test/run.sh` sets up pretend phones. `test/fake-adb` answers the way Android, OwnDroid 8.4 and Feed Guard do, and `test/taps` plays the person tapping. The tests cover:

- whole setups on Android 10, 14 and 16
- both app modes
- the API route and the taps route
- stops and restarts, and a pulled cable
- updates: newer APKs and newer add-ons
- the settings file's formats
- Set up a phone.command, including from a subfolder

The tests need bash and python3.
