/*
 * L2_MULTIWAY_JUMP.kt
 * Copyright © 1993-2024, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package avail.interpreter.levelTwo.operation.dispatch

import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operation.L2ConditionalJump
import avail.interpreter.levelTwo.operation.L2_JUMP
import avail.interpreter.levelTwo.operation.NumericComparator.Equal
import avail.interpreter.levelTwo.operation.NumericComparator.GreaterOrEqual
import avail.interpreter.levelTwo.operation.numbers.L2_JUMP_IF_COMPARE_INT
import avail.optimizer.L2ControlFlowGraph.Zone
import avail.optimizer.L2ControlFlowGraph.ZoneType
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.values.L2SemanticBoxedValue
import avail.utility.mapToSet
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor

/**
 * Given an integer and a constant tuple of N distinct integers in ascending
 * order, determine where the provided integer fits between the integers in the
 * tuple.  Then jump to one of the N+1 targets based on that computed index.
 *
 * If the integer is less than the first entry, jump to the first target.  If
 * the integer is greater than or equal to the first entry but less than the
 * second entry, jump to the second target, and so on.  If the integer is
 * greater than the last entry, jump to the N+1st target.
 *
 * @property value
 *   The [L2ReadIntOperand] providing the [Int] being dispatched.
 * @property splitter
 *   The [AbstractMultiWaySplitter] that determines how to jump.
 * @param branchEdges
 *   The [L2PcVectorOperand] contaaining the branch targets that the [splitter]
 *   is supposed to dispatch to.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2_MULTIWAY_JUMP
constructor(
	var value: L2ReadIntOperand,
	var splitter: L2ArbitraryConstantOperand<AbstractMultiWaySplitter>,
	var branchEdges: L2PcVectorOperand
): L2ConditionalJump()
{
	override fun StringBuilder.appendToWithWarnings(
		desiredOperandTypes: Set<L2OperandType>,
		warningStyleChange: (Boolean)->Unit)
	{
		renderPreamble()
		append(" ")
		append(value.registerString())
		renderOperandsExcludingFields(desiredOperandTypes, ::value)
	}

	override fun suggestVisualPortNames(): Map<L2PcOperand, String> = buildMap {
		val splits = splitter.constant.splitPoints
		branchEdges.edges.forEachIndexed { i, edge ->
			val name = when
			{
				i == 0 -> "..${splits[i] - 1}"
				i == splits.size -> "${splits[i - 1]}.."
				splits[i - 1] + 1 == splits[i] -> "${splits[i-1]}"
				else -> "${splits[i - 1]}..${splits[i] - 1}"
			}
			put(edge, name)
		}
	}

	override fun instructionWasAdded(
		manifest: L2ValueManifest)
	{
		// Note: Instructions that add multi-way jumps always set up the
		// manifests in the edges.
		value.instructionWasAdded(manifest)
		branchEdges.edges.forEach { edge ->
			// Feed each edge the base manifest, but allow the splitter to fix
			// them up later with more specific restrictions.
			edge.instructionWasAdded(manifest)
		}
		if (manifest.mode == BySemanticValue)
		{
			splitter.constant.populateEdgeManifests(value, branchEdges.edges)
		}
	}

	override val isPlaceholder get() = true

	override fun L2GeneratorInterface.emitTransformedInstruction()
	{
		// Delegate to the MultiWaySplitter.
		splitter.constant.run {
			emitSplitterInstruction(value, branchEdges.edges)
		}
	}

	override fun interestingConditions(): List<L2SplitCondition?> = buildList {
		// Delegate to the MultiWaySplitter.
		addAll(
			splitter.constant.interestingConditions(value, branchEdges.edges))
	}

	override fun L2GeneratorInterface.generateConditionalReplacement(
		originalInstruction: L2Instruction)
	{
		val splits = splitter.constant.splitPoints
		val edgeCount = splits.size + 1
		val low = splits.first()
		val high = splits.last()
		if (edgeCount > 4 && (high - low) < edgeCount * 10)
		{
			// It's at least 10% dense and has at least five edges out, so use
			// a dense table lookup.
			+this@L2_MULTIWAY_JUMP
			return
		}
		// Do a binary search instead.
		generateSubtree(
			value,
			splitter.constant,
			splitter.constant.originalValueSource(value),
			branchEdges.edges,
			1,
			splits.size,
			ZoneType.MULTI_WAY_EXPANSION.createZone(
				"multi-way branch:\n" +
					"\tsplits = $splits"))
	}

	/**
	 * Use the split values with indices `[`firstSplit..lastSplit`]` to
	 * determine which target to jump to.  For example if `firstSplit = 1` and
	 * `lastSplit = 1`, it should test against splitPoints`[`1`]` and branch to
	 * either edges`[`1`]` or edges`[`2`]`.  As another example, if
	 * `firstSplit = 5` and `lastSplit = 4`, this indicates edges`[`5`]` should
	 * be used without a further test.
	 *
	 * @receiver
	 *   The [L2GeneratorInterface] on which to generate the subtree.
	 * @param value
	 *   The [L2ReadIntOperand] value to generate the subtree for.
	 * @param splitter
	 *   The [AbstractMultiWaySplitter] controlling this multi-way jump.
	 * @param originalValueSource
	 *   If available, this is the [L2SemanticBoxedValue] representing the
	 *   original from which the int value has been extracted, such as a tag or
	 *   variant id.
	 * @param edges
	 *   The edges list as a [List] of [L2PcOperand]s.  There should be one more
	 *   than there are split points in the [splitter].
	 * @param firstSplit
	 *   The index of the first split point.
	 * @param lastSplit
	 *   The index of the last split point.
	 * @param zone
	 *   The optional [Zone] in which to create new basic blocks.
	 */
	private fun L2GeneratorInterface.generateSubtree(
		value: L2ReadIntOperand,
		splitter: AbstractMultiWaySplitter,
		originalValueSource: L2SemanticBoxedValue?,
		edges: List<L2PcOperand>,
		firstSplit: Int,
		lastSplit: Int,
		zone: Zone?)
	{
		if (!currentlyReachable()) return
		if (firstSplit > lastSplit)
		{
			// It's a leaf.
			assert(firstSplit == lastSplit + 1)
			val edge = edges[firstSplit - 1]  // Convert to zero-based.
			+L2_JUMP(edge)
			return
		}
		if (lastSplit - firstSplit < 10)
		{
			// It's not overly complex, so see if a small number of equality
			// checks are suitable, versus having to produce a binary search.
			val boundaries = (firstSplit - 1 .. lastSplit + 1)
				.map { splitIndex ->
					when (splitIndex)
					{
						firstSplit - 1 -> value.type().lowerBound.extractInt
						lastSplit + 1 -> value.type().upperBound.extractInt + 1
						else -> splitter.splitPoints[splitIndex - 1]
					}
				}
			// Find spans containing more than one int, and see if they all lead
			// to the same target block.
			val spanTargets = (0..boundaries.size - 2)
				.filter { i -> boundaries[i] + 1 < boundaries[i + 1] }
				.mapToSet {
					edges[firstSplit - 1 + it].targetBlockSkippingBareJumps()
				}
			if (spanTargets.size <= 1)
			{
				// The choices are all singular int ranges, except possibly some
				// non-singular ranges that all lead to the same target block.
				(0..boundaries.size - 2).forEach { i ->
					val boundary = boundaries[i]
					if (boundary + 1 == boundaries[i + 1])
					{
						val ifUnequal = createBasicBlock("not $boundary")
						+L2_JUMP_IF_COMPARE_INT(
							L2ArbitraryConstantOperand(Equal),
							value,
							unboxedIntConstant(boundary),
							edges[firstSplit - 1 + i],
							edgeTo(ifUnequal))
						startBlock(ifUnequal)
					}
				}
				if (spanTargets.size == 1)
				{
					+L2_JUMP(edgeTo(spanTargets.single()))
				}
				return
			}
		}
		// It's not a leaf.  Pick a split point near the middle of the range.

		val splitIndex = (firstSplit + lastSplit) ushr 1
		val splitValue = splitter.splitPoints[splitIndex - 1]
		val (leftName, rightName) = splitter.leftAndRightTargetNames(
			currentManifest.restrictionFor(value.semanticValue()),
			splitValue)
		val leftBlock = createBasicBlock(leftName, zone)
		val rightBlock = createBasicBlock(rightName, zone)
		compareAndBranchInt(
			GreaterOrEqual,
			value,
			unboxedIntConstant(splitValue),
			L2PcOperand(
				rightBlock,
				false,
				optionalName = rightName),
			L2PcOperand(
				leftBlock,
				false,
				optionalName = leftName))
		// Recursively generate the left side.
		startBlock(leftBlock)
		generateSubtree(
			value,
			splitter,
			originalValueSource,
			edges,
			firstSplit,
			splitIndex - 1,
			zone)
		// Recursively generate the right side.
		startBlock(rightBlock)
		generateSubtree(
			value,
			splitter,
			originalValueSource,
			edges,
			splitIndex + 1,
			lastSplit,
			zone)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor)
	{
		val splits = splitter.constant.splitPoints
		val edges = branchEdges.edges
		val lowerBound = value.type().lowerBound.extractInt
		if (lowerBound < splits[0])
		{
			// Handle values below the lowest split value.
			translator.load(method, value)
			translator.intConstant(method, splits[0])
			val notTooLow = Label()
			method.visitJumpInsn(GreaterOrEqual.opcode, notTooLow)
			// At this point the value is left of the first split.
			translator.jump(method, edges[0])
			method.visitLabel(notTooLow)
		}
		// At this point the value is at or after the first split.
		val targetsToLabels = edges
			.mapToSet(transform = L2PcOperand::targetBlock)
			.associateWith { Label() }
		val labelTable = buildList<Label> {
			splits.zipWithNext().forEachIndexed { i, (start, pastEnd) ->
				val targetBlock = edges[i+1].targetBlock()
				val label = targetsToLabels[targetBlock]!!
				repeat(pastEnd - start) {
					add(label)
				}
			}
		}
		translator.load(method, value)
		method.visitTableSwitchInsn(
			splits.first(),
			splits.last() - 1,
			targetsToLabels[edges.last().targetBlock()],
			*labelTable.toTypedArray())
		targetsToLabels.forEach { target, label ->
			method.visitLabel(label)
			translator.jump(method, target)
		}
	}
}
