/**
 * L2_JUMP_IF_INTERRUPT.java
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
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.RegisterSet;
import com.avail.optimizer.StackReifier;
import com.avail.optimizer.jvm.JVMTranslator;
import com.avail.utility.evaluation.Transformer1NotNullArg;
import org.objectweb.asm.MethodVisitor;

import java.util.List;

import static com.avail.interpreter.levelTwo.L2OperandType.PC;
import static com.avail.optimizer.jvm.JVMCodeGenerationUtility.emitIntConstant;
import static com.avail.utility.Nulls.stripNull;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Type.*;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.getType;

/**
 * Jump to the specified level two program counter if no interrupt has been
 * requested since last serviced.  Otherwise an interrupt has been requested
 * and we should proceed to the next instruction.
 */
public class L2_JUMP_IF_INTERRUPT extends L2Operation
{
	/**
	 * Initialize the sole instance.
	 */
	public static final L2Operation instance =
		new L2_JUMP_IF_INTERRUPT().init(
			PC.is("if interrupt"),
			PC.is("if not interrupt"));

	@Override
	public Transformer1NotNullArg<Interpreter, StackReifier> actionFor (
		final L2Instruction instruction)
	{
		final int offsetIfInterrupt = instruction.pcOffsetAt(0);
		final int offsetIfNotInterrupt = instruction.pcOffsetAt(1);
		return interpreter ->
		{
			interpreter.offset(interpreter.isInterruptRequested()
				? offsetIfInterrupt
				: offsetIfNotInterrupt);
			return null;
		};
	}

	@Override
	protected void propagateTypes (
		final L2Instruction instruction,
		final List<RegisterSet> registerSets,
		final L2Translator translator)
	{
		// If there's an interrupt then fall through, otherwise jump as
		// indicated.  Neither transition directly affects registers, although
		// the instruction sequence that deals with an interrupt may do plenty.
		assert registerSets.size() == 2;
	}

	@Override
	public boolean hasSideEffect ()
	{
		// It jumps, which counts as a side effect.  It also dynamically tests
		// for an interrupt request, which doesn't commute well with other
		// instructions.
		return true;
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final int offsetIfInterrupt = instruction.pcOffsetAt(0);
		final int offsetIfNotInterrupt = instruction.pcOffsetAt(1);

		method.visitVarInsn(ALOAD, translator.interpreterLocal());
		method.visitMethodInsn(
			INVOKEVIRTUAL,
			getInternalName(Interpreter.class),
			"isInterruptRequested",
			getMethodDescriptor(BOOLEAN_TYPE),
			false);
		method.visitJumpInsn(
			IFNE,
			stripNull(translator.instructionLabels)[offsetIfInterrupt]);
		method.visitJumpInsn(
			GOTO,
			stripNull(translator.instructionLabels)[offsetIfNotInterrupt]);
	}
}
