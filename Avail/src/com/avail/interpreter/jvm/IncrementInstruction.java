/**
 * IncrementInstruction.java
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
 * The immediate values of an {@code IncrementInstruction} are the {@linkplain
 * LocalVariable local variable} index and the constant delta.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class IncrementInstruction
extends SimpleInstruction
{
	/** The {@linkplain LocalVariable local variable}. */
	private final LocalVariable local;

	/** The constant delta. */
	private final int delta;

	/**
	 * Does the {@linkplain IncrementInstruction instruction} require 16-bit
	 * operands?
	 *
	 * @return {@code true} if the instruction requires 16-bit operands, {@code
	 *         false} otherwise.
	 */
	private boolean isWide ()
	{
		return local.isWide() || (delta & 255) != delta;
	}

	@Override
	int size ()
	{
		return super.size() + (isWide() ? 3 : 0);
	}

	@Override
	void writeBytecodeTo (final DataOutput out) throws IOException
	{
		if (isWide())
		{
			JavaBytecode.wide.writeTo(out);
		}
		super.writeBytecodeTo(out);
	}

	@Override
	void writeImmediatesTo (final DataOutput out) throws IOException
	{
		if (isWide())
		{
			out.writeShort(local.index);
			out.writeShort(delta);
		}
		else
		{
			out.writeByte(local.index);
			out.writeByte(delta);
		}
	}

	@Override
	public String toString ()
	{
		final String mnemonic = String.format(
			"%s%s",
			isWide() ? "wide " : "",
			bytecode().mnemonic());
		return String.format("%-15s#%s += %d", mnemonic, local, delta);
	}

	/**
	 * Construct a new {@link IncrementInstruction}.
	 *
	 * @param local
	 *        The {@linkplain LocalVariable local variable}.
	 * @param delta
	 *        The constant delta.
	 */
	public IncrementInstruction (final LocalVariable local, final int delta)
	{
		super(JavaBytecode.iinc);
		assert local.isLive();
		assert (short) delta == delta;
		this.local = local;
		this.delta = delta;
	}
}
