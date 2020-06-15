OVERVIEW
--------------------------------------------------------------------------------

This document describes the entire process of obtaining, installing,
configuring, and running the Avail development workbench. Depending on how you
obtained Avail, whether it is a first time installation, and what utilities are
already installed on your system, you may be able to skip some of the sections
of this document.


BEFORE OBTAINING AVAIL
--------------------------------------------------------------------------------

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


OBTAINING AVAIL
--------------------------------------------------------------------------------

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


BEFORE INSTALLING
--------------------------------------------------------------------------------

Before installing Avail, you will need to make sure that you have obtained and
installed the prerequisite software:

1) Java: You will need version 1.8 of the Java Development Kit (JDK) Standard
Edition (SE), NOT an earlier or later version. Many modern systems already
have some version of the JDK, so you should check your JDK version before
obtaining and installing it from Oracle (or some other vendor). You can do so
like this:

	$ javac -version

And hopefully you get back something like this:

	javac 1.8.0_152

Otherwise, the latest version of the JDK can be obtained at:

	http://www.oracle.com/technetwork/java/javase/downloads/index.html

Please follow any installation directions provided by the Oracle website or
included with the JDK. Be warned that the Java installer may offer to install
sponsored software from third parties (i.e., malware) or change settings on your
computer to favor their sponsors. You are encouraged to read the install screens
carefully, and to uncheck any of the sponsor options which are checked by
default.

The commands of interest are "java" and "javac". Please make sure that these are
on your path.


BUILDING
--------------------------------------------------------------------------------

You will need to compile Avail using the provided Gradle wrapper. To build Avail
on a Unix-based system, such as Linux, Mac OS X, Minimalist GNU for Windows
(MinGW), or Windows Subsystem for Linux (WSL):

	$ cd $PROJ
	$ ./gradlew build

To build Avail on vanilla Windows:

    $ cd $PROJ
    $ .\gradlew.bat build

You should see output similar to this:

    ATTENTION ==========================================================
    Be sure to set AVAIL_HOME to:
    
            $PROJ/distro
    
    And update your path to include:
    
            $PROJ/distro/bin
    
    For example, a user of bash might include something like the
    following in the appropriate shell config file:
    
            export AVAIL_HOME=$PROJ/distro
            export PATH=$PATH:$PROJ/distro/bin
    
    Once your path has been updated, from any directory you can launch
    the Avail workbench like this:
    
            avail-dev
    
    Or the Avail server like this:
    
            avail-server
    
    (The server is currently hard-wired to run on port 40000. This will
    change at some point.)
    
    To develop Avail code, you will also need to set AVAIL_ROOTS to a
    valid module root path. If AVAIL_ROOTS is not set, then avail-dev
    temporarily sets it to:
    
            avail=$HOME/.avail/repos/avail.repo,$PROJ/distro/src/avail;\
            examples=$HOME/.avail/repos/examples.repo,$PROJ/distro/src/examples
    
    This is convenient for experimenting with Avail, but must be
    extended with custom module roots as you develop your own modules.
    ====================================================================

    BUILD SUCCESSFUL in 23s
    19 actionable tasks: 19 executed

If your transcript ends with "BUILD SUCCESSFUL", then your build is ready for
use. You may, of course, move the "distro" subdirectory to another user-specific
or even system-wide location, and then update your AVAIL_HOME and PATH variables
accordingly. Hereinafter we use $INSTALL to refer to the final destination of
the "distro" directory.


AFTER BUILDING
--------------------------------------------------------------------------------

In order to develop Avail libraries and programs, you will need to configure
your environment appropriately. On Unix systems, this is best accomplished by
updating your shell configuration file. On Windows, you can use the Control
Panel to adjust the environment variables.

The following steps should be taken to prepare your environment for Avail
development:

1) Set the AVAIL_HOME environment variable to $INSTALL. The Avail workbench,
"avail-dev" on Unix and "avail-dev.bat" on vanilla Windows, uses this
information to locate Avail, and will not run correctly without AVAIL_HOME being
set.  

2) Update your path to include $INSTALL/bin. This is where "avail-dev" and
"avail-dev.bat" are located. This enables your shell to find the command without
the user needing to prefix its exact path or change the working directory.

3) Set the AVAIL_ROOTS environment variable to a valid module roots path so
that Avail can find its modules, or …

