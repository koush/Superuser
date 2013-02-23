# Checking out the source

You'll need the "Widgets" dependency.

* $ git clone git://github.com/koush/Superuser
* $ git clone git://github.com/koush/Widgets

These repositories do not keep the actual projects in the top level directory.
This is because they contain tests, libs, and samples.

# Eclipse

In Eclipse, import Widgets/Widgets and Superuser/Superuser. It should Just Work (TM).

# Building the su binary

Make sure you have the android-ndk downloaded with the tool "ndk-build" in your path.

* $ cd Superuser/Superuser
* $ ndk-build

The su binary will built into Superuser/Superuser/libs/armeabi/su.
