Sensor Logging Demonstration
============================
[Serval Project][], November 2012.

These are instructions for demonstrating the prototype Sensor Logging
capability of the [Serval Mesh][] app for Android.

Overview
--------

The sensor logging capability is designed to continuously record the physical
movement of several subjects, and periodically transmit the records to a
central collection point, from which they can be collated and analysed.  The
transmission does not require continuous Internet connection, so subjects are
free to roam throughout the day, as long as they spend a few minutes each day
within range of a WiFi access point or of another device participating in the
study.

Each subject carries an Android phone with two apps installed:

 * The [sensor-logger][] app reads the phone's built-in accelerometer five times a
   second and appends the readings to a log file.  Every hour it injects the
   log file into [Rhizome][] and starts a new file.

 * The [Serval Mesh][] app runs the [Rhizome][] file distribution service,
   which stores all files that have been injected to date and exchanges them
   over WiFi with any other phone that comes into range.  It periodically
   attempts to upload all files it carries (including those received from other
   phones) to a central server.  The upload will work whenever it is within
   range of a WiFi access point or if the phone has a SIM chip and the
   subscriber has a mobile data plan.

The demonstration uses a prototype of the software which is sufficient to prove
the concept but unsuitable for deployment with real subjects.  Many steps that
should be automatic must be performed manually for the demonstration.

Assumed knowledge
-----------------

The commands in this document are [Bourne shell][] commands, using standard
quoting and variable expansion.  Commands issued by the user are prefixed with
the shell prompt `$` to distinguish them from the output of the command.
Single and double quotes around arguments are part of the shell syntax, not
part of the argument itself.

These instructions assume the reader is proficient in the Unix command-line
shell, has some network experience, and has general experience with building,
installing and configuring software packages.

Requirements
------------

For the demonstration, you will need:

 * at least two Android devices (phones) which have been [rooted][];
 * a workstation (eg, laptop) running a recent release of Linux or Max OS X
   and connected to the Internet;
 * a USB cable for connecting an Android device to the workstation.

The workstation will be used to install apps on the Android devices, to
configure the Android devices, and to recover sensor log files from the central
server via an Android device.  For this, it will need to have the Android SDK
(Software Development Kit) installed.

Install Android SDK on workstation
----------------------------------

The first point of Step 3 of [INSTALL.md][] gives instructions for installing
the Android SDK on a workstation.  You only need to install the *Android SDK
Tools*, not Android NDK or Apache Ant or the SDK platform package.

Step 4 of [INSTALL.md][] gives instructions for setting up your environment to
use the Android SDK successfully.  You only need to set the `PATH` environment
variable to include the `$SDK_PATH/platform-tools` directory so that the *adb*
executable is available.

Once the Android SDK is installed and the environment is set up, test that it
works.  Connect an Android device to your workstation via USB and check that it
is recognised as an Android device:

    $ adb devices
    * daemon not running. starting it now on port 5037 *
    * daemon started successfully *
    List of devices attached
    54A51BD473FC	device
    $

If the device does not appear in the list, you must resolve this problem before
proceeding.  This will depend on your workstation's operating system, and is
outside the scope of these instructions.

Install Serval Mesh on an Android device
----------------------------------------

The [Serval Mesh][] Android app must be installed on all the Android devices
(phones) used in the demo.

If an older version of Serval Mesh is already installed on the connected
Android device, you will have to uninstall it first:

    $ adb uninstall org.servalproject
    Success
    $

Download the [Serval Mesh][] Android app from
<https://github.com/downloads/servalproject/batphone/batphone-debug-0.90-alpha-32-g91475eb.apk>
and install it on the connected Android device:

    $ adb install batphone-debug-0.90-alpha-32-g91475eb.apk
    1692 KB/s (1905466 bytes in 1.099s)
        pkg: /data/local/tmp/batphone-debug-0.90-alpha-32-g91475eb.apk
    Success
    $

Repeat this step for all the Android devices (phones) used in the demo.

Install Sensor Logger on an Android device
------------------------------------------

The [sensor-logger][] app must be installed on all the Android devices (phones)
that will be used to generate the logs.  In a real-world deployment, these would
be the phones carried by the subjects.

If an older version of Sensor Logger is already installed on the connected
Android device, you will have to uninstall it first:

    $ adb uninstall org.servalproject.sensorlogger
    Success
    $

Download the Sensor Logger Android app from
<https://github.com/downloads/servalproject/sensor-logger/sensor-logger.apk>
and install it on the connected Android device:

    $ adb install sensor-logger.apk
    397 KB/s (17823 bytes in 0.043s)
        pkg: /data/local/tmp/sensor-logger.apk
    Success
    $

Repeat this step for all the Android devices (phones) used as movement
recorders in the demo.

Configure Android device to connect to Serval HQ
------------------------------------------------

Serval HQ is a [Rhizome][] daemon operated by the [Serval Project][] for the
purpose of this demonstration.  It is available on TCP port 4110 of the
`serval1.csem.flinders.edu.au` server located in the data centre of [CSEM][] at
Flinders University, and should be accessible from everywhere.