4) … alternatively, you may start the Avail workbench without specifying
AVAIL_HOME and use the "Preferences…" menu item to enter the module roots path,
one module root at a time. Upon exiting the Avail workbench, this information is
persisted for future sessions.

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

If AVAIL_ROOTS is not defined and the Avail workbench has not been used to
define module roots, then "avail-dev" will use the following module roots path
by default:

	avail=$HOME/.avail/repos/avail.repo,$INSTALL/src/avail;
	examples=$HOME/.avail/repos/examples.repo,$INSTALL/src/examples

(There is not really a line feed after the semicolon; the line feed was inserted
as a formatting conceit only.)

This default is suitable for interacting with the Avail system as shipped by The
Avail Foundation, but not for developing your own Avail modules. To develop your
own Avail modules, your AVAIL_ROOTS environment variable must at least include a
module root specification for the Avail standard library, and it must also
include module root specifications for your own Avail projects.

Note that "avail-dev.bat" makes no effort to configure the environment. You must
preconfigure the environment yourself, customize "avail-dev.bat" to suit your
own needs, or use the Avail workbench to establish the module roots. (A lack of
Windows expertise on the part of The Avail Foundation is responsible for this
minimality; hopefully "avail-dev.bat" will be richer in a future release.)

For more information on this topic, please visit this webpage:

	http://www.availlang.org/about-avail/documentation/modules/module-discovery.html

RUNNING AVAIL
--------------------------------------------------------------------------------

To run an Avail program, launch the Avail development workbench. On Unix:

	$ avail-dev

On Windows:

    $ avail-dev.bat

This will open a graphical user interface (GUI) whose window bar is titled
"Avail Workbench". Using the workbench is beyond the scope of this document.
For information about using the workbench to load Avail modules and issue
Avail commands, please visit:

	http://www.availlang.org/about-avail/learn/tutorials/workbench.html


DEVELOPING AVAIL
--------------------------------------------------------------------------------

At the time of writing, there is not an integrated development environment (IDE)
that specifically targets Avail. To develop Avail, The Avail Foundation
recommends that you obtain a full-featured programmer's text editor that has
good support for Unicode and user templates.

For those who wish to contribute source code to Avail itself, please use the
latest version of IntelliJ IDEA. This practice assists the Avail team in
maintaining consistency in coding, documentation, and formatting practices. You
should also strive to imitate the existing stylistic conventions, just as you
would for any other established code base.


STAYING CURRENT
--------------------------------------------------------------------------------

To keep up-to-date with the latest Avail development, you will want to refresh
the Avail project directory from Git every once in a while:

	$ cd $PROJ
	$ git pull

On Unix, then build:

	$ ./gradlew build

On vanilla Windows, then build:

    $ .\gradlew.bat build

For more elaborate instructions, please refer back to the BUILDING and
INSTALLATION sections of this document.

Following a rebuild, if you should encounter any problems while running
"avail-dev", especially an unhandled java.lang.NoClassDefinitionError, then
please try a clean rebuild before reporting the problem.

On Unix:

	$ ./gradlew clean
	$ ./gradlew build
	
On vanilla Windows:

    $ .\gradlew.bat clean
    $ .\gradlew.bat build

Be sure to visit the official Avail website for updates and news:

	http://www.availlang.org

With some shame, I am forced to confess that the website does not undergo much
maintenance. Our active team has shrunk in recent years, and the website suffers
for it. Development of Avail itself is quite active, however, so GitHub might be
your best source of Avail news.

GENERATING DOCUMENTATION
--------------------------------------------------------------------------------
#### Software

