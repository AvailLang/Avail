# Post-phi data

Every [L2BasicBlock] of a control flow graph carries a [PostPhiData]: a map
from each synonym (a set of semantic values known to denote one value) to the
restriction bounding that value, as the block's manifest stood just before
the block's final instruction.  It records no registers and no postponed
instructions.  When the optimizer regenerates the graph, it replays each old
block's post-phi data into the corresponding new block right after the phis,
so the synonyms and restrictions that one pass established are present from
the start of the block in the next, even when the instructions that
established them have since moved or disappeared.

After a graph is complete, the same data serves as the block's settled view of
what every value in it is known to be (see [Other uses](#other-uses)).

## Why it is carried

Regenerating a graph rebuilds each edge's manifest from whatever the emitted
instructions record.  That alone cannot preserve what the input edge knew,
because the reason for a fact is often an instruction that has not been
emitted yet, because it is postponed, or one that never will be, because it
was found dead.  A move into a value, postponed into the first instruction of
a successor block, still leaves its mark on the edge: the two values are
synonymous there.  When the graph is regenerated, the move's cause arrives
only at the start of the successor, after the edge, so the edge loses the
synonymy, and with it whatever restriction only one of the two values
carried.  Everything downstream suffers: the value is recomputed rather than
reused, and its type is weaker than the program warrants.

So the facts are transported rather than re-derived.  The parts of a manifest
entry behave differently under a rewrite:

- **Transportable.**  Which semantic values name one value, and the
  [TypeRestriction] bounding it.  These are facts about the program.  They
  hold in any rewrite that does not change what the program computes.
- **Not transportable.**  Which registers hold the value, and where a
  postponed instruction currently sits.  These are facts about the current
  graph, and are either meaningless in a graph being built or re-established
  by building it, since graph rewriting always allocates fresh registers.

Post-phi data carries exactly the first kind.  A rewrite may change every
register, but it does not forget that two names denote one value.

## Content

[L2ValueManifest.extractPostPhiData] builds the map from the manifest's value
states, one entry per synonym, leaving out a synonym whose only member is a
semantic constant: the constant itself says everything about it.  Looking up a
synonym or a semantic value answers the constant's restriction when a member
is a semantic constant, and otherwise the recorded restriction.

## Capture

A block captures its post-phi data when its final instruction is added, in
[L2ControlFlowInstruction.justAdded] (and in [L2_JUMP_BACK], which ends a loop
body).  The capture comes after the instruction's reads have narrowed the
manifest, but before its writes are applied to it, and before any narrowing
that applies to only one outgoing edge.

- *After the reads.*  A read forces any postponed instruction it depends on to
  be emitted, and emitting one can strengthen the manifest: merging synonyms,
  narrowing a result, folding a constant.  Capturing afterward makes those
  consequences part of the record.
- *Before the writes and the edge-specific narrowing.*  A branch narrows
  differently along each outgoing edge, so that narrowing belongs to the
  edges, not to the block.  Leaving it out keeps the record true along every
  outgoing edge, which is what lets a regenerated block start from it.

Two other events touch a block's data:

- Splitting an edge creates a block whose data is taken from the edge's own
  manifest.
- Eliding a jump, by continuing generation in the jump's sole predecessor,
  discards the predecessor's data, which would otherwise describe a point in
  the middle of the merged block.  The merged block captures its own when its
  new final instruction is added.

## Replay

[L2Regenerator.processSourceGraph] starts each new block (generating its
phis) and then applies the corresponding old block's post-phi data to the
current manifest with [L2ValueManifest.applyPostPhiData], before regenerating
any of the block's instructions.  Applying an entry agglomerates its semantic
values into one synonym, merging any existing synonyms that share a member,
and intersects the restriction.  A block that code splitting has specialized
receives the same data as the unspecialized one; the restrictions its incoming
edges guarantee are already in its manifest, and the intersection keeps them.

The data is not copied into the new block.  The new block captures its own at
its end, which is what the old block knew plus whatever the current pass
added.

### Facts known early

Replay asserts at the top of a block facts that, in the original generation
order, became true only partway through it.  That is sound for the same
reason postponement is: postponing an instruction already records what an
unconditionally downstream instruction will produce, long before producing
it.  Nothing crosses a block boundary, and every instruction of a block runs
whenever its end is reached, so a fact true at the end of the block is true
for anything that observes the value, since an observer must read it after
the instruction that writes it.

When regeneration then reaches an instruction that writes a value the
manifest already knows, [L2ValueManifest.recordDefinitionNoCheck] extends the
existing synonym with any new semantic values, adds the register as a
definition, clears the value's postponed instruction, and intersects the
synonym's restriction with the writer's.

## Monotonicity

Replay is sound only under one law:

> A pass must never weaken a constraint found at an analogous location in a
> previous pass.

Merging synonyms, whether between passes or along a path, is a
strengthening.  Breaking them apart is forbidden, with exactly one exception:
phi generation, where a control flow merge destroys information because the
predecessors disagree.  That loss is the reason code splitting exists, which
specializes paths so the information survives.  Code splitting narrows
constraints along a specialized path, which is a strengthening and therefore
always allowed.

## Control flow preconditions

Replaying a block's own facts is sound as long as no path reaches the block
that did not reach it before.  Losing a predecessor is safe.  Gaining one
happens only at a loop head, through a back edge ([L2_JUMP_BACK]), and the
back edge is preceded by [L2_STRIP_MANIFEST] instructions that reduce the
manifest to the values that cross it.

The number of predecessors can still change between a graph and its
regeneration, through code splitting, without any new path reaching the
block.  Replay happens after phi generation, so it sees the merged manifest
whatever the count.

## Jump-only blocks

A block holding nothing but an unconditional jump can still carry post-phi
data that its incoming edges lack.  After register coloring, for example, a
move between two registers of the same color is dropped, and the synonym it
established survives only in the data of the block that held it.  Since each
regeneration rebuilds the edges' manifests from scratch, discarding that block
would discard the fact for good, and a later pass could no longer find a
register for a value it reads.

So [L2Optimizer] redirects an incoming edge past a jump-only block only when
the edge's manifest already knows everything the block's data records, and
keeps the block for the remaining edges.  Merging a block forward into its
successor applies only to a block with instructions to move, never to a
jump-only one.  The kept block costs nothing at run time: [JVMTranslator]
resolves a jump to a jump-only block as a jump to that block's target.
Chains of them cannot form a cycle, since every backward jump leads to code
that checks for interrupts.

## Other uses

Once a graph is complete, a block's post-phi data is the authoritative record
of what each value in the block is known to be, and the late phases and the
back end read restrictions from it rather than from the operands:

- Split condition discovery: each instruction's `interestingConditions`
  (and each primitive's `interestingSplitConditions`) is evaluated against
  the block's data, as are the restrictions that `RestrictionTracer` follows
  back through the graph.
- The insertion of [L2_MAKE_IMMUTABLE] and [L2_RECYCLE_IF_MUTABLE] in
  [L2Optimizer] takes the restrictions of the values it reads from the data.
- [JVMTranslator] answers a read operand's restriction from its block's data.
- [L2_INVOKE] decides whether it is cold from the called function's
  restriction in the data.
- Object field instructions render the slot index of a field when the data
  pins the object to a single variant.

## Rendering

[L2ControlFlowGraphVisualizer] draws a block's post-phi data as an extra row
of the block's table, one line per synonym with its restriction.

[L2BasicBlock]: ../../../src/main/kotlin/avail/optimizer/L2BasicBlock.kt
[PostPhiData]: ../../../src/main/kotlin/avail/optimizer/PostPhiData.kt
[TypeRestriction]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operand/TypeRestriction.kt
[L2ValueManifest.extractPostPhiData]: ../../../src/main/kotlin/avail/optimizer/manifest/L2ValueManifest.kt
[L2ValueManifest.applyPostPhiData]: ../../../src/main/kotlin/avail/optimizer/manifest/L2ValueManifest.kt
[L2ValueManifest.recordDefinitionNoCheck]: ../../../src/main/kotlin/avail/optimizer/manifest/L2ValueManifest.kt
[L2ControlFlowInstruction.justAdded]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/L2ControlFlowInstruction.kt
[L2_JUMP_BACK]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/L2_JUMP_BACK.kt
[L2Regenerator.processSourceGraph]: ../../../src/main/kotlin/avail/optimizer/reoptimizer/L2Regenerator.kt
[L2_STRIP_MANIFEST]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/L2_STRIP_MANIFEST.kt
[L2_MAKE_IMMUTABLE]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/L2_MAKE_IMMUTABLE.kt
[L2_RECYCLE_IF_MUTABLE]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/numbers/L2_RECYCLE_IF_MUTABLE.kt
[L2Optimizer]: ../../../src/main/kotlin/avail/optimizer/L2Optimizer.kt
[JVMTranslator]: ../../../src/main/kotlin/avail/optimizer/jvm/JVMTranslator.kt
[L2_INVOKE]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/L2_INVOKE.kt
[L2ControlFlowGraphVisualizer]: ../../../src/main/kotlin/avail/optimizer/L2ControlFlowGraphVisualizer.kt
