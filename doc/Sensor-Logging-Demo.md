Sensor Logging Demonstration
============================
[Serval Project][], November 2012, updated March 2013

These are instructions for demonstrating the prototype of the [Serval Project][]
sensor logger.

Overview
--------

The sensor logging capability is designed to continuously record the physical
movement of several subjects, and periodically transmit the records to a
central collection point, from which they can be collated and analysed.  The
transmission does not require continuous Internet connection, so subjects are
free to roam throughout the day, as long as they spend a few minutes each day
within range of a WiFi access point.

Each subject carries an Android phone with two apps installed:

 * The [Movement Sensor][] app reads the phone's built-in accelerometer five
   times a second and appends the readings to a log file.  Every hour it
   injects the log file into [Rhizome][] and starts a new file.

 * The [Serval Mesh][] app runs the [Rhizome][] file distribution service,
   which stores all files that have been injected to date and exchanges them
   over WiFi with any other phone that comes into range.  It periodically
   attempts to upload all files it carries (including those received from other
   phones) to a central server.  The upload will work whenever it is within
   range of a WiFi access point or if the phone has a SIM chip and the
   subscriber has a mobile data plan.

Scope of demonstration
----------------------

The demonstration uses a prototype of the software which is sufficient to prove
a concept but is unsuitable for deployment with real subjects.  Many steps that
should be automatic are performed manually for the demonstration.

In this demonstration:

1. the Android apps are installed on two or more Android devices (phones);
2. the first Android device generates one or more movement sensor log files;
3. the first Android device transmits the log files to Serval HQ;
4. a second Android device fetches the log files from Serval HQ;
5. a workstation downloads the log files from the second Android device;
6. the workstation is used to inspect the log file contents.

Assumed knowledge
-----------------

These instructions assume the reader is technically experienced with the
Internet, WiFi networking, USB devices, using Android smartphones, general
software installation and administration, and troubleshooting.

Requirements
------------

For the demonstration, you will need:

 * at least two Android devices (phones) with Internet access (WiFi access
   point or Mobile data plan);
 * for convenience, a QR code reader app installed on all Android devices,
   eg, [QR Droid][];
 * a workstation (eg, laptop);
 * a USB cable for connecting an Android device to the workstation.

The workstation is only used at the end of the demo to fetch sensor log
files from the central server via an Android device, because it is easier to
inspect the contents of ZIP files using a conventional operating system than
an Android device.  If ZIP files can be inspected directly using the second
Android device, then the workstation is unnecessary for the demonstration.

Install Serval Mesh app
-----------------------

The [Serval Mesh][] Android app must be installed on all the Android devices
(phones) used in the demo.

Download the [Serval Mesh][] app onto each Android device from
<http://developer.servalproject.org/files/sensor-log/Serval_Mesh.apk>
and install it.  The following QR code is a convenient way to do this:

