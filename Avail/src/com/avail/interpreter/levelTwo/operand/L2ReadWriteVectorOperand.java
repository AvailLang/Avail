/**
 * L2ReadWriteVectorOperand.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.levelTwo.operand;

import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;
import com.avail.utility.*;


/**
 * An {@code L2ReadWriteVectorOperand} is an operand of type {@link
 * L2OperandType#READWRITE_VECTOR}.  It holds an {@link L2RegisterVector}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class L2ReadWriteVectorOperand extends L2Operand
{
	/**
	 * The actual {@link L2RegisterVector}.
	 */
	public final @NotNull L2RegisterVector vector;

	/**
	 * Construct a new {@link L2ReadWriteVectorOperand} with the specified
	 * {@link L2RegisterVector}.
	 *
	 * @param vector The register vector.
	 */
	public L2ReadWriteVectorOperand (
		final @NotNull L2RegisterVector vector)
	{
		this.vector = vector;
	}

	@Override
	public L2OperandType operandType ()
	{
		return L2OperandType.READWRITE_VECTOR;
	}

	@Override
	public void dispatchOperand (final L2OperandDispatcher dispatcher)
	{
		dispatcher.doOperand(this);
	}

	@Override
	public L2ReadWriteVectorOperand transformRegisters (
		final @NotNull Transformer1<L2Register, L2Register> transformer)
	{
		final List<L2ObjectRegister> newRegisters =
			new ArrayList<L2ObjectRegister>(vector.registers().size());
		for (final L2ObjectRegister register : vector.registers())
		{
			final L2ObjectRegister newRegister =
				(L2ObjectRegister)transformer.value(register);
			newRegisters.add(newRegister);
		}
		final L2RegisterVector newVector = new L2RegisterVector(newRegisters);
		return new L2ReadWriteVectorOperand(newVector);
	}

	@Override
	public void emitOn (
		final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitVector(vector);
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("ReadWriteVector(");
		boolean first = true;
		for (final L2Register register : vector.registers())
		{
			if (!first)
			{
				builder.append(",");
			}
			builder.append(register);
			first = false;
		}
		builder.append(")");
		return builder.toString();
	}
}
