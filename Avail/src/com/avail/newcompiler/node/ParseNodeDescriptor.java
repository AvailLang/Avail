/**
 * com.avail.descriptor.parser/ParseNodeDescriptor.java
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

package com.avail.newcompiler.node;

import java.util.List;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.Transformer1;

/**
 * I'm used to implement the abstract notion of parse nodes.  All concrete parse
 * nodes are below me in the hierarchy.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public abstract class ParseNodeDescriptor extends Descriptor
{

	/**
	 * Construct a new {@link ParseNodeDescriptor}.
	 *
	 * @param isMutable Whether the descriptor being constructed represents
	 *                  mutable objects or not.
	 */
	public ParseNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * Return the parse node's expression type, which is the type of object that
	 * will be produced by this parse node.
	 *
	 * @return The {@link TypeDescriptor type} of the {@link AvailObject} that
	 *         will be produced by this parse node.
	 */
	@Override
	public abstract AvailObject o_ExpressionType (final AvailObject object);

	/**
	 * {@link ParseNodeDescriptor parse nodes} must implement {@link
	 * AbstractDescriptor#o_Hash(AvailObject) hash}.
	 */
	@Override
	public abstract int o_Hash (AvailObject object);

	/**
	 * {@link ParseNodeDescriptor parse nodes} must implement {@link
	 * AbstractDescriptor#o_Equals(AvailObject) equals}.
	 */
	@Override
	public abstract boolean o_Equals (
		AvailObject object,
		AvailObject another);

	/**
	 * Emit the effect of this node.  By default that means to emit the value of
	 * the node, then to pop the unwanted value from the stack.
	 *
	 * @param object The parse node.
	 * @param codeGenerator Where to emit the code.
	 */
	@Override
	public void o_EmitEffectOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.emitValueOn(codeGenerator);
		codeGenerator.emitPop();
	}

	/**
	 * Emit the value of this node.  That means emit a sequence of instructions
	 * that will cause this node's value to end up on the stack.
	 *
	 * @param object The parse node.
	 * @param codeGenerator Where to emit the code.
	 */
	@Override
	public abstract void o_EmitValueOn (
		AvailObject object,
		AvailCodeGenerator codeGenerator);

	/**
	 * Visit and transform every node constituting this parse tree.  Map this
	 * {@linkplain ParseNodeDescriptor parse node}'s children through the
	 * (destructive) transformation specified by aBlock, assigning them back
	 * into my slots.
	 *
	 * @param object The {@linkplain ParseNodeDescriptor parse node} to
	 *               transform.
	 * @param aBlock The {@link Transformer1 transformation} through which to
	 *               map this parse node's children.
	 */
	@Override
	public abstract void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock);


	/**
	 * Map the tree through the (destructive) transformation specified by
	 * aBlock, children before parents. Answer the resulting tree.
	 *
	 * @param object The current {@linkplain ParseNodeDescriptor parse node}.
	 * @param aBlock The destructive transformation to apply.
	 * @return The resulting transformed tree.
	 */
	public static AvailObject treeMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		object.childrenMap(
			new Transformer1<AvailObject, AvailObject>()
			{
				@Override
				public AvailObject value (final AvailObject child)
				{
					return treeMap(child, aBlock);
				}
			});
		return aBlock.value(object);
	}

	/**
	 * Validate this node, throwing an exception if there is a problem.
	 *
	 * @param object
	 * @param parent
	 * @param outerBlocks
	 * @param anAvailInterpreter
	 */
	@Override
	public abstract void o_ValidateLocally (
		final AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter);

	@Override
	public int maximumIndent ()
	{
		return Integer.MAX_VALUE;
	}
}
