/**
 * com.avail.newcompiler.node/LiteralNodeDescriptor.java
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
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.newcompiler.scanner.TokenDescriptor.TokenType;
import com.avail.utility.Transformer1;

/**
 * My instances are occurrences of literals parsed from Avail source code.  At
 * the moment only strings and non-negative numbers are supported.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class LiteralNodeDescriptor extends ParseNodeDescriptor
{

	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The token that was transformed into this literal.
		 */
		TOKEN,
	}

	/**
	 * Setter for field token.
	 */
	@Override
	public void o_Token (
		final AvailObject object,
		final AvailObject token)
	{
		object.objectSlotPut(ObjectSlots.TOKEN, token);
	}

	/**
	 * Getter for field token.
	 */
	@Override
	public AvailObject o_Token (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.TOKEN);
	}


	@Override
	public AvailObject o_ExpressionType (final AvailObject object)
	{
		final AvailObject token = object.token();
		assert token.tokenType() == TokenType.LITERAL;
		final AvailObject literal = token.literal();
		return literal.type().makeImmutable();
	}

	@Override
	public AvailObject o_Type (final AvailObject object)
	{
		return Types.literalNode.object();
	}

	@Override
	public AvailObject o_ExactType (final AvailObject object)
	{
		return Types.literalNode.object();
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return
			object.token().hash() ^ 0x9C860C0D;
	}

	@Override
	public boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.type().equals(another.type())
			&& object.token().equals(another.token());
	}

	@Override
	public void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.emitPushLiteral(object.token().literal());
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		object.token().literal().printOnAvoidingIndent(
			builder,
			recursionList,
			indent + 1);
	}



	/**
	 * Construct a new {@link LiteralNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public LiteralNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link LiteralNodeDescriptor}.
	 */
	private final static LiteralNodeDescriptor mutable =
		new LiteralNodeDescriptor(true);

	/**
	 * Answer the mutable {@link LiteralNodeDescriptor}.
	 *
	 * @return The mutable {@link LiteralNodeDescriptor}.
	 */
	public static LiteralNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link LiteralNodeDescriptor}.
	 */
	private final static LiteralNodeDescriptor immutable =
		new LiteralNodeDescriptor(false);

	/**
	 * Answer the immutable {@link LiteralNodeDescriptor}.
	 *
	 * @return The immutable {@link LiteralNodeDescriptor}.
	 */
	public static LiteralNodeDescriptor immutable ()
	{
		return immutable;
	}

	@Override
	public void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		// Do nothing.
	}

	@Override
	public void o_ValidateLocally (
		final AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		// Do nothing.
	}

}
