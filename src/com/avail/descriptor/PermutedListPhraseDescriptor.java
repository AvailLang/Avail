/*
 * PermutedListNodeDescriptor.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.PERMUTED_LIST_PHRASE;
import static com.avail.descriptor.PermutedListPhraseDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForTypes;

/**
 * My instances represent {@linkplain PhraseDescriptor phrases} which will
 * generate <em>permuted</em> tuples at runtime.  The elements still have to be
 * generated in their lexical order, but an {@link L1Operation#L1Ext_doPermute}
 * changes their order while they're still on the stack (before being made into
 * a tuple or passed as the top level arguments in a send).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class PermutedListPhraseDescriptor
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
		 * The {@linkplain ListPhraseDescriptor list phrase} to permute when
		 * generating level one nybblecodes.
		 */
		LIST,

		/**
		 * The permutation to apply to the list phrase when generating level one
		 * nybblecodes.
		 */
		PERMUTATION,

		/**
		 * A cache of the permuted list's type.
		 */
		EXPRESSION_TYPE;
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == EXPRESSION_TYPE;
	}

	/**
	 * Lazily compute and install the expression type of the specified permuted
	 * list phrase.
	 *
	 * @param object An object.
	 * @return A type.
	 */
	private static A_Type expressionType (final AvailObject object)
	{
		A_Type expressionType = object.mutableSlot(EXPRESSION_TYPE);
		if (expressionType.equalsNil())
		{
			final A_Type originalTupleType = object.slot(LIST).expressionType();
			final A_Tuple permutation = object.slot(PERMUTATION);
			final int size = permutation.tupleSize();
			assert originalTupleType.sizeRange().lowerBound().extractInt()
				== size;
			final A_Type [] adjustedTypes = new A_Type [size];
			for (int i = 1; i <= size; i++)
			{
				adjustedTypes[permutation.tupleIntAt(i) - 1] =
					originalTupleType.typeAtIndex(i);
			}
			expressionType = tupleTypeForTypes(adjustedTypes);
			object.setMutableSlot(EXPRESSION_TYPE, expressionType.makeShared());
		}
		return expressionType;
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append("Permute(");
		builder.append(object.slot(LIST));
		builder.append(", ");
		builder.append(object.slot(PERMUTATION));
		builder.append(")");
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		action.value(object.slot(LIST));
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		object.setSlot(LIST, transformer.valueNotNull(object.slot(LIST)));
	}

	@Override
	void o_EmitAllValuesOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.slot(LIST).emitAllValuesOn(codeGenerator);
		codeGenerator.emitPermute(object.tokens(), object.permutation());
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		object.slot(LIST).emitAllValuesOn(codeGenerator);
		final A_Tuple permutation = object.slot(PERMUTATION);
		codeGenerator.emitPermute(object.tokens(), permutation);
		codeGenerator.emitMakeTuple(object.tokens(), permutation.tupleSize());
	}

	@Override @AvailMethod
	boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return !aPhrase.isMacroSubstitutionNode()
			&& object.phraseKind().equals(aPhrase.phraseKind())
			&& object.list().equals(aPhrase.list())
			&& object.permutation().equals(aPhrase.permutation());
	}

	@Override
	A_Phrase o_ExpressionAt (final AvailObject object, final int index)
	{
		// DON'T transform the index.
		return object.slot(LIST).expressionAt(index);
	}

	@Override
	int o_ExpressionsSize (final AvailObject object)
	{
		return object.slot(LIST).expressionsSize();
	}

	@Override @AvailMethod
	A_Tuple o_ExpressionsTuple (final AvailObject object)
	{
		return object.slot(LIST).expressionsTuple();
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return expressionType(object);
			}
		}
		return expressionType(object);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return (object.slot(LIST).hash() ^ 0xC8FC27B2)
			+ object.slot(PERMUTATION).hash();
	}

	@Override
	boolean o_HasSuperCast (final AvailObject object)
	{
		return object.slot(LIST).hasSuperCast();
	}

	@Override
	A_Phrase o_LastExpression (final AvailObject object)
	{
		// DON'T transform the index.
		return object.slot(LIST).lastExpression();
	}

	@Override
	A_Phrase o_List (final AvailObject object)
	{
		return object.slot(LIST);
	}

	@Override
	PhraseKind o_PhraseKind (final AvailObject object)
	{
		return PERMUTED_LIST_PHRASE;
	}

	@Override
	A_Tuple o_Permutation (final AvailObject object)
	{
		return object.slot(PERMUTATION);
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.PERMUTED_LIST_PHRASE;
	}

	@Override
	void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override
	A_Phrase o_StripMacro (final AvailObject object)
	{
		// Strip away macro substitution phrases inside my recursive list
		// structure.  This has to be done recursively over list phrases because
		// of the way the "leaf" phrases are checked for grammatical
		// restrictions, but the "root" phrases are what get passed into
		// functions.
		final A_Phrase originalList = object.slot(LIST);
		final A_Phrase strippedList = originalList.stripMacro();
		if (strippedList.sameAddressAs(originalList))
		{
			// Nothing changed, so return the original permuted list.
			return object;
		}
		return newPermutedListNode(strippedList, object.slot(PERMUTATION));
	}

	@Override @AvailMethod
	A_Type o_SuperUnionType (final AvailObject object)
	{
		final A_Phrase list = object.slot(LIST);
		final A_Type listSuperUnionType = list.superUnionType();
		if (listSuperUnionType.isBottom())
		{
			// It doesn't contain a supercast, so answer bottom.
			return listSuperUnionType;
		}
		final A_Tuple permutation = object.slot(PERMUTATION);
		final int size = list.expressionsSize();
		final A_Type [] types = new A_Type[size];
		for (int i = 1; i <= size; i++)
		{
			final A_Type t = listSuperUnionType.typeAtIndex(i);
			final int index = permutation.tupleIntAt(i);
			types[index - 1] = t;
		}
		return tupleTypeForTypes(types);
	}

	@Override
	A_Tuple o_Tokens (final AvailObject object)
	{
		return object.slot(LIST).tokens();
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
		writer.write("permuted list phrase");
		writer.write("list");
		object.slot(LIST).writeTo(writer);
		writer.write("permutation");
		object.slot(PERMUTATION).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("permuted list phrase");
		writer.write("list");
		object.slot(LIST).writeSummaryTo(writer);
		writer.write("permutation");
		object.slot(PERMUTATION).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new permuted list phrase from the given {@linkplain
	 * ListPhraseDescriptor list phrase} and {@linkplain TupleDescriptor
	 * permutation}.
	 *
	 * @param list
	 *        The list phrase to wrap.
	 * @param permutation
	 *        The permutation to perform on the list phrase's elements.
	 * @return The resulting permuted list phrase.
	 */
	public static AvailObject newPermutedListNode (
		final A_Phrase list,
		final A_Tuple permutation)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(LIST, list);
		instance.setSlot(PERMUTATION, permutation);
		instance.setSlot(EXPRESSION_TYPE, nil);
		instance.makeShared();
		return instance;
	}

	/**
	 * Construct a new {@code PermutedListPhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private PermutedListPhraseDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.PERMUTED_LIST_PHRASE_TAG,
			ObjectSlots.class,
			null);
	}

	/** The mutable {@link PermutedListPhraseDescriptor}. */
	private static final PermutedListPhraseDescriptor mutable =
		new PermutedListPhraseDescriptor(Mutability.MUTABLE);

	@Override
	PermutedListPhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link PermutedListPhraseDescriptor}. */
	private static final PermutedListPhraseDescriptor shared =
		new PermutedListPhraseDescriptor(Mutability.SHARED);

	@Override
	PermutedListPhraseDescriptor shared ()
	{
		return shared;
	}
}
