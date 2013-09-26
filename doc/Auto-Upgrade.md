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
service that uses strong cryptography for content protection and authentication
and does not depend on Internet connections or servers.

Concept of operation
--------------------

The [APK][] file of every stable [release][] of [Serval Mesh][] is injected
into [Rhizome][] as an update of a single, well-known Rhizome bundle.  This
bundle's ID is built into the Batphone source code in the
[ant.properties](../ant.properties) file as the property
`release.serval.manifest.id`.

[Rhizome][] disseminates the [APK][] files as widely as possible using Wi-Fi
connections between nearby phones and [Mesh Extender][] devices where present.
Every device receiving a newer [APK][] file prompts the user to upgrade the
[Serval Mesh][] app to the new version.

Each installation of [Serval Mesh][] seeds its own Rhizome store with its own
[APK][] file so that every new release has the best possible chance of reaching
as many devices as possible via Rhizome.

Rhizome's built-in cryptographic protection and authentication ensures that
[Serval Mesh][] will only upgrade itself from an authentic, unmodified [APK][]
that was created by the [Serval Project][].

Detailed description of operation
---------------------------------

Auto Upgrade works as follows:

 1. A Serval Project senior developer performs a [release build][]:

    * The build script signs the release [APK][] file with the Serval Project's
      Android release secret key.  This produces `batphone-release-play.apk`
      ("the vanilla APK") which is suitable for upload to [Google Play][], who
      will not accept an APK which has been subsequently modified.

    * The build script invokes a native [Serval DNA][] executable, supplying
      the secret release key, to update the Rhizome release bundle to contain
      the vanilla APK.  The update increases the [Rhizome bundle's version
      number][#Rhizome bundle version number], so that wherever the updated
      bundle propagates, it replaces any older version of itself.

    * The build script produces `batphone-release.apk` ("the extended APK") by
      appending the updated bundle's manifest to the vanilla APK along with a
      special tail marker (two length bytes and magic bytes 0x41 0x10).

 2. The vanilla APK is uploaded to [Google Play][].  The extended APK is
    uploaded to [Dreamhost FTP][].

 3. Whenever Batphone starts, it checks its own APK file to see if it is
    extended or vanilla.  If it finds an extended APK file, then it retrieves
    the manifest from the end and injects the rest of the APK file (the vanilla
    part) together with the manifest into its own Rhizome store.  If the manifest
    signature the file hash both verify, then Rhizome will accept the update,
    replacing any existing Rhizome bundle whose version number is lower.  As a
    result, the APK quickly becomes available to other phones in the vicinity.

 4. Whenever Batphone receives a Rhizome bundle whose ID matches the bundle ID
    retrieved from its own extended APK file, it triggers an automatic upgrade.
    Batphone extracts the new APK file from Rhizome and passes it to the
    Android App Manager, which prompts the user to upgrade.  If the user
    consents, then the Batphone app is re-installed from the new APK.

This design has the drawback that Batphone apps installed from Google Play will
not participate in Auto Upgrade, either by sharing themselves via Rhizome or by
automatically upgrading themselves from Rhizome.  There are plans to improve
this state of affairs.

Protection from attack
----------------------

Were Auto Upgrade compromised, it would afford an attacker a powerful tool for
running malicious code on all devices that have [Serval Mesh][] installed.

Attackers would either have to exploit a vulnerability (defect) in the Auto
Upgrade or Rhizome code, steal secret keys from the Serval Project, or
circumvent the cryptosystem that Rhizome uses to prevent modification of
bundles as they are disseminated.

The Auto Upgrade code in [Batphone][] is small and simple, and has been
reviewed by senior developers, making it unlikely to contain any exploitable
vulnerability or logic error.

The Serval Project employ two layers of security to guard against disclosure of
secret keys, involving physical security measures and encryption with
passwords.

That leaves Rhizome as the largest target for any attacker wishing to subvert
Auto Upgrade.  Rhizome has been [carefully designed][security framework] to
foil all attempts to compromise the integrity and authenticity of the files it
distributes.

A Rhizome node will only accept a bundle that passes two verification checks:

 1. the payload's (file's) hash must match the hash recorded in the manifest,

 2. the manifest's cryptographic signature (over its entire content) must
    verify using its own bundle ID as the public counterpart of the signing
    key.

The only ways an attacker could produce a manifest that passed these two
verification steps would be either (a) to break the hash algorithm or the
cryptosign algorithm (which is reasonably believed to be beyond the ability of
most, possibly all agencies), or (b) to change the bundle ID, in which case the
automatic upgrade would not be triggered.

When building a release, if the secret Serval release key is not known, then a
signed Rhizome manifest cannot be produced.  If a bundle is produced without a
valid signature, then all Rhizome nodes will reject the bundle.

On any device, if an attacker modifies the APK file before it is injected into
Rhizome, or modifies the Rhizome store database after injection, then Rhizome
will reject the bundle as the payload's computed hash will no longer match the
hash recorded in the manifest.  If the attacker modifies the manifest, then the
manifest's signature will no longer be valid.

Whenever Rhizome receives a bundle, either via the network or local injection,
if the received bundle does not pass the two verification steps, then Rhizome
will reject the bundle.

Rhizome bundle version number
-----------------------------

The first Auto Upgrade release [APK][] bundle was created on or shortly after
14 January 2013.  Auto Upgrade was first trialled at [Linux.conf.au 2013][] in
Canberra from 28 January to 1 February 2013.

All the original release [APK][] bundles used the number of milliseconds since
the [Unix epoch][] as their Rhizome manifest version, at the time the bundle
was created.  This ensured that the version number increased (as long as the
system clocks on the development workstations used to build the release were
not wildly incorrect) without needing copy of the previous bundle version.

