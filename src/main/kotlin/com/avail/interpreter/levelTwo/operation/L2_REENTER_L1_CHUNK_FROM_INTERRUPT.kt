/*
 * L2_REENTER_L1_CHUNK_FROM_INTERRUPT.kt
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
import com.avail.descriptor.representation.NilDescriptor
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.execution.Interpreter.Companion.log
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import org.objectweb.asm.MethodVisitor
import java.util.logging.Level

/**
 * This is the first instruction of the L1 interpreter's on-ramp for resuming
 * after an interrupt.  The reified [A_Continuation] that was captured
 * (and is now being resumed) pointed to this [L2Instruction].  That
 * continuation is current in the [Interpreter.getReifiedContinuation].
 * Pop it from that continuation chain, create suitable pointer and integer
 * registers as expected by [L2_INTERPRET_LEVEL_ONE], then explode the
 * continuation's slots into those registers.  The [Interpreter.function]
 * should also have already been set up to agree with the continuation's
 * function.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_REENTER_L1_CHUNK_FROM_INTERRUPT : L2Operation()
{
	override fun hasSideEffect() = true

	override fun isEntryPoint(instruction: L2Instruction): Boolean = true

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		// :: L2_REENTER_L1_CHUNK_FROM_INTERRUPT.reenter();
		translator.loadInterpreter(method)
		reenterMethod.generateCall(method)
	}

	/**
	 * Reenter from an interrupt.
	 *
	 * @param interpreter
	 *   The [Interpreter].
	 */
	@ReferencedInGeneratedCode
	@JvmStatic
	fun reenter(interpreter: Interpreter)
	{
		val continuation: A_Continuation =
			interpreter.getReifiedContinuation()!!
		interpreter.setReifiedContinuation(continuation.caller())
		if (Interpreter.debugL1)
		{
			log(
				Interpreter.loggerDebugL1,
				Level.FINER,
				"{0}Reenter L1 from interrupt",
				interpreter.debugModeString)
		}
		val function = interpreter.function!!
		assert(function === continuation.function())
		val numSlots = continuation.numSlots()
		// Should agree with L2_PREPARE_NEW_FRAME_FOR_L1.
		val stepper = interpreter.levelOneStepper
		stepper.pointers = Array(numSlots + 1)
		{
			if (it == 0)
			{
				NilDescriptor.nil
			}
			else
			{
				continuation.stackAt(it)
			}
		}
		function.code().setUpInstructionDecoder(stepper.instructionDecoder)
		stepper.instructionDecoder.pc(continuation.pc())
		stepper.stackp = continuation.stackp()
	}

	/** The [CheckedMethod] for [reenter].  */
	private val reenterMethod = CheckedMethod.staticMethod(
		L2_REENTER_L1_CHUNK_FROM_INTERRUPT::class.java,
		::reenter.name,
		Void.TYPE,
		Interpreter::class.java)
}
