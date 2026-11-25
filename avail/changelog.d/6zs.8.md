### VM and optimizer

- Code splitting can now wish for a value to be known exactly, whatever it turns out to be, and keeps apart the paths that know different constants or object variants, so that successive dispatches on the variant of one object, in one chunk or across an embedded callee, test the variant only once (avail-6zs.8)
- Cold code, such as the reification path of a call, now splits for the wishes of the warm code it resumes into, so that the paths it would otherwise merge, such as the arms of a dispatch, keep what they knew when they resume (avail-6zs.8)
- A dispatch on object variants or on a value's hash wishes only to know the value exactly, rather than for each possible case to be known or excluded, which raises far fewer split conditions in large dispatches; a dispatch on type tags keeps its range conditions (avail-6zs.8)

### Internal architecture

- `L2SplitCondition.specializedFor` lets a condition stand, in the key of a split block, for what the incoming edge actually knows; `L2KnownConstantCondition` and `L2KnownVariantCondition` specialize to the edge's constant or variant (avail-6zs.8)
- `KnownExactlyTracer` traces a wish to know a value exactly through moves, phis, boxing, and code point conversion, and turns a wish for a constant variant id into a wish for the object's (or object type's) variant, through `L2Instruction.traceKnownExactly`; `knownExactlyConditions` replaces `constantConditions` (avail-6zs.8)
- `P_CompiledCodeOfFunction` is renamed `P_RawFunction` (avail-6zs.8)
- `SimpleOptimizerTest` and `InliningTest` pin that dispatching twice on one object's variant tests it once, on every path including those that resume after reification, within a function and with either dispatch in an embedded callee (avail-6zs.8)

### Beads closed

- `avail-6zs.8`

### Beads opened

- `avail-6zs.5`
- `avail-6zs.6`
- `avail-6zs.7`
- `avail-6zs.7.1`
- `avail-6zs.8`
