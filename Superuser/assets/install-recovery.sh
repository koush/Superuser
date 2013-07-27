#!/system/bin/sh
mount -orw,remount /
mkdir /superuser
mount -t tmpfs swap /superuser
mount -oro,remount /
 
cp /system/xbin/su /superuser
chmod 6755 /superuser/su
mount -oro,remount /superuser
/superuser/su --daemon &