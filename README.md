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
