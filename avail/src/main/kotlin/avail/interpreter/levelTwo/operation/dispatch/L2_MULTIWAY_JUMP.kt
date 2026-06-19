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

import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.operand.L2ArbitraryConstantOperand
import avail.interpreter.levelTwo.operand.L2PcOperand
import avail.interpreter.levelTwo.operand.L2PcVectorOperand
import avail.interpreter.levelTwo.operand.L2ReadIntOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.L2ConditionalJump
import avail.interpreter.levelTwo.operation.NumericComparator.GreaterOrEqual
import avail.optimizer.L2BasicBlock
import avail.optimizer.L2GeneratorInterface
import avail.optimizer.L2Optimizer.GenerationMode.BySemanticValue
import avail.optimizer.L2SplitCondition
import avail.optimizer.L2ValueManifest
import avail.optimizer.jvm.JVMTranslator
import avail.utility.mapToSet
import org.objectweb.asm.Label

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
		ignoreMisconnections: Boolean,
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
			splitter.constant.populateEdgeManifests(
				value, branchEdges.edges, manifest)
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

	/** Delegate to the MultiWaySplitter. */
	override fun interestingConditions(): List<L2SplitCondition?> =
		splitter.constant.interestingConditions(value, branchEdges.edges)

	override fun L2GeneratorInterface.generateConditionalReplacement(
		originalInstruction: L2Instruction)
	{
		+splitter.constant.run {
			reducedSplitterInstruction(
				value, branchEdges.edges, currentManifest)
		}
	}

	override fun JVMTranslator.translateToJVM()
	{
		val targetsToLabels = branchEdges.edges
			.mapToSet(transform = L2PcOperand::targetBlock)
			.associateWith { Label() }
		translateRegionToJVM(
			0,
			splitter.constant.splitPoints.size - 1,
			value.restriction(),
			targetsToLabels)
	}

	/**
	 * Generate JVM code to dispatch values in the given range of splits, with
	 * knowledge that the value being dispatched falls in the given restriction.
	 * Here is where the decision is made whether to use a tablelookup
	 * instruction or a tree of branches, or some combination.
	 *
	 * @receiver
	 *   The [JVMTranslator] on which to write the dispatch code.
	 * @param firstSplitIndex
	 *   The zero-based index into the [List] of splitPoints of the first split
	 *   value to be tested.
	 * @param lastSplitIndex
	 *   The zero-based index into the [List] of splitPoints of the last split
	 *   value to be tested.  If this is less than [firstSplitIndex], only one
	 *   edge is accessible.
	 * @param restriction
	 *   The [TypeRestriction] that bounds the dispatch value at this point.
	 * @param targetsToLabels
	 *   A [Map] from each outgoing edge's target block to a [Label].
	 */
	private fun JVMTranslator.translateRegionToJVM(
		firstSplitIndex: Int,
		lastSplitIndex: Int,
		restriction: TypeRestriction,
		targetsToLabels: Map<L2BasicBlock, Label>)
	{
		assert(!restriction.isImpossible)
		if (firstSplitIndex > lastSplitIndex)
		{
			jump(branchEdges.edges[firstSplitIndex])
			return
		}
		val splits = splitter.constant.splitPoints
		val edges = branchEdges.edges
		val splitCount = lastSplitIndex - firstSplitIndex
		val splitSpan = splits[lastSplitIndex] - splits[firstSplitIndex]
		val useLookupSwitch = when
		{
			// Too few entries to bother with a lookupswitch.
			splitCount <= 3 -> false
			// Entries are too sparse for a lookupswitch.
			splitSpan > splitCount * 100L -> false
			// Sure, use a lookupswitch.
			else -> true
		}
		if (!useLookupSwitch)
		{
			// Split at the median split point and recurse.
			val medianIndex = (firstSplitIndex + lastSplitIndex) / 2
			val medianValue = splits[medianIndex]
			val greaterOrEqualLabel = Label()
			load(value)
			intConstant(medianValue)
			method.visitJumpInsn(GreaterOrEqual.opcode, greaterOrEqualLabel)
			translateRegionToJVM(
				firstSplitIndex,
				medianIndex - 1,
				restriction.intersectionWithType(
					inclusive(Int.MIN_VALUE, medianValue - 1)),
				targetsToLabels)
			method.visitLabel(greaterOrEqualLabel)
			translateRegionToJVM(
				medianIndex + 1,
				lastSplitIndex,
				restriction.intersectionWithType(
					inclusive(medianValue, Int.MAX_VALUE)),
				targetsToLabels)
			return
		}
		// Produce a lookupswitch instruction.
		val lowerBound = restriction.type.lowerBound.extractInt
		if (lowerBound < splits[firstSplitIndex]
			&& edges[firstSplitIndex].targetBlock()
			!= edges[lastSplitIndex + 1].targetBlock())
		{
			// Handle values below the lowest split value, but only because such
			// a value is possible *and* the first and last edges go somewhere
			// different, precluding a trivial use of the default target of the
			// tablelookup instruction.
			load(value)
			intConstant(splits[firstSplitIndex])
			val notTooLow = Label()
			method.visitJumpInsn(GreaterOrEqual.opcode, notTooLow)
			// At this point the value is left of the first split.
			jump(edges[firstSplitIndex])
			method.visitLabel(notTooLow)
		}
		// At this point the value is at or after the first split.
		val labelTable = buildList<Label> {
			(firstSplitIndex .. lastSplitIndex - 1).forEach { i ->
				val startOfRun = splits[i]
				val pastEndOfRun = splits[i + 1]
				val block = edges[i + 1].targetBlock()
				val label = targetsToLabels[block]!!
				repeat(pastEndOfRun - startOfRun) {
					add(label)
				}
			}
		}
		assert(labelTable.isNotEmpty())
		load(value)
		method.visitTableSwitchInsn(
			splits[firstSplitIndex],
			splits[lastSplitIndex] - 1,
			targetsToLabels[edges[lastSplitIndex + 1].targetBlock()],
			*labelTable.toTypedArray())
		targetsToLabels.forEach { target, label ->
			method.visitLabel(label)
			jump(target)
		}
	}
}
