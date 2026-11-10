### Internal architecture

- Every build resolves only Avail's own artifacts (`org.availlang`) from Maven Local; other artifacts there, downloaded by Maven without Gradle module metadata, made Dokka 2.3.0's dependency resolution fail inside Gradle (avail-4ai)
- `gradle-plugin` uses Dokka 2.3.0, like the other builds (avail-4ai)

### Bug fixes

- `dokkaGeneratePublicationHtml` runs again, and the two KDoc links it reported as unresolved are fixed (avail-4ai)

### Beads closed

- `avail-4ai`
