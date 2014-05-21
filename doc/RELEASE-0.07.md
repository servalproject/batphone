Release Notes for Serval Mesh 0.07
----------------------------------
[Serval Project][], 11 December 2011

These notes accompany the release of version 0.07 of the [Serval Mesh][] app
for [Android 2.1 “Eclair”][] and above.  This release succeeds version 0.06.

The following features and bug fixes have been added:

 * Allow for no routing engine (requires change to strings before appearing in
   UI)
 * Preparation and installation cleanup
 * Improve unknown or unrooted device support
 * Prevent retrying a stuffed config
 * Prevent screen off filter for Samsung Galaxy 2 support
 * Fix bug with supported device support
 * Read command output directly
 * Always extract binaries that are not in source control
 * Allow up to 3 seconds for adhoc mode to start (looks like client mode till
   then if no peers)
 * Tweak layout and add phone number label
 * Refactor wifi mode detection and preparation control flow
 * Ensure all wifi mode changes are listened to
 * Retry root commands, don’t always run prep on startup
 * Don’t rerun preparation if unsupported handset.
 * Improve behaviour of unrooted handsets
 * Disallow adhoc mode if wifi chipset is re-selected after crashing the phone
 * Prevent logging of executed processes that are already captured somewhere
 * Substantial work towards nice integrated preparation wizard that starts up
   first and does all the detection and unpacking before handing over to the
   main activity (now called dashboard).
 * Fixed some bugs with new wizard that handles setup and checking that we have
   everything we need, and sets the framework for then guiding the user through
   testing of experimental chipset detections, and ultimately we hope uploading
   working scripts.
 * Added wireless-tools to jni build. This provides source for iwconfig, and
   also gets what we need to produce a fast JNI iwconfig and also add
   iwconfig() command to edify, which can be used to speed up mode switches by
   letting us replace the sleep(3) with a function that waits exactly until the
   wireless interface comes up.
 * Slight rearrangement of code for handling multiple and non detections of
   wifi chipset. (Still needs iwconfig JNI before it can be finished)
 * Progress towards having iwconfig output available from Java for testing wifi
   mode status.
 * we can now get the output of iwconfig natively from in the JVM. Next step is
   to use it to verify effectiveness of mode switches.
 * fixed some more bugs, now ifconfig and iwconfig output can both be gathered
   from in java.
 * Rearranged and hopefully fixed correct running of preparation wizard.
 * Added support for reading wifi.conf files for guessing support.
 * Added initial support for Galaxy S2
 * Fixed error in galaxy s2 detection script.
 * Fixes to GalaxyS2 compatability and bug fix in DNA to prevent segfaults.
 * We now check that the wifi mode actually changes as required — although we
   don’t yet warn the user — we just log it.
 * incorporated updated dna binary that catches errors better
 * Various things to fix hlr corruption induced dna crashes and other related
   things.
 * Incorporated fixed DNA (SIP address missing @ bug). Added check to supress
   here be dragons if wizard activity gets recreated while main application
   activity remains running. Using DNA seeded SID instead of asking for one to
   be created. Untested.
 * Incorporated modified DNA binary.
 * Various changes towards integrating new wifi detection process. Added code
   to prevent more than one instance of background task running at same time.
 * Changed indicators on preparation wizard to yellow to make visible for
   red-green colour blind. Added wake lock to preparation process and further
   interlocks to prevent double running.
 * Fixed remaining missing checks on exiting from background activities in
   preparation wizard.
 * Added first step of NaCl native library access functions. Requires latest
   serval-dna and building of libdnalib to work.
 * Modified makefile to handle NaCl-containing Serval DNA
 * NaCl native wrapping now reasonably sane, with a single class containing the
   native functions, and separate classes within that to wrap the calls.
 * Added experimental support for GT540 handset running CyanogenMod.
 * Added wrapper for NaCl safe random number source.
 * Partial de-spaghettied preparation wizard. Also replaced jiggly clock
   graphics with grey scale pulsing square, except that it doesn’t pulse due to
   some weird issue with the asynchronous task not really running
   asynchronously, or at least it seems that way.
 * Further work on integrating NaCl crypto library.
 * Updated LG GT540 control scripts using information gained from
   Android-wifi-tether support for the same.
 * Imported updates to edify from android-wifi-tether to allow use of
   `load_wifi()` and `unload_wifi()` for generic device control from in edify
   scripts (so we can make generic.edify.adhoc etc).
 * Added generic control scripts for new improved edify imported from latest
   version of android-wifi-tether.
 * Updated BUILD.txt to actually contain the build instructions for Batphone.
 * Improved BUILD.txt to automatically work out sdk.dir for local.properties
   file.
 * Added fix for missing local.properties file.
 * Improved BUILD.txt to better detect sdk directory and not stop convenience
   script.
 * attempt at fixing the obsolete build.xml file issue.
 * added git sub-module initialisation to make first-time builds even easier.
 * Fixed ordering or NaCl preparation and ndk-build.
 * Incoropated latest serval-dna commit to fix build problem with some shells.
 * Totally disabled uploading of detection logs to serval server until we build
   out the very explicit means of asking the user for permission.
 * Added layout and activity for trying experimental chipsets.
 * Fixed display of chipset in auto-detect if there are multiple matches. Now
   says “one of several possibilities”, instead of “Unsupported ….”. Also fixed
   order of chipset testing to always try non-experimental before experimental,
   so that in this case we always try the safe options before possibly jamming
   the kernel with a wrong module load, for example. This also deals with the
   problem of having stale invented support files laying around on reinstall.
 * wifi autodetect now informs user if wifi chipset is in a strange state, and
   asks them to reboot, and then quits batphone completely.
 * Removed some dead code. Fixed nasty quit-on-start bug in wifijammedactivity
   control.
 * Relaunching batphone after it realises the wifi is in a funny state now
   reminds the user to reboot their phone. This probably comes up sometimes
   when it doesn’t need to, because it doesn’t recheck that the wifi is in a
   funk. But it will do for now.
 * Improved chipset detection log display to be more meaningful, explicitly
   show chosen chipset and the capabilities the control script offers.
 * Fixed bugs in phone number extraction from hlr.dat
 * Added display of phone number on main display.
 * Added wifi refresh option to menu on main screen, partly to aid debugging.
   Progress towards “you don’t have adhoc” dialog on startup.
 * Added dialog to show when ad hoc could not be obtained.
 * Issue 141 resolved.
 * fixed potential null pointer problem.
 * Rebuild DNA and Asterisk DNA plugin, hopefully incorporating the source
   address bug fix committed to serval-dna.
 * Really fixed null-termination bug in asterisk module. Fixes issues 145, and
   probably 131 (to be confirmed).
 * Bug fix to broadcast address calculation.
 * Changed default SSID to ‘Mesh’, following interoperability agreement with
   VillageTelco.org.
 * Made BUILD.txt tolerant of trailing slash at end of path used to find adb.
 * Fixed a null pointer exception source.
 * More fixing of null pointer catching.
 * Added ARP table reading for Serval DNA and asterisk module (but not peer
   list in java). Phones with screens off in client mode can now be called
   (although what happens when the arp table entries expire?)
 * added screen-off detection code to reset wifi , but stimied by bug 146,
   which prevents us knowing when it should be done.
 * Adding commandline front end for libiwstatus for debugging.
 * Fixed wrapper function wrong function. Something is still not right, though,
   as it doesn’t show any output.
 * Delete attempt files when redetecting WiFi.
 * Fixed typo that prevented this script from being usable.
 * Made experimental for now, since we need to update edify to work with it.
 * Added fancy dlsym() code to access `libhardware_legacy` if it is available.
 * updated to also rebuild adhoc/edify interpretor binary.
 * Changed default SSID from ServalProject.org to Mesh in line with
   interoperability accord wth VillageTelco.org.
 * Generic chipset control script now works with updated adhoc/edify binary.
   But detection on installation is wonky for it.. Timing issue?
 * remarked generic experimental, because we want it to be used only if there
   is no dedicated script, as the dedicated scripts can make sure there is no
   packet filter nonsense going on, where as the `wifi_driver_load()` function
   will enable such silliness, and cause things like no reception of broadcast
   packets when the screen turns off.
 * attempted fixes and debugging for iwconfig jni bridge (looks like it might
   need root to run reliably, so will probably switch to using iwconfig from
   the command line instead).
 * Changed wifi modal management to fix some null pointer issues and use the
   command line iwconfig command to read current wifi mode instead of using the
   iwstatus jni library we made, because it looks like that the
   iwconfig/iwstatus code might need root access to work reliabily/properly.
 * AP->Client and Client->AP calling now works. Generic chipset handler now
   works.
 * Pulled in latest serval-dna code for ARP table scraping debug improvements.
 * Incorporated bug fixes to DNA reading of ARP table.
 * Incorporated ARP table parsing bug fixes in DNA and DNA Asterisk app.
 * Updated serval-dna with -m option for helping with diagnostics on root-less
   networks.
 * Force SSID from ServalProject.org -> Mesh on 0.06 -> 0.07 upgrade.
 * Added new nowirelessextensions option to chipset detect script language. (It
   is needed for G1/HTC Dream compatibility, which is still being fixed).
 * Added checks into preparation wizard to ignore lack of wifi mode reading on
   devices that don’t support it (i.e. G1/HTC Dream).
 * Added nowirelessextensions line to dream wifi detection script.
 * Work towards detecting wifi interface state without iwconfig as a fall-back
   (mainly for G1/HTC Dream).
 * G1/HTC Dream support again working. “One of several possible options”
   message should not occur now.
 * Filtered displayed chipset options to show only valid ones.
 * Made motorola droid/defy problems somewhat less bad. Install still hangs,
   but generic doesn’t get phone into infinite loop.
 * Disabled stop wifi on screen off in client mode.
 * Fixed null pointer in getting detected chipsets after force
   close/restart/reboot of BatPhone.
 * Add the translation for the first screen
 * Add SipDroid translations
 * Add the license translation — original license text stays in english
 * Add the stub for the somalian translation

-----
**Copyright 2011 Serval Project Inc.**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: https://play.google.com/store/apps/details?id=org.servalproject
[batphone]: https://github.com/servalproject/batphone
[Android 2.1 “Eclair”]: http://developer.android.com/about/versions/android-2.1.html
[CC BY 4.0]: ../LICENSE-DOCUMENTATION.md
