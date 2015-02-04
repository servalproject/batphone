Building and Installing Serval Mesh
===================================
[Serval Project][], March 2014

These are instructions for building a *debug mode* [APK][] of the [Serval
Mesh][] app for Android from [source code][batphone] and installing it on an
Android device.  These instructions only use the Unix command line, and are
not suitable for [Eclipse][] or any other IDEs.

To build an auto-upgradable *debug mode* [APK][], refer to the [auto upgrade][]
instructions.

To build a *release mode* [APK][] of the [Serval Mesh][] app (alpha, beta or
final), refer to the [release build][] instructions.

Assumed knowledge
-----------------

The commands in this document are [Bourne shell][] commands, using standard
quoting and variable expansion.  Commands issued by the user are prefixed with
the shell prompt `$` to distinguish them from the output of the command.
Single and double quotes around arguments are part of the shell syntax, not
part of the argument itself.

These instructions assume the reader is proficient in the Unix command-line
shell and has general experience with setting up and using software development
environments.

Supported Platforms
-------------------

These instructions are suitable for the following platforms;

 * [Debian][] Linux, ix86 and x86\_64
 * Mac OS X 10.7 “Lion”, x86\_64

Other Linux distributions, eg, [Ubuntu][] and [Fedora][], should work if
sufficiently recent.

Other platforms for which the [Android SDK][] is available, such as Microsoft
Windows, might work (eg, using [Cygwin][]), but are not tested or supported.

Step 1 - Download
-----------------

Requirements; for this step you will need:

 * a recent version of [Git][] installed;
 * the *git* executable must be in your PATH;
 * HTTPS access to the default port (433) of github.com;
 * a directory in which the [Serval Mesh][] source code can be downloaded and
   built, assumed to be `$HOME/src` in all these examples;
 * at least 80 MiB of free disk space.

