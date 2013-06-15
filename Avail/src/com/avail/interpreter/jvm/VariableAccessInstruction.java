/**
 * VariableAccessInstruction.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

/**
 * A {@code VariableAccessInstruction} has a one-byte immediate that represents
 * a local variable index.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class VariableAccessInstruction
extends SimpleInstruction
{
	/** The local variable index. */
	private final int index;

	/**
	 * Does the {@linkplain LoadInstruction instruction} require a 16-bit
	 * local variable index?
	 *
	 * @return {@code true} if the instruction requires a 16-bit local variable
	 *         index, {@code false} otherwise.
	 */
	private boolean isWide ()
	{
		return (index & 255) != index;
	}

	@Override
	final int size ()
	{
		return super.size() + (isWide() ? 2 : 0);
	}

	@Override
	final void writeBytecodeTo (final DataOutput out) throws IOException
	{
		if (isWide())
		{
			JavaBytecode.wide.writeTo(out);
		}
		super.writeBytecodeTo(out);
	}

	@Override
	final void writeImmediatesTo (final DataOutput out) throws IOException
	{
		if (isWide())
		{
			out.writeShort(index);
		}
		else
		{
			out.writeByte(index);
		}
	}

	@Override
	public final String toString ()
	{
		final String mnemonic = String.format(
			"%s%s",
			isWide() ? "wide " : "",
			bytecode().mnemonic());
		return String.format("%-15s#%d", mnemonic, index);
	}

	/**
	 * Construct a new {@link VariableAccessInstruction}.
	 *
	 * @param bytecode
	 *        The {@code JavaBytecode bytecode}.
	 * @param index
	 *        The local variable index.
	 */
	public VariableAccessInstruction (
		final JavaBytecode bytecode,
		final int index)
	{
		super(bytecode);
		this.index = index;
	}
}
