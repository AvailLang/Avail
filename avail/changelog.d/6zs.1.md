### VM and optimizer

- A call whose function is known, and whose function has an optimized chunk that makes no calls, reifies only to check for interrupts, and has no loop, is embedded in the caller's optimized code instead of invoked; the callee's arguments, function, and result become values of the caller's graph (avail-6zs.1)
- Extracting a captured outer value from a function the same chunk created, or from a constant function, now reads the captured value directly whenever the graph is regenerated, so the function itself may never need to be built (avail-6zs.1)

### Internal architecture

- A new optimization phase, `CAPTURE_INLINABLE_GRAPH`, keeps the graph as it stands before label placeholders are expanded, and each JVM chunk holds it as its `inlinableGraph` (avail-6zs.1)
- `L2Inliner` embeds a kept graph at a call site, translating each of the callee's semantic values into a new inlined `Frame` before regenerating its instructions; the caller then depends on everything the callee's chunk depends on (avail-6zs.1)
- `OptimizationLevel.inlinesCallees` says whether a level embeds callees; both JVM levels do (avail-6zs.1)
- `L2ValueManifest.transform` merges a synonym into one already holding the same constant instead of failing an assertion, and `L2Regenerator.isLoopHead` lets the inliner regenerate a callee's prologue blocks as ordinary blocks, so they keep what the caller knows (avail-6zs.1)
- The block that enters an embedded callee notes `INLINING:` and the callee's name, for graph visualizations; eliding a jump into a block, or splitting one, keeps the notes of the blocks involved (avail-6zs.1)
- `L2Chunk.contingentValues` is readable, `PostPhiData.transform` translates post-phi data, and `L2Instruction.sourceCopyWithOperands` lets a regenerator process a renamed copy of a source instruction (avail-6zs.1)
- `InliningTest` pins that a call-free callee is embedded with its dependencies and results intact, and that a callee making calls is still invoked (avail-6zs.1)

### Beads opened

- `avail-6zs.1`
- `avail-6zs.2`
- `avail-6zs.3`
- `avail-687`
- `avail-6bk`
