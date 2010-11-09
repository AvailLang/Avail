/**
 * compiler/AvailParseNode.java
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

import com.avail.compiler.AvailBlockNode;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.compiler.AvailParseNode;
import com.avail.compiler.Transformer1;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.VoidDescriptor;
import com.avail.interpreter.levelTwo.L2Interpreter;
import java.util.ArrayList;
import java.util.List;
import static com.avail.descriptor.AvailObject.*;

public class AvailParseNode
{


	// accessing

	public AvailObject type ()
	{
		error("Subclass responsibility: type in Avail.AvailParseNode");
		return VoidDescriptor.voidObject();
	}



	// code generation

	public void emitEffectOn (
			final AvailCodeGenerator codeGenerator)
	{
		emitValueOn(codeGenerator);
		codeGenerator.emitPop();
	}

	public void emitValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		error("Subclass responsibility: emitValueOn: in Avail.AvailParseNode");
		return;
	}



	// enumerating

	public void childrenMap (
			final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{
		//  Map my children through the (destructive) transformation
		//  specified by aBlock.  Answer the receiver.
		//
		//  do nothing by default.


	}

	public AvailParseNode treeMap (
			final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{
		//  Map the tree through the (destructive) transformation specified by aBlock, children
		//  before parents.  Answer the resulting tree.

		childrenMap(new Transformer1<AvailParseNode, AvailParseNode> ()
		{
			public AvailParseNode value(final AvailParseNode child)
			{
				return child.treeMap(aBlock);
			}
		});
		return aBlock.value(this);
	}

	public AvailParseNode treeMapAlsoPassingParentAndEnclosingBlocksMyParentOuterBlockNodes (
			final Transformer3<AvailParseNode, AvailParseNode, List<AvailBlockNode>, AvailParseNode> aBlock, 
			final AvailParseNode parent, 
			final List<AvailBlockNode> outerNodes)
	{
		//  Map the tree through the (destructive) transformation specified by aBlock, children
		//  before parents.  The block takes three arguments; the node, its parent, and the list of
		//  enclosing block nodes.  Answer the resulting tree.

		childrenMap(new Transformer1<AvailParseNode, AvailParseNode> ()
		{
			public AvailParseNode value(final AvailParseNode child)
			{
				return child.treeMapAlsoPassingParentAndEnclosingBlocksMyParentOuterBlockNodes(
					aBlock,
					AvailParseNode.this,
					outerNodes);
			}
		});
		return aBlock.value(
			this,
			parent,
			outerNodes);
	}



	// java printing

	public void printOnIndent (
			final StringBuilder aStream, 
			final int indent)
	{
		error("Subclass responsibility: printOn:indent: in Avail.AvailParseNode");
		return;
	}

	public void printOnIndentIn (
			final StringBuilder aStream, 
			final int indent, 
			final AvailParseNode outerNode)
	{
		printOnIndent(aStream, indent);
	}

	public String toString ()
	{
		StringBuilder stringBuilder = new StringBuilder(100);
		printOnIndent(stringBuilder, 0);
		return stringBuilder.toString();
	}



	// testing

	public boolean isAssignment ()
	{
		return false;
	}

	public boolean isBlock ()
	{
		return false;
	}

	public boolean isDeclaration ()
	{
		//  Answer whether the receiver is a local variable declaration.

		return false;
	}

	public boolean isLabel ()
	{
		return false;
	}

	public boolean isLiteralNode ()
	{
		return false;
	}

	public boolean isSend ()
	{
		return false;
	}

	public boolean isSuperCast ()
	{
		return false;
	}

	public boolean isVariableUse ()
	{
		return false;
	}



	// validation

	public AvailParseNode validatedWithInterpreter (
			final L2Interpreter anAvailInterpreter)
	{
		//  Ensure the tree represented by the receiver is valid.  Raise an appropriate
		//  exception if it is not.

		final List<AvailBlockNode> initialBlockNodes = new ArrayList<AvailBlockNode>(3);
		return treeMapAlsoPassingParentAndEnclosingBlocksMyParentOuterBlockNodes(
			new Transformer3<AvailParseNode, AvailParseNode, List<AvailBlockNode>, AvailParseNode> ()
			{
				public AvailParseNode value(AvailParseNode node, AvailParseNode parent, List<AvailBlockNode> blockNodes)
				{
					return node.validateLocallyWithParentOuterBlocksInterpreter(parent, blockNodes, anAvailInterpreter);
				}
			},
			null,
			initialBlockNodes);
	}

	public AvailParseNode validateLocallyWithParentOuterBlocksInterpreter (
			final AvailParseNode parent, 
			final List<AvailBlockNode> outerBlocks, 
			final L2Interpreter anAvailInterpreter)
	{
		//  Ensure the node represented by the receiver is valid.  Raise an appropriate
		//  exception if it is not.  outerBlocks is a list of enclosing BlockNodes.
		//  Answer the receiver.

		return this;
	}





}
