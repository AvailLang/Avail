/**
 * SuperCastNodeDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.SUPER_CAST_NODE;
import static com.avail.descriptor.SuperCastNodeDescriptor.ObjectSlots.EXPRESSION;
import static com.avail.descriptor.SuperCastNodeDescriptor.ObjectSlots.TYPE_FOR_LOOKUP;

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

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append("«(");
		builder.append(object.expression());
		builder.append(" :: ");
		builder.append(object.superUnionType());
		builder.append(")»");
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<A_Phrase> aBlock)
	{
		aBlock.value(object.slot(EXPRESSION));
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
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.slot(EXPRESSION).emitValueOn(codeGenerator);
	}

	/**
	 * Answer the expression producing the actual value.
	 */
	@Override
	A_Phrase o_Expression (final AvailObject object)
	{
		return object.slot(EXPRESSION);
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

	@Override @AvailMethod
	boolean o_EqualsParseNode (
		final AvailObject object,
		final A_Phrase aParseNode)
	{
		return !aParseNode.isMacroSubstitutionNode()
			&& object.parseNodeKind().equals(aParseNode.parseNodeKind())
			&& object.expression().equals(aParseNode.expression())
			&& object.superUnionType().equals(aParseNode.superUnionType());
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

	@Override
	boolean o_HasSuperCast (final AvailObject object)
	{
		return true;
	}

	@Override
	ParseNodeKind o_ParseNodeKind (final AvailObject object)
	{
		return SUPER_CAST_NODE;
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override
	A_Type o_SuperUnionType (final AvailObject object)
	{
		return object.slot(TYPE_FOR_LOOKUP);
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.SUPER_CAST_PHRASE;
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
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
	 * @param expression
	 *        The base expression.
	 * @param superUnionType
	 *        The type to combine via a {@link A_Type#typeUnion(A_Type) type
	 *        union} with the type of the actual runtime value produced by the
	 *        expression, in order to look up the method.
	 * @return The resulting super cast node.
	 */
	public static AvailObject create (
		final A_Phrase expression,
		final A_Type superUnionType)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(EXPRESSION, expression);
		instance.setSlot(TYPE_FOR_LOOKUP, superUnionType);
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
		super(
			mutability, TypeTag.SUPER_CAST_PHRASE_TAG, ObjectSlots.class, null);
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
