Release Notes for Serval Mesh 0.90.1
------------------------------------
[Serval Project][], February 2013

These notes accompany the release in February 2013 of version 0.90.1 of the
[Serval Mesh][] app for [Android 2.2 “Froyo”][] and above.

This release fixes several major bugs in [version 0.90][] “Shiny” that was
released in January 2013.

What is Serval Mesh?
--------------------

Serval Mesh is an app for [Android 2.2 “Froyo”][] and above.  It provides free,
secure phone-to-phone voice calling, SMS and file sharing over [Wi-Fi][],
without the need for a SIM card or a commercial mobile telephone carrier.  In
other words, it lets your Android phone call other Android phones running
Serval Mesh within Wi-Fi range.

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

Serval Mesh requests [root permission][] (super-user) on your Android device in
order to put Wi-Fi into [Ad-Hoc mode][].  If you grant super-user permission to
Serval Mesh, then it will take control of your device's Wi-Fi and use it to
contact other Serval Mesh devices in the vicinity.  **This will cut off normal
Wi-Fi network access.**

If your device has no root access or if you deny super-user permission to
Serval Mesh, or if no other Ad-Hoc mode devices are nearby, then Serval Mesh
will revert to using Wi-Fi in the normal Client mode.  This should not
interrupt conventional network access, but it could do so.  If there is no
nearby access point like a home Wi-Fi router or public hot-spot then Serval
Mesh will put your device's Wi-Fi into [Access Point mode][] (turn on personal
hotspot).  **This will give nearby devices access to your mobile data plan, and
COULD COST YOU MONEY.**

Serval Mesh telephony is a “best effort” service, primarily intended for when
conventional telephony is not possible or cost effective, and **MUST NOT BE
RELIED UPON** for emergencies in place of carrier-grade communications systems.
The Serval Project cannot be held responsible for any performance or
non-performance of the technologies that they provide in good will, and if you
use these technologies you must agree to indemnify the Serval Project from any
such claims.

The Serval Mesh software copies all files shared using the Rhizome file
distribution service to other phones and devices running the Serval Mesh
software, regardless of size, content or intended recipient.  The Serval
Project cannot be held responsible for the legality or propriety of any files
received via Rhizome, nor for any loss, damage or offence caused by the
transmission or receipt of any content via Rhizome.

See the disclaimers below.

What's new since 0.90
---------------------

