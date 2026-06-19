/*
 * P_InvokeWithTuple.kt
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

import avail.AvailRuntimeSupport.captureNanos
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Function
import avail.descriptor.representation.A_Number.Companion.equalsInt
import avail.descriptor.representation.A_Number.Companion.extractInt
import avail.descriptor.representation.A_Number.Companion.isInt
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_RawFunction.Companion.numArgs
import avail.descriptor.representation.A_RawFunction.Companion.returnTypeIfPrimitiveFails
import avail.descriptor.representation.A_Tuple
import avail.descriptor.representation.A_Tuple.Companion.tupleAt
import avail.descriptor.representation.A_Tuple.Companion.tupleSize
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Type.Companion.argsTupleType
import avail.descriptor.representation.A_Type.Companion.instance
import avail.descriptor.representation.A_Type.Companion.instanceCount
import avail.descriptor.representation.A_Type.Companion.isSubtypeOf
import avail.descriptor.representation.A_Type.Companion.lowerBound
import avail.descriptor.representation.A_Type.Companion.returnType
import avail.descriptor.representation.A_Type.Companion.sizeRange
import avail.descriptor.representation.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.representation.A_Type.Companion.typeAtIndex
import avail.descriptor.representation.A_Type.Companion.typeUnion
import avail.descriptor.representation.A_Type.Companion.upperBound
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.L2_JUMP_IF_KIND_OF_OBJECT
import avail.interpreter.levelTwoSimple.L2SimpleTranslator
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.registers.Read
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCanFail
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteCannotFail
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteMayInvoke
import avail.interpreter.primitive.Primitive.Fallibility.CallSiteMustFail
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.Invokes
import avail.interpreter.primitive.Primitive2
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator
import avail.optimizer.L2Generator.Companion.edgeTo
import avail.performance.Statistic
import avail.performance.StatisticReport
import java.util.Collections.nCopies

/**
 * **Primitive:** [Function][A_Function] evaluation, given a
 * [tuple][A_Tuple] of arguments. Check the [types][A_Type]
 * dynamically to prevent corruption of the type system. Fail if the arguments
 * are not of the required types.
 */