![Serval Mesh QR code](https://chart.googleapis.com/chart?cht=qr&chs=300x300&chl=http://developer.servalproject.org/files/sensor-log/Serval_Mesh.apk)

The [Serval Mesh][] app states that it requires root permission to operate,
but root is not necessary for this demo, only conventional Internet access.

Once the [Serval Mesh][] app is installed, complete the installation by
starting it.  This will have the side effect of turning OFF the device's WiFi
networking and putting the WiFi into AdHoc mode, which will cut off normal
Internet access (unless access is via a 3G mobile data plan).  To restore
normal WiFi networking, if it is needed to continue the demonstration, first
go to the *Serval Mesh* main screen and press the *Switch OFF* button at centre
bottom.  Then go to Android's main Settings *Wireless & networks* menu and
switch WiFi back on.  All subsequent times that the Serval Mesh app is started,
it will be in the OFF state, which is how it should remain for the demo.

Install Movement Sensor app
---------------------------

The [Movement Sensor][] app must be installed on all the Android devices (phones)
that will be used to generate the logs.  In a real-world deployment, these would
be the phones carried by the subjects.

**IMPORTANT**: the [Movement Sensor][] app must be installed *after* the [Serval
Mesh][] app, or it will not have permission to inject its log files into
[Rhizome][].

Download the Movement Sensor app onto each Android device from
<http://developer.servalproject.org/files/sensor-log/sensor-logger.apk>
and install it.  The following QR code is a convenient way to do this:

![Sensor Logger QR code](https://chart.googleapis.com/chart?cht=qr&chs=300x300&chl=http://developer.servalproject.org/files/sensor-log/sensor-logger.apk)

Start recording movement of an Android device
---------------------------------------------

Choose an Android device (phone) to be carried by a subject, with the [Serval
Mesh][] and [Movement Sensor][] apps installed, as per the instructions above.

On the device, start the *Movement Sensor* app.  The app's screen shows a
*Device ID* which is a sequence of six letters and numbers.  This ID does not
reveal any device- or user-specific information, because it is randomly
generated when the app is first started, and is not based on the IMEI or phone
number or any other system information.  The ID will persist until the app is
uninstalled.  To retain the same ID when upgrading the app, do not *remove* the
app first.

To help identify the log files generated by each subject, you will need to keep
a register of all subjects and their Device IDs.

On the *Movement Sensor* app screen, press the *Start* button.  The app will
start recording the device's accelerometer readings into a log file.  After 60
minutes have elapsed, the app will inject the log file into Rhizome and start a
new file.  This will repeat indefinitely until logging is stopped by pressing
the *Stop* button or the app is forcibly terminated or the phone is shut down.

When you press the *Stop* button, the app stops logging and injects the current
log file into Rhizome.  This provides a convenient way to run quick tests.

If the app is forcibly stopped or the phone is shut down, the current log file
will be lost.

Upload movement logs to Serval HQ
---------------------------------

This step would occur automatically in a production deployment, but must be
performed manually for the demonstration.

On the subject's device, start the [Serval Mesh][] app and check that it is
switched OFF: the lower centre button on the main screen should have a dark
background and the caption *Switch ON*.

Ensure that the device has Internet access, either through a mobile 3G data
plan (an installed SIM) or WiFi network access.

In the *Serval Mesh* app main screen, press the centre *Share Files* button
to go to the Share Files screen.

On the Share Files screen, press the MENU button and a menu should pop up with
two options: *Push* and *Sync*.  Press the *Push* button to start the upload.
The upload should complete within several seconds, but could take up to a
minute or two, depending on the speed of the phone's Internet access and the
volume of logging since the last upload.  There is nothing to indicate that the
upload is in progress or when it finishes, nor whether it succeeded or failed.

Retrieve log files from Serval HQ
---------------------------------

Choose a second Android device to retrieve the movement log files.  In a
production deployment, this would be done using Serval software installed
directly on a Linux or Apple Mac workstation, but in this demo it is done
using an Android device as intermediary.

On the retrieval device, follow the same procedure as for sending logs to
Serval HQ (above) but press the *Sync* button instead of *Push*.

The device will download all the log files that it does not already have from
Serval HQ.  This could take several seconds or up to a minute or two, depending
on the speed of the device's Internet access and the volume of log files uploaded
since the last retrieval.  There is nothing to indicate that the download is
in progress or when it finishes, nor whether it succeeded or failed.  If it
succeeds, then newly downloaded files will appear in the *Find* screen (see
below).

Once the sync is complete, press the *Find* button on the Share Files screen.
This will list all the log files that have been received (and generated) by the
device.  They can be extracted one by one by selecting each file in turn and
pressing the *Save* button in its pop-up dialog box.

Once all files are extracted, they can be transferred to the workstation by
connecting the Android device to the workstation via USB in *storage* mode.
The extracted files will be on the USB volume in in the directory
`/Android/data/org.servalproject/files/rhizome/saved`.  Simply copy them to a
directory on the workstation.

Inspect log files
-----------------

Each log file is a ZIP file with a name of the form
`XXXXXX_Accelerometer_Gsensor_YYYYMMDD_HHMMSS.zip`
containing a single data file with a name of the form
`XXXXXX_Accelerometer_Gsensor_YYYYMMDD_HHMMSS.log`.  `XXXXXX` is the *Device
ID* of the device that generated the file, and `YYMMDD_HHMMSS` is the date/time
stamp of when the log file commenced.

**Warning**: if a device has an incorrect date/time/zone setting, then its file
time stamps will not be correct.

Each data file is in ASCII [CSV][] format with a single header line followed by
many lines of data, each line representing a single sensor reading, for
example:

```
time,accuracy,x,y,z,magnitude,hpf_x,hpf_y,hpf_z,hpf_magnitude
0.0000,3,-0.027,0.000,9.344,9.344,0.000,0.000,0.000,0.000
0.0000,3,-0.068,0.000,9.262,9.262,-0.030,0.000,-0.061,0.068
0.2058,3,-0.068,0.109,9.344,9.344,-0.023,0.081,0.016,0.085
0.4122,3,-0.109,0.150,9.262,9.264,-0.047,0.090,-0.049,0.113
0.6209,3,-0.027,0.150,9.153,9.154,0.026,0.067,-0.117,0.137
0.8275,3,-0.027,0.191,9.412,9.414,0.019,0.080,0.105,0.134
```

Troubleshooting
---------------

If no log files arrive at the second Android device, then check the following:

1. Log files are being injected into Rhizome on the first Android device.  Go to
   the Serval Mesh app main screen on the first device, select *Share Files* then
   on the next screen press the *Find* button.  A list of files should appear,
   with names of the form `XXXXXX_Accelerometer_Gsensor_YYYYMMDD_HHMMSS.zip`.  Every
   time logging is started and stopped again, a new file should appear, with the
   current date and time in its name.  If no files appear, perhaps the
   [Movement Sensor][] app was not installed *after* the [Serval Mesh][] app.
   Un-install the [Movement Sensor][] app then install again.  Simply re-
   installing it without un-installing first may not resolve the problem.

2. Log files are being uploaded from the first Android device to Serval HQ
   successfully.  If the device does not have an active mobile 3G data plan, then
   this depends on WiFi access.  Check that the [Serval Mesh][] app is in OFF
   state (the button at lower centre of the *Serval Mesh* main screen should
   have a dark background and the caption *Switch ON*).  Check that the device's
   WiFi networking is on and associated to an access point.  Check that the device
   has a working internet connection.

3. Log files are being downloaded from Serval HQ to the second Android device
   successfully.  Ensure that the device has a working internet connection by
   following the same steps described for the first device in the previous point.

4. Once all the above checks pass, if log files still do not arrive at the second
   Android device, seek the assistance of a senior developer from the [Serval
   Project][].

-----
**Copyright 2013 Serval Project Inc.**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: ../README.md
[INSTALL.md]: ../INSTALL.md
[batphone]: http://github.com/servalproject/batphone/
[Movement Sensor]: https://github.com/servalproject/sensor-logger/
[CSEM]: http://www.flinders.edu.au/science_engineering/csem/
[Rhizome]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:technologies:rhizome
[rooted]: http://lifehacker.com/5789397/the-always-up+to+date-guide-to-rooting-any-android-phone
[QR Droid]: https://play.google.com/store/apps/details?id=la.droid.qr
[CSV]: http://en.wikipedia.org/wiki/Comma-separated_values
[CC BY 4.0]: ../LICENSE-DOCUMENTATION.md
