/*
 * P_RestartContinuation.kt
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

import com.avail.descriptor.functions.A_Continuation
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.BottomTypeDescriptor.bottom
import com.avail.descriptor.types.ContinuationTypeDescriptor.mostGeneralContinuationType
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.interpreter.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.*
import com.avail.interpreter.Primitive.Result.CONTINUATION_CHANGED
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operation.*
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.optimizer.L2Entity
import com.avail.optimizer.L2Generator.backEdgeTo
import com.avail.optimizer.L2Generator.edgeTo
import com.avail.optimizer.values.L2SemanticValue

/**
 * **Primitive:** Restart the given [continuation][A_Continuation]. Make sure
 * it's a label-like continuation rather than a call-like, because a call-like
 * continuation requires a value to be stored on its stack in order to resume
 * it, something this primitive does not do.
 */
object P_RestartContinuation : Primitive(
	1,
	CannotFail,
	CanInline,
	CanSwitchContinuations,
	AlwaysSwitchesContinuation)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(1)
		val originalCon = interpreter.argument(0)

		val code = originalCon.function().code()
		//TODO MvG - This should be a primitive failure.
		assert(originalCon.stackp() == code.numSlots() + 1)
		{
			"Continuation should have been a label- rather than " +
				"call-continuation"
		}
		assert(originalCon.pc() == 0)
		{
			"Continuation should have been a label- rather than " +
				"call-continuation"
		}

		// Move the (original) arguments from the continuation into
		// interpreter.argsBuffer.
		val numArgs = code.numArgs()
		interpreter.argsBuffer.clear()
		for (i in 1 .. numArgs)
		{
			interpreter.argsBuffer.add(originalCon.frameAt(i))
		}
		// The restart entry point expects the interpreter's reifiedContinuation
		// to be the label continuation's *caller*.
		interpreter.setReifiedContinuation(originalCon.caller())
		interpreter.function = originalCon.function()
		interpreter.chunk = code.startingChunk()
		interpreter.offset = 0
		interpreter.returnNow = false
		interpreter.setLatestResult(null)
		return CONTINUATION_CHANGED
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(tuple(mostGeneralContinuationType()), bottom())

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val continuationReg = arguments[0]

		// Check for the common case that the continuation was created for this
		// very frame.
		val generator = translator.generator
		val manifest = generator.currentManifest()
		val synonym = manifest.semanticValueToSynonym(
			continuationReg.semanticValue())
		val label = generator.topFrame.label()
		if (manifest.hasSemanticValue(label) &&
			manifest.semanticValueToSynonym(label) == synonym)
		{
			// Simply jump to the restartLoopHeadBlock, where the n@1 semantic
			// slots will be connected to the phis.

			// Copy the current argument values (which may have been made
			// immutable) into fresh temps.
			val tempSemanticValues = mutableSetOf<L2SemanticValue>()
			val tempRegisters = mutableSetOf<L2Register>()
			val tempReads = mutableListOf<L2ReadBoxedOperand>()
			for (i in 1..translator.code.numArgs()) {
				val read = translator.readSlot(i)
				val temp = generator.topFrame.temp(generator.nextUnique())
				tempSemanticValues.add(temp)
				val tempWrite = L2_MOVE.boxed.createWrite(
					generator, temp, read.restriction())
				generator.addInstruction(L2_MOVE.boxed, read, tempWrite)
				val move = generator.currentBlock().instructions().last()
				assert(move.operation() == L2_MOVE.boxed)
				tempRegisters.addAll(move.destinationRegisters())
				tempReads.add(
					L2ReadBoxedOperand(
						temp, read.restriction(), tempWrite.register()))
			}

			// Now keep only the new temps visible in the manifest.
			generator.addInstruction(
				L2_STRIP_MANIFEST.instance,
				L2ReadBoxedVectorOperand(tempReads))

			// Now move them into semantic slots n@1, so the phis at the
			// restartLoopHeadBlock will know what to do with them.  Force a
			// move for simplicity (i.e., suppress the mechanism that
			// moveRegister() uses to simply enlarge synonyms.
			val newReads = tempSemanticValues.mapIndexed {
				zeroIndex, temp ->
				val newArg = generator.topFrame.slot(zeroIndex + 1, 1)
				val writeOperand = generator.boxedWrite(
					newArg, manifest.restrictionFor(temp))
				generator.addInstruction(
					L2_MOVE.boxed,
					generator.readBoxed(temp),
					writeOperand)
				L2ReadBoxedOperand(
					newArg,
					writeOperand.restriction(),
					writeOperand.register())
			}

			// Now keep only the new args visible in the manifest.
			generator.addInstruction(
				L2_STRIP_MANIFEST.instance,
				L2ReadBoxedVectorOperand(newReads))

			val trampolineBlock = generator.createBasicBlock(
				"edge-split for restart")

			// Use an L2_JUMP_BACK to get to the trampoline block.
			generator.addInstruction(
				L2_JUMP_BACK.instance,
				edgeTo(trampolineBlock),
				L2ReadBoxedVectorOperand(newReads))

			// Finally, jump to the restartLoopHeadBlock, where the n@1 semantic
			// slots will be added to the phis.
			generator.startBlock(trampolineBlock)
			generator.addInstruction(
				L2_JUMP.instance,
				backEdgeTo(generator.restartLoopHeadBlock!!))

			// Ensure only the n@1 slots and registers are considered live.
			val liveEntities = mutableSetOf<L2Entity>()
			for (newRead in newReads)
			{
				liveEntities.add(newRead.semanticValue())
				liveEntities.add(newRead.register())
			}
			generator.currentBlock().successorEdgeAt(0).forcedClampedEntities =
				liveEntities

			return true
		}

		// A restart works with every continuation that is created by a label.
		// First, pop out of the Java stack frames back into the outer L2 run
		// loop (which saves/restores the current frame and continues at the
		// next L2 instruction).
		translator.addInstruction(
			L2_RESTART_CONTINUATION.instance, continuationReg)
		assert(!translator.generator.currentlyReachable())
		return true
	}
}
