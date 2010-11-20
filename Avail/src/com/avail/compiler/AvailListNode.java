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
	List<AvailParseNode> _expressions;
	AvailObject _listType;


	// accessing

	public List<AvailParseNode> expressions ()
	{
		return _expressions;
	}

	public void expressions (
			final List<AvailParseNode> arrayOfExpressions)
	{

		_expressions = arrayOfExpressions;
		List<AvailObject> types;
		types = new ArrayList<AvailObject>(_expressions.size());
		for (AvailParseNode expr : _expressions)
		{
			types.add(expr.type());
		}
		final AvailObject tupleType = TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
			IntegerRangeTypeDescriptor.singleInteger(IntegerDescriptor.objectFromInt(types.size())),
			TupleDescriptor.mutableObjectFromArray(types),
			Types.terminates.object());
		_listType = ListTypeDescriptor.listTypeForTupleType(tupleType);
		//  The problem isn't really here, but this Avail compiler is only a bootstrapper.
		_listType.makeImmutable();
	}

	public AvailObject type ()
	{
		return _listType;
	}



	// code generation

	public void emitValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		for (AvailParseNode expr : _expressions)
		{
			expr.emitValueOn(codeGenerator);
		}
		codeGenerator.emitMakeList(_expressions.size());
	}



	// enumerating

	public void childrenMap (
			final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{
		//  Map my children through the (destructive) transformation
		//  specified by aBlock.  Answer the receiver.

		_expressions = new ArrayList<AvailParseNode>(_expressions);
		for (int i = 0; i < _expressions.size(); i++)
		{
			_expressions.set(i, aBlock.value(_expressions.get(i)));
		}
	}



	// java printing

	public void printOnIndent (
			final StringBuilder aStream, 
			final int indent)
	{
		for (int i = 1, _end1 = _expressions.size(); i <= _end1; i++)
		{
			if (i > 1)
			{
				aStream.append(", ");
			}
			_expressions.get(i - 1).printOnIndentIn(
				aStream,
				(indent + 1),
				this);
		}
	}





}
