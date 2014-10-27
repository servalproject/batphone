Building Serval Mesh for Release
================================
[Serval Project][], December 2013

These are instructions for manually building a signed and [auto upgrade][]-able
[APK][] of the [Serval Mesh][] app for Android from source code, to produce
either a [release candidate][release] (Beta) or to produce a new stable version
for publication via [Google Play][] and [Rhizome][].

A release build of the Serval Mesh app should only be performed as part of the
[Serval Mesh release procedure][release].  If you are not following those
instructions, then stop right now and start following them.

Overview
--------

A release build is similar in most respects to a [debug build][].  You must
have successfully made and tested a debug build before attempting a release
build; see the [release procedure][release] for more details.

A “release build” can be either a release candidate build (also known as a Beta
version), or a final build for publication (also known as a stable version).

A *release candidate* build of [Serval Mesh][] differs from a [debug build][] in
the following important ways:

 * it passes all automated tests as mandated for the Batphone [development
   branch][],

 * its [release notes][] are suitable for Beta testers (ie, have been updated
   since the prior release, but are not necessarily complete),

 * it is signed using the Serval Project's [Android private key][], so that it
   can be published as a Beta version on [Google Play][],

 * it produces a new version of the a Serval Mesh *beta* upgrade Rhizome bundle
   which can be distributed using [Rhizome][] in order to automatically upgrade
   all devices currently running a prior release candidate; note that stable
   installations will not automatically upgrade to a Beta version.

A *final release* build of [Serval Mesh][] differs from a [debug build][] in
the following important ways:

 * it has passed [release][] testing,

 * its [release notes][] are up to date,

 * it is signed using the Serval Project's [Android private key][], so that it
   can be published on [Google Play][],

 * it produces a new version of the a Serval Mesh *release* upgrade Rhizome bundle
   which can be distributed using [Rhizome][] in order to test auto upgrade;
   note that stable installations will not automatically upgrade to a Beta
   version.

 * it produces a new version of the a Serval Mesh *release* upgrade Rhizome
   bundle which can be distributed using [Rhizome][] in order to automatically
   upgrade all devices currently running a prior stable release; note that Beta
   installations will not automatically upgrade to the stable version.

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

These instructions assume that the reader has already performed a successful
[debug build][].

Private keys
------------

The **Android private key** for signing all Serval Mesh alpha, beta (candidate) and
final releases is kept in a Java [keystore file][] which is guarded by senior
developers.

The **Rhizome secret** for signing all Serval Mesh alpha releases, all beta
releases (release candidates) and all final releases are both kept in a Serval
Mesh [keyring file][] which is also guarded by senior developers.

Both key files are provided on a USB Pen Drive which may be inserted while
needed.  These files must not be not copied, only used in-place as supplied.
The examples below assume that the USB Pen Drive is mounted at
`/media/USERNAME/SERVAL KEY`.

Ant properties
--------------

The build process is scripted by the [build.xml][] file and executed by
[Ant][].  The following release-specific properties must be supplied to Ant
through your own *Ant properties* file:

 * `android.key.store` is the absolute path of the Java [keystore file][] which
   contains the Serval Project's Android private key,

 * `serval.keyring.path` is the absolute path of the [keyring file][] which
   contains the Rhizome secret that is used to publish new versions of all
   Alpha, Beta and Release [auto upgrade][] manifests.

The [ant.properties][] file in the [batphone][] repository is **NOT** the
proper place to add these properties, because they will be different on every
developer's platform.  Editing the Batphone repository [ant.properties][] file,
even with the intention of never committing it, runs the risk of accidentally
committing and pushing your personal key store path to GitHub, which will
seriously annoy other developers.

The proper way to supply your own Ant properties file to [Ant][] is to set the
`SERVAL_BATPHONE_ANT_PROPERTIES` environment variable to the absolute path of
your own file, which may be in any location you choose.

For example, place the following line in your `$HOME/.profile`:

    export SERVAL_BATPHONE_ANT_PROPERTIES=$HOME/serval/ant.properties

Then create a text file named `ant.properties` within the `serval` directory
(folder) within your home directory, having the following content:

    android.key.store=/media/${env.LOGNAME}/SERVAL KEY/serval-release.keystore
    serval.keyring.path=/media/${env.LOGNAME}/SERVAL KEY/serval-release.keyring

