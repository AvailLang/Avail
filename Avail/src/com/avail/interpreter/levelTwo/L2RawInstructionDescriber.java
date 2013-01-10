/**
 * L2RawInstructionDescriber.java
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

import com.avail.descriptor.*;

/**
 * An {@code L2RawInstructionDescriber} can {@linkplain #describe(
 * L2RawInstruction, AvailObject, StringBuilder) describe} an {@link
 * L2RawInstruction}, properly interpreting the instruction's {@linkplain
 * L2Operation operation} and {@linkplain L2OperandType operands}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2RawInstructionDescriber
{
	/**
	 *
	 */
	final L2OperandDescriber operandDescriber = new L2OperandDescriber();

	/**
	 * Describe an {@link L2RawInstruction}, including information about its
	 * {@linkplain L2Operation operation} and {@linkplain L2OperandType
	 * operands}.
	 *
	 * @param rawInstruction
	 *            The {@code L2RawInstruction} to describe.
	 * @param chunk
	 *            The {@link L2ChunkDescriptor chunk} in which the instruction
	 *            occurs.
	 * @param stream
	 *            Where to describe the instruction.
	 */
	public void describe (
			final L2RawInstruction rawInstruction,
			final AvailObject chunk,
			final StringBuilder stream)
	{
		final L2Operation operation = rawInstruction.operation();
		final String operationName = operation.name();
		stream.append(operationName);
		stream.append(" (");
		final L2NamedOperandType[] operandTypes = operation.operandTypes();
		final int[] operands = rawInstruction.operands();
		for (int i = 0; i < operands.length; i++)
		{
			if (i > 0)
			{
				stream.append(", ");
			}
			operandDescriber.describeInOperandChunkOn(
				operandTypes[i],
				operands[i],
				chunk,
				stream);
		}
		stream.append(")");
	}
}
