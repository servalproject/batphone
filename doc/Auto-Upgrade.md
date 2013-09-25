Serval Mesh Auto Upgrade
========================
[Serval Project][], September 2013

This is description of the *Auto Upgrade* functionality of the [Serval Mesh][]
app for Android.

What is Serval Auto Upgrade?
----------------------------

Auto Upgrade is a feature of the [Serval Mesh][] app for Android which allows
it to be upgraded to newer versions automatically, by distributing [APK][]
files via [Rhizome][].  Rhizome is a store-and-forward file distribution
service that uses strong cryptography to authenticate the contents of files.

Auto Upgrade uses Rhizome's built-in cryptographic authentication to ensure
that the [Batphone][] app will only upgrade itself from an authentic [APK][]
that was created by the [Serval Project][].  Only the Serval Project possesses
the cryptographic key that is needed to create an authentic Rhizome bundle.

Concept of operation
--------------------

Auto Upgrade relies on [Rhizome][] to disseminate and authenticate release
[APK][] files.

A single, fixed Rhizome bundle ID is used to publish the [APK][] file of every
single stable [release][] of [Serval Mesh][].  This Bundle ID is built into the
Batphone source code in the [ant.properties](../ant.properties) file as the
property `release.serval.manifest.id`.

Whenever a [release build][] of Serval Mesh is performed, the build procedure
updates the release Rhizome bundle to contain the new [APK][] file.  The update
increases the [Rhizome bundle's version number][#Rhizome bundle version
number], which ensures that wherever the bundle propagates, it replaces any
older version of itself.

Detailed description of operation
---------------------------------

Auto Upgrade works as follows:

 1. A Serval Project senior developer performs a [release build][]:

    * The build script signs the release [APK][] file with the Serval Project's
      Android release secret key.  This produces `batphone-release-play.apk`
      ("the vanilla APK") which is suitable for upload to [Google Play][], who
      will not accept an APK which has been subsequently modified.

    * The build script then invokes a native [Serval DNA][] executable to
      create a new Rhizome release bundle containing the vanilla APK using the
      secret release key.  It then produces `batphone-release.apk` ("the
      extended APK") by appending the new bundle's manifest to the vanilla APK
      along with a special tail marker (two length bytes and magic bytes 0x41,
      x10).  This is uploaded to [Dreamhosti FTP][] for distribution outside Google
      Play.

 2. The vanilla APK is distributed via [Google Play][] and Rhizome.  The
    extended APK is distributed via [Dreamhost FTP][].

 3. Whenever a Batphone app is installed and started, it checks its own APK
    file to see if it is extended or vanilla.  If it finds an extended APK
    file, then it retrieves the manifest from the end and injects the remainder
    of the APK file (the vanilla part) together with the manifest into its own
    Rhizome store.  This will replace any existing bundle whose version number
    is lower.  As a result, the APK quickly becomes available to other phones
    in the vicinity as Rhizome automatically disseminates the file.

 4. Whenever a running Batphone app receives a Rhizome bundle whose ID matches
    the ID of its own extended APK file, this triggers an automatic upgrade.
    Batphone extracts the new APK file from Rhizome and passes it to the
    Android App Manager, which prompts the user to upgrade.  If the user
    consents, then the Batphone app is installed from the new APK.
 
This design has the drawback that Batphone apps installed from Google Play will
not participate in Auto Upgrade, either by sharing themselves via Rhizome or by
automatically upgrading themselves from Rhizome.  There are plans to improve
this state of affairs.

Protection from forgery
-----------------------

Rhizome's built-in cryptographic authentication prevents unauthorised parties
from forging release APK bundles or modifying bundles as they are disseminated.

A Rhizome node will only accept a bundle that passes two verification checks:
 1. the payload's (file's) hash must match the hash recorded in the manifest,
 2. the manifest's cryptographic signature over its entire content must verify
    with its own bundle ID (which is the public part of the signing key).

In step 1 above, if the secret Serval release key is not known, then a signed
Rhizome manifest cannot be produced.  All Rhizome nodes will reject the bundle.

In step 3 above, if an attacker modifies the APK file before it is injected
into Rhizome, or modifies the Rhizome store database after injection, then
Rhizome will reject the bundle as its hash will no longer match the hash
recorded in the manifest.  If the attacker also modifies the manifest, then the
manifest signature will no longer be valid.  In either case, all Rhizome nodes
will reject the bundle.

In step 4 above, if the received bundle does not pass the two verification
steps, then Rhizome will reject the bundle and no automatic upgrade will be
triggered.  If an attacker produces a manifest which does pass the two
verification steps described above, then it can only be by changing the bundle
ID, in which case the automatic upgrade will not be triggered.

Rhizome bundle version number
-----------------------------

The first Auto Upgrade release [APK][] bundle was created on or shortly after
14 January 2013.  Auto Upgrade was first trialled at [Linux.conf.au 2013][] in
Canberra from 28 January to 1 February 2013.

All the original release [APK][] bundles had their Rhizome manifest version
number as milliseconds since the Unix epoch when the bundle was created, which
ensured that the version number increased (as long as the system clocks on the
development workstations used to build the release were not wildly incorrect).
Another Rhizome bundle version numbering scheme could potentially be used (eg,
using the Android version code, which is a monotonically increasing positive
integer), but any such change would have to ensure that version numbers
continued to increase from the current value.

Testing Auto Upgrade
--------------------

TBC - ant debug-autoup, ant alpha, ant beta


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: ../README.md
[APK]: http://en.wikipedia.org/wiki/APK_(file_format)
[Batphone]: ../README.md
[Rhizome]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:rhizome
[release]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:release:
[release build]: ./Build-for-Release.md
[Linux.conf.au 2013]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:activity:linux.conf.au_2013
