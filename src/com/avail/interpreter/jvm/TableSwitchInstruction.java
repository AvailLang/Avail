/**
 * TableSwitchInstruction.java
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
import java.util.Formatter;
import java.util.List;

/**
 * The immediate values of a {@code TableSwitchInstruction} describe an {@code
 * int} range and {@linkplain Label labels}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class TableSwitchInstruction
extends JavaInstruction
{
	/** The lower bound. */
	private final int lowerBound;

	/** The upper bound. */
	private final int upperBound;

	/** The case {@linkplain Label labels}. */
	private final Label[] labels;

	/** The default {@linkplain Label} label. */
	private final Label defaultLabel;

	/**
	 * Answer the number of pad bytes required in the format of this
	 * {@linkplain TableSwitchInstruction instruction}.
	 *
	 * @return The number of pad bytes.
	 */
	private int padBytes ()
	{
		if (!hasValidAddress())
		{
			return 0;
		}
		return (int) (-(address() + 1) & 3);
	}

	@Override
	int size ()
	{
		// The magic number 13 accounts for the opcode, the default address,
		// the lower bound, and the upper bound.
		return 13 + padBytes() + 4 * labels.length;
	}

	@Override
	public boolean isBranch ()
	{
		return true;
	}

	@Override
	public Label[] labels ()
	{
		return labels;
	}

	/**
	 * Answer the appropriate {@linkplain JavaBytecode bytecode} for this
	 * {@linkplain LookupSwitchInstruction instruction}.
	 *
	 * @return The appropriate bytecode.
	 */
	private JavaBytecode bytecode ()
	{
		return JavaBytecode.tableswitch;
	}

	@Override
	VerificationTypeInfo[] inputOperands ()
	{
		return new VerificationTypeInfo[] {JavaOperand.INDEX.create()};
	}

	@Override
	VerificationTypeInfo[] outputOperands (
		final List<VerificationTypeInfo> operandStack)
	{
		return noOperands;
	}

	@Override
	public String toString ()
	{
		@SuppressWarnings("resource")
		final Formatter formatter = new Formatter();
		final int size = labels.length;
		formatter.format("%s [0..%d]%n\t{", bytecode(), size);
		for (int i = 0; i < size; i++)
		{
			final Label label = labels[i];
			formatter.format("%n\t\t[%d] → %s", i, label);
		}
		formatter.format("%n\t\tdefault → %s%n\t}", defaultLabel);
		return formatter.toString();
	}

	@Override
	void writeBytecodeTo (final DataOutput out) throws IOException
	{
		bytecode().writeTo(out);
	}


	@Override
	void writeImmediatesTo (final DataOutput out) throws IOException
	{
		switch (padBytes())
		{
			default:
				assert false : "padBytes() should be between 0 and 3";
				break;
			case 3:
				out.writeByte(0);
				// $FALL-THROUGH$
			case 2:
				out.writeByte(0);
				// $FALL-THROUGH$
			case 1:
				out.writeByte(0);
				// $FALL-THROUGH$
			case 0:
				// Do nothing.
		}
		assert defaultLabel.hasValidAddress();
		out.writeInt((int) (defaultLabel.address() - address()));
		out.writeInt(lowerBound);
		out.writeInt(upperBound);
		for (final Label label : labels)
		{
			assert label.hasValidAddress();
			out.writeInt((int) (label.address() - address()));
		}
	}

	/**
	 * Construct a new {@link TableSwitchInstruction}.
	 *
	 * @param lowerBound
	 *        The lower bound.
	 * @param upperBound
	 *        The upper bound.
	 * @param labels
	 *        The case {@linkplain Label labels}.
	 * @param defaultLabel
	 *        The default label.
	 */
	public TableSwitchInstruction (
		final int lowerBound,
		final int upperBound,
		final Label[] labels,
		final Label defaultLabel)
	{
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
		this.labels = labels;
		this.defaultLabel = defaultLabel;
	}
}