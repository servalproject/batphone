#!/bin/csh -f

@ n = $1 + 2
set dev=`adb devices| tail +${n} | awk '{ print $1;}' | head -1`
echo $dev
shift

adb -s $dev $*

