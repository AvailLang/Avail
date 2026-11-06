# Plan: system limits and panics

Status: the cap and panic type are implemented. The items below were reviewed
and dropped for now; they are recorded so the reasoning isn't lost.

## Current state

- `IntegerDescriptor.MAX_LONG_COUNT = Int.MAX_VALUE - 8` caps the number of
  64-bit words in one integer. That is below the JVM array-length limit, and
  every bit position fits in a `Long` (fewer than 2^37 bits).
- `IntegerDescriptor.checkLongCount` and the bound in `o_BitSet` throw
  `AvailSystemLimitException` when the cap would be exceeded. The check runs
  before any allocation.
- `AvailSystemLimitException` extends `PrimitiveThrownException`, so
  `AvailTask` terminates the fiber and calls its failure continuation with the
  exception. It does not extend `AvailRuntimeException`, the family of ordinary
  failures.
- Primitives that can't otherwise fail (`CannotFail`) stay that way. They never
  handle a system limit.

## Policy

- An ordinary semantic failure (out-of-range index, infinity minus infinity)
  is a failure value, handled by the Avail failure mechanism.
- A system limit (out of integer words, out of memory) is a panic. It is not a
  failure value, and no primitive signature changes to accommodate it.
- Before exceptions are bootstrapped, a panic may simply abort the library
  load. That is an acceptable answer.
- After exceptions are bootstrapped, a fiber can catch and rethrow
  exceptions, so an unwind can be clean. Decide whether Avail code may catch a
  system-limit panic at all. The default answer should be no, because the
  limit is a property of the VM, not of the program.

## Dropped for now

- **Fiber-termination test at the cap.** Not planned. The unit tests cover the
  throw.
- **Compile-time folding of a panic.** Not planned. Both options, abandoning
  the whole optimization or only the one fold, were considered.
- **Parse workers and panics.** Not planned.
- **Size policy at serialization.** Not planned. Serialization is transactional,
  so anything that completed serialization already loaded, and fast-loading it
  should work.
- **Word-level bit-range check in `P_BitTest`.** Not planned. The bit-by-bit
  loop costs at most about 64 times more than a word-level check, and the
  pathological case is unlikely to matter.
- **Double-based quotient estimate in division.** Dropped. The Java library's
  division routine is faster, even for dozens of longs.
- **Serialization version bump on format changes.** Not planned. The format
  has changed many times since the last commercial release, so one more bump
  adds nothing. Repository caches are cleared by hand when the format changes.

## Still open

- Audit of 32-bit integer access in integer code: clean. No `intSlot` or
  `setIntSlot` remains in `descriptor/numbers` or the integer primitives.
- `intCount` removed; its uses test `longCount` directly.
- Next major work: widen L2 int registers from 32 to 64 bits. See below.

## L2 int registers: 32 to 64 bits

Int registers are 64-bit JVM locals that currently always hold sign-extended
ints.  `JVMTranslator` offers two views: `load`/`store` (the int view, with
`l2i`/`i2l`) and `loadLong`/`storeLong` (the long view).

### Long view (done)

Unbox, box, int and constant moves, add, subtract, multiply, divide,
compare, bit logic, chunk entry, the register dump, and the L2 trace.

Arithmetic is 64-bit (done): `_+_`, `_-_`, `_×_`, `_÷_`, `_bit∧_`,
`_bit∨_`, `_bit⊕_`, `_<<_` and `_>>_` unbox operands in `i64`.  The checked
operations test for `long` overflow, and are only emitted when that overflow
is possible; otherwise the exact bit-logic operations are used.  Every
bit-logic operation is exact on longs except `Ushr`, which reproduces the
32-bit unsigned shift (`ushr32`) for hash dispatch.  Rules that produce `Shl`
require the result to fit a `Long`.

