/*
 * P_IfTrueThenElse.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.controlflow

import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.returnType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CannotFail
import com.avail.interpreter.Primitive.Flag.Invokes
import com.avail.interpreter.Primitive.Result.READY_TO_INVOKE
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper

/**
 * **Primitive:** Invoke the [trueBlock][FunctionDescriptor].
 */
@Suppress("unused")
object P_IfTrueThenElse : Primitive(3, Invokes, CanInline, CannotFail)
{

	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(3)
		//		final A_Atom ignoredBoolean = interpreter.argument(0);
		val trueFunction = interpreter.argument(1)
		//		final A_Function ignoredFalseFunction = interpreter.argument(2);

		// Function takes no arguments.
		interpreter.argsBuffer.clear()
		interpreter.function = trueFunction
		return READY_TO_INVOKE
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val trueBlockType = argumentTypes[1]
		return trueBlockType.returnType()
	}

	override fun privateBlockTypeRestriction(): A_Type =
		 functionType(
			 tuple(
				ANY.o,
				functionType(emptyTuple, TOP.o),
				functionType(emptyTuple, TOP.o)),
			 TOP.o
		 )

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		// Fold out the call of this primitive, replacing it with an invoke of
		// the else function, instead.  The client will generate any needed type
		// strengthening, so don't do it here.
		val thenFunction = arguments[1]
		// 'then' function
		// takes no arguments.
		translator.generateGeneralFunctionInvocation(
			thenFunction, emptyList(), true, callSiteHelper)
		return true
	}
}
