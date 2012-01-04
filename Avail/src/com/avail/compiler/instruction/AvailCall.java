/**
 * AvailCall.java
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

package com.avail.compiler.instruction;

import com.avail.descriptor.ImplementationSetDescriptor;
import com.avail.interpreter.levelOne.L1Operation;
import java.io.ByteArrayOutputStream;

/**
 * This is a multi-method call instruction.  The opcode is followed by the index
 * of the message (an {@linkplain ImplementationSetDescriptor implementation set}),
 * then the index of the literal that holds the return type for this call site.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AvailCall extends AvailInstructionWithIndex
{

	/**
	 * The index of the literal that holds the call-site specific return type.
	 */
	int verifyIndex;


	/**
	 * Construct a new {@link AvailCall}.
	 *
	 * @param messageIndex The index of the literal that holds the message (an
	 *                     {@linkplain ImplementationSetDescriptor implementation
	 *                     set}.
	 * @param verifyIndex The index of the literal that holds the return type.
	 */
	public AvailCall (int messageIndex, int verifyIndex)
	{
		super(messageIndex);
		this.verifyIndex = verifyIndex;
	}


	@Override
	public void writeNybblesOn (
			final ByteArrayOutputStream aStream)
	{
		L1Operation.L1_doCall.writeTo(aStream);
		writeIntegerOn(index, aStream);
		writeIntegerOn(verifyIndex, aStream);
	}
}
