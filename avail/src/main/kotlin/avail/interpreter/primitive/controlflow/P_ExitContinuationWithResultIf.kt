/*
 * P_ExitContinuationWithResultIf.kt
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

import avail.descriptor.atoms.A_Atom.Companion.extractBoolean
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.functions.A_Continuation.Companion.caller
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.ContinuationDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.exceptions.AvailErrorCode.E_CONTINUATION_EXPECTED_STRONGER_TYPE
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.L2_RETURN
import avail.interpreter.levelTwoSimple.L2SimpleTranslator
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.L2Simple_JumpIfTrue
import avail.interpreter.levelTwoSimple.instructions.L2Simple_PushLabel
import avail.interpreter.levelTwoSimple.instructions.L2Simple_Return
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Read
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CanSwitchContinuations
import avail.interpreter.primitive.Primitive3
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator

/**
 * **Primitive:** Exit the given [continuation][ContinuationDescriptor]
 * (returning result to its caller), but only if the provided boolean is true.
 * Otherwise do nothing.
 */
@Suppress("unused")
object P_ExitContinuationWithResultIf : Primitive3(
	CanInline, CanSwitchContinuations)
{
	override fun Interpreter.attempt3(
		arg1: AvailObject,
		arg2: AvailObject,
		arg3: AvailObject
	): A_BasicObject?
	{
		val continuation = arg1
		val result = arg2
		val condition = arg3

		if (!condition.extractBoolean) return nil
		// The primitive fails if the value being returned disagrees with the
		// label continuation's function's return type.  Any stronger check, as
		// specified in a semantic restriction, will be tested in the caller.
		var expectedType =
			continuation.function().code().functionType().returnType
		if (!result.isInstanceOf(expectedType))
			return fail(E_CONTINUATION_EXPECTED_STRONGER_TYPE)
		return returnIntoContinuation(
			this@P_ExitContinuationWithResultIf, continuation.caller, result)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralContinuationType,
				ANY(),
				booleanType),
			TOP())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_CONTINUATION_EXPECTED_STRONGER_TYPE))

	override fun L2SimpleTranslator.attemptToGenerateSimpleInvocation(
		functionIfKnown: A_Function?,
		rawFunction: A_RawFunction,
		optionalFunctionRead: Read?,
		expectedType: A_Type,
		args: ReadArray,
		argRestrictions: List<TypeRestriction>,
		stateOfL1: StateOfL1,
		answer: Write
	): Boolean
	{
		val continuation = args[0]
		val result = args[1]
		val condition = args[2]

		val continuationOrigin = originInstructionSkippingMoves(continuation)
		if (continuationOrigin !is L2Simple_PushLabel) return false

		val conditionRestriction = argRestrictions[2]
		if (conditionRestriction.isConstant)
		{
			if (conditionRestriction.constantOrNull!!.extractBoolean)
			{
				// Always return.
				+L2Simple_Return(value = result)
				return true
			}
			else
			{
				// Never return.
				return true
			}
		}
		// The branch can't be postponed, nor can the return.
		val after = newLabel()
		+L2Simple_JumpIfTrue(
			nextOffset = after,
			ifTrueOffset = Offset.NEXT,
			condition = condition)
		+L2Simple_Return(value = result)
		emitLabel(after)
		return true
	}

	override fun L1Translator.tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: CallSiteHelper
	): Boolean
	{
		val (continuationRead, valueRead, conditionRead) = arguments

		// Check for the common case that the continuation was created for this
		// very frame.
		val manifest = currentManifest
		val synonym =
			manifest.semanticValueToSynonym(continuationRead.semanticValue())
		val label = topFrame.label()
		if (manifest.hasSemanticValue(label) &&
			manifest.semanticValueToSynonym(label) == synonym)
		{
			// We're conditionally exiting the current frame.
			val exit = createBasicBlock("Exit")
			val noExit = createBasicBlock("Don't exit")
			jumpIfEqualsConstant(conditionRead, trueObject, exit, noExit)
			startBlock(exit)
			+L2_RETURN(valueRead)
			startBlock(noExit)
			if (currentlyReachable())
			{
				callSiteHelper.useAnswer(boxedConstant(nil), false)
			}
			return true
		}
		return false
	}
}
