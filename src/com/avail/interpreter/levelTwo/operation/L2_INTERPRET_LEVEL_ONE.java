/*
 * L2_INTERPRET_LEVEL_ONE.java
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

import com.avail.descriptor.A_Continuation;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L1InstructionStepper;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.ON_RAMP;
import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.*;

/**
 * Use the {@link Interpreter#levelOneStepper} to execute the Level One
 * unoptimized nybblecodes.  If an interrupt request is indicated, throw a
 * {@link StackReifier}, making sure to synthesize a continuation for the
 * current frame.
 *
 * <p>Note that Avail calls are now executed as Java calls, causing this thread
 * to block until either it completes or a {@link StackReifier} is
 * thrown, which causes an {@link A_Continuation} to be built, allowing the
 * Avail frame to continue executing later.</p>
 *
 * <p>Single-stepping is currently not supported – perhaps a separate {@link
 * L2Operation} in a special {@link L2Chunk} would be an appropriate way to do
 * that.  Also, be careful not to saturate the interrupt request to the point
 * that no progress can be made.  Perhaps a solution to both concerns is to add
 * a one-step-delayed interrupt flag.  Querying the interrupt flag would cause
 * the delayed flag to be OR-ed into the current interrupt flag, returning its
 * previous value.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_INTERPRET_LEVEL_ONE
extends L2ControlFlowOperation
{
	/**
	 * Construct an {@code L2_INTERPRET_LEVEL_ONE}.
	 */
	private L2_INTERPRET_LEVEL_ONE ()
	{
		super(
			PC.is("call reentry point", ON_RAMP),
			PC.is("interrupt reentry point", ON_RAMP));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_INTERPRET_LEVEL_ONE instance =
		new L2_INTERPRET_LEVEL_ONE();

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Generator generator)
	{
		// No real optimization should ever be done near this wordcode.
		// Do nothing.
	}

	@Override
	public boolean hasSideEffect ()
	{
		// Keep this instruction from being removed, since it's only used
		// by the default chunk.
		return true;
	}

	@Override
	public boolean isEntryPoint (final L2Instruction instruction)
	{
		return true;
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
//		final int callReentryOffset = instruction.pcAt(0);
//		final int interruptReentryOffset = instruction.pcAt(1);

		// :: return interpreter.levelOneStepper.run();
		translator.loadInterpreter(method);
		method.visitFieldInsn(
			GETFIELD,
			getInternalName(Interpreter.class),
			"levelOneStepper",
			getDescriptor(L1InstructionStepper.class));
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(L1InstructionStepper.class),
			"run",
			getMethodDescriptor(getType(StackReifier.class)),
			false);
		method.visitInsn(ARETURN);
	}
}
