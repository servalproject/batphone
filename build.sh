#!/bin/sh
#
# Copyright (C) 2012 Serval Project, Inc.
#
# To build Serval BatPhone the first time, run this script in a standard Posix
# shell: /bin/sh build.sh or ./build.sh
#
# BUILDING ON WINDOWS REQUIRES THIS TO ALL HAPPEN FROM IN A CYGWIN SHELL, AND
# MAY STILL CAUSE PROBLEMS.  WE RECOMMEND LINUX OR OSX AS THE BUILD ENVIRONMENT
# FOR SERVAL BATPHONE.
#
# After running this script the first time, you should be able to build within
# Eclipse.   (For some unfathomable reason, Eclipse doesn't run ndk-build to
# create the android/java conformant native binaries.)

# Exit on error
set -e

# Setup and clone the submodules used.
git submodule init
git submodule update

if [ -z "$(which android)" ]; then
  echo "Unable to find 'android' executable."
  echo "Have you set up your build environment correctly?"
  exit 1
fi

# Update android SDK directory in local.properties.
target_id=$(android list targets | grep '"android-10"' | awk '{print $2}')
if [ -z "$target_id" ]; then
  echo "Unable to find 'android-10' target."
  echo "Have you installed the Android SDK for API level 10?"
  echo "The currently installed targets are:"
  android list targets --compact
  exit 1
fi
android update project --target "$target_id" --path .

# Build everything.  This calls ndk-build but you must have NDK_ROOT env var set
# to the root of the NDK directory.
if [ ! -d "${NDK_ROOT?}" ]; then
  echo "\$NDK_ROOT ($NDK_ROOT) is not a directory"
  exit 1
fi
if [ ! -x "${NDK_ROOT}/ndk-build" ]; then
  echo "\$NDK_ROOT ($NDK_ROOT) does not appear to be an Android NDK (missing 'ndk-build' executable)"
  exit 1
fi

ant debug
