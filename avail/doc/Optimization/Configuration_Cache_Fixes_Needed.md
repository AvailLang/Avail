# Configuration Cache Compatibility Fixes

The Gradle configuration cache requires that build scripts avoid capturing references to mutable Gradle objects like `Project`, `Gradle`, or script objects. Here are the specific issues and fixes needed in `build.gradle.kts`:

## Issue 1: Project Extensions Access in Task Configuration (Line 207)

### Current Code (INCOMPATIBLE):
```kotlin
withType<Test> {
    val toolChains =
        project.extensions.getByType(JavaToolchainService::class)
    javaLauncher =
        toolChains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(jvmTarget))
        }
    // ...
}
```

### Problem:
Directly accessing `project.extensions` inside task configuration captures the `Project` object.

### Fix:
Use the receiver context instead of explicit `project` reference:

```kotlin
withType<Test> {
    val toolChains =
        extensions.getByType(JavaToolchainService::class)
    javaLauncher =
        toolChains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(jvmTarget))
        }
    // ...
}
```

**Rationale:** Inside `withType<Test>`, the receiver is already the `Test` task, which has access to `extensions` without going through `project`.

---

## Issue 2: rootProject.projectDir in Task Configuration (Line 550)

### Current Code (INCOMPATIBLE):
```kotlin
fun Project.scrubReleases (task: Delete)
{
    task.run {
        // distro/lib
        delete(fileTree("${rootProject.projectDir}/${distroLib}")
            .matching { include("**/*.jar") })
        // And the publication staging directory, build/libs
        delete(fileTree("$projectDir/build/libs").matching {
            include("**/*.jar")
        })
    }
}
```

### Problem:
Accessing `rootProject.projectDir` and `projectDir` (which is `project.projectDir`) directly captures the Project objects.

### Fix:
Use `layout.projectDirectory` and `rootProject.layout.projectDirectory`:

```kotlin
fun Project.scrubReleases (task: Delete)
{
    task.run {
        // distro/lib
        delete(fileTree(rootProject.layout.projectDirectory.dir(distroLib))
            .matching { include("**/*.jar") })
        // And the publication staging directory, build/libs
        delete(fileTree(layout.buildDirectory.dir("libs"))
            .matching { include("**/*.jar") })
    }
}
```

**Rationale:** `layout.projectDirectory` returns a `Directory` provider that is serializable, unlike `projectDir` which returns a `File` and requires accessing the `Project`.

---

## Issue 3: rootProject.projectDir in Extension Function (Line 619)

### Current Code (INCOMPATIBLE):
```kotlin
fun Project.availRoot(name: String): AvailRoot
{
    val rootURI = systemPath("${rootProject.projectDir}", distroSrc, name)
    println("AvailRoot(${rootURI.length}): $rootURI")

    return AvailRoot(
        name,
        File(rootURI).toURI())
}
```

### Problem:
This function is called during configuration phase and directly accesses `rootProject.projectDir`.

### Fix Option 1 (Preferred):
Capture the root directory path at the top level before any functions:

```kotlin
// At top level of build script
val rootDirPath: String = rootProject.layout.projectDirectory.asFile.absolutePath

fun Project.availRoot(name: String): AvailRoot
{
    val rootURI = systemPath(rootDirPath, distroSrc, name)
    println("AvailRoot(${rootURI.length}): $rootURI")

    return AvailRoot(
        name,
        File(rootURI).toURI())
}
```

### Fix Option 2 (Alternative):
Pass the root directory as a parameter instead of accessing it:

```kotlin
fun Project.availRoot(name: String, rootDir: File): AvailRoot
{
    val rootURI = systemPath(rootDir.absolutePath, distroSrc, name)
    println("AvailRoot(${rootURI.length}): $rootURI")

    return AvailRoot(
        name,
        File(rootURI).toURI())
}

// Call site:
val root = availRoot("avail", rootProject.layout.projectDirectory.asFile)
```

**Rationale:** By capturing the value early or passing it as a parameter, we avoid holding a reference to the `rootProject` object itself.

---

## Issue 4: Similar Pattern at Line 679

Look for other occurrences of `rootProject.projectDir` usage:

### Search Pattern:
```kotlin
"${rootProject.projectDir}"
```

### Fix:
Apply the same pattern as Issue 3 - capture early or use `layout.projectDirectory.asFile.absolutePath`.

---

## Additional Considerations

### Gradle Script Object References

The error mentions "Gradle script object references" which typically refers to:
- Lambda closures capturing `this` (the build script)
- Extension functions capturing the receiver implicitly

### Common Patterns to Avoid:

1. **Direct Project Access:**
   ```kotlin
   // BAD
   tasks.register("myTask") {
       doLast {
           println(project.name)  // Captures project
       }
   }

   // GOOD
   tasks.register("myTask") {
       val projectName = project.name  // Capture value early
       doLast {
           println(projectName)  // Uses value, not project
       }
   }
   ```

2. **Lazy Access:**
   ```kotlin
   // BAD
   val myProvider = provider { rootProject.projectDir }

   // GOOD
   val myProvider = rootProject.layout.projectDirectory.map { it.asFile }
   ```

3. **Task Dependencies:**
   ```kotlin
   // BAD
   dependsOn(project.tasks.named("someTask"))

   // GOOD
   dependsOn(tasks.named("someTask"))  // Use local tasks
   ```

---

## Migration Strategy

### Step 1: Fix Direct Project References
Start with the three specific issues above (lines 207, 550, 619, 679).

### Step 2: Validate
```bash
./gradlew assemble --configuration-cache
```

Should see fewer problems reported.

### Step 3: Check Problems Report
```bash
# After running with --configuration-cache
open build/reports/problems/problems-report.html
```

This HTML report will show remaining issues with stack traces.

### Step 4: Iterative Fix
- Fix reported issues one by one
- Re-run with configuration cache
- Repeat until no problems remain

---

## Expected Benefits

Once fixed, configuration cache will:
- **Speed up builds** by 40-70% on subsequent runs
- **Skip configuration phase** entirely when nothing changed
- **Improve IDE sync** times
- **Enable better build caching** across machines

---

## Testing

After applying fixes:

```bash
# Clean and test with configuration cache
./gradlew clean --configuration-cache
./gradlew assemble --configuration-cache
./gradlew test --configuration-cache

# Second run should be much faster (reusing cache)
./gradlew assemble --configuration-cache
# Should see: "Reusing configuration cache."
```

---

## Summary of Required Changes

| Line | Current | Fix |
|------|---------|-----|
| 207 | `project.extensions.getByType(...)` | `extensions.getByType(...)` |
| 550 | `rootProject.projectDir` | `rootProject.layout.projectDirectory.asFile` |
| 553 | `$projectDir` | `layout.projectDirectory.asFile` |
| 619 | `rootProject.projectDir` | Capture at top level or pass as parameter |
| 679 | `rootProject.projectDir` | Same as 619 |

Total estimated effort: **30-60 minutes** to fix and test all issues.
