/*
 * L2_NEGATE_INT_NO_CHECK.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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
import com.avail.interpreter.levelTwo.operand.L2IntImmediateOperand;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_INT;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_INT;
import static org.objectweb.asm.Opcodes.INEG;

/**
 * Extract an {@code int} from the specified register and negate it. The result
 * must also be an {@code int}; the result is not checked for overflow. If
 * overflow detection is required, then use {@link
 * L2_SUBTRACT_INT_MINUS_INT_CONSTANT} with an {@linkplain L2IntImmediateOperand
 * immediate} {@code 0} instead.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_NEGATE_INT_NO_CHECK
extends L2Operation
{
	/**
	 * Construct an {@code L2_NEGATE_INT_NO_CHECK}.
	 */
	private L2_NEGATE_INT_NO_CHECK ()
	{
		super(
			READ_INT.is("value"),
			WRITE_INT.is("negation"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_NEGATE_INT_NO_CHECK instance =
		new L2_NEGATE_INT_NO_CHECK();

	@Override
	public boolean hasSideEffect ()
	{
		return true;
	}

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final String valueReg =
			instruction.readIntRegisterAt(0).registerString();
		final String negationReg =
			instruction.writeIntRegisterAt(1).registerString();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(negationReg);
		builder.append(" ← -");
		builder.append(valueReg);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2IntRegister valueReg =
			instruction.readIntRegisterAt(0).register();
		final L2IntRegister negationReg =
			instruction.writeIntRegisterAt(1).register();

		// :: negationReg = -valueReg;
		translator.load(method, valueReg);
		method.visitInsn(INEG);
		translator.store(method, negationReg);
	}
}
