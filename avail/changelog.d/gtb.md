### VM and optimizer

- Postponing a move, a constant move, boxing, unboxing, or an infallible primitive named by its invocation no longer records an instruction; the optimizer produces the value again where it is read (avail-gtb)
- Side-effectless primitives can be postponed again, since running one no longer counts as writing the interpreter's latest return value (avail-gtb)
- Control flow merges keep each value that can be produced on every incoming edge, including one produced again from a primitive invocation (avail-gtb)

### Documentation

- The value manifest design doc describes producible values, elided postponement, and the assumption that a primitive invocation is infallible wherever it is known, and lists its consistency checks as they now are (avail-gtb)

### Internal architecture

- A primitive is foldable by implementing the new `Foldable` interface, from which its `CanFold` flag is derived; primitive invocations, and running a primitive without a raw function, require a `Foldable` (avail-gtb)
- `L2ValueManifest.isProducible` answers whether a value can be produced at a point, recursively through primitive invocations (avail-gtb)
- `canBePostponed` takes the manifest, and `canBeElidedFromPostponement` says when a postponed instruction need not be recorded (avail-gtb)
- Value manifest tests pin producibility, and `FoldableTest` pins that `CanFold` holds exactly for foldable primitives (avail-gtb)
- Stale debugging notes are removed from the optimization phases (avail-gtb)

### Bug fixes

- A primitive that cannot fail at its call site, but cannot be folded, no longer names its result as a primitive invocation (avail-gtb)

### Beads closed

- `avail-gtb`

### Beads opened

- `avail-bjv`
- `avail-3ib`
