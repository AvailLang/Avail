/*
 * L2WriteOperand.java
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
import com.avail.interpreter.levelTwo.operation.L2_MAKE_IMMUTABLE
import com.avail.interpreter.levelTwo.register.L2IntRegister
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.interpreter.levelTwo.register.L2Register.RegisterKind
import com.avail.optimizer.L2ValueManifest
import com.avail.optimizer.values.L2SemanticValue
import com.avail.utility.cast
import java.util.HashSet

/**
 * `L2WriteOperand` abstracts the capabilities of actual register write
 * operands.
 *
 * @param R
 * The subclass of [L2Register] that this writes to.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @property restriction
 *   The [TypeRestriction] that indicates what values may be written to the
 *   destination register.
 * @property register
 *   The actual [L2Register]. This is only set during late optimization of the
 *   control flow graph.
 *
 * @constructor
 * Construct a new `L2WriteOperand` for the specified [L2SemanticValue].
 *
 * @param semanticValues
 *   The [Set] of [L2SemanticValue] that this operand is effectively producing.
 * @param restriction
 *   The [TypeRestriction] that indicates what values are allowed to be written
 *   into the register.
 * @param register
 *   The [L2Register] to write.
 */
abstract class L2WriteOperand<R : L2Register> constructor(
		semanticValues: Set<L2SemanticValue>,
		private val restriction: TypeRestriction,
		protected var register: R)
	: L2Operand()
{
	/**
	 * The [L2SemanticValue]s being written when an [L2Instruction] uses this
	 * [L2Operand].
	 */
	private val semanticValues: MutableSet<L2SemanticValue> =
		HashSet(semanticValues)

	/**
	 * Answer this write's immutable set of [L2SemanticValue]s.
	 *
	 * @return
	 *   The semantic value being written.
	 */
	fun semanticValues(): Set<L2SemanticValue> = semanticValues

	/**
	 * Answer this write's sole [L2SemanticValue], failing if there isn't
	 * exactly one.
	 *
	 * @return
	 *   The write operand's [L2SemanticValue].
	 */
	fun onlySemanticValue(): L2SemanticValue
	{
		assert(semanticValues.size == 1)
		return semanticValues.iterator().next()
	}

	/**
	 * Choose an arbitrary one of the [L2SemanticValue]s that this operand
	 * writes.
	 *
	 * @return
	 *   The write operand's [L2SemanticValue].
	 */
	fun pickSemanticValue(): L2SemanticValue =
		semanticValues.iterator().next()

	/**
	 * Answer this write's [TypeRestriction].
	 *
	 * @return
	 *   The [TypeRestriction] that constrains what's being written.
	 */
	fun restriction(): TypeRestriction = restriction

	/**
	 * Answer the [RegisterKind] of register that is written by this
	 * `L2WriteOperand`.
	 *
	 * @return
	 *   The [RegisterKind].
	 */
	abstract fun registerKind(): RegisterKind

	/**
	 * Answer the [L2Register]'s [finalIndex][L2Register.finalIndex].
	 *
	 * @return
	 *   The index of the register, computed during register coloring.
	 */
	fun finalIndex(): Int = register.finalIndex()

	/**
	 * Answer the register that is to be written.
	 *
	 * @return
	 *   An [L2IntRegister].
	 */
	fun register(): R = register

	/**
	 * Answer a String that describes this operand for debugging.
	 *
	 * @return
	 *   A [String].
	 */
	fun registerString(): String =
		if (semanticValues.isNotEmpty()) register.toString() + semanticValues
		else register.toString()


	override fun instructionWasAdded(manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		register.addDefinition(this)
		manifest.recordDefinition(this)
	}

	/**
	 * This operand is a write of an [L2_MAKE_IMMUTABLE] operation.  The
	 * manifest has already had boxed definitions for the synonym removed from
	 * it, even if it left the definition list empty.
	 *
	 * @param sourceSemanticValue
	 *   The [L2SemanticValue] that already holds the value.
	 * @param manifest
	 *   The [L2ValueManifest] in which to capture the synonymy of the source
	 *   and destination.
	 */
	fun instructionWasAddedForMakeImmutable(
		sourceSemanticValue: L2SemanticValue,
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		register.addDefinition(this)
		assert(manifest.hasSemanticValue(sourceSemanticValue))
		manifest.recordDefinitionForMakeImmutable(this, sourceSemanticValue)
	}

	/**
	 * This operand is a write of a move-like operation.  Make the semantic
	 * value a synonym of the given [L2ReadOperand]'s semantic value.
	 *
	 * @param sourceSemanticValue
	 *   The [L2SemanticValue] that already holds the value.
	 * @param manifest
	 *   The [L2ValueManifest] in which to capture the synonymy of the source
	 *   and destination.
	 */
	fun instructionWasAddedForMove(
		sourceSemanticValue: L2SemanticValue,
		manifest: L2ValueManifest)
	{
		super.instructionWasAdded(manifest)
		register.addDefinition(this)
		manifest.recordDefinitionForMove(this, sourceSemanticValue)
	}

	override fun instructionWasInserted(newInstruction: L2Instruction)
	{
		super.instructionWasInserted(newInstruction)
		register.addDefinition(this)
	}

	override fun instructionWasRemoved()
	{
		super.instructionWasRemoved()
		register().removeDefinition(this)
	}

	/**
	 * Add the given [L2SemanticValue] to this write operand's set of semantic
	 * values.  DO NOT update any other structures to reflect this change, as
	 * this is the caller's responsibility.
	 *
	 * @param newSemanticValue
	 *   The new [L2SemanticValue] to add to the write operand's set of semantic
	 *   values.
	 */
	fun retroactivelyIncludeSemanticValue(newSemanticValue: L2SemanticValue)
	{
		semanticValues.add(newSemanticValue)
	}

	override fun replaceRegisters(
		registerRemap: Map<L2Register, L2Register>,
		theInstruction: L2Instruction)
	{
		val replacement = registerRemap[register]
		if (replacement === null || replacement === register)
		{
			return
		}
		register().removeDefinition(this)
		replacement.addDefinition(this)
		register = replacement.cast()
	}

	override fun addWritesTo(writeOperands: MutableList<L2WriteOperand<*>>)
	{
		writeOperands.add(this)
	}

	override fun addDestinationRegistersTo(
		destinationRegisters: MutableList<L2Register>)
	{
		destinationRegisters.add(register)
	}

	override fun appendTo(builder: StringBuilder)
	{
		builder.append("→").append(registerString())
	}

	override fun postOptimizationCleanup()
	{
		// Leave the restriction in place.  It shouldn't be all that big.
		semanticValues.clear()
	}
}
