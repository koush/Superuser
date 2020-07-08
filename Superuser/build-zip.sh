#!/bin/bash

tmpdir="$(mktemp -d)"
ORIG="$(pwd)"

rm -f update-su.zip
pushd "$tmpdir"
mkdir -p META-INF/com/google/android
cp "$ORIG"/assets/update-binary META-INF/com/google/android/update-binary
cp "$ORIG"/assets/install-recovery.sh .
cp -R "$ORIG"/libs/{x86,mips,armeabi} .

zip -r "$ORIG"/update-su.zip *
popd
rm -Rf "$tmpdir"
