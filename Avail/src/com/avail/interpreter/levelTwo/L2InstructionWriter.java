/**
 * interpreter/levelTwo/L2InstructionWriter.java
 * Copyright (c) 2010, Mark van Gulik.
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

import java.io.ByteArrayOutputStream;
import com.avail.descriptor.*;

/**
 * An {@code L2InstructionWriter} provides a mechanism for writing a sequence of
 * {@linkplain L2RawInstruction level two wordcode instructions}.
 */
public class L2InstructionWriter
{

	/**
	 * The stream of bytes on which to write the wordcodes.  Each wordcode must
	 * be in the range 0..65535, as must each operand.
	 */
	final ByteArrayOutputStream stream = new ByteArrayOutputStream();

	/**
	 * Write an operand in the range 0..65535.  Write it as two bytes in
	 * big endian order.
	 *
	 * @param operand The operand to write.
	 */
	private void writeOperand(final int operand)
	{
		assert operand == (operand & 0xFFFF);
		stream.write(operand >>> 8);
		stream.write(operand & 0xFF);
	}

	/**
	 * Write the given {@link L2RawInstruction}, including the operation code
	 * and the operands.
	 *
	 * @param instruction The {@link L2RawInstruction} to write.
	 */
	public void write(final L2RawInstruction instruction)
	{
		final int opcode = instruction.operation().ordinal();
		assert opcode == (opcode & 0xFFFF);
		stream.write(opcode >>> 8);
		stream.write(opcode & 0xFF);
		final int [] operands = instruction.operands();
		for (final int operand : operands)
		{
			writeOperand(operand);
		}
	}

	/**
	 * Extract the previously {@linkplain
	 * L2InstructionWriter#write(L2RawInstruction) written} wordcodes as a tuple
	 * of integers.  Use a compact representation if the result can be expressed
	 * as a {@linkplain ByteTupleDescriptor tuple of bytes}.
	 *
	 * @return The {@linkplain TupleDescriptor tuple} of integers representing the
	 *         operations and operands of the {@link L2RawInstruction}s
	 *         previously {@link #write(L2RawInstruction) written} to the
	 *         receiver.
	 */
	public AvailObject words()
	{
		final byte [] byteArray = stream.toByteArray();
		final int wordCount = byteArray.length >> 1;
		// If all the high bytes are zero we can use a ByteTuple.
		boolean allBytes = true;
		for (int i = 0; i < byteArray.length; i += 2)
		{
			if (byteArray[i] != 0)
			{
				allBytes = false;
				break;
			}
		}
		AvailObject words;
		if (allBytes)
		{
			words = ByteTupleDescriptor.mutableObjectOfSize(wordCount);
			int dest = 1;
			for (int source = 1; source < byteArray.length; source += 2)
			{
				words.rawByteAtPut(dest++, byteArray[source]);
			}
		}
		else
		{
			words = ObjectTupleDescriptor.mutable().create(
				wordCount);
			int dest = 1;
			for (int source = 0; source < byteArray.length; source += 2)
			{
				final int value =
					(byteArray[source] & 0xFF) << 8
					+ (byteArray[source + 1] & 0xFF);
				words.tupleAtPut(dest++, IntegerDescriptor.fromInt(value));
			}
		}
		words.hashOrZero(0);
		words.makeImmutable();
		return words;
	}
}