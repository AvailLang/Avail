### VM and optimizer

- Sanity checks assert that no regenerated edge, and no output edge of a single-input block, knows less than the edge it corresponds to (avail-ezf.2)
- Blocks holding only a jump are kept when their post-phi data records facts their incoming edges lack, and the JVM translator jumps straight past them (avail-ezf.2)

### Build, distribution, and tooling

- `Time all tests_time|times` marks with ` *` each run faster than every earlier run in the batch (avail-ezf.2)

### Documentation

- The post-phi data design doc describes when a jump-only block is kept (avail-ezf.2)

### Internal architecture

- `TypeRestriction` flags are facts that combine by set operations; `EXCLUDES_BOTTOM_FLAG` replaces `CAN_BE_BOTTOM_FLAG` (avail-ezf.2)
- Decayed optimizer sanity checks match the current semantic-value rules, and their assertions say what failed (avail-ezf.2)

### Bug fixes

- Removing useless branches no longer shares registers between the old and new graphs (avail-ezf.2)
- Intersecting a restriction with an equal constant keeps both sides' flags, and two object-type intersections no longer ignore one operand (avail-ezf.2)
- Reading an object field narrows its type by the field atom's constraint, avoiding an `ObjectFieldTypeException` during optimization (avail-ezf.2)
- Recomputing a primitive's result type skips arguments narrowed to ⊥ (avail-ezf.2)
- Elided reads of unescaped local variables merge synonyms instead of postponing moves (avail-ezf.2)

### Beads closed

- `avail-ezf.2`

### Beads opened

- `avail-1ww`
- `avail-p10`
