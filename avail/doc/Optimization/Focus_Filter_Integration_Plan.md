# Focus Filter Integration Plan

## Overview
Integration of `L2ControlFlowGraphVisualizationFilter` into `L2ControlFlowGraphVisualizer` to support focused visualization of data flow for a specific semantic value.

## Changes to L2ControlFlowGraphVisualizer

### 1. Add Filter Field

```kotlin
/**
 * The visualization filter that controls what information is displayed.
 * Either [NoFilter] to show everything, or [FocusFilter] to trace a
 * specific semantic value.
 */
private val filter: L2ControlFlowGraphVisualizationFilter
```

### 2. Initialize Filter in Constructor

Add initialization after parameter declarations:

```kotlin
init
{
	filter = if (focusValue != null)
	{
		FocusFilter(focusValue, currentBlock, controlFlowGraph)
	}
	else
	{
		NoFilter()
	}
}
```

### 3. Modify `basicBlock()` Method

**Current behavior**: Always renders blocks as full tables with all instructions.

**New behavior**:
- Check `filter.isBlockInteresting(basicBlock)`
- If `false`, render as small circle node
- If `true`, render as table (current behavior)

```kotlin
private fun GraphWriter.basicBlock(
	basicBlock: L2BasicBlock,
	started: Boolean)
{
	if (!filter.isBlockInteresting(basicBlock))
	{
		// Render as small circle for uninteresting blocks
		node(basicBlockName(basicBlock)) {
			shape("circle")
			label("") // Empty label
			// TODO: Adjust size - current placeholder uses default
			// User mentioned will adjust size later
			val fillColor = when
			{
				!started -> adjust("#202080/303000")
				basicBlock.isCold -> adjust(coldInstructionBackColor)
				else -> adjust("#c1f0f6/104048")
			}
			color(fillColor)
			style("filled")
		}
		return
	}

	// ... existing full table rendering code ...
}
```

### 4. Modify `instructionTableRow()` Method

**Current behavior**: Shows full instruction details for all instructions.

**New behavior**:
- Check `filter.isInstructionInteresting(instruction)`
- If `false`, show only short operation name
- If `true`, show full details (current behavior)

```kotlin
private fun StringBuilder.instructionTableRow(
	gridcolor: String,
	instruction: L2Instruction,
	writer: GraphWriter,
	basicBlock: L2BasicBlock)
{
	tag("tr") {
		val cellAttributes = mutableMapOf(
			"colspan" to "2",
			"align" to "left",
			"balign" to "left",
			"border" to "1",
			"color" to gridcolor,
			"valign" to "top")

		// ... existing color logic ...

		if (!filter.isInstructionInteresting(instruction))
		{
			// Show just the short operation name
			tagIf(true, "td", cellAttributes) {
				font(italic = true, color = writer.adjust("#808080/a0a0a0")) {
					append(escape(instruction.operation.name))
				}
			}
			return
		}

		// ... existing full rendering code ...
	}
}
```

### 5. Modify `edge()` Method

**Current behavior**: Always shows edge labels with manifest.

**New behavior**:
- Check `filter.shouldShowEdgeManifest(edge)`
- If `false`, skip label generation but keep headlabel (input numbering)
- If `true`, show label with filtered manifest

