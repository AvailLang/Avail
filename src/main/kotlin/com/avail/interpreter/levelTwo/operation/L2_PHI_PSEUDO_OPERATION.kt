/*
 * L2_PHI_PSEUDO_OPERATION.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 *   may be used to endorse or promote products derived from this software
 *   without specific prior written permission.
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
package com.avail.interpreter.levelTwo.operation

import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2NamedOperandType
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2Operand
import com.avail.interpreter.levelTwo.operand.L2Operand.Companion.instructionWasAddedForPhi
import com.avail.interpreter.levelTwo.operand.L2PcOperand
import com.avail.interpreter.levelTwo.operand.L2ReadOperand
import com.avail.interpreter.levelTwo.operand.L2ReadVectorOperand
import com.avail.interpreter.levelTwo.operand.L2WriteOperand
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.optimizer.L2BasicBlock
import com.avail.optimizer.L2ControlFlowGraph
import com.avail.optimizer.L2Generator
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.jvm.JVMTranslator
import org.objectweb.asm.MethodVisitor

/**
 * The `L2_PHI_PSEUDO_OPERATION` occurs at the start of a [L2BasicBlock].  It's
 * a convenient fiction that allows an [L2ControlFlowGraph] to be in Static
 * Single Assignment form (SSA), where each [L2Register] has exactly one
 * instruction that writes to it.
 *
 * The vector of source registers are in the same order as the corresponding
 * predecessors of the containing [L2BasicBlock].  The runtime effect
 * would be to select from that vector, based on the predecessor from which
 * control arrives, and move that register's value to the destination register.
 * However, that's a fiction, and the phi operation is instead removed during
 * the transition of the control flow graph out of SSA, being replaced by move
 * instructions along each incoming edge.
 *
 * @param R
 * The kind of [L2Register] to merge.
 * @param RR
 * The kind of [L2ReadOperand]s to merge.
 * @param WR
 * The kind of [L2WriteOperand]s to write the result to.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property moveOperation
 *   The [L2_MOVE] operation to substitute for this instruction on incoming
 *   split edges.
 * @constructor
 * Construct an `L2_PHI_PSEUDO_OPERATION`.
 *
 * @param moveOperation
 *   The [L2_MOVE] operation to substitute for this instruction on incoming
 *   split edges.
 * @param theNamedOperandTypes
 *   An array of [L2NamedOperandType]s that describe this particular
 *   L2Operation, allowing it to be specialized by register type.
 */
