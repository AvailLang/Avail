### VM and optimizer

- An optimized chunk captures the function it was invoked with when it is entered, instead of asking the interpreter for the current function wherever it needs it, and stops capturing it when nothing uses it (avail-6zs.2)
- Values that only pass through phis, moves, and manifest strips without being used, such as an unused function or argument circulating around a loop, are removed by dead code elimination (avail-6zs.2)
- Falling back to level one code, and reporting a result that disagrees with its expected type, take the frame's function explicitly, and the report takes the returning function explicitly, so both are right for a frame embedded in another chunk (avail-6zs.2)
- A callee that falls back to level one code, or that reports a result disagreeing with its expected type, can now be embedded; if the embedded frame then reifies, the caller's frame is reified beneath it through the call site's own reification path (avail-6zs.2)
- A callee that makes calls of its own can now be embedded, including one that embeds callees itself; a reification inside embedded code builds one continuation for the whole chunk, and freezing it builds the frames of the embedding callers as level one continuations beneath it, innermost first (avail-6zs.2)
- A continuation whose freeze builds such frames always resumes in level one code afterward, even when its function has no local variables (avail-6zs.2)
- Primitives that need nothing from the interpreter but a way to fail are isolated, so their infallible invocations can be postponed into a chunk's freeze code, which has no interpreter; only those primitives are produced on demand, and other postponed invocations are run before a continuation's freeze edge (avail-6zs.2)

### Internal architecture

- `L2_INLINED_FALL_BACK_TO_L1` continues an embedded frame in level one code, then either continues like a return or reifies the running chunk's frame; `L2_INVOKE_INVALID_MESSAGE_RESULT_FUNCTION` gains an optional reification edge for the same purpose (avail-6zs.2)
- `L2_ENTER_L2_CHUNK_FOR_CALL` has an optional write for the invoked function, and `L2_GET_CURRENT_FUNCTION` is gone (avail-6zs.2)
- `DeadCodeAnalyzer.shuffledOnlyRegisters` finds registers that are only shuffled, and `L2Instruction.withoutShuffledOnlyValues` drops them from strips, back jumps, moves, and chunk entries (avail-6zs.2)
- `L2_GET_RETURNING_FUNCTION` reads the interpreter's returning function after a call that actually returned; `CallSiteHelper.returningFunction` carries it, or an embedded callee's function, to the return check (avail-6zs.2)
- `L2_FINISH_FREEZE` is split into `L2_UPDATE_SLOTS`, which writes the frozen frame's slots, and `L2_EXIT_FREEZE`, which ends the freeze; `L2_CREATE_L1_CONTINUATION` builds an enclosing frame as level one sees it, and `L2_ADD_UNDER_FROZEN_FRAME` slides it beneath the frames built so far (avail-6zs.2)
- `L2Inliner` turns an embedded callee's `L2_EXIT_FREEZE` into a jump to its call site's freeze block, which `L1Translator.generateFreezeOfCaller` fills, and gives every embedded continuation creation a freeze subgraph (avail-6zs.2)
- `PrimitiveContext` is all an isolated primitive may ask of whatever runs it: a way to fail; `Interpreter` implements it, and `FreezePrimitiveContext` serves a chunk's freeze method; the Java classes `PrimitiveIsolated0` through `PrimitiveIsolated4` and `PrimitiveIsolatedN` declare `attemptIsolated`, which takes a context in place of an interpreter and which generated code calls directly, and 321 primitives now extend them (avail-6zs.2)
- `L2ControlFlowGraph.makeAllShared` makes every Avail object a graph holds shared, through `L2Instruction.makeOperandsShared`, `L2Operand.makeOperandShared`, `L2ValueManifest.makeAllShared`, `Representation.makeRepresentationShared`, and the `makeShared` of semantic values and registers; the graph kept for inlining is made shared when it is captured (avail-6zs.2)
- `L2Instruction.canRunInFreeze` says whether a postponed instruction may travel into a freeze subgraph; `Interpreter.checkArgumentCount` is gone, since the primitives that used it assert on their argument arrays (avail-6zs.2)
- `SimpleOptimizerTest` pins that a function known exactly is not captured; `InliningTest` pins that a failed check of an embedded callee's result blames the callee, that reifying from an embedded fallback or wrong-result report puts the caller's frame beneath the callee's, and that freezing an embedded callee's continuation builds its callers' frames, nested or not, creates both frames' elided locals, shares a captured local with an embedded block, and survives the caller's chunk being invalidated while the callee is suspended, and that two embeddings of one callee on one argument compute a single value (avail-6zs.2)

### Bug fixes

- Redirecting edges past a block holding only a backward jump gives them the jump's kept registers, which liveness analysis needs along a back edge (avail-6zs.2)
- A failed check of an embedded callee's result no longer names whichever function last returned through the interpreter, and a return from optimized code now records its function as the returning function, which only level one returns and primitives did before (avail-6zs.2)
- Extracting an element from the result of appending to a tuple asks the input tuple's size range for its bounds, rather than the tuple type itself, which failed (avail-6zs.2)
- A primitive invocation that becomes known after its arguments are already known to be equal, such as one replayed from an embedded callee's post-phi data, is now merged with the equal invocation already present; previously a later merge point could find both holding registers and fail (avail-6zs.2)
- Folding a chain of tuple element updates into a constant tuple records each updated element's type at its own index, rather than at the next one, which also failed for an update of the last element (avail-6zs.2)