Another Rhizome bundle version numbering scheme could potentially be used (eg,
using the Android version code, which is a monotonically increasing positive
integer), but any such change would have to ensure that version numbers
continued to increase from the current value.

Testing Auto Upgrade
--------------------

Conceivably, Auto Upgrade could be tested by releasing a [debug build][]
[APK][] file through Rhizome using the well-known bundle ID, taking great care
that the debug build APK did not find its way into wider distribution.  In
practice, however, [Rhizome][] has been found to disseminate bundles with such
obstinate tenacity that simply neglecting to wipe an SD Card, or turning on a
phone at the wrong moment, or even a developer walking through the testing area
with [Serval Mesh][] installed and running, could leak the unwanted [APK][]
file to the world, which would wreak havoc with the user community and the
[Serval Project][]'s reputation.

To solve this problem, different Auto Upgrade bundles have been created to
carry four different grades of [APK][] files: *debug*, *alpha*, *beta* and
*release*.  Other grades may be added in future, but it is expected that these
four should suffice for quite some time.

The bundle IDs for *alpha*, *beta* and *release* are fixed and built into the
the [release build][] system.  Changing any of them would break its respective
chain of automatic upgrades, and all devices would have to be manually upgraded
to the new version of [Serval Mesh][] in order to join the new chain of
upgrades with the new bundle ID.

However, each individual developer can create a new bundle at any time to
produce his or her own *debug* builds.  The Auto Upgrade chain for that bundle
will be restricted to only debug [APK][] files built by that same developer, so
it can safely be used to test the Auto Upgrade functions without any impact on
other developers or the community of alpha and beta testers or stable [Serval
Mesh][] users.

Building an Auto Upgrade debug APK
----------------------------------

A debug build does not involve any secret keys, only the *Bundle Secret* of the
developer's own Auto Upgrade testing bundle.  This is configured in the clear
on the developer's own workstation, as disclosure of this secret is of no
concern.

To make an Auto Upgrade debug build, first make a successful [debug build][].
Then:

 1. Create a new, empty Rhizome bundle and note the *manifestid* and *secret* fields:

        $ ./jni/serval-dna/servald rhizome add file '' ''
        service:file
        manifestid:FC5738E12A7D3D8CDDA131E487F793534519ACCA5488065F0AEEA39047BCAC18
        secret:46FD650CAA2FF573A1DE96852AE91BA9BF51132AD956436240FD6CFAAE56B521
        version:1380180007376
        filesize:0
        name:
        $

 2. Configure the new Bundle's ID and secret in your personal *ant.properties*
    file whose absolute path is set in the `SERVAL_BATPHONE_ANT_PROPERTIES`
    environment variable:

        debug.serval.manifest.id=FC5738E12A7D3D8CDDA131E487F793534519ACCA5488065F0AEEA39047BCAC18
        debug.serval.manifest.secret=46FD650CAA2FF573A1DE96852AE91BA9BF51132AD956436240FD6CFAAE56B521

 3. Execute [Apache Ant][]:

        $ ant debug-autoup
        Buildfile: /home/USERNAME/src/batphone/build.xml

        -args-debug:
            [echo] key.store=/media/USERNAME/SERVAL KEY/serval-release.keystore
            [echo] key.alias=release
            [echo] keyring.path=/media/USERNAME/SERVAL KEY/serval-release.keyring
            [echo] manifest.author=8C3C8F77E334EE909F06307DC75400CF1C7DFF5905A8B1D25AACE6B35900A814
            [echo] manifest.id=FC5738E12A7D3D8CDDA131E487F793534519ACCA5488065F0AEEA39047BCAC18
            [echo] manifest.bk=
            [echo] manifest.secret=46FD650CAA2FF573A1DE96852AE91BA9BF51132AD956436240FD6CFAAE56B521
            [echo] keyring.pin=

        -keystore-properties:

        ...

        debug:

        -create-initial-manifest:

        -add-manifest-with-bk:

        -add-manifest-with-secret:
            [exec] service:file
            [exec] manifestid:FC5738E12A7D3D8CDDA131E487F793534519ACCA5488065F0AEEA39047BCAC18
            [exec] secret:46FD650CAA2FF573A1DE96852AE91BA9BF51132AD956436240FD6CFAAE56B521
            [exec] version:1380181738117
            [exec] filesize:1884257
            [exec] filehash:A4DA13DEA00481DC964BAC00AF009AB565FBAB3AAECB26416E2976480F863DB81F9C41E795234566B01EB200F9D8706EE7A324D24AC5AA20A5877167E57330D8
            [exec] name:Serval-0.92-pre3-29-gff2c712.apk

        -add-manifest:

        -append-manifest:

        -create-manifest:

        -remove-instance:
        [delete] Deleting directory /home/USERNAME/src/batphone/bin/instance

        debug-autoup:

        BUILD SUCCESSFUL
        Total time: 19 seconds
        $

    The built debug-mode APK file is in `bin/batphone-debug.apk`.


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: ../README.md
[APK]: http://en.wikipedia.org/wiki/APK_(file_format)
[Batphone]: ../README.md
[Rhizome]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:rhizome
[release]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:release:
[release build]: ./Build-for-Release.md
[debug build]: ../INSTALL.md
[Linux.conf.au 2013]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:activity:linux.conf.au_2013
[security framework]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:security_framework
[Unix epoch]: http://en.wikipedia.org/wiki/Unix_time
[Apache Ant]: http://ant.apache.org/
