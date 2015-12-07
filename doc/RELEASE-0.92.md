Release Notes for Serval Mesh 0.92
==================================
[Serval Project][], October 2014

These notes accompany the release in October 2014 of version 0.92 of the [Serval
Mesh][] app for [Android 2.2 “Froyo”][] and above.

What is Serval Mesh?
--------------------

Serval Mesh is an app for [Android 2.2 “Froyo”][] and above.  It provides free,
secure phone-to-phone voice calling, SMS and file sharing over [Wi-Fi][],
without the need for a SIM card or a commercial mobile telephone carrier.  In
other words, it lets your Android phone call other Android phones running
Serval Mesh within Wi-Fi range.

The [Serval Mesh Privacy Policy][] describes how Serval Mesh handles your
personal and other sensitive information.

Warnings
--------

Serval Mesh is **EXPERIMENTAL SOFTWARE**.  It has not yet reached version 1.0,
and is intended for pre-production, demonstration purposes only.  It may not
work as advertised, it may lose or alter messages and files that it carries, it
may consume a lot of space, speed and battery, and it may crash unexpectedly.

On the Serval Mesh "Connect" screen, connecting to "Ad Hoc Mesh" will request
[root permission][] (super-user) on your Android device in order to put Wi-Fi
into [Ad-Hoc mode][].  If you grant super-user permission to Serval Mesh, it
will attempt to reinstall the Wi-Fi driver software on your device, which
**could result in YOUR DEVICE BECOMING PERMANENTLY DISABLED ("BRICKED").**

On the Serval Mesh "Connect" screen, selecting "Portable Wi-Fi Hotspot" will
put your device's Wi-Fi into [Access Point mode][].  If you have a mobile data
plan, **this will give nearby devices access to your mobile data plan, and
COULD COST YOU MONEY.**

The Serval Mesh "Connect" screen allows you to connect to other Serval Mesh
devices that act as Access Points (Hotspots) or Ad Hoc peers.  If you do so,
**this will cut off normal Wi-Fi network access** while Serval Mesh is running,
and services like Google Updates, E-mail, social media and other notifications
may not work.

Serval Mesh telephony is a “best effort” service, primarily intended for when
conventional telephony is not possible or cost effective, and **MUST NOT BE
RELIED UPON** for emergencies in place of carrier-grade communications systems.
The Serval Project cannot be held responsible for any performance or
non-performance of the technologies that they provide in good will, and if you
use these technologies you must agree to indemnify the Serval Project from any
such claims.

The Serval Mesh software copies all files shared using the [Rhizome][] file
distribution service to other phones and devices running the Serval Mesh
software, regardless of size, content or intended recipient.  The Serval
Project cannot be held responsible for the legality or propriety of any files
received via Rhizome, nor for any loss, damage or offence caused by the
transmission or receipt of any content via Rhizome.

See the disclaimers below.

