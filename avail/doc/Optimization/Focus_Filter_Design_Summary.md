# Focus Filter Design Summary

## Overview
The focus filter provides a way to visualize only the data and control flow contributing to a specific semantic value in an L2 control flow graph. This dramatically reduces visual clutter when debugging specific value computations.

## Key Design Decisions

### 1. Interface-Based Design
**Decision**: Create `L2ControlFlowGraphVisualizationFilter` interface with `NoFilter` and `FocusFilter` implementations.

**Rationale**:
- Clean separation of concerns - filtering logic separate from rendering
- Easy to extend with other filter types in future
- No API changes to existing visualizer constructor

**Trade-offs**:
- Additional indirection (minor)
- More files to maintain
- Benefits outweigh costs for clarity and extensibility

### 2. Backward Work Queue Algorithm
**Decision**: Use work queue with (block, interesting values) pairs for backward tracing.

**Rationale**:
- Natural fit for data flow analysis
- Handles multiple paths and convergence correctly
- Allows incremental discovery of interesting values

**Alternative Considered**: Recursive backward traversal
- Rejected due to potential stack depth issues
- Work queue provides better control and debugging visibility

### 3. Sibling Edge Handling
**Decision**: Edges sharing a source block with interesting edges show filtered manifests.

**Rationale**:
- Provides context about alternate paths from decision points
- Helps understand why certain paths weren't taken
- Essential for understanding conditional logic

**Implementation**: Separate `siblingEdgesOfInterestingEdges` set, populated after main trace completes.

### 4. CurrentBlock Semantics
**Decision**: When `currentBlock` provided, trace only to that block. When `null`, trace from all exit blocks.

**Rationale**:
- `currentBlock != null`: User debugging specific location, wants ancestry
- `currentBlock == null`: User wants complete picture of value origins
- Exit blocks (no forward successors) are natural starting points

**Alternative Considered**: Always trace from all blocks
- Rejected as too broad when user has specific focus point

### 5. Filter Queries vs. Filtered Data
**Decision**: Filter provides boolean queries (`isBlockInteresting`) rather than returning filtered collections.

**Rationale**:
- Rendering code maintains control of iteration
- Easier to add conditional logic based on filter state
- Filter remains stateless from renderer perspective

**Exception**: `interestingSynonymsFor()` returns filtered set because manifest structure would be complex to query piecewise.

## Identified Issues and Concerns

### Issue 1: Instruction-Level Granularity
**Problem**: An instruction with 10 operands where only 1 is interesting still shows all 10.

**Status**: Accepted limitation
**Reason**: Instruction rendering is already complex; partial operand display would require significant refactoring.
**Mitigation**: Short-name rendering for uninteresting instructions reduces clutter.

### Issue 2: Postponed Instructions in Manifests
**Problem**: Manifests can have postponed instructions. Should these be traced?

**Status**: NOT handled in current design
**Resolution Needed**: Need to determine if:
1. Postponed instructions should be marked interesting if they mention interesting values
2. Their operands should contribute to traced values
3. They should be visually distinguished

**Recommendation**: Add in phase 2 after basic implementation proven.

### Issue 3: Register Definition Chains
**Problem**: Instructions write to registers, which then appear in manifest definitions. We trace semantic values, but relationship through register definitions might be lost.

**Status**: Handled implicitly
**Reason**:
- Write operands contain semantic values
- When we find interesting write, we add its read operands' semantic values
- This propagates through the register/semantic value relationship

**Verification Needed**: Test with complex register reuse scenarios.

### Issue 4: Cross-Block Value Propagation
**Problem**: A semantic value might exist in a predecessor manifest but not be explicitly written by any instruction in that block (just propagated through).

**Status**: Handled by edge-based propagation
**Reason**: We check predecessor edges' manifests for interesting values and propagate backward.

**Edge Case**: Block that only reads interesting values but doesn't write them might not be marked interesting.
**Resolution**: This is correct - such blocks don't contribute new information to focus value.

### Issue 5: Phi Node Merging
**Problem**: Phi nodes merge values from multiple predecessors. All input paths should be traced.

**Status**: Handled correctly
**Reason**: Phi read operands reference semantic values from each predecessor. When we trace a phi write, we add all its read operands' values, which then propagate to all predecessors.

**Verification Needed**: Test with complex control flow merges.

### Issue 6: Backward Edge Handling
**Problem**: Backward edges create cycles. Could cause infinite loops or miss interesting values.

**Status**: Handled by filtering backward edges
**Reason**:
- `backwardVisit()` ignores backward edges (by design)
- `predecessorEdges().filter { !it.isBackward }` in work queue
- Focus on acyclic data flow paths

**Limitation**: If focus value's *only* origin is through a loop backedge, we won't find it.
**Severity**: Low - loop backedges typically merge with forward edges (phi nodes).

