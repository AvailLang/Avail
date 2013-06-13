/**
 * SetFieldInstruction.java
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
import com.avail.interpreter.jvm.ConstantPool.FieldrefEntry;

/**
 * The immediate value of a {@code SetFieldInstruction} is a {@linkplain
 * FieldrefEntry field reference entry} within the {@linkplain ConstantPool
 * constant pool}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class SetFieldInstruction
extends SimpleInstruction
{
	/** The {@linkplain FieldrefEntry entry} for the referenced field. */
	private final FieldrefEntry fieldrefEntry;

	@Override
	void writeImmediatesTo (final DataOutput out) throws IOException
	{
		fieldrefEntry.writeIndexTo(out);
	}

	@Override
	public String toString ()
	{
		return String.format("%s%s", super.toString(), fieldrefEntry);
	}

	/**
	 * Construct a new {@link SetFieldInstruction}.
	 *
	 * @param bytecode
	 *        The {@linkplain JavaBytecode bytecode}.
	 * @param fieldrefEntry
	 *        The {@linkplain FieldrefEntry entry} for the referenced field.
	 */
	public SetFieldInstruction (
		final JavaBytecode bytecode,
		final FieldrefEntry fieldrefEntry)
	{
		super(bytecode);
		this.fieldrefEntry = fieldrefEntry;
	}
}
