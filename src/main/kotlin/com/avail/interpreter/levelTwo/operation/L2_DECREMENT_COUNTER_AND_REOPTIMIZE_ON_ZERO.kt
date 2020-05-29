/*
 * L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.kt
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
package com.avail.interpreter.levelTwo.operation

import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.levelTwo.L2Chunk
import com.avail.interpreter.levelTwo.L2Chunk.Companion.countdownForNewlyOptimizedCode
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand
import com.avail.optimizer.L1Translator
import com.avail.optimizer.L2Generator.OptimizationLevel
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Explicitly decrement the current compiled code's countdown via
 * [AvailObject.countdownToReoptimize].  If it reaches zero then re-optimize the
 * code and jump to its [L2Chunk.offsetAfterInitialTryPrimitive], which expects
 * the arguments to still be set up in the [Interpreter].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
object L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO : L2Operation(
	L2OperandType.INT_IMMEDIATE.named("new optimization level"),
	L2OperandType.INT_IMMEDIATE.named("is entry point"))
{
	override fun hasSideEffect(): Boolean
	{
		return true
	}

	override fun isEntryPoint(instruction: L2Instruction): Boolean
	{
		val immediate = instruction.operand<L2IntImmediateOperand>(1)
		return immediate.value != 0
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val optimization = instruction.operand<L2IntImmediateOperand>(0)
		//		final L2IntImmediateOperand isEntryPoint = instruction.operand(1);

		// :: if (L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO.decrement(
		// ::    interpreter, targetOptimizationLevel)) return null;
		translator.loadInterpreter(method)
		translator.literal(method, optimization.value)
		decrementMethod.generateCall(method)
		val didNotOptimize = Label()
		method.visitJumpInsn(Opcodes.IFEQ, didNotOptimize)
		method.visitInsn(Opcodes.ACONST_NULL)
		method.visitInsn(Opcodes.ARETURN)
		method.visitLabel(didNotOptimize)
	}
	/**
	 * Decrement the counter associated with the code.  If this thread was
	 * responsible for decrementing it to zero, (re)optimize the code by
	 * producing a new chunk.  Return whether the chunk was replaced.
	 *
	 * @param interpreter
	 * The interpreter for the current thread.
	 * @param targetOptimizationLevel
	 * What level of optimization to apply if reoptimization occurs.
	 * @return
	 * Whether a new chunk was created.
	 */
	@JvmStatic
	@ReferencedInGeneratedCode
	fun decrement(
		interpreter: Interpreter,
		targetOptimizationLevel: Int): Boolean
	{
		val function = interpreter.function!!
		val code = function.code()
		var chunkChanged = false
		code.decrementCountdownToReoptimize { optimize: Boolean ->
			if (optimize)
			{
				code.countdownToReoptimize(
					countdownForNewlyOptimizedCode())
				L1Translator.translateToLevelTwo(
					code,
					OptimizationLevel.optimizationLevel(targetOptimizationLevel),
					interpreter)
			}
			val chunk = code.startingChunk()
			interpreter.chunk = chunk
			interpreter.setOffset(chunk.offsetAfterInitialTryPrimitive())
			chunkChanged = true
		}
		return chunkChanged
	}

	/**
	 * The [CheckedMethod] for [decrement].
	 */
	private val decrementMethod = CheckedMethod.staticMethod(
		L2_DECREMENT_COUNTER_AND_REOPTIMIZE_ON_ZERO::class.java,
		::decrement.name,
		Boolean::class.javaPrimitiveType!!,
		Interpreter::class.java,
		Int::class.javaPrimitiveType)
}
