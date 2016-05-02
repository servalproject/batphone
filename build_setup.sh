#!/bin/bash -e
# Copyright (C) 2016 Serval Project, Inc.
#
# This script will setup your work folder so that everything is ready to build for the first time
#
# After running this script once, the application can be built with $ ant debug
#

if [ ! -f "${NDK_ROOT}/ndk-build" ]; then
   echo "Please ensure that NDK_ROOT points to a valid android NDK"
   echo "See INSTALL.md for more details"
   exit
fi

git submodule init
git submodule update
pushd jni/libsodium
./autogen.sh
ANDROID_NDK_HOME="$NDK_ROOT" ./dist-build/android-arm.sh
popd