class L2_PHI_PSEUDO_OPERATION<R : L2Register, RR : L2ReadOperand<R>, WR : L2WriteOperand<R>>
private constructor(
	val moveOperation: L2_MOVE<R, RR, WR>,
	vararg theNamedOperandTypes: L2NamedOperandType) : L2Operation(*theNamedOperandTypes)
{
	override fun propagateTypes(
		instruction: L2Instruction,
		registerSet: RegisterSet,
		generator: L2Generator)
	{
		val sources: L2ReadVectorOperand<RR, R> = instruction.operand(0)
		val destination: L2WriteOperand<R> = instruction.operand(1)
		val iterator = sources.elements().iterator()
		assert(iterator.hasNext())
		var restriction = iterator.next().restriction()
		while (iterator.hasNext())
		{
			restriction = restriction.union(iterator.next().restriction())
		}
		registerSet.removeConstantAt(destination.register())
		registerSet.removeTypeAt(destination.register())
		registerSet.typeAtPut(
			destination.register(), restriction.type, instruction)
		restriction.constantOrNull?.let {
			registerSet.constantAtPut(
				destination.register(),
				it,
				instruction)
		}
	}

	override val isPhi: Boolean
		get() = true

	override fun instructionWasAdded(
		instruction: L2Instruction, manifest: L2ValueManifest)
	{
		// The reads in the input vector are from the positionally corresponding
		// incoming edges, which carry the manifests that should be used to
		// look up the best source semantic values.
		val sources: L2ReadVectorOperand<RR, R> = instruction.operand(0)
		val destination: L2WriteOperand<R> = instruction.operand(1)
		val predecessorEdges =
			instruction.basicBlock().predecessorEdgesCopy()
		instructionWasAddedForPhi(sources, predecessorEdges)
		destination.instructionWasAdded(manifest)
	}

	override fun shouldEmit(
		instruction: L2Instruction): Boolean
	{
		// Phi instructions are converted to moves along predecessor edges.
		return false
	}

	/**
	 * One of this phi function's predecessors has been removed because it's
	 * dead code.  Clean up its vector of inputs by removing the specified
	 * index.
	 *
	 * @param instruction
	 *   The [L2Instruction] whose operation has this type.
	 * @param inputIndex
	 *   The index to remove.
	 * @return
	 *   A replacement [L2Instruction], whose operation may be either another
	 *   `L2_PHI_PSEUDO_OPERATION` or an [L2_MOVE].
	 */
	fun withoutIndex(
		instruction: L2Instruction,
		inputIndex: Int): L2Instruction
	{
		val oldVector: L2ReadVectorOperand<RR, R> = instruction.operand(0)
		val destinationReg: L2WriteOperand<R> = instruction.operand(1)
		val newSources = oldVector.elements().toMutableList()
		newSources.removeAt(inputIndex)
		val onlyOneRegister = newSources.size == 1
		return if (onlyOneRegister)
		{
			// Replace the phi function with a simple move.
			L2Instruction(
				instruction.basicBlock(),
				moveOperation,
				newSources[0],
				destinationReg)
		}
		else L2Instruction(
			instruction.basicBlock(),
			this,
			oldVector.clone(newSources),
			destinationReg)
	}

	/**
	 * Replace this phi by providing a lambda that alters a copy of the list of
	 * [L2ReadOperand]s that it's passed.  The predecessor edges are expected to
	 * correspond with the inputs.  Do not attempt to normalize the phi to a
	 * move.
	 *
	 * @param instruction
	 *   The phi instruction to augment.
	 * @param updater
	 *   What to do to a copied mutable [List] of read operands that starts out
	 *   having all of the vector operand's elements.
	 */
	fun updateVectorOperand(
		instruction: L2Instruction,
		updater: (MutableList<RR>) -> Unit)
	{
		assert(instruction.operation() === this)
		val block = instruction.basicBlock()
		val instructionIndex = block.instructions().indexOf(instruction)
		val vectorOperand: L2ReadVectorOperand<RR, R> = instruction.operand(0)
		val writeOperand: L2WriteOperand<R> = instruction.operand(1)
		instruction.justRemoved()
		val passedCopy = vectorOperand.elements().toMutableList()
		updater(passedCopy)
		val finalCopy: List<RR> = passedCopy.toList()
		val replacementInstruction = L2Instruction(
			block, this, vectorOperand.clone(finalCopy), writeOperand)
		block.instructions()[instructionIndex] = replacementInstruction
		replacementInstruction.justInserted()
	}

	/**
	 * Examine the instruction and answer the predecessor [L2BasicBlock]s
	 * that supply a value from the specified register.
	 *
	 * @param instruction
	 *   The phi-instruction to examine.
	 * @param usedRegister
	 *   The [L2Register] whose use we're trying to trace back to its
	 *   definition.
	 * @return
	 *   A [List] of predecessor blocks that supplied the usedRegister as an
	 *   input to this phi operation.
	 */
	fun predecessorBlocksForUseOf(
		instruction: L2Instruction,
		usedRegister: L2Register): List<L2BasicBlock>
	{
		assert(this == instruction.operation())
		val sources: L2ReadVectorOperand<RR, R> = instruction.operand(0)
		assert(sources.elements().size
				   == instruction.basicBlock().predecessorEdgesCount())
		val list = mutableListOf<L2BasicBlock>()
		var i = 0
		instruction.basicBlock().predecessorEdgesDo { edge: L2PcOperand ->
			if (sources.elements()[i++].register() === usedRegister)
			{
				list.add(edge.sourceBlock())
			}
		}
		return list
	}

	/**
	 * Answer the [L2WriteOperand] from this phi function.  This should only be
	 * used when generating phi moves (which takes the [L2ControlFlowGraph] out
	 * of Static Single Assignment form).
	 *
	 * @param W
	 *   The type of the [L2WriteOperand].
	 * @param instruction
	 *   The instruction to examine. It must be a phi operation.
	 * @return
	 *   The instruction's destination [L2WriteOperand].
	 */
	fun <W : L2WriteOperand<R>> destinationRegisterWrite(instruction: L2Instruction): W
	{
		assert(this == instruction.operation())
		return instruction.operand(1)
	}

	/**
	 * Answer the [List] of [L2ReadOperand]s for this phi function.
	 * The order is correlated to the instruction's blocks predecessorEdges.
	 *
	 * @param instruction
	 *   The phi instruction.
	 * @return
	 *   The instruction's list of sources.
	 */
	fun sourceRegisterReads(instruction: L2Instruction): List<RR>
	{
		val vector: L2ReadVectorOperand<RR, R> = instruction.operand(0)
		return vector.elements()
	}

	/**
	 * Update an `L2_PHI_PSEUDO_OPERATION` instruction that's in a loop head
	 * basic block.
	 *
	 * @param predecessorManifest
	 *   The [L2ValueManifest] in some predecessor edge.
	 * @param instruction
	 *   The phi instruction itself.
	 */
	fun updateLoopHeadPhi(
		predecessorManifest: L2ValueManifest, instruction: L2Instruction)
	{
		assert(instruction.operation() === this)
		val semanticValue =
			sourceRegisterReads(instruction)[0].semanticValue()
		val kind = moveOperation.kind
		val readOperand: RR = kind.readOperand(
			semanticValue,
			predecessorManifest.restrictionFor(semanticValue),
			predecessorManifest.getDefinition(semanticValue, kind))
		updateVectorOperand(instruction) { it.add(readOperand) }
	}

	override fun toString(): String =
		super.toString() + "(" + moveOperation.kind.kindName + ")"

	override fun appendToWithWarnings(
		instruction: L2Instruction,
		desiredTypes: Set<L2OperandType>,
		builder: StringBuilder,
		warningStyleChange: (Boolean) -> Unit)
	{
		assert(this == instruction.operation())
		val vector = instruction.operand<L2Operand>(0)
		val target = instruction.operand<L2Operand>(1)
		builder.append("ϕ ")
		builder.append(target)
		builder.append(" ← ")
		builder.append(vector)
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		throw UnsupportedOperationException(
			"This instruction should be factored out before JVM translation")
	}

	companion object
	{
		/**
		 * Initialize the instance used for merging boxed values.
		 */
		@kotlin.jvm.JvmField
		val boxed = L2_PHI_PSEUDO_OPERATION(
			L2_MOVE.boxed,
			L2OperandType.READ_BOXED_VECTOR.named("potential boxed sources"),
			L2OperandType.WRITE_BOXED.named("boxed destination"))

		/**
		 * Initialize the instance used for merging boxed values.
		 */
		@kotlin.jvm.JvmField
		val unboxedInt = L2_PHI_PSEUDO_OPERATION(
			L2_MOVE.unboxedInt,
			L2OperandType.READ_INT_VECTOR.named("potential int sources"),
			L2OperandType.WRITE_INT.named("int destination"))

		/**
		 * Initialize the instance used for merging boxed values.
		 */
		@kotlin.jvm.JvmField
		val unboxedFloat = L2_PHI_PSEUDO_OPERATION(
			L2_MOVE.unboxedFloat,
			L2OperandType.READ_FLOAT_VECTOR.named("potential float sources"),
			L2OperandType.WRITE_FLOAT.named("float destination"))
	}
}
