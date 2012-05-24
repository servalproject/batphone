Serval Mesh
-----------

Enables Wifi Adhoc mode and runs servald to allow easy use of Android phones in
a Serval/Village Telco telephony mesh, including with the existing Mesh Potato "wifi land line"
devices.

This program should be considered ALPHA quality, and is currently intended for demonstration
purposes only.  

IMPORTANT NOTICE: 
Serval Mesh and Village Telco's mesh telephony is a "best effort" service, primarily intended for when
traditional telephony is not possible or cost effective, and MUST NOT BE RELIED UPON for emergencies in
place of carrier-grade communications systems.  Serval and Village Telco cannot be held responsible for any
performance or non-performance of the technologies that they provide in good will, and if you use these
technologies you must agree to indemnify Serval and Village Telco from any such claims.  

20100613 - Paul Gardner-Stephen (paul@servalproject.org)

Based on:

Wifi Tether for Root Users
--------------------------

This software turns your android-based handset (tested with a T-Mobile G1) into "something" like
a wireless-accesspoint (that's incorrect in technical-view because the wireless-interface will be 
set-up in AdHoc-Mode). Clients can connect via wifi and get access to internet using the 3G-connection
(GPRS/EDGE/UMTS/HSDPA) which is established by the handset.

Warning:
--------
This application needs root-permission in order to enable adhoc wifi support.

Credits/Thanks:
---------------
* parrothd  (shell-scripts for tethering - an excellent starting-point)
http://forum.xda-developers.com/showthread.php?t=474470

*zodiac (updated tether shell script that works without JF-specific utilities)
http://code.google.com/p/android-wifi-tether/issues/detail?id=9

* manowarlord - (iconset) http://www.kde-look.org/content/show.php/KDE4+Crystal+Diamond+Icons?content=92163 

* Eugene Ray (iptables)
