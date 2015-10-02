## Why another Superuser?
* Superuser should be open source. It's the gateway to root on your device. It must be open for independent security analysis. Obscurity (closed source) is not security.
* Superuser should be NDK buildable. No internal Android references.
* Superuser should also be AOSP buildable for those that want to embed it in their ROM.
* Superuser should also be AOSP _embeddable_, meaning a ROM can easily embed it into their Settings app.
* Maintenance and updates on both the market and source repositories should be timely.
* I want to be able to point users of my app to a Superuser solution that I wrote, that I know works, and that I can fix if something is wrong.
* Handle multiuser (4.2+) properly
* Handle concurrent su requests properly

## Why NOT use this Superuser?
* If you have any doubt about how to go recover from a critical failure, DON'T TRY THIS
* Don't use it if you're afraid of a brick

## Checking out the source

You'll need the "Widgets" dependency.

* $ mkdir /path/to/src
* $ cd /path/to/src
* $ git clone git://github.com/phhusson/Superuser
* $ cd Superuser
* $ git clone git://github.com/phhusson/Widgets

These repositories do not keep the actual projects in the top level directory.
This is because they contain tests, libs, and samples.

Make sure the SDK Platform for API 19 is installed, through the Android SDK Manager.  Install NDK Revision 9b from [here](http://developer.android.com/tools/sdk/ndk/index.html).

## Building the su and placeholder binaries

You can use ant as shown above, to build the binary, but it can also be built without building the APK.

Make sure you have the android-ndk downloaded with the tool "ndk-build" in your path.

* $ cd /path/to/src/
* $ cd Superuser/Superuser
* $ ndk-build

The su binary will built into Superuser/Superuser/libs/armeabi/su, and the placeholder will be built into Superuser/Superuser/libs/armeabi/placeholder

## Building the application

* $ ./gradlew assembleDebug

(Yes I use debug builds for the moment.)

#### Configuring the Package Name
//The Superuser distributed on Google Play is in the package name com.koushikdutta.superuser. (To be changed)
To prevent conflicts with the Play store version, the build process changes the package
name to com.thirdparty.superuser. You can configure this value by setting the following
in your build.gradle

```
applicationId "com.thirdparty.superuser"
```

## How to install?

At the moment, I'm not sure this SuperUser is stable enough for ready-to-flash zip, though I'll probably make one.
To install it, here are the needed steps:
- Rename /system/bin/app_process32 to /system/bin/app_process32.old
- Copy placeholder to system/bin/app_process32
- Ensure permissions of app_process32 are the same as of app_process32.old, including SELinux attributes (should be 0755 u:object_r:zygote_exec:s0)
- Put su file in system/xbin/
- Add /system/xbin/su --daemon in install-recovery.sh

## TODO List

Here is a list of what's left to do, to be compatible with Chainfire's SuperSU (as documented at https://su.chainfire.eu):
- -cn option (--context already implemented)
- Execute /system/su.d/
- Set supolicy.loaded property to 1
- Start as init instead of install_recovery ?
- --live option to supolicy/sepolicy-inject (see https://github.com/phhusson/sepolicy-inject)
- Check we can --context to shell, system_server, system_app, platform_app, untrusted_app (I guess there will be ptmx/SELinux problems)
- --mount-master

Here is an additional TODO list:
- Create restricted domains, which should match of basic needs. So that we can tell users "this app is not as bad as it might"
- Be able to switch to /any/ context with --context (just need to rewrite SELinux on the fly)
- Per-app SELinux domain, with apps writing full rules ?


## Contact me
* IRC: #superuser-phh @ Freenode
* mail: phh@phh.me

## Communication

This project is in REALLY early state, though some points have to be mentioned:
* For development purposes, I prefer IRC
* Any issue discussed MUST have an entry in github bugtracker
* There will be security flaws. If you find one, please first discuss it with me privately (by mail or IRC).
* If you feel you need to be aware of security flaws before disclosure, please contact me, I might create a dedicated mailing list.
