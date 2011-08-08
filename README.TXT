Serval BatPhone for Root Users
------------------------------

Enables Wifi Adhoc mode and runs batmand, asterisk and dna server to allow easy use of Android phones in
a Serval/Village Telco telephony mesh, including with the existing Mesh Potato "wifi land line"
devices.

This program should be considered ALPHA quality, and is currently intended for demonstration
purposes only.  

IMPORTANT NOTICE: 
Serval BatPhone and Village Telco's mesh telephony is a "best effort" service, primarily intended for when
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
This application needs root-permission.

Credits/Thanks:
---------------
* parrothd  (shell-scripts for tethering - an excellent starting-point)
http://forum.xda-developers.com/showthread.php?t=474470

*zodiac (updated tether shell script that works without JF-specific utilities)
http://code.google.com/p/android-wifi-tether/issues/detail?id=9

* manowarlord - (iconset) http://www.kde-look.org/content/show.php/KDE4+Crystal+Diamond+Icons?content=92163 

* Eugene Ray (iptables)


Sipdroid is an open-source SIP client for Android

See http://sipdroid.org for more info

Copyright (C) 2009 The Sipdroid Open Source Project (http://sipdroid.org)
Copyright (C) 2008 Hughes Systique Corporation, USA (http://hsc.com)
Copyright (C) 2006 The Android Open Source Project (http://android.com)
Copyright (C) 2005 Luca Veltri - University of Parma - Italy (http://mjsip.org)

This file is part of Sipdroid (http://www.sipdroid.org)

Sipdroid is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.
 
This source code is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
 
You should have received a copy of the GNU General Public License
along with this source code; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


The source code was based on:

- Mjsip
  contributing the original stack,
  packages org.zoolu.* and where Copyright noted

- Hughes Systique Corporation
  initial port and development of a Test SIP Client to Google's Android Platform,
  packages org.sipdroid.* where Copyright noted

Then the Sipdroid Open Source project began with porting to an actual phone,
the Android Developer Phone 1, and testing basic calls. Therefore also patches to
the Android Frameworks have been submitted.

As Copyright noted, mostly modules in org.sipdroid.ui.* have been added to
fully integrate the Client into Android's built-in dialer such as the standard UI
can be used to initiate calls or ring.

Work has begun to add features like putting calls on-hold, muting, sending DTMF,
and handing-off between networks.

Welcome to the growing worldwide community of Sipdroid users/developers!


i-p-tel GmbH
March, 2009
