/*
 * L2_JUMP_IF_UNBOX_INT.java
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
import com.avail.descriptor.numbers.A_Number;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.operand.L2PcOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.optimizer.L2ValueManifest;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.function.Consumer;

import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.FAILURE;
import static com.avail.interpreter.levelTwo.L2NamedOperandType.Purpose.SUCCESS;
import static com.avail.interpreter.levelTwo.L2OperandType.*;
import static org.objectweb.asm.Opcodes.IFEQ;

/**
 * Jump to {@code "if unboxed"} if an {@code int} was unboxed from an {@link
 * AvailObject}, otherwise jump to {@code "if not unboxed"}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_JUMP_IF_UNBOX_INT
extends L2ConditionalJump
{
	/**
	 * Construct an {@code L2_JUMP_IF_UNBOX_INT}.
	 */
	private L2_JUMP_IF_UNBOX_INT ()
	{
		super(
			READ_BOXED.is("source"),
			WRITE_INT.is("destination", SUCCESS),
			PC.is("if not unboxed", FAILURE),
			PC.is("if unboxed", SUCCESS));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_JUMP_IF_UNBOX_INT instance =
		new L2_JUMP_IF_UNBOX_INT();

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand source = instruction.operand(0);
		final L2WriteIntOperand destination = instruction.operand(1);
//		final L2PcOperand ifNotUnboxed = instruction.operand(2);
//		final L2PcOperand ifUnboxed = instruction.operand(3);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(destination.registerString());
		builder.append(" ←? ");
		builder.append(source.registerString());
		renderOperandsStartingAt(instruction, 2, desiredTypes, builder);
	}

	@Override
	public void instructionWasAdded (
		final L2Instruction instruction,
		final L2ValueManifest manifest)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand source = instruction.operand(0);
		final L2WriteIntOperand destination = instruction.operand(1);
		final L2PcOperand ifNotUnboxed = instruction.operand(2);
		final L2PcOperand ifUnboxed = instruction.operand(3);

		source.instructionWasAdded(manifest);
		ifNotUnboxed.instructionWasAdded(manifest);
		// Ensure the new write ends up in the same synonym as the source, along
		// the success edge.
		destination.instructionWasAddedForMove(
			source.semanticValue(), manifest);
		ifUnboxed.instructionWasAdded(manifest);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadBoxedOperand source = instruction.operand(0);
		final L2WriteIntOperand destination = instruction.operand(1);
		final L2PcOperand ifNotUnboxed = instruction.operand(2);
		final L2PcOperand ifUnboxed = instruction.operand(3);

		// :: if (!source.isInt()) goto ifNotUnboxed;
		translator.load(method, source.register());
		A_BasicObject.isIntMethod.generateCall(method);
		method.visitJumpInsn(IFEQ, translator.labelFor(ifNotUnboxed.offset()));
		// :: else {
		// ::    destination = source.extractInt();
		// ::    goto ifUnboxed;
		// :: }
		translator.load(method, source.register());
		A_Number.extractIntMethod.generateCall(method);
		translator.store(method, destination.register());
		translator.jump(method, instruction, ifUnboxed);
	}
}
