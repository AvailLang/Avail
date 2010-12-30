/**
 * compiler/AvailInitializingDeclarationNode.java
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
import com.avail.descriptor.VoidDescriptor;
import com.avail.utility.Transformer1;

public class AvailInitializingDeclarationNode extends AvailVariableDeclarationNode
{
	AvailParseNode _initializingExpression;


	// accessing

	public AvailParseNode initializingExpression ()
	{
		return _initializingExpression;
	}

	public void initializingExpression (
			final AvailParseNode expr)
	{
		_initializingExpression = expr;
	}

	@Override
	public boolean isInitializing ()
	{
		return true;
	}



	// code generation

	@Override
	public void emitEffectOn (
			final AvailCodeGenerator codeGenerator)
	{
		//  This constant declaration acts like an assignment at the point it occurs in code.

		assert !isArgument() : "inconsistency - is it constant or argument?";
		_initializingExpression.emitValueOn(codeGenerator);
		codeGenerator.emitSetLocalOrOuter(this);
	}

	@Override
	public void emitValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		//  This is a declaration, so it shouldn't produce a value.
		//
		//  DialogView warn: 'Consistency error - constant declaration shouldn''t be last statement of a value-returning block'.

		emitEffectOn(codeGenerator);
		codeGenerator.emitPushLiteral(VoidDescriptor.voidObject());
	}



	// enumerating

	@Override
	public void childrenMap (
			final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{
		//  Map my children through the (destructive) transformation
		//  specified by aBlock.  Answer the receiver.


		_initializingExpression = aBlock.value(_initializingExpression);
	}



	// java printing

	@Override
	public void printOnIndent (
			final StringBuilder aStream,
			final int indent)
	{
		if (_name != null)
		{
			aStream.append(_name.string().asNativeString());
		}
		aStream.append(" : ");
		aStream.append(_declaredType.toString());
		aStream.append(" := ");
		_initializingExpression.printOnIndent(aStream, indent);
	}





}
