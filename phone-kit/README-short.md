# phone-kit: the short version

This kit locks an Android phone from your Mac, over a USB cable. Run it again later to update the phone. Everything you can choose is in `my-phone.conf`.

## Set up a new phone

1. Reset the phone, or remove all of its accounts. Don't sign in to Google yet.
2. Turn on USB debugging: Settings › About phone, tap **Build number** 7 times. Then Settings › System › Developer options › **USB debugging**.
3. Plug the phone into the Mac and double-click **Set up a phone.command**.
4. Do what the window says. Press Enter after each tap on the phone.

If it stops, double-click it again. It goes on from where it was.

## Update the phone, or change a setting

1. Open **OwnDroid** with your password. It's in `owndroid-password.txt` on the Mac.
2. In OwnDroid › User restrictions, switch **Debug features** off. Then turn on **USB debugging** again.
3. Plug the phone in and double-click **Set up a phone.command**.
4. At the end, switch **Debug features** back on.

New app versions go in the `apks` folder, and the kit picks the newest one. The newest signed add-on is copied to the phone's Downloads, and the window tells you how to install it.

## Change what's blocked

Open `my-phone.conf` in TextEdit, change it, save it, then do the update above.

- **Block an app:** add its package name under `BLOCK_APPS`, on its own line, indented.
- **Morning news sites:** under `NEWS_SITES`.
- **Apps that keep colour:** under `COLOR_APPS`.

An app's package name is the end of its Play Store link. For example, `details?id=com.whatsapp` means `com.whatsapp`.

More detail is in [README.md](README.md).
