/*
 * L2_TRY_PRIMITIVE.java
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

import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.representation.AvailObject
import com.avail.interpreter.Primitive
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.execution.Interpreter.Companion.log
import com.avail.interpreter.levelTwo.L2Instruction
import com.avail.interpreter.levelTwo.L2OperandType
import com.avail.interpreter.levelTwo.L2Operation
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.*
import com.avail.interpreter.levelTwo.ReadsHiddenVariable
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand
import com.avail.optimizer.L2Generator
import com.avail.optimizer.RegisterSet
import com.avail.optimizer.StackReifier
import com.avail.optimizer.jvm.CheckedMethod
import com.avail.optimizer.jvm.JVMTranslator
import com.avail.optimizer.jvm.ReferencedInGeneratedCode
import com.avail.utility.Nulls
import com.avail.utility.evaluation.Continuation0
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.util.logging.Level

/**
 * Expect the [AvailObject] (pointers) array and int array to still
 * reflect the caller. Expect [Interpreter.argsBuffer] to have been
 * loaded with the arguments to this primitive function, and expect the
 * code/function/chunk to have been updated for this primitive function.
 * Try to execute a primitive, setting the [Interpreter.returnNow] flag
 * and [latestResult][Interpreter.setLatestResult] if
 * successful. The caller always has the responsibility of checking the return
 * value, if applicable at that call site.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@ReadsHiddenVariable(theValue = [
	CURRENT_CONTINUATION::class,
	CURRENT_FUNCTION::class,
	//	CURRENT_ARGUMENTS.class,
	LATEST_RETURN_VALUE::class])
object L2_TRY_PRIMITIVE : L2Operation(
	L2OperandType.PRIMITIVE.named("primitive"))
{
	override fun isEntryPoint(instruction: L2Instruction): Boolean = true

	override fun propagateTypes(
		instruction: L2Instruction,
		registerSets: List<RegisterSet>,
		generator: L2Generator)
	{
		// This instruction should only be used in the L1 interpreter loop.
		throw UnsupportedOperationException()
	}

	// It could fail and jump.
	override fun hasSideEffect(): Boolean = true

	/**
	 * Attempt the [inlineable][Primitive.Flag.CanInline] [primitive][Primitive].
	 *
	 * @param interpreter
	 *   The [Interpreter].
	 * @param function
	 *   The primitive [A_Function] to invoke.
	 * @param primitive
	 *   The [Primitive] to attempt.
	 * @return
	 *   The [StackReifier], if any.
	 */
	@JvmStatic
	@ReferencedInGeneratedCode
	fun attemptInlinePrimitive(
		interpreter: Interpreter,
		function: A_Function,
		primitive: Primitive): StackReifier?
	{
		// It can succeed or fail, but it can't mess with the fiber's stack.
		if (Interpreter.debugL2)
		{
			log(
				Interpreter.loggerDebugL2,
				Level.FINER,
				"{0}          inline prim = {1}",
				interpreter.debugModeString,
				primitive.fieldName())
		}
		val timeBefore = interpreter.beforeAttemptPrimitive(primitive)
		val result = primitive.attempt(interpreter)
		interpreter.afterAttemptPrimitive(primitive, timeBefore, result)
		return when (result)
		{
			Primitive.Result.SUCCESS ->
			{
				assert(interpreter.latestResultOrNull() != null)
				interpreter.function = null
				interpreter.returnNow = true
				interpreter.returningFunction = function
				null
			}
			Primitive.Result.FAILURE ->
			{
				assert(interpreter.latestResultOrNull() != null)
				interpreter.function = function
				interpreter.setOffset(
					Nulls.stripNull(interpreter.chunk)
						.offsetAfterInitialTryPrimitive())
				assert(!interpreter.returnNow)
				null
			}
			Primitive.Result.READY_TO_INVOKE ->
			{
				assert(primitive.hasFlag(Primitive.Flag.Invokes))
				val stepper = interpreter.levelOneStepper
				val savedChunk = interpreter.chunk
				val savedOffset = interpreter.offset
				val savedPointers = stepper.pointers

				// The invocation did a runChunk, but we need to do another
				// runChunk now (via invokeFunction).  Only one should count
				// as an unreified frame (specifically the inner one we're
				// about to start).
				interpreter.adjustUnreifiedCallDepthBy(-1)
				val reifier = interpreter.invokeFunction(
					Nulls.stripNull(interpreter.function))
				interpreter.adjustUnreifiedCallDepthBy(1)
				interpreter.function = function
				interpreter.chunk = savedChunk
				interpreter.setOffset(savedOffset)
				stepper.pointers = savedPointers
				if (reifier != null)
				{
					return reifier
				}
				assert(interpreter.latestResultOrNull() != null)
				interpreter.returnNow = true
				interpreter.returningFunction = function
				null
			}
			Primitive.Result.CONTINUATION_CHANGED ->
			{
				assert(primitive.hasFlag(Primitive.Flag.CanSwitchContinuations))
				val newContinuation =
					Nulls.stripNull(interpreter.getReifiedContinuation())
				val newFunction = interpreter.function
				val newChunk = interpreter.chunk
				val newOffset = interpreter.offset
				val newReturnNow = interpreter.returnNow
				val newReturnValue =
					if (newReturnNow)
					{
						interpreter.getLatestResult()
					}
					else
					{
						null
					}
				interpreter.isReifying = true
				StackReifier(
					false,
					Nulls.stripNull(primitive.reificationAbandonmentStat),
					Continuation0 {
						interpreter.setReifiedContinuation(newContinuation)
						interpreter.function = newFunction
						interpreter.chunk = newChunk
						interpreter.setOffset(newOffset)
						interpreter.returnNow = newReturnNow
						interpreter.setLatestResult(newReturnValue)
						interpreter.isReifying = false
					})
			}
			Primitive.Result.FIBER_SUSPENDED ->
			{
				assert(false)
					{ "CanInline primitive must not suspend fiber" }
				null
			}
		}
	}

	override fun translateToJVM(
		translator: JVMTranslator,
		method: MethodVisitor,
		instruction: L2Instruction)
	{
		val primitiveOperand =
			instruction.operand<L2PrimitiveOperand>(0)
		val primitive = primitiveOperand.primitive
		translator.loadInterpreter(method)
		// interpreter
		method.visitInsn(Opcodes.DUP)
		// interpreter, interpreter
		Interpreter.interpreterFunctionField.generateRead(method)
		// interpreter, fn
		translator.literal(method, primitive)
		// interpreter, fn, prim
		if (primitive.hasFlag(Primitive.Flag.CanInline))
		{
			// :: return L2_TRY_PRIMITIVE.attemptInlinePrimitive(
			// ::    interpreter, function, primitive);
			attemptTheInlinePrimitiveMethod.generateCall(method)
		}
		else
		{
			// :: return L2_TRY_PRIMITIVE.attemptNonInlinePrimitive(
			// ::    interpreter, function, primitive);
			attemptTheNonInlinePrimitiveMethod.generateCall(method)
		}
		method.visitInsn(Opcodes.ARETURN)
	}

	/**
	 * Attempt the [non-inlineable][Primitive.Flag.CanInline]
	 * [primitive][Primitive].
	 *
	 * @param interpreter
	 *   The [Interpreter].
	 * @param function
	 *   The [A_Function].
	 * @param primitive
	 *   The [Primitive].
	 * @return
	 *   The [StackReifier], if any.
	 */
	@JvmStatic
	@ReferencedInGeneratedCode
	fun attemptNonInlinePrimitive(
		interpreter: Interpreter,
		function: A_Function?,
		primitive: Primitive): StackReifier
	{
		if (Interpreter.debugL2)
		{
			log(
				Interpreter.loggerDebugL2,
				Level.FINER,
				"{0}          reifying for {1}",
				interpreter.debugModeString,
				primitive.fieldName())
		}
		val stepper = interpreter.levelOneStepper
		val savedChunk = Nulls.stripNull(interpreter.chunk)
		val savedOffset = interpreter.offset
		val savedPointers = stepper.pointers

		// Continue in this frame where it left off, right after
		// the L2_TRY_OPTIONAL_PRIMITIVE instruction.
		// Inline and non-inline primitives are each allowed to
		// change the continuation.  The stack has already been
		// reified here, so just continue in whatever frame was
		// set up by the continuation.
		// The exitNow flag is set to ensure the interpreter
		// will wind down correctly.  It should be in a state
		// where all frames have been reified, so returnNow
		// would be unnecessary.
		interpreter.isReifying = true
		return StackReifier(
			true,
			Nulls.stripNull(primitive.reificationForNoninlineStat),
			Continuation0 {
				assert(interpreter.unreifiedCallDepth() == 0) 
					{ "Should have reified stack for non-inlineable primitive" }
				interpreter.chunk = savedChunk
				interpreter.setOffset(savedOffset)
				stepper.pointers = savedPointers
				interpreter.function = function
				if (Interpreter.debugL2)
				{
					log(
						Interpreter.loggerDebugL2,
						Level.FINER,
						"{0}          reified, now starting {1}",
						interpreter.debugModeString,
						primitive.fieldName())
				}
				val timeBefore = 
					interpreter.beforeAttemptPrimitive(primitive)
				val result = primitive.attempt(interpreter)
				interpreter.afterAttemptPrimitive(
					primitive, timeBefore, result)
				when (result)
				{
					Primitive.Result.SUCCESS ->
					{
						assert(interpreter.latestResultOrNull() != null)
						interpreter.returnNow = true
						interpreter.returningFunction = function
					}
					Primitive.Result.FAILURE ->
					{
						assert(interpreter.latestResultOrNull() != null)
						interpreter.function = function
						interpreter.setOffset(
							Nulls.stripNull(interpreter.chunk)
								.offsetAfterInitialTryPrimitive())
						assert(!interpreter.returnNow)
					}
					Primitive.Result.READY_TO_INVOKE ->
					{
						assert(false) 
							{ "Invoking primitives should be inlineable" }
					}
					Primitive.Result.CONTINUATION_CHANGED ->
					{
						assert(primitive.hasFlag(
							Primitive.Flag.CanSwitchContinuations))
					}
					Primitive.Result.FIBER_SUSPENDED ->
					{
						assert(interpreter.exitNow)
						interpreter.returnNow = false
					}
				}
				interpreter.isReifying = false
			})
	}

	/**
	 * The [CheckedMethod] for [attemptInlinePrimitive].
	 */
	val attemptTheInlinePrimitiveMethod =
		CheckedMethod.staticMethod(
			L2_TRY_PRIMITIVE::class.java,
			::attemptInlinePrimitive.name,
			StackReifier::class.java,
			Interpreter::class.java,
			A_Function::class.java,
			Primitive::class.java)

	/**
	 * The [CheckedMethod] for [attemptNonInlinePrimitive].
	 */
	val attemptTheNonInlinePrimitiveMethod =
		CheckedMethod.staticMethod(
			L2_TRY_PRIMITIVE::class.java,
			::attemptNonInlinePrimitive.name,
			StackReifier::class.java,
			Interpreter::class.java,
			A_Function::class.java,
			Primitive::class.java)
}