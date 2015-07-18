/**
 * SimpleInstruction.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.interpreter.jvm;

import java.io.DataOutput;
import java.io.IOException;
import java.util.List;

/**
 * {@code SimpleInstruction} represents all single byte instructions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
class SimpleInstruction
extends JavaInstruction
{
	/** The {@linkplain JavaBytecode bytecode}. */
	private final JavaBytecode bytecode;

	/**
	 * Answer the {@linkplain JavaBytecode bytecode}.
	 *
	 * @return The bytecode.
	 */
	final JavaBytecode bytecode ()
	{
		return bytecode;
	}

	@Override
	VerificationTypeInfo[] inputOperands ()
	{
		final JavaOperand[] input = bytecode.inputOperands();
		final VerificationTypeInfo[] operands =
			new VerificationTypeInfo[input.length];
		for (int i = 0, size = input.length; i < size; i++)
		{
			operands[i] = input[i].create();
		}
		return operands;
	}

	@Override
	VerificationTypeInfo[] outputOperands (
		final List<VerificationTypeInfo> operandStack)
	{
		final JavaOperand[] input = bytecode.outputOperands();
		final VerificationTypeInfo[] operands =
			new VerificationTypeInfo[input.length];
		for (int i = 0, size = input.length; i < size; i++)
		{
			operands[i] = input[i].create();
		}
		return operands;
	}

	@Override
	int size ()
	{
		return bytecode.minimumFormatSize();
	}

	@Override
	final boolean isLabel ()
	{
		return false;
	}

	@Override
	boolean isReturn ()
	{
		return bytecode.isReturn();
	}

	@Override
	void writeBytecodeTo (final DataOutput out) throws IOException
	{
		bytecode.writeTo(out);
	}

	@Override
	void writeImmediatesTo (final DataOutput out) throws IOException
	{
		// No immediate values, so do nothing.
	}

	@Override
	public String toString ()
	{
		// If the class is an exact match (and not a subclass), then don't emit
		// any trailing spaces.
		if (this.getClass().equals(SimpleInstruction.class))
		{
			return bytecode.mnemonic();
		}
		return String.format("%-15s", bytecode.mnemonic());
	}

	/**
	 * Construct a new {@link SimpleInstruction}.
	 *
	 * @param bytecode
	 *        The {@linkplain JavaBytecode bytecode}.
	 */
	SimpleInstruction (final JavaBytecode bytecode)
	{
		this.bytecode = bytecode;
	}
}
