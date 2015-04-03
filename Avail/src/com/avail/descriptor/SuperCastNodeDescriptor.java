/**
 * SuperCastNodeDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * modification, are permitted provided that the following conditions are met:
 * Redistribution and use in source and binary forms, with or without
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

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.SuperCastNodeDescriptor.ObjectSlots.*;
import java.util.*;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.utility.evaluation.*;
import com.avail.utility.json.JSONWriter;

/**
 * My instances represent {@linkplain ParseNodeDescriptor parse nodes} which
 * are elements of recursive {@linkplain ListNodeDescriptor list nodes} holding
 * arguments to a (super) {@linkplain SendNodeDescriptor send node}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class SuperCastNodeDescriptor
extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The expression producing the actual value.
		 */
		EXPRESSION,

		/**
		 * The static type used to look up this argument in the enclosing
		 * (super) {@linkplain SendNodeDescriptor send node}.
		 */
		TYPE_FOR_LOOKUP
	}

	/**
	 * Answer the expression producing the actual value.
	 */
	@Override
	A_Phrase o_Expression (final AvailObject object)
	{
		return object.slot(EXPRESSION);
	}

	@Override
	A_Type o_TypeForLookup (final AvailObject object)
	{
		return object.slot(TYPE_FOR_LOOKUP);
	}

	/**
	 * Answer the lookup type to ensure polymorphic macro substitutions happen
	 * the right way.
	 */
	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		return object.slot(TYPE_FOR_LOOKUP);
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		builder.append("«(");
		builder.append(object.expression());
		builder.append(" :: ");
		builder.append(object.typeForLookup());
		builder.append(")»");
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		int h = 0x29490D69;
		h ^= object.slot(EXPRESSION).hash();
		h *= multiplier;
		h ^= object.slot(TYPE_FOR_LOOKUP).hash();
		return h;
	}

	@Override @AvailMethod
	boolean o_EqualsParseNode (
		final AvailObject object,
		final A_Phrase aParseNode)
	{
		return !aParseNode.isMacroSubstitutionNode()
			&& object.parseNodeKind().equals(aParseNode.parseNodeKind())
			&& object.expression().equals(aParseNode.expression())
			&& object.typeForLookup().equals(aParseNode.typeForLookup());
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.slot(EXPRESSION).emitValueOn(codeGenerator);
	}

	@Override
	void o_EmitForSuperSendOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		// This is a super-cast.  Push the value, then push the explicit type.
		object.slot(EXPRESSION).emitValueOn(codeGenerator);
		codeGenerator.emitPushLiteral(object.slot(TYPE_FOR_LOOKUP));
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> aBlock)
	{
		final A_Phrase expression =
			aBlock.valueNotNull(object.slot(EXPRESSION));
		object.setSlot(EXPRESSION, expression);
	}


	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<A_Phrase> aBlock)
	{
		aBlock.value(object.slot(EXPRESSION));
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	/**
	 * Create a new {@code ListNodeDescriptor list node} with one more parse
	 * node added to the end of the list.
	 *
	 * @param object
	 *        The list node to extend.
	 * @param newParseNode
	 *        The parse node to append.
	 * @return
	 *         A new {@code ListNodeDescriptor list node} with the parse node
	 *         appended.
	 */
	@Override @AvailMethod
	A_Phrase o_CopyWith (
		final AvailObject object,
		final A_Phrase newParseNode)
	{
		final A_Phrase expression = object.slot(EXPRESSION);
		final A_Type typeForLookup = object.slot(TYPE_FOR_LOOKUP);
		return SuperCastNodeDescriptor.create(expression, typeForLookup);
	}

	@Override
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		return SUPER_CAST_NODE;
	}

	@Override
	boolean o_HasSuperCast (final AvailObject object)
	{
		return true;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("super cast phrase");
		writer.write("expression");
		object.slot(EXPRESSION).writeTo(writer);
		writer.write("type to lookup");
		object.slot(TYPE_FOR_LOOKUP).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("list phrase");
		writer.write("expression");
		object.slot(EXPRESSION).writeSummaryTo(writer);
		writer.write("type to lookup");
		object.slot(TYPE_FOR_LOOKUP).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new {@linkplain SuperCastNodeDescriptor super cast node} from
	 * the given {@linkplain ParseNodeDescriptor phrase} and {@linkplain
	 * TypeDescriptor type} with which to perform a method lookup.
	 *
	 * @param expression The base expression.
	 * @param typeForLookup The type with which to look up the method.
	 * @return The resulting super cast node.
	 */
	public static AvailObject create (
		final A_Phrase expression,
		final A_Type typeForLookup)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(EXPRESSION, expression);
		instance.setSlot(TYPE_FOR_LOOKUP, typeForLookup);
		instance.makeShared();
		return instance;
	}

	/**
	 * Construct a new {@link SuperCastNodeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private SuperCastNodeDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, null);
	}

	/** The mutable {@link SuperCastNodeDescriptor}. */
	private static final SuperCastNodeDescriptor mutable =
		new SuperCastNodeDescriptor(Mutability.MUTABLE);

	@Override
	SuperCastNodeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link SuperCastNodeDescriptor}. */
	private static final SuperCastNodeDescriptor shared =
		new SuperCastNodeDescriptor(Mutability.SHARED);

	@Override
	SuperCastNodeDescriptor shared ()
	{
		return shared;
	}
}
