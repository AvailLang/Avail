/*
 * P_RestartContinuation.kt
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

import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Continuation.Companion.frameAt
import avail.descriptor.functions.A_Continuation.Companion.pc
import avail.descriptor.functions.A_Continuation.Companion.stackp
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.numArgs
import avail.descriptor.functions.A_RawFunction.Companion.numSlots
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.interpreter.levelTwo.operation.L2_RESTART_CONTINUATION
import avail.interpreter.levelTwoSimple.L2SimpleTranslator
import avail.interpreter.levelTwoSimple.StateOfL1
import avail.interpreter.levelTwoSimple.instructions.L2Simple_LocalRestartWithArguments
import avail.interpreter.levelTwoSimple.instructions.L2Simple_PushLabel
import avail.interpreter.levelTwoSimple.instructions.registers.Offset
import avail.interpreter.levelTwoSimple.instructions.registers.Read
import avail.interpreter.levelTwoSimple.instructions.registers.ReadArray
import avail.interpreter.levelTwoSimple.instructions.registers.Write
import avail.interpreter.primitive.Primitive.Flag.AlwaysSwitchesContinuation
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CanSwitchContinuations
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive1
import avail.optimizer.CallSiteHelper
import avail.optimizer.L1Translator

/**
 * **Primitive:** Restart the given [continuation][A_Continuation]. Make sure
 * it's a label-like continuation rather than a call-like, because a call-like
 * continuation requires a value to be stored on its stack in order to resume
 * it, something this primitive does not do.
 */
@Suppress("unused")
object P_RestartContinuation : Primitive1(
	CannotFail,
	CanInline,
	CanSwitchContinuations,
	AlwaysSwitchesContinuation)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val originalCon = arg1

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

		// Move the (original) arguments from the continuation into
		// interpreter.argsBuffer.
		val numArgs = code.numArgs()
		val arguments = (1..numArgs).map { originalCon.frameAt(it) }
		// The restart entry point expects the interpreter's reifiedContinuation
		// to be the label continuation's *caller*.
		clearLatestResult()
		currentReifier = reifierToRestartWithArguments(originalCon, arguments)
		return null
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralContinuationType), bottom)

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
		val continuation = args[0]

		val continuationOrigin = originInstructionSkippingMoves(continuation)
		if (continuationOrigin !is L2Simple_PushLabel) return false

		// Read the original argument slots.  Since a label is pushed in this
		// function, the arguments are never cleared.
		val tupleElementReads =
			ReadArray((1..code.numArgs()).map(::readSlot))

		+L2Simple_LocalRestartWithArguments(
			nextOffset = Offset(0),
			argSources = tupleElementReads)
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
		val continuationReg = arguments[0]

		// Check for the common case that the continuation was created for this
		// very frame.
		val manifest = currentManifest
		val synonym = manifest.semanticValueToSynonym(
			continuationReg.semanticValue())
		val label = topFrame.label()
		if (manifest.hasSemanticValue(label) &&
			manifest.semanticValueToSynonym(label) == synonym)
		{
			generateRestartContinuation((1..code.numArgs()).map(::readSlot))
			return true
		}

		// A restart works with every continuation that is created by a label.
		// First, pop out of the Java stack frames back into the outer L2 run
		// loop (which saves/restores the current frame and continues at the
		// next L2 instruction).
		+L2_RESTART_CONTINUATION(continuationReg)
		assert(!currentlyReachable())
		return true
	}
}
