/**
 * compiler/AvailReferenceNode.java
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
import com.avail.compiler.AvailVariableDeclarationNode;
import com.avail.compiler.AvailVariableUseNode;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ContainerTypeDescriptor;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.Transformer1;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

public class AvailReferenceNode extends AvailParseNode
{
	AvailVariableUseNode _variable;


	// accessing

	public AvailVariableDeclarationNode associatedDeclaration ()
	{
		//  Answer the variable declaration associated with this reference expression.

		return _variable.associatedDeclaration();
	}

	@Override
	public AvailObject type ()
	{
		//  The value I represent is a variable itself.  Answer an appropriate container type.

		return ContainerTypeDescriptor.wrapInnerType(_variable.type());
	}

	public AvailVariableUseNode variable ()
	{
		//  Answer the AvailVariableUseNode that this expression refers to.

		return _variable;
	}

	public void variable (
			final AvailVariableUseNode aVariableUse)
	{

		_variable = aVariableUse;
	}



	// code generation

	@Override
	public void emitValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		_variable.associatedDeclaration().emitVariableReferenceOn(codeGenerator);
	}


	@Override
	public void childrenMap (
			final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{
		_variable = ((AvailVariableUseNode)(aBlock.value(_variable)));
	}


	@Override
	public void printOnIndent (
			final StringBuilder aStream,
			final int indent)
	{
		aStream.append("& ");
		_variable.printOnIndent(aStream, indent);
	}


	@Override
	public AvailParseNode validateLocallyWithParentOuterBlocksInterpreter (
			final AvailParseNode parent,
			final List<AvailBlockNode> outerBlocks,
			final L2Interpreter anAvailInterpreter)
	{
		final AvailVariableDeclarationNode decl = _variable.associatedDeclaration();
		if ((decl.isSyntheticVariableDeclaration() & decl.isConstant()))
		{
			error("You can't take the reference of a module constant");
			return null;
		}
		if (decl.isArgument())
		{
			error("You can't take the reference of an argument");
			return null;
		}
		if (decl.isLabel())
		{
			error("You can't take the reference of a label");
			return null;
		}
		if (decl.isConstant())
		{
			error("You can't take the reference of a constant");
			return null;
		}
		return this;
	}

}
