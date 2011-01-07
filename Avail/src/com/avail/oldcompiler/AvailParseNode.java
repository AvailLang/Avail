/**
 * compiler/AvailParseNode.java Copyright (c) 2010, Mark van Gulik. All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
import java.util.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

@Deprecated
public abstract class AvailParseNode
{

	// accessing

	public AvailObject expressionType ()
	{
		error("Subclass responsibility: type in Avail.AvailParseNode");
		return VoidDescriptor.voidObject();
	}

	// code generation

	public void emitEffectOn (final AvailCodeGenerator codeGenerator)
	{
		emitValueOn(codeGenerator);
		codeGenerator.emitPop();
	}

	public void emitValueOn (final AvailCodeGenerator codeGenerator)
	{
		error("Subclass responsibility: emitValueOn: in Avail.AvailParseNode");
		return;
	}

	// enumerating

	/**
	 * Map my children through the (destructive) transformation specified by
	 * aBlock.
	 */
	public abstract void childrenMap (
		final Transformer1<AvailParseNode, AvailParseNode> aBlock);


	/**
	 * Map the tree through the (destructive) transformation specified by
	 * aBlock, children before parents. Answer the root of the resulting tree.
	 *
	 * @param aBlock
	 * @return
	 */
	public final AvailParseNode treeMap (
		final Transformer1<AvailParseNode, AvailParseNode> aBlock)
	{

		childrenMap(new Transformer1<AvailParseNode, AvailParseNode>()
		{
			@Override
			public AvailParseNode value (final AvailParseNode child)
			{
				return child.treeMap(aBlock);
			}
		});
		return aBlock.value(this);
	}

	/**
	 * Map the tree through the (destructive) transformation specified by
	 * aBlock, children before parents. The block takes three arguments: the
	 * node, its parent, and the list of enclosing block nodes. Answer the
	 * resulting tree.
	 *
	 * @param aBlock What to do with each descendant.
	 * @param parent This node's parent.
	 * @param outerNodes The list of blocks surrounding this node, from
	 *                   outermost to innermost.
	 * @return A replacement for this node, possibly this node itself.
	 */
	public AvailParseNode treeMapWithParent (
		final Transformer3<
				AvailParseNode,
				AvailParseNode,
				List<AvailBlockNode>,
				AvailParseNode>
			aBlock,
		final AvailParseNode parent,
		final List<AvailBlockNode> outerNodes)
	{
		childrenMap(new Transformer1<AvailParseNode, AvailParseNode>()
		{
			@Override
			public AvailParseNode value (final AvailParseNode child)
			{
				return child.treeMapWithParent(
					aBlock,
					AvailParseNode.this,
					outerNodes);
			}
		});
		return aBlock.value(this, parent, outerNodes);
	}

	/**
	 * @param aStream
	 * @param indent
	 */
	public void printOnIndent (final StringBuilder aStream, final int indent)
	{
		error("Subclass responsibility: printOn:indent: in Avail.AvailParseNode");
		return;
	}

	/**
	 * @param aStream
	 * @param indent
	 * @param outerNode
	 */
	public void printOnIndentIn (
		final StringBuilder aStream,
		final int indent,
		final AvailParseNode outerNode)
	{
		printOnIndent(aStream, indent);
	}

	@Override
	public String toString ()
	{
		final StringBuilder stringBuilder = new StringBuilder(100);
		printOnIndent(stringBuilder, 0);
		return stringBuilder.toString();
	}


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
		// Answer whether the receiver is a local variable declaration.

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


	/**
	 * Ensure that the tree represented by this node is valid.  Throw an
	 * appropriate exception if it is not.
	 *
	 * @param anAvailInterpreter Used to run requires and returns clauses.
	 * @return This node or a replacement.
	 */
	public AvailParseNode validatedWithInterpreter (
		final L2Interpreter anAvailInterpreter)
	{
		final List<AvailBlockNode> initialBlockNodes =
			new ArrayList<AvailBlockNode>(3);
		return treeMapWithParent(
			new Transformer3<
				AvailParseNode,
				AvailParseNode,
				List<AvailBlockNode>,
				AvailParseNode>()
			{
				@Override
				public AvailParseNode value (
					final AvailParseNode node,
					final AvailParseNode parent,
					final List<AvailBlockNode> blockNodes)
				{
					return node.validateLocally(
						parent,
						blockNodes,
						anAvailInterpreter);
				}
			},
			null,
			initialBlockNodes);
	}

	/**
	 * Ensure the parse node represented by the receiver is valid. Throw a
	 * suitable exception if it is not.
	 *
	 * @param parent
	 *            This node's parent.
	 * @param outerBlocks
	 *            The chain of blocks that this node is in, outermost to
	 *            innermost.
	 * @param anAvailInterpreter
	 *            An interpreter with which to run the requires and returns
	 *            clauses of {@link MethodSignatureDescriptor methods}.
	 * @return The receiver.
	 */
	public AvailParseNode validateLocally (
		final AvailParseNode parent,
		final List<AvailBlockNode> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		return this;
	}

}
