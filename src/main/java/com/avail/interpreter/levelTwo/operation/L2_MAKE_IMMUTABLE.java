/*
 * L2_MAKE_IMMUTABLE.java
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

import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.L2Generator;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_BOXED;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_BOXED;
import static com.avail.utility.Casts.cast;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Type.*;

/**
 * Force the specified object to be immutable.  Maintenance of conservative
 * sticky-bit reference counts is mostly separated out into this operation to
 * allow code transformations to obviate the need for it in certain non-obvious
 * circumstances.
 *
 * <p>To keep this instruction from being neither removed due to not having
 * side-effect, nor kept from being re-ordered due to having side-effect, the
 * instruction has an input and an output, the latter of which should be the
 * only way to use the value after this instruction.  Accidentally using the
 * input value again would be incorrect, since that use could be re-ordered to a
 * point before this instruction.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_MAKE_IMMUTABLE
extends L2Operation
{
	/**
	 * Construct an {@code L2_MAKE_IMMUTABLE}.
	 */
	private L2_MAKE_IMMUTABLE ()
	{
		super(
			READ_BOXED.is("input"),
			WRITE_BOXED.is("output"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_MAKE_IMMUTABLE instance = new L2_MAKE_IMMUTABLE();

	/**
	 * Given an {@link L2Instruction} using this operation, extract the source
	 * {@link L2ReadBoxedOperand} that is made immutable by the instruction.
	 *
	 * @param instruction
	 *        The make-immutable instruction to examine.
	 * @return The instruction's source {@link L2ReadBoxedOperand}.
	 */
	public static L2ReadBoxedOperand sourceOfImmutable (
		final L2Instruction instruction)
	{
		assert instruction.operation() instanceof L2_MAKE_IMMUTABLE;
		return cast(instruction.operand(0));
	}

	@Override
	public L2ReadBoxedOperand extractFunctionOuter (
		final L2Instruction instruction,
		final L2ReadBoxedOperand functionRegister,
		final int outerIndex,
		final A_Type outerType,
		final L2Generator generator)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand read = instruction.operand(0);
//		final L2WriteBoxedOperand write = instruction.operand(1);

		// Trace it back toward the actual function creation. We don't care if
		// the function is still mutable, since the generated JVM code will make
		// the outer variable immutable.
		final L2Instruction earlierInstruction =
			read.definitionSkippingMoves(true);
		return earlierInstruction.operation().extractFunctionOuter(
			earlierInstruction,
			functionRegister,
			outerIndex,
			outerType,
			generator);
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand read = instruction.operand(0);
		final L2WriteBoxedOperand write = instruction.operand(1);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(write.registerString());
		builder.append(" ← ");
		builder.append(read.registerString());
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand read = instruction.operand(0);
		final L2WriteBoxedOperand write = instruction.operand(1);

		// :: output = input.makeImmutable();
		translator.load(method, read.register());
		method.visitMethodInsn(
			INVOKEINTERFACE,
			getInternalName(A_BasicObject.class),
			"makeImmutable",
			getMethodDescriptor(getType(AvailObject.class)),
			true);
		translator.store(method, write.register());
	}
}
