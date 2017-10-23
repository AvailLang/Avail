/**
 * L2_TRY_PRIMITIVE.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Result;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.ReifyStackThrowable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.utility.Nulls.stripNull;

/**
 * Expect the AvailObject (pointers) array and int array to still reflect the
 * caller.  Expect argsBuffer to have been loaded with the arguments to this
 * primitive function, and expect the code/function/chunk to have been updated
 * for this primitive function.  Try to execute the primitive, setting the
 * returnNow flag and latestResult if successful.  The caller always has the
 * responsibility of checking the return value, if applicable at that call site.
 */
public class L2_TRY_PRIMITIVE
extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_TRY_PRIMITIVE().init();

	@Override
	public void step (
		final L2Instruction instruction,
		final Interpreter interpreter)
	throws ReifyStackThrowable
	{
		final A_Function function = stripNull(interpreter.function);
		final @Nullable Primitive primitive = function.code().primitive();
		if (primitive == null)
		{
			// Not a primitive.  Exit quickly, having done nothing.
			if (Interpreter.debugL2)
			{
				System.out.println("          (no prim)");
			}
			return;
		}

		if (primitive.hasFlag(CanInline))
		{
			// It can succeed or fail, but it can't mess with the fiber's stack.
			if (Interpreter.debugL2)
			{
				System.out.println("          inline = " + primitive.name());
			}
			final Result result = interpreter.attemptPrimitive(
				primitive, interpreter.argsBuffer, interpreter.skipReturnCheck);
			switch (result)
			{
				case SUCCESS:
				{
					interpreter.function = null;
					interpreter.returnNow = true;
					interpreter.returningFunction = function;
					return;
				}
				case FAILURE:
				{
					// The failure value was set up, and the next L2 instruction
					// will set up the frame, including capturing it in a local.
					interpreter.function = function;
					interpreter.returnNow = false;
					return;
				}
				case READY_TO_INVOKE:
				{
					// A Flag.Invokes primitive needed to invoke a function, but
					// it's not allowed to directly.  Instead, it set up the
					// argsBuffer and function for us to do the call.  The
					// original primitive does not have a frame built for it in
					// the event of reification.
					assert primitive.hasFlag(Invokes);

					final @Nullable L2Chunk savedChunk = interpreter.chunk;
					final int savedOffset = interpreter.offset;
					final AvailObject[] savedPointers = interpreter.pointers;
					final int[] savedInts = interpreter.integers;
					try
					{
						interpreter.invokeFunction(interpreter.function);
					}
					finally
					{
						interpreter.function = function;
						interpreter.chunk = savedChunk;
						interpreter.offset = savedOffset;
						interpreter.pointers = savedPointers;
						interpreter.integers = savedInts;
					}
					interpreter.returnNow = true;
					interpreter.returningFunction = function;
					return;
				}
				case CONTINUATION_CHANGED:
				{
					// Inline primitives are allowed to change the continuation,
					// although they can't reify the stack first.  Abort the
					// stack here, and resume the continuation.
					assert primitive.hasFlag(SwitchesContinuation);

					final A_Continuation newContinuation =
						interpreter.reifiedContinuation;
					final A_Function newFunction = interpreter.function;
					final @Nullable L2Chunk newChunk = interpreter.chunk;
					final int newOffset = interpreter.offset;
					final boolean newReturnNow = interpreter.returnNow;
					final @Nullable AvailObject newReturnValue =
						newReturnNow ? interpreter.latestResult() : null;
					throw interpreter.abandonStackThen(() ->
					{
						interpreter.reifiedContinuation = newContinuation;
						interpreter.function = newFunction;
						interpreter.chunk = newChunk;
						interpreter.offset = newOffset;
						interpreter.returnNow = newReturnNow;
						interpreter.latestResult(newReturnValue);
					});
				}
				case FIBER_SUSPENDED:
				{
					assert false : "CanInline primitive must not suspend fiber";
				}
			}
		}

		// The primitive can't be safely inlined, so reify the stack and try the
		// primitive.
		if (Interpreter.debugL2)
		{
			System.out.println("          reifying for " + primitive.name());
		}
		interpreter.skipReturnCheck = false;
		final L2Chunk savedChunk = stripNull(interpreter.chunk);
		final int savedOffset = interpreter.offset;
		final AvailObject[] savedPointers = interpreter.pointers;
		final int[] savedInts = interpreter.integers;

		throw interpreter.reifyThen(() ->
		{
			interpreter.chunk = savedChunk;
			interpreter.offset = savedOffset;
			interpreter.pointers = savedPointers;
			interpreter.integers = savedInts;
			interpreter.function = function;

			if (Interpreter.debugL2)
			{
				System.out.println(
					"          reified, now starting " + primitive.name());
			}
			final Result result = interpreter.attemptPrimitive(
				primitive,
				interpreter.argsBuffer,
				interpreter.skipReturnCheck);
			switch (result)
			{
				case SUCCESS:
				{
					interpreter.returnNow = true;
					interpreter.returningFunction = function;
					break;
				}
				case FAILURE:
				{
					// Continue in this frame where it left off, right after
					// the L2_TRY_PRIMITIVE instruction.
					assert !interpreter.returnNow;
					break;
				}
				case READY_TO_INVOKE:
				{
					assert false : "Invoking primitives should be inlineable";
					break;
				}
				case CONTINUATION_CHANGED:
				{
					// Inline and non-inline primitives are each allowed to
					// change the continuation.  The stack has already been
					// reified here, so just continue in whatever frame was set
					// up by the continuation.
					assert primitive.hasFlag(SwitchesContinuation);
					break;
				}
				case FIBER_SUSPENDED:
				{
					// Set the exitNow flag to ensure the interpreter will wind
					// down correctly.  It should be in a state where all frames
					// have been reified, so returnNow would be unnecessary.
					assert interpreter.exitNow;
					interpreter.returnNow = false;
					break;
				}
			}

		});
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
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
}
