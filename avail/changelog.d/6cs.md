### VM and optimizer

- Looking up a semantic value equivalent to one absent from a manifest, and merging a newly known primitive invocation with an equal one, now follow the classes of the invocation's arguments to the invocations that read them, examining only those of the argument with the fewest readers, instead of scanning every value in the manifest (avail-6cs)

### Internal architecture

- `L2ValueManifest` answers equivalence through `indexedEquivalentsOf` and `congruentClassesOf` while it tracks semantic values, and keeps the scan for the register-only modes; with `deepManifestDebugCheck` set it checks that the classes find everything a scan does (avail-6cs)

### Bug fixes

- A manifest's record of which primitive invocations read a value no longer loses entries when values are rebound together: the uses are recorded once all are bound, and a class left empty hands its readers to the class that replaces it (avail-6cs)

### Beads closed

- `avail-6cs`

### Beads opened

- `avail-6cs`
