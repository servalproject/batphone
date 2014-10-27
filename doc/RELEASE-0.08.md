Release Notes for Serval Mesh 0.08
----------------------------------
[Serval Project][], 27 June 2012

These notes accompany the release of version 0.08 of the [Serval Mesh][] app
for [Android 2.1 “Eclair”][] and above.  This release succeeds [version 0.07][]
released 15th December 2011.

New features
------------

* Rhizome store and forward transport.  Share files, share videos, and send
  text messages to people who aren’t directly reachable at the moment. This
  should be considered an early preview, there’s plenty of work still to go.
  (text messaging currently requires WebSMS / SMSDroid to use as the front end)

* Improved peer list.

* Now with name resolution from your Android contacts, real-time display of
  network reach-ability information, and quick link to open WebSMS (if
  installed) for text message entry.

* Smaller APK file size.

* Improved handset support:
  * added Galaxy Tab

Bug Fixes
---------

 * Reduced prompting for root access, better detection of failures.

 * Keep a foreground service running to prevent our process being killed while started.

 * Fixed control of hotspot on android 4.0+

A full list of commits are available in the [GitHub repository][batphone].

-----
**Copyright 2012 Serval Project Inc.**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: https://play.google.com/store/apps/details?id=org.servalproject
[version 0.07]: ./RELEASE-0.07.md
[batphone]: https://github.com/servalproject/batphone
[Android 2.1 “Eclair”]: http://developer.android.com/about/versions/android-2.1.html
[CC BY 4.0]: ../LICENSE-DOCUMENTATION.md
