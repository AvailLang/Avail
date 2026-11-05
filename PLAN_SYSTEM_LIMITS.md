# Plan: system limits and panics

Status: planned, not implemented. Written down for a later session.

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

## Open items

1. **Verify the fiber-termination path end to end.** Build an integer one word
   past the cap in a running fiber. Confirm that only that fiber terminates,
   that its failure continuation receives the `AvailSystemLimitException`, and
   that the runtime stays usable. Unit tests only cover the throw so far.

2. **Compile-time folding.** `CanFold` primitives can run during compilation.
   Decide what happens when a fold hits a panic. Two options:
   - (a) The optimizer catches the panic, abandons the whole optimization of
     that unit, and forces L1 or L2Simple execution for a while, possibly
     indefinitely.
   - (b) The folding code catches the panic and abandons only that fold. The
     operation is then left to run at runtime, with its full consequences.
   Option (b) is more local and probably the better default. Check how the
   fold path currently handles `PrimitiveThrownException`, and what the
   optimizer does with it.

3. **Parse workers.** Compiler parse workers already use
   `PrimitiveThrownException` to accept or reject a parse immediately. The
   result goes through a hook function bootstrapped in `Exceptions.avail`.
   Confirm that a system-limit panic inside a parse worker reaches the compiler
   as a fiber termination, and that it is not mistaken for a rejected parse.

4. **Size policy.** Confirm that `MAX_LONG_COUNT = Int.MAX_VALUE - 8` is the
   intended ceiling. Decide whether to enforce it at object creation only, as
   now, or also at serialization and deserialization.

## Related int-level work still open

- Done: `IntegerDescriptor.intCount` was removed. Its uses now test
  `longCount` directly, which avoids the `Int` overflow above 2^30 longs.
- `P_BitTest.returnTypeGuaranteedByVM` checks bits one at a time, so its cost
  grows with the bit size of the bounds. A word-at-a-time check would bound it.
- `P_BitTest` still uses `extractLong` and `isLong` on bit positions, which is
  correct. Keep this in mind if the bit-position type changes again.
