Building and Installing Serval Mesh
===================================
[Serval Project][], November 2012.

These are instructions for manually building the [Serval Mesh][] [APK][]
from source code and installing it on an Android device.

Instructions for [Eclipse][] are not included.

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

These instructions are for the following platforms;

 * Debian Linux, ix86 and x86\_64
 * Mac OS X 10.7 “Lion”, x86\_64

Other Linux distributions, eg, [Ubuntu][] and [Fedora][], should work if
sufficiently recent.

Other platforms for which the [Android SDK][] is available, such as Microsoft
Windows, may work (eg, using [Cygwin][]), but are not tested or supported.

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
    $

If the command fails:

 * Check that the requirements above are all met;
 * Contact the [Serval Project Developers][] Google Group.

Step 2 - Choose a version
-------------------------

In [Git][], each version has its own named *branch* or *label*.  Common
branches are:

 * **master** is always the latest official release
 * **development** is the latest up-to-the-minute, unstable, “bleeding edge”
   version

Choose which version you want to build, and check it out using this command:

    $ cd ~/src/batphone
    $ git checkout master
    Branch master set up to track remote branch master from origin.
    Switched to a new branch 'master'
    $

Step 3 - install development tools
----------------------------------

Install the following packages in directories of your choice:

 * Revision 17 or later of the [Android SDK Tools][] (Software Development
   Kit), with all its requirements and dependencies satisfied.  See the [SDK
   installation instructions][].
   
 * Revision 7b or later of the [Android NDK][] (Native Development Kit) must be
   installed, with all its requirements satisfied.

 * A recent version of the [Apache Ant][] build system.  Each [Android SDK
   Tools][] revision specifies its required Apache Ant version.

 * The [SDK platform package][] for [Android 2.2][].

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

Run the [build.sh](./build.sh) script (was named `BUILD.txt` in versions prior to 0.90),
which will take many minutes and produce a lot of output as it works:

    $ cd ~/src/batphone
    $ ./build.sh
    Submodule 'jni/serval-dna' (git://github.com/servalproject/serval-dna.git) registered for path 'jni/serval-dna'
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
    Buildfile: /tmp/batphone/build.xml
    ...
    -setup:
        [echo] Creating output directories if needed...
        [echo] Gathering info for batphone...
        [setup] Android SDK Tools Revision 19
        [setup] Project Target: Android 2.2
        [setup] API level: 8
        [setup] ------------------
    ...
    version:
        [echo] Version Name: 0.90-alpha-27-gb7326d0
        [echo] Version Code: 1854
    ...
    ndk-build:
        [exec] Install        : adhoc => libs/armeabi/adhoc
        [exec] Install        : libcutils.so => libs/armeabi/libcutils.so
        ...
        ...
        [exec] Executable     : servaldsimple
        [exec] Install        : servaldsimple => libs/armeabi/servaldsimple
    ...
    -compile:
        [javac] Compiling 163 source files to /tmp/batphone/bin/classes
    ...
    -crunch:
    [crunch] Crunching PNG Files in source dir: /tmp/batphone/res
    [crunch] To destination dir: /tmp/batphone/bin/res
    ...
    debug:
    BUILD SUCCESSFUL
    Total time: 3 minutes 38 seconds
    $

If successful then:

 * The built APK is in `~/src/batphone/bin/batphone-debug.apk`.
 * The built version identifier is in `batphone/res/values/version.xml`.

If the command fails:

 * Ensure that all the requirements and environment specified above are met;
 * Contact the [Serval Project Developers][] Google Group.

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

If Serval Mesh was already installed on the connected device, you will have to
uninstall it first:

    $ adb uninstall org.servalproject
    Success
    $

To install the built APK on the connected Android device, use the following
commands:

    $ cd ~/src/batphone
    $ adb install bin/batphone-debug.apk
    1783 KB/s (1905585 bytes in 1.043s)
        pkg: /data/local/tmp/batphone-debug.apk
    Success
    $

If the installation fails:

 * Ensure that all the requirements and environment specified above are met;
 * Check that your workstation recognises the connected device as an Android
   device;
 * Check that the device is running [Android 2.2][] or later.
 * Read more about the [Android Debug Bridge][adb].
 * Contact the [Serval Project Developers][] Google Group.


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
[Git]: http://git-scm.com/
[GitHub]: http://github.com/servalproject/
[batphone]: http://github.com/servalproject/batphone/
[Apache Ant]: http://ant.apache.org/
[Eclipse]: http://developer.android.com/sdk/installing/installing-adt.html
[Ubuntu]: http://www.ubuntu.com/
[Fedora]: http://fedoraproject.org/
[Cygwin]: http://www.cygwin.com/
[Bourne shell]: http://en.wikipedia.org/wiki/Bourne_shell
