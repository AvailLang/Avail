### VM and optimizer

- Optimized code no longer checks, after calls and after writes to module variables, whether an elided local has become shared or gained a write reactor; that can no longer happen while the optimized code is running (avail-jfk)

### Documentation

- The variable elision design doc explains why an elided local cannot escape while its optimized code runs, and no longer describes the escape check (avail-jfk)

### Internal architecture

- `L2_CHECK_ESCAPED_LOCALS`, `emitCheckLocals`, the escape-testing call-site junctions, `mightMakeEscapedVariableShared`, and the primitive flag `MightMakeEscapedVariableShared` are removed (avail-jfk)
- The clearing and non-clearing reads of an elided local are one operation with a `GetClearMode` operand, and the elided-local get and set operations are renamed `L2_GET_ELIDABLE_LOCAL_VARIABLE` and `L2_SET_ELIDABLE_LOCAL_VARIABLE` (avail-jfk)

### Beads closed

- `avail-jfk`
