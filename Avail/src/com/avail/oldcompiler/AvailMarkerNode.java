/**
 * compiler/AvailMarkerNode.java
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
import com.avail.descriptor.AvailObject;
import com.avail.utility.Transformer1;

/**
 * An {@link AvailMarkerNode} represent a parsing marker that can be pushed onto
 * the parse stack.  It should never occur as part of a composite {@link
 * AvailParseNode}, and is not capable of emitting code.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
@Deprecated
public class AvailMarkerNode extends AvailParseNode
{
	/**
	 * The actual value I enclose.
	 */
	private final AvailObject markerValue;


	/**
	 * Answer the {@link AvailObject} held by this node.  Its type is usage
	 * dependent.
	 *
	 * @return The value.
	 */
	public AvailObject markerValue ()
	{
		assert markerValue != null;
		return markerValue;
	}


	/**
	 * Construct a new marker node for the given {@link AvailObject value}.
	 *
	 * @param markerValue The {@link AvailObject} that this node holds.
	 */
	public AvailMarkerNode (final AvailObject markerValue)
	{
		this.markerValue = markerValue;
	}


	@Override
	public AvailObject expressionType ()
	{
		assert false : "A marker node has no type.";
		return null;
	}


	@Override
	public void emitValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		assert false : "A marker node can not generate code.";
	}


	@Override
	public void printOnIndent (
			final StringBuilder aStream,
			final int indent)
	{
		aStream.append("Marker node: ");
		aStream.append(markerValue.toString());
	}


	@Override
	public void childrenMap (
		final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{
		// Do nothing.
	}

}
