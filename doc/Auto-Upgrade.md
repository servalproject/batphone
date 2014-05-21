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

Auto Upgrade takes advantage of a fortunate feature: Android will happily
install an App from an [APK][] file which has additional content appended to
the end; Android's signature verification apparently only checks content
covered by the [Zip file][] index.  So to every release [APK][] file built,
Auto Upgrade appends a manifest describing the APK file itself.  (An APK file
cannot include its own manifest, since the manifest must contain the hash of
the APK file, but that cannot be known until after the APK file is built: a
circular dependency.)

Unfortunately, [Google Play][] will not accept an [APK][] file which has been
modified in any way, including additional appended content.

Auto Upgrade works as follows:

 1. A Serval Project senior developer performs a [release build][]:
     * The build script signs the release [APK][] file with the Serval
       Project's Android release secret key.  This produces
       `batphone-release-play.apk` ("the vanilla APK") which is suitable for
       upload to [Google Play][], who will not accept an APK which has been
       subsequently modified.
     * The build script invokes a native [Serval DNA][] executable, supplying
       the secret release key, to update the Rhizome release bundle to contain
       the vanilla APK.  The update increases the [Rhizome bundle's version
       number](#rhizome-bundle-version-number), so that wherever the updated
       bundle propagates, it replaces any older version of itself.
     * The build script produces `batphone-release.apk` ("the extended APK") by
       appending the updated bundle's manifest to the vanilla APK along with a
       special tail marker (two length bytes and magic bytes 0x41 0x10).

 2. The senior developer uploads the vanilla APK to [Google Play][] and the
    extended APK to [Dreamhost FTP][].  From there, the two APK files are
    downloaded and installed on various devices.

 3. Whenever Batphone starts, it checks its own APK file to see whether it is
    extended or vanilla.  If it finds an extended APK file, then it retrieves
    the manifest from the end and injects the rest of the APK file (the vanilla
    part) together with the manifest into its own Rhizome store.  If the manifest
    signature the file hash both verify, then Rhizome will accept the update,
    replacing any existing Rhizome bundle whose version number is lower.  As a
    result, the APK quickly becomes available to other phones in the vicinity.

 4. Whenever Batphone receives a Rhizome bundle whose ID matches the bundle ID
    retrieved from its own extended APK file, an automatic upgrade is
    triggered.  Batphone extracts the new APK file from Rhizome and passes it
    to the Android App Manager, which prompts the user to upgrade.  If the user
    consents, then the Batphone app is re-installed from the new APK.

This design has the drawback that Batphone apps installed from [Google Play][]
will not participate in Auto Upgrade, because they will not share themselves
via Rhizome nor will they automatically upgrade themselves from Rhizome.  There
are plans to improve this state of affairs.

Protection from attack
----------------------

Were Auto Upgrade compromised, it would afford an attacker a powerful tool for
installing and running trojan code on all devices that have [Serval Mesh][]
installed.

To compromise Auto Upgrade, an attacker would either have to exploit a
vulnerability (defect) in the Auto Upgrade or Rhizome code, steal secret keys
from the Serval Project, or circumvent the cryptosystem that Rhizome uses to
prevent modification of bundles as they are disseminated.

The Auto Upgrade code in [Batphone][] is small and simple, and has been
reviewed by senior developers, making it unlikely to contain any exploitable
vulnerability or logic error.

The Serval Project employ several layers of security to guard against
disclosure or theft of secret keys, involving physical security measures and
encryption with passwords.

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

Assumed knowledge
-----------------

The commands in this document are [Bourne shell][] commands, using standard
quoting, variable expansion and backslash line continuation.  Commands issued
by the user are prefixed with the shell prompt `$` to distinguish them from the
output of the command.  Single and double quotes around arguments are part of
the shell syntax, not part of the argument itself.

These instructions assume the reader is proficient in the Unix command-line
shell and has general experience with setting up and using software development
environments.

Building an Auto Upgrade debug APK
----------------------------------

A debug build does not involve any secret Android key, only the *Bundle Secret*
of the developer's own Auto Upgrade testing bundle.  There are two options for
supplying this:

 1. configuring the Bundle Secret in the clear, or

 2. using a private Serval keyring protected by a PIN (the same mechanism used
    by [release build][]).

Normally, disclosure of the Bundle Secret is of no concern, since the only
phones that could be compromised by a rogue upgrade are the few on which the
developer has installed his or her own debug build for testing purposes.
Nevertheless, the second option is available to developers who wish to protect
their phones from trojan upgrades.

Option 1 - Build using Bundle Secret
------------------------------------

To make an Auto Upgrade debug build using a Bundle Secret configured in the
clear, first make a successful [debug build][].  Then:

 1. Create a new, empty Rhizome bundle with no author, and note the
    *manifestid* and *secret* fields:

        $ ./jni/serval-dna/servald rhizome add file --force-new '' ''
        service:file
        manifestid:FC5738E12A7D3D8CDDA131E487F793534519ACCA5488065F0AEEA39047BCAC18
        secret:46FD650CAA2FF573A1DE96852AE91BA9BF51132AD956436240FD6CFAAE56B521
        BK:21091C1E8E6574FBA2FFA1DE11244EDC7F8E80746B5459D9ACF12C616A791AD2
        version:1380180007376
        filesize:0
        name:
        $

 2. Configure the new Bundle's ID and secret in your personal *ant.properties*
    file whose absolute path is set in the SERVAL_BATPHONE_ANT_PROPERTIES
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

Option 2 - Build using Keyring and PIN
--------------------------------------

If you configure an Auto Upgrade debug build using a Keyring and secret PIN,
then only the BK need be configured in the clear (just like a [release
build][]).  First, make a successful [debug build][].  Then:

 1. Create a Serval keyring file at a known, private location (eg, on a USB
    flash drive or on an encrypted partition of your workstation), containing a
    single identity, optionally protected by a secret PIN, given as
    “lumberjack” in the following example (if you do not want a PIN, simply
    omit the “lumberjack” argument):

        $ export SERVALD_KEYRING_PATH=/path/to/safe/directory/serval-debug.keyring
        $ export SERVALD_KEYRING_READONLY=no
        $ ./jni/serval-dna/servald keyring add lumberjack
        sid:CA4B0F5D2AB0EB25B3D157FB2F6B69FC7D43AE885409E2A10A9A2E61AED30007
        did:
        name:
        $

    Take note of the *sid* of the new identity.

 2. Create a new, empty Rhizome bundle using the identity ([SID][]) as the
    author, and note the *manifestid* and *BK* fields:

        $ ./jni/serval-dna/servald rhizome add file \
            --entry-pin=lumberjack --force-new \
            CA4B0F5D2AB0EB25B3D157FB2F6B69FC7D43AE885409E2A10A9A2E61AED30007 ''
        service:file
        manifestid:FB83B9DFB6A5A27A540EAD59157D6613F7F56807CF72B52B8BD31AC656F6003C
        .secret:A517796C7996B29D9408540711DA4A36787FD4324CDA99FBA0448B6DBAC88680
        .author:CA4B0F5D2AB0EB25B3D157FB2F6B69FC7D43AE885409E2A10A9A2E61AED30007
        BK:A9679004CB839012A10ACABE639C03C6A7F34077D2A12D0B5554DA235BF1523E
        version:1380525993590
        filesize:0
        name:
        $

 3. Configure the new Bundle's author ([SID][]), ID and BK, along with the
    location of the Serval keyring file, in your personal *ant.properties* file
    whose absolute path is set in the SERVAL_BATPHONE_ANT_PROPERTIES
    environment variable:

        debug.serval.keyring.path=/path/to/safe/directory/serval-debug.keyring
        debug.serval.manifest.author=CA4B0F5D2AB0EB25B3D157FB2F6B69FC7D43AE885409E2A10A9A2E61AED30007
        debug.serval.manifest.id=FB83B9DFB6A5A27A540EAD59157D6613F7F56807CF72B52B8BD31AC656F6003C
        debug.serval.manifest.bk=A9679004CB839012A10ACABE639C03C6A7F34077D2A12D0B5554DA235BF1523E

 3. Execute [Apache Ant][]:

        $ ant debug-autoup
        Buildfile: /home/USERNAME/src/batphone/build.xml

        -args-debug:
            [echo] key.store=/media/USERNAME/SERVAL KEY/serval-release.keystore
            [echo] key.alias=release
            [echo] keyring.path=/media/USERNAME/SERVAL KEY/serval-release.keyring
            [echo] manifest.author=CA4B0F5D2AB0EB25B3D157FB2F6B69FC7D43AE885409E2A10A9A2E61AED30007
            [echo] manifest.id=FB83B9DFB6A5A27A540EAD59157D6613F7F56807CF72B52B8BD31AC656F6003C
            [echo] manifest.bk=A9679004CB839012A10ACABE639C03C6A7F34077D2A12D0B5554DA235BF1523E
            [echo] manifest.secret=
            [echo] keyring.pin=

        -keystore-properties:

        ...

        debug:

        -create-initial-manifest:

        -add-manifest-with-bk:

        -add-manifest-with-secret:
            [exec] service:file
            [exec] manifestid:FB83B9DFB6A5A27A540EAD59157D6613F7F56807CF72B52B8BD31AC656F6003C
            [exec] .secret:A7C29153A5DA4D1BBD8C3C28CE1353E35043F0C2D49C82972C663FDE3FF1F1B5
            [exec] .author:CA4B0F5D2AB0EB25B3D157FB2F6B69FC7D43AE885409E2A10A9A2E61AED30007
            [exec] BK:A9679004CB839012A10ACABE639C03C6A7F34077D2A12D0B5554DA235BF1523E
            [exec] version:1380181738117
            [exec] filesize:1884257
            [exec] filehash:7234B73E5494C436344823D8640DEF2340342DB610C39A0EDA56EB789BE4B5BE1AD5F39C0EB74A5DA85F2DC561C62BDCCEA25A7652AF6E876D7BCBDCCEC9944C
            [exec] name:Serval-Serval-0.92-pre3-34-g063692e.apk

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

-----
**Copyright 2013 Serval Project Inc.**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: ../README.md
[APK]: http://en.wikipedia.org/wiki/APK_(file_format)
[Batphone]: ../README.md
[Serval DNA]: https://github.com/servalproject/serval-dna
[Rhizome]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:rhizome
[Mesh Extender]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:meshextender:
[release]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:release:
[release build]: ./Build-for-Release.md
[debug build]: ../INSTALL.md
[SID]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:sid
[Google Play]: https://play.google.com/store/apps/details?id=org.servalproject
[Dreamhost FTP]: http://developer.servalproject.org/files/
[Linux.conf.au 2013]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:activity:linux.conf.au_2013
[security framework]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:security_framework
[Unix epoch]: http://en.wikipedia.org/wiki/Unix_time
[Apache Ant]: http://ant.apache.org/
[Zip file]: http://en.wikipedia.org/wiki/ZIP_file_format
[Bourne shell]: http://en.wikipedia.org/wiki/Bourne_shell
[CC BY 4.0]: ./LICENSE-DOCUMENTATION.md