**Future Work**: Consider adding backward edge support with cycle detection.

### Issue 7: Empty Result Set
**Problem**: Focus value might not appear anywhere in graph.

**Status**: Produces empty/minimal visualization
**Behavior**:
- All blocks render as circles
- All edges have no labels (just input numbering)
- Could be confusing to user

**Recommendation**: Add validation in FocusFilter constructor:
```kotlin
init
{
	computeInterestingEntities()
	if (interestingSemanticValues.size == 1)
	{
		// Only focusValue in set means nothing else found
		// Could log warning or throw exception
	}
}
```

### Issue 8: Performance on Large Graphs
**Problem**: Work queue algorithm touches many blocks.

**Status**: Acceptable for typical L2 chunk sizes
**Analysis**:
- Typical chunks: 10-100 blocks
- Large chunks: 100-1000 blocks
- Work queue processes each block at most once per unique interesting value set
- Worst case: O(blocks × semantic_values) but semantic values grow slowly

**Optimization if Needed**: Add early termination when reaching entry block.

### Issue 9: Multiple Writers to Same Value
**Problem**: Multiple instructions might write to the same semantic value (register reuse, phi merging).

**Status**: All writers marked interesting
**Reason**: Work queue approach finds all paths naturally.
**Verification**: Correct behavior - shows all contributions to focus value.

### Issue 10: Constraint Changes Not Highlighted
**Problem**: Original `synonym()` rendering highlights when constraints change. Filtered view might hide this.

**Status**: Accepted limitation
**Reason**: Filter only controls *which* synonyms display, not *how* they display. Highlighting logic remains unchanged for displayed synonyms.

**Consideration**: If most synonyms filtered out, remaining ones might show spurious "changed" highlighting (compared to filtered-out synonyms).

## Design Strengths

1. **Separation of Concerns**: Filter logic isolated from rendering
2. **Incremental Discovery**: Work queue naturally expands trace as needed
3. **Correct Backward Propagation**: Handles all edge cases (merges, splits, phis)
4. **Flexible Starting Points**: CurrentBlock or exit blocks
5. **Context Preservation**: Sibling edges provide decision context

## Design Weaknesses

1. **No Backward Edge Tracing**: Misses loop-carried dependencies
2. **Instruction Granularity**: Can't filter individual operands
3. **No Postponed Instruction Handling**: Deferred to future work
4. **Empty Result Not Validated**: Silent failure mode
5. **Performance Not Measured**: Could be issue for very large chunks

## Recommendations for Implementation

### Priority 1 (Must Have)
1. Implement basic filter interface and classes ✅
2. Integrate into visualizer rendering paths
3. Test with simple linear flow cases
4. Verify phi node handling

### Priority 2 (Should Have)
1. Add empty result validation with warning
2. Test performance on large real-world chunks
3. Document usage examples
4. Add integration tests

### Priority 3 (Nice to Have)
1. Consider backward edge support
2. Investigate postponed instruction handling
3. Add filter statistics (% blocks/edges shown)
4. Visual indicator when filter active

## Usage Examples

### Example 1: Debug Specific Value in Current Block
```kotlin
val visualizer = L2ControlFlowGraphVisualizer(
	fileName = "debug.dot",
	name = "My Chunk",
	charactersPerLine = 80,
	controlFlowGraph = cfg,
	visualizeLiveness = false,
	visualizeManifest = true,
	visualizeRegisterDescriptions = true,
	accumulator = StringBuilder(),
	currentBlock = generator.currentBlock,
	currentManifest = generator.currentManifest,
	focusValue = problematicSemanticValue
)
```

**Result**: Shows only instructions/blocks that contribute to `problematicSemanticValue` in `currentBlock`.

### Example 2: Trace Value Through Entire Chunk
```kotlin
val visualizer = L2ControlFlowGraphVisualizer(
	// ... same parameters ...
	currentBlock = null,  // No specific block
	currentManifest = null,
	focusValue = valueToTrace
)
```

**Result**: Traces `valueToTrace` from all exit blocks backward through entire chunk.

### Example 3: No Filter (Current Behavior)
```kotlin
val visualizer = L2ControlFlowGraphVisualizer(
	// ... same parameters ...
	currentBlock = null,
	currentManifest = null,
	focusValue = null  // No focus value
)
```

**Result**: Shows entire graph unchanged (NoFilter used).

## Conclusion

The focus filter design is straightforward and addresses the core requirement: showing data flow for a specific semantic value while reducing visual clutter. The interface-based approach provides clean separation and extensibility. Main concerns are edge cases (postponed instructions, backward edges) which can be addressed in future iterations once basic functionality is validated.

Implementation should proceed in phases:
1. Core filter logic (done)
2. Integration into visualizer rendering
3. Testing with real-world examples
4. Refinement based on actual usage patterns
