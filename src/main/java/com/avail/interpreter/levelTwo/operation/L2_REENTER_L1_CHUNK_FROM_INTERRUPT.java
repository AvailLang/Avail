/*
 * L2_REENTER_L1_CHUNK_FROM_INTERRUPT.java
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.functions.A_Continuation;
import com.avail.descriptor.functions.A_Function;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L1InstructionStepper;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.jvm.CheckedMethod;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import org.objectweb.asm.MethodVisitor;

import java.util.logging.Level;

import static com.avail.interpreter.Interpreter.debugL1;
import static com.avail.optimizer.jvm.CheckedMethod.staticMethod;
import static com.avail.utility.Nulls.stripNull;

/**
 * This is the first instruction of the L1 interpreter's on-ramp for resuming
 * after an interrupt.  The reified {@link A_Continuation} that was captured
 * (and is now being resumed) pointed to this {@link L2Instruction}.  That
 * continuation is current in the {@link Interpreter#getReifiedContinuation}.
 * Pop it from that continuation chain, create suitable pointer and integer
 * registers as expected by {@link L2_INTERPRET_LEVEL_ONE}, then explode the
 * continuation's slots into those registers.  The {@link Interpreter#function}
 * should also have already been set up to agree with the continuation's
 * function.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_REENTER_L1_CHUNK_FROM_INTERRUPT
extends L2Operation
{
	/**
	 * Construct an {@code L2_REENTER_L1_CHUNK_FROM_INTERRUPT}.
	 */
	private L2_REENTER_L1_CHUNK_FROM_INTERRUPT ()
	{
		// Prevent accidental construction due to code cloning.
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_REENTER_L1_CHUNK_FROM_INTERRUPT instance =
		new L2_REENTER_L1_CHUNK_FROM_INTERRUPT();

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}

	@Override
	public boolean isEntryPoint (final L2Instruction instruction)
	{
		return true;
	}

	/**
	 * Reenter from an interrupt.
	 *
	 * @param interpreter
	 *        The {@link Interpreter}.
	 */
	@SuppressWarnings("unused")
	@ReferencedInGeneratedCode
	public static void reenter (final Interpreter interpreter)
	{
		final A_Continuation continuation =
			stripNull(interpreter.getReifiedContinuation());
		interpreter.setReifiedContinuation(continuation.caller());
		if (debugL1)
		{
			Interpreter.log(
				Interpreter.loggerDebugL1,
				Level.FINER,
				"{0}Reenter L1 from interrupt",
				interpreter.debugModeString);
		}

		final A_Function function = stripNull(interpreter.function);
		assert function == continuation.function();
		final int numSlots = continuation.numSlots();
		// Should agree with L2_PREPARE_NEW_FRAME_FOR_L1.
		final L1InstructionStepper stepper = interpreter.levelOneStepper;
		stepper.pointers = new AvailObject[numSlots + 1];
		int dest = 1;
		for (int i = 1; i <= numSlots; i++)
		{
			stepper.pointerAtPut(dest++, continuation.stackAt(i));
		}
		function.code().setUpInstructionDecoder(stepper.instructionDecoder);
		stepper.instructionDecoder.pc(continuation.pc());
		stepper.stackp = continuation.stackp();
	}

	/** The {@link CheckedMethod} for {@link #reenter(Interpreter)}. */
	private static final CheckedMethod reenterMethod = staticMethod(
		L2_REENTER_L1_CHUNK_FROM_INTERRUPT.class,
		"reenter",
		void.class,
		Interpreter.class);

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		// :: L2_REENTER_L1_CHUNK_FROM_INTERRUPT.reenter();
		translator.loadInterpreter(method);
		reenterMethod.generateCall(method);
	}
}
