Building and Installing Serval Mesh
===================================
[Serval Project][], May 2016

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


Setting up your envorinment
---------------------------

To build this application for android, you will need:

 * Java Development Kit (JDK) for Java 1.6 or higher, for example [OpenJDK][]
   or [Oracle Java SE][] -- **JRE (Java runtime environment) alone is not
   enough, the full JDK is required**

 * [Apache Ant][] build tool, version 1.9 or higher

 * [pandoc][] markup converter, version 1.12 or higher (this tool is optional
   for debug builds -- if absent, some pages of the app's Help section are not
   created, which should not interfere with testing)

 * a recent version of [Git][] installed and in your PATH

 * revision 18 or later of [Android SDK Tools][] (Software Development Kit),
   with all its requirements and dependencies satisfied -- see the [SDK
   installation instructions][]

 * [SDK platform package][] for [Android 2.3.3][], which provides Android API
   level 10 (the built Batphone app will install and run on [Android 2.2][]
   (API level 8); the higher platform package is only needed for code that uses
   [Java reflection][] to detect API level 10 features and only use them if
   present)

 * revision 13b or later of [Android NDK][] (Native Development Kit) and all its
   requirements

Before building the APK for the first and all subsequent times, the following
environment must be set up.  A good way to do this is in the `$HOME/.profile`
file.

 * The `SDK_ROOT` environment variable must be the absolute path of the root
   directory of the installed [Android SDK][].

 * The `NDK_ROOT` environment variable must be the absolute path of the root
   directory of the installed [Android NDK][].


Obtaining the source code
-------------------------

The [Serval Mesh][] source code is available from [GitHub][].
Download and build it for the first time using the following commands:

    $ git clone https://github.com/servalproject/batphone.git
    Cloning into 'batphone'...
    ...
    $ cd batphone
    $ ./build_setup.sh
    ...


Building the APK
----------------

Due to the mixture of native compiled code and Java code required to build a
working APK. We only support building the application from the command line using ant.
Once your environment is setup as above, this should be as simple as running the following;

    $ ant debug

If successful the built APK should be available at `batphone/bin/batphone-debug.apk`.

If you have any questions, please contact the [Serval Project Developers][] Google Group for
assistance.


Installing the APK
------------------

Check that your Android device is recognised:

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
