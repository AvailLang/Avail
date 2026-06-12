# Packaging the Anvil IDE

The Anvil IDE is packaged as a self-contained, double-clickable application
bundle by Gradle. The previous per-platform `jpackage` recipes (run by hand
against the shadow jar) are obsolete on every platform.

## Quick start

From inside `avail/`:

```shell
# Build the bundle
./gradlew packageApp

# Build the bundle and launch it
./gradlew packageAppAndRun
```

Or from the repo root:

```shell
avail/gradlew -p avail packageApp
avail/gradlew -p avail packageAppAndRun
```

The bundle is written to `avail/build/jpackage/Anvil.app`.

## What the task does

`packageApp` chains three steps, all driven by the project's Java toolchain
(currently JDK 26, Oracle vendor — auto-discovered by Gradle, downloaded via
the toolchain provisioner if missing):

1. `package` builds the fat `avail-anvil.jar` (via the Shadow plugin).
2. `jlinkAnvilRuntime` runs `jlink` to produce a slim, application-specific
   JDK image under `avail/build/anvil-runtime/`. The module set is fixed in
   `build.gradle.kts` and covers `java.base`, `java.desktop`, the management
   and crypto modules, `jdk.attach`, `jdk.jdwp.agent`, `jdk.localedata`,
   `jdk.zipfs`, and the rest of the modules Anvil actually loads at runtime.
   The image is roughly 70 MB.
3. `packageApp` invokes `jpackage --type app-image` against the staged jar
   and the jlinked runtime, producing `Anvil.app`. The bundle is roughly
   90 MB total, has the `AvailHammer.icns` icon, and embeds JVM options
   (`-ea`, `-Xmx6g`, `--enable-native-access=ALL-UNNAMED`, splash screen).

The main class is `avail.project.AvailProjectManagerRunner`; the bundle
identifier is `org.availlang.anvil`.

End users do **not** need a JDK installed — the bundled runtime is what runs
inside the `.app`.

## Architecture

`jpackage` does not produce universal Apple binaries. The launcher
(`Anvil.app/Contents/MacOS/Anvil`), the bundled `java` binary, and the
JDK's native libraries are all single-architecture and match the host's
architecture at build time:

- Build on x86_64 → x86_64 bundle (runs natively on Intel Macs; runs on
  Apple Silicon under Rosetta 2 with the usual translation overhead).
- Build on Apple Silicon → arm64 bundle (runs natively on Apple Silicon;
  will not run on Intel Macs).

To ship to both architectures, run `packageApp` on each host and distribute
the matching bundle, or build both and `lipo -create` the native binaries
together by hand.

## Platform scope

`packageApp` is currently macOS-only:

- The icon resource is `.icns`.
- The jpackage invocation passes `--mac-package-identifier` and
  `--mac-package-name`.
- The output is a `.app` bundle (Windows would produce `.exe`/`.msi` and
  Linux a `.deb`/`.rpm` directory layout).

Wiring up Windows (`.ico`/`.msi`) and Linux (`.png`/`.deb`/`.rpm`)
equivalents is a future addition; once added, they would live in the same
Gradle file so the Gradle task remains the only entry point on every
platform.
