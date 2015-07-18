OVERVIEW =======================================================================

This document describes the entire process of obtaining, installing,
configuring, and running the Avail development workbench. Depending on how you
obtained Avail, whether it is a first time installation, and what utilities are 
already installed on your system, you may be able to skip some of the sections 
of this document.


BEFORE OBTAINING AVAIL =========================================================

In order to obtain Avail, you will need to make sure that you have obtained and
installed the prerequisite software:

1) Git: This is the version control software used by the Avail team to develop
and deliver Avail. The Avail team can say with certainty that version 1.7.10.2
will work, but you will probably want to go with something later unless you
already have Git installed.

To see if you have Git installed, try this:

	$ git version

If Git is installed and available on your path, then it will respond with a
version string like:

	git version 1.7.10.2 (Apple Git-33)

The latest version of Git can be obtained at:

	http://git-scm.com/

Please follow any installation directions provided by the Git website or
included with the Git product.

The main command is called "git". Please make sure that "git" is available on
your path.


OBTAINING AVAIL ================================================================

The preferred method of acquiring Avail is from The Avail Foundation's official
public repository. This repository is located at:

	https://github.com/AvailLang/Avail.git

Choose the directory where you would like the local copy of the Avail repository 
to reside (the path to which is hereafter denoted as $PROJ). Clone the official 
public repository into it like this:

	$ git clone https://github.com/AvailLang/Avail.git $PROJ

Even if you are planning to install Avail for system-wide usage, you will
probably want to choose a non-system directory to house the local copy of the
Avail repository.


BEFORE INSTALLING ==============================================================

Before installing Avail, you will need to make sure that you have obtained and
installed the prerequisite software:

1) Java: You will need version 1.7, or later, of the Java Development Kit (JDK)
Standard Edition (SE). Many modern systems already have some version of the JDK,
so you should check your JDK version before obtaining and installing it from
Oracle (or some other vendor). You can do so like this:

	$ javac -version

And hopefully you get back something like this:

	javac 1.7.0_55

Otherwise, the latest version of the JDK can be obtained at:

	http://www.oracle.com/technetwork/java/javase/downloads/index.html

Please follow any installation directions provided by the Oracle website or
included with the JDK. Be warned that the Java installer may offer to install
sponsored software from third parties (i.e. malware) or change settings on your
computer to favor their sponsors. You are encouraged to read the install screens
carefully, and to uncheck any of the sponsor options which are checked by
default.

The commands of interest are "java" and "javac". Please make sure that these are
on your path.

2) Apache Ant: This is the build software used to compile and install Avail. You
will need version 1.8.2 or later. Most modern Unix systems already have some
version of Ant installed, but, if you are a Windows user, then you will
definitely need to download it yourself if you have not previously done so. You
can check to see if ant is installed and has the correct version like this:

	$ ant -version

And maybe get back something like this:

	Apache Ant(TM) version 1.8.2 compiled on October 14 2011

If you want to download a recent binary distribution of Ant, then go here:

	https://ant.apache.org/bindownload.cgi

Or you can obtain a source distribution here:

	http://ant.apache.org/srcdownload.cgi

Please follow any installation directions provided by the Apache Ant website or
included with the distribution of your choice.

The main command is "ant". Please make sure that "ant" is on your path.


BUILDING =======================================================================

You will need to compile Avail using the provided build script, "build.xml". To
build Avail:

	$ cd $PROJ/Avail
	$ ant

(When no arguments are provided, Ant will search the current directory for a 
"build.xml" and, if one was found, run its default target. If you are invoking
Ant from a different directory than $PROJ/Avail, your invocation should read 
"ant -f $PROJ/Avail/build.xml".)

You should see output similar to this:

	Buildfile: $PROJ/Avail/build.xml

	build-sources:
		[mkdir] Created dir: $PROJ/Avail/bin
		[javac] Compiling 1022 source files to $PROJ/Avail/bin

	generate-build-time:

	generate-primitives-list:

	avail-vm:
		[jar] Building jar: $PROJ/Avail/distro/lib/Avail.jar

	avail-dev:
		[jar] Building jar: $PROJ/Avail/distro/lib/AvailDev.jar

	BUILD SUCCESSFUL
	Total time: 14 seconds

If your transcript ends with "BUILD SUCCESSFUL", then your build is ready for
installation.


INSTALLATION ===================================================================

Once you have successfully built the Avail project, you can then install it for
either user-specific or system-wide usage. The installation directory
(hereinafter, $INSTALL) is specified by an Ant property, "path.install". You can
set this property by providing the -Dpath.install=... option to Ant.

You can install Avail into a specific installation directory like this:

	$ cd $PROJ/Avail
	$ ant -Dpath.install=$INSTALL install

