/**
 * LocalVariableAccessInstruction.java
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
 * A {@code LocalVariableAccessInstruction} represents accessing a local
 * variable. It may be {@linkplain JavaBytecode#wide}, in which case its
 * immediate is a 2-byte {@linkplain LocalVariable local variable} index rather
 * than a 1-byte local variable index.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class LocalVariableAccessInstruction
extends JavaInstruction
{
	/** The {@linkplain LocalVariable local variable}. */
	final LocalVariable local;

	@Override
	final int size ()
	{
		return local.isWide() ? 3 : 1;
	}

	/**
	 * Answer the table of possible {@linkplain JavaBytecode bytecodes} that
	 * may encode this {@linkplain LocalVariableAccessInstruction instruction}.
	 *
	 * @return The candidate bytecodes, arranged by {@linkplain Class type} and
	 *         {@linkplain LocalVariable local variable} index.
	 */
	abstract JavaBytecode[] bytecodes ();

	/**
	 * Answer the appropriate {@linkplain JavaBytecode bytecode} for this
	 * {@linkplain LocalVariableAccessInstruction instruction}.
	 *
	 * @return The appropriate bytecode.
	 */
	final JavaBytecode bytecode ()
	{
		final String descriptor = local.descriptor();
		final Class<?> type = JavaDescriptors.typeForDescriptor(descriptor);
		final int rowIndex = type == Object.class ? 0
			: type == Float.TYPE ? 1
			: type == Double.TYPE ? 2
			: type == Boolean.TYPE ? 3
			: type == Byte.TYPE ? 3
			: type == Character.TYPE ? 3
			: type == Integer.TYPE ? 3
			: type == Short.TYPE ? 3
			: type == Long.TYPE ? 4
			: -1;
		assert rowIndex != -1;
		final int columnIndex = Math.min(local.index, 4);
		return bytecodes()[5 * rowIndex + columnIndex];
	}

	@Override
	final JavaOperand[] inputOperands ()
	{
		return bytecode().inputOperands();
	}

	@Override
	final JavaOperand[] outputOperands (final List<JavaOperand> operandStack)
	{
		return bytecode().outputOperands();
	}

	@Override
	final void writeBytecodeTo (final DataOutput out) throws IOException
	{
		if (local.isWide())
		{
			JavaBytecode.wide.writeTo(out);
		}
		bytecode().writeTo(out);
	}

	@Override
	final void writeImmediatesTo (final DataOutput out) throws IOException
	{
		if (local.index > 3)
		{
			local.writeTo(out);
		}
	}

	@Override
	public final String toString ()
	{
		final String mnemonic = String.format(
			"%s%s",
			local.isWide() ? "wide " : "",
			bytecode().mnemonic());
		return String.format("%-15s%s", mnemonic, local);
	}

	/**
	 * Construct a new {@link LocalVariableAccessInstruction}.
	 *
	 * @param local
	 *        The {@linkplain LocalVariable local variable}.
	 */
	public LocalVariableAccessInstruction (final LocalVariable local)
	{
		assert local.isLive();
		this.local = local;
	}
}
