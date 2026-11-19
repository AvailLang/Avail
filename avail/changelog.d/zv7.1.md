### VM and optimizer

- When a continuation with elided local variables becomes immutable or shared, the optimized chunk itself now creates those variables, by running a freeze subgraph generated alongside the continuation's creation, instead of decoding a table of placeholders from its register dump (avail-zv7.1)
- A read of an elided local whose creation or latest assignment is still postponed now becomes a move of that value, instead of creating the variable at the read, so locals such as the guard variable of the exception-catching primitive stay elided unless their frame is frozen (avail-zv7.1)
- Generated JVM chunks have a `freeze` method, which takes the continuation instead of an interpreter, and is selected by a lookupswitch on the register dump's new `FREEZE_OFFSET` (avail-zv7.4)

### Documentation

- The variable elision design doc describes freeze subgraphs in a new §Freezing, and no longer describes placeholder variables (avail-zv7.1)

### Internal architecture

- `L2_CREATE_CONTINUATION` is a control-flow instruction with a fall-through edge and an optional freeze edge, and the new `L2_ENTER_FREEZE` and `L2_FINISH_FREEZE` start and end a freeze subgraph (avail-zv7.1)
- `VariablePlaceholderDescriptor`, the elided-local encoding in register dumps, and `L2_SAVE_ALL_AND_PC_TO_INT`'s `dirtyLocals` are removed; a register dump instead reserves room for the registers its freeze subgraph needs (avail-zv7.1)
- `L2_CREATE_VARIABLE` marks an elidable local with a flag instead of carrying its placeholder (avail-zv7.1)
- Control flow graph renderings show freeze subgraphs in their own zone and draw freeze edges in a distinct color, with retuned zone colors for light and dark mode (avail-zv7.1)

### Bug fixes

- Dark-mode control flow graph renderings take a single-valued color's green component from its green byte rather than its red byte

### Beads closed

- `avail-zv7.1`
- `avail-zv7.4`
- `avail-4y0`

### Beads opened

- `avail-1ct`
