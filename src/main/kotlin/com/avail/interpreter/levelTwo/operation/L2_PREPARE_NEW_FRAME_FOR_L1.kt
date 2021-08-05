/*
 * L2_PREPARE_NEW_FRAME_FOR_L1.kt
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
package com.avail.interpreter.levelTwo.operation

import com.avail.descriptor.functions.A_Continuation
import com.avail.descriptor.functions.A_RawFunction.Companion.localTypeAt
import com.avail.descriptor.functions.A_RawFunction.Companion.numArgs
import com.avail.descriptor.functions.A_RawFunction.Companion.numLocals
import com.avail.descriptor.functions.A_RawFunction.Companion.numSlots
import com.avail.descriptor.functions.ContinuationDescriptor.Companion.createContinuationWithFrame
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.variables.A_Variable
import com.avail.descriptor.variables.VariableDescriptor.Companion.newVariableWithOuterType
import com.avail.interpreter.Primitive
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.optimizer.StackReifier
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.performance.Statistic
import com.avail.performance.StatisticReport.REIFICATIONS
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * This operation is only used when entering a function that uses the
 * default chunk.  A new function has been set up for execution.  Its
 * arguments have been written to the architectural registers.  If this is a
 * primitive, then the primitive has already been attempted and failed,
 * writing the failure value into the failureValueRegister().  Set up the pc
 * and stackp, as well as local variables.  Also transfer the primitive
 * failure value into the first local variable if this is a primitive (and
 * therefore failed).
 *
 *
 * Also check for interrupts after all that, reifying and suspending the fiber
 * if needed.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_PREPARE_NEW_FRAME_FOR_L1 : L2Operation()
{
	override fun hasSideEffect() = true

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		// :: reifier = L2_PREPARE_NEW_FRAME_FOR_L1.prepare(interpreter);
		translator.loadInterpreter(method)
		prepareMethod.generateCall(method)
		method.visitInsn(Opcodes.DUP)
		method.visitVarInsn(Opcodes.ASTORE, translator.reifierLocal())
		// :: if (reifier === null) goto noReification;
		val noReification = Label()
		method.visitJumpInsn(Opcodes.IFNULL, noReification)
		// :: else return reifier;
		method.visitVarInsn(Opcodes.ALOAD, translator.reifierLocal())
		method.visitInsn(Opcodes.ARETURN)
		method.visitLabel(noReification)
	}

	/** [Statistic] for reifying in L1 interrupt-handler preamble. */
	private val reificationForInterruptInL1Stat = Statistic(
		REIFICATIONS, "Reification for interrupt in L1 preamble")

	/**
	 * Prepare a new frame for L1 interpretation.
	 *
	 * @param interpreter
	 *   The [Interpreter].
	 * @return
	 *   A [StackReifier], if any.
	 */
	@ReferencedInGeneratedCode
	@JvmStatic
	fun prepare(interpreter: Interpreter): StackReifier?
	{
		assert(!interpreter.exitNow)
		val function = interpreter.function!!
		val code = function.code()
		val numArgs = code.numArgs()
		val numLocals = code.numLocals
		val numArgsAndLocals = numArgs + numLocals
		val numSlots = code.numSlots
		// The L2 instructions that implement L1 don't reserve room for any
		// fixed registers, but they assume [0] is unused (to simplify
		// indexing).  I.e., pointers[1] <-> continuation.stackAt(1).
		val stepper = interpreter.levelOneStepper
		stepper.pointers = Array(numSlots + 1) { i ->
			when
			{
				// The 0th position will never be accessed
				i == 0 -> NilDescriptor.nil
				// Populate the arguments from argsBuffer.
				i <= numArgs -> interpreter.argsBuffer[i - 1]
				// Create actual local variables.
				i <= numArgsAndLocals ->
					newVariableWithOuterType(code.localTypeAt(i - numArgs))
				else ->
				{
					// Write nil into the remaining stack slots.
					// These values should not encounter any kind of ordinary
					// use, but they must still be transferred into a
					// continuation during reification.  Therefore, don't
					// use Java nulls here.
					NilDescriptor.nil
				}
			}
		}

		code.setUpInstructionDecoder(stepper.instructionDecoder)
		stepper.instructionDecoder.pc(1)
		stepper.stackp = numSlots + 1
		val primitive = code.codePrimitive()
		if (primitive !== null)
		{
			// A failed primitive.  The failure value was captured in the
			// latestResult().
			assert(!primitive.hasFlag(Primitive.Flag.CannotFail))
			val primitiveFailureValue: A_BasicObject =
				interpreter.getLatestResult()
			val primitiveFailureVariable: A_Variable =
				stepper.pointerAt(numArgs + 1)
			primitiveFailureVariable.setValue(primitiveFailureValue)
		}
		if (interpreter.isInterruptRequested)
		{
			// Build an interrupted continuation, reify the rest of the stack,
			// and push the continuation onto the reified stack.  Then process
			// the interrupt, which may or may not suspend the fiber.
			val continuation: A_Continuation = createContinuationWithFrame(
				function = function,
				caller = NilDescriptor.nil,
				registerDump = NilDescriptor.nil,
				pc = 1,  // start of function
				stackp = numSlots + 1,  // empty stack
				levelTwoChunk = L2Chunk.unoptimizedChunk,
				levelTwoOffset = ChunkEntryPoint.TO_RESUME.offsetInDefaultChunk,
				frameValues = listOf(*stepper.pointers),
				zeroBasedStartIndex = 1)
			// Push the continuation from above onto the reified stack.
			interpreter.isReifying = true
			return StackReifier(true, reificationForInterruptInL1Stat)
			{
				// Push the continuation from above onto the reified stack.
				interpreter.returnNow = false
				interpreter.setReifiedContinuation(
					continuation.replacingCaller(
						interpreter.getReifiedContinuation()!!))
				interpreter.processInterrupt(
					interpreter.getReifiedContinuation()!!)
				interpreter.isReifying = false
			}
		}
		return null
	}

	/** The [CheckedMethod] for [prepare]. */
	private val prepareMethod = CheckedMethod.staticMethod(
		L2_PREPARE_NEW_FRAME_FOR_L1::class.java,
		::prepare.name,
		StackReifier::class.java,
		Interpreter::class.java)
}
