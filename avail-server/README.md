OVERVIEW
--------------------------------------------------------------------------------

This document describes the use and testing of the Avail Server. It presumes you
have already read through and followed the instructions from the **Avail
 README**.

The Avail Server is built in Kotlin, the underlying language in which Avail is
developed. The server interoperates with an `AvailRuntime`, giving it support
to directly interact with Avail. 

The purpose of the Avail Server is to run Avail programs. It establishes a
socketed connection to an `AvailRuntime` via an API by which programs can be
run. It can be set up to run Avail programs locally or over a network.

BUILDING
--------------------------------------------------------------------------------

Run the `avail-server` Gradle task, `jar`.

RUNNING AVAIL SERVER
--------------------------------------------------------------------------------

To run an Avail program, launch the Avail development workbench. On Unix:

	$ avail-server

On Windows:

    $ avail-server.bat

This will start the WebSocket server on `localhost` on port 40000
 `ws://localhost:40000/`.


DEVELOPING AVAIL SERVER
--------------------------------------------------------------------------------

_TODO disuss how the Avail server works, the different components, and how to
 add new messages to the server._


TESTING AVAIL SERVER
--------------------------------------------------------------------------------

The Avail Server provides integration tests that test the functionality of the
server as it will be used by a client. To test the Avail Server, run the Gradle 
`test` task.

The tests utilize a resource folder, `/src/test/resources`, to run tests. It 
expects the existence the following subdirectories:
  - `tests` - An Avail root that contains Avail source files used in testing
  - `tests/Some Tests.avail` - The main Avail module for the `tests` root.
  - `repos` - The Avail tests' repository for `test.repo`.

Tests are broken up into different classes to represent the part of the
functionality it is testing. These classes are:
  - `FileManagerTest` - Tests the underlying `FileManager` API used to interact
    with files. It creates and and ultimately cleans up files in the 
    `/src/test/resources` directory.
  - `BinaryAPITests`-  Tests the underlying `BinaryCommand` API. These are
     purely binary requests, Communicated on an `AvailServerChannel` that has
     the protocol state, `ProtocolState.BINARY`. 



REPORTING PROBLEMS
--------------------------------------------------------------------------------

To report a problem with the Avail Server, please take the following steps:

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

                                            Richard Arriaga <rich@availlang.org>
                                               on behalf of The Avail Foundation
