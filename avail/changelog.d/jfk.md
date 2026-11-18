### VM and optimizer

- Optimized code no longer checks, after calls and after writes to module variables, whether an elided local has become shared or gained a write reactor; that can no longer happen while the optimized code is running (avail-jfk)

### Documentation

- The variable elision design doc explains why an elided local cannot escape while its optimized code runs, and no longer describes the escape check (avail-jfk)

### Internal architecture

- `L2_CHECK_ESCAPED_LOCALS`, `emitCheckLocals`, the escape-testing call-site junctions, `mightMakeEscapedVariableShared`, and the primitive flag `MightMakeEscapedVariableShared` are removed (avail-jfk)

### Beads closed

- `avail-jfk`
