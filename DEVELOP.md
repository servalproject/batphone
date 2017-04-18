Developing Serval Mesh
======================
[Serval Project][], April 2017

These are instructions for developing the [Serval Mesh][README] app for
Android.

Assumed knowledge
-----------------

The commands in this document are [Bourne shell][] commands, using standard
quoting and variable expansion.  Commands issued by the user are prefixed with
the shell prompt `$` to distinguish them from the output of the command.
Single and double quotes around arguments are part of the shell syntax, not
part of the argument itself.

These instructions assume the reader is proficient in the Unix command-line
shell and has general experience with setting up and using software development
environments.

Supported Platforms
-------------------

These instructions have been sued successfully on the following platforms;

 * [Debian][] Linux, ix86 and x86\_64

Other Linux distributions, eg, [Ubuntu][] and [Fedora][], should work if
sufficiently recent.

Other platforms for which the [Android SDK][] is available, such as Microsoft
Windows, might work (eg, using [Cygwin][]), but are not tested or supported.

Basic concepts
--------------

The [Serval Mesh][] app is composed of two parts:

 * “Batphone” is the Android user interface code, written in Java with XML
   resource files, compiled using the [Android SDK][].  The official Batphone
   source code is kept in the [batphone][] repository on GitHub.

 * [Serval DNA][] is the core networking component, written in C, compiled
   using the [Android NDK][].  The official Serval DNA source code is kept in
   the [serval-dna][] repository on GitHub.  The [batphone][] repository embeds
   that repository as a [Git submodule][].

Working with Git
----------------

The Serval Project uses [Git][] to manage all its source code, and uses the
free [GitHub][] as its hosting provider.

All Serval Mesh development is done in a [Git clone][] of the official
[batphone][] and [serval-dna][] source code repositories on [GitHub][].  A
“clone” is a local copy on your own computer that you can modify however you
wish without affecting any other developers and without needing any permission.

### Fork on GitHub

The recommended way to start working on your own copy of the Serval Project
source code is to make a [GitHub fork][] of both official repositories.

A "fork" is simply a [Git][] repository, stored on GitHub, that was copied
(cloned) directly from another GitHub repository.  The difference is that,
although you may not be permitted to modify the original repository (no
permission to push), you can modify your own forked copy however you like.

To create a fork on GitHub, use the GitHub web interface.

Once a fork exists, it does not automatically stay in sync with the original
repository; you must [manually do that yourself](#sync-with-the-serval-project).

### Clone from GitHub

To clone from GitHub, first choose [which remote URL to use][].  The
recommended scheme is HTTPS, because it will pass through most firewalls.  An
SSH remote URL works just as well as HTTPS, as long as you can access the SSH
port (443) on github.com.  You can avoid the need to enter a password every
time you access GitHub by [adding your public key to your GitHub account][].  A
third option is [SSH over the HTTPS port][].

The following example shows how to create clones of repositories that you have
already [forked on GitHub](#fork-on-github).  It creates the local clones in
the current working directory, and uses the HTTPS method to download from
GitHub:

    $ git clone -q https://github.com/YourGitHubAccountName/batphone.git
    $ cd batphone
    $ git submodule init
    Submodule 'jni/serval-dna' (https://github.com/YourGitHubAccountName/serval-dna.git) registered for path 'jni/serval-dna'
    $ git submodule -q update
    $

If all goes well, you will now have a full copy of the Serval Mesh source code
on your computer, with the “master” branch checked out.

### Sync with the Serval Project

To keep your fork updated (synced) with the latest changes from the Serval
Project, first [configure your clone with a remote that points to the original
repositories][]:

    $ git remote add upstream https://github.com/servalproject/batphone.git
    $ cd jni/serval-dna
    $ git remote add upstream https://github.com/servalproject/serval-dna.git
    $ cd ../..
    $

Then, use the [Git fetch][] or [Git pull][] command whenever you want to
download changes from the upstream repositories.  If you are working on the
*development* branch (not recommended), you may have to merge the upstream
changes with your own local changes, which is outside the scope of these
instructions.

### Keeping backup copies

It is highly recommended you use [Git][] to keep a backup copy of your work.
This generally involves cloning your local repository onto another device (such
as a separate hard drive or host), and pushing your changes to that clone at
regular intervals.  With Git, you can make as many clones as you like, and push
to them whenever you like.

If you already [forked on GitHub](#fork-on-github) before starting work, then
those forked repositories are the best place to back up your work.  To upload
your changes to GitHub, simply [push to your GitHub fork][].  Do this
regularly, eg, at the end of every day.

### Contributing to the Serval Project

You cannot push changes from your cloned repositories into the Serval Project's
repositories unless you are a member of the Serval Project development team.

The recommended way to contribute your modifications to the [batphone][] or
[serval-dna][] source code is to use [forked repositories on
GitHub](#fork-on-github), and when your changes are ready, submit them to the
Serval Project as a [pull request][].

-----
**Copyright 2014-2016 Serval Project Inc.**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].


[Serval Project]: http://www.servalproject.org/
[README]: ./README.md
[Serval Mesh]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:
[Serval DNA]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servaldna:
[batphone]: http://github.com/servalproject/batphone/
[serval-dna]: http://github.com/servalproject/serval-dna/
[Git]: http://git-scm.com/
[GitHub]: http://github.com/
[Git clone]: http://git-scm.com/book/en/v2/Git-Basics-Getting-a-Git-Repository#Cloning-an-Existing-Repository
[Git submodule]: http://git-scm.com/book/en/v2/Git-Tools-Submodules
[GitHub fork]: https://help.github.com/articles/fork-a-repo/
[Git remote]: http://git-scm.com/book/en/v2/Git-Basics-Working-with-Remotes
[Git push]: http://git-scm.com/docs/git-push
[Git fetch]: http://git-scm.com/docs/git-fetch
[Git pull]: http://git-scm.com/docs/git-pull
[which remote URL to use]: https://help.github.com/articles/which-remote-url-should-i-use/
[adding your public key to your GitHub account]: https://help.github.com/articles/generating-ssh-keys/
[SSH over the HTTPS port]: https://help.github.com/articles/using-ssh-over-the-https-port/
[push to your GitHub fork]: https://help.github.com/articles/pushing-to-a-remote/
[pull request]: https://help.github.com/articles/creating-a-pull-request/
[configure your clone with a remote that points to the original repositories]: https://help.github.com/articles/configuring-a-remote-for-a-fork/
[Android SDK]: http://developer.android.com/sdk/index.html
[Android NDK]: http://developer.android.com/sdk/ndk/index.html
[Bourne shell]: http://en.wikipedia.org/wiki/Bourne_shell
[Debian]: http://www.debian.org/
[Ubuntu]: http://www.ubuntu.com/
[Fedora]: http://fedoraproject.org/
[Cygwin]: http://www.cygwin.com/
[CC BY 4.0]: ./LICENSE-DOCUMENTATION.md
