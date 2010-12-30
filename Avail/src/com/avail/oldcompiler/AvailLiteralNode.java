/**
 * compiler/AvailLiteralNode.java
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

package com.avail.oldcompiler;

import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.newcompiler.scanner.TokenDescriptor;

/**
 * I represent a literal occurring in the text of an Avail program.  At the
 * moment I must be a positive integer, float or double, or a string.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AvailLiteralNode extends AvailParseNode
{
	/**
	 * The token I'm based on.
	 */
	AvailObject _token;

	/**
	 * The actual value I enclose.
	 */
	AvailObject _availValue;

	/**
	 * The {@link TypeDescriptor type} of the value I enclose.
	 */
	AvailObject _availType;


	/**
	 * Answer the literal value I hold.
	 *
	 * @return The literal's value.
	 */
	public AvailObject availValue ()
	{
		assert _availValue != null;
		return _availValue;
	}

	/**
	 * Answer the token I'm based on.
	 *
	 * @return The {@link TokenDescriptor token}.
	 */
	public AvailObject token ()
	{
		return _token;
	}


	/**
	 * Set the token I'm based on.
	 *
	 * @param aToken The {@link TokenDescriptor token}.
	 */
	public void token (
			final AvailObject aToken)
	{
		_token = aToken;
		_availValue = _token.literal();
		_availValue.makeImmutable();
		_availType = null;
	}


	@Override
	public AvailObject expressionType ()
	{
		assert _availValue != null;
		if (_availType == null)
		{
			_availType = _availValue.type().makeImmutable();
		}
		return _availType;
	}


	@Override
	public void emitValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		assert _availValue != null;
		codeGenerator.emitPushLiteral(_availValue);
	}


	@Override
	public void printOnIndent (
			final StringBuilder aStream,
			final int indent)
	{
		aStream.append(_availValue.toString());
	}


	@Override
	public boolean isLiteralNode ()
	{
		return true;
	}

}
