/*
 * P_InvokeWithTuple.kt
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
package com.avail.interpreter.primitive.controlflow

import com.avail.descriptor.*
import com.avail.descriptor.FunctionTypeDescriptor.functionType
import com.avail.descriptor.FunctionTypeDescriptor.mostGeneralFunctionType
import com.avail.descriptor.ObjectTupleDescriptor.tuple
import com.avail.descriptor.TupleDescriptor.toList
import com.avail.descriptor.TupleTypeDescriptor.mostGeneralTupleType
import com.avail.descriptor.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.Invokes
import com.avail.interpreter.Primitive.Result.READY_TO_INVOKE
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import java.util.*

/**
 * **Primitive:** [Function][FunctionDescriptor] evaluation, given a
 * [tuple][TupleDescriptor] of arguments. Check the [types][TypeDescriptor]
 * dynamically to prevent corruption of the type system. Fail if the arguments
 * are not of the required types.
 */
object P_InvokeWithTuple : Primitive(2, Invokes, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val function = interpreter.argument(0)
		val argTuple = interpreter.argument(1)
		val functionType = function.kind()

		val numArgs = argTuple.tupleSize()
		val code = function.code()
		if (code.numArgs() != numArgs)
		{
			return interpreter.primitiveFailure(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		val tupleType = functionType.argsTupleType()
		for (i in 1 .. numArgs)
		{
			val arg = argTuple.tupleAt(i)
			if (!arg.isInstanceOf(tupleType.typeAtIndex(i)))
			{
				return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE)
			}
		}

		// The arguments and parameter types agree.  Can't fail after here, so
		// feel free to clobber the argsBuffer.
		interpreter.argsBuffer.clear()
		for (arg in argTuple)
		{
			interpreter.argsBuffer.add(arg)
		}
		interpreter.function = function
		return READY_TO_INVOKE
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralFunctionType(),
				mostGeneralTupleType()),
			TOP.o())

	override fun fallibilityForArgumentTypes(argumentTypes: List<A_Type>)
		: Primitive.Fallibility
	{
		val functionType = argumentTypes[0]
		val argTupleType = argumentTypes[1]
		val paramsType = functionType.argsTupleType()
		val fixedSize = argTupleType.sizeRange().upperBound().equals(
			argTupleType.sizeRange().lowerBound())
		return if (fixedSize
		           && paramsType.sizeRange().equals(argTupleType.sizeRange())
		           && argTupleType.isSubtypeOf(paramsType))
			{
				CallSiteCannotFail
			}
			else
			{
				CallSiteCanFail
			}
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction, argumentTypes: List<A_Type>): A_Type
	{
		val functionType = argumentTypes[0]
		val argTupleType = argumentTypes[1]
		val paramsType = functionType.argsTupleType()
		val fixedSize = argTupleType.sizeRange().upperBound().equals(
			argTupleType.sizeRange().lowerBound())
		if (fixedSize
		    && paramsType.sizeRange().equals(argTupleType.sizeRange())
		    && argTupleType.isSubtypeOf(paramsType))
		{
			// The argument types are hereby guaranteed to be compatible.
			// Therefore the invoke itself will succeed, so we can rely on the
			// invoked function's return type at least.  See if we can do even
			// better if we know the exact function being invoked.
			if (functionType.instanceCount().equalsInt(1))
			{
				// The actual function being invoked is known.
				val function = functionType.instance()
				val code = function.code()
				val primitive = code.primitive()
				if (primitive !== null)
				{
					// The function being invoked is itself a primitive. Dig
					// deeper to find out whether that primitive would itself
					// always succeed, and if so, what type it guarantees.
					val primArgCount = primitive.argCount
					val primArgSizes = argTupleType.sizeRange()
					if (primArgSizes.lowerBound().equalsInt(primArgCount)
					    && primArgSizes.upperBound().equalsInt(primArgCount))
					{
						val innerArgTypes = ArrayList<A_Type>(primArgCount)
						for (i in 1 .. primArgCount)
						{
							innerArgTypes.add(argTupleType.typeAtIndex(i))
						}
						val fallibility = primitive.fallibilityForArgumentTypes(
							innerArgTypes)
						return if (fallibility == CallSiteCannotFail)
						{
							// The inner invocation of the primitive function
							// will always succeed. Ask the primitive what type
							// it guarantees to return.
							primitive.returnTypeGuaranteedByVM(
								code, innerArgTypes)
						}
						else functionType.returnType()
						// The inner primitive might fail, and its failure code
						// can return something as general as the primitive
						// function's return type.
					}
					// The invocation of the inner function might not have the
					// right number of arguments. Fall through.
				}
				// The invoked inner function is not a primitive. Fall through.
			}
			// The exact function being invoked is not known. Fall through.
		}
		// The arguments that will be supplied to the inner function might not
		// have the right count.
		return functionType.returnType()
	}

	/**
	 * The arguments list initially has two entries: the register holding the
	 * function to invoke, and the register holding the tuple of arguments to
	 * pass it.  If it can be determined which registers or constants provided
	 * each tuple slot, then indicate that this invocation should be transformed
	 * by answering the register holding the function after replacing the list
	 * of (two) argument registers by the list of registers that supplied
	 * entries for the tuple.
	 *
	 *
	 * If, however, the exact constant function cannot be determined, and it
	 * cannot be proven that the function's type is adequate to accept the
	 * arguments (each of whose type must be known here for safety), then don't
	 * change the list of arguments, and simply return false.
	 */
	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val functionReg = arguments[0]
		val tupleReg = arguments[1]

		// Examine the function type.
		val functionType = functionReg.type()
		val functionArgsType = functionType.argsTupleType()
		val functionTypeSizes = functionArgsType.sizeRange()
		val upperBound = functionTypeSizes.upperBound()
		if (!upperBound.isInt || !functionTypeSizes.lowerBound().equals(upperBound))
		{
			// The exact function signature is not known.  Give up.
			return false
		}
		val argsSize = upperBound.extractInt()

		val explodedArgumentRegisters =
			translator.explodeTupleIfPossible(
				tupleReg,
				toList(functionArgsType.tupleOfTypesFromTo(1, argsSize)))
                  ?: return false

		// Fold out the call of this primitive, replacing it with an invoke of
		// the supplied function, instead.  The client will generate any needed
		// type strengthening, so don't do it here.
		translator.generateGeneralFunctionInvocation(
			functionReg, explodedArgumentRegisters, true, callSiteHelper)
		return true
	}
}