The [Serval Mesh][] source code is available from [GitHub][], in a repository
called [batphone][].  Download it using the following commands:

    $ cd ~/src
    $ git clone https://github.com/servalproject/batphone.git
    Cloning into 'batphone'...
    remote: Counting objects: 20167, done.
    remote: Compressing objects: 100% (6553/6553), done.
    remote: Total 20167 (delta 11993), reused 19945 (delta 11821)
    Receiving objects: 100% (20167/20167), 16.98 MiB | 273 KiB/s, done.
    Resolving deltas: 100% (11993/11993), done.
    $ cd batphone
    $ git submodule init
    Submodule 'jni/serval-dna' (https://github.com/servalproject/serval-dna.git) registered for path 'jni/serval-dna'
    $ git submodule update
    Cloning into 'jni/serval-dna'...
    remote: Counting objects: 13721, done.
    remote: Compressing objects: 100% (6451/6451), done.
    remote: Total 13721 (delta 8732), reused 12143 (delta 7197)
    Receiving objects: 100% (13721/13721), 12.92 MiB | 13.00 KiB/s, done.
    Resolving deltas: 100% (8732/8732), done.
    Checking connectivity... done
    Submodule path 'jni/serval-dna': checked out '06d762031475ce4706a5ca3e6bd05df1dc3a5798'
    $

If the command fails:

 * Check that the requirements above are all met;
 * Contact the [Serval Project Developers][] Google Group.

Step 2 - Choose a version
-------------------------

In [Git][], each version has its own named *branch* or *tag*.  Common
branches and tags are:

 * the head of the [master branch][] is always the latest public release
 * the head of the [development branch][] is the latest unstable, “bleeding
   edge” code
 * every release is tagged with its [version number][]

Choose which version you want to build, and check it out using these
commands:

    $ cd ~/src/batphone
    $ git checkout master
    Switched to a new branch 'master'
    $ git submodule update
    Submodule path 'jni/serval-dna': checked out '8249f65f8f0cfbf0fb121fb5558a39572cd5e8b0'
    $

Step 3 - install development tools
----------------------------------

The following packages should be available as part of your operating system's
development tools packages:

 * Java Development Kit (JDK) for Java 1.6 or higher, for example [OpenJDK][]
   or [Oracle Java SE][] -- **JRE (Java runtime environment) alone is not
   enough, the full JDK is required**

 * [Apache Ant][] build tool, version 1.9 or higher

 * [pandoc][] markup converter, version 1.12 or higher (this tool is optional
   for debug builds -- if absent, some pages of the app's Help section are not
   created, which does not interfere with testing)

Download the the following packages from **android.com** and install them in
directories of your choice:

 * revision 18 or later of [Android SDK Tools][] (Software Development Kit),
   with all its requirements and dependencies satisfied -- see the [SDK
   installation instructions][]

 * [SDK platform package][] for [Android 2.3.3][], which provides Android API
   level 10 (the built Batphone app will install and run on [Android 2.2][]
   (API level 8); the higher platform package is only needed for code that uses
   [Java reflection][] to detect API level 10 features and only use them if
   present)

 * revision 7b or later of [Android NDK][] (Native Development Kit) and all its
   requirements

Step 4 - set up environment
---------------------------

Before building the APK for the first and all subsequent times, the following
environment must be set up.  A good way to do this is in the `$HOME/.profile`
file.

 * The `SDK_ROOT` environment variable must be the absolute path of the root
   directory of the installed [Android SDK][].

 * The `NDK_ROOT` environment variable must be the absolute path of the root
   directory of the installed [Android NDK][].

 * The `PATH` environment variable must contain:
    - `$SDK_ROOT/tools`
    - `$SDK_ROOT/platform-tools`
    - `$NDK_ROOT`
    - the directory containing the `ant` executable

Step 5 - build the APK
----------------------

Requirements; for this step you will need:

 * the [batphone][] source code as per Steps 1 and 2 above;
 * the development tools installed as per Step 3 above;
 * the environment set up as per Step 4 above;
 * at least 170 MiB of free disk space.

The following procedure will build a *debug mode* APK with no auto-upgrade
bundle, suitable for testing by an individual developer.

Run the [build.sh](./build.sh) script (was named `BUILD.txt` in versions prior
to 0.90), which will take a few minutes and produce a lot of output as it
works:

    $ cd ~/src/batphone
    $ ./build.sh
    Submodule 'jni/serval-dna' (https://github.com/servalproject/serval-dna.git) registered for path 'jni/serval-dna'
    Cloning into 'jni/serval-dna'...
    remote: Counting objects: 8475, done.
    remote: Compressing objects: 100% (2437/2437), done.
    remote: Total 8475 (delta 5997), reused 8464 (delta 5989)
    Receiving objects: 100% (8475/8475), 4.19 MiB | 176 KiB/s, done.
    Resolving deltas: 100% (5997/5997), done.
    Submodule path 'jni/serval-dna': checked out 'a29f685bdd0ca49acc17ea0c19c8b782ff5f3790'
    Updated project.properties
    Updated local.properties
    build.xml: Found version-tag: custom. File will not be updated.
    Added file ./proguard-project.txt
    Buildfile: /home/USERNAME/src/batphone/build.xml

    -set-mode-check:

    -set-debug-files:

    -check-env:
     [checkenv] Android SDK Tools Revision 22.2.1
     [checkenv] Installed at /home/USERNAME/serval/android-sdk-linux-r22.2.1

    -setup:
         [echo] Project Name: batphone
      [gettype] Project Type: Application

    -set-debug-mode:

    -debug-obfuscation-check:

    version:
         [echo] Version Name: 0.92-pre3-25-geaea782
         [echo] Version Code: 2173

    ndk-build:
         [exec] Android NDK: WARNING: APP_PLATFORM android-9 is larger than android:minSdkVersion 8 in ./AndroidManifest.xml
         [exec] Compile thumb  : adhoc <= install.c
         [exec] Compile thumb  : adhoc <= adhoc.c

    ...

    -package-resources:
         [aapt] Creating full resource package...
         [aapt] Warning: AndroidManifest.xml already defines versionName (in http://schemas.android.com/apk/res/android); using existing value in manifest.

    -package:
    [apkbuilder] Current build type is different than previous build: forced apkbuilder run.
    [apkbuilder] Creating batphone-debug-unaligned.apk and signing it with a debug key...

    -post-package:

    -do-debug:
     [zipalign] Running zip align on final apk...
         [echo] Debug Package: /home/USERNAME/src/batphone/bin/batphone-debug.apk
    [propertyfile] Creating new property file: /home/USERNAME/src/batphone/bin/build.prop
    [propertyfile] Updating property file: /home/USERNAME/src/batphone/bin/build.prop
    [propertyfile] Updating property file: /home/USERNAME/src/batphone/bin/build.prop
    [propertyfile] Updating property file: /home/USERNAME/src/batphone/bin/build.prop

    -post-build:

    debug:

    BUILD SUCCESSFUL
    Total time: 2 minutes 49 seconds
    $

If successful then:

 * The built APK is in `~/src/batphone/bin/batphone-debug.apk`.
 * The built version identifier is in `batphone/res/values/version.xml`.

If the command fails, check that all the requirements and environment specified
above are met.  Over 90% of all build errors stem from this cause.  Some common
problems are:

 * **Unable to locate tools.jar. Expected to find it in ...**  The JDK (Java
   development kit) is not installed, only the JRE (Java runtime environment).
   Solution: install the JDK (see step 3).

 * **Error: Oops, it looks like you didn't provide an argument for '-t'.
   '-p' was found instead.**  The [SDK platform package][] for Android API
   level 8 is not installed.  Install it (see step 3) and try again.

If the development environment is all present and correct, and the build still
fails, then contact the [Serval Project Developers][] Google Group for
assistance.

Step 6 - Install the APK
------------------------

Requirements; for this step you will need:

 * the built APK as per Step 5;
 * an Android device connected to a USB port on your workstation.

Check that the Android device is recognised:

    $ adb devices
    * daemon not running. starting it now on port 5037 *
    * daemon started successfully *
    List of devices attached
    54A51BD473FC	device
    $

If the device does not appear in the list, you must resolve this problem before
proceeding.  This will depend on your workstation's operating system, and is
outside the scope of these instructions.

If Serval Mesh is already installed on the connected device, you have the
option of re-installing (upgrading) by giving the `-r` option.  This will
preserve the data and settings kept by the Serval Mesh app, which could provoke
incompatibility issues, especially in a development version.  The following
commands will perform the re-install, after which no further commands are
needed in this step:

    $ cd ~/src/batphone
    $ adb install -r bin/batphone-debug.apk
    1783 KB/s (1905585 bytes in 1.043s)
        pkg: /data/local/tmp/batphone-debug.apk
    Success
    $

Alternatively, you can uninstall the Serval Mesh app and continue with the procedure
below (this will erase all data and settings kept by the Serval Mesh app):

    $ adb uninstall org.servalproject
    Success
    $

To install the built APK on a device without the Serval Mesh app currently
installed:

    $ cd ~/src/batphone
    $ adb uninstall org.servalproject
    Success
    $ adb install bin/batphone-debug.apk
    1783 KB/s (1905585 bytes in 1.043s)
        pkg: /data/local/tmp/batphone-debug.apk
    Success
    $

If the (re-)installation fails:

 * Ensure that all the requirements and environment specified above are met;
 * Check that your workstation recognises the connected device as an Android
   device;
 * Check that the device is running [Android 2.2][] or later.
 * Read more about the [Android Debug Bridge][adb].
 * Contact the [Serval Project Developers][] Google Group.

-----
**Copyright 2014 Serval Project Inc.**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: ./README.md
[Serval Project Developers]: http://groups.google.com/group/serval-project-developers
[APK]: http://en.wikipedia.org/wiki/APK_(file_format)
[Android SDK]: http://developer.android.com/sdk/index.html
[Android NDK]: http://developer.android.com/sdk/ndk/index.html
[Android SDK Tools]: https://developer.android.com/tools/sdk/tools-notes.html
[SDK installation instructions]: http://developer.android.com/sdk/installing/index.html
[SDK platform package]: http://developer.android.com/sdk/installing/adding-packages.html
[adb]: http://developer.android.com/tools/help/adb.html
[Android 2.2]: http://developer.android.com/about/versions/android-2.2.html
[Android 2.3.3]: http://developer.android.com/about/versions/android-2.3.3.html
[Oracle Java SE]: http://www.oracle.com/technetwork/java/javase/downloads/index.html
[OpenJDK]: http://openjdk.java.net/
[Git]: http://git-scm.com/
[GitHub]: http://github.com/servalproject/
[batphone]: http://github.com/servalproject/batphone/
[auto upgrade]: ./doc/Auto-Upgrade.md
[release build]: ./doc/Build-for-Release.md
[master branch]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:git_master_branch
[development branch]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:git_development_branch
[version number]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:version_numbering
[Apache Ant]: http://ant.apache.org/
[pandoc]: http://johnmacfarlane.net/pandoc/
[Eclipse]: http://developer.android.com/sdk/installing/installing-adt.html
[Debian]: http://www.debian.org/
[Ubuntu]: http://www.ubuntu.com/
[Fedora]: http://fedoraproject.org/
[Cygwin]: http://www.cygwin.com/
[Bourne shell]: http://en.wikipedia.org/wiki/Bourne_shell
[Java reflection]: http://docs.oracle.com/javase/tutorial/reflect
[CC BY 4.0]: ./LICENSE-DOCUMENTATION.md