This example assumes that the key store and keyring files will be provided on
one of the Serval Key USB flash drives that is inserted into the workstation
when needed (see below) and mounted under `/media/USERNAME/SERVAL KEY`.  You will
probably need to vary these paths on your own workstation.

Protection of secrets
---------------------

KEY FILES MUST NOT BE COPIED FROM THEIR USB FLASH DRIVE, NOR STORED OR
TRANSMITTED IN ANY WAY, EXCEPT WITH EXPLICIT AUTHORISATION AND UNDER THE DIRECT
SUPERVISION OF SERVAL PROJECT SENIOR DEVELOPERS.

THE FOLLOWING ANT PROPERTIES **MUST NEVER BE SET IN ANY ANT PROPERTIES FILE**:

 * `key.store.password` is the plain text of the password to unlock the key
   store file specified by `key.store`

 * `key.alias.password` is the plain text of the password to unlock the private
   key within the key store file that has the name given by `key.alias` (this
   name is configured as "release" in [ant.properties][]).

Placing passwords into an Ant properties file would risk disclosure if the file
were ever stolen, and permanently compromise the authenticity of the Serval
Mesh app.  The only legitimate way to supply passwords is interactively when
running the [Ant][] build command (see below).

Build Serval DNA
----------------

The [build.xml][] file invokes the native [Serval DNA][] executable
`jni/serval-dna/servald` to sign the release with a Rhizome private key.  This
executable must be [built, tested and configured][Serval DNA INSTALL] before a
Batphone release build can proceed.

Building a release candidate
----------------------------

If you do not know the necessary passwords, you cannot build a release
candidate, even if you possess both key files.

The following process will prompt for three passwords -- one for the Serval
keyring, one for the Android key store, and one for the Android key called
"release".  You must type each one in followed by the Enter key:

    $ ant beta
    ...
    -get-keyring-pin:
        [input] Please enter Serval release PIN:
    keyringpassword<Enter>
    ...
    -release-prompt-for-password:
        [input] Please enter keystore password (store:/media/USERNAME/SERVAL KEY/serval-release.keystore):
    keystorepassword<Enter>
        [input] Please enter password for alias 'release':
    keyaliaspassword<Enter>
    ...
    $

The built APK suitable for [Dreamhost FTP][] and direct installation on devices
is in `bin/batphone-beta.apk`.

The built APK suitable for [Google Play][] is in `bin/batphone-beta-play.apk`.

Building a final release
------------------------

If you do not know the necessary passwords, you cannot build a release, even if
you possess both key files.

Use the same procedure as [building a release candidate][#Building a release
candidate] (above) except use the `ant release` command instead of `ant beta`.
The same passwords apply:

    $ ant release
    ... as above ...
    $

The built APK suitable for [Dreamhost FTP][] and direct installation on devices
is in `bin/batphone-release.apk`.

The built APK suitable for [Google Play][] is in `bin/batphone-release-play.apk`.

-----
**Copyright 2013 Serval Project Inc.**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: ../README.md
[Serval DNA]: https://github.com/servalproject/serval-dna
[Serval DNA INSTALL]: https://github.com/servalproject/serval-dna/blob/development/INSTALL.md
[APK]: http://en.wikipedia.org/wiki/APK_(file_format)
[Android private key]: http://developer.android.com/tools/publishing/app-signing.html
[Google Play]: https://play.google.com/store/apps/details?id=org.servalproject
[Dreamhost FTP]: http://developer.servalproject.org/files/
[Rhizome]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:rhizome
[debug build]: ../INSTALL.md
[release]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:release:
[release notes]: ../CURRENT-RELEASE.md
[development branch]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:git_development_branch
[build.xml]: ../build.xml
[ant.properties]: ../ant.properties
[Ant]: http://ant.apache.org/
[keystore file]: http://developer.android.com/tools/publishing/app-signing.html
[keyring file]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:keyring
[auto upgrade]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:automatic_upgrade
[Jarsigner]: http://docs.oracle.com/javase/6/docs/technotes/tools/windows/jarsigner.html
[Keytool]: http://docs.oracle.com/javase/6/docs/technotes/tools/windows/keytool.html
[batphone]: http://github.com/servalproject/batphone
[GNU Java Compiler]: http://gcc.gnu.org/java/
[CC BY 4.0]: ./LICENSE-DOCUMENTATION.md
