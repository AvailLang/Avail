/*
 * L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

import avail.descriptor.functions.CompiledCodeDescriptor.Companion.decrementCountdownToReoptimize
import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.A_RawFunction.Companion.reoptimizationCountdown
import avail.descriptor.representation.A_RawFunction.Companion.startingChunk
import avail.interpreter.JavaLibrary.int
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelTwo.L2Chunk
import avail.interpreter.levelTwo.L2Instruction
import avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import avail.optimizer.OptimizationLevel
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.JVMTranslator
import avail.optimizer.jvm.ReferencedInGeneratedCode
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import java.util.concurrent.atomic.AtomicLong

/**
 * Explicitly decrement the current compiled code's countdown, the
 * [reoptimizationCountdown] of its [A_RawFunction], which the generated code
 * captures as a constant.  If it reaches zero then re-optimize the code and
 * jump to its
 * [L2Chunk.offsetAfterInitialTryPrimitive], which expects the arguments to
 * still be set up in the [Interpreter].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO
constructor(
	var currentOptimizationLevel: L2IntImmediateOperand,
	var isEntryPointFlag: L2IntImmediateOperand
): L2Instruction()
{
	override val hasSideEffect: Boolean get() = true

	override val isEntryPoint get() = isEntryPointFlag.intValue != 0

	override fun JVMTranslator.translateToJVM()
	{
		// :: if (L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.decrement(
		// ::    interpreter, countdown, targetOptimizationLevel)) return null;
		loadInterpreter()
		// :: interpreter
		loadLiteralObject(code!!.reoptimizationCountdown)
		// :: interpreter, countdown
		intConstant(currentOptimizationLevel.intValue)
		// :: interpreter, countdown, level
		generateCall(decrementMethod)
		// :: bool
		val didNotOptimize = Label()
		method.visitJumpInsn(Opcodes.IFEQ, didNotOptimize)
		// Directly invoke the newly generated chunk.
		loadInterpreter()
		// :: interpreter
		generateCall(Interpreter.interpreterRunChunkMethod)
		// :: valueOrNull
		method.visitInsn(Opcodes.ARETURN)
		method.visitLabel(didNotOptimize)
		// ::
	}

	companion object
	{
		/**
		 * Decrement the counter associated with the code.  If this thread was
		 * responsible for decrementing it to zero, (re)optimize the code by
		 * producing a new chunk.  Return whether the chunk was replaced.
		 *
		 * @param interpreter
		 *   The interpreter for the current thread.
		 * @param countdown
		 *   The code's [reoptimizationCountdown], captured by the caller.
		 * @param currentOptimizationLevel
		 *   What level of optimization to apply if reoptimization occurs.
		 * @return
		 *   Whether a new chunk was activated, whether or not the optimization
		 *   was due to this fiber.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun decrement(
			interpreter: Interpreter,
			countdown: AtomicLong,
			currentOptimizationLevel: Int
		): Boolean
		{
			val hitZero = decrementCountdownToReoptimize(countdown)
			if (hitZero)
			{
				val code = interpreter.function!!.code()
				OptimizationLevel.optimizationLevel(currentOptimizationLevel)
					.optimize(code, interpreter)
				val chunk = code.startingChunk
				interpreter.chunk = chunk
				interpreter.setOffset(chunk.offsetAfterInitialTryPrimitive)
			}
			return hitZero
		}

		/**
		 * The [CheckedMethod] for [decrement].
		 */
		private val decrementMethod = staticMethod(
			::decrement,
			Interpreter::class.java,
			AtomicLong::class.java,
			int)
	}
}