If you do not specify -Dpath.install=... explicitly, then $INSTALL defaults to
/usr/local/avail on Unix and C:\Program Files\Avail on Windows. Note that these
are system directories, and in order to install to such a location you may need
to escalate your privileges by using a tool like "sudo" or logging in with an
administrative account.

Your transcript should look similar to this:

	Buildfile: $PROJ/Avail/build.xml

	build-sources:

	generate-build-time:

	generate-primitives-list:

	avail-vm:
		[jar] Building jar: $PROJ/Avail/distro/lib/Avail.jar

	avail-dev:
		[jar] Building jar: $PROJ/Avail/distro/lib/AvailDev.jar

	install:
		[copy] Copying 229 files to $INSTALL
		[echo] 
		[echo] ATTENTION =======================================================
		[echo] Be sure to set AVAIL_HOME to:
		[echo] 
		[echo]   $INSTALL
		[echo] 
		[echo] And update your path to include:
		[echo] 
		[echo]   $INSTALL/bin
		[echo] 
		[echo] For example, a user of bash might include something like the
		[echo] following in the appropriate shell config file:
		[echo] 
		[echo]   export AVAIL_HOME=$INSTALL
		[echo]   export PATH=$PATH:$INSTALL/bin
		[echo] 
		[echo] Once your path has been updated, from any directory you can
		[echo] launch the Avail workbench like this:
		[echo] 
		[echo]   avail-dev
		[echo] 
		[echo] To develop Avail code, you will also need to set AVAIL_ROOTS to a
		[echo] valid module root path. If AVAIL_ROOTS is not set, then avail-dev
		[echo] temporarily sets it to:
		[echo] 
		[echo]   avail=$HOME/.avail/repos/avail.repo,$INSTALL/src/avail;\
		[echo]   examples=$HOME/.avail/repos/examples.repo,$INSTALL/src/examples
		[echo] 
		[echo] This is convenient for experimenting with Avail, but must be
		[echo] extended with custom module roots as you develop your own 
		[echo] modules.
		[echo] =================================================================
		[echo] 

	BUILD SUCCESSFUL
	Total time: 3 seconds

If your transcript ends with "BUILD SUCCESSFUL", then the installation has
completed. The transcript contains information about how to configure your
environment for Avail development. Please be sure to follow these instructions
(reiterated in more detail below).


AFTER INSTALLING ==============================================================

In order to develop Avail libraries and programs, you will need to configure
your environment appropriately. On Unix, this is best accomplished by updating
your shell configuration file. On Windows, you can use the Control Panel to
adjust the environment variables.

The following steps should be taken to prepare your environment for Avail
development:

1) Set the AVAIL_HOME environment variable to $INSTALL. The Avail workbench,
"avail-dev", uses this information to locate Avail, and will not run correctly
without AVAIL_HOME being set.

2) Update your path to include $INSTALL/bin. This is where "avail-dev" is
located. This enables your shell to find the command without the user needing to 
prefix its exact path or change the working directory.

3) Set the AVAIL_ROOTS environment variable to a valid module roots path so
that Avail can find its modules.

A "module roots path" is a specification of locations that contain Avail code,
in either binary or source form. A module roots path comprises several
"module root specifications" separated by semicolons (";"). (On Unix systems,
you will need to quote each semicolon, in order to hide it from the shell's
command parser.)

Each module root specification comprises a "module root name", which is just a
unique logical identifier, and a module root path. A module root name usually 
denotes a vendor or a project, and does not need to have any relationship to the
file system. By convention, the module root name of the Avail standard library
is "avail", and the official collection of examples supported by The Avail
Foundation is called "examples". The Avail Foundation advises Avail developers
to follow a reverse DNS name convention, like "com.acme.super-neato-project", to
prevent collision between module root names among several vendors and projects.

A "module root path" is actually a conjunction of a binary repository path and a
source path, respectively, separated by a comma (","). A "binary repository
path" locates a binary file that contains the compiled forms of Avail source
modules. A "source path" locates a directory containing Avail source modules.
The same binary repository path may be used for many source paths. If a binary
repository path does not refer to an existing file, then "avail-dev" will create
and populate this file as needed. A source path must always refer to an existing
directory that is readable by the user running "avail-dev".

If AVAIL_ROOTS is not defined, then "avail-dev" will use the following module 
roots path by default:

	avail=$HOME/.avail/repos/avail.repo,$INSTALL/src/avail;
	examples=$HOME/.avail/repos/examples.repo,$INSTALL/src/examples

(There is not really a line feed after the semicolon; the line feed was inserted
as a formatting conceit only.)

This default is suitable for interacting with the Avail system as shipped by The
Avail Foundation, but not for developing your own Avail modules. To develop your
own Avail modules, your AVAIL_ROOTS environment variable must at least include a
module root specification for the Avail standard library, and it must also
include module root specifications for your own Avail projects.

