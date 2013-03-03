/**
 * L2OperandDescriber.java
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

package com.avail.interpreter.levelTwo;

import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.Primitive;

/**
 * An {@code L2OperandDescriber} uses the {@link L2OperandTypeDispatcher}
 * mechanism to describe one of the operands of a level two instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class L2OperandDescriber implements L2OperandTypeDispatcher
{
	/**
	 * The {@link String name} of this {@link L2NamedOperandType}.
	 */
	private @Nullable String _name;

	/**
	 * The numeric operand being described.
	 */
	private int _operand;

	/**
	 * The {@linkplain L2ChunkDescriptor level two chunk} containing the
	 * operation and the operand to be described.
	 */
	private @Nullable A_Chunk _chunk;

	/**
	 * @return The current {@linkplain L2ChunkDescriptor chunk} containing the
	 *         instruction being described
	 */
	private A_Chunk chunk ()
	{
		final A_Chunk chunk = _chunk;
		assert chunk != null;
		return chunk;
	}

	/**
	 * The {@link StringBuilder} on which to write an operand description.
	 */
	private @Nullable StringBuilder _description;


	/**
	 * Print the format string, with the arguments plugged in.
	 *
	 * @see String#format(String, Object...)
	 *
	 * @param format The format {@link String} to use.
	 * @param arguments The arguments to substitute in the format string.
	 */
	private void print (
		final String format,
		final Object... arguments)
	{
		final StringBuilder builder = _description;
		assert builder != null;
		builder.append(String.format(format, arguments));
	}

	/**
	 * Describe the current operand, which must be some vector of object
	 * registers.
	 */
	private void printVector ()
	{
		final StringBuilder builder = _description;
		assert builder != null;
		print("Vec=(");
		final A_Tuple vector = chunk().vectors().tupleAt(_operand);
		for (int i = 1; i <= vector.tupleSize(); i++)
		{
			if (i > 1)
			{
				builder.append(",");
			}
			builder.append(vector.tupleIntAt(i));
		}
		builder.append(")");
	}

	/**
	 * Output a description of the given operand to the stream, given its
	 * numeric encoding, its {@linkplain L2OperandType operand type}, and the current
	 * {@linkplain L2ChunkDescriptor chunk}.
	 *
	 * @param namedOperandType
	 *            The {@link L2OperandType} used to interpret the operand.
	 * @param operand
	 *            The numeric operand itself, an {@code int}.
	 * @param chunk
	 *            The current {@linkplain L2ChunkDescriptor level two chunk} within
	 *            which the description is to occur.
	 * @param stream
	 *            The {@link StringBuilder} that will have a suitable operand
	 *            description appended.
	 */
	public void describeInOperandChunkOn (
			final L2NamedOperandType namedOperandType,
			final int operand,
			final A_Chunk chunk,
			final StringBuilder stream)
	{
		_name = namedOperandType.name();
		_operand = operand;
		_chunk = chunk;
		_description = stream;
		stream.append(
			String.format(
				"%n\t%s = ",
				_name));
		namedOperandType.operandType().dispatch(this);
	}


	@Override
	public void doConstant()
	{
		print("Const(%s)", chunk().literalAt(_operand));
	}
	@Override
	public void doImmediate()
	{
		print("Immediate(%d)", _operand);
	}
	@Override
	public void doPC()
	{
		print("PC(%d)", _operand);
	}
	@Override
	public void doPrimitive()
	{
		print("Prim(%s)", Primitive.byPrimitiveNumberOrFail(
			(short)_operand).name());
	}
	@Override
	public void doSelector()
	{
		final A_Method method = chunk().literalAt(_operand);
		print("Message(%s)", method.originalName().name().asNativeString());
	}
	@Override
	public void doReadPointer()
	{
		print("Obj(%s)[r]", _operand);
	}
	@Override
	public void doWritePointer()
	{
		print("Obj(%s)[w]", _operand);
	}
	@Override
	public void doReadWritePointer()
	{
		print("Obj(%s)[r/w]", _operand);
	}
	@Override
	public void doReadInt()
	{
		print("Int(%s)[r]", _operand);
	}
	@Override
	public void doWriteInt()
	{
		print("Int(%s)[w]", _operand);
	}
	@Override
	public void doReadWriteInt()
	{
		print("Int(%s)[r/w]", _operand);
	}
	@Override
	public void doReadVector()
	{
		printVector();
		print("[r]");
	}
	@Override
	public void doWriteVector()
	{
		printVector();
		print("[w]");
	}
	@Override
	public void doReadWriteVector()
	{
		printVector();
		print("[r/w]");
	}

	@Override
	public void doImplicitlyInitializeVector ()
	{
		printVector();
		print("[INIT]");
	}
}