The following issues in [version 0.90][] have been fixed:

 * application crash on start reported on Android version 2.3.7 CyanogenMod
   (French error message observed on HTC Desire HD), caused by an unknown
   exception thrown from `getPackageManager().getPackageInfo()` leaving a
   variable uninitialised, later producing an unhandled `NullPointerException`
   -- see [serval-dna issue #43][];

 * application crash on opening the Message list under CyanogenMod 10 -- see
   [batphone issue #67][];

 * application crash on opening the Message list if the SD Card is absent or
   apparently unmounted (observed on an HTC phone with user-installed ROM) --
   was an unhandled exception thrown when the Batphone app attempts to insert
   the initial message into the empty "messages" SQLite database;

 * Serval-DNA daemon crash if system memory exhausted -- the function that
   assembles Rhizome bundle advertisments did not perform proper clean-up
   upon receiving a `malloc()` NULL result;

 * initial period of silence in audio calls via an Asterisk SIP-VoIP gateway --
   caused when the initial RTP timestamp was non-zero (probably arising from
   SIP Early Media) -- the solution was for VoMP playback to treat the initial
   timestamp as zero instead of filling the jitter buffer to synchronise with
   it;

 * improve voice call usability on congested or high-latency networks by
   increasing the VoMP timeout when waiting for the called party to indicate
   ringing while placing a voice call;

 * reduce UI freezes during Rhizome transfers by preventing the Serval DNA
   daemon from holding a Rhizome database read lock for the entire duration of
   an outgoing bundle, instead it now acquires and releases the lock for each
   sent buffer;

 * reduce UI freezes and reduce network congestion during Rhizome transfers by
   reducing the MDP packet priority for Rhizome transfers.

There are also more known issues, listed below.

What's new since 0.08
---------------------

If you have used [version 0.08][], you will notice these changes:

 * A completely redesigned human interface.

 * A much smaller APK; faster to download and install.

 * No need for third-party apps like SMSDroid or WebSMS.

The main screen now presents nine buttons:

 * *Call* to make voice calls
 * *Messages* to compose and view messages
 * *Contacts* to discover nearby phones on the Mesh and show your Contact List
 * *Maps* calls up the Serval Maps interface (if installed)
 * *Share files* to send files via the Rhizome file-distribution system, list
   and view received files, see how much storage you are using
 * *Share Us* to give the Serval Mesh software to other users with compatible
   Android devices
 * *Settings* to adjust settings (see below)
 * *Switch Off(On)* to stop or start Serval Mesh
 * *Help* for instructions and information

The help system is more detailed and complete:

 * *Guide To Interface* explains the buttons on the main screen
 * *Accounts & Contacts* explains how Serval Mesh identifies you and other
   users to each other
 * *Licence* is the full text of the software licence
 * *Serval Security* describes Serval's security features, Android permissions
   used, and the Privacy Policy
 * *About* introduces the Serval Project and leads to the Donate button
 * *Quick Links* contains some useful links for further reading
 * *Serval Version* is the full text of these release notes

The Settings menu has been overhauled:

 * *Wifi Settings* lets you examine and change Wi-Fi settings
 * *Accounts Management* lets you change your Serval Mesh phone number and name
 * *View Logs* shows a log of recent software activity
 * *Redetect Wifi* redetects the device's Wi-Fi chipset

There have been enormous changes under the hood:

 * The foundations of the [Serval Security Framework][] are now in place.
   [Elliptic curve cryptography][NaCl] is used for identifying, protecting and
   authenticating subscribers and mesh network traffic.

 * All Serval-to-Serval traffic (except Rhizome transfers) is now encapsulated
   in Serval's new, secure [Mesh Datagram Protocol][MDP], implemented as an
   overlay network on standard [IP][] over [Wi-Fi][].

 * The original Java implementation of the [Rhizome][] file sharing system has
   been superseded by a new implementation in C within the [serval-dna][]
   component, using [SQLite][] as the local storage engine.

 * Voice calls are now carried over the mesh using Serval's own [Voice over
   Mesh Protocol][VoMP], which has been designed to replace [SIP][] and
   [RTP][].  As a result, call quality and latency have improved.

 * [MeshMS][] (Serval's SMS-like service) now uses [Rhizome][] as its transport.

 * Improved stability and responsiveness.

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

The following issues are planned to be fixed by version 1.0:

 * Poor support for multi-hop mesh calls -- see [serval-dna issue #37][].  You
   can successfully call someone who is within Wi-Fi range of your phone, but
   calls that need to be carried through intermediate phones are unreliable.

 * Rhizome slowly and gradually consumes all space on your SD Card as you send
   and receive files -- see [batphone issue #8][] and [serval-dna issue #10][].
   You can work around this by deleting the Rhizome database while the Serval
   Mesh app is not running, or by re-installing the Serval Mesh app.  To delete
   the database, use the [adb shell][] command:

        rm -r /sdcard/Android/data/org.servalproject/files/rhizome

 * Mesh call quality degrades whenever Rhizome file or MeshMS transfers are in
   progress -- see [serval-dna issue #1][].

 * The Serval Mesh app works best with Android [root permission][], because it
   depends on Wi-Fi [Ad-Hoc mode][] which can only be started with root
   permision -- see [batphone issue #47][].

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

 * VoMP does not play a "ringing" sound while placing a call, nor a "hangup"
   sound when the other party hangs up -- see [batphone issue #76][].

 * After using the "Unshare" button on a Rhizome file, it does not disappear
   from the Rhizome file list -- see [batphone issue #53][].  Work around:
   close the list (Back control) and re-open it ("Find" button).

 * The application has been observed to crash when the remote party hangs up a
   voice call under conditions of high network latency or packet loss -- see
   [batphone issue #68][].

 * The application may crash when adding a contact from the peer list -- see
   [batphone issue #70][].

 * The application may crash when opening the peer list, observed on Samsung
   Galaxy S running CyanogenMod 10 nightly build -- see [batphone issue #71][].

 * Rhizome can worsen network congestion, because Rhizome database lock
   conflicts under conditions of high network packet loss cause Rhizome to
   re-fetch the failed bundles from the start -- see [batphone issue #72][].

 * The user's personal hotspot name (ESSID) is not restored after the
   application has used Wi-Fi in Access Point mode: it remains set to
   "mesh.servalproject.org" until the user re-sets it -- see [batphone
   issue #73][].

 * The incoming message notification sound on Samsung Galaxy Ace with
   CyanogenMod 7 is the last audio message played by WhatsApp -- see [batphone
   issue #75][].

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
version 2][GPL2].  All source code is freely available from the Serval
Project's [batphone][] and [serval-dna][] Git repositories on [GitHub][].

Acknowledgements
----------------

Development of Serval Mesh was funded by the [New America Foundation's][NAF]
[Open Technology Institute][OTI] and the [Shuttleworth Foundation][].

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

SERVAL MESH may COST YOU MONEY if you have a MOBILE DATA PLAN by allowing
NEARBY DEVICES TO USE YOUR DATA PLAN WITHOUT YOUR KNOWLEDGE OR CONSENT.

SERVAL MESH may REVEAL AND/OR BROADCAST YOUR LOCATION, IDENTITY OR OTHER
INFORMATION through its normal operation.

SERVAL MESH is an INCOMPLETE, PRE-PRODUCTION software, experimental in nature
and is not to be considered fit for merchantability for any purpose.  It has
many defects, omissions and errors that will hamper its fulfilling of its
intended purposes.

-----
**Copyright 2013 Serval Project Inc.**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: https://play.google.com/store/apps/details?id=org.servalproject
[Serval Mesh Privacy Policy]: ../PRIVACY.md
[Serval Security Framework]: https://github.com/servalproject/serval-docs/blob/master/serval-security-framework/ServalSecurityFramework.odt
[version 0.08]: ./RELEASE-0.08.md
[version 0.90]: ./RELEASE-0.90.md
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
[Wi-Fi]: http://en.wikipedia.org/wiki/Wi-Fi
[SQLite]: http://www.sqlite.org/
[SIP]: http://en.wikipedia.org/wiki/Session_Initiation_Protocol
[RTP]: http://en.wikipedia.org/wiki/Real-time_Transport_Protocol
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
[GPL3]: http://gplv3.fsf.org/
[GPL2]: http://www.gnu.org/licenses/gpl-2.0.html
[contributors]: ../CREDITS.md
[batphone issue #8]: https://github.com/servalproject/batphone/issues/8
[batphone issue #47]: https://github.com/servalproject/batphone/issues/47
[batphone issue #53]: https://github.com/servalproject/batphone/issues/53
[batphone issue #67]: https://github.com/servalproject/batphone/issues/67
[batphone issue #68]: https://github.com/servalproject/batphone/issues/68
[batphone issue #70]: https://github.com/servalproject/batphone/issues/70
[batphone issue #71]: https://github.com/servalproject/batphone/issues/71
[batphone issue #72]: https://github.com/servalproject/batphone/issues/72
[batphone issue #73]: https://github.com/servalproject/batphone/issues/73
[batphone issue #75]: https://github.com/servalproject/batphone/issues/75
[batphone issue #76]: https://github.com/servalproject/batphone/issues/76
[serval-dna issue #1]: https://github.com/servalproject/serval-dna/issues/1
[serval-dna issue #10]: https://github.com/servalproject/serval-dna/issues/10
[serval-dna issue #28]: https://github.com/servalproject/serval-dna/issues/28
[serval-dna issue #35]: https://github.com/servalproject/serval-dna/issues/35
[serval-dna issue #37]: https://github.com/servalproject/serval-dna/issues/37
[serval-dna issue #43]: https://github.com/servalproject/serval-dna/issues/43
[CC BY 4.0]: ../LICENSE-DOCUMENTATION.md
