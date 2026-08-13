# L2ValueManifest redesign: ValueClass / ValueState / Representation

Status: **in progress.** Steps 1, 2a, 6 and the `ValueClass` re-keying of
step 4 landed, all green under
`compileKotlin` + `SimpleOptimizerTest` (31 tests). Steps 2b–2d are **blocked
on step 4** – see the ordering correction in step 2 and the evidence in
section 9. Next action: step 4, as designed in section 2. Section 10 records a
shortcut that was proposed and **rejected**, and why; section 11 tracks step 4
itself.

There is now a **failing reproducer** for the section 1.1 defect:
`SimpleOptimizerTest.earlyMapsBindingsSemanticRestriction`. It is expected to
fail until step 4 and the remaining specialization work are done, and is the
acceptance criterion for them. Current state of that suite is therefore 31
passing, 1 failing-by-design.

Step 6 was deliberately done **before** step 4: its consumer index can be
tolerant (keyed by `L2SemanticValue`, validated on use), so it does not need
stable class identity.

Note on running `AvailTest`: loading the standard library currently dies at
~24% with `AssertionError: Invalid target of JVMTranslator` from
`L2PcOperand.createAndPushRegisterDump` via `L2_SAVE_ALL_AND_PC_TO_INT`. This
is a **pre-existing, unrelated** defect – `L2_SAVE_ALL_*` has edges to basic
blocks that should have started with an `L2_ENTER_*` instruction – confirmed
unchanged by this work. It is not the postponed-staleness defect of section
1.1. Until it is fixed, `AvailTest` is not a usable gate; use `compileKotlin`
and `SimpleOptimizerTest`.

This document is the durable plan for replacing the `L2Synonym` →
`Constraint` mapping in `L2ValueManifest` with an equivalence-class model. It
records the motivation, the target data model, the step sequence, the
invariants each step establishes, and the evidence behind each decision, so
that the work can be resumed cold.

---

## 1. Motivation

### 1.1 The observable defect

