The aim of this file is to explain the SELinux policy rules have been decided for implementation, either as a patch on AOSP, or a binary modification with sepolicy-inject.


## Mentioned domains:

- su //The domain of the su daemon itself.
- su\_sensitive //A "super domain", heavily protected, with greater power
- untrusted\_app //The domain of an app. Most roots apps will come from there
- shell //The domain when in adb shell


## su domain should be able to:
- Access audio devices
- Access network, incl. configuration

## su domain MUST NOT be able to:
- have direct access to any partition
- be able to remount a partition
- write to cache partition/reboot to recovery
- access to another app's folder
- maintain su context "forever" (except daemon obviously)

## su\_sensitive should be able to:
- remount system partition (only if verity is disabled?)

## su\_sensitive MUST NOT be able to:
- Rewrite keystore/boot.img

## Evolutions which would make SU more powerful, but needs deeper changes:
- Some files in system/etc could be mount --bindable from su-context (mixer\_paths, hosts, ...?)
- Another domain whose only aim would be to resign keystore/boot.img/system.img

## Special case of su\_sensitive:
su\_sensitive activation MUST get full user attention, and remind him everyday he granted this right to an app.
For such a context, doing on-execution popup is almost dangerous, because user might/will believe this is a one-time permission.
Getting su\_sensitive context means the app is able to stay root forever, and this is how it must be reported to the user.
The user have to go in SuperUser's settings, explicitly select the app to get su\_sensitive, and type his password.



## Variations:
- There will always be an "eng" build with "su\_sensitive" permissive
- The full lists mentioned before refers to "user" build
- A "cts" build can be added, which would have "user" settings, minus the one breaking neverallow/CTS