```kotlin
private fun edge(
	edge: L2PcOperand,
	writer: GraphWriter,
	started: Boolean,
	edgeCounter: AtomicInteger)
{
	val sourceBlock = edge.sourceBlock()
	val targetBlock = edge.targetBlock()
	// ... existing setup code ...

	val showManifest = filter.shouldShowEdgeManifest(edge)
	val edgeLabel = if (showManifest)
	{
		buildString {
			tag("table", "border" to "0", "cellspacing" to "0") {
				tag("tr") {
					tag("td",
						"balign" to "left",
						"cellpadding" to "5"
					) {
						font(bold = true) {
							append(escape(basicName))
							namedOperandType?.purpose?.let {
								append(" ($it)")
							}
						}
						append("<br/>")

						// ... existing clamped entities code ...
						// ... existing liveness code ...

						val manifest = edge.manifestOrNull()
						val predecessorEdges =
							edge.instruction.basicBlock().predecessorEdges()
						if (visualizeManifest && manifest != null)
						{
							// Pass filter to manifest rendering
							manifest(manifest, writer, predecessorEdges, edge)
						}
					}
				}
			}
		}
	}
	else
	{
		"" // Empty label
	}

	try
	{
		writer.edge(
			source = node(
				basicBlockName(sourceBlock),
				sourcePortNamesByEdge[edge],
				if (edge in sourcePortNamesByEdge) null else CompassPoint.S),
			target = when
			{
				edge.isBackward ->
					node(basicBlockName(targetBlock), "1", CompassPoint.N)
				else -> node(basicBlockName(targetBlock))
			})
		{
			// ... existing styling code ...

			if (showManifest)
			{
				label(edgeLabel)
			}

			// Always show headlabel for input numbering, even without manifest
			if (targetBlock.instructions().any { it is L2_PHI<*> })
			{
				val predecessors = edge.targetBlock().predecessorEdges()
				val targetIndex = predecessors.indexOf(edge) + 1
				headlabel(
					buildString
					{
						font(
							size = 8,
							color = writer.adjust("#400040/ff00ff"))
						{
							append("#$targetIndex/${predecessors.size}")
						}
					})
			}
		}
	}
	catch (e: IOException)
	{
		throw UncheckedIOException(e)
	}
}
```

### 6. Modify `manifest()` Method

**Current behavior**: Shows all synonyms in manifest.

**New behavior**: Filter synonyms based on `filter.interestingSynonymsFor(edge)`.

```kotlin
private fun StringBuilder.manifest(
	manifest: L2ValueManifest,
	writer: GraphWriter,
	predecessorEdges: Iterable<L2PcOperand>,
	edge: L2PcOperand)  // Add edge parameter
{
	if (manifest in duplicateManifests)
	{
		font(
			italic = true,
			size = 20,
			color = writer.adjust("#400000/ff0000")
		) { append("DUPLICATE MANIFEST!!!") }
	}

	val allSynonyms = manifest.synonymsArray()
	if (allSynonyms.isEmpty()) return

	// Filter synonyms based on filter
	val interestingSet = filter.interestingSynonymsFor(edge)
	val synonymsToShow = if (interestingSet != null)
	{
		// Filter to only interesting synonyms
		allSynonyms.filter { it in interestingSet }
	}
	else
	{
		// Show all (NoFilter case)
		allSynonyms.toList()
	}

	if (synonymsToShow.isEmpty()) return

	font(italic = true) { append("manifest:") }
	synonymsToShow.sorted().forEach { synonym ->
		val pick = synonym.pickSemanticValue()
		synonym(
			writer,
			synonym,
			manifest.restrictionFor(pick),
			manifest.getAllDefinitions(pick),
			manifest.postponedInstructionFor(pick),
			predecessorEdges)
	}
}
```

Note: All call sites of `manifest()` need to pass the edge parameter.

### 7. Update Call Sites

Update all locations that call `manifest()` to include the edge parameter:

1. In `edge()` method (already shown above)
2. In `basicBlock()` method when showing current manifest:

```kotlin
if (isCurrent && currentManifest != null)
{
	val manifestText = buildString {
		tag("table", "border" to "0", "cellspacing" to "0") {
			tag("tr") {
				tag(
					"td",
					"balign" to "left",
					"bgcolor" to adjust(currentBlockBackColor)
				) {
					// Need to pass a dummy edge or handle differently
					// Current manifest has no associated edge
					// For now, pass null and handle in manifest()
					manifest(
						currentManifest,
						this@basicBlock,
						currentBlock.predecessorEdges(),
						null)  // No edge for current manifest
				}
			}
		}
	}
	// ... rest of manifest node rendering ...
}
```

