Serval Mesh README
==================
[Serval Project][], March 2014

[Serval Mesh][] (also called “Batphone”) is an app for [Android 2.2 “Froyo”][]
and above.  It provides free, secure phone-to-phone voice calling, SMS and file
sharing over Wi-Fi, without the need for a SIM card or a commercial mobile
telephone carrier.  In other words, it lets your Android phone communicate with
other Android phones running Serval Mesh within Wi-Fi range.

The latest release of Serval Mesh is available for download on [Google Play][].

Serval Mesh is [free software][] produced by the [Serval Project][].  The
Java/XML source code of Serval Mesh is licensed to the public under the [GNU
General Public License version 3][GPL3].  The [serval-dna][] component of
Serval Mesh is licensed to the public under the [GNU General Public License
version 2][GPL2].  All [technical documentation][] is licensed to the public
under the [Creative Commons Attribution 4.0 International license][CC BY 4.0].

All source code and technical documentation is freely available from the Serval
Project's [batphone][] and [serval-dna][] Git repositories on [GitHub][].


Warnings
--------

Serval Mesh is **EXPERIMENTAL SOFTWARE**.  It has not yet reached version 1.0,
and is intended for pre-production, demonstration purposes only.  It may not
work as advertised, it may lose or alter messages and files that it carries, it
may consume a lot of space, speed and battery, and it may crash unexpectedly.

The Serval Mesh "Connect" screen allows you to connect to other Serval Mesh
devices that provide their own access points.  If you do so, **this will cut
off normal Wi-Fi network access** while Serval Mesh is running, and services
like Google Updates, E-mail, social media and other notifications may not work,
or if you have a mobile data plan, **may cost you money.**

On the Serval Mesh "Connect" screen, connecting to any network marked "HotSpot"
will put your device's Wi-Fi into [Access Point mode][].  **This will give
nearby devices access to your mobile data plan, and COULD COST YOU MONEY.**

On the Serval Mesh "Connect" screen, connecting to any network marked "Mesh"
will request [root permission][] (super-user) on your Android device in order
to put Wi-Fi into [Ad-Hoc mode][].  If you grant super-user permission to
Serval Mesh, **this will cut off normal Wi-Fi network access** while Serval
Mesh is running, and services like Google Updates, E-mail, social media and
other notifications may not work, or if you have a mobile data plan, **may cost
you money.**

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

Documentation
-------------

 * [CURRENT-RELEASE.md](./CURRENT-RELEASE.md)  Release notes for the current
   release.

 * [INSTALL.md](./INSTALL.md)  Instructions for building the Android APK from
   the source code and installing manually.

 * [DEVELOP.md](./DEVELOP.md)  Tips for contributing to the software

 * [PRIVACY.md](./PRIVACY.md)  Privacy policy.

 * [doc](./doc/)  Technical documentation, including past release notes.

 * [CREDITS.md](./CREDITS.md)  Individuals and organisations who have
   contributed to the software.

 * [Serval Wiki][] contains helpful information for developers and users.

 * [GitHub Issues][] Tracking of bug reports and tasks.  Please see the wiki
   pages on reporting bugs and managing issues before adding any new issues.

-----
**Copyright 2014 Serval Project Inc.**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].


[Serval Mesh]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:
[Android 2.2 “Froyo”]: http://developer.android.com/about/versions/android-2.2-highlights.html
[Serval Project]: http://www.servalproject.org/
[Google Play]: https://play.google.com/store/apps/details?id=org.servalproject
[GPL3]: ./GPLV3.TXT
[GPL2]: http://www.gnu.org/licenses/gpl-2.0.html
[CC BY 4.0]: ./LICENSE-DOCUMENTATION.md
[batphone]: https://github.com/servalproject/batphone
[serval-dna]: https://github.com/servalproject/serval-dna
[GitHub]: https://github.com/servalproject
[free software]: http://www.gnu.org/philosophy/free-sw.html
[Serval Wiki]: http://developer.servalproject.org/dokuwiki
[technical documentation]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:dev:techdoc
[GitHub Issues]: https://github.com/servalproject/batphone/issues
[root permission]: http://en.wikipedia.org/wiki/Android_rooting
[AdHoc mode]: http://compnetworking.about.com/cs/wirelessfaqs/f/adhocwireless.htm
[Access Point mode]: http://compnetworking.about.com/cs/wireless/g/bldef_ap.htm