For more information on this topic, please visit this webpage:

	http://www.availlang.org/about-avail/documentation/modules/module-discovery.html

RUNNING AVAIL ==================================================================

To run an Avail program, launch the Avail development workbench:

	$ avail-dev

This will open a graphical user interface (GUI) whose window bar is titled
"Avail Workbench". Using the workbench is beyond the scope of this document.
For information about using the workbench to load Avail modules and issue
Avail commands, please visit:

	http://www.availlang.org/about-avail/learn/tutorials/workbench.html


DEVELOPING AVAIL ===============================================================

At the time of writing, there is not an integrated development environment (IDE)
that specifically targets Avail. To develop Avail, The Avail Foundation
recommends that you obtain a full-featured programmer's text editor that has
good support for Unicode and user templates.

For those who wish to contribute source code to Avail itself, please use the
latest version of Eclipse and the "eclipse-luna.epf" preferences file and the
"avail-templates.xml" code templates files provided in $PROJ/Avail. This
practice assists the Avail team in maintaining consistency in coding,
documentation, and formatting practices. You are also strive to imitate the
existing stylistic conventions, just as you would for any other established code
base.


STAYING CURRENT ================================================================

To keep up-to-date with the latest Avail development, you will want to refresh
the Avail project directory from Git every once in a while:

	$ cd $PROJ
	$ git pull

Then build and install:

	$ ant
	$ ant install

For more elaborate instructions, please refer back to the BUILDING and
INSTALLATION sections of this document.

Following a rebuild, if you should encounter any problems while running
"avail-dev", especially an unhandled java.lang.NoClassDefinitionError, then
please try a clean rebuild before reporting the problem:

	$ ant clean
	$ ant
	$ ant install

Be sure to visit the official Avail website frequently for updates and news:

	http://www.availlang.org


UNINSTALLATION =================================================================

Once you have installed Avail, should you decide to uninstall it, you can do so
with a command like:

	$ cd $PROJ
	$ ant uninstall

Even if you do not wish to discontinue use of Avail, you may find that the
installation directory structure accumulates craft from ancient versions as a
consequence of staying current and reinstalling. This is because, at the time of
writing, the build process makes no effort to remove files that are not part of
the current version of the software. Though this is undoubtedly inconvenient for
general usage, it is a nicety for users who may be installing additional
resources in this directory structure — even though this is not a recommended
practice. Should you wish to reclaim this storage and/or tidy this directory
structure, you may wish to uninstall Avail preceding a rebuild:

	$ cd $PROJ
	$ git pull
	$ ant uninstall
	$ ant
	$ ant install


REPORTING PROBLEMS =============================================================

To report a problem with the Avail virtual machine or standard library, please
take the following steps:

1) Please verify, to the best of your ability, that the problem does not result
from a bug in your own code. We all write buggy code, including the developers
at The Avail Foundation, so please make sure that the problem is ours and not
yours – so that we can concentrate our efforts on fixing our own bugs!

2) Once you are certain that the bug's provenance makes it our problem, please
verify that the bug has not already been reported. You can do this by searching
for the bug using our issue tracking system:

	https://trac.availlang.org/report

If you do locate the bug, then please feel free to add any information not
already reported in the history of the existing issue. If this bug is
mission-critical for you, then please let us know this so that we can assess our
current priorities and adjust them accordingly.

3) If you could not locate the bug in our database, then please create a new
ticket in our issue system:

	https://trac.availlang.org/newticket

Please try to find the minimal test case that reproduces the problem, and attach
any relevant source files, resources, and screenshots to the new issue.

4) You are certainly welcome to stop at #3; at this point, you have definitely
done your duty as a responsible and considerate user of open-source software. If
you are feeling especially ambitious, however, you may wish to tackle the bug
yourself! This may be a trivial matter or a serious ordeal, depending on the
nature of the bug, and providing advice to the ambitious is beyond the scope of
this humble document. If you do decide to attempt a resolution yourself, then
please include a statement of your intentions in the issue report. Should your
efforts prove successful, then please use Git to create a patch file:

	$ git diff > $PATCH

Where $PATCH denotes the name of the patch file. Please attach this patch file
to the relevant issue. Once we have had an opportunity to review the patch for
vulnerabilities and stylistic issues, then we will apply your patch to the
appropriate project branches in Git.

If you start but give up before achieving a resolution, then simply update the
issue to let us know that you are no longer working on the problem.


================================================================================

Thank you for using Avail!

                                               Todd L Smith <todd@availlang.org>
                                       and Leslie Schultz <leslie@availlang.org>
                                               on behalf of The Avail Foundation
