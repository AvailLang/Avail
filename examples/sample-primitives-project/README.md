SAMPLE PRIMITIVE PROJECT OVERVIEW
--------------------------------------------------------------------------------

![Version](https://img.shields.io/badge/v2.0.0.alpha01-0f824e)

***NOTE: 2023.09.23 The current state of this example is: Expected to Work***

*This uses alpha version tools that are in flux. This README will be updated*
*to reflect accurate usage for the 2.0.0 production release of the Avail*
*toolchain. Once the tools are finalized, this sample will be updated to a*
*working state relative to the latest production release.*

This represents a project that creates Avail primitives in a JVM language, 
in this case, Kotlin, that is dynamically linked in Avail with Avail's 
primitive linking system. This adds new primitives to Avail that can be used in
Avail code. This project will be updated over time to reflect changes to 
Avail as well as to further demonstrate how Avail can be used in a JVM project.

SETUP
--------------------------------------------------------------------------------
The sample project uses Kotlin DSL Gradle to set up the project environment.

It uses the [Avail Gradle Plugin](../../gradle-plugin/README.md) to run Anvil:

```kotlin
id("org.availlang.avail-plugin") version "2.0.0.alpha20"
```

PRIMITIVE
--------------------------------------------------------------------------------

### Kotlin
The Primitive is defined in
[P_PlayAudioResource.kt](src/main/kotlin/org/availlang/sample/P_PlayAudioResource.kt).

All primitives must extend `avail.interpreter.Primitive`. It is necessary that 
all primitive classes are prefixed with `P_` in order for it to be recognized
by the Avail VM as a primitive when the jar is loaded by the Avail VM.

### Avail
The primitive is loaded in [Sounds.avail](roots/sounds-root/App.avail/Sounds.avail)

This is where the bindings are created to the native JVM functionality. To
dynamically link a JAR, use Avail's `"Link primitives:_"`. This is done inside
`Sounds.avail` with the statement:

```
Link primitives: "/sounds-root/App/sounds.jar";
```

This is linking the JAR file at the specified location directly inside the
`sounds-root` module root. In order for a Jar file to be linked by Avail, it
***must*** be inside the module root that is linking it.

The entry point `ribbit` is created in
[Frog Sound.avail](roots/sounds-root/App.avail/Frog Sound.avail)

RUNNING
--------------------------------------------------------------------------------
Use the Avail Gradle task, `anvil` in the task group, `avail` to launch the
Avail Project Manager. Select the "Open" button to open the sample project.
Select `sample-linking-primitives.json` at the root of the project to open the
project.

Double-click on the modules to build them. Run the entry point `ribbit` to play
the sound file:
![workbench](readme/workbench.jpg?raw=true)

