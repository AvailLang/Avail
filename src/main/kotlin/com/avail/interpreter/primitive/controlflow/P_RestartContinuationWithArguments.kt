/*
 * P_RestartContinuationWithArguments.kt
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

import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.ContinuationDescriptor
import com.avail.descriptor.functions.FunctionDescriptor
import com.avail.descriptor.sets.SetDescriptor.Companion.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.tuples.TupleDescriptor
import com.avail.descriptor.tuples.TupleDescriptor.toList
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.BottomTypeDescriptor.bottom
import com.avail.descriptor.types.ContinuationTypeDescriptor.mostGeneralContinuationType
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.TupleTypeDescriptor.mostGeneralTupleType
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_ARGUMENT_TYPE
import com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.AlwaysSwitchesContinuation
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.Primitive.Flag.CanSwitchContinuations
import com.avail.interpreter.Primitive.Result.CONTINUATION_CHANGED
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedVectorOperand
import com.avail.interpreter.levelTwo.operation.L2_JUMP
import com.avail.interpreter.levelTwo.operation.L2_JUMP_BACK
import com.avail.interpreter.levelTwo.operation.L2_MOVE
import com.avail.interpreter.levelTwo.operation.L2_RESTART_CONTINUATION_WITH_ARGUMENTS
import com.avail.interpreter.levelTwo.operation.L2_STRIP_MANIFEST
import com.avail.interpreter.levelTwo.register.L2Register
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L1Translator.CallSiteHelper
import com.avail.optimizer.L2Entity
import com.avail.optimizer.L2Generator.backEdgeTo
import com.avail.optimizer.L2Generator.edgeTo
import com.avail.optimizer.values.L2SemanticValue

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
object P_RestartContinuationWithArguments : Primitive(
	2,
	CanInline,
	CanSwitchContinuations,
	AlwaysSwitchesContinuation)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val originalCon = interpreter.argument(0)
		val arguments = interpreter.argument(1)

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

		val numArgs = code.numArgs()
		if (numArgs != arguments.tupleSize())
		{
			return interpreter.primitiveFailure(
				E_INCORRECT_NUMBER_OF_ARGUMENTS)
		}
		// Check the argument types.
		if (!code.functionType().acceptsTupleOfArguments(arguments))
		{
			return interpreter.primitiveFailure(E_INCORRECT_ARGUMENT_TYPE)
		}
		// Move the arguments into interpreter.argsBuffer.
		interpreter.argsBuffer.clear()
		for (arg in arguments)
		{
			interpreter.argsBuffer.add(arg)
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
		functionType(
			tuple(mostGeneralContinuationType(), mostGeneralTupleType()),
			bottom())

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(
			set(E_INCORRECT_NUMBER_OF_ARGUMENTS, E_INCORRECT_ARGUMENT_TYPE))

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: CallSiteHelper): Boolean
	{
		val continuationReg = arguments[0]
		val argumentsTupleReg = arguments[1]

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
			// We're restarting the current frame.  First set up the semantic
			// arguments for phis at the loop head to converge.
			val code: A_RawFunction = translator.code
			val numArgs = code.numArgs()
			val argsType = argumentsTupleReg.type()
			val argsSizeRange = argsType.sizeRange()

			if (!argsSizeRange.lowerBound().equalsInt(numArgs)
				|| !argsSizeRange.upperBound().equalsInt(numArgs))
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
			val explodedTupleRegs = generator.explodeTupleIfPossible(
				argumentsTupleReg, argTypesTuple.toList())
			// First, move these values into fresh temps.
			val tempSemanticValues = mutableSetOf<L2SemanticValue>()
			val tempRegisters = mutableSetOf<L2Register>()
			val tempReads = mutableListOf<L2ReadBoxedOperand>()
			explodedTupleRegs!!.forEach { read ->
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
				L2_STRIP_MANIFEST,
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
				L2_STRIP_MANIFEST,
				L2ReadBoxedVectorOperand(newReads))

			val trampolineBlock = generator.createBasicBlock(
				"edge-split for restart with arguments")

			// Use an L2_JUMP_BACK to get to the trampoline block.
			generator.addInstruction(
				L2_JUMP_BACK,
				edgeTo(trampolineBlock),
				L2ReadBoxedVectorOperand(newReads))

			// Finally, jump to the restartLoopHeadBlock, where the n@1 semantic
			// slots will be added to the phis.
			generator.startBlock(trampolineBlock)
			generator.addInstruction(
				L2_JUMP,
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
		// next L2 instruction).  Extract the tuple of arguments back into a
		// vector of individual registers, aborting code generation of this
		// special invocation if it's not possible.

		// Examine the continuation's function's type.
		val continuationType = continuationReg.type()
		val functionType = continuationType.functionType()
		val functionArgsType = functionType.argsTupleType()
		val functionTypeSizes = functionArgsType.sizeRange()
		val upperBound = functionTypeSizes.upperBound()
		if (!upperBound.isInt
			|| !functionTypeSizes.lowerBound().equals(upperBound))
		{
			// The exact function signature is not known.  Give up.
			return false
		}
		val argsSize = upperBound.extractInt()
		val explodedArgumentRegs = generator.explodeTupleIfPossible(
			argumentsTupleReg,
			toList(functionArgsType.tupleOfTypesFromTo(1, argsSize)))
		explodedArgumentRegs ?: return false

		translator.addInstruction(
			L2_RESTART_CONTINUATION_WITH_ARGUMENTS,
			continuationReg,
			L2ReadBoxedVectorOperand(explodedArgumentRegs))
		assert(!generator.currentlyReachable())
		generator.startBlock(
			generator.createBasicBlock(
				"unreachable after L2_RESTART_CONTINUATION_WITH_ARGUMENTS"))
		return true
	}
}
