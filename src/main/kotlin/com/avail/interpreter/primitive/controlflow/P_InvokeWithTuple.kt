/*
 * P_InvokeWithTuple.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.A_RawFunction.Companion.numArgs
import com.avail.descriptor.functions.A_RawFunction.Companion.returnTypeIfPrimitiveFails
import com.avail.descriptor.numbers.A_Number.Companion.equalsInt
import com.avail.descriptor.numbers.A_Number.Companion.extractInt
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleAt
import com.avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.A_Type.Companion.argsTupleType
import com.avail.descriptor.types.A_Type.Companion.instance
import com.avail.descriptor.types.A_Type.Companion.instanceCount
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.lowerBound
import com.avail.descriptor.types.A_Type.Companion.returnType
import com.avail.descriptor.types.A_Type.Companion.sizeRange
import com.avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import com.avail.descriptor.types.A_Type.Companion.typeAtIndex
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.A_Type.Companion.upperBound
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.types.TypeDescriptor.Types.TOP
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail
import com.avail.interpreter.Primitive.Fallibility.CallSiteMayInvoke
import com.avail.interpreter.Primitive.Fallibility.CallSiteMustFail
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.Invokes
import com.avail.interpreter.Primitive.Result.READY_TO_INVOKE
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_OBJECT
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.optimizer.L2Generator.Companion.edgeTo
import java.util.Collections.nCopies

/**
 * **Primitive:** [Function][A_Function] evaluation, given a
 * [tuple][A_Tuple] of arguments. Check the [types][A_Type]
 * dynamically to prevent corruption of the type system. Fail if the arguments
 * are not of the required types.
 */
