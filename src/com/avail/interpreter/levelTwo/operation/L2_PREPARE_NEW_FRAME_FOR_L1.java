/*
 * L2_PREPARE_NEW_FRAME_FOR_L1.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.Primitive.Flag;
import com.avail.interpreter.levelTwo.L1InstructionStepper;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.performance.Statistic;
import com.avail.performance.StatisticReport;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import javax.annotation.Nullable;

import static com.avail.descriptor.ContinuationDescriptor
	.createContinuationWithFrame;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.VariableDescriptor.newVariableWithOuterType;
import static com.avail.interpreter.levelTwo.L2Chunk.ChunkEntryPoint.TO_RESUME;
import static com.avail.interpreter.levelTwo.L2Chunk.unoptimizedChunk;
import static com.avail.utility.Nulls.stripNull;
import static java.util.Arrays.asList;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

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
 * <p>Also check for interrupts after all that, reifying and suspending the
 * fiber if needed.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_PREPARE_NEW_FRAME_FOR_L1
extends L2Operation
{
	/**
	 * Construct an {@code L2_PREPARE_NEW_FRAME_FOR_L1}.
	 */
	private L2_PREPARE_NEW_FRAME_FOR_L1 ()
	{
		// Prevent accidental construction due to code cloning.
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_PREPARE_NEW_FRAME_FOR_L1 instance =
		new L2_PREPARE_NEW_FRAME_FOR_L1();

	/** {@link Statistic} for reifying in L1 interrupt-handler preamble. */
	private static final Statistic reificationForInterruptInL1Stat =
		new Statistic(
			"Reification for interrupt in L1 preamble",
			StatisticReport.REIFICATIONS);

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final RegisterSet registerSet,
		final L2Generator translator)
	{
		// No real optimization should ever be done near this L2 instruction.
		// Do nothing.
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Keep this instruction from being removed, since it's only used
		// by the default chunk.
		return true;
	}

	/**
	 * Prepare a new frame for L1 interpretation.
	 *
	 * @param interpreter
	 *        The {@link Interpreter}.
	 * @return A {@link StackReifier}, if any.
	 */
	@ReferencedInGeneratedCode
	public static @Nullable StackReifier prepare (final Interpreter interpreter)
	{
		assert !interpreter.exitNow;
		final A_Function function = stripNull(interpreter.function);
		final A_RawFunction code = function.code();
		final int numArgs = code.numArgs();
		final int numLocals = code.numLocals();
		final int numSlots = code.numSlots();
		// The L2 instructions that implement L1 don't reserve room for any
		// fixed registers, but they assume [0] is unused (to simplify
		// indexing).  I.e., pointers[1] <-> continuation.stackAt(1).
		final L1InstructionStepper stepper = interpreter.levelOneStepper;
		stepper.pointers = new AvailObject[numSlots + 1];
		int dest = 1;
		// Populate the arguments from argsBuffer.
		for (final AvailObject arg : interpreter.argsBuffer)
		{
			stepper.pointerAtPut(dest++, arg);
		}
		// Create actual local variables.
		for (int i = 1; i <= numLocals; i++)
		{
			stepper.pointerAtPut(
				dest++, newVariableWithOuterType(code.localTypeAt(i)));
		}
		// Write nil into the remaining stack slots.  These values should not
		// encounter any kind of ordinary use, but they must still be
		// transferred into a continuation during reification.  Therefore, don't
		// use Java nulls here.
		while (dest <= numSlots)
		{
			stepper.pointerAtPut(dest++, nil);
		}
		code.setUpInstructionDecoder(stepper.instructionDecoder);
		stepper.instructionDecoder.pc(1);
		stepper.stackp = numSlots + 1;
		final @Nullable Primitive primitive = code.primitive();
		if (primitive != null)
		{
			// A failed primitive.  The failure value was captured in the
			// latestResult().
			assert !primitive.hasFlag(Flag.CannotFail);
			final A_BasicObject primitiveFailureValue =
				interpreter.latestResult();
			final A_Variable primitiveFailureVariable =
				stepper.pointerAt(numArgs + 1);
			primitiveFailureVariable.setValue(primitiveFailureValue);
		}

		if (interpreter.isInterruptRequested())
		{
			// Build an interrupted continuation, reify the rest of the stack,
			// and push the continuation onto the reified stack.  Then process
			// the interrupt, which may or may not suspend the fiber.
			final A_Continuation continuation =
				createContinuationWithFrame(
					function,
					nil,
					1,  // start of function
					numSlots + 1,   // empty stack
					unoptimizedChunk,
					TO_RESUME.offsetInDefaultChunk,
					asList(stepper.pointers),
					1);
			return interpreter.reifyThen(
				reificationForInterruptInL1Stat,
				() ->
				{
					// Push the continuation from above onto the reified stack.
					interpreter.returnNow = false;
					interpreter.reifiedContinuation =
						continuation.replacingCaller(
							stripNull(interpreter.reifiedContinuation));
					interpreter.processInterrupt(
						interpreter.reifiedContinuation);
				});
		}
		return null;
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		// :: reifier = L2_PREPARE_NEW_FRAME_FOR_L1.prepare(interpreter);
		translator.loadInterpreter(method);
		method.visitMethodInsn(
			INVOKESTATIC,
			getInternalName(L2_PREPARE_NEW_FRAME_FOR_L1.class),
			"prepare",
			getMethodDescriptor(
				getType(StackReifier.class),
				getType(Interpreter.class)),
			false);
		method.visitInsn(DUP);
		method.visitVarInsn(ASTORE, translator.reifierLocal());
		// :: if (reifier == null) goto noReification;
		final Label noReification = new Label();
		method.visitJumpInsn(IFNULL, noReification);
		// :: else return reifier;
		method.visitVarInsn(ALOAD, translator.reifierLocal());
		method.visitInsn(ARETURN);
		method.visitLabel(noReification);
	}
}
