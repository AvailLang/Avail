### VM and optimizer

- Every regeneration that tracks semantic values postpones instructions again, while forcing a postponed translation emits its instruction at once (avail-9dm)
- A local variable whose variable object is ever pushed, as for a closure or a tuple, is no longer elided; it is created up front and read and written by the general variable instructions, in program order (avail-9dm)
- A read of an elided local whose last write or creation is still postponed becomes a move during translation and in every optimization pass that regenerates the code (avail-9dm)
- The three dedicated postponement passes are removed, since translation and every regeneration already postpone (avail-9dm)

### Documentation

- The variable elision design doc defines aliased locals separately from escaped variables, and describes how aliased locals are translated (avail-9dm)
- The variable elision design doc states that postponed writes merge only at control flow merges, and that a postponed write may pass a call or return (avail-9dm)

### Internal architecture

- A branch heuristic in postponement that never took effect is removed (avail-9dm)
- `regenerateForPostponement` and its overrides are gone, and calls and returns no longer force postponed writes to locals (avail-9dm)
- `L2_MULTIWAY_JUMP` is no longer a placeholder instruction (avail-9dm)
- `L2_CREATE_TUPLE` asserts on construction that it has at least one element (avail-9dm)

### Bug fixes

- A write to a local captured by a closure or stored in a tuple is no longer postponed past a call or read that can observe the variable, which could make the closure read a stale value, or overwrite the closure's own write (avail-9dm)

### Beads closed

- `avail-9dm`

### Beads opened

- `avail-o67`
- `avail-zv7`
- `avail-zv7.1`
- `avail-k33`
