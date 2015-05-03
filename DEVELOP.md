Developing Serval Mesh
======================
[Serval Project][], May 2015

These are instructions for developing the [Serval Mesh][README] app for
Android.  These instructions are aimed at Unix command line development, not
[Eclipse][] or other IDEs.

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

These instructions are suitable for the following platforms;

 * [Debian][] Linux, ix86 and x86\_64
 * Mac OS X 10.7 “Lion”, x86\_64

Other Linux distributions, eg, [Ubuntu][] and [Fedora][], should work if
sufficiently recent.

Other platforms for which the [Android SDK][] is available, such as Microsoft
Windows, might work (eg, using [Cygwin][]), but are not tested or supported.

Basic concepts
--------------

The [Serval Mesh][] app is composed of two parts:

 * “Batphone” is the Android user interface code, written in Java with XML
   resource files, compiled using the [Android SDK][].  The Batphone source
   code is kept in the [batphone][] repository on GitHub.

 * [Serval DNA][] is the core networking component, written in C, compiled
   using the [Android NDK][].  The [batphone][] repository embeds
   [serval-dna][] as a [Git submodule][].

Git repositories
----------------

All Serval Mesh development is done in a [Git clone][] of the [batphone][] and
[serval-dna][] source code repositories.  A “clone” is a local copy on your own
computer that you can modify however you wish without affecting any other
developers and without needing any permission.

You cannot push changes directly to these repositories unless you are a member
of the Serval Project development team.

The recommended way to contribute your modifications to the [batphone][] or
[serval-dna][] source code is to store your modifications on GitHub while you
work on them, and when they are ready, submit them to the Serval Project as a
[pull request][].

To do this, first make a [GitHub fork][] of *both repositories*, then make
local clones of those forked copies.

To fork on GitHub, use the GitHub web interface.

To clone from GitHub, first choose [which remote URL to use][].  The
recommended scheme is HTTPS, because it will pass through most firewalls.  An
SSH remote URL works just as well as HTTPS, as long as you can access the SSH
port (443) on github.com.  You can avoid the need to enter a password every
time you access GitHub by [adding your public key to your GitHub account][].  A
third option is [SSH over the HTTPS port][].

The following example shows how to create a clone in the current working
directory using the HTTPS URL:

    $ git clone -q https://github.com/YourGitHubAccountName/batphone.git
    $ cd batphone
    $ git submodule init
    Submodule 'jni/serval-dna' (https://github.com/YourGitHubAccountName/serval-dna.git) registered for path 'jni/serval-dna'
    $ git submodule -q update
    $

If all goes well, you will now have a full copy of the Serval Mesh source code
on your computer, with the “master” branch checked out.

To upload your changes to GitHub, [push to your GitHub fork][].

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

-----
**Copyright 2014 Serval Project Inc.**  
![CC-BY-4.0](./cc-by-4.0.png)
This document is available under the [Creative Commons Attribution 4.0 International licence][CC BY 4.0].


[Serval Project]: http://www.servalproject.org/
[README]: ./README.md
[Serval Mesh]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servalmesh:
[Serval DNA]: http://developer.servalproject.org/dokuwiki/doku.php?id=content:servaldna:
[batphone]: http://github.com/servalproject/batphone/
[Git clone]: http://git-scm.com/book/en/v2/Git-Basics-Getting-a-Git-Repository#Cloning-an-Existing-Repository
[Git submodules]: http://git-scm.com/book/en/v2/Git-Tools-Submodules
[GitHub fork]: https://help.github.com/articles/fork-a-repo/
[Git remote]: http://git-scm.com/book/en/v2/Git-Basics-Working-with-Remotes
[Git push]: http://git-scm.com/docs/git-push
[Git fetch]: http://git-scm.com/docs/git-fetch
[Git pull]: http://git-scm.com/docs/git-pull
[which remote URL to use]: https://help.github.com/articles/which-remote-url-should-i-use/
[adding your public key to your GitHub account]: https://help.github.com/articles/generating-ssh-keys/
[SSH over the HTTPS port]: https://help.github.com/articles/using-ssh-over-the-https-port/
[push to your GitHub fork]: https://help.github.com/articles/pushing-to-a-remote/
[configure your clone with a remote that points to the original repositories]: https://help.github.com/articles/configuring-a-remote-for-a-fork/
[Android SDK]: http://developer.android.com/sdk/index.html
[Android NDK]: http://developer.android.com/sdk/ndk/index.html
[Bourne shell]: http://en.wikipedia.org/wiki/Bourne_shell
[Eclipse]: http://developer.android.com/sdk/installing/installing-adt.html
[Debian]: http://www.debian.org/
[Ubuntu]: http://www.ubuntu.com/
[Fedora]: http://fedoraproject.org/
[Cygwin]: http://www.cygwin.com/
[CC BY 4.0]: ./LICENSE-DOCUMENTATION.md
