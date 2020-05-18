/*
 * L2_GET_OBJECT_FIELD.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ConstantOperand;
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteBoxedOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.function.Consumer;

import static com.avail.descriptor.representation.AvailObject.fieldAtMethod;
import static com.avail.interpreter.levelTwo.L2OperandType.*;

/**
 * Extract the specified field of the object.
 *
 * TODO - Eventually we should generate code to collect stats on which variants
 * occur, then at reoptimization time inline tests for the likely ones, and use
 * the field indices directly for those variants.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class L2_GET_OBJECT_FIELD
extends L2Operation
{
	/**
	 * Construct an {@code L2_CREATE_OBJECT}.
	 */
	private L2_GET_OBJECT_FIELD ()
	{
		super(
			READ_BOXED.is("object"),
			CONSTANT.is("field atom"),
			WRITE_BOXED.is("field value"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_GET_OBJECT_FIELD instance = new L2_GET_OBJECT_FIELD();

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<? extends L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand objectRead = instruction.operand(0);
		final L2ConstantOperand fieldAtom = instruction.operand(1);
		final L2WriteBoxedOperand fieldValue = instruction.operand(2);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(fieldValue.registerString());
		builder.append(" ← ");
		builder.append(objectRead);
		builder.append("[");
		builder.append(fieldAtom);
		builder.append("]");
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		assert this == instruction.operation();
		final L2ReadBoxedOperand objectRead = instruction.operand(0);
		final L2ConstantOperand fieldAtom = instruction.operand(1);
		final L2WriteBoxedOperand fieldValue = instruction.operand(2);

		translator.load(method, objectRead.register());
		translator.literal(method, fieldAtom.object);
		fieldAtMethod.generateCall(method);
		translator.store(method, fieldValue.register());
	}
}
