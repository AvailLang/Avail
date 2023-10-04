OVERVIEW
--------------------------------------------------------------------------------
This repository contains sample projects that demonstrate how to use [Avail](https://github.com/AvailLang/Avail). 
Each project has its own README that explains what type of project it demonstrates.

## UNDER DEVELOPMENT
***NOTE: Avail development has focused on the toolchain, specifically the IDE. The state of each of the sample projects are indicated in each project's README.***

## Projects

### Sample Library Linking Project
[sample-library-linking-project](sample-library-linking-project) represents a 
project that creates a library written and Java that is dynamically linked and 
used in an Avail root. Any JAR can be linked in this way. Note, the JAR must be 
an uber-JAR, containing all dependencies, as Avail does not resolve and link any 
library dependencies not packaged in the JAR.

### Sample Primitives Project
[sample-primitives-project](sample-primitives-project) represents a project that 
creates Avail primitives in a JVM language, in this case, Kotlin, that is 
dynamically linked in Avail with Avail's primitive linking system. This adds new 
primitives to Avail that can be used in Avail code. This project will be updated 
over time to reflect changes to Avail as well as to further demonstrate how 
Avail can be used in a JVM project.