`L2Generator` postpones side-effect-free instructions (`unaryPlus()` records
them in the current manifest's `Constraint`). Narrowing propagation updates
the `TypeRestriction` held in `Constraint`s of related `L2Synonym`s, but it
does **not** narrow the `TypeRestriction`s captured inside the
`L2ReadOperand`s and `L2WriteOperand`s of the *postponed* instructions.

Consequence: a postponed `L2_RUN_INFALLIBLE_PRIMITIVE` sunk below a branch
that narrows one of its arguments to a constant never notices. It should be
rewritten as a constant move, with an `L2SemanticConstant` joining the
synonym, but its read operands still carry the pre-branch restriction, so
`returnTypeGuaranteedByVM` is computed from stale types and the `CanFold`
path in `L2_RUN_INFALLIBLE_PRIMITIVE.emitTransformedInstruction` is never
taken.

### 1.2 The structural cause

`equivalentSemanticValue` (`L2ValueManifest`) has a fast path
(`semanticValue in semanticValueToSynonym`) and then degrades to a **linear
scan of every key**, running the recursive `isEquivalentSemanticValue`
predicate against each. `equivalentPopulatedSemanticValue` is worse: it
scans *and* runs `isPopulated` per candidate. There is a third such scan in
the edge-merge code, and a fourth loop over all keys filtering for
primitive invocations.

`propagateForRestrictionChange` calls these nine times and is itself
recursive through `updateRestriction`. One branch narrowing a boxed value
costs `O(depth x 9 x |semanticValues| x recursive-predicate)`.

**The misses dominate.** Most boxed values have no unboxed-int companion, so
`equivalentSemanticValue(semanticValue.unboxedInt)` scans the entire manifest,
running a recursive predicate against every unrelated semantic value, only to
return `null`. That happens on every boxed narrowing. The hits are rare; the
exhaustive failures are the steady-state cost.

The scan exists for exactly one reason: **derived semantic values are keyed
by an arbitrary representative rather than by the equivalence class.**
`L2SemanticUnboxedInt` holds `privateBoxed`; `L2SemanticExtractedTag` holds
`base`; `L2SemanticObjectVariantId` likewise. When two boxed synonyms merge,
`Int(b1)` and `Int(b2)` remain distinct map keys and nothing merges them, so
the lookup fails and the fallback re-derives the equivalence from scratch,
every single time.

This is congruence closure performed by linear search.

Symptomatic workarounds already in the tree, all of which the redesign
deletes:

- `L2_UNBOX_INT.emitTransformedInstruction` — "Synonyms of ints are tricky,
  so check if there's an int version of a synonym of the source available",
  which maps every boxed synonym member through `::L2SemanticUnboxedInt` and
  filters.
- `L2Generator.readIntNoFail` — "Because of the way synonyms work, the boxed
  form might have synonymous boxed semantic values, without the unboxed form
  having all the same corresponding unboxed values. Do a slower check for
  this case."
- `L2ValueManifest.dynamicAgglomerateSynonym` — the `innerFun(kind, ...)`
  trick, with its comment about "a number of Kotlin type shortcomings, most
  notably the inability of the calling code to introduce fresh genericity
  somewhere other than at a method boundary". This exists solely to launder
  the `K` type parameter on `L2SemanticValue`.

### 1.3 Why not just add an index to the current structures

`L2Synonym` is a value object hashed on its member set, and `constraints` is
keyed by it. Every membership change constructs a *new* `L2Synonym` and
rewires the map (see `agglomerateSynonym`). An equivalence class therefore
has **no stable identity**, so no index into it can be maintained. Giving
classes a stable identity is the prerequisite for everything else, including
the postponed-instruction consumer index needed to fix section 1.1.

---

## 2. Target data model

```
ValueClass          opaque interned identity; a Long id, no mutable state
ValueState          immutable; one abstract value
  members       : Set<L2SemanticValue>        (all boxed - see step 2)
  restriction   : TypeRestriction             (boxed only - see step 3)
  boxed         : Representation<BOXED_KIND>?
  int           : Representation<INTEGER_KIND>?
  float         : Representation<FLOAT_KIND>?
  tag           : ValueClass?                 forward edge to Tag(this)
  variantId     : ValueClass?                 forward edge to VariantId(this)
  derivations   : Set<Derivation>             back edges
Representation<K>   immutable; the register-level facts for one RegisterKind
  definitions   : List<L2Register<K>>
  postponed     : L2Instruction?
Derivation          sealed: TagOf(base) | VariantIdOf(base)
```

Manifest fields:

```
classOf          : MutableMap<L2SemanticValue, ValueClass>
forward          : MutableMap<ValueClass, ValueClass>   union-find
states           : MutableMap<ValueClass, ValueState>
constantOf       : MutableMap<AvailObject, ValueClass>
postponedReaders : MutableMap<ValueClass, MutableSet<ValueClass>>
                   keyed by the class being READ, valued by the classes whose
                   postponed instruction reads it.  Indexed, never scanned.
```

`L2Synonym` becomes a derived view over `ValueState.members`, so existing
call sites keep compiling.

### 2.1 Design constraints and traps

**Sharing.** `Constraint`s — and therefore the `L2Instruction` objects they
hold as `postponedInstruction` — are shared across manifests; the copy
constructor does `constraints.toMutableMap()`. The *same* postponed
instruction object is reachable from both outbound edges of a branch, where
the narrowings are contradictory. Two consequences:

1. Narrowing a postponed instruction must **clone, never mutate in place**.
   `L2ReadOperand.restrict` mutates. `L2Instruction.transformEachRead`
   already clones and is the right primitive.
2. `ValueClass` must be **stateless**. A classic mutable union-find node with
   parent pointers would let a union performed on one edge leak to the other.
   All union state lives in the manifest's `forward` map.

**Stale inter-state references.** `ValueState.tag` / `.variantId` store class
references that go stale when the referenced class loses a union. Resolve
every stored reference through `resolve(c)` (path-compressed walk of
`forward`). `forward` grows monotonically within a manifest; compact it in
`retainSemanticValues` and at block boundaries.

**Congruence is downward only.** `a === b` implies `Tag(a) === Tag(b)`, so
`union` must unify the forward `tag` / `variantId` fields. The converse is
**false** — tags are not injective — so `union` must *never* unify the bases
found in `derivations`. Getting this backwards silently merges unrelated
values.

**`derivations` is a set.** If `Tag(a)` and `Tag(b)` are unioned (say both
narrowed to the same constant tag), the merged class legitimately has two
bases. Usually the set is empty or a singleton.

**The forward field is the hashcons.** There is exactly one possible tag
class per base, so `base.tag` *is* the signature-table entry for `TagOf(base)`.
No separate signature table is needed while primitive invocations are out of
scope.

**Degenerate mode.** When `caresAboutSemanticValues` is false, `classOf` is
null and `states` holds boxed-only representations with no
`tag`/`variantId`/`constantOf`/`postponedReaders` and `postponed == null`.

---

## 3. The union algorithm

```kotlin
fun union(a0: ValueClass, b0: ValueClass): ValueClass
{
    val a = resolve(a0)
    val b = resolve(b0)
    if (a === b) return a
    val sa = states[a]!!
    val sb = states[b]!!
    // Weighted: keep whichever class has more members.
    val (win, lose, sw, sl) = order(a, b, sa, sb)
    val merged = ValueState(
        members = sw.members + sl.members,
        restriction = sw.restriction.intersection(sl.restriction),
        boxed = mergeRep(sw.boxed, sl.boxed),
        int = mergeRep(sw.int, sl.int),
        float = mergeRep(sw.float, sl.float),
        tag = unify(sw.tag, sl.tag),               // downward congruence
        variantId = unify(sw.variantId, sl.variantId),
        derivations = sw.derivations + sl.derivations)
    forward[lose] = win
    states.remove(lose)
    states[win] = merged
    mergePostponedReaders(win, lose)
    renarrow(win)     // the intersected restriction may have tightened
    return win
}

private fun unify(x: ValueClass?, y: ValueClass?) = when
{
    x == null -> y
    y == null -> x
    else -> union(x, y)
}
```

`mergeRep` unions definitions and picks a surviving postponed instruction —
i.e. today's logic in `agglomerateSynonym`, applied per kind.

## 4. The narrowing algorithm

Converted from recursion to a drained worklist. This bounds the depth, gives
one place to assert monotone progress (restrictions only ever intersect), and
provides the single hook for postponed-instruction re-narrowing.

```kotlin
private fun drain()
{
    while (worklist.isNotEmpty())
    {
        val (c0, body) = worklist.removeFirst()
        val c = resolve(c0)
        val old = states[c] ?: continue
        val new = old.restriction.intersection(old.restriction.body())
        if (new == old.restriction) continue        // no progress
        states[c] = old.narrowed(new)
        if (new.isImpossible) impossibleCount++

        // 1. Constant -> union with the canonical constant class.
        new.constantOrNull?.let { k ->
            constantOf[k]?.let { return@let union(c, it) }
            registerConstant(k, c)
        }
        // 2. Representations that can no longer exist.
        if (!new.type.isSubtypeOf(i32)) dropRep(c, INTEGER_KIND)
        if (!new.type.isSubtypeOf(DOUBLE())) dropRep(c, FLOAT_KIND)
        // 3. Derived classes, downward.  Field reads, no searching.
        states[c]!!.tag?.let {
            enqueue(it) { intersectionWithType(tagRange(new)) } }
        states[c]!!.variantId?.let {
            enqueue(it) { variantIdRange(new) } }
        // 4. Derived classes, upward.  Back edges, no unwrapping.
        states[c]!!.derivations.forEach { d ->
            when (d)
            {
                is TagOf -> enqueue(d.base) {
                    restrictionForTagRestriction(new) }
                is VariantIdOf -> enqueue(d.base) {
                    restrictionForVariantId(new) }
            }
        }
        // 5. Postponed consumers -- the section 1.1 defect.
        postponedReaders[c]?.forEach(::renarrowPostponed)
    }
}
```

Step 4 replaces the `L2SemanticExtractedTag` / `L2SemanticObjectVariantId`
unwrapping in today's `propagateForRestrictionChange`, including the stale
comment claiming "we simply don't keep that backward map from id to variant"
(`variantFromId` is called sixteen lines later).

## 5. Fixing the postponed-instruction defect (step 6)

Two tiers.

**Tier 1, emit-time refresh.** At the two places a postponed instruction
becomes real — `L2Generator.ensureDefinedOrEmitMove` and
`L2Instruction.basicForcePostponedTranslationNow` — the clone is already
being made. Before `emitTransformedInstruction()`, re-restrict each read from
the current manifest and intersect the write's restriction with the state's.
`L2Generator.populateForRead` already uses this idiom. This alone makes
`L2_RUN_INFALLIBLE_PRIMITIVE` see narrowed argument types and take its
existing `CanFold` path. It is lazy: nothing between the branch and the use
benefits.

**Tier 2, eager re-narrowing.** Add to `L2Instruction`:

```kotlin
open fun impliedWriteRestriction(
    readRestrictions: List<TypeRestriction>): TypeRestriction

open fun narrowedForManifest(manifest: L2ValueManifest): L2Instruction?
```

`narrowedForManifest` returns `null` when nothing tightened (preserve
identity, as `restrict` and `ConstraintBuilder` both do), otherwise a
`transformEachRead` clone. `L2_RUN_INFALLIBLE_PRIMITIVE` overrides
`impliedWriteRestriction` with the `returnTypeGuaranteedByVM` computation
currently inlined at the top of its `emitTransformedInstruction` — **factor
that into a shared helper** so the eager and emit paths cannot drift. Worth
overriding on `L2_TUPLE_AT_CONSTANT`, `L2_GET_OBJECT_FIELD`, the int
arithmetic ops, and `L2_UNBOX_INT`.

Driven from step 5 of `drain()` via `postponedReaders`. Drive re-narrowing
off **manifest restrictions**, never off the producing instruction's write
operand: then transitive chains (a postponed primitive reading another
postponed primitive's output) resolve for free.

**No new emission logic is required for the fold.** `recordPostponedInstruction`
already refuses to record an instruction whose result is a known constant,
agglomerating the `L2SemanticConstant` instead; and `ensureDefinedOrEmitMove`
already tests `restriction.isConstant -> moveConstant(...)` *before* falling
back to the postponed instruction. Factor that constant-folding branch of
`recordPostponedInstruction` into `installOrFoldPostponed(class, instruction)`
and call it from both record-time and re-narrow-time so they cannot drift.

`postponedReaders` may be a **tolerant** index: validate on use rather than
maintaining it exactly across every synonym mutation. A stale entry costs one
wasted recompute, not a wrong answer.

---

## 6. Step sequence

Each step is independently shippable and testable. Do them in this order.

### Step 1 — instrumentation and safety net (DONE)

Zero semantic change. Establishes measurement and catches latent violations
before the wide refactors.

1. `L2_UNBOX_INT.instructionWasAdded` asserts its source is statically an
   i32. See section 7 for why this is a memory-safety invariant and not
   merely tidiness.
2. Restrict the equivalence scans in `equivalentSemanticValue`,
   `equivalentPopulatedSemanticValue` and the edge-merge site to candidates
   of the same concrete class, when that is provably sound. See section 8
   for the proof.

Deliberately **no** parallel class-partitioned index: `semanticValueToSynonym`
has nine mutation sites, and maintaining a second structure across all of
them is exactly the sort of risk this step exists to avoid. Revisit only if
measurement shows the class-comparison scan is still hot; by then step 2 has
removed the two hottest query shapes anyway.

Regression coverage added to `SimpleOptimizerTest`:
`manifestFindsUnboxedIntAcrossMergedSynonyms` (the cross-representative hit
that the class filter must preserve) and
`manifestReportsNoEquivalentForUnrelatedValues`. The first was verified to
fail when the class filter is inverted; none of the pre-existing L1-level
tests detect that mutation, so this coverage is not redundant.

Verified against `SimpleOptimizerTest` (30 tests, all passing) with
`enableAssertions = true`. The rest of the suite was not run, since compiling
the Avail library still fails on the postponed-instruction staleness defect
that step 6 addresses.

### Step 2 — eliminate the unboxed semantic values

> **Ordering correction (found while doing 2a).** Sub-steps 2c and 2d
> structurally depend on **step 4** and must not be attempted before it. See
> section 9. Step 2a is independent and is done; do step 4 next, then return
> for 2b–2d.

Make `L2ReadIntOperand` / `L2WriteIntOperand` / `L2ReadFloatOperand` /
`L2WriteFloatOperand` and their vector forms hold a **boxed**
`L2SemanticValue`. Delete `L2SemanticUnboxedInt` and `L2SemanticUnboxedFloat`.

Footprint at time of writing: 108 mentions of the two classes across 28
files, plus 108 uses of the `.unboxedInt` / `.unboxedFloat` / `.boxed`
accessors.

The payoff is larger than the map key. With every `L2SemanticValue` boxed,
the class loses its `<K: RegisterKind<K>>` parameter, which is currently
forced through `L2Synonym<K>`, `Constraint<K>`, `ConstraintBuilder<K>` and
most of the manifest API. That deletes `dynamicAgglomerateSynonym` and a pile
of `.cast()` calls, and the compiler finds nearly all the sites for you.

It also means there is no such thing as an int-specific *name*, so `members`
and `restriction` move up from `Representation` to `ValueState`, and
`Representation` shrinks to `definitions` + `postponed`. `postponed` stays
per-representation, because the instruction that materialises the int
register (`L2_UNBOX_INT`) is not the one that materialises the boxed
register — this preserves the definition-isolation between `RegisterKind`s
that the current `Constraint` split was introduced to provide.

**The invasive part.** `L2ReadIntOperand.semanticValue()` starts returning a
boxed value, so `manifest.restrictionFor(read.semanticValue())` becomes
ambiguous at roughly 108 sites. Decide the API split *before* starting:
add `L2ReadOperand.restrictionIn(manifest)` which projects using the
operand's statically-known kind, and make `restrictionFor(sv)` boxed-only.

Sub-order within the step:

- 2a. **(DONE)** Funnel every representation *search* through a manifest-level
  API. Not the operand-side accessors originally planned here – those would
  have been dead API with no callers. Instead `L2ValueManifest` gained
  `intFormOf`, `populatedIntFormOf`, `floatFormOf`, `boxedFormOfInt`,
  `boxedFormOfFloat`, `tagFormOf` and `variantIdFormOf`, and every site that
  constructed an `L2SemanticUnboxedInt`/`Float`/`ExtractedTag`/
  `ObjectVariantId` purely in order to search for it was migrated onto them:
  the whole boxed/int/float/tag cascade in `propagateForRestrictionChange`,
  `L2Generator.readBoxed`/`readIntNoFail`/the type-test path,
  `L2GeneratorInterface.canUnboxInt`, `P_BitwiseAnd`, `NumericComparator`,
  `L2_SAVE_ALL_AND_PC_TO_INT`, `P_TupleSize`, `P_ExtractSubtuple`,
  `P_TupleAt`, `Primitive`, `L2_BIT_LOGIC_OP` and
  `ObjectLayoutVariantDecisionStep`. These seven functions are precisely the
  `ValueState` field reads of step 4, so the migration is forward progress
  independent of whether the classes are ever deleted, and it shrinks the
  step-4 diff.

  Remaining `.unboxedInt`/`.unboxedFloat` uses are *name constructions* (write
  targets, read sources), not searches. They can only go away with 2c/2d.
  Deliberately left alone: the `L2SemanticPrimitiveInvocation`-flavoured
  lookups in `DecisionStep` (out of scope) and the populated-tag search in
  `TypeTagDecisionStep` (single caller, would need an eighth accessor).
- 2b. Migrate remaining non-operand code off direct `L2SemanticUnboxedInt`
  mentions. **Blocked on step 4** for anything that changes what a name means.
- 2c. Change the operands to store boxed semantic values. **Blocked on step 4.**
- 2d. Delete the classes; drop `<K>` from `L2SemanticValue` and `L2Synonym`.
  Worth splitting into 2d-i (delete the classes) and 2d-ii (drop the type
  parameter). **Blocked on step 4.**

### Step 3 — boxed-only `TypeRestriction`

One `TypeRestriction` per `ValueState`, always boxed.

Evidence that the non-boxed forms carry no information:

- `TypeRestriction`'s `init` asserts the flags are *exactly one* of
  `BOXED`, `BOXED|IMMUTABLE`, `UNBOXED_INT`, `UNBOXED_FLOAT` (comment:
  "Mixing boxed/unboxed in a restriction is now forbidden (Feb 2021)"). The
  flags are a discriminator for which representation the restriction is
  *about* — precisely what `Representation<K>` already carries statically.
- `forUnboxedInt()` is a lossy projection: `type ∩ i32`, all four variant
  fields nulled, `IMMUTABLE` dropped, everything else copied. `canBeBottom`
  is asserted false for unboxed.
- `intersection` ANDs the flags and `union` ORs them, both of which would
  trip the `init` assert if applied across kinds — so they are already
  same-kind-only by convention rather than by type.

Consequences to plan for:

- `BOXED_FLAG` / `UNBOXED_INT_FLAG` / `UNBOXED_FLOAT_FLAG` delete.
  `IMMUTABLE_FLAG` stays and becomes unambiguous.
- The lost dynamic kind check is replaced by the only claim with content: at
  the int-operand boundary, assert `restriction.type.isSubtypeOf(i32)`.
- The `type ∩ i32` inside `forUnboxedInt` becomes the invariant
  `state.int != null implies state.restriction.type ⊆ i32`. See section 7.
- Operands still need a projected restriction for `translateToJVM` and the
  `dispatch/` lookup trees. Compute it at emission and freeze it into the
  operand. The manifest stops storing three copies and keeping them in sync.

### Step 4 — `ValueClass` / `ValueState` / `Representation`

Introduce the identity, the state record, `union`, and `drain`. Keep
`L2Synonym` as a computed view so call sites survive. By this point this is a
much smaller change than it would have been, because steps 2 and 3 removed
most of what made `Constraint` awkward.

### Step 5 — derived-class edges

`tag` / `variantId` forward fields and `derivations` back edges. Rewrite
`propagateForRestrictionChange` as the `drain()` of section 4. Delete the
corresponding `isEquivalentSemanticValue` cases.

### Step 6 — postponed instructions

`postponedReaders`, `impliedWriteRestriction`, `narrowedForManifest`,
`installOrFoldPostponed`, and the Tier 1 emit-time refresh. Section 5.

### Step 7 — constants

`constantOf`; delete the two `L2SemanticConstant` cases from
`isEquivalentSemanticValue`.

**Keep `L2SemanticConstant` as the canonical member; do not delete the class.**
`retainSemanticValuesInSynonym` explicitly re-adds `createSemanticConstant`
after intersecting member sets, so a constant class survives cross-edge
intersection even when every other member is dropped; the same
intersect-across-edges pattern appears in the merge code. Deleting the class
would mean adding a new special case to the hairiest code in the file to
replace one just removed.

### Step 8 — variant consistency

Keep the variant sets in `TypeRestriction`. They are **not** redundant: they
are read where no manifest exists — `L2_GET_OBJECT_FIELD.translateToJVM`
chooses `fieldAtIndex` versus `fieldAt` from them, as do
`L2_GET_OBJECT_TYPE_FIELD` and `L2_EXTRACT_OBJECT_VARIANT_ID`; and the
`dispatch/` lookup trees (`InternalLookupTree`,
`ObjectLayoutVariantDecisionStep`, `ObjectTypeLayoutVariantDecisionStep`) are
built and cached per method from bare restrictions, entirely outside L2
generation. JVM translation runs long after manifests are gone.

The manifest's `VariantId(x)` class is the *inference* carrier; the
restriction's variant sets are the *durable* carrier. What can be deleted is
the ad hoc reconstruction in `propagateForRestrictionChange` that converts a
narrowed variant-id range back into `intersectionWithObjectVariant`. Replace
it with an invariant: the manifest is the sole writer of variant information
into restrictions, the two are reconciled at every update, and `check()`
asserts consistency.

### Out of scope for now

`L2SemanticPrimitiveInvocation`. Its equivalences will eventually depend on
declared theorems with a representation/evaluation ranking (`a+a+a` to `a*3`
unless `b = a+a` is already computed; `{a,a,a} = {a}`; `|{a,a,a}| = 1`). That
work is e-graph saturation with a cost model for extraction, where
`ValueClass` is the e-class and `ValueState` is the e-class data, and it
needs a real signature table `Map<Pair<Primitive, List<ValueClass>>,
ValueClass>` rehashed on union. Steps 4 and 5 are the foundation it requires;
do not try to anticipate it further than that.

---

## 7. The i32 safety invariant

`L2_UNBOX_INT` generates `A_Number.extractInt`, which **faults at runtime**
if the value is not actually an int. The guarantee is established separately,
by an `L2_JUMP_IF_KIND_OF_OBJECT` i32 test with an unconditional extraction
on the edge where it holds. (This replaced an older fused test-and-extract
instruction that was structurally problematic; the split form made requesting
code splitting much easier, and `L2_JUMP_IF_KIND_OF_OBJECT`'s JVM translation
handles the i32 test efficiently.)

Therefore:

> **If an int representation exists for a value, that value's boxed
> restriction must be a subtype of i32.**

This is a memory-safety invariant, not a tidiness one. Today it is
*unenforced*: the `type.typeIntersection(i32)` inside `forUnboxedInt` silently
repairs any violation in the derived int restriction while leaving the actual
extraction unguarded. The existing `assert(restriction.containedByType(i32))`
in `L2Generator.readIntNoFail` is tautological — `restriction` has already
been through `forUnboxedInt()` on the path that needs checking.

Step 1 adds the real check in `L2_UNBOX_INT.instructionWasAdded`, **after**
`super.instructionWasAdded(manifest)`. Order matters: `super` is what drives
each operand's own `instructionWasAdded`, and `L2ReadOperand.instructionWasAdded`
re-restricts the read from the manifest. Asserting before `super` would test
a stale restriction captured when the operand was built, which for the
`L2Generator` unbox site predates the very type-test branch that establishes
the guarantee.

Note in passing that `destination.restrict { source.restriction().forUnboxedInt() }`
on the first line of that method reads the *stale* source restriction for the
same reason, so the destination can receive a wider int restriction than
necessary. That is an instance of the section 1.1 defect and is left for
step 6.

## 8. Soundness of the class-restricted scan (step 1)

**Claim.** If `q` is absent from `semanticValueToSynonym` and `q` is not an
`L2SemanticConstant`, then for every candidate `other`,
`isEquivalentSemanticValue(q, other)` implies `q` and `other` have the same
concrete class.

**Proof**, by cases over `isEquivalentSemanticValue`:

- `q == other`. `L2SemanticValue.equals` delegates to `equalsSemanticValue`,
  and every override requires `other is <that exact class>`. All the classes
  concerned are final. Same class.
- Kind mismatch returns false.
- The shared-synonym test requires `q in semanticValueToSynonym`. Excluded by
  hypothesis.
- `UnboxedInt`/`UnboxedInt`, `UnboxedFloat`/`UnboxedFloat`,
  `PrimitiveInvocation`/`PrimitiveInvocation`, `ExtractedTag`/`ExtractedTag`,
  `ObjectVariantId`/`ObjectVariantId`. Same class in each.
- `q is L2SemanticConstant` may match an `other` of any class. Excluded by
  hypothesis. **This is why the constant probe must be exempted.**
- `other is L2SemanticConstant` requires `hasSemanticValue(q)`. Excluded by
  hypothesis.

Since candidates are filtered but their relative order is preserved, and any
*matching* candidate is necessarily in the retained subset, the element chosen
by `firstOrNull` is identical to before. The documented licence to return "an
arbitrary (and not necessarily stable) one" is not even exercised.

Note the guard is `q !in semanticValueToSynonym`, which is stricter than
`equivalentPopulatedSemanticValue`'s own `isPopulated(q)` fast path: a `q`
that is present but unpopulated still needs the full scan, because the
shared-synonym test can then match across classes.

The edge-merge site in `populateFromIntersection` has already established
that *all* of its probes are absent (`values.firstOrNull(::hasSemanticValue)`
returned null), so the same argument applies with the candidate class set
taken over all probes.

## 9. Why 2c/2d depend on step 4

The premise of 2c is that an `L2ReadIntOperand`/`L2WriteIntOperand` names a
**boxed** `L2SemanticValue`, with the operand's static `RegisterKind` saying
which representation is meant. Today the manifest cannot express that.

The only thing currently keeping a value's boxed facts apart from its int
facts is that they are named by two *different* semantic values, `x` and
`Int(x)`, which therefore land in two different synonyms:

- `semanticValueToSynonym` maps one `L2SemanticValue` to one `L2Synonym`.
- `constraints` maps one `L2Synonym` to one `Constraint`, holding exactly one
  `TypeRestriction` and one `List<L2Register<K>>`.
- `check()` asserts synonyms are single-kind.
- `TypeRestriction`'s `init` asserts the flags are exactly one of `BOXED`,
  `BOXED|IMMUTABLE`, `UNBOXED_INT`, `UNBOXED_FLOAT`.

Collapse `Int(x)` into `x` and both facts have to live in one constraint. The
failure is concrete and immediate in `recordDefinitionNoCheck`: on the branch
commented "This is a new RegisterKind for an existing semantic value" it does

```kotlin
updateRestriction(pickSemanticValue) { writer.restriction() }
```

— replacing the boxed restriction wholesale with the int one — and then
appends the writer's `L2IntRegister` to the definitions list of what is a
`Constraint<BOXED_KIND>`. Both are corrupt, and no local fix repairs them,
because the data model has one slot where two are needed.

`ValueState` with a per-`RegisterKind` `Representation` is exactly the missing
Note that a per-kind `Representation` is emphatically **not** a return to
mixing register kinds within one constraint. That was a previous incarnation
and caused problems; keeping definitions isolated per `RegisterKind` is a
requirement, and `Representation` is what enforces it structurally. (The
surviving KDoc on `Constraint.definitions` describing mixed kinds is vestigial
from that incarnation and has been corrected.)

A cheaper-looking alternative – rekeying the manifest by
`(semanticValue, kind)` pairs – is rejected: it is most of the risk of step 4
(touching every synonym mutation site) with none of the payoff, since it
provides no stable class identity, no `union`, and no field-based propagation.

**Therefore: do step 4 next, then 2b–2d become straightforward.** Step 4 is
also the step that unblocks step 6, which is what currently prevents the Avail
library from compiling and therefore prevents 2c/2d from being tested at all.

## 10. Rejected: making `L2Synonym` itself identity-stable

An earlier revision of this document proposed skipping `ValueClass` by making
`L2Synonym` hash and compare by identity, with a member set that grows in
place. **This is wrong and must not be attempted.**

`L2Synonym` objects are shared between the manifests of a control flow graph.
Making one mutable makes *per-manifest state* shared: growing a synonym's
member set on one edge of a branch would silently change membership on every
other manifest that references it. That is precisely the hazard section 2.1
already forbids for `ValueClass` – "a classic mutable union-find node with
parent pointers would let a union performed on one edge leak to the other."

The proposal collapsed a distinction that is the entire point of the
`ValueClass` / `ValueState` split:

- **Identity** – "which equivalence class is this?" – must be stable *and*
  safely shareable between manifests.  It can be, precisely because it holds
  no state.
- **State** – members, restriction, definitions, postponed instruction – must
  be **immutable** and **per-manifest**, reachable only through that
  manifest's own `states` map, and replaced copy-on-write exactly as
  `Constraint` / `ConstraintBuilder` do today.

`L2Synonym` conflates the two: it *is* the membership and it *is* the map key.
That is why it cannot be made identity-stable by making it mutable. The
`ValueClass` / `ValueState` mechanism of section 2 exists to separate them,
and `ValueState.members` is immutable – see the `<<immutable>>` marker in the
class diagram.

### 10.1 The brief window where the manifest's invariants do not hold

Within an `L2ValueManifest` there is a short lexical/temporal interval where
the synonym invariants legitimately do not hold: just long enough to update
both `semanticValueToSynonym` and `constraints` so their memberships
correspond again. The invariant is the `check()` assertion

```kotlin
assert(semanticValueToSynonym!!.values.toSet() == constraints.keys)
```

— the set of synonyms reachable from the first map must be exactly the key set
of the second. This window is deliberate, and it is narrow.

The redesign does not remove the window, since `classOf` and `states` are
still two maps that must be updated together. What it must preserve is that
the window stays **as short and as local as it is now**: confine the paired
update to a single private helper, and do not call `check()` from inside it.
Widening that window – for instance by letting a public operation return with
the two maps disagreeing – would be a regression, and is the kind of thing the
`deepManifestDebugCheck` assertions exist to catch.

### 10.2 Two sites that prove the point

The precondition for the rejected shortcut was that nothing depends on two
distinct `L2Synonym`s with equal member sets comparing equal. Checking it
found two places that do, both in `L2ValueManifest`:

1. In the primitive-invocation grouping code, argument semantic values that
   are absent from the manifest get a **deliberately unregistered** singleton
   synonym, `semanticValueToSynonymOrElse(it) { L2Synonym(setOf(it)) }`, and
   the resulting list is used as a key: `map.computeIfAbsent(argumentSynonyms)`.
   Value-equality is what makes two equal arguments group together. Identity
   hashing would make every freshly constructed singleton distinct and silently
   destroy the grouping.

2. In `dynamicAgglomerateSynonym`'s helper, after
   `introduceSynonym(newSemanticValues, restriction)` the code recovers the
   just-registered synonym by **rebuilding an equal one**:
   `existingSynonyms.add(L2Synonym(newSemanticValues))`. Under identity, that
   object would not be a key of `constraints`, and the subsequent
   `constraints[it]!!` would fail.

Both are handled cleanly by the design in section 2, which is a further reason
to prefer it:

- Site 1 keeps working untouched, because `L2Synonym` remains an immutable
  value object. Only the *keying* of the manifest's maps moves to `ValueClass`.
- Site 2 becomes an explicit lookup of the class for one of the new semantic
  values, which is what it meant all along.

### 10.3 Shape of the change

`L2Synonym` needs no modification. It stays immutable, and sharing it between
manifests stays safe precisely *because* it is immutable. Membership changes
produce a **new** `L2Synonym` inside a **new** `ValueState`, replacing the
entry for the **same** `ValueClass`:

```
ValueClass   stateless identity; default identity equality
ValueState   immutable: synonym (membership), definitions, restriction,
             postponed
classOf      MutableMap<L2SemanticValue, ValueClass>   (was semanticValueToSynonym)
forward      MutableMap<ValueClass, ValueClass>        (per-manifest union-find)
states       MutableMap<ValueClass, ValueState>        (was constraints)

semanticValueToSynonym(sv) == states[resolve(classOf[sv])]!!.synonym
```

Scope in `L2ValueManifest`: 13 sites index `constraints` by synonym, 6
construct an `L2Synonym`, and 34 read membership via `semanticValues()`.

**Determinism.** `ValueClass` uses identity equality, so iteration order
must not depend on identity hash codes, which vary between JVM runs and would
make code generation nondeterministic. Kotlin's `mutableMapOf()` /
`mutableSetOf()` already give insertion-ordered `LinkedHashMap` /
`LinkedHashSet`, so the default is correct; the rule is simply never to
substitute an explicit `HashMap`/`HashSet` for a collection keyed by
`ValueClass`.

**Lazily materialized synonym view.** Give `ValueState` a nullable
`cachedSynonym` slot rather than building the `L2Synonym` eagerly. Most states
are never asked for their synonym, and the view is only needed at the
boundary where existing callers ask for one. The slot is also robust to a
later decision to make `ValueState` mutable: replacing the members set simply
nulls the cache.


## 11. Step 4 progress

### 11.1 Done: `Constraint` carries its own membership

`Constraint` gained a `members: Set<L2SemanticValue<K>>` property and a
lazily materialized `cachedSynonym` slot exposing an `L2Synonym` view. All
membership now comes from the *value* of the `constraints` map rather than
from its *key*, which is what frees the key to become a pure identity.

Mutating `cachedSynonym` is safe despite constraints being shared between
manifests, because it is a pure memoization of immutable data: every
computation of it yields an equal result. If `members` is ever made
replaceable, null the slot at the same time.

Three sites reused a `Constraint` under a *different* synonym and would have
carried stale membership across; they now rebuild:

- `clearPostponedInstructions`
- `extendSynonym`, which moved a constraint from the old synonym to the merged
  one
- `retainSemanticValuesInSynonym`, which refiled a constraint under a reduced
  synonym

`check()` now asserts `constraint.members == synonym.semanticValues()` under
`deepManifestDebugCheck`. That assertion passes across the whole suite, which
is the evidence that the rebuild sites are complete.

### 11.2 Done: the key is now `ValueClass`

`L2ValueManifest` now holds:

```
classOf : MutableMap<L2SemanticValue<*>, ValueClass>?
forward : MutableMap<ValueClass, ValueClass>
states  : MutableMap<ValueClass, Constraint<*>>
```

with `resolve` doing a path-compressed walk of `forward`, and `classOrNull` /
`classFor` / `stateOrNull` / `bind` / `forwardClass` as the only ways in.
`Constraint` is unchanged apart from the `members` and `cachedSynonym` of
11.1, and the public `semanticValueToSynonym(sv)` keeps its signature,
answering `stateOrNull(sv)?.synonym`.  The tautological 11.1 assertion is
retired; `check()` now asserts instead that every class reachable from
`classOf` resolves to a live key of `states`.

**How it transformed.** Three operations got *simpler*, which is the sign the
split was the right one:

- `extendSynonym` used to remove the constraint from the old synonym key and
  re-file it under a freshly constructed one.  The class now keeps its
  identity and only its state is replaced.
- `retainSemanticValuesInSynonym` likewise shrinks a class in place rather
  than deleting and re-adding it under a new key.
- `privateMergeSynonyms` gained an `if (class1 === class2) return false`
  early exit, which the synonym-keyed version could not express - it could
  only compare membership.

Both merge sites now forward: `agglomerateSynonym` elects a survivor from the
existing classes and absorbs the rest, and `privateMergeSynonyms` has class1
absorb class2, so anything still holding a merged-away class resolves through
`forward`.

`isEquivalentSemanticValue`'s shared-synonym test was two map lookups compared
with `===`; it is now `classOrNull(a) === classOrNull(b)`, which is the same
test but says what it means.

Verified behaviour-preserving: 31 of 32 `SimpleOptimizerTest` tests pass, the
sole failure being the `earlyMapsBindingsSemanticRestriction` reproducer,
which is expected to fail until the specialization work is finished.  This is
with `deepManifestDebugCheck` enabled throughout.

### 11.3 Next

- Re-key `postponedReaders` (step 6) from `L2SemanticValue` to `ValueClass`,
  upgrading it from tolerant to exact, and prune it in `forwardClass`.
- Rewrite the `dynamicAgglomerateSynonym` helper site described in 10.2 as an
  explicit class lookup instead of rebuilding an equal `L2Synonym`.
- Then `ValueState` with per-kind `Representation`s, which is what 2b-2d
  need.

## 12. The regenerator path does not need the emit-time refresh

`L2Instruction.generateReplacement`'s default body and the tail of
`basicForcePostponedTranslationNow` are the same two lines,
`cloneFor(this).run { emitTransformedInstruction() }`, and only the latter
gained a `refreshReadRestrictionsFrom`.  That asymmetry is correct.

**Who drives each.**  `generateReplacement` has exactly one caller:
`L2Optimizer.replacePlaceholderInstructions`, one of seven
`regenerateGraph` passes.  The seven, with their `GenerationMode`:

| Pass | Mode | Transformer |
|---|---|---|
| `removeDeadInstructions` | inherited | either |
| `doCodeSplitting` | `BySemanticValue` | semantic |
| `postponeConditionallyUsedValues` | `BySemanticValue` | semantic |
| `replacePlaceholderInstructions` | `BySemanticValue` | semantic |
| `insertPhiMoves` | `ByRegister` | register |
| `replaceRegistersByColor` | inherited | register |
| `removeUselessBranches` | `WithFixedRegisterMap` | register |

**Why there is no staleness.**  A regenerator copies each instruction from the
old graph into the new one by *rebuilding every operand*, and rebuilding is
where it consults the manifest.  `OperandSemanticTransformer`, selected exactly
when the mode is `BySemanticValue`, builds each read as

```kotlin
val equivalent = mapReadSemanticValue(operand.semanticValue())
currentOperand = L2ReadBoxedOperand(
    equivalent,
    currentManifest.restrictionFor(equivalent)
        .intersection(operand.restriction()))
```

and does the same for `L2ReadIntOperand` and `L2ReadFloatOperand`.  That *is*
the refresh, applied earlier and in a different place.  By the time
`generateReplacement` runs, the reads already carry the new manifest's
knowledge.

`OperandRegisterTransformer`, used for `ByRegister` and
`WithFixedRegisterMap`, keeps `operand.restriction()` verbatim.  That is also
correct: in those late passes the graph is held together by registers, the
manifest does not track semantic values, and there is nothing to refresh
against.  `refreshReadRestrictionsFrom` early-returns on exactly that
condition, so the two paths agree.

A postponed instruction has no rebuild step - it is stored verbatim in a
`Constraint` and later cloned - which is why it needed an explicit refresh.
Same requirement, different mechanism.

**One real wart, left alone.**  `replacePlaceholderInstructions` does

```kotlin
basicTransformInstruction(sourceInstruction)
    .cloneFor(this@regenerateGraph)
    .run { generateReplacement(sourceInstruction) }
```

and the default `generateReplacement` then does `cloneFor(this)` again, so any
instruction that does not override it is cloned twice.  Harmless, but
wasteful and confusing to read.  Removing one of the clones means checking the
four overriders (`L2ConditionalJump`, `L2_PHI`, `L2_VIRTUAL_CREATE_LABEL`,
`L2_EXTRACT_OBJECT_TYPE_VARIANT_ID`), each of which relies on `cloneFor`
having set the target block and adjusted the operands, so it is not a pure
deletion.

## 13. The derivation defect, and the decision about dead code

### 13.1 What actually goes wrong

Loading the library dies around 16% during code splitting, because a manifest
holds `Int(Tag(6-13))` while knowing nothing about `6-13`. Code splitting
consults the tag to decide what to duplicate, so it misbehaves a long way from
the damage.

Two graphs were compared: the pre-split CFG and the code-split copy. Checking
**per edge manifest** rather than globally, exactly 2 of 32 manifests in the
*pre-split* graph are already inconsistent, and they are the two outgoing edges
of the first tag test – precisely the ones splitting later consumes:

```
112..112, BottomType (SUCCESS)
  〖Outer#1(elements) & 6-3 & ⌛Constant(⊥)〗              <- no 6-13
  〖Int(Tag(6-13)) & Int(Tag(6-3)) & ⌛Int(Constant(112))〗

107..107, TupleType (FAILURE)
  〖Outer#1(elements) & 6-3〗                              <- no 6-13
  〖Int(Tag(6-13)) & Int(Tag(6-3)) & ⌛Int(Constant(107))〗
```

The culprit is `L2Optimizer.removeDeadInstructions`, in phase
`REMOVE_DEAD_CODE_AFTER_POSTPONEMENTS_1`. Its `else` branch emits a diagnostic
`L2_NOP` and touches the manifest not at all, so every semantic value the
omitted instruction would have written simply ceases to exist – including its
membership in a synonym that otherwise survives. The pre-split graph contains
the tombstone:

```
// Omitted: MoveBoxed →r71[5-28 & 6-13] ← @r17[Outer#1(elements)]:Meta
```

That move is what carried `6-13` into the outer's synonym. `Int(Tag(6-13))` was
written by a different, live instruction, so it survived. Tag without base.

### 13.2 Decision: do not "fix" dead code elimination

The tempting fix – have the `else` branch re-agglomerate the omitted write's
semantic values – is **rejected**. Dropping a move whose target is unused is
not merely harmless, it is desirable: it reduces the weight of the manifest,
which would otherwise carry every dead slot value through every merge. The
asymmetry is entirely on the derivation side, and that is where it gets fixed.

### 13.3 Interim tripwire

`check()` gained `checkDerivedValuesHaveTheirBases`, asserting that no synonym
mentions a derived semantic value whose base is absent. It fires in **8 of 32**
`SimpleOptimizerTest` cases, turning a 16%-into-a-library-load failure into
several second-long reproductions. One is starker than the 6-13 case: a
*singleton* synonym `[Int(Tag(4-5))]`, a tag with no base and no companion.

Those 8 tests are expected to stay red until the structural change lands; the
tripwire is the progress meter. **Remove it once derived values are structural
edges**, since the inconsistency stops being representable.

Also improved, since the original stack was being lost: `L2Optimizer.optimize`
now prints the failing phase and the whole trace rather than just the message,
and `L1Translator`'s debugging catch was widened from `Exception` to
`Throwable` so that assertion failures – which are `Error`s – stop at the
breakpoint placed for them instead of slipping past. Note that an internal
failure deliberately does **not** become a fiber termination, so it will not
reach JUnit as itself; stderr plus the phase name is the intended diagnostic
route.

### 13.4 Landed: derivation as real edges

`L2ValueManifest` now carries

```
tagOf       : MutableMap<ValueClass, ValueClass>   base -> Tag(base)
variantIdOf : MutableMap<ValueClass, ValueClass>   base -> VariantId(base)
derivedFrom : MutableMap<ValueClass, ValueClass>   derived -> base
```

maintained in `bind`, which is the single choke point every class membership
passes through, so the edges cannot drift out of step with the memberships they
describe. `forwardClass` folds a merged-away base's edges into the survivor's,
merging the two derived classes when both exist – downward congruence only,
since neither tags nor variant ids are injective. `tagFormOf` and
`variantIdFormOf` now traverse an edge, falling back to the old search while the
edges populate.

### 13.5 Next

- Use the edges to make the inconsistency unrepresentable: a derived class must
  not outlive its base. Either retain the base's membership when a derived
  class is live, or drop the derived class with it.
- Then move the edges onto `ValueState` proper, along with per-`RegisterKind`
  `Representation`s.
- Then the goal stated for this work: **only boxed `L2SemanticValue`s
  participate in synonyms and appear in read/write operands**, even when an
  unboxed representation is the one being accessed. The operand's static
  `RegisterKind` selects the representation; the semantic value stops carrying
  it. That is what finally deletes `L2SemanticUnboxedInt` and
  `L2SemanticUnboxedFloat`, and with them the `<K>` parameter on
  `L2SemanticValue`.
