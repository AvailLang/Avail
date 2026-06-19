/*
 * P_IfTrueThenElse.kt
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
package avail.interpreter.primitive.controlflow

import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.returnType
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive.Flag.Invokes
import avail.interpreter.primitive.Primitive3
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator

/**
 * **Primitive:** Invoke the [trueBlock][FunctionDescriptor].
 */
@Suppress("unused")
object P_IfTrueThenElse : Primitive3(Invokes, CanInline, CannotFail)
{
	override fun Interpreter.attempt3(
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		// val booleanValue = arg1
		val trueFunction = arg2
		// val falseFunction = arg3

		// Function takes no arguments.
		argsBuffer.clear()
		return invokeInPrimitive(trueFunction)
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>): A_Type
	{
		val trueBlockType = argumentTypes[1]
		return trueBlockType.returnType
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				ANY(),
				functionType(emptyTuple, TOP()),
				functionType(emptyTuple, TOP())),
			TOP())

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		// Fold out the call of this primitive, replacing it with an invoke of
		// the else function, instead.  The client will generate any needed type
		// strengthening, so don't do it here.
		val thenFunction = arguments[1]
		// 'then' function
		// takes no arguments.
		generateGeneralFunctionInvocation(
			thenFunction, true, callSiteHelper, emptyList())
		return true
	}
}
