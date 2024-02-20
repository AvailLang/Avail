/*
 * L2ReadVectorOperand.kt
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
import avail.interpreter.levelTwo.operation.L2_PHI_PSEUDO_OPERATION
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.RegisterKind
import avail.optimizer.L2ValueManifest
import java.util.Collections.unmodifiableList

/**
 * An `L2ReadVectorOperand` is an operand of type
 * [L2OperandType.READ_BOXED_VECTOR]. It holds a [List] of [L2ReadOperand]s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @param K
 *   The [RegisterKind].
 */
abstract class L2ReadVectorOperand<K: RegisterKind<K>>
constructor(
	elements: List<L2ReadOperand<K>>
) : L2Operand()
{
	/**
	 * The [List] of [L2ReadBoxedOperand]s.
	 */
	val elements: List<L2ReadOperand<K>> = unmodifiableList(elements)

	abstract override fun clone(): L2ReadVectorOperand<K>

	/**
	 * Create a vector like this one, but using the provided elements.
	 *
	 * @param replacementElements
	 *   The [List] of [L2ReadOperand]s to use in the clone.
	 * @return
	 *   A new `L2ReadVectorOperand`, of the same type as the receiver, but
	 *   having the given elements.
	 */
	abstract fun clone(
		replacementElements: List<L2ReadOperand<K>>
	): L2ReadVectorOperand<K>

	override fun assertHasBeenEmitted()
	{
		super.assertHasBeenEmitted()
		elements.forEach { it.assertHasBeenEmitted() }
	}

	abstract override val operandType: L2OperandType

	/**
	 * Answer a [List] of my elements' [L2Register]s.
	 *
	 * @return
	 *   The list of [L2Register]s that I read.
	 */
	fun registers(): List<L2Register<K>> =
		elements.map(L2ReadOperand<K>::register)

	abstract override fun dispatchOperand(dispatcher: L2OperandDispatcher)

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

	override fun addReadsTo(readOperands: MutableList<L2ReadOperand<*>>)
	{
		readOperands.addAll(elements)
	}

	override fun transformEachRead(
		transformer: (L2ReadOperand<*>) -> (L2ReadOperand<*>)
	) : L2ReadVectorOperand<K> =
		clone(elements.map { it.transformEachRead(transformer) })

	override fun addSourceRegistersTo(
		sourceRegisters: MutableList<L2Register<*>>)
	{
		elements.forEach { it.addSourceRegistersTo(sourceRegisters) }
	}

	override fun setInstruction(theInstruction: L2Instruction?)
	{
		super.setInstruction(theInstruction)
		// Also update the instruction fields of its L2ReadOperands.
		elements.forEach { it.setInstruction(theInstruction) }
	}

	override fun appendTo(builder: StringBuilder): Unit = with(builder)
	{
		append("@<")
		var first = true
		for (read in elements)
		{
			if (!first)
			{
				append(", ")
			}
			append(read.registerString())
			val restriction = read.restriction()
			if (restriction.constantOrNull === null)
			{
				// Don't redundantly print restriction information for
				// constants.
				append(restriction.suffixString())
			}
			first = false
		}
		append(">")
	}

	override fun postOptimizationCleanup() =
		elements.forEach(L2ReadOperand<*>::postOptimizationCleanup)

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
