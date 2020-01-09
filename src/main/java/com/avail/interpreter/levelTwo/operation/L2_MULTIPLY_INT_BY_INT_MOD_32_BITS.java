/*
 * L2_MULTIPLY_INT_BY_INT_MOD_32_BITS.java
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

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.operand.L2ReadIntOperand;
import com.avail.interpreter.levelTwo.operand.L2WriteIntOperand;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;
import java.util.function.Consumer;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_INT;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_INT;
import static org.objectweb.asm.Opcodes.IMUL;


/**
 * Multiply the value in one int register by the value in another int register,
 * truncating it to the low 32 bits and storing it back in the second register.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_MULTIPLY_INT_BY_INT_MOD_32_BITS
extends L2Operation
{
	/**
	 * Construct an {@code L2_MULTIPLY_INT_BY_INT_MOD_32_BITS}.
	 */
	private L2_MULTIPLY_INT_BY_INT_MOD_32_BITS ()
	{
		super(
			READ_INT.is("multiplicand"),
			READ_INT.is("multiplier"),
			WRITE_INT.is("product"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_MULTIPLY_INT_BY_INT_MOD_32_BITS instance =
		new L2_MULTIPLY_INT_BY_INT_MOD_32_BITS();

	@Override
	public void appendToWithWarnings (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder,
		final Consumer<Boolean> warningStyleChange)
	{
		assert this == instruction.operation();
		final L2ReadIntOperand multiplicand = instruction.operand(0);
		final L2ReadIntOperand multiplier = instruction.operand(1);
		final L2WriteIntOperand product = instruction.operand(2);

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(product.registerString());
		builder.append(" ← ");
		builder.append(multiplicand.registerString());
		builder.append(" × ");
		builder.append(multiplier.registerString());
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2ReadIntOperand multiplicand = instruction.operand(0);
		final L2ReadIntOperand multiplier = instruction.operand(1);
		final L2WriteIntOperand product = instruction.operand(2);

		// :: product = multiplicand * multiplier;
		translator.load(method, multiplicand.register());
		translator.load(method, multiplier.register());
		method.visitInsn(IMUL);
		translator.store(method, product.register());
	}
}
