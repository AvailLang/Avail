/**
 * compiler/AvailVariableUseNode.java
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
import com.avail.compiler.scanner.AvailToken;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Interpreter;
import java.util.List;

public class AvailVariableUseNode extends AvailParseNode
{
	AvailToken _name;
	AvailVariableDeclarationNode _associatedDeclaration;
	boolean _isLastUse;


	// accessing

	public AvailVariableDeclarationNode associatedDeclaration ()
	{
		return _associatedDeclaration;
	}

	public void associatedDeclaration (
			final AvailVariableDeclarationNode declarationNode)
	{

		_associatedDeclaration = declarationNode;
	}

	public boolean isLastUse ()
	{
		return _isLastUse;
	}

	public void isLastUse (
			final boolean aBoolean)
	{
		//  Set to true if there are no other places in my enclosing block (but not a subblock)
		//  that access the variable and execute later.  Since closing compiled code pushes
		//  outer and local variables, we can simply treat the complete (including subblocks)
		//  postfix tree-order as an equivalent ordering on the uses of the variable.  To
		//  determine if it executes later is difficult, but not because of blocks - continuations
		//  are tricky.  To simplify level one, when a continuation is constructed as the result
		//  of label construction at the start of a method, all variables are automatically made
		//  immutable (because the new continuation and the current one both use them).  If
		//  a garbage collect later makes them mutable (because only one reference was found),
		//  then the 'last use point' is a valid place to eliminate a variable, as there are no
		//  longer any continuations except the current one using it.

		_isLastUse = aBoolean;
	}

	public AvailToken name ()
	{
		return _name;
	}

	public void name (
			final AvailToken aToken)
	{

		_name = aToken;
	}

	@Override
	public AvailObject type ()
	{
		return _associatedDeclaration.declaredType();
	}


	@Override
	public void emitValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		_associatedDeclaration.emitVariableValueOn(codeGenerator);
	}


	@Override
	public void printOnIndent (
			final StringBuilder aStream,
			final int indent)
	{
		aStream.append(_name.string());
	}


	@Override
	public boolean isVariableUse ()
	{
		return true;
	}


	@Override
	public AvailParseNode validateLocallyWithParentOuterBlocksInterpreter (
			final AvailParseNode parent,
			final List<AvailBlockNode> outerBlocks,
			final L2Interpreter anAvailInterpreter)
	{
		return this;
	}

}
