/**
 * compiler/AvailListNode.java
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

import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.AvailParseNode;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ConcatenatedTupleTypeDescriptor;
import com.avail.descriptor.IntegerDescriptor;
import com.avail.descriptor.IntegerRangeTypeDescriptor;
import com.avail.descriptor.ListTypeDescriptor;
import com.avail.descriptor.TupleDescriptor;
import com.avail.descriptor.TupleTypeDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;

import java.util.ArrayList;
import java.util.List;

public class AvailListNode extends AvailParseNode
{
	List<AvailParseNode> parseNodes;

	AvailObject listType;


	public List<AvailParseNode> expressions ()
	{
		return parseNodes;
	}

	public void expressions (
		final List<AvailParseNode> arrayOfExpressions)
	{
		parseNodes = arrayOfExpressions;
		List<AvailObject> types;
		types = new ArrayList<AvailObject>(parseNodes.size());
		for (AvailParseNode expr : parseNodes)
		{
			types.add(expr.type());
		}
		final AvailObject tupleType = TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			IntegerRangeTypeDescriptor.singleInteger(IntegerDescriptor.objectFromInt(types.size())),
			TupleDescriptor.mutableObjectFromArray(types),
			Types.terminates.object());
		listType = ListTypeDescriptor.listTypeForTupleType(tupleType);
		//  The problem isn't really here, but this Avail compiler is only a bootstrapper.
		listType.makeImmutable();
	}

	@Override
	public AvailObject type ()
	{
		return listType;
	}


	@Override
	public void emitValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		for (AvailParseNode expr : parseNodes)
		{
			expr.emitValueOn(codeGenerator);
		}
		codeGenerator.emitMakeList(parseNodes.size());
	}

	
	/**
	 * Create a new {@code AvailListNode} with one more parse node added to the
	 * end of the list.
	 * 
	 * @param newParseNode The parse node to append.
	 * @return A new {@code AvailListNode} with the parse node appended.
	 */
	public AvailListNode copyWith (
		AvailParseNode newParseNode)
	{
		List<AvailParseNode> newNodes =
			new ArrayList<AvailParseNode>(parseNodes.size() + 1);
		newNodes.addAll(parseNodes);
		newNodes.add(newParseNode);
		AvailListNode newListNode = new AvailListNode();
		newListNode.expressions(newNodes);
		return newListNode;
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
