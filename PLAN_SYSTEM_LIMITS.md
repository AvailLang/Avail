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
- Next major work: widen L2 int registers from 32 to 64 bits. See the notes
  in the L2 section of the plan once it's written.