Handle `null` edge in `manifest()`:

```kotlin
private fun StringBuilder.manifest(
	manifest: L2ValueManifest,
	writer: GraphWriter,
	predecessorEdges: Iterable<L2PcOperand>,
	edge: L2PcOperand?)  // Now nullable
{
	// ... duplicate check ...

	val allSynonyms = manifest.synonymsArray()
	if (allSynonyms.isEmpty()) return

	// Filter synonyms based on filter
	val interestingSet = if (edge != null)
	{
		filter.interestingSynonymsFor(edge)
	}
	else
	{
		null  // Show all for current manifest
	}

	// ... rest of filtering logic ...
}
```

## Implementation Order

1. ✅ Create `L2ControlFlowGraphVisualizationFilter.kt` with interface and implementations
2. Add filter field and initialization to visualizer
3. Modify `basicBlock()` for circle rendering
4. Modify `instructionTableRow()` for short names
5. Add edge parameter to `manifest()` signature
6. Modify `edge()` to conditionally show labels
7. Modify `manifest()` to filter synonyms
8. Update all call sites
9. Test with simple cases
10. Refine based on usage

## Known Issues and Future Work

### Issue 1: Circle Size for Uninteresting Blocks
**Status**: Placeholder implementation using default size.
**Resolution**: User mentioned they will adjust size later. Current implementation uses `shape("circle")` with default size. May need to add explicit width/height attributes or use fixedsize and specify dimensions.

### Issue 2: Empty Manifests After Filtering
**Status**: Handled by checking if `synonymsToShow.isEmpty()` and returning early.
**Note**: Edges with no interesting information will have empty labels but still show input numbering.

### Issue 3: Current Manifest Display
**Status**: Current manifest (when currentBlock provided) has no associated edge.
**Resolution**: Pass `null` edge and show all synonyms for current manifest (no filtering).

### Issue 4: Performance with Large Graphs
**Status**: Not yet measured.
**Consideration**: Backward tracing touches all blocks in worst case. Should be acceptable for typical L2 chunk sizes (< 1000 blocks), but may need optimization for very large chunks.

### Issue 5: Multiple Paths to Focus Value
**Status**: Handled correctly by work queue approach.
**Note**: All paths contributing to focus value are traced, which is the desired behavior.

## Testing Strategy

### Test Case 1: Simple Linear Flow
- Single block with arithmetic: `x = a + b; y = x * 2; focus on y`
- Expected: Both instructions interesting, all blocks interesting

### Test Case 2: Branch with Merge
- `if` condition splits, both branches write to same value, merge with phi
- Expected: Both branches interesting, phi interesting

### Test Case 3: Focus Not Present
- Focus value that doesn't appear in graph
- Expected: Empty visualization (only entry block as circle)

### Test Case 4: CurrentBlock Provided
- Focus value in specific block, trace only to that block
- Expected: Only ancestors of that block shown

### Test Case 5: Sibling Edges
- Decision point with one interesting path and one uninteresting
- Expected: Both edges show manifests, uninteresting filtered

### Test Case 6: Loop with Focus Value
- Value computed in loop body
- Expected: Loop blocks interesting, backward edges handled correctly

## API Considerations

The visualizer constructor signature remains unchanged - filter creation is internal. Users continue to pass `focusValue` and optionally `currentBlock`:

```kotlin
L2ControlFlowGraphVisualizer(
	fileName = "example.dot",
	name = "Example Chunk",
	charactersPerLine = 80,
	controlFlowGraph = cfg,
	visualizeLiveness = false,
	visualizeManifest = true,
	visualizeRegisterDescriptions = true,
	accumulator = StringBuilder(),
	currentBlock = someBlock,  // Optional - limits trace to this block
	currentManifest = null,
	focusValue = mySemanticValue  // Triggers focus filtering
)
```

When `focusValue = null`, uses `NoFilter` (current behavior).
When `focusValue != null`, uses `FocusFilter` (new focused behavior).
