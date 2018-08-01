/*
 * L2_SUBTRACT_INT_FROM_INT_MOD_32_BITS.java
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

import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.interpreter.levelTwo.L2OperandType;
import com.avail.interpreter.levelTwo.L2Operation;
import com.avail.interpreter.levelTwo.register.L2IntRegister;
import com.avail.optimizer.jvm.JVMTranslator;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static com.avail.interpreter.levelTwo.L2OperandType.READ_INT;
import static com.avail.interpreter.levelTwo.L2OperandType.WRITE_INT;
import static org.objectweb.asm.Opcodes.ISUB;

/**
 * Subtract the subtrahend from the minuend, converting the result to a signed
 * 32-bit int through signed truncation.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class L2_SUBTRACT_INT_MINUS_INT_MOD_32_BITS
extends L2Operation
{
	/**
	 * Construct an {@code L2_SUBTRACT_INT_MINUS_INT_MOD_32_BITS}.
	 */
	private L2_SUBTRACT_INT_MINUS_INT_MOD_32_BITS ()
	{
		super(
			READ_INT.is("minuend"),
			READ_INT.is("subtrahend"),
			WRITE_INT.is("difference"));
	}

	/**
	 * Initialize the sole instance.
	 */
	public static final L2_SUBTRACT_INT_MINUS_INT_MOD_32_BITS instance =
		new L2_SUBTRACT_INT_MINUS_INT_MOD_32_BITS();

	@Override
	public void toString (
		final L2Instruction instruction,
		final Set<L2OperandType> desiredTypes,
		final StringBuilder builder)
	{
		assert this == instruction.operation();
		final L2IntRegister minuendReg =
			instruction.readIntRegisterAt(0).register();
		final L2IntRegister subtrahendReg =
			instruction.readIntRegisterAt(1).register();
		final L2IntRegister differenceReg =
			instruction.writeIntRegisterAt(2).register();

		renderPreamble(instruction, builder);
		builder.append(' ');
		builder.append(differenceReg);
		builder.append(" ← ");
		builder.append(minuendReg);
		builder.append(" - ");
		builder.append(subtrahendReg);
	}

	@Override
	public void translateToJVM (
		final JVMTranslator translator,
		final MethodVisitor method,
		final L2Instruction instruction)
	{
		final L2IntRegister minuendReg =
			instruction.readIntRegisterAt(0).register();
		final L2IntRegister subtrahendReg =
			instruction.readIntRegisterAt(1).register();
		final L2IntRegister differenceReg =
			instruction.writeIntRegisterAt(2).register();

		// :: difference = minuend - subtrahend;
		translator.load(method, minuendReg);
		translator.load(method, subtrahendReg);
		method.visitInsn(ISUB);
		translator.store(method, differenceReg);
	}
}
