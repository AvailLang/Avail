/*
 * L2OperandDescriber.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import com.avail.descriptor.A_Bundle;
import com.avail.interpreter.levelTwo.operand.*;
import com.avail.optimizer.L2BasicBlock;

import javax.annotation.Nullable;
import java.util.List;

import static com.avail.utility.Nulls.stripNull;
import static java.lang.String.format;

/**
 * An {@code L2OperandDescriber} uses the {@link L2OperandTypeDispatcher}
 * mechanism to describe one of the operands of a level two instruction.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
final class L2OperandDescriber
implements L2OperandTypeDispatcher
{
	/**
	 * The operand being described.
	 */
	private @Nullable L2Operand operand;

	/**
	 * The {@link StringBuilder} on which to write an operand description.
	 */
	private @Nullable StringBuilder description;

	/**
	 * Extract the current operand being described.
	 *
	 * @return The current {@link L2Operand}.
	 */
	private <T extends L2Operand> T operand (final Class<T> clazz)
	{
		//noinspection unchecked
		return clazz.cast(stripNull(operand));
	}

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
		final StringBuilder builder = stripNull(description);
		builder.append(format(format, arguments));
	}

	/**
	 * Describe the current operand, which must be some vector of object
	 * registers.
	 *
	 * @param vector The {@link List} of {@link L2Operand}s inside the operand.
	 */
	private <SpecificOperand extends L2Operand> void printVector (
		final List<SpecificOperand> vector)
	{
		final StringBuilder builder = stripNull(description);
		print("Vec=(");
		boolean first = true;
		for (final SpecificOperand reg : vector)
		{
			if (!first)
			{
				builder.append(",");
			}
			builder.append(reg);
			first = false;
		}
		builder.append(")");
	}

	/**
	 * Output a description of the given operand to the stream, given its
	 * numeric encoding, its {@linkplain L2OperandType operand type}, and the
	 * current {@link L2Chunk}.
	 *
	 * @param namedOperandType
	 *        The {@link L2OperandType} used to interpret the operand.
	 * @param op
	 *        The numeric operand itself, an {@code int}.
	 * @param stream
	 *        The {@link StringBuilder} that will have a suitable operand
	 *        description appended.
	 */
	public void describeInOperandChunkOn (
			final L2NamedOperandType namedOperandType,
			final L2Operand op,
			final StringBuilder stream)
	{
		this.operand = op;
		description = stream;
		stream.append(
			format(
				"%n\t%s = ",
				namedOperandType.name()));
		namedOperandType.operandType().dispatch(this);
	}


	@Override
	public void doConstant()
	{
		print("Const(%s)", operand(L2ConstantOperand.class).object);
	}

	@Override
	public void doImmediate()
	{
		print("Immediate(%d)", operand(L2ImmediateOperand.class).value);
	}

	@Override
	public void doPC()
	{
		final L2BasicBlock targetBlock =
			operand(L2PcOperand.class).targetBlock();
		print(
			"PC(%s at #%d)",
			targetBlock.name(),
			targetBlock.offset());
	}

	@Override
	public void doPrimitive()
	{
		print("Prim(%s)", operand(L2PrimitiveOperand.class).primitive.name());
	}

	@Override
	public void doSelector()
	{
		final A_Bundle bundle = operand(L2SelectorOperand.class).bundle;
		print("Message(%s)", bundle.message().atomName().asNativeString());
	}

	@Override
	public void doReadPointer()
	{
		print("Obj(%s)[r]", operand(L2ReadPointerOperand.class));
	}

	@Override
	public void doWritePointer()
	{
		print("Obj(%s)[w]", operand(L2WritePointerOperand.class));
	}

	@Override
	public void doReadInt()
	{
		print("Int(%s)[r]", operand(L2ReadIntOperand.class));
	}

	@Override
	public void doWriteInt()
	{
		print("Int(%s)[w]", operand(L2WriteIntOperand.class));
	}

	@Override
	public void doReadVector()
	{
		printVector(operand(L2ReadVectorOperand.class).elements());
		print("[r]");
	}

	@Override
	public void doWriteVector()
	{
		printVector(operand(L2WriteVectorOperand.class).elements());
		print("[w]");
	}

	@Override
	public void doComment ()
	{
		print(format(
			"[comment: %s]", operand(L2CommentOperand.class).comment));
	}
}
