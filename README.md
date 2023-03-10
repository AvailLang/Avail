OVERVIEW
--------------------------------------------------------------------------------
[![Maven Central](https://img.shields.io/badge/maven--central-v2.0.0.alpha20-0f824e)](https://search.maven.org/artifact/org.availlang/avail)

Avail is a multi-paradigmatic general purpose programming language whose feature
set emphasizes support for constructing domain-specific languages (DSLs). Anvil
is the integrated development environment (IDE) of Avail, and it is bundled
directly with the language for convenience. Due to the advanced lexical and
syntactical features of Avail, popular IDEs such as IntelliJ and Eclipse are
unsuitable for use, so you should use Anvil for best ergonomics.

Avail is an open-source project that comprises a 
[language virtual machine](https://en.wikipedia.org/wiki/Virtual_machine), a 
standard library, and an IDE. All three are released under the
[3-clause BSD license](https://en.wikipedia.org/wiki/BSD_licenses#3-clause_license_.28.22Revised_BSD_License.22.2C_.22New_BSD_License.22.2C_or_.22Modified_BSD_License.22.29).

 * [Before Getting Started](#before-getting-started)
 * [Before Obtaining Avail](#before-obtaining-avail)
 * [Obtaining Avail](#obtaining-avail)
 * [Before Installing](#before-installing)
 * [Building Anvil](#building)
 * [Running Anvil](#running)

This document describes the entire process of obtaining, installing,
configuring, and running Anvil, for the purpose of developing software in Avail.

BEFORE GETTING STARTED
--------------------------------------------------------------------------------

If you merely wish to embed Avail into a larger JVM project, for use as an
extension language, you can import a prebuilt version of Avail as a dependency.
See the
[Avail Gradle Plugin](https://github.com/AvailLang/gradle-plugin) for more
details on how to accomplish this. You can also refer to our
[sample project](https://github.com/AvailLang/samples) to see how Avail can be
incorporated into a JVM project.

BEFORE OBTAINING AVAIL
--------------------------------------------------------------------------------

In order to obtain Avail you will need to make sure that you have obtained and
installed the prerequisite software:

1) **Git**: This is the version control software used by the Avail team to
develop and deliver Avail.

To see if you have Git installed, try this:

	$ git version

If Git is installed and available on your path, then it will respond with a
version string like:

	git version 2.39.2

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

1) **Java**: You will need version 17 of the Java Development Kit (JDK) Standard
Edition (SE), _NOT_ an earlier version. Many modern systems already have some
version of the JDK, so you should check your JDK version before obtaining and
installing it from OpenJDK (or some other vendor). You can do so like this:

	$ javac -version

And hopefully you get back something like this:

	javac 17.0.2

Otherwise, the latest version of the JDK can be obtained at:

https://jdk.java.net/

Please follow any installation directions provided by the website or included
with the JDK, especially with respect to setting up the appropriate environment
variables for command-line use.

The commands of interest are "java" and "javac". Please make sure that these are
on your path.


BUILDING
--------------------------------------------------------------------------------

You will need to compile Anvil using the provided Gradle wrapper. To build Anvil
on a Unix-based system, such as Linux, macOS, Minimalist GNU for Windows
(MinGW), or Windows Subsystem for Linux (WSL):

	$ cd $PROJ
	$ ./gradlew :avail:anvil

To build Anvil on vanilla Windows:

    $ cd $PROJ
    $ .\gradlew.bat :avail:anvil

You should see output similar to this:

	> Configure project :avail
	Java version for tests: 17
	AvailRoot(49): /Users/avail/projects/avail/distro/src/avail
	AvailRoot(57): /Users/avail/projects/avail/distro/src/builder-tests
	AvailRoot(52): /Users/avail/projects/avail/distro/src/examples
	AvailRoot(51): /Users/avail/projects/avail/distro/src/website
	
	> Configure project :avail-stdlib
	
	BUILD SUCCESSFUL in 9s
	12 actionable tasks: 5 executed, 1 from cache, 6 up-to-date

Toward the end of the transcript you should find `BUILD SUCCESSFUL`, which
indicates that your build is ready to use. You can find it here:

	$PROJ/anvil.jar

This JAR file contains the whole of Anvil, which bundles the Avail compiler
toolchain and runtime within.

Naturally, you can move `anvil.jar` to another location if you feel so inclined.
You will need to adjust the paths mentioned below to account for relocation if
you have done this.


RUNNING
--------------------------------------------------------------------------------

You can launch Anvil using the provided Gradle wrapper. To launch Anvil
on a Unix-based system:

	$ cd $PROJ
	$ ./gradlew :avail:run

To launch Anvil on vanilla Windows:

	$ cd $PROJ
	$ .\gradlew.bat :avail:run

If the default JDK on your system is version 17 or later, you may be able to
double click this JAR file to launch Anvil.

If all else fails, you can also launch Anvil by invoking `java` directly. On
a Unix-based system:

	$ java -jar $PROJ/anvil.jar

On vanilla Windows:

	$ java.exe -jar $PROJ/anvil.jar

--------------------------------------------------------------------------------

Thank you for using Avail!

                               on behalf of Avail                                                              
                      Richard Arriaga <rich@availlang.org>
                      Mark van Gulik <todd@availlang.org>                       
                     Leslie Schultz <leslie@availlang.org>
                       Todd L Smith <todd@availlang.org>