@Suppress("unused")
object P_InvokeWithTuple : Primitive2(Invokes, CanInline)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val function = arg1
		val argTuple = arg2
		val functionType = function.kind()

		val numArgs = argTuple.tupleSize
		val code = function.code()
		if (code.numArgs() != numArgs)
		{
			return fail(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		if (numArgs > 0)
		{
			val before = captureNanos(this)
			val tupleType = functionType.argsTupleType
			for (i in 1 .. numArgs)
			{
				val arg = argTuple.tupleAt(i)
				if (!arg.isInstanceOf(tupleType.typeAtIndex(i)))
				{
					return fail(E_INCORRECT_ARGUMENT_TYPE)
				}
			}
			argumentCheckStat.record(captureNanos(this) - before)
		}

		// The arguments and parameter types agree.  Can't fail after here, so
		// feel free to clobber the argsBuffer.
		argsBuffer.clear()
		argsBuffer.addAll(argTuple)
		return invokeInPrimitive(function)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralFunctionType,
				mostGeneralTupleType),
			TOP())

	override fun fallibilityForArgumentTypes(argumentTypes: List<A_Type>)
		: Fallibility
	{
		val (functionType, argTupleType) = argumentTypes
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
		rawFunction: A_RawFunction?,
		argumentTypes: List<A_Type>): A_Type
	{
		val (functionType, argTupleType) = argumentTypes
		val paramsType = functionType.argsTupleType
		val argCountRange = argTupleType.sizeRange
		val argCount = argCountRange.upperBound
		if (!argCount.equals(argCountRange.lowerBound)
			|| !paramsType.sizeRange.equals(argCountRange)
			|| !argTupleType.isSubtypeOf(paramsType))
		{
			// The arguments that will be supplied to the inner function might
			// not have the right count, or might have the wrong types.
			return functionType.returnType.typeUnion(
				rawFunction!!.returnTypeIfPrimitiveFails)
		}
		// The argument types are hereby guaranteed to be compatible. Therefore
		// the invoke itself will succeed, so we can rely on the invoked
		// function's return type at least.  See if we can do even better if we
		// know the exact function being invoked.
		if (!functionType.instanceCount.equalsInt(1))
		{
			// The exact function being invoked isn't known.
			return functionType.returnType.typeUnion(
				rawFunction!!.returnTypeIfPrimitiveFails)
		}
		// The actual function being invoked is known.
		val function = functionType.instance
		val code = function.code()
		val primitive = code.codePrimitive()
		if (primitive === null)
		{
			// The function being invoked isn't a primitive, so fall back.
			return functionType.returnType.typeUnion(
				rawFunction!!.returnTypeIfPrimitiveFails)
		}
		// The function being invoked is itself a primitive. Dig deeper to find
		// out whether that primitive would itself always succeed, and if so,
		// what type it guarantees.
		val primArgCount = primitive.argCount
		if (!argCountRange.lowerBound.equalsInt(primArgCount)
			|| !argCountRange.upperBound.equalsInt(primArgCount))
		{
			// The invocation of the inner function might not have the
			// right number of arguments. Fall back.
			return functionType.returnType.typeUnion(
				rawFunction!!.returnTypeIfPrimitiveFails)
		}
		val innerArgTypes = (1 .. primArgCount).map {
			argTupleType.typeAtIndex(it)
		}
		val fallibility = primitive.fallibilityForArgumentTypes(innerArgTypes)
		return when (fallibility)
		{
			CallSiteCannotFail ->
			{
				// The inner invocation of the primitive function will always
				// succeed. Ask the primitive what type it guarantees to return.
				primitive.returnTypeGuaranteedByVM(code, innerArgTypes)
			}
			CallSiteMustFail ->
			{
				code.returnTypeIfPrimitiveFails
			}
			else ->
			{
				code.returnTypeIfPrimitiveFails.typeUnion(
					primitive.returnTypeGuaranteedByVM(code, innerArgTypes))
			}
		}
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
	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val (functionRead, tupleRead) = arguments

		// Examine the function type.
		val functionType = functionRead.type()
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
		val explodedArgumentReads =
			explodeTupleIfPossible(
				tupleRead,
				nCopies(argsSize, Types.ANY()))

		// Fall back if we couldn't even pin down the argument count.
		explodedArgumentReads ?: return false
		val functionArgTypes = functionArgsType.tupleOfTypesFromTo(1, argsSize)

		// Fall back if the count will always be wrong.
		if (functionArgTypes.tupleSize != argsSize) return false
		val failurePath = createBasicBlock(
			"Failed dynamic type check for P_InvokeWithTuple",
			isCold = true)
		for (i in 1 .. argsSize)
		{
			val argReg = explodedArgumentReads[i - 1]
			val argType = argReg.type()
			val exactTypeReg = extractParameterTypeFromFunction(functionRead, i)
			val constantExactArgType = exactTypeReg.constantOrNull
			if (constantExactArgType === null
				|| !argType.isSubtypeOf(constantExactArgType))
			{
				// This argument has to be checked at runtime.
				val passedAnother = createBasicBlock(
					"Passed check for argument #$i")
				if (constantExactArgType !== null)
				{
					// We have a known exact type to compare against.
					jumpIfKindOfConstant(
						argReg,
						constantExactArgType,
						passedAnother,
						failurePath)
				}
				else
				{
					// The arg type was extracted at runtime from the function.
					+L2_JUMP_IF_KIND_OF_OBJECT(
						argReg,
						exactTypeReg,
						edgeTo(passedAnother),
						edgeTo(failurePath))
				}
				startBlock(passedAnother)
			}
		}

		// Fold out the call of this primitive, replacing it with an invoke of
		// the supplied function, instead.  The client will generate any needed
		// type strengthening, so don't do it here.
		generateGeneralFunctionInvocation(
			functionRead,
			true,
			callSiteHelper,
			explodedArgumentReads)

		startBlock(failurePath)
		// At least one argument disagreed with the required type, so call the
		// actual invoker function (i.e., the one with this primitive) with the
		// function to invoke and the tuple of arguments.
		if (currentlyReachable())
		{
			generateGeneralFunctionInvocation(
				functionToCallReg,
				false,
				callSiteHelper,
				arguments)
		}
		return true
	}

	override fun L2SimpleTranslator.attemptToGenerateSimpleInvocation(
		functionIfKnown: A_Function?,
		rawFunction: A_RawFunction,
		functionRead: Read,
		expectedType: A_Type,
		args: ReadArray,
		argRestrictions: List<TypeRestriction>,
		stateOfL1: StateOfL1,
		answer: Write
	): Boolean
	{
		assert(argRestrictions.size == 2)
		val functionToInvoke = args[0]
		val functionArguments = args[1]
		return attemptToEmbedInvocation(
			functionToInvoke = functionToInvoke,
			functionArguments = functionArguments,
			stateOfL1 = stateOfL1,
			answer = answer,
			expectedType = expectedType)
	}

	/**
	 * A statistic that only measures the cost of type-checking arguments for
	 * this primitive.  Note that this time is also counted as primitive
	 * execution time.
	 */
	val argumentCheckStat = Statistic(
		StatisticReport.TYPE_CHECKS_IN_PRIMITIVES,
		"Check for arguments of $name")
}
