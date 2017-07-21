/**
 * AvailCloseCode.java
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

package com.avail.compiler.instruction;

import com.avail.descriptor.FunctionDescriptor;
import com.avail.descriptor.CompiledCodeDescriptor;
import com.avail.interpreter.levelOne.L1Operation;
import java.io.ByteArrayOutputStream;

/**
 * This instruction build a {@linkplain FunctionDescriptor function} from
 * {@linkplain CompiledCodeDescriptor compiled code} and some pushed variables.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailCloseCode extends AvailInstructionWithIndex
{
	/**
	 * The number of variables that have been pushed on the stack to be
	 * captured as outer variables of the resulting {@linkplain
	 * FunctionDescriptor function}.
	 */
	final int numCopiedVars;

	/**
	 * Construct a new {@link AvailCloseCode}.
	 *
	 * @param numCopiedVars The number of already-pushed variables to capture
	 *                      in the function as outer variables.
	 * @param codeIndex The index of the compiled code in the literals.
	 */
	public AvailCloseCode (
			final int numCopiedVars,
			final int codeIndex)
	{
		super(codeIndex);
		this.numCopiedVars = numCopiedVars;
	}

	@Override
	public void writeNybblesOn (
			final ByteArrayOutputStream aStream)
	{
		L1Operation.L1_doClose.writeTo(aStream);
		writeIntegerOn(numCopiedVars, aStream);
		writeIntegerOn(index, aStream);
	}
}
