/**
 * LiteralNodeDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.descriptor;

import java.util.List;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelTwo.L2Interpreter;
import com.avail.utility.*;

/**
 * My instances are occurrences of literals parsed from Avail source code.  At
 * the moment only strings and non-negative numbers are supported.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class LiteralNodeDescriptor
extends ParseNodeDescriptor
{
	/**
	 * A synthetic {@linkplain LiteralNodeDescriptor literal node} that
	 * represents a literal {@linkplain NullDescriptor#nullObject() null
	 * object}. Note that the literal null object is not available to an Avail
	 * programmer.
	 */
	private static AvailObject literalNullObject;

	/**
	 * Answer a synthetic {@linkplain LiteralNodeDescriptor literal node} that
	 * represents a literal {@linkplain NullDescriptor#nullObject() null
	 * object}. Note that the literal null object is not available to an Avail
	 * programmer
	 * .
	 * @return A literal node representing null.
	 */
	public static @NotNull AvailObject literalNullObject ()
	{
		return literalNullObject;
	}

	/**
	 * Create any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void createWellKnownObjects ()
	{
		final AvailObject token = LiteralTokenDescriptor.mutable().create();
		token.tokenType(TokenType.LITERAL);
		token.string(StringDescriptor.from("nil"));
		token.start(0);
		token.lineNumber(0);
		token.literal(NullDescriptor.nullObject());
		literalNullObject = fromToken(token).makeImmutable();
	}

	/**
	 * Destroy or reset any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void clearWellKnownObjects ()
	{
		literalNullObject = null;
	}

	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The token that was transformed into this literal.
		 */
		TOKEN,
	}

	/**
	 * Getter for field token.
	 */
	@Override @AvailMethod
	AvailObject o_Token (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.TOKEN);
	}


	@Override @AvailMethod
	AvailObject o_ExpressionType (final AvailObject object)
	{
		final AvailObject token = object.token();
		assert token.tokenType() == TokenType.LITERAL
			|| token.tokenType() == TokenType.SYNTHETIC_LITERAL;
		final AvailObject literal = token.literal();
		return InstanceTypeDescriptor.on(literal).makeImmutable();
	}

	@Override @AvailMethod
	AvailObject o_Kind (final AvailObject object)
	{
		return ParseNodeTypeDescriptor.ParseNodeKind.LITERAL_NODE.create(
			object.expressionType());
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return
			object.token().hash() ^ 0x9C860C0D;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final AvailObject another)
	{
		return object.kind().equals(another.kind())
			&& object.token().equals(another.token());
	}

	@Override @AvailMethod
	void o_EmitEffectOn (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		// Do nothing.
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final @NotNull AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		codeGenerator.emitPushLiteral(object.token().literal());
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final @NotNull AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		// Do nothing.
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final @NotNull AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		// Do nothing.
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final @NotNull AvailObject object,
		final AvailObject parent,
		final List<AvailObject> outerBlocks,
		final L2Interpreter anAvailInterpreter)
	{
		// Do nothing.
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
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
	 * Create a {@linkplain LiteralNodeDescriptor literal node} from a {@linkplain
	 * LiteralTokenDescriptor literal token}.
	 *
	 * @param token The token that describes the literal.
	 * @return The new literal node.
	 */
	public static AvailObject fromToken (final @NotNull AvailObject token)
	{
		assert token.isInstanceOfKind(Types.LITERAL_TOKEN.o());
		final AvailObject node = mutable().create();
		node.setSlot(ObjectSlots.TOKEN, token);
		node.makeImmutable();
		return node;
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
	private static final LiteralNodeDescriptor mutable =
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
	private static final LiteralNodeDescriptor immutable =
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
}
