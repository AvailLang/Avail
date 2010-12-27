/**
 * compiler/AvailAssignmentNode.java
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

import static com.avail.descriptor.AvailObject.error;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.Transformer1;

public class AvailAssignmentNode extends AvailParseNode
{
	AvailVariableUseNode _variable;
	AvailParseNode _expression;


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
	public AvailObject type ()
	{
		return Types.voidType.object();
	}

	public AvailVariableUseNode variable ()
	{
		return _variable;
	}

	public void variable (
			final AvailVariableUseNode aVariable)
	{
		_variable = aVariable;
	}



	// code generation

	@Override
	public void emitEffectOn (
			final AvailCodeGenerator codeGenerator)
	{
		final AvailVariableDeclarationNode varDecl = _variable.associatedDeclaration();
		//  Disallow assignment to args, but allow assignment to module variables.
		assert !varDecl.isArgument();
		_expression.emitValueOn(codeGenerator);
		varDecl.emitVariableAssignmentOn(codeGenerator);
	}

	@Override
	public void emitValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		//  Assignments are now required to be top-level statements.

		error("Pass-through (embedded) assignments are no longer supported");
		return;
	}



	// enumerating

	@Override
	public void childrenMap (
			final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{
		//  Map my children through the (destructive) transformation
		//  specified by aBlock.  Answer the receiver.

		_expression = aBlock.value(_expression);
		_variable = (AvailVariableUseNode)aBlock.value(_variable);
	}



	// java printing

	@Override
	public void printOnIndent (
			final StringBuilder aStream,
			final int indent)
	{
		_variable.printOnIndent(aStream, indent);
		aStream.append(" := ");
		_expression.printOnIndent(aStream, indent);
	}

	@Override
	public void printOnIndentIn (
			final StringBuilder aStream,
			final int indent,
			final AvailParseNode sendOrCastNode)
	{
		//  Either way we need brackets, as an arg of a send or as an expression being superCasted.

		aStream.append('(');
		printOnIndent(aStream, indent);
		aStream.append(')');
	}


	@Override
	public boolean isAssignment ()
	{
		return true;
	}


	@Override
	public AvailParseNode validateLocally (
			final AvailParseNode parent,
			final List<AvailBlockNode> outerBlocks,
			final L2Interpreter anAvailInterpreter)
	{
		if (_variable.associatedDeclaration().isArgument())
		{
			error("Can't assign to argument");
			return null;
		}
		if (_variable.associatedDeclaration().isLabel())
		{
			error("Can't assign to label");
			return null;
		}
		if (_variable.associatedDeclaration().isConstant())
		{
			error("Can't assign to constant");
			return null;
		}
		return this;
	}





}
