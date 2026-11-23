### VM and optimizer

- An optimized chunk captures the function it was invoked with when it is entered, instead of asking the interpreter for the current function wherever it needs it, and stops capturing it when nothing uses it (avail-6zs.2)
- Values that only pass through phis, moves, and manifest strips without being used, such as an unused function or argument circulating around a loop, are removed by dead code elimination (avail-6zs.2)
- Falling back to level one code, and reporting a result that disagrees with its expected type, take the frame's function explicitly, and the report takes the returning function explicitly, so both are right for a frame embedded in another chunk (avail-6zs.2)

- A callee that falls back to level one code, or that reports a result disagreeing with its expected type, can now be embedded; if the embedded frame then reifies, the caller's frame is reified beneath it through the call site's own reification path (avail-6zs.2)

### Internal architecture

- `L2_INLINED_FALL_BACK_TO_L1` continues an embedded frame in level one code, then either continues like a return or reifies the running chunk's frame; `L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION` gains an optional reification edge for the same purpose (avail-6zs.2)

- `L2_ENTER_L2_CHUNK_FOR_CALL` has an optional write for the invoked function, and `L2_GET_CURRENT_FUNCTION` is gone (avail-6zs.2)
- `DeadCodeAnalyzer.shuffledOnlyRegisters` finds registers that are only shuffled, and `L2Instruction.withoutShuffledOnlyValues` drops them from strips, back jumps, moves, and chunk entries (avail-6zs.2)
- `L2_GET_RETURNING_FUNCTION` reads the interpreter's returning function after a call that actually returned; `CallSiteHelper.returningFunction` carries it, or an embedded callee's function, to the return check (avail-6zs.2)
- `SimpleOptimizerTest` pins that a function known exactly is not captured; `InliningTest` pins that a failed check of an embedded callee's result blames the callee, and that reifying from an embedded fallback or wrong-result report puts the caller's frame beneath the callee's (avail-6zs.2)

### Bug fixes

- Redirecting edges past a block holding only a backward jump gives them the jump's kept registers, which liveness analysis needs along a back edge (avail-6zs.2)
- A failed check of an embedded callee's result no longer names whichever function last returned through the interpreter, and a return from optimized code now records its function as the returning function, which only level one returns and primitives did before (avail-6zs.2)