What's new since 0.91
---------------------

 * The "Connect" screen has been simplified and made more usable.  It no longer
   lists all available Wi-Fi networks, but can open the Android Wi-Fi settings
   screen to allow you to choose a network.  It also supports the [Commotion
   MeshTether][] app, if installed.

 * The Serval mesh routing protocol now sends unicast packets whenever
   necessary, instead of only on single-hop links.  Most devices cease
   responding to broadcast packets when their screen is off (to save power), so
   this improvement means that [MeshMS][] messages are now delivered more
   rapidly, all devices now show their phone number and name on peer list
   screens at all times, and multi-hop voice calls are now possible, even when
   intermediate nodes have their screen off.

 * The [MeshMS][] protocol and implementation have been completely overhauled.
   [MeshMS][] messages are encrypted end-to-end, and only decrypted when
   displayed. No copies of the clear text of messages will be stored. When new 
   messages are sent, only the new data will be copied across the network.
   When the other party receives a message, it is acknowledged and a "delivered"
   label will be displayed indicating that all messages before this marker have
   arrived. 
   Content is encrypted using [Rhizome][]'s default payload encryption: the 
   [Salsa20][] stream cypher with key agreement using the [Curve25519][]
   elliptic curve Diffie-Hellman scheme by Daniel J. Bernstein, implemented by
   the [NaCl][] library.

 * **[MeshMS][] in release 0.92 is incompatible with earlier releases.  A
   MeshMS message created using release 0.92 cannot be read on 0.91 or earlier,
   and vice versa.**  The internal format we use for storing messages, and the
   way we encrypt them has changed. Furthermore, due to an upgrade of the 
   [Rhizome][] sync protocol (see below), messages created by one release will
   not be carried to a device with the same release unless all intermediate
   nodes (hops) have the same release.  This means that in order to preserve
   [MeshMS][] coverage, all nodes in the network must be upgraded to 0.92.
   (The auto-upgrade feature makes this very easy.)

 * The peer list screen is much more responsive.  It now resolves phone numbers
   (DID) and names of peers in parallel, not sequentially, and is no longer
   starved by other operations that make heavy use of the internal
   [serval-dna][] interface (for example the high volumes of incoming
   [Rhizome][] bundles that tends to occur when starting a freshly installed
   app for the first time in an active mesh network).  Peers dim to grey
   within seconds of becoming unreachable; leaving and re-entering the peer
   list screen will remove unreachable peers altogether.  If peers fail to
   appear, it can now only be because they remain unreachable due to network
   conditions (eg, poor signal, congestion or Wi-Fi incompatibility).

 * The [Rhizome][] synchronisation protocol has been upgraded and is backward
   compatible but not forward compatible, ie, release 0.92 can receive content
   from 0.91 and earlier, but not vice versa.  The new protocol uses less CPU
   and network to detect updates after synchronisation is complete, so
   conserves power and bandwidth.

 * The [Rhizome][] storage layer will attempt to preserve a minimum of 100MB
   of free space. While internally there are two settings to control this behaviour,
   no user interface has been built to set them. Old content will be discarded to 
   make room for new content, with a bias towards discarding large files first.
   This check will be performed whenever new content arrives, and every 30
   minutes while the application is running.

 * The impact of [Rhizome][] operations and transfers on voice call latency
   has been reduced but not eliminated (see Known Issues below).

 * Multi-hop voice calls have been tested and are more reliable than they were
   in 0.90 “Shiny”, but there are still issues (see below).

 * If there is no built in dialler, we now provide our own simple activity to
   search for a phone number and initiate a phone call.

 * The [Rhizome][] File Detail dialog now displays the "Open" and "Save"
   buttons together instead of just "Save" initially, which changed to "Open"
   after the file was saved.

 * There is a new setting to change the MeshMS notification tone -- see
   [batphone issue #86][].

 * The "Help" screen has been updated to include these Release Notes and
   Credits.

 * We no longer display scary warnings on install. Instead we only display
   warnings if you attempt to test Adhoc Wi-Fi.

 * Code quality has improved, closing various memory leaks and potential SQL
   injection vulnerabilities.

 * Fixed [batphone issue #53][] -- the "Unshare" button on the Rhizome "Find"
   list did not remove the name from the list.

 * Fixed [batphone issue #68][] -- application crash when the remote party
   hung up a voice call.

 * Fixed [batphone issue #71][] -- application crash when opening the
   peer list, observed on Samsung Galaxy S running CyanogenMod 10 nightly
   build.

 * Fixed -- application crash if there are nearby Access Points with hidden SSIDs.

 * Fixed -- application crash if the phone has an incompatible library for the
   [Opus][] audio codec.

What was new in 0.91 since 0.90.1
---------------------------------

 * The application will operate without requesting root permission unless "Mesh"
   network support is explicitly requested by the user. No attempt will be made
   to modify the phone's network settings, test for Adhoc support or request
   permission to run as root on the initial install of the application.

 * New network connection screen replaces the old On/Off switch. This screen has
   been designed to guide the user in how to establish a network connection with
   other nearby phones.

 * Significant changes have been made to the method the application uses to
   deliver packets reliably across the network. A new routing algorythm has
   been implemented. The previous distance vector approach to routing could be
   confused by highly dense networks and reacted very slowly to changing
   conditions.  Serval's new link state router will react to changing network
   conditions more accurately and rapidly. When network packets are lost, they
   will be retransmitted on a per-hop basis, greatly increasing the reliability
   of the network even over multiple very lossy network links.

 * Add support for the [Opus][] audio codec. This codec can greatly reduce the
   bandwidth required for a voice call without noticibly reducing audio quality
   or adding to delay.

Supported Devices
-----------------

This release of Serval Mesh has been extensively used and tested on the
following devices with no problems:

 * **Huawei IDEOS X1 u8180**, running Android 2.2.2 (rooted) and CyanogenMod 2.3.7

 * **HTC Sensation**, running Android 2.3.4 (rooted) and HTC Sense 3.0

 * **HTC One S**

 * **Motorola Milestone**

Prior releases of Serval Mesh are known to work on the following devices, which
is a strong indication that this release may also work:

 * **Huawei IDEOS u8150**

 * **Samsung Galaxy Tab 7 inch**

 * **Samsung Galaxy Gio S5660**, running Android 2.3.6 (rooted)

 * **Samsung Vitality SCH-R720**

 * **ZTE Score X500**

 * **HTC/Google G1** (“Dream”)

This release of Serval Mesh is known to work on the following devices with
minor problems:

 * **Samsung Galaxy S2 GT-I9100**, running Android 2.3 (rooted): Ad-Hoc Wi-Fi is
   not completely compatible with the Ad-Hoc Wi-Fi on other devices,
   specifically the Huawei IDEOS phones listed above.  If the Galaxy S2 is the
   first device to join the mesh, then IDEOS phones cannot join.  However, if
   an IDEOS phone is the first device, then the Galaxy S2 *does* join okay.

 * **Google Nexus 1**: does not interoperate well with HTC/Google G1.

The following devices have major known problems in this or prior releases:

 * HTC Wildfire A3335

 * Samsung Galaxy Nexus: Wi-Fi Ad-Hoc mode does not start; Wi-Fi mode reverts
   to Off.

 * Motorola Razr i XT890: Wi-Fi control does not work.

 * Samsung Galaxy Note 2: does not detect peers.  Possibly the same problem
   as the Galaxy S2 described above, but not tested.

See the [Mobile Device Compatability Table][] for more details and devices.

Known Issues
------------

 * While Serval Mesh services are enabled and you are connected to a Wi-Fi
   network, Android will be prevented from sleeping. This will drain the
   battery quickly -- see [batphone issue #91][].

 * Voice call quality degrades whenever [Rhizome][] or [MeshMS][] operations or
   transfers are in progress. [Rhizome][] can worsen network congestion, 
   transfers are not throttled and can lead to additional network latency and 
   packet loss due to a problem known as [Bufferbloat][]. -- see [serval-dna issue #1][].

 * Voice call quality is variable.  There is no echo cancellation, so echo may
   have to be controlled by lowering speaker volume or using earphones.  Audio
   latency (delay) can exceed one second in some situations -- see [batphone
   issue #93][].

 * Voice call audio has been observed to be missing on a Nexus 4 running 4.2.1,
   and upgrading to a 4.2.2 custom ROM restored audio -- see [batphone issue #77][]
   and [batphone issue #96][].

 * VoMP does not play a "ringing" sound while placing a call, nor a "hangup"
   sound when the other party hangs up -- see [batphone issue #76][].

 * Every new [MeshMS][] message increases the size of the [Rhizome][] payload
   that contains all the messages in that conversation ply.  So every
   [MeshMS][] conversation will consume more network bandwidth and SD Card
   space as it grows -- see [serval-dna issue #28][].  This cannot be worked
   around.

 * If a user starts a Serval Hotspot on the "Connect" screen, then the
   application overwrites the user's own personal hotspot name (and settings)
   with "ap.servalproject.org".  When the Serval Hotspot is turned off, Serval
   Mesh restores the user's own personal hotspot settings, which involves
   turning the user's Wi-Fi hotspot on and off briefly.  This could cause some
   concern or confusion, but is the only way that Android provides to restore
   hotspot settings.

There are more known bugs and issues listed under the GitHub Issues page for
[batphone issues][] and [serval-dna issues][].

Copyright and licensing
-----------------------

Serval Mesh is [free software][] produced by the [Serval Project][] and many
[contributors][].  The copyright in all source code is owned by Serval Project
Inc., an organisation incorporated in the state of South Australia in the
Commonwealth of Australia.

The Java/XML source code of Serval Mesh is licensed to the public under the
[GNU General Public License version 3][GPL3].  The [serval-dna][] component of
Serval Mesh is licensed to the public under the [GNU General Public License
version 2][GPL2].

All [technical documentation][] is licensed to the public under the [Creative
Commons Attribution 4.0 International license][CC BY 4.0].

All source code and technical documentation is freely available from the Serval
Project's [batphone][] and [serval-dna][] Git repositories on [GitHub][].

Acknowledgements
----------------

This release was made possible by the generous donors to the [Speak Freely
crowdfunding campaign][], in particular our "True Believers":

 * Douglas P. Chamberlin
 * Walter Ebert
 * Andrew G. Morgan, California, USA
 * Fred Fisher

This release was funded by a [grant][] from [OpenITP][].

Earlier development of Serval Mesh has been funded by the [New America
Foundation's][NAF] [Open Technology Institute][OTI], the [Shuttleworth
Foundation][], and [Nlnet Foundation][].

The Serval Project was founded by [Dr Paul Gardner-Stephen][pgs] and [Romana
Challans][timelady], both academic staff at the [School of Computer Science,
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

SERVAL MESH may COST YOU MONEY if you have a MOBILE DATA PLAN by TURNING OFF
WI-FI NETWORK ACCESS or by allowing NEARBY DEVICES TO USE YOUR DATA PLAN
WITHOUT YOUR KNOWLEDGE OR CONSENT.

SERVAL MESH may REVEAL AND/OR BROADCAST YOUR LOCATION, IDENTITY OR OTHER
INFORMATION through its normal operation.

SERVAL MESH is an INCOMPLETE, PRE-PRODUCTION software, experimental in nature
and is not to be considered fit for merchantability for any purpose.  It has
many defects, omissions and errors that will hamper its fulfilling of its
intended purposes.

-----
**Copyright 2014 Serval Project Inc.**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0
International licence][CC BY 4.0].


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: https://play.google.com/store/apps/details?id=org.servalproject
[Serval Mesh Privacy Policy]: ../PRIVACY.md
[Serval Security Framework]: https://github.com/servalproject/serval-docs/blob/master/serval-security-framework/ServalSecurityFramework.odt
[version 0.08]: ../doc/RELEASE-0.08.md
[version 0.90]: ../doc/RELEASE-0.90.md
[version 0.91]: ../doc/RELEASE-0.91.md
[OpenITP]: http://www.openitp.org/
[NAF]: http://www.newamerica.net/
[OTI]: http://oti.newamerica.net/
[Shuttleworth Foundation]: http://www.shuttleworthfoundation.org/
[Flinders University]: http://www.flinders.edu.au/
[Speak Freely crowdfunding campaign]: http://www.indiegogo.com/projects/speak-freely
[Nlnet Foundation]: http://www.nlnet.nl/
[Commotion MeshTether]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:commotion_meshtether
[pgs]: http://www.flinders.edu.au/people/paul.gardner-stephen
[timelady]: http://www.flinders.edu.au/people/romana.challans
[CSEM]: http://www.flinders.edu.au/science_engineering/csem/
[Android 2.2 “Froyo”]: http://developer.android.com/about/versions/android-2.2-highlights.html
[MDP]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:mdp
[VoMP]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:vomp
[Rhizome]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:rhizome
[MeshMS]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:tech:meshms
[NaCl]: http://nacl.cr.yp.to/
[Salsa20]: http://cr.yp.to/snuffle.html
[Curve25519]: http://cr.yp.to/ecdh.html
[elliptic curve Diffie-Hellman]: http://en.wikipedia.org/wiki/Elliptic_curve_Diffie–Hellman
[IP]: http://en.wikipedia.org/wiki/Internet_Protocol
[Wi-Fi]: http://en.wikipedia.org/wiki/Wi-Fi
[Bufferbloat]: http://en.wikipedia.org/wiki/Bufferbloat
[SQLite]: http://www.sqlite.org/
[SIP]: http://en.wikipedia.org/wiki/Session_Initiation_Protocol
[RTP]: http://en.wikipedia.org/wiki/Real-time_Transport_Protocol
[Opus]: http://www.opus-codec.org/
[batphone]: https://github.com/servalproject/batphone
[serval-dna]: https://github.com/servalproject/serval-dna
[GitHub]: https://github.com/servalproject
[free software]: http://www.gnu.org/philosophy/free-sw.html
[root permission]: http://en.wikipedia.org/wiki/Android_rooting
[Ad-Hoc mode]: http://compnetworking.about.com/cs/wirelessfaqs/f/adhocwireless.htm
[Access Point mode]: http://compnetworking.about.com/cs/wireless/g/bldef_ap.htm
[Mobile Device Compatability Table]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:hardware:devices
[batphone issues]: https://github.com/servalproject/batphone/issues
[serval-dna issues]: https://github.com/servalproject/serval-dna/issues
[adb shell]: http://developer.android.com/tools/help/adb.html
[GPL3]: ../LICENSE-SOFTWARE.md
[GPL2]: http://www.gnu.org/licenses/gpl-2.0.html
[CC BY 4.0]: ../LICENSE-DOCUMENTATION.md
[contributors]: ../CREDITS.md
[technical documentation]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:dev:techdoc
[grant]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:activity:openitp2
[batphone issue #8]: https://github.com/servalproject/batphone/issues/8
[batphone issue #53]: https://github.com/servalproject/batphone/issues/53
[batphone issue #68]: https://github.com/servalproject/batphone/issues/68
[batphone issue #70]: https://github.com/servalproject/batphone/issues/70
[batphone issue #71]: https://github.com/servalproject/batphone/issues/71
[batphone issue #76]: https://github.com/servalproject/batphone/issues/76
[batphone issue #77]: https://github.com/servalproject/batphone/issues/77
[batphone issue #86]: https://github.com/servalproject/batphone/issues/86
[batphone issue #91]: https://github.com/servalproject/batphone/issues/91
[batphone issue #93]: https://github.com/servalproject/batphone/issues/93
[batphone issue #96]: https://github.com/servalproject/batphone/issues/96
[serval-dna issue #1]: https://github.com/servalproject/serval-dna/issues/1
[serval-dna issue #28]: https://github.com/servalproject/serval-dna/issues/28
