Avail Artifact
--------------------------------------------------------------------------------
[![Maven Central](https://img.shields.io/badge/maven--central-v2.0.0.alpha22-0f824e)](https://search.maven.org/artifact/org.availlang/avail-artifact)

This is a standalone upstream library that supports Avail project definition and
Avail artifact creation for the Avail [Programming Language](https://github.
com/AvailLang/Avail). Avail needs a way to provide external tools knowledge of
the shape of Avail without having to have deep knowledge of every aspect of the 
Avail programming language. 

This provides external tools the minimum information to:

1. create Avail [projects](#project)
2. open Avail projects in a third-party IDE
3. package Avail [artifacts](#artifact)

# Project
Avail is an extremely powerful general purpose programming language that can 
be customized in a lot of ways. Because of the potential for high complexity, it
is important to impose a rigid project structure that can be known by both Avail
and external tools. This rigid structure makes it easier to manage Avail 
projects by making it predictable where project components can be found by both 
Avail and Avail's external tools. This has the added benefit that it is also
predictable in a singular way for programmers. 

## Avail Project
The root of an Avail project is the class, 
[AvailProject](src/main/kotlin/org/availlang/artifact/environment/project/AvailProject.kt).

An Avail project is described by an Avail project configuration file. This is
a JSON file that can be named anything, but it follows the JSON structure
of this `AvailProject.writeTo`.

The Avail project configuration file lives at the top level of the Avail
project; the project root. All project locations are relative to this
directory. A project also has a `.avail` directory. Inside of this directory
all of the project configuration files can be found. At the top level of the
`.avail/` directory can be found a directory that shares the same name as the
[AvailProject] configuration JSON file without the `.json` file extension.
Inside this directory can be found:

- The configuration file for the [AvailArtifactBuildPlan] which describes 
how to construct [AvailArtifact]s from this project.
- a file called `{name of config file}-local-state.json` that contains
window position location information about open Anvil windows. This should
not be added to git.
- `settings-local.json` file that provides settings information specific to
the local environment. This should not be added to git.
- `styles.json` file that contains the project-wide styles settings for the
project (covers all roots).
- `templates.json` file that contains the project wide templates (covers
all roots).
- a directory for each of the [AvailProjectRoot]s representing each of the
    Avail module roots. These directories contain:
  - `settings-local.json` file that contains local settings information
  specific to that module for the current local environment. This should
  not be checked in to git.
  - `styles.json` file that contains the root-wide styles settings for the 
  project (covers all roots).
  - `templates.json` file that contains the root-wide templates (covers
  all roots).

# Artifact
The Avail Artifact is a versioned package of an Avail library or application.

## Avail Artifact Types
At the time of writing this there are two types of Avail Artifacts:
 1. `LIBRARY` - Contains only Avail Roots that can be shared as libraries in 
 2. `APPLICATION` - Contains all the components needed to run an Avail application.

## Artifact Structure
![file-structure](readme/structure.jpg)

#### 1. avail-artifact-manifest.txt
The [Avail Artifact Manifest](src/main/kotlin/org/availlang/artifact/manifest/AvailArtifactManifest.kt) 
file describes the contents of an Avail artifact. It also contains project 
information that is used when the library is imported into another project. 
This file is a JSON file but is intentionally provided with `.txt` extension.

### 2. Avail-Sources
Contains all the Avail source files packaged in the artifact.

### 3. artifact-descriptor
The [artifact descriptor](src/main/kotlin/org/availlang/artifact/ArtifactDescriptor.kt)
contains a compact binary format that describes the artifact with:
1. The package type
2. Packaging version
3. Version of the artifact manifest file.

### 4. all_digests.txt
The digest listing all the source files inclued.

# Project
An Avail project is described as an [AvailProject](src/main/kotlin/org/availlang/artifact/environment/project/AvailProject.kt).
This is represented as a series of files found in the `.avail` directory of an
Avail project. These files are described in [Core Avail](https://github.com/AvailLang/Avail).
