Building and Installing Serval Mesh
===================================
[Serval Project][], April 2017

Batphone can be built and installed using the Android Studio IDE or by invoking gradle manually from the command line.

To build this application for android, you will need:

 * [pandoc][] markup converter, version 1.12 or higher

 * a recent version of [Git][] installed and in your PATH

 * [Android SDK Tools][] (Software Development Kit),
   with all its requirements and dependencies satisfied, matching the version specified in
   `app/build.gradle`

 * [SDK platform package][] matching the version specified in `app/build.gradle`.
   The built Batphone app should install and run on any phone with [Android 2.2][]
   (API level 8) or later. 

 * [Android NDK][] (Native Development Kit) version 15 or later and all its requirements.


Obtaining the source code
-------------------------

The [Serval Mesh][] source code is available from [GitHub][].
Download and build it for the first time using the following commands:

    $ git clone https://github.com/servalproject/batphone.git
    Cloning into 'batphone'...
    ...
    $ cd batphone
    $ git submodule init
    $ git submodule update
    ...


Building from the Command Line
------------------------------

The gradle build process needs to know the install location of the Android SDK & NDK. 
If you open the project in Android Studio these locations will be writen to local.properties as follows;

    ndk.dir={PATH}/Sdk/ndk-bundle
    sdk.dir={PATH}/Sdk

Building a debug [APK][] Should be as simple as the following;

    $ ./gradlew assembleDebug


Release Builds
--------------

The build script will determine the type of release build being performed by examining the output of `git describe`. 
All releases or release candidates must be performed from a clean working folder, 
with an explicit tag named like 'N.NN' or 'N.NN.RCN' respectively.
All other builds from a clean working folder will be considered alpha builds.

All release builds of an [APK][] uploaded to [Google Play][] must be signed by the same [Android private key][].
In order to support [Automatic Upgrades][] over the local mesh network, we add an additional signature to the [APK][].
Using a different manifest id, depending on the build type, as provided in ./gradle.properties. 

The private keys and passwords used in the signing process are provided to senior developers 
on a USB Pen Drive, which must be inserted while needed.
These files must not be not copied, only used in-place as supplied.

If these secret keys are not located by the build process, an unsigned release will be produced instead.

The release build can be triggered either by generating a signed apk from within Android Studio, or from the command line as follows;

    $ ./gradlew --no-daemon assembleRelease
    ...
    $ Enter password for jar sign:

    $ Enter password for jar signing key:

    $ Enter serval keyring entry pin:
    ...

The assembled APK will be located at `app/build/outputs/apk/app-release.apk` or app-release-unsigned.apk.


Known Issues
------------

Compiling libsodium.a for armeabi requires NDK revision 14b or later and must be compiled once. 
However revision 14b fails to compile the opus codec
(see [Issue 361][https://github.com/android-ndk/ndk/issues/361#issuecomment-294642890]).
The opus codec can be compiled with either revision 13b or revision 15 when that is released.

In the mean time, ensure your ndk.dir points to revision 14b and attempt to build once. 
Then point your ndk.dir to revision 13b for all future builds.


-----
**Copyright 2017 Serval Project Inc. & Flinders University**
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].


[APK]: http://en.wikipedia.org/wiki/APK_(file_format)
[Android SDK]: http://developer.android.com/sdk/index.html
[Android NDK]: http://developer.android.com/sdk/ndk/index.html
[Android SDK Tools]: https://developer.android.com/tools/sdk/tools-notes.html
[Android 2.2]: http://developer.android.com/about/versions/android-2.2.html
[Android private key]: http://developer.android.com/tools/publishing/app-signing.html
[Automatic Upgrades]: ./doc/Auto-Upgrade.md
[Git]: http://git-scm.com/
[GitHub]: http://github.com/servalproject/
[Google Play]: https://play.google.com/store/apps/details?id=org.servalproject
[batphone]: http://github.com/servalproject/batphone/
[pandoc]: http://johnmacfarlane.net/pandoc/
[CC BY 4.0]: ./LICENSE-DOCUMENTATION.md

