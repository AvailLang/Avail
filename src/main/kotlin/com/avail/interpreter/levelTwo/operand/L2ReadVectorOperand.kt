/*
 * L2ReadVectorOperand.java
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
package com.avail.interpreter.levelTwo.operand

import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandDispatcher
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.optimizer.L2ValueManifest
import com.avail.utility.Casts
import java.util.*

/**
 * An `L2ReadVectorOperand` is an operand of type
 * [L2OperandType.READ_BOXED_VECTOR]. It holds a [List] of [L2ReadOperand]s.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @param RR
 *   A subclass of [L2ReadOperand]&lt;R>.
 * @param R
 *   A subclass of L2Register
 */
abstract class L2ReadVectorOperand<RR : L2ReadOperand<R>, R
	: L2Register> constructor(elements: List<RR>) : L2Operand()
{
	/**
	 * The [List] of [L2ReadBoxedOperand]s.
	 */
	val elements: List<RR> = Collections.unmodifiableList(elements)

	abstract override fun clone(): L2ReadVectorOperand<RR, R>

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
		replacementElements: List<RR>): L2ReadVectorOperand<RR, R>

	override fun assertHasBeenEmitted()
	{
		super.assertHasBeenEmitted()
		elements.forEach { it.assertHasBeenEmitted() }
	}

	abstract override fun operandType(): L2OperandType

	/**
	 * Answer my [List] of [L2ReadOperand]s.
	 *
	 * @return
	 *   The requested operands.
	 */
	fun elements(): List<RR> = elements

	/**
	 * Answer a [List] of my elements' [L2Register]s.
	 *
	 * @return
	 *   The list of [L2Register]s that I read.
	 */
	fun registers(): List<R> = elements.map { obj: RR -> obj.register() }

	abstract override fun dispatchOperand(dispatcher: L2OperandDispatcher)

	override fun instructionWasAdded(manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		elements.forEach{ it.instructionWasAdded(manifest) }
	}

	override fun adjustedForReinsertion(manifest: L2ValueManifest)
		: L2ReadVectorOperand<RR, R>
	{
		val newElements: MutableList<RR> = mutableListOf()
		for (element in elements)
		{
			val newElement = Casts.cast<L2ReadOperand<*>, RR>(
				element.adjustedForReinsertion(manifest))
			newElements.add(newElement)
		}
		return clone(newElements)
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
		registerRemap: Map<L2Register, L2Register>,
		theInstruction: L2Instruction)
	{
		elements.forEach { it.replaceRegisters(registerRemap, theInstruction) }
	}

	override fun addReadsTo(readOperands: MutableList<L2ReadOperand<*>>)
	{
		readOperands.addAll(elements)
	}

	override fun transformEachRead(
			transformer: (L2ReadOperand<*>) -> (L2ReadOperand<*>))
		: L2ReadVectorOperand<RR, R>
	{
		val vs: List<RR> = elements.map {
			val x: RR = Casts.cast(it.transformEachRead(transformer))
			x
		}
		return clone(vs)
	}

	override fun addSourceRegistersTo(sourceRegisters: MutableList<L2Register>)
	{
		elements.forEach { it.addSourceRegistersTo(sourceRegisters) }
	}

	override fun setInstruction(theInstruction: L2Instruction?)
	{
		super.setInstruction(theInstruction)
		// Also update the instruction fields of its L2ReadOperands.
		elements.forEach { it.setInstruction(theInstruction) }
	}

	override fun appendTo(builder: StringBuilder) =
		with(builder)
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
				if (restriction.constantOrNull == null)
				{
					// Don't redundantly print restriction information for
					// constants.
					append(restriction.suffixString())
				}
				first = false
			}
			append(">")
			Unit
		}
}