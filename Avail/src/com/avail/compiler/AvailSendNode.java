/**
 * compiler/AvailSendNode.java
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
import com.avail.interpreter.levelTwo.L2Interpreter;
import java.util.ArrayList;
import java.util.List;

public class AvailSendNode extends AvailParseNode
{
	AvailObject _message;
	AvailObject _bundle;
	AvailObject _implementationSet;
	List<AvailParseNode> _arguments;
	AvailObject _returnType;


	// accessing

	public List<AvailParseNode> arguments ()
	{
		return _arguments;
	}

	public void arguments (
			final List<AvailParseNode> anArray)
	{
		_arguments = anArray;
	}

	public AvailObject bundle ()
	{
		return _bundle;
	}

	public void bundle (
			final AvailObject aMessageBundle)
	{
		_bundle = aMessageBundle;
	}

	public AvailObject implementationSet ()
	{
		return _implementationSet;
	}

	public void implementationSet (
			final AvailObject anImplementationSet)
	{
		_implementationSet = anImplementationSet;
	}

	public AvailObject message ()
	{
		return _message;
	}

	public void message (
			final AvailObject cyclicType)
	{
		_message = cyclicType;
	}

	public AvailObject returnType ()
	{
		//  Make it immutable so multiple requests avoid accidental sharing of a mutable object.

		return _returnType.makeImmutable();
	}

	public void returnType (
			final AvailObject aType)
	{
		_returnType = aType;
	}

	public AvailObject type ()
	{
		return returnType();
	}



	// code generation

	public void emitValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		boolean anyCasts;
		anyCasts = false;
		for (AvailParseNode arg : _arguments)
		{
			arg.emitValueOn(codeGenerator);
			if (arg.isSuperCast())
			{
				anyCasts = true;
			}
		}
		_message.makeImmutable();
		if (anyCasts)
		{
			for (AvailParseNode arg: _arguments)
			{
				if (arg.isSuperCast())
				{
					codeGenerator.emitPushLiteral(arg.type());
				}
				else
				{
					codeGenerator.emitGetType(_arguments.size()-1);
				}
			}
			//  We've pushed all argument values and all arguments types onto the stack.
			codeGenerator.emitCallMethodByTypesNumArgsLiteral(_arguments.size(), _implementationSet);
		}
		else
		{
			codeGenerator.emitCallMethodByValuesNumArgsLiteral(_arguments.size(), _implementationSet);
		}
		//  Ok, now the return result is left on the stack, which is what we want.
		//  Emit an instruction that will verify the return type is what the method
		//  and its superimplementations promised at link time.
		codeGenerator.emitVerifyReturnedTypeToBe(returnType());
	}



	// enumerating

	public void childrenMap (
			final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{
		//  Map my children through the (destructive) transformation
		//  specified by aBlock.  Answer the receiver.

		_arguments = new ArrayList<AvailParseNode>(_arguments);
		for (int i = 0; i < _arguments.size(); i++)
		{
			_arguments.set(i, aBlock.value(_arguments.get(i)));
		}
	}



	// java printing

	public void printOnIndent (
			final StringBuilder aStream, 
			final int indent)
	{
		int underscores = 0;
		for (int charIndex = 1, _end1 = _message.name().tupleSize(); charIndex <= _end1; charIndex++)
		{
			if ((((char)(_message.name().tupleAt(charIndex).codePoint())) == '_'))
			{
				underscores++;
			}
		}
		if (underscores != _arguments.size())
		{
			aStream.append("<An AvailSendNode with the wrong number of arguments>");
			return;
		}
		int argIndex = 1;
		for (int charIndex = 1, _end2 = _message.name().tupleSize(); charIndex <= _end2; charIndex++)
		{
			final char chr = ((char)(_message.name().tupleAt(charIndex).codePoint()));
			if (chr == '_')
			{
				if (charIndex > 1)
				{
					aStream.append(' ');
				}
				//  No leading space for leading args
				_arguments.get(argIndex - 1).printOnIndentIn(
					aStream,
					indent,
					this);
				if ((charIndex < _message.name().tupleSize()))
				{
					aStream.append(' ');
				}
				//  No trailing space for trailing args
				argIndex++;
			}
			else
			{
				aStream.append(chr);
			}
		}
	}

	public void printOnIndentIn (
			final StringBuilder aStream, 
			final int indent, 
			final AvailParseNode outerNode)
	{
		aStream.append('(');
		printOnIndent(aStream, indent);
		aStream.append(')');
	}



	// testing

	public boolean isSend ()
	{
		return true;
	}



	// validation

	public AvailParseNode validateLocallyWithParentOuterBlocksInterpreter (
			final AvailParseNode parent, 
			final List<AvailBlockNode> outerBlocks, 
			final L2Interpreter anAvailInterpreter)
	{
		//  Ensure the node represented by the receiver is valid.  Raise an appropriate
		//  exception if it is not.  outerBlocks is a list of enclosing BlockNodes.
		//  Answer the receiver.
		//  Overridden to invoke the requires clauses in bottom-up order.

		List<AvailObject> argumentTypes;
		argumentTypes = new ArrayList<AvailObject>(_arguments.size());
		for (AvailParseNode arg : _arguments)
		{
			argumentTypes.add(arg.type());
		}
		anAvailInterpreter.validateRequiresClausesOfMessageSendArgumentTypes(message(), argumentTypes);
		return this;
	}





}
