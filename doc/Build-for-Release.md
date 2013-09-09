Building Serval Mesh for Release
================================
[Serval Project][], August 2013

These are instructions for manually building a signed [APK][] of the [Serval
Mesh][] app for Android from source code, to produce either a [release
candidate][release] (Beta version) or to produce a new stable version for
publication via [Google Play][] and [Rhizome][].

A release build of the Serval Mesh app should only be performed as part of the
[Serval Mesh release procedure][release].  If you are following these
instructions in order to make a release, but are not following the [release
procedure][release], then you should stop right now and start following it.

Overview
--------

A release build is similar in most respects to a [debug build][].  You must
have made a successful debug build before attempting a release build, in order
to test it as a prerequisite to building a release candidate; see the [release
procedure][release] for more details.

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

 * it is signed using the Serval Mesh *release candidate* Rhizome private key,
   so that it can be distributed using [Rhizome][], auto upgrade can be tested,
   but stable installations will not auto upgrade to a Beta version.

A *final release* build of [Serval Mesh][] differs from a [debug build][] in
the following important ways:

 * it has passed [release][] testing,

 * its [release notes][] are up to date,

 * it is signed using the Serval Project's [Android private key][], so that it
   can be published on [Google Play][],

 * it is signed using the Serval Mesh *stable release* Rhizome private key, so
   that it can be distributed using [Rhizome][], and all older stable
   installations will auto upgrade to this release.

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

The Android private key for signing all Serval Mesh release candidates and
final releases is kept in a Java [keystore file][] which is guarded by senior
developers.

The Rhizome private keys for signing a Serval Mesh release candidate and for
signing a final release are both kept in a Serval Mesh [keyring file][] which
is also guarded by senior developers.

Both key files are provided on a USB Pen Drive which may be inserted while
needed.  These files must not be not copied, only used in-place as supplied.
The examples below assume that the USB Pen Drive is already mounted at
`/media/usbdrive`.

Ant properties
--------------

The build process is scripted by the [build.xml][] file and executed by
[Ant][].  The following release-specific properties must be supplied to Ant
through your own *Ant properties* file:

 * `key.store` is the absolute path of the [keystore file][] which contains the
   Serval Project's Android private key,

 * `key.alias` is the name (label) of the Serval Project's Android private key
   within the key store file.  This is always `release`.

The [ant.properties][] file in the [batphone][] repository is **NOT** the
proper place to add these properties, because they will be different on every
developer's platform.  Editing the Batphone repository [ant.properties][] file,
even with the intention of never committing it, runs the risk of committing and
pushing your personal key store path to GitHub, which will seriously annoy
other developers.

The proper way to supply your own Ant properties file to [Ant][] is to set the
`SERVAL_BATPHONE_ANT_PROPERTIES` environment variable to the absolute path of
your own file, which may be in any location you choose.

For example, you could place the following in your `$HOME/.profile`:

    export SERVAL_BATPHONE_ANT_PROPERTIES=$HOME/serval/ant.properties

and create a text file named `ant.properties` within the `serval` directory
(folder) within your home directory, having the following content:

    key.store=/media/usbdrive/serval-release-key.keystore
    key.alias=release

This assumes that the key store file will be provided on a USB flash drive that
is inserted into the workstation when needed (see below) and mounted at
`/media/usbdrive`.

Protection of secrets
---------------------

Key files may not be copied from their USB flash drive, nor stored or
transmitted under the explicit authorisation and supervision of the senior
developers of the Serval Project.

The following properties **must never be set in any Ant properties file**:

 * `key.store.password` is the plain text of the password to unlock the key
   store file specified by `key.store`

 * `key.alias.password` is the plain text of the password to unlock the private
   key within the key store file having the name (label) specified by
   `key.alias`

Placing passwords into an Ant properties file would risk disclosure if the file
were ever stolen, permanently compromising the authenticity of the Serval Mesh
app.  The only safe way to supply passwords is interactively when running the
[Ant][] build command (see below).

Build Serval DNA
----------------

The [build.xml][] file invokes the native [Serval DNA][] executable
`jni/serval-dna/servald` to sign the release with a Rhizome private key.  This
executable must be [built, tested and configured][Serval DNA INSTALL] before a
Batphone release build can proceed.

Building a release candidate
----------------------------

The following process will prompt for the passwords for the Android key store
and the Serval keyring, which you must type in:

    $ ant beta
    $

Building a final release
------------------------

The following process will prompt for the passwords for the Android key store
and the Serval keyring, which you must type in:


    $ ant release
    $


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: ../README.md
[Serval DNA]: https://github.com/servalproject/serval-dna
[Serval DNA INSTALL]: https://github.com/servalproject/serval-dna/blob/development/INSTALL.md
[APK]: http://en.wikipedia.org/wiki/APK_(file_format)
[Android private key]: http://developer.android.com/tools/publishing/app-signing.html
[Google Play]: https://play.google.com/store/apps/details?id=org.servalproject
[Rhizome]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:rhizome
[debug build]: ../INSTALL.md
[release]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:release:
[release notes]: ../CURRENT-RELEASE.md
[development branch]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:git_development_branch
[build.xml]: ../build.xml
[ant.properties]: ../ant.properties
[Ant]: http://ant.apache.org/
[keystore file]: http://developer.android.com/tools/publishing/app-signing.html
[Jarsigner]: http://docs.oracle.com/javase/6/docs/technotes/tools/windows/jarsigner.html
[Keytool]: http://docs.oracle.com/javase/6/docs/technotes/tools/windows/keytool.html
[batphone]: http://github.com/servalproject/batphone
[GNU Java Compiler]: http://gcc.gnu.org/java/
