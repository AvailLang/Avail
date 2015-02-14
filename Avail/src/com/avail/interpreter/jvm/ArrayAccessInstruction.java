/**
 * ArrayAccessInstruction.java
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
 * An {@code ArrayAccessInstruction} represents accessing an element of an
 * array. It requires no immediate values.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class ArrayAccessInstruction
extends JavaInstruction
{
	/**
	 * The {@linkplain Class type} of the array, either a {@linkplain
	 * Class#isPrimitive() primitive type} or {@link Object Object.class} for
	 * a reference type.
	 */
	private final Class<?> type;

	@Override
	int size ()
	{
		return 0;
	}

	/**
	 * Answer a table of possible {@linkplain JavaBytecode bytecodes} for the
	 * encoding of this {@linkplain ArrayAccessInstruction instruction}.
	 *
	 * @return A table of possible bytecodes.
	 */
	abstract JavaBytecode[] bytecodes ();

	/**
	 * Answer the appropriate {@linkplain JavaBytecode bytecode} that encodes
	 * this {@linkplain ArrayAccessInstruction instruction}.
	 *
	 * @return The appropriate bytecode.
	 */
	private JavaBytecode bytecode ()
	{
		final int index = type == Object.class ? 0
			: type == Boolean.TYPE ? 1
			: type == Byte.TYPE ? 1
			: type == Character.TYPE ? 2
			: type == Double.TYPE ? 3
			: type == Float.TYPE ? 4
			: type == Integer.TYPE ? 5
			: type == Long.TYPE ? 6
			: type == Short.TYPE ? 7
			: -1;
		assert index != -1;
		return bytecodes()[index];
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
		bytecode().writeTo(out);
	}

	@Override
	final void writeImmediatesTo (final DataOutput out) throws IOException
	{
		// No implementation required.
	}

	@Override
	public String toString ()
	{
		return String.format("%-15s%s", bytecode().mnemonic(), type.getName());
	}

	/**
	 * Construct a new {@link ArrayAccessInstruction}.
	 *
	 * @param type
	 *        The {@linkplain Class type} of the array, either a {@linkplain
	 *        Class#isPrimitive() primitive type} or {@link Object Object.class}
	 *        for a reference type.
	 */
	ArrayAccessInstruction (final Class<?> type)
	{
		this.type = type;
	}
}
