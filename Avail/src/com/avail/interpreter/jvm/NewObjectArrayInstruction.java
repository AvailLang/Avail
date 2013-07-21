/**
 * NewObjectArrayInstruction.java
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
import java.util.Arrays;
import java.util.List;
import com.avail.interpreter.jvm.ConstantPool.ClassEntry;

/**
 * The immediate values of a {@code NewObjectArrayInstruction} refers to a
 * {@linkplain ClassEntry class entry} within the {@linkplain ConstantPool
 * constant pool} and the number of dimensions.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class NewObjectArrayInstruction
extends JavaInstruction
{
	/** The {@linkplain ClassEntry class entry} of the array type. */
	private final ClassEntry classEntry;

	/** The number of dimensions. */
	private final int dimensions;

	/**
	 * Does the {@linkplain NewObjectArrayInstruction instruction} create a
	 * multidimensional array?
	 *
	 * @return {@code true} if the instruction creates a multidimensional array,
	 *         {@code false} if the instruction creates a one-dimensional array.
	 */
	private boolean isMultidimensional ()
	{
		return dimensions > 1;
	}

	@Override
	int size ()
	{
		return isMultidimensional() ? 4 : 3;
	}

	/**
	 * Answer the appropriate {@linkplain JavaBytecode bytecode} for the
	 * {@linkplain NewObjectArrayInstruction instruction}.
	 *
	 * @return The appropriate bytecode.
	 */
	private JavaBytecode bytecode ()
	{
		return isMultidimensional()
			? JavaBytecode.anewarray
			: JavaBytecode.multianewarray;
	}

	@Override
	boolean canConsumeOperands (final List<JavaOperand> operands)
	{
		final int size = operands.size();
		try
		{
			for (int i = size - dimensions; i < size; i++)
			{
				if (operands.get(i).baseOperand() != JavaOperand.INT)
				{
					return false;
				}
			}
			return true;
		}
		catch (final IndexOutOfBoundsException e)
		{
			return false;
		}
	}

	@Override
	JavaOperand[] inputOperands ()
	{
		final JavaOperand[] operands = new JavaOperand[dimensions];
		Arrays.fill(operands, JavaOperand.COUNT);
		return operands;
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
		classEntry.writeIndexTo(out);
		if (isMultidimensional())
		{
			out.writeByte(dimensions);
		}
	}

	@Override
	public String toString ()
	{
		if (isMultidimensional())
		{
			return String.format(
				"%-15s%s [%d]", super.toString(), classEntry, dimensions);
		}
		return String.format("%-15s%s", super.toString(), classEntry);
	}

	/**
	 * Construct a new {@link NewObjectArrayInstruction}.
	 *
	 * @param classEntry
	 *        The {@linkplain ClassEntry class entry}.
	 * @param dimensions
	 *        The number of dimensions.
	 */
	NewObjectArrayInstruction (
		final ClassEntry classEntry,
		final int dimensions)
	{
		assert dimensions >= 1;
		assert classEntry.isArray || dimensions == 1;
		this.classEntry = classEntry;
		this.dimensions = dimensions;
	}
}
