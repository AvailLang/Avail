# Documentation Updates - L2 Optimizer (2025-01-26)

## Sites Requiring Documentation Updates

### 1. L1Translator.kt:1272
**Issue**: Potential misuse of `equivalentSemanticValue`
- Should verify whether `equivalentPopulatedSemanticValue` should be used instead
- Context: This was identified during the investigation of postponed instruction invariants

### 2. ObjectLayoutVariantDecisionStep.kt:285-307
**Issue**: Potential misuse of `equivalentSemanticValue`
- Lines involve semantic value lookup during variant dispatch
- May need to distinguish between populated vs postponed semantic values
- Context: Found during review of semantic value handling patterns

### 3. L2ValueManifest.kt - General Documentation
**Topics to document**:
- **Computed vs Populated semantics**: Clarify the distinction between:
  - Values in the manifest (may have empty definitions if postponed)
  - Values with actual register definitions (populated/live)

- **hasSemanticValue vs hasLiveSemanticValue**: Document when to use each:
  - `hasSemanticValue(value)`: Returns true if value is in manifest (may be postponed)
  - `hasLiveSemanticValue(value)`: Returns true only if value has non-empty definitions
  - `equivalentSemanticValue(value)`: Finds equivalent value in manifest (may be postponed)
  - `equivalentPopulatedSemanticValue(value)`: Finds equivalent value with definitions

### 4. L2ValueManifest.kt:565-589
**Issue**: Restriction propagation code needs documentation
- Complex logic for how type restrictions flow through the manifest
- Should document the invariants maintained during restriction updates

## Key Invariants (For Documentation)

### Postponed Instructions Invariant
From L2ValueManifest.kt lines 100-125:
```
During optimization passes, a manifest can track postponed instructions that have
no side effects. The two maps (semanticValueToSynonym and postponedInstructions)
are kept up-to-date with the effects of the postponed instructions, even though
the associated Constraint might list no registers as actually holding that value.
```

**Concrete invariant**:
- A semantic value can be in the manifest with empty definitions IF AND ONLY IF
  there's a corresponding entry in the postponedInstructions map
- Synonyms and type restrictions ARE maintained for postponed values
- Register definitions are NOT maintained (definitions list is empty)

### Register vs Semantic Value Uniqueness
- **SSA applies to registers**: Each register has a unique definition point
- **Semantic values can be duplicated**: Multiple registers can hold the same
  semantic value along a path (e.g., at merge points during phi generation)

## Recent Fixes (Context for Documentation)

### Fix 1: recordPostponedInstruction() - Lines 331-349
**Problem**: Duplicate moves to same semantic value at merge points caused
assertion failures.

**Solution**: Treat postponed values as "already written" and exclude them
from unwritten set:
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

### Fix 2: recordPostponedSourceInstructionFor() - Line 379
**Problem**: getDefinitionOrNull returns a register for postponed values,
making the check too permissive.

**Solution**: Use hasLiveSemanticValue instead:
```kotlin
assert(!hasLiveSemanticValue(value))  // Was: getDefinitionOrNull(value) == null
```

### Fix 3: recordPostponedInstruction() - Line 332
**Problem**: equivalentSemanticValue finds postponed values, causing them
to be treated as unpopulated.

**Solution**: Use equivalentPopulatedSemanticValue:
```kotlin
equivalentPopulatedSemanticValue(it)  // Was: equivalentSemanticValue(it)
```

## Notes
- Integer/float register allocation issue was in
  L2Regenerator.OperandSemanticTransformer.doOperand
- Merge point phi generation creates moves into new registers for every
  live value, which can create duplicate writes to semantic values
  (but different registers)
