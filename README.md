# xgimi

Small, clean fixes for XGIMI projectors running Google TV.

## SuperFrame Keeper

SuperFrame switches the HORIZON 20 series to its 1080p high refresh mode (up to 240 Hz). The firmware turns it off every time the projector boots or goes to standby, so a PC that stays at 1080p 240 Hz is shown again as upscaled 4K at 60 Hz after each wake. SuperFrame Keeper remembers that you enabled SuperFrame and turns it back on after boot and after each wake.

It installs as a regular app. It does not need root, does not touch system files or the firmware, and uninstalls with one command.

### Root cause

The setting is not lost by accident. XGIMI's power service, `com.xgimi.xrmservice` (`PowerService`), calls the vendor settings provider to disable SuperFrame (and 3D) in `onCreate()` at boot and on every screen off, and never restores it:

```
xPowerService: xgimi screen off
xPowerService: tryCloseSuperFrame called begin
XgimiProvider: call() called with: method = setSuperFrame, arg = false
MWManager: setSuperFrame() called with: ..., enable = false
```

The settings menu goes through the same path: `TvVideoManager.setSuperFrame()` writes `Settings.Global` `CFG_SUPER_FRAME` and calls `IRenderService.setSuperFrameEnable()`. Writing the global key alone only changes what the menu shows; the display pipeline stays at 4K 60 Hz.

The settings app exports this provider without a permission, so any app can make the same call as the menu:

```
adb shell content call --uri content://com.mediatek.tv.settings.xgimi.provider --method setSuperFrame --arg true
```

The provider does nothing when `CFG_SUPER_FRAME` is already 1.

### How it works

A foreground service started at boot:

- observes `CFG_SUPER_FRAME`; a change while the screen is on is the user's choice and is saved, a change while the screen is off comes from the firmware and is ignored
- 10 s after `SCREEN_ON`, and 10 s after the service starts, calls `setSuperFrame` with `true` if the user wants SuperFrame and the key is 0

The delay matters. After `SCREEN_ON`, the vendor wake sequence (DLP reinit over I2C, then laser on) takes about 6.5 s. Applying SuperFrame in the middle of it leaves the display pipeline half initialized (a small image on the left half of the screen) until a full reboot.

### Build

Requires a JDK and the Android SDK command line tools with `platforms;android-34` and `build-tools;35.0.0`. No Gradle.

```
ANDROID_HOME=/path/to/android-sdk superframe/build.sh
```

The script recreates `superframe/build/` and signs `build/superframe.apk` with the standard Android debug keystore (`~/.android/debug.keystore`, created if missing, override with `KEYSTORE`).

### Install

Enable wireless debugging in the projector developer options, then:

```
adb connect <projector-ip>:5555
adb install -r superframe/build/superframe.apk
adb shell am start-foreground-service -n com.serveurperso.xgimi.superframe/.KeeperService
```

The last command starts the service once; after that it starts by itself at each boot. Enable SuperFrame in the picture settings so the service records your choice.

To check what it does:

```
adb logcat -s SuperFrameKeeper
```

### Uninstall

```
adb uninstall com.serveurperso.xgimi.superframe
```

### Tested on

HORIZON 20 Max (XR16A), Android 14, system firmware V1.4.163, PC input at 1080p 240 Hz: standby cycles and full reboots.

### Limitations

- The 10 s delay is measured on one HORIZON 20 Max; a slower wake would need a longer one.
- The provider is internal to XGIMI's firmware and can change with an update.
