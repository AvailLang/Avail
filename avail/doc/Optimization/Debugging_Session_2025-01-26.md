# L2 Optimizer Debugging Session - 2025-01-26

## Session Overview
Debugging failures in SimpleOptimizerTest during postponement pass optimization.
Multiple issues were identified and fixed related to postponed instruction handling.

## Tests Involved
- `SimpleOptimizerTest.optimizeTupleMapThrough()`
- `SimpleOptimizerTest.optimizeAnyOfSatisfies1()`

## Issues Discovered and Fixed

### Issue 1: Missing Postponed Instruction for TupleSize(9-3)
**Symptom**: Semantic value TupleSize(9-3) was in manifest with empty definitions
but NOT in postponed map, violating the invariant.

**Root Cause**: L2ValueManifest.kt:332 used `equivalentSemanticValue(it)` which
returns semantic values in the manifest even with empty definitions (postponed).
This made the code think values were "already written" when they weren't populated.

**Fix**: Changed to `equivalentPopulatedSemanticValue(it)` to only find values
with actual register definitions.

**Location**: L2ValueManifest.kt:332

---

### Issue 2: Assertion Failure in Off-Ramp Reification
**Symptom**: Test failed with assertion `getDefinitionOrNull(value) == null`
at line 379.

**Root Cause**: `getDefinitionOrNull` has special logic that returns the register
from a postponed instruction's write operand even if the value isn't in the
manifest yet. It doesn't check for "no register", it checks for "not in manifest
AND not postponed".

**Fix**: Changed to `assert(!hasLiveSemanticValue(value))`

**Location**: L2ValueManifest.kt:379

---

### Issue 3: Duplicate Postponed Instruction Recording
**Symptom**: Assertion failure at line 378: `assert(value !in postponedInstructions)`
when trying to record a move for semantic value 11-26.

**Analysis**:
- temp 2.dot showed TWO writes to 11-26 along the same execution path:
  - Block #14: `MoveBoxed →r303[11-26] ← @r273[innerPredicate-1]`
  - Block #21: `MoveBoxed →r327[11-26] ← @r273[innerPredicate-1]`
  - Path: #14 → #15 → #21

**Root Cause**: At merge points, phi generation creates moves into new registers
for every live value. This can create multiple writes to the same semantic value
(but different registers) along a path. The recordPostponedInstruction logic
didn't handle the case where a semantic value was already in postponedInstructions.

**Key Understanding**:
- SSA uniqueness applies to REGISTERS, not semantic values
- Multiple registers can hold the same semantic value along a path
- Block #21 was a "COLD / failed bounds check" block reconstructing live values

**Fix**: Modified recordPostponedInstruction to:
1. Treat postponed values as "already written" (lines 331-334)
2. Exclude postponed values from the unwritten set (lines 347-349)

**Location**: L2ValueManifest.kt:331-349

```kotlin
val alreadyWritten = originalWrite.semanticValues().mapNotNull {
    equivalentPopulatedSemanticValue(it) ?:
        (if (it in postponedInstructions) it else null)
}
// ...
val unwritten = originalWrite.semanticValues() -
    semanticValueToSynonym!!.keys -
    postponedInstructions.keys  // Added this line
```

---

### Issue 4: Integer Register Numbers in Postponed Instructions
**Symptom**: Postponed TupleSize instruction showed concrete register i463,
while boxed moves showed no register numbers.

**Root Cause**: L2Regenerator.OperandSemanticTransformer.doOperand was allocating
registers for integer/float operands during operand transformation.

**Note**: This was identified but the specific fix was not shown in this session.
The user provided the location for future reference.

**Location**: L2Regenerator.OperandSemanticTransformer.doOperand (lines 332-356)

---

## Key Invariants Documented

### Postponed Instructions Invariant
- Semantic values written by postponed instructions ARE in the manifest
- Synonyms and type restrictions are maintained
- Register definitions are empty (definitions list)
- MUST have entry in postponedInstructions map

### hasSemanticValue vs hasLiveSemanticValue
- `hasSemanticValue(value)`: True if value is in manifest (may be postponed)
- `hasLiveSemanticValue(value)`: True only if value has non-empty definitions
- `equivalentSemanticValue(value)`: Finds equivalent in manifest (may be postponed)
- `equivalentPopulatedSemanticValue(value)`: Finds equivalent with definitions

### Register vs Semantic Value Uniqueness
- SSA applies to register definitions (unique definition points)
- Semantic values can have multiple registers holding them
- Merge points generate moves into new registers for live values
- This creates duplicate semantic value writes but to different registers

## File References

### Source Files Modified
- `avail/src/main/kotlin/avail/optimizer/L2ValueManifest.kt`
  - Lines 332, 349, 379

### Test Files
- `avail/src/test/kotlin/avail/test/optimizer/SimpleOptimizerTest.kt`
  - Tests: `optimizeTupleMapThrough`, `optimizeAnyOfSatisfies1`

### Graph Dumps Referenced
- `temp 2.dot` / `temp 2.svg`: Source graph before postponement pass
- `temp.dot` / `temp.svg`: Output graph after postponement pass

## Next Issue (2025-01-26)
**New failure**: Postponements consistency error when retroactively generating
code in a predecessor block as part of phi generation during a postponement pass.

**Status**: Not yet investigated in this session.

## Session Participants
- User: Mark van Gulik
- Assistant: Claude (Sonnet 4.5) via Claude Code

## Related Documentation
- `avail/doc/Optimization/TODO_Documentation_Updates.md` - Documentation tasks
- `avail/doc/Optimization/Level_Two/Variable_elision.md` - Related optimization docs
