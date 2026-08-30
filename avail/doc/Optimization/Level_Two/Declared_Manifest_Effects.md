# Declared manifest effects: the post-phi manifest

Status: **proposed, not started.** First captured 2026-08-15 from the analysis
of `testSemanticRestrictionOfSetCreation_1`, which reproduces the failure that
was stopping a full library load at roughly 16%. Revised after review: the
mechanism is a registerless manifest held on the basic block, not an
instruction.

Companion to `Manifest_ValueClass_Redesign.md`, which supplies the value-level
versus register-level split this design depends on.

## 1. The defect

Regenerating a graph rebuilds each edge's manifest from whatever the emitted
instructions happen to record. That cannot preserve what the input edge knew,
because the *reason* for a fact is frequently an instruction that has not been
emitted yet — it is postponed — or one that never will be, because it was
found dead.

Concretely, on the edge from block #9 to block #11 of the reproducer:

| | synonym for the outer | 5-13 |
|---|---|---|
| naive generation | `Outer#1(elements) & 5-3 & ⌛5-13`, `t={any+}ᵀ` | (in that synonym) |
| after dead code elimination | `Outer#1(elements) & 5-3`, `t={any+}ᵀ` | alone, `t=⊤` less ruled-out tags |

The move into 5-13 had already been postponed into the first instruction of #11
during naive generation, but it correctly left its mark on the edge: 5-3 and
5-13 are synonymous there. During the rewrite for dead code elimination the
cause of that synonymy was not available at the point the edge was built — it
arrives at the start of #11, after the edge — so the edge lost it. 5-13 is left
by itself, and with a weaker restriction, since all that remains of it is the
suprema left over from subtracting ruled-out tags.

Everything downstream of that is a consequence: 5-13 gets recomputed rather
than reused, and a value that should be a metatype is described as ⊤, which the
primitives asked to reason about it cannot use.

## 2. Why re-derivation is the wrong shape

Manifest content is currently a *side effect* of code generation, when it needs
to be an *input* to it. A pass emits instructions, and whatever they happen to
record is what the successor edges believe. Any fact whose carrier is absent at
that moment is lost, and no amount of care in individual passes fixes it,
because the carrier's absence is exactly what postponement and dead code
elimination are for.

So the information has to be *transported*, not recomputed.

## 3. Which facts transport

The parts of a manifest entry behave completely differently under a rewrite:

- **Transportable** — which semantic values name one value, the
  `TypeRestriction` bounding it, and any postponed instruction that would
  produce it. These are facts about the program. They are monotone and survive
  any rewrite that does not change what the program computes.
- **Not transportable** — which registers hold the value. These are facts about
  the *current* graph and are meaningless in a graph being built.

In the terms of the `ValueState` redesign, the transportable part is
`ValueState.members`, `ValueState.restriction` and each `Representation`'s
postponed instruction; the untransportable part is each `Representation`'s
definitions.

The bug is entirely in the first category. A rewrite may legitimately change
every register; it may not silently forget that two names denote one value.

## 4. The post-phi manifest

Every basic block carries a **`postPhiManifest`**: a registerless manifest
holding synonyms, restrictions and postponed instructions, and no registers.

It is not an instruction. An instruction that never reads, never writes, never
emits code and may appear in only one position is not an instruction, and
making it one obliges every pass that manipulates instructions to know it is
special. Blocks already carry `zone`, `isLoopHead`, `isCold` and
`hasControlFlowAtEnd`, so block-level state is not a new idea here.

### 4.1 When it is captured

At the end of code generation for the block: **after the reads of the final
instruction have been processed, but before any of its writes and before any
edge-specific narrowing.**

Both halves of that matter.

- *After the reads* — a read forces any postponed instruction it depends on to
  be materialized. Capturing afterwards means a postponed instruction that
  supplies a value to a compare-and-branch has already been reified into the
  block, and is recorded as what it now is rather than as a postponement that
  will never happen there.
- *Before the writes and the edge-specific parts* — the narrowing a branch
  applies differs per outgoing edge, so it belongs to the edges, not to the
  block. Capturing before it keeps the block's record true on every outgoing
  edge, which is exactly the property that lets the edges be rebuilt from it.

### 4.2 When it is replayed

During regeneration, in passes that track semantic values, the *old* block's
`postPhiManifest` is replayed into the new block's manifest **after phis have
been generated**, ensuring the synonym structure and the narrowed restrictions
are present before any instruction in the block is processed.

It is not copied into the new block. The new block captures its own at the end,
which is the old one plus whatever this pass managed to strengthen.

### 4.3 Prescience is not a problem here

Replaying at the top of a block asserts facts that, in the original generation
order, only became true partway through it. That is fine, and the existing
postponement mechanism already does much the same thing: it records what an
unconditionally-downstream instruction would produce, long before producing it.
Nothing here crosses a block boundary, and in straight-line code every
instruction in the block runs, so a fact true at the end of the block is true
for anyone who reaches the end.

Writing a value the manifest already knows is already supported:
`recordDefinitionNoCheck` takes its "existing semantic value" branch, extends
the synonym, intersects the restriction, adds the definition and clears the
postponement. Note that this makes that branch — written for the boxed/int case
— the common path for nearly every write, and that the comment above
`recordDefinition` claiming the value "must not yet be in this manifest" is
already stale and would become actively misleading.

## 5. Witnesses, and why postponements must be captured

Every synonym in a `postPhiManifest` needs a **witness**: something that will
make the value producible again when the manifest is replayed. A synonym with
no witness is a value that is known, unproducible, and reachable by anything
that goes looking — the exact pathology this whole effort has been chasing.

The admissible witnesses are:

1. an instruction in the block that writes it, replayed by processing the block
   in the ordinary way;
2. a postponed instruction recorded in the `postPhiManifest`;
3. a constant restriction;
4. synonymy with a value that is itself witnessed, which produces it by the
   implicit move;
5. presence in every incoming edge, witnessed by the predecessors.

Capturing postponements is what makes (2) available, and it is load-bearing
across passes in a way that is easy to miss:

> Suppose instruction X cannot be postponed all the way out of its block on
> pass 1. It is not a postponement in that block's `postPhiManifest`, but X
> itself is a perfectly good witness — a replayable statement about a write.
> On pass 2, code splitting makes X postponable, and it is postponed out; now
> the captured postponement is the witness. On pass 3 the `postPhiManifest` is
> replayed and the postponement is preserved into the new one. **In the current
> implementation that postponed instruction is simply lost at that point.**

A consequence worth stating explicitly, because it changes liveness: **a
postponed instruction's reads are not uses of its inputs.** An input read only
by a postponed instruction does not have to be emitted — but neither may it be
dropped, since the postponed instruction still needs it if it ever
materializes. So liveness becomes three-valued: used (must emit), read only by
a postponement (may postpone, must not drop), and unused (may drop).

## 6. The monotonicity law

The rule that makes replay sound:

> **A pass must never weaken a constraint found at an analogous location in a
> previous pass.**

Merging synonyms, whether between passes or along a path, is a strengthening.
Breaking them apart is forbidden — with exactly one exception, phi generation,
where a control flow merge genuinely destroys information because the
predecessors disagree.

That destruction is not incidental; it is the driving force behind code
splitting, which exists to specialize paths so that the information survives.
Code splitting narrows constraints along a specialized path, which is a
strengthening and therefore always allowed.

## 7. Control flow preconditions

**Predecessor sets.** Replaying a block's own captured facts is sound as long
as no path reaches the block that did not reach it before. Losing a predecessor
is safe. Gaining one is only done by loop generation, which already handles it
with `L2_STRIP_MANIFEST` and `forcedClampedEntities`; planned loop splitting —
running a first iteration to hoist bounds checks, parts of lookups and some
unboxing, much as code splitting does today — is expected to work the same way.

Note that the predecessor *count* can go up or down between a graph and its
regeneration, through code splitting, without any new path reaching the block.
Replay after phi generation has to cope with that, but there is no reason to
expect it to be hard.

**Jump elision.** Merging a block into its sole predecessor leaves the merged
block's `postPhiManifest` describing a mid-block point. Since these are
re-derived every pass, discarding the absorbed block's copy is right; the
combined block captures its own at the end.

## 8. What this makes unnecessary, and what it improves

- **Explicit edge transport, and any need for input/output edge
  correspondence.** Rewrites that change the shape of control flow are then
  free: a multi-way tag branch becoming one-direction jumps, `≤` rewritten as
  `>` or `=` according to the boundaries of known restrictions, elision of
  edges all of whose paths lead to impossible conditions, and jump elision to
  enlarge basic blocks.
- **The `L2_NOP` tombstone** that `removeDeadInstructions` leaves where it drops
  an instruction. It is a witness with its content thrown away.
- Probably **the base fabrication in `recordDerivation`**. It exists because a
  derived value can be bound while its base is unknown; if the base's synonymy
  is transported, the base should already be present. The restriction seeded
  from the tag (commit `a3cf993db`) is a narrower patch for the same hole and
  may become redundant.
- **Dead code elimination gets stronger, not weaker.** Previously the only
  record that `x` and `z` were synonymous might be two overlapping facts,
  `[x,y]` and `[y,z]`, so `y` could not be dropped without losing the
  relationship. A `postPhiManifest` records the synonym `{x,y,z}` directly, so
  `y` can be dropped outright. Once semantic values and registers that are
  never used or populated have been identified, they can be excluded from every
  manifest in a fresh regeneration pass, and synonyms left empty are dropped.

## 9. Remaining questions

1. **Rendering.** Every diagnosis in this effort has come from reading .dot
   output, so an invisible mechanism this central would be a bad trade. Options
   are a graph-level node beside the block, or an extra column in the block's
   table — the latter is awkward because multi-way final instructions already
   use multiple columns.
2. **Semantic value transformation.** A transported map keyed by semantic
   values must be mapped when a pass renames them. Not a problem today, since
   `L2ValueManifest.transform` has no callers; it is intended for inlining
   called graphs, where a callee's stack slots must be distinguished from the
   caller's via `Frame`. Whatever holds the map will need to participate then.
3. **Replay order for derived values.** Binding `Int(Tag(x))` before `x` takes
   the introduce-the-base path. Harmless, and it disappears when the manifest
   becomes keyed by boxed-only semantic values, with the int forms implicit in
   the read and write operands.

## 10. Suggested order

1. Add `postPhiManifest` to `L2BasicBlock` and capture it at the point
   described in section 4.1. Render it in the .dot output. Nothing consumes it
   yet, so the graphs should be unchanged and the captures inspectable.
2. Assert the witness invariant of section 5 over each capture. This is the
   property the design turns on, and it is cheaper to establish before anything
   depends on it.
3. Replay it during regeneration, after phi generation. Verify against
   `testSemanticRestrictionOfSetCreation_1`, and add a per-edge assertion that
   an output edge is at least as informative as the corresponding input edge —
   the invariant this whole design exists to establish.
4. Adjust liveness for the three-valued rule in section 5, so that inputs read
   only by postponements are postponed rather than emitted or dropped.
5. Remove what section 8 makes unnecessary, and add the never-used exclusion
   pass that section 8 makes possible.