To configure an Android device to connect to Serval HQ, first make sure that
the [Serval Mesh][] app has been started at least once on the device, otherwise
the following commands will fail with the message
`/data/data/org.servalproject/bin/servald: not found`.

Issue the following commands:

    $ adb shell echo /data/data/org.servalproject/bin/servald config set rhizome.direct.peer.1 http://129.96.12.91:4110 '|' su
    $ adb shell echo /data/data/org.servalproject/bin/servald config set rhizome.direct.peer.count 1 '|' su
    $ adb shell echo /data/data/org.servalproject/bin/servald config get '|' su
    interfaces=+eth0,+tiwlan0,+wlan0,+wl0.1
    rhizome.datastore_path=/mnt/sdcard/Android/data/org.servalproject/files/rhizome
    rhizome.enabled=1
    rhizome.direct.peer.0=http://129.96.12.91:4110
    rhizome.direct.peer.count=1
    $

To make these settings take effect, you must terminate the Serval Mesh app if
it is running.  There are two ways to do this:

 * EITHER Using the Android Applications Manager on the device:
    1. Go to the home screen, press the MENU button and select “Settings”.
    2. Scroll down to the “Applications” item and press it.
    3. Press the “Manage Applications” item.
    4. Select the list of “Running” applications.
    5. Scroll down to find the “Serval Mesh” application, and press it.
    6. Press the “Force Stop” button.

 * OR Using the workstation:

        $ pid=$(adb shell ps | tr -d '\r' | grep 'org.servalproject$' | awk '{print $2}')
        $ [ "$pid" ] && adb shell echo kill $pid '|' su
        $

To check that the configuration has taken effect, start the [Serval Mesh][] app
on the device, go to the “Share Files” screen and press the MENU button.  A
menu with “Push” and “Sync” should pop up.

Start recording movement of an Android device
---------------------------------------------

On the device, start the “Serval Mesh” app.  The first time you start it after
installation, it will turn OFF the device's WiFi and Switch itself ON
automatically, which puts its WiFi radio into AdHoc mode and cuts off Internet
access via WiFi.  To recover normal WiFi networking, press the “Switch OFF”
button on the Serval Mesh main screen, then go to the Android main Settings
“Wireless & networks” menu and switch WiFi back on.  All subsequent times that
the Serval Mesh app is started, it will be switched OFF, which is how it should
remain for the demo.

On the device, start the “Movement Sensor” app and press its “Start” button.
The app will start recording the device's accelerometer readings into a log
file.  After 60 minutes have elapsed, the app will inject the log file into
Rhizome and start a new file.  This will repeat indefinitely until logging is
stopped by pressing the “Stop” button or the app is forcibly terminated or the
phone is shut down.

When you press the “Stop” button, the app stops logging and injects the current
log file into Rhizome.  This provides a convenient way to run quick tests.

If the app is forcibly stopped or the phone is shut down, the current log file
will be lost.

Send movement logs to Serval HQ
-------------------------------

On the device, start the Serval Mesh app and check that it is switched OFF: the
lower centre button should have a dark background and the caption “Switch ON”.

Ensure that the device has Internet access, either through a mobile carrier
data plan (an installed SIM) or WiFi network access.

In the Serval Mesh app main screen, press the “Share Files” button to go to the
Share Files screen.

On the Share Files screen, press the MENU button and a menu should pop up with
two buttons: “Push” and “Sync”.  Press the “Push” button.  The transfer should
complete within several seconds, or possibly a minute or two, depending on the
speed of the phone's Internet access.  The button will remain visible and the
app will be unresponsive while the push is in progress.

Extract log files from Serval HQ
--------------------------------

Follow the same procedure as for sending logs to Serval HQ (above) but press
the “Sync” button instead of “Push”.

The device will download all the log files that it does not already have.  This
could take several seconds or up to a minute or two, depending on the speed of
the phone's Internet access.  The button will remain visible and the app will
be unresponsive while the sync is in progress.

Once the sync is complete, press the “Find” button on the Share Files screen.
This will list all the log files that have been received (and generated) by the
device.  They can be extracted one by one by selecting each file in turn and
pressing the “Save” button in its pop-up dialog box.

Once all files are extracted, they can be transferred to the workstation by
connecting the Android device to the workstation via USB in *storage* mode.
The extracted files will be on the USB volume in in the directory
`/Android/data/org.servalproject/files/rhizome/saved`.  Simply copy them to a
directory on the workstation.


[Serval Project]: http://www.servalproject.org/
[Serval Mesh]: ../README.md
[INSTALL.md]: ../INSTALL.md
[batphone]: http://github.com/servalproject/batphone/
[sensor-logger]: https://github.com/servalproject/sensor-logger/
[CSEM]: http://www.flinders.edu.au/science_engineering/csem/
[Rhizome]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:technologies:rhizome
[rooted]: http://lifehacker.com/5789397/the-always-up+to+date-guide-to-rooting-any-android-phone
[Bourne shell]: http://en.wikipedia.org/wiki/Bourne_shell
