Building Serval Mesh for Release
================================
[Serval Project][], August 2013

These are instructions for manually building a signed [APK][] of the [Serval
Mesh][] app for Android from source code, for release via [Google Play][] and
[Rhizome][].

Overview
--------

The release build process is similar in most respects to the [debug build][]
process.  It is strongly recommended to get that working before attempting a
release build.

A release build of [Serval Mesh][] is distinguished from a [debug build][] in the
following important ways:

 * it has passed [release][] testing,

 * its [release notes][] are up to date,

 * it is signed using the Serval Project's Android private key, so that it can
   be published on to [Google Play][],

 * it is signed using the Serval Project's Rhizome private key, so that it can
   be distributed using [Rhizome][].

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

Key store
---------

The private keys needed to sign the release are available in two separate files
which are kept safe by senior developers and provided on request.

In general, the key files are provided on a USB Pen Drive which may be inserted
while needed and must not be not copied to your hard disk.

 * The Android key store file is managed with the [Keytool][] utility that
   comes with the Java Development Kit (JDK) you are using.  (The [Android
   documentation](http://developer.android.com/tools/publishing/app-signing.html)
   recommends **not** using the Keytool that comes with the [GNU Java
   Compiler][].)

 * The Rhizome key store is managed with the Serval DNA


Ant properties
--------------

The build process is expressed in the [build.xml][] file which is executed by
[Ant][].  The following release-specific properties must be supplied to Ant
through your own *Ant properties* file:

 * `key.store` is the absolute path of the [Jarsigner][] key file which
   contains the Serval Project's Android private key

 * `key.alias` is the name (label) of the Serval Project's Android private key
   within the key store file (a key store file may contain many private keys,
   each identified by a unique *alias*)

The [ant.properties][] file in the [batphone][] repository is **NOT** a
suitable place to add these properties, because they will be different on every
developer's platform.  Editing the Batphone repository [ant.properties][] file,
even with the intention of never committing it, runs the risk of committing and
pushing your personal key store path to GitHub, which will seriously annoy
other developers.

The proper way to identify your own Ant properties file to [Ant][] is to set
the `SERVAL_BATPHONE_ANT_PROPERTIES` environment variable to the absolute path
of your file, which may be in any location you choose (but not within the
Batphone Git repository).

For example, you could place the following in your `$HOME/.profile`:

    export SERVAL_BATPHONE_ANT_PROPERTIES=$HOME/serval/ant.properties

and place the following lines into the text file named `ant.properties` within
the `serval` directory (folder) within your home directory:

    key.store=/media/usbdrive/serval-release-key.keystore
    key.alias=release

This assumes that the key store file will be provided on a USB Pen Drive that
is inserted into the workstation when needed (see below).

Passwords
---------

The following properties **must never be used in an Ant properties file**:

 * `key.store.password` is the plain text of the password to unlock the key
   store file specified by `key.store`

 * `key.alias.password` is the plain text of the password to unlock the private
   key within the key store file having the name (label) specified by
   `key.alias`

Placing passwords into an Ant properties file would risk disclosure if the file
were ever stolen, permanently compromising the authenticity of the Serval Mesh
app.  The only safe way to supply passwords is interactively when running the
[Ant][] build command (see below).

Android private key
-------------------

The Serval Project's Android private key is kept in a key store file and
protected by a password.

Rhizome private key
-------------------


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: ../README.md
[APK]: http://en.wikipedia.org/wiki/APK_(file_format)
[Google Play]: https://play.google.com/store/apps/details?id=org.servalproject
[Rhizome]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:rhizome
[debug build]: ../INSTALL.md
[release]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:release:main_page
[release notes]: ../CURRENT-RELEASE.md
[build.xml]: ../build.xml
[ant.properties]: ../ant.properties
[Ant]: http://ant.apache.org/
[Jarsigner]: http://docs.oracle.com/javase/6/docs/technotes/tools/windows/jarsigner.html
[Keytool]: http://docs.oracle.com/javase/6/docs/technotes/tools/windows/keytool.html
[batphone]: http://github.com/servalproject/batphone
[GNU Java Compiler]: http://gcc.gnu.org/java/
