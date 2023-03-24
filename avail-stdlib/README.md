AVAIL STANDARD LIBRARY
--------------------------------------------------------------------------------
[![Maven Central](https://img.shields.io/badge/maven--central-v2.0.0.alpha21--1.6.1.alpha10-0f824e)](https://central.sonatype.com/namespace/org.availlang)

The Avail Standard Library is the general purpose programming library for 
writing Avail. An Avail runtime includes Avail code through **Module Roots**.
By including the Avail standard library as a module root in your project, you
can use the standard library functions by including `Avail` in either the `Uses`
section or `Extends` section of the header of an Avail module file. This can be
seen in the Avail example program, [Sudoku.avail](../distro/src/examples/Sudoku.avail).

At the time of writing this, the Avail Standard Library can be included as a 
module root either by pointing to it as
    * the top level directory where the Avail library exists in pure Avail modules
    * a jar file that contains the Avail standard library.

See 