`readInt`, `readTwoInts`, `canUnboxInt`, `readAsTwoInts` and
`attemptToGenerateTwoIntToIntPrimitive` take a `range` that defaults to
`i32`; only the callers that read int registers as longs pass `i64`.

The split conditions (`L2SplitCondition.unboxedIntConditions` and
`constantConditions`) still seed int registers with `i32Restriction`.  That's
a splitting heuristic, not a claim about the value, so it's left for now.

### Int view, permanently 32-bit

These read or write values that are ints by nature: JVM `int` APIs, indices,
or 32-bit identities.

- Dispatch: `L2_HASH`, `L2_EXTRACT_OBJECT_VARIANT_ID`,
  `L2_EXTRACT_OBJECT_TYPE_VARIANT_ID`, `L2_EXTRACT_TAG_ORDINAL`,
  `L2_MULTIWAY_JUMP` (`lookupswitch`/`tableswitch`).
- Program counters: `L2_SAVE_ALL_AND_PC_TO_INT`, `L2_CREATE_CONTINUATION`.
- Tuples: `L2_TUPLE_SIZE`, `L2_TUPLE_AT_NO_FAIL`,
  `L2_TUPLE_CODEPOINT_AT_NO_FAIL`, `L2_TUPLE_INT_AT_NO_FAIL`,
  `L2_TUPLE_SUBRANGE_NO_FAIL`.
- `L2_CODEPOINT_TO_CHARACTER`.

### Invariant change needed before widening

Today every int register holds an int, so an int-view read never truncates.
Once arithmetic results may exceed 32 bits, an int-view read is only safe on a
register whose restriction lies within `i32`.  Done: each int-view reader
above calls `L2ReadIntOperand.assertInt32` when it is added, and
`JVMTranslator.transferPairwise` moves int registers as whole longs.

### Remaining steps

1. Done: bitwise and, or, xor unbox in `i64` (`generateBinaryIntOperation`
   takes a `range`); `Max`/`Min` bounds use longs; comparisons and
   equality-with-constant accept `i64`; `readIntNoFail` and
   `unboxedIntConstant` handle longs.
2. Done: division unboxes in `i64`; `L2_DIVIDE_INT_BY_INT` bounds its
   quotient with longs; `Div`/`Mod` are exact for a non-negative dividend and
   positive divisor; `modBound` uses longs.
3. Done: `Shl` and `Shr` are 64-bit (counts 0..63, rule shift limits
   0..63), and `_<<_` and `_>>_` unbox in `i64`.  `Ushr` stays a 32-bit
   operation for good: its only producer is hash dispatch, which needs the
   32-bit unsigned shift of an int hash.  `P_BitTest`'s `SelectBit` path is
   still `i32` (see the next item).
4. Done: `P_BitTest`, and the infallible rewrites in `P_Addition` and
   `P_BitwiseAnd`, use `i64`.  The primitives that still unbox only `i32` do
   so because their values are ints by nature: `P_Hash`, `P_MapSize`,
   `P_TupleAt`, `P_ExtractSubtuple`, `P_FloatToIntBits`, `P_FloatFromIntBits`.
5. Done: split conditions.  `unboxedIntConditions` takes the range the
   consumer can unbox (default `i64`); `constantConditions` starts int
   registers at `i64`.  Only `L2_EXTRACT_TAG_ORDINAL`, whose values are ints
   by nature, keeps `i32`.
6. Done: phi kinds at merges, boxed-integer recycling, and the int fast
   paths in `L2Generator`'s type check and `L2_JUMP_IF_OBJECTS_EQUAL` use
   `i64`.  `L2_JUMP_IF_KIND_OF_OBJECT` tests an exact `i64` with `isLong`, as
   it tests an exact `i32` with `isInt`, so the dynamic checks before
   unboxing a long are cheap.  The manifest's `readInt` defaults to
   `i64Restriction`, so it never narrows a value.

The 32-bit to 64-bit widening of L2 int registers is complete, apart from the
values that are ints by nature (listed above).
