/**
 * compiler/AvailSuperCastNode.java
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

import static com.avail.descriptor.AvailObject.error;
import java.util.List;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.Transformer1;

public class AvailSuperCastNode extends AvailParseNode
{
	AvailParseNode _expression;
	AvailObject _type;


	// accessing

	public AvailParseNode expression ()
	{
		return _expression;
	}

	public void expression (
			final AvailParseNode anExpression)
	{
		_expression = anExpression;
	}

	@Override
	public AvailObject expressionType ()
	{
		return _type;
	}

	public void type (
			final AvailObject aType)
	{
		_type = aType;
	}



	// code generation

	@Override
	public void emitEffectOn (
			final AvailCodeGenerator codeGenerator)
	{
		error("A superCast can only be done to an argument of a call.");
		return;
	}

	@Override
	public void emitValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		//  my parent deals with my altered semantics.
		_expression.emitValueOn(codeGenerator);
	}



	// enumerating

	@Override
	public void childrenMap (
			final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{
		//  Map my children through the (destructive) transformation
		//  specified by aBlock.  Answer the receiver.

		_expression = aBlock.value(_expression);
	}



	// java printing

	@Override
	public void printOnIndent (
			final StringBuilder aStream,
			final int indent)
	{
		_expression.printOnIndentIn(
			aStream,
			indent,
			this);
		aStream.append(' ');
		aStream.append(" :: ");
		aStream.append(_type.toString());
	}


	@Override
	public boolean isSuperCast ()
	{
		return true;
	}


	@Override
	public AvailParseNode validateLocally (
			final AvailParseNode parent,
			final List<AvailBlockNode> outerBlocks,
			final L2Interpreter anAvailInterpreter)
	{
		if (!parent.isSend())
		{
			error("Only use superCast notation as a message argument");
			return null;
		}
		return this;
	}





}
