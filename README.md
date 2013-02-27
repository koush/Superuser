## Why another Superuser?
* Superuser should be open source. It's the gateway to root on your device. It must be open for independent security analysis. Obscurity (closed source) is not security.
* Superuser should be NDK buildable. No internal Android references.
* Superuser should also be AOSP buildable for those that want to embed it in their ROM.
* Superuser should also be AOSP _embeddable_, meaning a ROM can easily embed it into their Settings app.
* Maintenance and updates on both the market and source repositories should be timely.
* I want to be able to point users of my app to a Superuser solution that I wrote, that I know works, and that I can fix if something is wrong. Yes, this is selfish: Carbon does not work with some versions of Chainsdd's Superuser. SuperSU works great, but I am not comfortable pointing a user to a closed source su implementation.
* Handle multiuser (4.2+) properly
* Handle concurrent su requests properly

## Checking out the source

You'll need the "Widgets" dependency.

* $ mkdir /path/to/src
* $ cd /path/to/src
* $ git clone git://github.com/koush/Superuser
* $ git clone git://github.com/koush/Widgets

These repositories do not keep the actual projects in the top level directory.
This is because they contain tests, libs, and samples.

## Eclipse

In Eclipse, import Widgets/Widgets and Superuser/Superuser. It should Just Work (TM).

## Ant

Same old, same old.

* $ mkdir /path/to/src
* $ cd /path/to/src
* $ cd Superuser/Superuser
* $ ant release

Outputs:
* bin/update.zip - Recovery installable zip
* bin/Superuser.apk - Superuser Android app
* libs/armeabi/su - ARM su binary
* libs/x86/su - x86 su binary

## Building the su binary

Make sure you have the android-ndk downloaded with the tool "ndk-build" in your path.

* $ cd /path/to/src/
* $ cd Superuser/Superuser
* $ ndk-build

The su binary will built into Superuser/Superuser/libs/armeabi/su.



## Building with AOSP, CyanogenMod, etc

TODO!
