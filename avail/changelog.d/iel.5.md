### Language

- Labels and continuations are now distinct kinds of value with independent types: `$[…]→R` is a label type, and `continuation` is a single type with no structure, the type of an activated execution frame (avail-iel.5)

### Standard library

- New special objects `label` (the most general label type) and `label meta`; `continuation meta` is retired, and `continuation returning top` is now `label returning top` (avail-iel.5)
- New bootstrap primitive `Exit current fiber with_` ends the current fiber cleanly with the given result, which a fiber's joiner checks against the fiber's result type; the unhandled-exception handler uses it instead of exiting a hand-made root continuation (avail-iel.5)
- `_'s⁇caller` and `_'s⁇function` also accept a label; restarting and exiting (`Restart_with_`, `Exit_`, `Exit_with_`, and their conditional forms) take labels, and `$_` and `_'s⁇function type` convert between function types and label types (avail-iel.5)

### VM and optimizer

- A continuation's type is the primitive type `continuation`; a label's type is the label type for its function's type (avail-iel.5)
- The repository version is now V20, since serialized types and special objects changed meaning; existing compiled module caches are rebuilt (avail-iel.5)

### Internal architecture

- `ContinuationTypeDescriptor` is now `LabelTypeDescriptor`, with `PrimitiveTypeDescriptor.Types.CONTINUATION`, the `LABEL_TAG` and `LABEL_TYPE_TAG` type tags, and the `LABEL_TYPE` serializer operation; new primitives `P_LabelCaller` and `P_LabelFunction`, and the type conversions are renamed `P_LabelTypeToFunctionType` and `P_FunctionTypeToLabelType` (avail-iel.5)

### Beads closed

- `avail-iel.5`

### Beads opened

- `avail-0gy`
