/*
 * L2ReadIntOperand.kt
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
import avail.interpreter.levelTwo.register.L2IntRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.interpreter.levelTwo.register.L2Register.RegisterKind.INTEGER_KIND
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticUnboxedInt
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast

/**
 * An `L2ReadIntOperand` is an operand of type [L2OperandType.READ_INT]. It
 * holds the actual [L2IntRegister] that is to be accessed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2ReadIntOperand : L2ReadOperand<L2IntRegister>
{
	override val operandType: L2OperandType
		get() = L2OperandType.READ_INT

	/**
	 * Construct a new `L2ReadIntOperand` for the specified [L2SemanticValue]
	 * and [TypeRestriction], using information from the given
	 * [L2ValueManifest].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] that is being read when an [L2Instruction] uses
	 *   this [L2Operand].
	 * @param restriction
	 *   The [TypeRestriction] to constrain this particular read. This
	 *   restriction has been guaranteed by the VM at the point where this
	 *   operand's instruction occurs.
	 * @param manifest
	 *   The [L2ValueManifest] from which to extract a suitable definition
	 *   instruction.
	 */
	constructor(
		semanticValue: L2SemanticValue,
		restriction: TypeRestriction,
		manifest: L2ValueManifest
	) : super(
		semanticValue,
		restriction,
		manifest.getDefinition<L2IntRegister>(semanticValue, INTEGER_KIND))
	{
		assert(restriction.isUnboxedInt)
	}

	/**
	 * Construct a new `L2ReadIntOperand` with an explicit definition
	 * register [L2WriteIntOperand].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] that is being read when an [L2Instruction] uses
	 *   this [L2Operand].
	 * @param restriction
	 *   The [TypeRestriction] that bounds the value being read.
	 * @param register
	 *   The [L2IntRegister] being read by this operand.
	 */
	constructor(
		semanticValue: L2SemanticValue,
		restriction: TypeRestriction,
		register: L2IntRegister
	) : super(semanticValue, restriction, register)

	override fun semanticValue(): L2SemanticUnboxedInt =
		super.semanticValue().cast()

	override fun copyForRegister(newRegister: L2Register): L2ReadIntOperand =
		L2ReadIntOperand(
			semanticValue(), restriction(), newRegister as L2IntRegister)

	override fun createNewRegister() = L2IntRegister(-1)

	override fun dispatchOperand(dispatcher: L2OperandDispatcher)
	{
		dispatcher.doOperand(this)
	}

	override val registerKind get() = INTEGER_KIND
}
