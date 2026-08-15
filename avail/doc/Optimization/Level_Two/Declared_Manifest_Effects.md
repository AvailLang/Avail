# Declared manifest effects: `L2_DECLARE`

Status: **proposed, not started.** Design captured 2026-08-15 from the analysis
of `testSemanticRestrictionOfSetCreation_1`, which reproduces the failure that
was stopping a full library load at roughly 16%.

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

## 3. Value-level and register-level facts

The two halves of a manifest entry behave completely differently under a
rewrite:

- **Value-level** — which semantic values name one value, and the
  `TypeRestriction` bounding it. These are facts about the program, monotone,
  and survive any rewrite that does not change what the program computes. This
  is `ValueState.members` and `ValueState.restriction`.
- **Register-level** — which registers hold the value, and any postponed
  instruction that would populate one. These are facts about the *current*
  graph and are meaningless in a graph being built. This is `Representation`.

The bug is entirely in the first category. A rewrite may legitimately change
every register; it may not silently forget that two names denote one value.

## 4. `L2_DECLARE`

Emit every manifest-affecting instruction as a pair:

- an **`L2_DECLARE`** wrapping a translation of the instruction, emitted
  **eagerly** at the point where the effect belongs, which performs only the
  value-level part of the effect — synonymy and restriction, no definitions —
  and generates no JVM code;
- the **real instruction**, free to float by the existing postponement
  machinery, materializing only when some instruction that cannot be postponed
  requires the value, recursively.

The declaration is not a prophecy. It stays where it was emitted. What moves is
the *action*, and postponement already lets actions move with no
inter-instruction constraints.

### 4.1 Why eager, and how early

Synonyms and restrictions must be established before *both*:

- instructions that **consume** them, and
- live instructions that **populate** them.

The second is the one that is easy to miss. An instruction that populates a
value needs the manifest to already agree about what that value *is*, or it
records its register against a different value than the one the rest of the
graph is discussing — which is precisely how 5-13 ends up in a synonym of its
own holding a register nobody else believes in.

### 4.2 What a declaration does when added

Not a replay of `instructionWasAdded`. It contributes membership and
restriction and *no* `Representation` definitions — the ⌛ state that a value
has between being known and being written. The existing manifest already models
this; `L2_DECLARE` makes it explicit in the instruction stream so that a
regeneration reproduces it instead of having to rediscover it.

### 4.3 What it must not do

- **It must not pin registers alive.** Its operands have to be semantic-value
  references, not register reads. A declaration holding a real read operand
  would keep the dead move's source register live forever, trading a manifest
  bug for a register-pressure bug.
- **It must not generate code**, and must not participate in reification.
- **It must not be removed by dead code elimination.** The instruction it
  declares may be removed; the declaration is the surviving carrier of the
  fact.

### 4.4 Stripping

Once every postponement-enabled pass has run, all `L2_DECLARE`s are stripped in
one sweep. De-edge-splitting, register colouring and JVM translation then run
on a graph with no declarations in it, and never need to know about them.

That is what makes the accumulation question moot: declarations do not need a
liveness-based discard rule, because they have a defined end of life. If they
prove expensive before that point, a pass may drop one whose values are dead in
every successor, but nothing depends on it happening.

## 5. What this makes unnecessary

- **Explicit edge transport.** Since declarations are ordinary instructions,
  they are transformed by a regenerator like anything else, and each output
  edge acquires the right manifest by construction. No separate mechanism for
  copying an input edge's manifest to a corresponding output edge is needed.
- **Correspondence between input and output edges.** Rewrites that change the
  shape of control flow are then fine: a multi-way tag branch becoming
  one-direction jumps, `≤` rewritten as `>` or `=` according to the boundaries
  of known restrictions, and elision of edges all of whose paths lead to
  impossible conditions. Eliding jumps to enlarge basic blocks stays available.
- **The `L2_NOP` tombstone** that `removeDeadInstructions` leaves where it drops
  an instruction. That NOP is a declaration with its effect discarded.
- Probably **the base fabrication in `recordDerivation`**. It exists because a
  derived value can be bound while its base is unknown; with the declaration
  carrying the base's synonymy, the base should already be present. The
  restriction seeded from the tag (commit `a3cf993db`) is a narrower patch for
  the same hole and may become redundant.

## 6. Design questions to settle while implementing

1. **Is a postponed instruction just a declaration plus a deferred action?**
   The manifest's postponed instruction already means "this value is known, and
   here is how to produce it when asked". If so, postponement could be expressed
   as `L2_DECLARE` at the original site plus the real instruction wherever it
   materializes, and the two mechanisms become one. Attractive, but a larger
   change than the defect requires.
2. **Ordering constraints among declarations.** They are pure manifest edits, so
   two declarations touching disjoint values commute. The real constraint is
   against other manifest-affecting instructions touching *the same* values.
3. **Non-SSA phases.** A declaration asserts something about semantic values; if
   a later pass reuses a register for a different value, the declaration must
   still describe the value, not the register. Stripping before colouring should
   make this a non-issue, but it is worth checking that no phase between here
   and there rewrites values in place.
4. **What exactly is "a translation of" the declared instruction?** Holding the
   whole `L2Instruction` keeps effect and knowledge together and avoids a second
   language of manifest deltas that would drift from `instructionWasAdded`. But
   the inner instruction's write operands mention registers that may not exist
   in the new graph. Either those operands are rewritten to registerless form
   when the declaration is built, or the declaration holds only the semantic
   content — the write's semantic values and restriction, and the reads'
   semantic values.

## 7. Suggested order

1. Give `removeDeadInstructions` a declaration instead of an `L2_NOP` when it
   drops an instruction. Smallest change that exercises the whole combination
   — no registers, no code, not removable — against the exact failure in
   `testSemanticRestrictionOfSetCreation_1`.
2. Emit declarations generally, at every point a manifest-affecting instruction
   is generated, and confirm that edge manifests survive regeneration
   unchanged. A per-edge comparison of input against output manifests makes a
   good assertion here, and is the invariant this whole design exists to
   establish.
3. Strip declarations after the last postponement-enabled pass, and check that
   de-edge-splitting and register colouring are untouched.
4. Remove what section 5 makes unnecessary.
