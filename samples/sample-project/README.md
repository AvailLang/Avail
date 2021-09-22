SAMPLE PROJECT OVERVIEW
--------------------------------------------------------------------------------
This represents a project that uses Avail in a JVM project. This project will be
updated over time to reflect changes to Avail as well as to further 
demonstrate how Avail can be used in a JVM project.

SETUP
--------------------------------------------------------------------------------
The sample project uses Gradle to set up the project environment. It 
utilizes the `buildSrc` directory for managing state used in `build.gradle.
kts`.

It uses the [Avail Plugin](/avail-plugin/README.md) to set up Avail:

```kotlin
 id("avail.avail-plugin") version Versions.availStripeVersion
```

