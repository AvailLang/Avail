/*
 * P_RestartContinuationWithArguments.kt
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

import avail.descriptor.functions.A_Continuation.Companion.pc
import avail.descriptor.functions.A_Continuation.Companion.stackp
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numSlots
import avail.descriptor.functions.ContinuationDescriptor
import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.numbers.A_Number.Companion.equalsInt
import avail.descriptor.numbers.A_Number.Companion.extractInt
import avail.descriptor.numbers.A_Number.Companion.isInt
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.tuples.TupleDescriptor.Companion.toList
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.acceptsTupleOfArgTypes
import avail.descriptor.types.A_Type.Companion.acceptsTupleOfArguments
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.functionType
import avail.descriptor.types.A_Type.Companion.lowerBound
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.tupleOfTypesFromTo
import avail.descriptor.types.A_Type.Companion.upperBound
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import avail.interpreter.levelTwo.operation.L2_RESTART_CONTINUATION_WITH_ARGUMENTS
import avail.interpreter.primitive.Primitive.Flag.AlwaysSwitchesContinuation
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CanSwitchContinuations
import avail.interpreter.primitive.Primitive2
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator

/**
 * **Primitive:** Restart the given [continuation][ContinuationDescriptor], but
 * passing in the given [tuple][TupleDescriptor] of arguments. Make sure it's a
 * label-like continuation rather than a call-like, because a call-like
 * continuation has the expected return type already pushed on the stack, and
 * requires the return value, after checking against that type, to overwrite the
 * type in the stack (without affecting the stack depth). Fail if the
 * continuation's [function][FunctionDescriptor] is not capable of accepting the
 * given arguments.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_RestartContinuationWithArguments : Primitive2(
	CanInline,
	CanSwitchContinuations,
	AlwaysSwitchesContinuation)
{
	override fun Interpreter.attempt2(
		arg1: AvailObject,
		arg2: AvailObject
	): A_BasicObject?
	{
		val originalCon = arg1
		val arguments = arg2

		val code = originalCon.function().code()
		//TODO MvG - This should be a primitive failure.
		assert(originalCon.stackp == code.numSlots + 1)
		{
			"Continuation should have been a label- rather than " +
				"call-continuation"
		}
		assert(originalCon.pc == 0)
		{
			"Continuation should have been a label- rather than " +
				"call-continuation"
		}

		val numArgs = code.numArgs()
		if (numArgs != arguments.tupleSize)
		{
			return fail(E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		// Check the argument types.
		if (!code.functionType().acceptsTupleOfArguments(arguments))
		{
			return fail(E_INCORRECT_ARGUMENT_TYPE)
		}
		// Move the arguments into interpreter.argsBuffer.
		argsBuffer.clear()
		for (arg in arguments)
		{
			argsBuffer.add(arg)
		}
		// The restart entry point expects the interpreter's reifiedContinuation
		// to be the label continuation's *caller*.
		clearLatestResult()
		currentReifier = reifierToRestartWithArguments(originalCon, arguments)
		return null
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(mostGeneralContinuationType, mostGeneralTupleType),
			bottom)

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_INCORRECT_NUMBER_OF_ARGUMENTS, E_INCORRECT_ARGUMENT_TYPE))

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val (continuationReg, argumentsTupleReg) = arguments

		// Check for the common case that the continuation was created for this
		// very frame.
		val manifest = currentManifest
		val synonym = manifest.semanticValueToSynonym(
			continuationReg.semanticValue())
		val label = topFrame.label()
		if (manifest.hasSemanticValue(label) &&
			manifest.semanticValueToSynonym(label) == synonym)
		{
			// We're restarting the current frame.  First set up the semantic
			// arguments for phis at the loop head to converge.
			val numArgs = code.numArgs()
			val argsType = argumentsTupleReg.type()
			val argsSizeRange = argsType.sizeRange

			if (!argsSizeRange.lowerBound.equalsInt(numArgs)
				|| !argsSizeRange.upperBound.equalsInt(numArgs))
			{
				// Couldn't guarantee the argument count matches.
				return false
			}
			val argTypesTuple = argsType.tupleOfTypesFromTo(1, numArgs)
			if (!code.functionType().acceptsTupleOfArgTypes(argTypesTuple))
			{
				// Couldn't guarantee the argument types matched.
				return false
			}
			val explodedTupleRegs = explodeTupleIfPossible(
				argumentsTupleReg,
				argTypesTuple.toList())
			if (explodedTupleRegs === null)
			{
				// This shouldn't happen, but just in case the continuation is
				// being restarted with an unknown number of arguments through
				// some reflective mechanism, fall back to the primitive.
				return false
			}
			generateRestartContinuation(explodedTupleRegs)
			return true
		}

		// A restart works with every continuation that is created by a label.
		// First, pop out of the Java stack frames back into the outer L2 run
		// loop (which saves/restores the current frame and continues at the
		// next L2 instruction).  Extract the tuple of arguments back into a
		// vector of individual registers, aborting code generation of this
		// special invocation if it's not possible.

		// Examine the continuation's function's type.
		val continuationType = continuationReg.type()
		val functionType = continuationType.functionType
		val functionArgsType = functionType.argsTupleType
		val functionTypeSizes = functionArgsType.sizeRange
		val upperBound = functionTypeSizes.upperBound
		if (!upperBound.isInt
			|| !functionTypeSizes.lowerBound.equals(upperBound))
		{
			// The exact function signature is not known.  Give up.
			return false
		}
		val argsSize = upperBound.extractInt
		val explodedArgumentRegs = explodeTupleIfPossible(
			argumentsTupleReg,
			toList(functionArgsType.tupleOfTypesFromTo(1, argsSize)))
		explodedArgumentRegs ?: return false

		+L2_RESTART_CONTINUATION_WITH_ARGUMENTS(
			continuationReg,
			L2ReadBoxedVectorOperand(explodedArgumentRegs))
		assert(!currentlyReachable())
		startBlock(
			createBasicBlock(
				"unreachable after L2_RESTART_CONTINUATION_WITH_ARGUMENTS"))
		return true
	}
}
