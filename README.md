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

ROM developers are welcome to distribute the official Superuser APK and binary that I publish. That will
allow them to receive updates with Google Play. However, you can also build Superuser as part of your
build, if you choose to.

There are two ways to include Superuser in your build. The easiest is to build the APK as a separate app.
To do that, simply add the local_manifest.xml as described below. The second way is by embedding it
into the native Android System Settings.

#### Repo Setup
Add the [local_manifest.xml](https://github.com/koush/Superuser/blob/master/local_manifest.xml) to your .repo/local_manifests

#### Configuring the Package Name
The Superuser distributed on Google Play is in the package name com.koushikdutta.superuser.
To prevent conflicts with the Play store version, the build process changes the package
name to com.thirdparty.superuser. You can configure this value by setting the following
in your vendor makefile or BoardConfig:

```
SUPERUSER_PACKAGE := com.mypackagename.superuser
```

#### Advanced - Embedding Superuser into System Settings

You will not need to change the package name as described above. Superuser will simply go
into the com.android.settings package. To modify the Settings app, you will need this [https://gist.github.com/koush/5059098](patch):

```diff

diff --git a/Android.mk b/Android.mk
index fe8ed2d..6dea5b0 100644
--- a/Android.mk
+++ b/Android.mk
@@ -13,6 +13,11 @@ LOCAL_CERTIFICATE := platform
 
 LOCAL_PROGUARD_FLAG_FILES := proguard.flags
 
+LOCAL_AAPT_INCLUDE_ALL_RESOURCES := true
+LOCAL_AAPT_FLAGS := --extra-packages com.koushikdutta.superuser:com.koushikdutta.widgets -S $(LOCAL_PATH)/../../../external/koush/Widgets/Widgets/res -S $(LOCAL_PATH)/../../../external/koush/Superuser/Superuser/res --auto-add-overlay
+
+LOCAL_SRC_FILES += $(call all-java-files-under,../../../external/koush/Superuser/Superuser/src) $(call all-java-files-under,../../../external/koush/Widgets/Widgets/src)
+
 include $(BUILD_PACKAGE)
 
 # Use the folloing include to make our test apk.
diff --git a/AndroidManifest.xml b/AndroidManifest.xml
index 72be71b..4171800 100644
--- a/AndroidManifest.xml
+++ b/AndroidManifest.xml
@@ -64,6 +64,29 @@
     <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
     <uses-permission android:name="android.permission.SET_TIME" />
 
+    <permission
+        android:name="android.permission.REQUEST_SUPERUSER"
+        android:protectionLevel="signature" />
+    <permission
+        android:name="android.permission.REPORT_SUPERUSER"
+        android:protectionLevel="signature" />
+
+    <permission-group
+        android:name="android.permission-group.SUPERUSER"
+        android:description="@string/superuser_description_more"
+        android:icon="@drawable/ic_action_permission"
+        android:label="@string/superuser"
+        android:priority="10000" />
+
+    <permission
+        android:name="android.permission.ACCESS_SUPERUSER"
+        android:description="@string/superuser_description_more"
+        android:icon="@drawable/ic_action_permission"
+        android:label="@string/superuser_description"
+        android:logo="@drawable/ic_action_permission"
+        android:permissionGroup="android.permission-group.SUPERUSER"
+        android:protectionLevel="dangerous" />
+
     <application android:label="@string/settings_label"
             android:icon="@mipmap/ic_launcher_settings"
             android:taskAffinity=""
@@ -72,6 +95,41 @@
             android:hardwareAccelerated="true"
             android:supportsRtl="true">
 
+        <!-- Only system/su can open this activity -->
+        <!-- This activity will then call the MultitaskSuRequestActivity to create a new task stack -->
+        <activity
+            android:name="com.koushikdutta.superuser.RequestActivity"
+            android:configChanges="keyboardHidden|orientation|screenSize"
+            android:label="@string/superuser"
+            android:launchMode="singleTask"
+            android:noHistory="true"
+            android:permission="android.permission.REQUEST_SUPERUSER"
+            android:theme="@style/RequestTheme" />
+        <!-- Only system/su can open this activity -->
+        <!-- This is activity is started in multiuser mode when the user invoking su -->
+        <!-- is not the device owner (user id 0). -->
+        <activity
+            android:name="com.koushikdutta.superuser.NotifyActivity"
+            android:configChanges="keyboardHidden|orientation|screenSize"
+            android:label="@string/superuser"
+            android:launchMode="singleTask"
+            android:noHistory="true"
+            android:permission="android.permission.REQUEST_SUPERUSER"
+            android:theme="@style/RequestTheme" />
+
+        <!-- Multiple instances of this activity can be running for multiple su requests -->
+        <activity
+            android:name="com.koushikdutta.superuser.MultitaskSuRequestActivity"
+            android:configChanges="keyboardHidden|orientation|screenSize"
+            android:exported="false"
+            android:label="@string/request"
+            android:theme="@style/RequestTheme" />
+
+        <receiver
+            android:name="com.koushikdutta.superuser.SuReceiver"
+            android:permission="android.permission.REPORT_SUPERUSER" />
+
+
         <!-- Settings -->
 
         <activity android:name="Settings"
diff --git a/proguard.flags b/proguard.flags
index 0805d68..bc0a933 100644
--- a/proguard.flags
+++ b/proguard.flags
@@ -12,6 +12,7 @@
 -keep class com.android.settings.accounts.*
 -keep class com.android.settings.fuelgauge.*
 -keep class com.android.settings.users.*
+-keep class com.koushikdutta.**
 
 # Keep click responders
 -keepclassmembers class com.android.settings.inputmethod.UserDictionaryAddWordActivity {
diff --git a/res/xml/settings_headers.xml b/res/xml/settings_headers.xml
index 156d63f..6a903e3 100644
--- a/res/xml/settings_headers.xml
+++ b/res/xml/settings_headers.xml
@@ -215,6 +215,13 @@
 
     <!-- Date & Time -->
     <header
+        android:id="@+id/superuser"
+        android:fragment="com.koushikdutta.superuser.PolicyNativeFragment"
+        android:icon="@drawable/ic_action_permission"
+        android:title="@string/superuser" />
+
+    <!-- Date & Time -->
+    <header
         android:id="@+id/date_time_settings"
         android:fragment="com.android.settings.DateTimeSettings"
         android:icon="@drawable/ic_settings_date_time"

```
