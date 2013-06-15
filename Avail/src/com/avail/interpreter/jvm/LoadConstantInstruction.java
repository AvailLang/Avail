/**
 * LoadConstantInstruction.java
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
import com.avail.interpreter.jvm.ConstantPool.Entry;

/**
 * The immediate value of a {@code LoadConstantInstruction} is an index of a
 * {@linkplain ConstantPool constant pool} {@linkplain Entry entry}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class LoadConstantInstruction
extends JavaInstruction
{
	/** A {@linkplain ConstantPool constant pool} {@linkplain Entry entry}. */
	private final Entry entry;

	@Override
	boolean isLabel ()
	{
		return false;
	}

	/**
	 * Does the {@linkplain Entry entry} consume two indices in the {@linkplain
	 * ConstantPool constant pool}?
	 *
	 * @return {@code true} if the entry consumes two indices in the constant
	 *         pool, {@code false} if the entry consumes one index.
	 */
	private boolean usesWideEntry ()
	{
		return entry.isWide();
	}

	/**
	 * Does the {@linkplain Entry entry}'s index require 16-bits of
	 * representation?
	 *
	 * @return {@code true} if the entry's index requires 16-bits of
	 *         representation, {@code false} otherwise.
	 */
	private boolean usesWideIndex ()
	{
		return (entry.index & 255) != entry.index;
	}

	@Override
	int size ()
	{
		return usesWideIndex() ? 3 : 2;
	}

	/**
	 * Answer the appropriate {@linkplain JavaBytecode bytecode} for this
	 * instruction based on the {@linkplain #usesWideIndex() wideness} of the
	 * {@linkplain Entry entry}'s index.
	 *
	 * @return The appropriate bytecode.
	 */
	private JavaBytecode bytecode ()
	{
		return
			usesWideEntry() ? JavaBytecode.ldc2_w
			: usesWideIndex() ? JavaBytecode.ldc_w
			: JavaBytecode.ldc;
	}

	@Override
	void writeBytecodeTo (final DataOutput out) throws IOException
	{
		bytecode().writeTo(out);
	}

	@Override
	void writeImmediatesTo (final DataOutput out) throws IOException
	{
		if (usesWideIndex())
		{
			entry.writeIndexTo(out);
		}
		else
		{
			entry.writeSingleByteIndexTo(out);
		}
	}

	@Override
	public String toString ()
	{
		return String.format("%15s%s", bytecode().mnemonic(), entry);
	}

	/**
	 * Construct a new {@link LoadConstantInstruction}.
	 *
	 * @param entry
	 *        A {@linkplain ConstantPool constant pool} {@linkplain Entry
	 *        entry}.
	 */
	public LoadConstantInstruction (final Entry entry)
	{
		this.entry = entry;
	}
}
