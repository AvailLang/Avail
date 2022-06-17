/*
 * L2_REENTER_L1_CHUNK_FROM_CALL.kt
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
package avail.interpreter.levelTwo.operation

import avail.descriptor.functions.A_Continuation
import avail.descriptor.functions.A_Continuation.Companion.caller
import avail.descriptor.functions.A_Continuation.Companion.function
import avail.descriptor.functions.A_Continuation.Companion.numSlots
import avail.descriptor.functions.A_Continuation.Companion.pc
import avail.descriptor.functions.A_Continuation.Companion.stackAt
import avail.descriptor.functions.A_Continuation.Companion.stackp
import avail.descriptor.representation.NilDescriptor
import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.L2Operation
import avail.optimizer.StackReifier
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.jvm.ReferencedInGeneratedCode
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.logging.Level

/**
 * This is the first instruction of the L1 interpreter's on-ramp for resuming
 * after a callee returns.  The reified [A_Continuation] that was captured
 * (and is now being resumed) pointed to this [L2Instruction].  That
 * continuation is current in the [Interpreter.getReifiedContinuation].
 * Pop it from that continuation chain, create suitable pointer and integer
 * registers as expected by [L2_INTERPRET_LEVEL_ONE], then explode the
 * continuation's slots into those registers.  The [Interpreter.function]
 * should also have already been set up to agree with the continuation's
 * function.
 *
 * The value being returned is in [Interpreter.getLatestResult], and
 * the top-of-stack of the continuation contains the type to check it against.
 * Whether to skip the return check is up to the generated L2 code after an
 * entry point.  In this case (reentering L1), we always do the check.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_REENTER_L1_CHUNK_FROM_CALL : L2Operation()
{
	override val hasSideEffect get() = true

	override fun isEntryPoint(instruction: L2Instruction): Boolean = true

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		// :: if ((temp = reenter(interpreter)) != null) return temp;
		val okLabel = Label()
		translator.loadInterpreter(method)
		reenterMethod.generateCall(method)
		method.visitInsn(Opcodes.DUP)
		method.visitJumpInsn(Opcodes.IFNULL, okLabel)
		method.visitInsn(Opcodes.ARETURN)
		// :: else { }
		method.visitLabel(okLabel)
		method.visitInsn(Opcodes.POP)
	}

	/**
	 * Reenter L1 from a call.
	 *
	 * @param interpreter
	 * The [Interpreter].
	 */
	@ReferencedInGeneratedCode
	@JvmStatic
	fun reenter(interpreter: Interpreter): StackReifier?
	{
		if (Interpreter.debugL1)
		{
			log(
				Interpreter.loggerDebugL1,
				Level.FINER,
				"{0}Reenter L1 from call",
				interpreter.debugModeString)
		}
		val continuation: A_Continuation = interpreter.getReifiedContinuation()!!
		interpreter.setReifiedContinuation(continuation.caller())
		val returnValue = interpreter.getLatestResult()
		val returneeFunction = interpreter.function!!
		assert(returneeFunction === continuation.function())
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
		returneeFunction.code().setUpInstructionDecoder(
			stepper.instructionDecoder)
		stepper.instructionDecoder.pc(continuation.pc())
		val stackp = continuation.stackp()
		stepper.stackp = stackp
		val expectedReturnType = stepper.pointerAt(stackp)
		// Perform a redundant check, since the mechanism for handling failure
		// should stay off the critical HotSpot path.
		if (!returnValue.isInstanceOf(expectedReturnType))
		{
			val returnCheckReifier = stepper.checkReturnType(
				returnValue, expectedReturnType, returneeFunction)
			assert (returnCheckReifier !== null) {
				"The second return type check unexpectedly passed!"
			}
			// The reifier already contains what's needed to synthesize the
			// returnee's frame.
			return returnCheckReifier
		}
		stepper.pointerAtPut(stackp, returnValue)
		return null
	}

	/** The [CheckedMethod] for [reenter]. */
	private val reenterMethod = staticMethod(
		L2_REENTER_L1_CHUNK_FROM_CALL::class.java,
		::reenter.name,
		StackReifier::class.java,
		Interpreter::class.java)
}
