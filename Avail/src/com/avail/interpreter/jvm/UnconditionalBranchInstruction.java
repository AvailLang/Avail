/**
 * UnconditionalBranchInstruction.java
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
 * A {@code UnconditionalBranchInstruction} abstractly specifies an
 * unconditional branch.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class UnconditionalBranchInstruction
extends JavaInstruction
{
	/** The {@linkplain Label branch target}. */
	private final Label label;

	@Override
	final boolean isLabel ()
	{
		return false;
	}

	/**
	 * Is the {@linkplain GotoInstruction branch} wide?
	 *
	 * @return {@code true} if the branch is wide, {@code false} otherwise.
	 */
	final boolean isWide ()
	{
		// If either the instruction or the label do not yet have a valid
		// address, then conservatively assume that the instruction is not wide.
		if (!hasValidAddress() || !label.hasValidAddress())
		{
			return false;
		}
		final long offset = label.address() - address();
		return (offset < Short.MIN_VALUE || offset > Short.MAX_VALUE)
			? true
			: false;
	}

	@Override
	final int size ()
	{
		return isWide() ? 5 : 3;
	}

	/**
	 * Answer the appropriate {@linkplain JavaBytecode bytecode} for this
	 * {@linkplain UnconditionalBranchInstruction branch instruction}.
	 *
	 * @return The bytecode.
	 */
	abstract JavaBytecode bytecode ();

	@Override
	final void writeBytecodeTo (final DataOutput out) throws IOException
	{
		bytecode().writeTo(out);
	}

	@Override
	final void writeImmediatesTo (final DataOutput out) throws IOException
	{
		if (isWide())
		{
			out.writeInt((int) address());
		}
		else
		{
			out.writeShort((short) address());
		}
	}

	/**
	 * The mnemonic to use when the instruction or its {@linkplain
	 * Label label} have not yet been assigned an address.
	 *
	 * @return The mnemonic.
	 */
	abstract String mnemonicForInvalidAddress ();

	@Override
	public final String toString ()
	{
		if (hasValidAddress() && label.hasValidAddress())
		{
			return String.format("%-15s%s", bytecode().mnemonic(), label);
		}
		return String.format("%-15s%s", mnemonicForInvalidAddress(), label);
	}

	/**
	 * Construct a new {@link UnconditionalBranchInstruction}.
	 *
	 * @param label
	 *        The {@linkplain Label branch target}.
	 */
	UnconditionalBranchInstruction (final Label label)
	{
		this.label = label;
	}
}
