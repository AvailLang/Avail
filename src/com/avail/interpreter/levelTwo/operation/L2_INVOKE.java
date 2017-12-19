/**
 * L2_INVOKE.java
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

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Continuation;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadPointerOperand;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.utility.evaluation.Transformer1NotNullArg;

import javax.annotation.Nullable;
import java.util.List;
import java.util.logging.Level;

import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static com.avail.utility.Nulls.stripNull;

/**
 * The given function is invoked.  The function may be a primitive, and the
 * primitive may succeed, fail, or replace the current continuation (after
 * reifying the stack).  It may also trigger reification of this frame by
 * Java-returning a {@link StackReifier} instead of null.
 *
 * <p>An immediate, if true ({@code 1}), indicates that the VM can elide the
 * check of the return type (because it has already been proven for this
 * circumstance).</p>
 */
public class L2_INVOKE extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_INVOKE().init(
			READ_POINTER.is("called function"),
			READ_VECTOR.is("arguments"),
			IMMEDIATE.is("skip return check"),
			PC.is("on return"),
			PC.is("on reification"));

	@Override
	public Transformer1NotNullArg<Interpreter, StackReifier> actionFor (
		final L2Instruction instruction)
	{
		final int functionRegNumber =
			instruction.readObjectRegisterAt(0).finalIndex();
		final List<L2ReadPointerOperand> argumentRegs =
			instruction.readVectorRegisterAt(1);
		final boolean skipReturnCheck = instruction.immediateAt(2) != 0;
		final int onNormalReturn = instruction.pcOffsetAt(3);
		final int onReification = instruction.pcOffsetAt(4);

		// Pre-decode the argument registers as much as possible.
		final int[] argumentRegNumbers = argumentRegs.stream()
			.mapToInt(L2ReadPointerOperand::finalIndex)
			.toArray();

		return interpreter ->
		{
			final A_Function savedFunction = stripNull(interpreter.function);
			final L2Chunk savedChunk = stripNull(interpreter.chunk);
			final int savedOffset = interpreter.offset;
			final AvailObject[] savedPointers = interpreter.pointers;
			final int[] savedInts = interpreter.integers;
			assert savedChunk.instructions[savedOffset - 1] == instruction;

			final A_Function calledFunction =
				interpreter.pointerAt(functionRegNumber);
			interpreter.argsBuffer.clear();
			for (final int argumentRegNumber : argumentRegNumbers)
			{
				interpreter.argsBuffer.add(
					interpreter.pointerAt(argumentRegNumber));
			}

			interpreter.function = calledFunction;
			interpreter.chunk = calledFunction.code().startingChunk();
			interpreter.offset = 0;
			interpreter.skipReturnCheck = skipReturnCheck;
			// Safety
			interpreter.pointers = Interpreter.emptyPointersArray;
			interpreter.integers = Interpreter.emptyIntArray;
			final @Nullable StackReifier reifier = interpreter.runChunk();
			try
			{
				if (reifier != null)
				{
					if (reifier.actuallyReify())
					{
						// We were somewhere inside the callee and received a
						// reification signal.  Run the steps at the reification
						// off-ramp to produce this continuation, ending at an
						// L2_Return.  None of the intervening instructions may
						// cause reification or Avail function invocation.  The
						// resulting continuation is "returned" by the L2_Return
						// instruction.  That continuation should know how to be
						// reentered when the callee returns.
						interpreter.function = savedFunction;
						interpreter.chunk = savedChunk;
						interpreter.offset = onReification;
						interpreter.pointers = savedPointers;
						interpreter.integers = savedInts;
						interpreter.returnNow = false;
						assert !interpreter.exitNow;
						final String oldModeString =
							interpreter.debugModeString;
						if (Interpreter.debugL2)
						{
							interpreter.debugModeString += "REIFY L2 ";
						}
						final @Nullable StackReifier reifier2 =
							interpreter.runChunk();
						assert reifier2 == null
							: "Off-ramp must not cause reification!";
						// The off-ramp "returned" the callerless continuation
						// that captures this frame.
						final A_Continuation continuation =
							interpreter.latestResult();
						if (Interpreter.debugL2)
						{
							Interpreter.log(
								Interpreter.loggerDebugL2,
								Level.FINER,
								"{0}Push reified continuation "
									+ "for L2_INVOKE: {1}",
								interpreter.debugModeString,
								continuation.function().code().methodName());
						}
						reifier.pushContinuation(continuation);
						interpreter.debugModeString = oldModeString;
					}
					return reifier;
				}
			}
			finally
			{
				interpreter.function = savedFunction;
				interpreter.chunk = savedChunk;
				interpreter.offset = Integer.MAX_VALUE;
				interpreter.pointers = savedPointers;
				interpreter.integers = savedInts;
				interpreter.returnNow = false;
				assert !interpreter.exitNow;
			}
			// The invocation returned normally.
			interpreter.offset = onNormalReturn;
			return null;
		};
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		// Successful return or a reification off-ramp.
		assert registerSets.size() == 2;
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Never remove invocations -- but inlining might make them go away.
		return true;
	}

	@Override
	public String debugNameIn (
		final L2Instruction instruction)
	{
		final L2ReadPointerOperand functionReg =
			instruction.readObjectRegisterAt(0);
		final @Nullable A_BasicObject exactFunction =
			functionReg.constantOrNull();
		if (exactFunction == null)
		{
			return name() + "(function unknown)";
		}
		return name()
			+ ": "
			+ ((A_Function) exactFunction).code().methodName().asNativeString();
	}
}