- [Python 3](https://www.python.org/) / [pip 3](https://pip.pypa.io/en/stable/)
- [MkDocs](https://www.mkdocs.org/)
- [MkDocs Material](https://squidfunk.github.io/mkdocs-material/)

The following instructions assume that both Python 3 (`python3`) and Pip 3 (`pip3`) are installed. If you have not done this please install them before proceeding.

### Automated Setup
Simply run the command `gradle installDocsToolsUnix`. The following Gradle commands should now be available:

* _serveDocsUnix_: Can be run with `gradle serveDocsUnix` or from the provided convenience run configuration of the same name. 

### Manual Setup (_not recommended_)

If you feel automation is making the masses too lazy, the following enumerates all the manual steps for setting up documentation.

##### Scripts

Ensure the _scripts/docs.sh_ is executable. This can be done From the terminal run the following command:
```
chmod +x scripts/docs.sh
```

##### MkDocs

###### Mac OSX

It is recommended that you use Homebrew to install MkDocs. If you do not have Homebrew installed, follow the [installation instructions](https://docs.brew.sh/Installation). Once homebrew is installed, in the terminal simply run:
```
brew install mkdocs
```

###### Linux
Depending on your Linux distro, a package manager may make MkDocs available to you with an easy install. Using this path for installation is disrecommended as the versions of the binaries available through the OS package managers may be out of date.

It is recommeneded that the [manual instructions](https://www.mkdocs.org/#installation) using `pip`. Be sure to use `pip3` for installation of MkDocs. Doing this may prevent mkdocs from being available on the PATH. The command `mkdocs` will need to be available on the PATH for documentation to function. 

To see where mkdocs was installed (_the package directory_), use the command: `pip3 show mkdocs`. You'll see an output similar to this:
```
Name: mkdocs
Version: 1.1.2
Summary: Project documentation with Markdown.
Home-page: https://www.mkdocs.org
Author: Tom Christie
Author-email: tom@tomchristie.com
License: BSD
Location: /home/MY_COMPUTER/.local/lib/python3.6/site-packages
Requires: Jinja2, Markdown, livereload, lunr, PyYAML, click, tornado
```
##### MkDocs Material

The documentation site utilizes Material for MkDocs for its style theme. To install Material, follow their _[Getting Started](https://squidfunk.github.io/mkdocs-material/getting-started/)_ guide.

At the time of writing this README, Material could be installed using PIP.
```
pip3 install mkdocs-material
```

#### Generating Documentation

To generate the documentation, run the gradle task:
```
gradle dokka
```
or use the convenience run configuration, _`Avail [generateDocumentation]`_.

To clean the documentation (_delete it_), run the gradle task:
```
gradle cleanDocs
``` 
or use the convenience run configuration, _`Avail [cleanDocs]`_.

#### Serving Documentation
To make the documentation available through a web browser, run the gradle task:
```
gradle serveDocsUnix
```
or use the convenience run configuration, _`Avail [serveDocsUnix]`_.

Access the documentation from your web browser at [http://127.0.0.1:8000/]()

REPORTING PROBLEMS
--------------------------------------------------------------------------------

To report a problem with the Avail virtual machine or standard library, please
take the following steps:

1) Please verify, to the best of your ability, that the problem does not result
from a bug in your own code. We all write buggy code, including the developers
at The Avail Foundation, so please make sure that the problem is ours and not
yours – so that we can concentrate our efforts on fixing our own bugs!

2) Once you are certain that the bug's provenance makes it our problem, please
verify that the bug has not already been reported. You can do this by searching
for the bug using our issue tracking system:

	https://github.com/AvailLang/Avail/issues

If you do locate the bug, then please feel free to add any information not
already reported in the history of the existing issue. If this bug is
mission-critical for you, then please let us know this so that we can assess our
current priorities and adjust them accordingly.

3) If you could not locate the bug in our database, then please create a new
ticket in our issue system:

	https://github.com/AvailLang/Avail/issues/new

Please try to find the minimal test case that reproduces the problem, and attach
any relevant source files, resources, and screenshots to the new issue.

4) You are certainly welcome to stop at #3; at this point, you have definitely
done your duty as a responsible and considerate user of open-source software. If
you are feeling especially ambitious, however, you may wish to tackle the bug
yourself! This may be a trivial matter or a serious ordeal, depending on the
nature of the bug, and providing advice to the ambitious is beyond the scope of
this humble document. If you do decide to attempt a resolution yourself, then
please include a statement of your intentions in the issue report. Should your
efforts prove successful, then please create a new pull request for your fix.
If you start but give up before achieving a resolution, then simply update the
issue to let us know that you are no longer working on the problem.


================================================================================

Thank you for using Avail!

                                               Todd L Smith <todd@availlang.org>
                                           Leslie Schultz <leslie@availlang.org>
                                            Richard Arriaga <rich@availlang.org>
                                               on behalf of The Avail Foundation
