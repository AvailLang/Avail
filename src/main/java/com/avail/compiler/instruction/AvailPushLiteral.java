/*
 * AvailPushLiteral.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.compiler.instruction;

import com.avail.descriptor.A_Token;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.ContinuationDescriptor;
import com.avail.interpreter.levelOne.L1Operation;

import java.io.ByteArrayOutputStream;

/**
 * {@code AvailPushLiteral} is an instruction that represents pushing a
 * particular object (known at code generation time, undoubtedly earlier) onto a
 * {@linkplain ContinuationDescriptor continuation}'s stack.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailPushLiteral extends AvailInstructionWithIndex
{
	/**
	 * Construct a new {@code AvailPushLiteral}.
	 *
	 * @param relevantTokens
	 *        The {@link A_Tuple} of {@link A_Token}s that are associated with
	 *        this instruction.
	 * @param literalIndex The index of the literal being pushed.
	 */
	public AvailPushLiteral (
		final A_Tuple relevantTokens,
		final int literalIndex)
	{
		super(relevantTokens, literalIndex);
	}

	@Override
	public void writeNybblesOn (final ByteArrayOutputStream aStream)
	{
		L1Operation.L1_doPushLiteral.writeTo(aStream);
		writeIntegerOn(index, aStream);
	}
}
