/*
 * AvailLabel.java
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
import com.avail.descriptor.BlockPhraseDescriptor;
import com.avail.descriptor.DeclarationPhraseDescriptor.DeclarationKind;

import java.io.ByteArrayOutputStream;

/**
 * An {@code AvailLabel} is a pseudo-instruction in the {@linkplain
 * AvailInstruction Level One instruction} set.  It represents a {@linkplain
 * DeclarationKind#LABEL label phrase} in the parse tree of a {@linkplain
 * BlockPhraseDescriptor block}.  If a label declaration occurs at all in a
 * block, it must be the first statement of the block.
 *
 * <p>No actual nybblecodes are generated for an {@code AvailLabel}.  The only
 * reason for a label pseudo-instruction to exist is to keep track of which
 * blocks require labels.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailLabel extends AvailInstruction
{
	/**
	 * Construct an instruction.  Capture the tokens that contributed to it.
	 *
	 * @param relevantTokens
	 *        The {@link A_Tuple} of {@link A_Token}s that are associated with
	 *        this instruction.
	 */
	public AvailLabel (final A_Tuple relevantTokens)
	{
		super(relevantTokens);
	}

	@Override
	public void writeNybblesOn (
		final ByteArrayOutputStream aStream)
	{
		// A label pseudo-instruction has no actual nybblecode instructions
		// generated for it.
	}
}
