/**
 * compiler/AvailTupleNode.java
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

package com.avail.compiler;

import java.util.ArrayList;
import java.util.List;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;

/**
 * Create a tuple from a list of expressions that generate the tuple's elements.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AvailTupleNode extends AvailParseNode
{

	/**
	 * The expressions that generate elements of the tuple.
	 */
	List<AvailParseNode> parseNodes;

	/**
	 * The type of tuple to be constructed.
	 */
	AvailObject tupleType;

	/**
	 * Construct a new instance that builds a tuple from the values of the given
	 * expressions.
	 * 
	 * @param parseNodes The expressions that generate my elements.
	 */
	public AvailTupleNode (final List<AvailParseNode> parseNodes)
	{
		this.parseNodes = parseNodes;
		List<AvailObject> types;
		types = new ArrayList<AvailObject>(parseNodes.size());
		for (AvailParseNode expr : parseNodes)
		{
			types.add(expr.type());
		}
		tupleType = TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			IntegerRangeTypeDescriptor.singleInteger(IntegerDescriptor.objectFromInt(types.size())),
			TupleDescriptor.mutableObjectFromArray(types),
			Types.terminates.object());
		tupleType.makeImmutable();
	}


	@Override
	public AvailObject type ()
	{
		return tupleType;
	}


	@Override
	public void emitValueOn (
		final AvailCodeGenerator codeGenerator)
	{
		for (AvailParseNode expr : parseNodes)
		{
			expr.emitValueOn(codeGenerator);
		}
		codeGenerator.emitMakeTuple(parseNodes.size());
	}


	/**
	 * Create a new {@code AvailTupleNode} with one more parse node added to the
	 * end of the tuple.
	 * 
	 * @param newParseNode The parse node to append.
	 * @return A new {@code AvailTupleNode} with the parse node appended.
	 */
	public AvailTupleNode copyWith (
		AvailParseNode newParseNode)
	{
		List<AvailParseNode> newNodes =
			new ArrayList<AvailParseNode>(parseNodes.size() + 1);
		newNodes.addAll(parseNodes);
		newNodes.add(newParseNode);
		AvailTupleNode newTupleNode = new AvailTupleNode(newNodes);
		return newTupleNode;
	}


	/**
	 * Map my children through the (destructive) transformation specified by
	 * aBlock.  Answer the receiver.
	 */
	@Override
	public void childrenMap (
		final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{
		parseNodes = new ArrayList<AvailParseNode>(parseNodes);
		for (int i = 0; i < parseNodes.size(); i++)
		{
			parseNodes.set(i, aBlock.value(parseNodes.get(i)));
		}
	}


	@Override
	public void printOnIndent (
		final StringBuilder aStream,
		final int indent)
	{
		for (int i = 1, _end1 = parseNodes.size(); i <= _end1; i++)
		{
			if (i > 1)
			{
				aStream.append(", ");
			}
			parseNodes.get(i - 1).printOnIndentIn(
				aStream,
				(indent + 1),
				this);
		}
	}

}
