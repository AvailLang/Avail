# Variable Elision

Level Two code avoids creating the variable object for a local variable of
the function being optimized until something could observe that object.  An
*elided* local exists only as the values in registers that it would hold:
its creation and its writes are postponed, and a read of it becomes a move of
the value most recently written.  The variable is created when something needs
the variable object itself, such as the frame's continuation being reified
and then made immutable or shared.

Two distinct hazards end a local's elision, and this document names them
separately:

- A local is *aliased* when the code hands out the variable itself, rather
  than its value: to close a function over it, to put it in a tuple, or to
  pass it as an argument.  Other code may then read or write the same
  variable, so its accesses must stay ordered against that code.  Aliasing is
  decided statically, before translation (see [Aliased
  locals](#aliased-locals)), and an aliased local is never elided.
- A variable has *escaped* when it has become shared or has acquired a
  reactor.  Its value can then change, or a write to it must run code, outside
  the knowledge of L2, so the fast unescaped accesses are no longer valid (see
  [Translation](#translation)).

The governing invariant is that **L1 code only ever sees real variables**.
Any continuation that would expose an elided local to the L1 interpreter, or
to another fiber, first creates the missing variables and switches its own
resumption to the L1 interpreter.

## Scope

Elision applies to the local variables of a raw function (the slots after
its arguments), in chunks that the [L1Translator] and the L2 optimizer
produce.  The unoptimized [DefaultL1Chunk] and the [L2SimpleChunk] create
every local up front, as does the optimizer when
`L1Translator.enableVariableElision` is `false`.  Even when elision is
enabled, an aliased local is not elided.

## Aliased locals

Before translating a raw function, the [L1Translator] scans its nybblecodes
for `L1_doPushLocal` and `L1_doPushLastLocal` applied to a local slot.  Those
are the only L1 operations that push the variable itself, so the locals they
name are exactly the aliased ones.  The decision covers the whole function:
a local pushed on any path is aliased on every path, from the start.

An aliased local is created at the head of the function's body, before the
interrupt check, by a `Create x := nil` that carries `nil` in place of a
placeholder.  Such a `Create` is never postponed, so it never reaches
`SaveAll`, and nothing ever stands in for the variable.  Every read and write
of an aliased local uses the general variable instructions,
[L2_GET_VARIABLE], [L2_GET_VARIABLE_CLEARING_IF_MUTABLE], and
[L2_SET_VARIABLE], none of which is ever postponed.  So each access happens
where the L1 code put it, in order with any call that might run code holding
the same variable, and with any read of the variable taken back out of a
tuple.  Those instructions also handle a shared variable and write reactors
themselves, so `Check` never lists an aliased local.

## Instructions

The mechanism is carried by ordinary L2 instructions that the postponement
machinery knows how to transform.  The diagrams below use the shorthand on
the left.

- **`Create x := y`** — [L2_CREATE_VARIABLE].  Create local `#n`, initialized
  to `y`, or unassigned if `y` is `nil`.  It also carries the placeholder for
  local `#n` (see [Placeholders](#placeholders)), to stand in for the
  variable if the creation never happens.  It can be postponed.
- **`Set x := y`** — [L2_SET_UNESCAPED_LOCAL_VARIABLE].  Write `y` into `x`
  with no type check and no write reactors, which is valid only while `x` is
  neither shared nor reactive.  It produces `x` again under a new semantic
  value, so every later use of the local is ordered after the write.  It can
  be postponed.
- **`v ← Get x`** — [L2_GET_UNESCAPED_LOCAL_VARIABLE].  Read `x` into `v`,
  branching on whether `x` is assigned, and produce `x` again.  It branches,
  so it is never postponed.
- **`v ← GetClearing x`** —
  [L2_GET_AND_CLEAR_IF_MUTABLE_UNESCAPED_LOCAL_VARIABLE].  Like `Get`, but
  also clear `x` if it is mutable.  The compiler emits the clearing read only
  for the last read of a local (`AvailGetLocalVariable.canClear`).
- **`Check x, …`** — [L2_CHECK_ESCAPED_LOCALS].  If any listed local has
  become shared or has acquired a reactor, fall back to the L1 interpreter.
- **`SaveAll`** — [L2_SAVE_ALL_AND_PC_TO_INT].  Begin reifying this frame,
  capturing its live registers in an [A_RegisterDump], along with how to
  create each elided local (its `dirtyLocals` and `dirtyLocalIndices`).
- **`CreateContinuation`** — [L2_CREATE_CONTINUATION].  Build the frame's
  continuation from the dump.
- **`Invoke`** — [L2_INVOKE] or [L2_INVOKE_CONSTANT_FUNCTION]: a call that is
  not inlined.
- **`Return`** — [L2_RETURN].

## Placeholders

Each elided local has a placeholder, an instance of
[VariablePlaceholderDescriptor] that records the local's index and variable
type, compares by identity, and prints as `Elided local #n`.  A continuation
slot holds a placeholder where the real variable has not been created.

The [L1Translator] creates the placeholders at the head of the function's
body.  It binds each local's slot to its placeholder, emits the interrupt
check, and only then emits one `Create x := nil` per local.  A continuation
reified to service that interrupt therefore holds placeholders for every
local.  Anywhere else, a `Create` that is still postponed when the frame is
reified is replaced on the reification path by its placeholder (see
[Reification](#reification)).

## Translation

- The L1 operations that read and write an elided local translate to `Get`,
  `GetClearing`, and `Set`.  Those of an aliased local translate to the
  general variable instructions instead (see [Aliased
  locals](#aliased-locals)).
- After an operation that might make a created local shared or give it a
  reactor, the translator emits `Check` over every elided local slot that is
  not bound to a constant.  (A slot bound to a placeholder, or to `nil` after
  a clearing read, holds no variable to check.)  An elided local can have
  been created only because something needed the variable itself, such as a
  `Get` whose value might be unassigned.  Those operations are:
  - every call that is not inlined;
  - every inlined primitive that has the flag
    `Primitive.Flag.MightMakeEscapedVariableShared`, such as `P_SetValue`,
    `P_Fork`, and `P_AddWriteReactor`;
  - every write to a variable that is a literal of the code, which is always
    shared, so it may share anything written into it.

A local that has become shared is open to reads and writes from other fibers,
so L2 can no longer track its value in registers.  A local with a reactor
must run that reactor on each write, which `Set` does not do.  Either way,
`Check` falls back to the L1 interpreter ([L2_FALL_BACK_TO_L1]), which
creates any locals still elided and uses only real variables from then on.

## Postponement

A side-effect-free instruction with a single output can be *postponed*: the
regenerator records it in the edge's [L2ValueManifest] instead of emitting it,
and emits it only when something reads its output.  `Create` and `Set` are
postponable, and the rules below say what happens when one of them, still
postponed, reaches another instruction.

The initial translation by the [L1Translator] applies these rules, and so does
every regeneration that tracks semantic values, such as code splitting.  There
is no separate postponement pass.

The merging of a `Create` or `Set` with a following `Set`
(`L2Instruction.rewritePostponed`) happens only on the edges into a control
flow merge (`L2ValueManifest.populateForMerge`), where both are still
postponed.  Elsewhere the `Set` stays postponed on top of the instruction that
produced its variable.

### Postponed creation

```mermaid
---
title: A postponed Create reaching another instruction
---
flowchart LR
 Set["Create x := y<br>Set x := z"]
 Set'["Create x := z<br><i>(at a merge, unless<br>z is from x itself)</i>"]
 Set .-> Set'
 Get["Create x := y<br>v ← Get x"]
 Get'["Move v ← y<br>go to success<br><i>(failure: y nil)</i><br>Create x := y"]
 Get .-> Get'
 Clear["Create x := y<br>v ← GetClearing x"]
 Clear .-> Clear'["Move v ← y<br>go to success<br>Create x := y"]
 Check["Create x := y<br>Check x, w"]
 Check'["Check w<br>Create x := y<br><i>(no Check if w is empty)</i>"]
 Check .-> Check'
 Call["Create x := y<br>Invoke / Return<br><i>(x not used)</i>"]
 Call'["Invoke / Return<br>Create x := y<br><i>(dead after Return)</i>"]
 Call .-> Call'
 Use["Create x := y<br>… x … <i>(x itself used)</i>"]
 Use .-> Use'["Create x := y<br>… x …<br><i>(emitted: x is needed)</i>"]
```

When the value `y` might or might not be assigned, the `Get` reads the
variable, which forces the `Create` to be emitted first.  After a
`GetClearing`, the postponed creation keeps its initial value: the clearing
read is the local's last read, so nothing observes the difference, and the
creation is never emitted unless something later needs the variable itself.

### Postponed assignment

A postponed `Set` may follow a postponed `Create` or another postponed `Set`
until they reach a control flow merge, where they merge into one.

```mermaid
---
title: A postponed Set reaching another instruction
---
flowchart LR
 Set["Set x := y<br>Set x := z"]
 Set'["Set x := z<br><i>(at a merge: the first<br>write is unobserved)</i>"]
 Set .-> Set'
 Get["Set x := y<br>v ← Get x"]
 Get'["Move v ← y<br>go to success<br><i>(failure: y nil)</i><br>Set x := y"]
 Get .-> Get'
 Check["Set x := y<br>Check x"]
 Check .-> Check'["Set x := y<br>Check x<br><i>(emitted: x already exists)</i>"]
 Call["Set x := y<br>Invoke / Return"]
 Call'["Invoke / Return<br>Set x := y<br><i>(nothing else<br>sees x)</i>"]
 Call .-> Call'
```

Like any postponed instruction, a `Set` is also emitted as soon as anything
reads the variable it produces.  An elided local is never aliased, so no
invoked code can read or write it, and a `Set` may pass an `Invoke` or a
`Return`.  After an `Invoke`, the `Check` that follows reads the variable,
which emits the `Set`; after a `Return`, the `Set` is dead.

### Reification

`SaveAll` has two outgoing edges: the *fall-through* edge, which goes on to
build and store the frame's continuation, and the *reference* edge, where
execution resumes when that continuation is returned into while still
mutable.  A postponed `Create` follows both.

```mermaid
---
title: A postponed Create reaching SaveAll
---
flowchart LR
 subgraph Before["&nbsp;"]
  direction TB
  BeforeHead["Create x <i>(local #n)</i> := y<br>SaveAll"]
  BeforeRef["…"]
  BeforeFall["…<br>CreateContinuation<br>&lt;… x …&gt;"]
  BeforeHead ..->|reference| BeforeRef
  BeforeHead -->|fall-through| BeforeFall
 end
 subgraph After["&nbsp;"]
  direction TB
  AfterHead["SaveAll<br><i>dirtyLocals[n] = y,<br>when y is not nil</i>"]
  AfterRef["Create x := y<br><i>(still postponed)</i><br>…"]
  AfterFall["`…
  CreateContinuation
  &lt;… placeholder #n …&gt;
  Create x := y
  *(still postponed, likely unused)*`"]
  AfterHead ..->|reference| AfterRef
  AfterHead -->|fall-through| AfterFall
 end
 Before .-> After
```

Along the fall-through edge, the `Create` is dropped and the local's value is
bound to the placeholder, so the continuation holds the placeholder in that
slot.  If the creation's initial value is not `nil`, `SaveAll` records the
pair (local index, register holding the value) in its `dirtyLocals`, choosing
a register that is already live (int, then float, then boxed), else the
constant, else the producer of a postponed value.  A local that has not yet
been assigned is not recorded; it will be created unassigned.  The register
dump keeps these pairs, encoded by `A_RegisterDump.encodeLocalValue`.

Along the reference edge the creation stays postponed, so a frame that is
resumed while its continuation is still mutable continues in L2 with its
locals still elided.

## Creating elided variables

`ContinuationDescriptor.createElidedVariables` creates a continuation's
elided locals.  It runs:

- when the continuation becomes immutable or shared, from
  `o_MakeImmutableInternal` and `o_MakeSharedInternal`;
- when the interpreter returns into a reified continuation whose chunk has
  been invalidated, or while a debugger is attached
  (`Interpreter.checkValidity`).

A mutable continuation belongs to its fiber, and is returned into at most
once along the path that created it, so L2 can restore its registers and
carry on with its locals still elided.  Once the continuation is immutable,
it can be examined or resumed more than once, and each of those must see the
same variable objects.  Once it is shared, other fibers can reach it, and
through it the locals, which exposes them to concurrent modification.  Either
way the locals have escaped, so they are created, and the continuation
resumes in the L1 interpreter, whose execution only uses real variables.  An
invalidated chunk cannot be resumed at all, so its frame likewise resumes in
L1, with real variables.

For each pair recorded in the register dump, it creates a variable of the
local's declared type, initialized to the decoded value: a boxed register's
object, an int register's whole 64-bit value, or a float register's double.
Every other placeholder becomes a new, unassigned variable of its local's
type.  The replacement covers every slot of the frame, not only the local
slots, because a pushed local is the same value as its slot, so a stack slot
of the reified frame can hold the placeholder too.  Finally it sets the
continuation's chunk to the [DefaultL1Chunk], at the dump's
`fallbackEntryPoint`.

It does nothing for a continuation of the [DefaultL1Chunk] or of an
[L2SimpleChunk], neither of which elides locals, or for a function with no
locals.

[A_RegisterDump]: ../../../src/main/kotlin/avail/descriptor/representation/A_RegisterDump.kt
[DefaultL1Chunk]: ../../../src/main/kotlin/avail/optimizer/DefaultL1ExecutableChunk.kt
[L1Translator]: ../../../src/main/kotlin/avail/optimizer/L1Translator.kt
[L2SimpleChunk]: ../../../src/main/kotlin/avail/interpreter/levelTwo/L2SimpleChunk.kt
[L2ValueManifest]: ../../../src/main/kotlin/avail/optimizer/manifest/L2ValueManifest.kt
[L2_CHECK_ESCAPED_LOCALS]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/variables/L2_CHECK_ESCAPED_LOCALS.kt
[L2_CREATE_CONTINUATION]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/L2_CREATE_CONTINUATION.kt
[L2_CREATE_VARIABLE]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/variables/L2_CREATE_VARIABLE.kt
[L2_FALL_BACK_TO_L1]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/L2_FALL_BACK_TO_L1.kt
[L2_GET_AND_CLEAR_IF_MUTABLE_UNESCAPED_LOCAL_VARIABLE]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/variables/L2_GET_AND_CLEAR_IF_MUTABLE_UNESCAPED_LOCAL_VARIABLE.kt
[L2_GET_UNESCAPED_LOCAL_VARIABLE]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/variables/L2_GET_UNESCAPED_LOCAL_VARIABLE.kt
[L2_GET_VARIABLE]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/variables/L2_GET_VARIABLE.kt
[L2_GET_VARIABLE_CLEARING_IF_MUTABLE]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/variables/L2_GET_VARIABLE_CLEARING_IF_MUTABLE.kt
[L2_INVOKE]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/L2_INVOKE.kt
[L2_INVOKE_CONSTANT_FUNCTION]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/L2_INVOKE_CONSTANT_FUNCTION.kt
[L2_RETURN]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/L2_RETURN.kt
[L2_SAVE_ALL_AND_PC_TO_INT]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/L2_SAVE_ALL_AND_PC_TO_INT.kt
[L2_SET_UNESCAPED_LOCAL_VARIABLE]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/variables/L2_SET_UNESCAPED_LOCAL_VARIABLE.kt
[L2_SET_VARIABLE]: ../../../src/main/kotlin/avail/interpreter/levelTwo/operation/variables/L2_SET_VARIABLE.kt
[VariablePlaceholderDescriptor]: ../../../src/main/kotlin/avail/descriptor/variables/VariablePlaceholderDescriptor.kt
