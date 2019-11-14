/*
 * SuperCastNodeDescriptor.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.descriptor.parsing.A_Phrase;
import com.avail.descriptor.parsing.PhraseDescriptor;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.SUPER_CAST_PHRASE;
import static com.avail.descriptor.SuperCastPhraseDescriptor.ObjectSlots.EXPRESSION;
import static com.avail.descriptor.SuperCastPhraseDescriptor.ObjectSlots.TYPE_FOR_LOOKUP;

/**
 * My instances represent {@linkplain PhraseDescriptor phrases} which are
 * elements of recursive {@linkplain ListPhraseDescriptor list phrases} holding
 * arguments to a (super) {@linkplain SendPhraseDescriptor send phrase}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class SuperCastPhraseDescriptor
extends PhraseDescriptor
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
		 * (super) {@linkplain SendPhraseDescriptor send phrase}.
		 */
		TYPE_FOR_LOOKUP
	}

	@Override
	protected void printObjectOnAvoidingIndent (
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
	protected void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		action.value(object.slot(EXPRESSION));
	}

	@Override @AvailMethod
	protected void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		final A_Phrase expression =
			transformer.valueNotNull(object.slot(EXPRESSION));
		object.setSlot(EXPRESSION, expression);
	}

	@Override @AvailMethod
	protected void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.slot(EXPRESSION).emitValueOn(codeGenerator);
	}

	@Override @AvailMethod
	protected boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return !aPhrase.isMacroSubstitutionNode()
			&& object.phraseKind().equals(aPhrase.phraseKind())
			&& object.expression().equals(aPhrase.expression())
			&& object.superUnionType().equals(aPhrase.superUnionType());
	}

	/**
	 * Answer the expression producing the actual value.
	 */
	@Override
	protected A_Phrase o_Expression (final AvailObject object)
	{
		return object.slot(EXPRESSION);
	}

	/**
	 * Answer the lookup type to ensure polymorphic macro substitutions happen
	 * the right way.
	 */
	@Override @AvailMethod
	protected A_Type o_ExpressionType (final AvailObject object)
	{
		return object.slot(TYPE_FOR_LOOKUP);
	}

	@Override @AvailMethod
	protected int o_Hash (final AvailObject object)
	{
		int h = 0x29490D69;
		h ^= object.slot(EXPRESSION).hash();
		h *= multiplier;
		h ^= object.slot(TYPE_FOR_LOOKUP).hash();
		return h;
	}

	@Override
	protected boolean o_HasSuperCast (final AvailObject object)
	{
		return true;
	}

	@Override
	protected PhraseKind o_PhraseKind (final AvailObject object)
	{
		return SUPER_CAST_PHRASE;
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.SUPER_CAST_PHRASE;
	}

	@Override
	protected void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected A_Type o_SuperUnionType (final AvailObject object)
	{
		return object.slot(TYPE_FOR_LOOKUP);
	}

	@Override
	protected A_Tuple o_Tokens (final AvailObject object)
	{
		return object.slot(EXPRESSION).tokens();
	}

	@Override @AvailMethod
	protected void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
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
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
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
	 * Create a new {@linkplain SuperCastPhraseDescriptor super cast phrase} from
	 * the given {@linkplain PhraseDescriptor phrase} and {@linkplain
	 * TypeDescriptor type} with which to perform a method lookup.
	 *
	 * @param expression
	 *        The base expression.
	 * @param superUnionType
	 *        The type to combine via a {@link A_Type#typeUnion(A_Type) type
	 *        union} with the type of the actual runtime value produced by the
	 *        expression, in order to look up the method.
	 * @return The resulting super cast phrase.
	 */
	public static AvailObject newSuperCastNode (
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
	 * Construct a new {@link SuperCastPhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private SuperCastPhraseDescriptor (final Mutability mutability)
	{
		super(
			mutability, TypeTag.SUPER_CAST_PHRASE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link SuperCastPhraseDescriptor}. */
	private static final SuperCastPhraseDescriptor mutable =
		new SuperCastPhraseDescriptor(Mutability.MUTABLE);

	@Override
	protected SuperCastPhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link SuperCastPhraseDescriptor}. */
	private static final SuperCastPhraseDescriptor shared =
		new SuperCastPhraseDescriptor(Mutability.SHARED);

	@Override
	protected SuperCastPhraseDescriptor shared ()
	{
		return shared;
	}
}
