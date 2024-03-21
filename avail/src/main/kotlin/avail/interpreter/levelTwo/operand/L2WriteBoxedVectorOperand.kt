/*
 * L2WriteBoxedVectorOperand.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.interpreter.levelTwo.operand

import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.WRITE_BOXED_VECTOR
import avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2ValueManifest
import avail.utility.cast
import java.util.Collections.unmodifiableList

/**
 * An [L2WriteBoxedVectorOperand] is an operand of type [WRITE_BOXED_VECTOR]. It
 * holds a [List] of [L2WriteBoxedOperand]s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2WriteBoxedVectorOperand
constructor(
	elements: List<L2WriteBoxedOperand>
) : L2Operand()
{
	/** The [List] of [L2WriteBoxedOperand]s. */
	protected val privateElements: List<L2WriteBoxedOperand> =
		unmodifiableList(elements)

	/** The [List] of [L2WriteBoxedOperand]s. */
	val elements: List<L2WriteBoxedOperand> get() = privateElements

	override fun clone(): L2WriteBoxedVectorOperand =
		L2WriteBoxedVectorOperand(elements.map { it.clone().cast() })

	/**
	 * Create a write vector like this one, but using the provided elements.
	 *
	 * @param replacementElements
	 *   The [List] of [L2WriteBoxedOperand]s to use in the clone.
	 * @return
	 *   A new [L2WriteBoxedVectorOperand] having the given elements.
	 */
	fun clone(
		replacementElements: List<L2WriteBoxedOperand>
	): L2WriteBoxedVectorOperand =
		L2WriteBoxedVectorOperand(replacementElements.cast())

	override fun assertHasBeenEmitted()
	{
		super.assertHasBeenEmitted()
		elements.forEach { it.assertHasBeenEmitted() }
	}

	override val operandType: L2OperandType get() = WRITE_BOXED_VECTOR

	/**
	 * Answer a [List] of my elements' [L2Register]s.
	 *
	 * @return
	 *   The list of [L2Register]s that I read.
	 */
	fun registers(): List<L2BoxedRegister> =
		elements.map(L2WriteBoxedOperand::register).cast()

	override fun dispatchOperand(dispatcher: L2OperandDispatcher) =
		dispatcher.doOperand(this)

	override fun instructionWasAdded(manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		elements.forEach{ it.instructionWasAdded(manifest) }
	}

	override fun instructionWasInserted(newInstruction: L2Instruction)
	{
		super.instructionWasInserted(newInstruction)
		elements.forEach { it.instructionWasInserted(newInstruction) }
	}

	override fun instructionWasRemoved()
	{
		super.instructionWasRemoved()
		elements.forEach { it.instructionWasRemoved() }
	}

	override fun replaceRegisters(
		registerRemap: Map<L2Register<*>, L2Register<*>>,
		theInstruction: L2Instruction)
	{
		elements.forEach { it.replaceRegisters(registerRemap, theInstruction) }
	}

	override fun addWritesTo(writeOperands: MutableList<L2WriteOperand<*>>)
	{
		writeOperands.addAll(elements)
	}

	override fun addDestinationRegistersTo(
		destinationRegisters: MutableList<L2Register<*>>)
	{
		elements.forEach { it.addDestinationRegistersTo(destinationRegisters) }
	}

	override fun setInstruction(theInstruction: L2Instruction?)
	{
		super.setInstruction(theInstruction)
		// Also update the instruction fields of its L2WriteBoxedOperands.
		elements.forEach { it.setInstruction(theInstruction) }
	}

	override fun appendTo(builder: StringBuilder): Unit = with(builder)
	{
		append("→@<")
		var first = true
		for (write in elements)
		{
			if (!first)
			{
				append(", ")
			}
			append(write.registerString())
			first = false
		}
		append(">")
	}

	override fun postOptimizationCleanup() =
		elements.forEach(L2WriteBoxedOperand::postOptimizationCleanup)

	/**
	 * This vector operand is the input to an [L2_PHI_PSEUDO_OPERATION]
	 * instruction that has just been added.  Update it specially, to take into
	 * account the correspondence between vector elements and predecessor edges.
	 *
	 * @param predecessorEdges
	 *   The [List] of predecessor edges ([L2PcOperand]s) that correspond
	 *   positionally with the elements of the vector.
	 */
	fun instructionWasAddedForPhi(predecessorEdges: List<L2PcOperand>)
	{
		val fanIn = elements.size
		assert(fanIn == predecessorEdges.size)
		for (i in 0 until fanIn)
		{
			// The read operand should use the corresponding incoming manifest.
			elements[i].instructionWasAdded(predecessorEdges[i].manifest())
		}
	}
}
