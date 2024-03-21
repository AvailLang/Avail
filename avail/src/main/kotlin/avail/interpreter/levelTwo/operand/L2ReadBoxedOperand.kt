/*
 * L2ReadBoxedOperand.kt
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

import avail.descriptor.functions.A_Function
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.typeAtIndex
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.READ_BOXED
import avail.interpreter.levelTwo.operation.L2_CREATE_FUNCTION
import avail.interpreter.levelTwo.operation.L2_CREATE_FUNCTION.constantRawFunctionOf
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT
import avail.interpreter.levelTwo.operation.L2_MOVE_CONSTANT.Companion.constantOf
import avail.interpreter.levelTwo.register.BOXED_KIND
import avail.interpreter.levelTwo.register.L2BoxedRegister
import avail.interpreter.levelTwo.register.L2Register
import avail.optimizer.L2ValueManifest
import avail.optimizer.values.L2SemanticBoxedValue
import avail.optimizer.values.L2SemanticValue
import avail.utility.cast

/**
 * An `L2ReadBoxedOperand` is an operand of type [L2OperandType.READ_BOXED]. It
 * holds the actual [L2BoxedRegister] that is to be accessed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2ReadBoxedOperand : L2ReadOperand<BOXED_KIND>
{
	override val operandType: L2OperandType get() = READ_BOXED

	/**
	 * Construct a new `L2ReadBoxedOperand` for the specified [L2SemanticValue]
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
		semanticValue: L2SemanticValue<BOXED_KIND>,
		restriction: TypeRestriction,
		manifest: L2ValueManifest
	) : super(
		semanticValue,
		restriction,
		manifest.getDefinition(semanticValue))
	{
		assert(restriction.isBoxed)
	}

	/**
	 * Construct a new `L2ReadBoxedOperand` with an explicit definition
	 * register [L2WriteBoxedOperand].
	 *
	 * @param semanticValue
	 *   The [L2SemanticValue] that is being read when an [L2Instruction] uses
	 *   this [L2Operand].
	 * @param restriction
	 *   The [TypeRestriction] that bounds the value being read.
	 * @param register
	 *   The [L2BoxedRegister] being read by this operand.
	 */
	constructor(
		semanticValue: L2SemanticBoxedValue,
		restriction: TypeRestriction,
		register: L2BoxedRegister
	) : super(semanticValue, restriction, register)

	override fun semanticValue(): L2SemanticBoxedValue =
		super.semanticValue().cast()

	override fun copyForRegister(
		newRegister: L2Register<BOXED_KIND>
	): L2ReadBoxedOperand =
		L2ReadBoxedOperand(
			semanticValue(), restriction(), newRegister as L2BoxedRegister)

	override fun createNewRegister() = L2BoxedRegister(-1)

	override fun dispatchOperand(dispatcher: L2OperandDispatcher) =
		dispatcher.doOperand(this)

	override val kind get() = BOXED_KIND

	/**
	 * See if we can determine the exact type of this register, which holds a
	 * function.  If the function type is known, answer it, otherwise `null`.
	 *
	 * @return
	 *   Either `null` or an exact [A_Type] for the function in this register.
	 */
	fun exactFunctionType(): A_Type?
	{
		val constantFunction: A_Function? = constantOrNull()
		if (constantFunction !== null)
		{
			// Function is a constant.
			return constantFunction.code().functionType()
		}
		val originOfFunction = definitionSkippingMoves()
		if (originOfFunction.operation === L2_MOVE_CONSTANT.Companion.boxed)
		{
			// Function came from a constant (although the TypeRestriction
			// should have ensured the clause above caught it).
			return constantOf(originOfFunction).code().functionType()
		}
		if (originOfFunction.operation === L2_CREATE_FUNCTION)
		{
			// We found where the function was closed from a raw function,
			// which knows the exact function type that it'll be.  Use that.
			return constantRawFunctionOf(originOfFunction).functionType()
		}
		return null
	}

	/**
	 * See if we can determine the exact type required as the first argument of
	 * the function produced by this read.  If the exact type is known, answer
	 * it, otherwise `null`.
	 *
	 * @return
	 *   Either `null` or an exact [A_Type] to compare some value against in
	 *   order to determine whether the one-argument function will accept the
	 *   given argument.
	 */
	fun exactSoleArgumentType(): A_Type?
	{
		val functionType = exactFunctionType() ?: return null
		return functionType.argsTupleType.typeAtIndex(1)
	}
}
