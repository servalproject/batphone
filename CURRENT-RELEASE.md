Release Notes for Serval Mesh 0.90 “Shiny”
------------------------------------------

**SECOND DRAFT**

[Serval Project][], November, 2012.

These notes accompany the release of version 0.90 (codename “Shiny”) of the
[Serval Mesh][] app for [Android 2.2 “Froyo”][] and above.  This release
succeeds version 0.08 released in June 2012.

What is Serval Mesh?
--------------------

Serval Mesh is an app for [Android 2.2 “Froyo”][] and above.  It provides free,
secure phone-to-phone voice calling, SMS and file sharing over WiFi, without
the need for a SIM card or a commercial mobile telephone carrier.  In other
words, it lets your Android phone communicate with other Android phones running
Serval Mesh within WiFi range.

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

Serval Mesh telephony is a "best effort" service, primarily intended for when
conventional telephony is not possible or cost effective, and MUST NOT BE
RELIED UPON for emergencies in place of carrier-grade communications systems.
The Serval Project cannot be held responsible for any performance or
non-performance of the technologies that they provide in good will, and if you
use these technologies you must agree to indemnify the Serval Project from any
such claims.

Serval Mesh will interfere with the normal operation of your Android device.
In particular, while activated, it will take control of your device's WiFi
network and use it to contact other Serval Mesh devices in the vicinity.  This
will cut it off from any existing WiFi network.

What's new since 0.08
---------------------

If you have used version 0.08, the first things you will notice are:

 * A much smaller APK; faster to download and install.

 * A completely redesigned human interface.

 * No need for third-party apps like SMSDroid or WebSMS.

There have been enormous changes under the hood since 0.08:

 * The foundations of the Serval security framework are now in place.
   [Elliptic curve cryptography][NaCl] is now used for identifying, protecting
   and authenticating subscribers and mesh network traffic.

 * The original Java implementation of the [Rhizome][] file sharing system has
   been superseded by a new implementation in C within the [serval-dna][]
   component, using [SQLite][] as the local storage engine.

 * Voice calls are now carried over the mesh using Serval's own [Voice over
   Mesh Protocol][VoMP], which has been designed to replace [SIP][] and
   [RTP][].  As a result, call quality and latency have improved.

 * [MeshMS][] (Serval's SMS-like service) now uses [Rhizome][] as its transport.

 * Improved stability due to automated testing of the [serval-dna][] component
   and better logic for automatically re-starting [serval-dna][].

Known Issues
------------

All the following issues should be fixed by version 1.0.

 * No support for multi-hop mesh calls.  You can only call someone who is
   within WiFi range of your phone.

 * Rhizome slowly and gradually consumes all space on your SD Card as you send
   and receive files.  You can work around this by deleting the Rhizome
   database while the Serval Mesh app is not running, or by re-installing the
   Serval Mesh app.  To delete the database, use the [adb shell][] command: `rm
   /sdcard/serval/rhizome.db`

 * Mesh call quality degrades whenever Rhizome file or MeshMS transfers are in
   progress.

 * The Serval Mesh app needs you to have Android [root permission][] to
   function well, because it depends on WiFi [AdHoc mode][] which can only be
   started with root permision.

 * Voice call quality is unstable and relativly untested.  The codec used by
   VoMP is very space inefficient, so it consumes more bandwidth than
   necessary.  There is no echo cancellation, so echo may have to be controlled
   by lowering speaker volume or using earphones.  Audio latency (delay) might
   increase to much greater than one second in some situations.

 * Every time a new MeshMS message is added to a thread, the size of the
   message transmitted by Rhizome increases, because it re-transmits all the
   prior messages in the same thread.  So every message thread will consume
   more network bandwidth and SD Card space as it grows.  This can be worked
   around by deleting the Rhizome database as described above.

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
the Commonwealth of Australia for the purpose of developing the Serval mesh
software.

The [Serval Project][] will accept contributions from individual developers who
have agreed to the [Serval Project Developer Agreement - Individual][individ],
and from organisations that have agreed to the [Serval Project Developer
Agreement - Entity][entity].

Acknowledgements
----------------

Much of this work was funded by the [New America Foundation's][NAF] [Open
Technology Institute][OTI] and the [Shuttleworth Foundation][].

The project's founders, Dr Paul Gardner-Stephen and Romana Challans, are
academic staff at the [School of Computer Science, Engineering and
Mathematics][CSEM] at [Flinders University][] in South Australia, and their
work is made possible by the ongoing support of the university.

Disclaimer
----------

SERVAL MESH refers to the software, protocols, systems and other goods,
tangible and intangible produced by The Serval Project, Serval Project, Inc.,
and Serval Project Pty. Ltd.

SERVAL MESH COMES WITH NO WARRANTY, EXPRESSED OR IMPLIED, AND IS NOT FIT FOR
MERCHANTABILITY FOR ANY PURPOSE. USE AT YOUR SOLE RISK.

SERVAL MESH WILL REDUCE THE BATTERY LIFE OF DEVICES ON WHICH IT RUNS.

SERVAL MESH SHOULD NOT BE INSTALLED ON DEVICES WHICH ARE DEPENDED UPON FOR EMERGENCY
COMMUNICATION.

SERVAL MESH MAY TRANSMIT SOME DATA, INCLUDING TELEPHONE CALLS, MESSAGES AND
OTHER POTENTIALLY PRIVATE DATA IN THE CLEAR.

SERVAL MESH PROTECTIONS against IMPERSONATION or OTHER MISAPPROPRIATION of
IDENTITY ESTABLISHING FACTORS MAY BE DEFECTIVE and MAY NOT PERFORM AS EXPECTED.

SERVAL MESH SHOULD NOT BE RELIED UPON IN AN EMERGENCY is it is an INCOMPLETE
PROTOTYPE and BEST EFFORT in nature, and may FAIL TO OPERATE.

SERVAL MESH may REVEAL AND/OR BROADCAST YOUR LOCATION, IDENTITY OR OTHER INFORMATION
through its normal operation.

SERVAL MESH is an INCOMPLETE, PRE-PRODUCTION software, experimental in nature
and is not to be considered fit for merchantability for any purpose.  It has
many defects, omissions and errors that will hamper its fulfilling of its
intended purposes.


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: https://play.google.com/store/apps/details?id=org.servalproject
[NAF]: http://www.newamerica.net/
[OTI]: http://oti.newamerica.net/
[Shuttleworth Foundation]: http://www.shuttleworthfoundation.org/
[Flinders University]: http://www.flinders.edu.au/
[CSEM]: http://www.flinders.edu.au/science_engineering/csem/
[Android 2.2 “Froyo”]: http://developer.android.com/about/versions/android-2.2-highlights.html
[Rhizome]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:technologies:rhizome
[MeshMS]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:technologies:meshms
[VoMP]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:technologies:vomp
[NaCl]: http://nacl.cr.yp.to/
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
[individ]: http://developer.servalproject.org/dokuwiki/lib/exe/fetch.php?media=content:software:developeragreements:serval_project_inc-individual.pdf
[entity]: http://developer.servalproject.org/dokuwiki/lib/exe/fetch.php?media=content:software:developeragreements:serval_project_inc-entity.pdf
