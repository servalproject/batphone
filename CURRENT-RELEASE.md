Release Notes for Serval Mesh 0.90 “Shiny”
------------------------------------------
[Serval Project][], November 2012.

**FOURTH DRAFT**

These notes accompany the release of version 0.90 (codename “Shiny”) of the
[Serval Mesh][] app for [Android 2.2 “Froyo”][] and above.  This release
succeeds [version 0.08][] released in June 2012.

What is Serval Mesh?
--------------------

Serval Mesh is an app for [Android 2.2 “Froyo”][] and above.  It provides free,
secure phone-to-phone voice calling, SMS and file sharing over [WiFi][],
without the need for a SIM card or a commercial mobile telephone carrier.  In
other words, it lets your Android phone call other Android phones running
Serval Mesh within WiFi range.

The [Serval Mesh Privacy Policy][] describes how Serval Mesh handles your
personal and other sensitive information.

The [Serval Mesh 0.90 User's Manual](TBC) contains instructions for using
Serval Mesh.

Warnings
--------

Serval Mesh version 0.90 is **EXPERIMENTAL SOFTWARE**.  It has not yet reached
version 1.0, and is intended for pre-production, demonstration purposes only.
It may not work as advertised, it may lose or alter messages and files that it
carries, it may consume a lot of space, speed and battery, and it may crash
unexpectedly.

This version of Serval Mesh requires [root permission][] on your Android device
in order to put its WiFi into [AdHoc mode][].  It can function without root
permission, but will be limited to using an existing WiFi home network or hot
spot, and may not work altogether.

Serval Mesh will interfere with the normal operation of your Android device.
In particular, while activated, it will take control of your device's WiFi
network and use it to contact other Serval Mesh devices in the vicinity.  This
will cut it off from any existing WiFi network.

Serval Mesh telephony is a “best effort” service, primarily intended for when
conventional telephony is not possible or cost effective, and MUST NOT BE
RELIED UPON for emergencies in place of carrier-grade communications systems.
The Serval Project cannot be held responsible for any performance or
non-performance of the technologies that they provide in good will, and if you
use these technologies you must agree to indemnify the Serval Project from any
such claims.

The Serval Mesh software copies all files shared using the Rhizome file
dissemination service to other phones and devices running the Serval Mesh
software, regardless of size, content or intended recipient.  The Serval
Project cannot be held responsible for the legality or propriety of any files
received via Rhizome, nor for any loss, damage or offence caused by the
transmission or receipt of any content via Rhizome.

See the disclaimers below.

What's new since 0.08
---------------------

If you have used [version 0.08][], the first things you will notice are:

 * A completely redesigned human interface.

 * A much smaller APK; faster to download and install.

 * No need for third-party apps like SMSDroid or WebSMS.

There have been enormous changes under the hood since 0.08:

 * The foundations of the [Serval Security Framework][] are now in place.
   [Elliptic curve cryptography][NaCl] is used for identifying, protecting and
   authenticating subscribers and mesh network traffic.

 * All Serval-to-Serval traffic (except Rhizome transfers) is now encapsulated
   in Serval's new, secure [Mesh Datagram Protocol][MDP], implemented as an
   overlay network on standard [IP][] over [WiFi][].

 * The original Java implementation of the [Rhizome][] file sharing system has
   been superseded by a new implementation in C within the [serval-dna][]
   component, using [SQLite][] as the local storage engine.

 * Voice calls are now carried over the mesh using Serval's own [Voice over
   Mesh Protocol][VoMP], which has been designed to replace [SIP][] and
   [RTP][].  As a result, call quality and latency have improved.

 * [MeshMS][] (Serval's SMS-like service) now uses [Rhizome][] as its transport.

 * Improved stability and responsiveness.

Known Issues
------------

The following issues are planned to be fixed by version 1.0:

 * Poor support for multi-hop mesh calls -- see [serval-dna issue #37][].  You
   can successfully call someone who is within WiFi range of your phone, but
   calls that need to be carried through intermediate phones are unreliable.

 * MeshMS messages are transmitted in clear form without encryption, so are not
   private from other WiFi users -- see [serval-dna issue #35][].

 * Rhizome slowly and gradually consumes all space on your SD Card as you send
   and receive files -- see [batphone issue #8][] and [serval-dna issue #10][].
   You can work around this by deleting the Rhizome database while the Serval
   Mesh app is not running, or by re-installing the Serval Mesh app.  To delete
   the database, use the [adb shell][] command:

        rm -r /sdcard/Android/data/org.servalproject/files/rhizome

 * Mesh call quality degrades whenever Rhizome file or MeshMS transfers are in
   progress -- see [serval-dna issue #1][].

 * The Serval Mesh app needs you to have Android [root permission][] to
   function well, because it depends on WiFi [AdHoc mode][] which can only be
   started with root permision -- see [batphone issue #47][].

 * Voice call quality is unstable and relatively untested.  The inefficient
   codec used by VoMP consumes more bandwidth than necessary.  There is no echo
   cancellation, so echo may have to be controlled by lowering speaker volume
   or using earphones.  Audio latency (delay) might exceed one second in some
   situations.

 * Every time a new MeshMS message is added to a thread, the size of the
   message transmitted by Rhizome increases, because it re-transmits all the
   prior messages in the same thread.  So every message thread will consume
   more network bandwidth and SD Card space as it grows -- see [serval-dna
   issue #28][].  This can be worked around by deleting the Rhizome database as
   described above.

There are more known bugs and issues listed under the GitHub Issues page for
[batphone issues][] and [serval-dna issues][].

Copyright and licensing
-----------------------

Serval Mesh is [free software][] produced by the [Serval Project][] and many
[contributors][].  The Java/XML source code of Serval Mesh is licensed to the
public under the [GNU General Public License version 3][GPL3].  The
[serval-dna][] component of Serval Mesh is licensed to the public under the
[GNU General Public License version 2][GPL2].  All source code is freely
available from the Serval Project's [batphone][] and [serval-dna][] Git
repositories on [GitHub][].

The copyright in most of the source code in Serval Mesh is held by Serval
Project Inc., an organisation incorporated in the state of South Australia in
the Commonwealth of Australia.

The [Serval Project][] will accept contributions from individual developers who
have agreed to the [Serval Project Developer Agreement - Individual][individ],
and from organisations that have agreed to the [Serval Project Developer
Agreement - Entity][entity].

Acknowledgements
----------------

Much of this work was funded by the [New America Foundation's][NAF] [Open
Technology Institute][OTI] and the [Shuttleworth Foundation][].

The project's founders, [Dr Paul Gardner-Stephen][pgs] and [Romana
Challans][timelady], are academic staff at the [School of Computer Science,
Engineering and Mathematics][CSEM] at [Flinders University][] in South
Australia.  Their work on the Serval Project is made possible by the ongoing
support of the university.

Disclaimer
----------

SERVAL MESH refers to the software, protocols, systems and other goods,
tangible and intangible produced by The Serval Project, Serval Project, Inc.,
and Serval Project Pty Limited.

SERVAL MESH COMES WITH NO WARRANTY, EXPRESSED OR IMPLIED, AND IS NOT FIT FOR
MERCHANTABILITY FOR ANY PURPOSE. USE AT YOUR SOLE RISK.

SERVAL MESH WILL REDUCE THE BATTERY LIFE OF DEVICES ON WHICH IT RUNS.

SERVAL MESH MAY CONSUME ALL STORAGE, both LOCAL and EXTERNAL (eg, MICRO SD
CARD) ON THE DEVICES ON WHICH IT RUNS.

SERVAL MESH SHOULD NOT BE INSTALLED ON DEVICES WHICH ARE DEPENDED UPON FOR
EMERGENCY COMMUNICATION.

SERVAL MESH MAY TRANSMIT SOME DATA, INCLUDING TELEPHONE CALLS, MESSAGES AND
OTHER POTENTIALLY PRIVATE DATA IN THE CLEAR.

SERVAL MESH PROTECTIONS against IMPERSONATION or OTHER MISAPPROPRIATION of
IDENTITY ESTABLISHING FACTORS MAY BE DEFECTIVE and MAY NOT PERFORM AS EXPECTED.

SERVAL MESH SHOULD NOT BE RELIED UPON IN AN EMERGENCY is it is an INCOMPLETE
PROTOTYPE and BEST EFFORT in nature, and may FAIL TO OPERATE.

SERVAL MESH may REVEAL AND/OR BROADCAST YOUR LOCATION, IDENTITY OR OTHER
INFORMATION through its normal operation.

SERVAL MESH is an INCOMPLETE, PRE-PRODUCTION software, experimental in nature
and is not to be considered fit for merchantability for any purpose.  It has
many defects, omissions and errors that will hamper its fulfilling of its
intended purposes.


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: https://play.google.com/store/apps/details?id=org.servalproject
[Serval Mesh Privacy Policy]: ./PRIVACY.md
[Serval Security Framework]: https://github.com/servalproject/serval-docs/blob/master/serval-security-framework/ServalSecurityFramework.odt
[version 0.08]: ./doc/RELEASE-0.08.md
[NAF]: http://www.newamerica.net/
[OTI]: http://oti.newamerica.net/
[Shuttleworth Foundation]: http://www.shuttleworthfoundation.org/
[Flinders University]: http://www.flinders.edu.au/
[pgs]: http://www.flinders.edu.au/people/paul.gardner-stephen
[timelady]: http://www.flinders.edu.au/people/romana.challans
[CSEM]: http://www.flinders.edu.au/science_engineering/csem/
[Android 2.2 “Froyo”]: http://developer.android.com/about/versions/android-2.2-highlights.html
[MDP]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:technologies:mdp
[VoMP]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:technologies:vomp
[Rhizome]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:technologies:rhizome
[MeshMS]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:technologies:meshms
[NaCl]: http://nacl.cr.yp.to/
[IP]: http://en.wikipedia.org/wiki/Internet_Protocol
[WiFi]: http://en.wikipedia.org/wiki/Wi-Fi
[SQLite]: http://www.sqlite.org/
[SIP]: http://en.wikipedia.org/wiki/Session_Initiation_Protocol
[RTP]: http://en.wikipedia.org/wiki/Real-time_Transport_Protocol
[batphone]: https://github.com/servalproject/batphone
[serval-dna]: https://github.com/servalproject/serval-dna
[GitHub]: https://github.com/servalproject
[free software]: http://www.gnu.org/philosophy/free-sw.html
[root permission]: http://en.wikipedia.org/wiki/Android_rooting
[AdHoc mode]: http://compnetworking.about.com/cs/wirelessfaqs/f/adhocwireless.htm
[batphone issues]: https://github.com/servalproject/batphone/issues
[serval-dna issues]: https://github.com/servalproject/serval-dna/issues
[adb shell]: http://developer.android.com/tools/help/adb.html
[GPL3]: http://gplv3.fsf.org/
[GPL2]: http://www.gnu.org/licenses/gpl-2.0.html
[contributors]: ./CONTRIBUTORS.md
[individ]: http://developer.servalproject.org/dokuwiki/lib/exe/fetch.php?media=content:software:developeragreements:serval_project_inc-individual.pdf
[entity]: http://developer.servalproject.org/dokuwiki/lib/exe/fetch.php?media=content:software:developeragreements:serval_project_inc-entity.pdf
[batphone issue #8]: https://github.com/servalproject/batphone/issues/8
[batphone issue #47]: https://github.com/servalproject/batphone/issues/47
[serval-dna issue #1]: https://github.com/servalproject/serval-dna/issues/1
[serval-dna issue #10]: https://github.com/servalproject/serval-dna/issues/10
[serval-dna issue #28]: https://github.com/servalproject/serval-dna/issues/28
[serval-dna issue #35]: https://github.com/servalproject/serval-dna/issues/35
[serval-dna issue #37]: https://github.com/servalproject/serval-dna/issues/37
