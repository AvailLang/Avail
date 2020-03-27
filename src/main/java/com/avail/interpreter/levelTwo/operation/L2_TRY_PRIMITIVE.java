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

package com.avail.interpreter.levelTwo.operation;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.functions.A_Function;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.L1InstructionStepper;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_CONTINUATION;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.CURRENT_FUNCTION;
import com.avail.interpreter.levelTwo.L2Operation.HiddenVariable.LATEST_RETURN_VALUE;
import com.avail.interpreter.levelTwo.ReadsHiddenVariable;
import com.avail.interpreter.levelTwo.operand.L2PrimitiveOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.CheckedMethod;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.logging.Level;

import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.levelTwo.L2OperandType.PRIMITIVE;
import static com.avail.optimizer.jvm.CheckedMethod.staticMethod;
import static com.avail.utility.Nulls.stripNull;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DUP;

/**
 * Expect the {@link AvailObject} (pointers) array and int array to still
 * reflect the caller. Expect {@link Interpreter#argsBuffer} to have been
 * loaded with the arguments to this primitive function, and expect the
 * code/function/chunk to have been updated for this primitive function.
 * Try to execute a primitive, setting the {@link Interpreter#returnNow} flag
 * and {@link Interpreter#setLatestResult(A_BasicObject) latestResult} if
 * successful. The caller always has the responsibility of checking the return
 * value, if applicable at that call site.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@ReadsHiddenVariable({
	CURRENT_CONTINUATION.class,
	CURRENT_FUNCTION.class,
//	CURRENT_ARGUMENTS.class,
	LATEST_RETURN_VALUE.class,
})
public final class L2_TRY_PRIMITIVE
extends L2Operation
{
	/**
	 * Construct an {@code L2_TRY_PRIMITIVE}.
	 */
	private L2_TRY_PRIMITIVE ()
	{
		super(
			PRIMITIVE.is("primitive"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_TRY_PRIMITIVE instance =
		new L2_TRY_PRIMITIVE();

	@Override
	public boolean isEntryPoint (final L2Instruction instruction)
	{
		return true;
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Generator generator)
	{
		// This instruction should only be used in the L1 interpreter loop.
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It could fail and jump.
		return true;
	}

	/**
	 * Attempt the {@linkplain Flag#CanInline inlineable} {@linkplain Primitive
	 * primitive}.
	 *
	 * @param interpreter
	 *        The {@link Interpreter}.
	 * @param function
	 *        The primitive {@link A_Function} to invoke.
	 * @param primitive
	 *        The {@link Primitive} to attempt.
	 * @return The {@link StackReifier}, if any.
	 */
	@SuppressWarnings("unused")
	@ReferencedInGeneratedCode
	public static @Nullable StackReifier attemptInlinePrimitive (
		final Interpreter interpreter,
		final A_Function function,
		final Primitive primitive)
	{
		// It can succeed or fail, but it can't mess with the fiber's stack.
		if (Interpreter.debugL2)
		{
			Interpreter.log(
				Interpreter.loggerDebugL2,
				Level.FINER,
				"{0}          inline prim = {1}",
				interpreter.debugModeString,
				primitive.name());
		}
		final long timeBefore = interpreter.beforeAttemptPrimitive(primitive);

		final Result result = primitive.attempt(interpreter);

		interpreter.afterAttemptPrimitive(primitive, timeBefore, result);
		switch (result)
		{
			case SUCCESS:
			{
				assert interpreter.latestResultOrNull() != null;
				interpreter.function = null;
				interpreter.returnNow = true;
				interpreter.returningFunction = function;
				return null;
			}
			case FAILURE:
			{
				// The failure value was set up, and the next L2 instruction
				// will set up the frame, including capturing it in a local.
				assert interpreter.latestResultOrNull() != null;
				interpreter.function = function;
				interpreter.offset =
					stripNull(interpreter.chunk)
						.offsetAfterInitialTryPrimitive();
				assert !interpreter.returnNow;
				return null;
			}
			case READY_TO_INVOKE:
			{
				// A Flag.Invokes primitive needed to invoke a function, but
				// it's not allowed to directly.  Instead, it set up the
				// argsBuffer and function for us to do the call.  The
				// original primitive does not have a frame built for it in
				// the event of reification.
				assert primitive.hasFlag(Invokes);

				final L1InstructionStepper stepper =
					interpreter.levelOneStepper;
				final @Nullable L2Chunk savedChunk = interpreter.chunk;
				final int savedOffset = interpreter.offset;
				final AvailObject[] savedPointers = stepper.pointers;

				// The invocation did a runChunk, but we need to do another
				// runChunk now (via invokeFunction).  Only one should count
				// as an unreified frame (specifically the inner one we're
				// about to start).

				interpreter.adjustUnreifiedCallDepthBy(-1);
				final @Nullable StackReifier reifier =
					interpreter.invokeFunction(
						stripNull(interpreter.function));
				interpreter.adjustUnreifiedCallDepthBy(1);

				interpreter.function = function;
				interpreter.chunk = savedChunk;
				interpreter.offset = savedOffset;
				stepper.pointers = savedPointers;
				if (reifier != null)
				{
					return reifier;
				}

				assert interpreter.latestResultOrNull() != null;
				interpreter.returnNow = true;
				interpreter.returningFunction = function;
				return null;
			}
			case CONTINUATION_CHANGED:
			{
				// Inline primitives are allowed to change the continuation,
				// although they can't reify the stack first.  Abort the
				// stack here, and resume the continuation.
				assert primitive.hasFlag(CanSwitchContinuations);

				final AvailObject newContinuation =
					stripNull(interpreter.getReifiedContinuation());
				final @Nullable A_Function newFunction = interpreter.function;
				final @Nullable L2Chunk newChunk = interpreter.chunk;
				final int newOffset = interpreter.offset;
				final boolean newReturnNow = interpreter.returnNow;
				final @Nullable AvailObject newReturnValue =
					newReturnNow ? interpreter.getLatestResult() : null;
				interpreter.isReifying = true;
				return new StackReifier(
					false,
					stripNull(primitive.getReificationAbandonmentStat()),
					() ->
					{
						interpreter.setReifiedContinuation(newContinuation);
						interpreter.function = newFunction;
						interpreter.chunk = newChunk;
						interpreter.offset = newOffset;
						interpreter.returnNow = newReturnNow;
						interpreter.setLatestResult(newReturnValue);
						interpreter.isReifying = false;
					});
			}
			case FIBER_SUSPENDED:
			{
				assert false : "CanInline primitive must not suspend fiber";
				return null;
			}
		}
		return null;
	}

	/**
	 * Attempt the {@linkplain Flag#CanInline non-inlineable} {@linkplain
	 * Primitive primitive}.
	 *
	 * @param interpreter
	 *        The {@link Interpreter}.
	 * @param function
	 *        The {@link A_Function}.
	 * @param primitive
	 *        The {@link Primitive}.
	 * @return The {@link StackReifier}, if any.
	 */
	@SuppressWarnings("unused")
	@ReferencedInGeneratedCode
	public static StackReifier attemptNonInlinePrimitive (
		final Interpreter interpreter,
		final A_Function function,
		final Primitive primitive)
	{
		if (Interpreter.debugL2)
		{
			Interpreter.log(
				Interpreter.loggerDebugL2,
				Level.FINER,
				"{0}          reifying for {1}",
				interpreter.debugModeString,
				primitive.name());
		}
		final L1InstructionStepper stepper = interpreter.levelOneStepper;
		final L2Chunk savedChunk = stripNull(interpreter.chunk);
		final int savedOffset = interpreter.offset;
		final AvailObject[] savedPointers = stepper.pointers;

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
		interpreter.isReifying = true;
		return new StackReifier(
			true,
			stripNull(primitive.getReificationForNoninlineStat()),
			() ->
			{
				assert interpreter.unreifiedCallDepth() == 0
					: "Should have reified stack for non-inlineable primitive";
				interpreter.chunk = savedChunk;
				interpreter.offset = savedOffset;
				stepper.pointers = savedPointers;
				interpreter.function = function;

				if (Interpreter.debugL2)
				{
					Interpreter.log(
						Interpreter.loggerDebugL2,
						Level.FINER,
						"{0}          reified, now starting {1}",
						interpreter.debugModeString,
						primitive.name());
				}
				final long timeBefore =
					interpreter.beforeAttemptPrimitive(primitive);
				final Result result = primitive.attempt(interpreter);
				interpreter.afterAttemptPrimitive(
					primitive, timeBefore, result);
				switch (result)
				{
					case SUCCESS:
					{
						assert interpreter.latestResultOrNull() != null;
						interpreter.returnNow = true;
						interpreter.returningFunction = function;
						break;
					}
					case FAILURE:
					{
						// Continue in this frame where it left off, right after
						// the L2_TRY_OPTIONAL_PRIMITIVE instruction.
						assert interpreter.latestResultOrNull() != null;
						interpreter.function = function;
						interpreter.offset =
							stripNull(interpreter.chunk)
								.offsetAfterInitialTryPrimitive();
						assert !interpreter.returnNow;
						break;
					}
					case READY_TO_INVOKE:
					{
						assert false
							: "Invoking primitives should be inlineable";
						break;
					}
					case CONTINUATION_CHANGED:
					{
						// Inline and non-inline primitives are each allowed to
						// change the continuation.  The stack has already been
						// reified here, so just continue in whatever frame was
						// set up by the continuation.
						assert primitive.hasFlag(CanSwitchContinuations);
						break;
					}
					case FIBER_SUSPENDED:
					{
						// The exitNow flag is set to ensure the interpreter
						// will wind down correctly.  It should be in a state
						// where all frames have been reified, so returnNow
						// would be unnecessary.
						assert interpreter.exitNow;
						interpreter.returnNow = false;
						break;
					}
				}
				interpreter.isReifying = false;
			});
	}

	/**
	 * The {@link CheckedMethod} for
	 * {@link #attemptInlinePrimitive(Interpreter, A_Function, Primitive)}.
	 */
	public static final CheckedMethod attemptTheInlinePrimitiveMethod =
		staticMethod(
			L2_TRY_PRIMITIVE.class,
			"attemptInlinePrimitive",
			StackReifier.class,
			Interpreter.class,
			A_Function.class,
			Primitive.class);

	/**
	 * The {@link CheckedMethod} for
	 * {@link #attemptNonInlinePrimitive(Interpreter, A_Function, Primitive)}.
	 */
	public static final CheckedMethod attemptTheNonInlinePrimitiveMethod =
		staticMethod(
			L2_TRY_PRIMITIVE.class,
			"attemptNonInlinePrimitive",
			StackReifier.class,
			Interpreter.class,
			A_Function.class,
			Primitive.class);

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2PrimitiveOperand primitiveOperand = instruction.operand(0);

		final Primitive primitive = primitiveOperand.primitive;
		translator.loadInterpreter(method);
		// interpreter
		method.visitInsn(DUP);
		// interpreter, interpreter
		Interpreter.interpreterFunctionField.generateRead(method);
		// interpreter, fn
		translator.literal(method, primitive);
		// interpreter, fn, prim
		if (primitive.hasFlag(CanInline))
		{
			// :: return L2_TRY_PRIMITIVE.attemptInlinePrimitive(
			// ::    interpreter, function, primitive);
			attemptTheInlinePrimitiveMethod.generateCall(method);
		}
		else
		{
			// :: return L2_TRY_PRIMITIVE.attemptNonInlinePrimitive(
			// ::    interpreter, function, primitive);
			attemptTheNonInlinePrimitiveMethod.generateCall(method);
		}
		method.visitInsn(ARETURN);
	}
}
