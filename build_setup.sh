#!/bin/bash
# Copyright 2016 Flinders University
#
# This script will setup your work folder so that everything is ready to build
# for the first time.
#
# After running this script once, the application can be built with $ ant debug

# Exit on error.
set -e

# Change to the directory that contains this script.
case "$0" in
*/*) cd "${0%/*}";;
esac

if [ ! -f "${NDK_ROOT}/ndk-build" ]; then
   echo "Please ensure that NDK_ROOT points to a valid android NDK." >&2
   echo "See INSTALL.md for more details." >&2
   exit 1
fi

shopt -s extglob

export NDK_PLATFORM=$(sed -n -e '/^APP_PLATFORM *:\?= */s///p' jni/Application.mk)
case "$NDK_PLATFORM" in
android-+([0-9])) ;;
*)
   echo "Unsupported NDK target '$NDK_PLATFORM'" >&2
   echo "Please ensure the jni/Application.mk file is present and correct." >&2
   echo "See INSTALL.md for more details." >&2
   exit 1
   ;;
esac

git submodule init
git submodule update
pushd jni/libsodium
ANDROID_NDK_HOME="$NDK_ROOT" ./dist-build/android-arm.sh
popd
