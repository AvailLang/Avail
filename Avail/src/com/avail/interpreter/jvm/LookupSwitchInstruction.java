/**
 * LookupSwitchInstruction.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
 * The immediate values of a {@code LookupSwitchInstruction} describe keys and
 * {@linkplain Label labels}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class LookupSwitchInstruction
extends JavaInstruction
{
	/** The keys for the switch. */
	private final int[] keys;

	/** The case {@linkplain Label labels} for the switch. */
	private final Label[] labels;

	/** The default {@linkplain Label label} for the switch. */
	private final Label defaultLabel;

	/**
	 * Answer the number of pad bytes required in the format of this
	 * {@linkplain LookupSwitchInstruction instruction}.
	 *
	 * @return The number of pad bytes.
	 */
	private int padBytes ()
	{
		if (!hasValidAddress())
		{
			return 0;
		}
		return (int) (-address() & 3);
	}

	@Override
	int size ()
	{
		// The magic number 9 accounts for the opcode, the default address, and
		// the number of labels.
		return 9 + padBytes() + 8 * labels.length;
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
		return JavaBytecode.lookupswitch;
	}

	@Override
	JavaOperand[] inputOperands ()
	{
		return bytecode().inputOperands();
	}

	@Override
	JavaOperand[] outputOperands (final List<JavaOperand> operandStack)
	{
		return bytecode().outputOperands();
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
		out.writeInt(labels.length);
		for (int i = 0; i < labels.length; i++)
		{
			assert labels[i].hasValidAddress();
			out.writeInt(keys[i]);
			out.writeInt((int) (labels[i].address() - address()));
		}
	}

	/**
	 * Construct a new {@link LookupSwitchInstruction}.
	 *
	 * @param keys
	 *        The keys for the switch.
	 * @param labels
	 *        The case {@linkplain Label labels} for the switch.
	 * @param defaultLabel
	 *        The default label for the switch.
	 */
	public LookupSwitchInstruction (
		final int[] keys,
		final Label[] labels,
		final Label defaultLabel)
	{
		assert keys.length == labels.length;
		this.keys = keys;
		this.labels = labels;
		this.defaultLabel = defaultLabel;
	}
}