@Suppress("unused")
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
		val tupleType = functionType.argsTupleType
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
		interpreter.argsBuffer.addAll(argTuple)
		interpreter.function = function
		return READY_TO_INVOKE
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralFunctionType(),
				mostGeneralTupleType()),
			TOP.o
		)

	override fun fallibilityForArgumentTypes(argumentTypes: List<A_Type>)
		: Fallibility
	{
		val functionType = argumentTypes[0]
		val argTupleType = argumentTypes[1]
		val paramsType = functionType.argsTupleType
		val fixedSize = argTupleType.sizeRange.upperBound.equals(
			argTupleType.sizeRange.lowerBound)
		if (fixedSize
			&& paramsType.sizeRange.equals(argTupleType.sizeRange)
			&& argTupleType.isSubtypeOf(paramsType))
		{
			return CallSiteMayInvoke
		}
		return CallSiteCanFail
	}

	override fun returnTypeGuaranteedByVM(
		rawFunction: A_RawFunction,
		argumentTypes: List<A_Type>): A_Type
	{
		val functionType = argumentTypes[0]
		val argTupleType = argumentTypes[1]
		val paramsType = functionType.argsTupleType
		val argCountRange = argTupleType.sizeRange
		val argCount = argCountRange.upperBound
		if (argCount.equals(argCountRange.lowerBound)
		    && paramsType.sizeRange.equals(argCountRange)
		    && argTupleType.isSubtypeOf(paramsType))
		{
			// The argument types are hereby guaranteed to be compatible.
			// Therefore the invoke itself will succeed, so we can rely on the
			// invoked function's return type at least.  See if we can do even
			// better if we know the exact function being invoked.
			if (functionType.instanceCount.equalsInt(1))
			{
				// The actual function being invoked is known.
				val function = functionType.instance
				val code = function.code()
				val primitive = code.codePrimitive()
				if (primitive !== null)
				{
					// The function being invoked is itself a primitive. Dig
					// deeper to find out whether that primitive would itself
					// always succeed, and if so, what type it guarantees.
					val primArgCount = primitive.argCount
					if (argCountRange.lowerBound.equalsInt(primArgCount)
						&& argCountRange.upperBound.equalsInt(primArgCount))
					{
						val innerArgTypes = (1 .. primArgCount).map {
							argTupleType.typeAtIndex(it)
						}
						val fallibility = primitive.fallibilityForArgumentTypes(
							innerArgTypes)
						return when (fallibility)
						{
							CallSiteCannotFail ->
							{
								// The inner invocation of the primitive
								// function will always succeed. Ask the
								// primitive what type it guarantees to return.
								primitive.returnTypeGuaranteedByVM(
									code, innerArgTypes)
							}
							CallSiteMustFail ->
							{
								code.returnTypeIfPrimitiveFails
							}
							else ->
							{
								code.returnTypeIfPrimitiveFails.typeUnion(
									primitive.returnTypeGuaranteedByVM(
										code, innerArgTypes))
							}
						}
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
		return functionType.returnType.typeUnion(
			rawFunction.returnTypeIfPrimitiveFails)
	}

	/**
	 * The arguments list initially has two entries: the register holding the
	 * function to invoke, and the register holding the tuple of arguments to
	 * pass it.  If the call will always succeed (i.e., the supplied arguments
	 * satisfy the function's parameter types) then generate a direct invocation
	 * of the function with those arguments.
	 *
	 * If the call cannot be checked until runtime, assume that the most likely
	 * scenario by far is that the argument types will conform to the required
	 * parameter types.  Create a path of dynamic type tests that leads to code
	 * where the call will always succeed and the function is being directly
	 * invoked.  On the rare failure paths, we still have to invoke the
	 * invoker function (the one defined as this primitive), as its failure code
	 * must be executed.  Since this is exceedingly rare, let the primitive do
	 * the usual dynamic type tests (redundantly), just to fail the primitive in
	 * a way that hides the optimization.
	 *
	 * If the call will always fail, just invoke this primitive normally, and
	 * let it fail.
	 */
	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val (functionReg, tupleReg) = arguments
		val generator = translator.generator

		// Examine the function type.
		val functionType = functionReg.type()
		val functionArgsType = functionType.argsTupleType
		val functionTypeSizes = functionArgsType.sizeRange
		val upperBound = functionTypeSizes.upperBound
		if (!upperBound.isInt ||
			!functionTypeSizes.lowerBound.equals(upperBound))
		{
			// The exact function arity is not known.  Give up.
			return false
		}
		val argsSize = upperBound.extractInt

		// Note: Uses any as each type, since we're going to do strengthening
		// checks ourselves, below.
		val explodedArgumentRegisters =
			generator.explodeTupleIfPossible(
				tupleReg,
				nCopies(argsSize, Types.ANY.o))

		// Fall back if we couldn't even pin down the argument count.
		explodedArgumentRegisters ?: return false
		val functionArgTypes = functionArgsType.tupleOfTypesFromTo(1, argsSize)

		// Fall back if the count will always be wrong.
		if (functionArgTypes.tupleSize() != argsSize) return false
		val failurePath = generator.createBasicBlock(
			"Failed dynamic type check for P_InvokeWithTuple")
		for (i in 1..argsSize)
		{
			val argReg = explodedArgumentRegisters[i - 1]
			val argType = argReg.type()
			val exactTypeReg = generator.extractParameterTypeFromFunction(
				functionReg, i)
			val constantExactArgType = exactTypeReg.restriction().constantOrNull
			if (constantExactArgType === null
				|| !argType.isSubtypeOf(constantExactArgType))
			{
				// This argument has to be checked at runtime.
				val passedAnother = generator.createBasicBlock(
					"Passed check for argument #$i")
				if (constantExactArgType !== null)
				{
					// We have a known exact type to compare against.
					translator.jumpIfKindOfConstant(
						argReg,
						constantExactArgType,
						passedAnother,
						failurePath)
				}
				else
				{
					// The arg type was extracted at runtime from the function.
					generator.addInstruction(
						L2_JUMP_IF_KIND_OF_OBJECT,
						argReg,
						exactTypeReg,
						edgeTo(passedAnother),
						edgeTo(failurePath))
				}
				generator.startBlock(passedAnother)
			}
		}

		// Fold out the call of this primitive, replacing it with an invoke of
		// the supplied function, instead.  The client will generate any needed
		// type strengthening, so don't do it here.
		translator.generateGeneralFunctionInvocation(
			functionReg,
			explodedArgumentRegisters,
			true,
			callSiteHelper)

		generator.startBlock(failurePath)
		// At least one argument disagreed with the required type, so call the
		// actual invoker function (i.e., the one with this primitive) with the
		// function to invoke and the tuple of arguments.
		if (generator.currentlyReachable())
		{
			translator.generateGeneralFunctionInvocation(
				functionToCallReg,
				arguments,
				false,
				callSiteHelper)
		}
		return true
	}
}
