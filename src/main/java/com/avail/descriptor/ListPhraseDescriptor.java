/*
 * ListNodeDescriptor.java
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ListPhraseDescriptor.ObjectSlots.EXPRESSIONS_TUPLE;
import static com.avail.descriptor.ListPhraseDescriptor.ObjectSlots.TUPLE_TYPE;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tupleFromList;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TupleTypeDescriptor.tupleTypeForTypes;

/**
 * My instances represent {@linkplain PhraseDescriptor phrases} which
 * will generate tuples directly at runtime.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ListPhraseDescriptor
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
		 * The {@linkplain TupleDescriptor tuple} of {@linkplain
		 * PhraseDescriptor phrases} that produce the values that will be
		 * aggregated into a tuple at runtime.
		 */
		EXPRESSIONS_TUPLE,

		/**
		 * The static type of the tuple that will be generated.
		 */
		TUPLE_TYPE;
	}

	@Override
	protected boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		return e == TUPLE_TYPE;
	}

	/**
	 * Lazily compute and install the expression type of the specified list
	 * phrase.
	 *
	 * @param object An object.
	 * @return A type.
	 */
	private static A_Type expressionType (final AvailObject object)
	{
		A_Type tupleType = object.mutableSlot(TUPLE_TYPE);
		if (tupleType.equalsNil())
		{
			final A_Tuple expressionsTuple = object.expressionsTuple();
			final List<A_Type> types = new ArrayList<>(
				expressionsTuple.tupleSize());
			for (final AvailObject expression : expressionsTuple)
			{
				final A_Type expressionType = expression.expressionType();
				if (expressionType.isBottom())
				{
					return bottom();
				}
				types.add(expressionType);
			}
			tupleType = tupleTypeForTypes(types);
			object.setMutableSlot(TUPLE_TYPE, tupleType.makeShared());
		}
		return tupleType;
	}

	@Override
	protected void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append("List(");
		boolean first = true;
		for (final A_BasicObject element : object.expressionsTuple())
		{
			if (!first)
			{
				builder.append(", ");
			}
			element.printOnAvoidingIndent(builder, recursionMap, indent + 1);
			first = false;
		}
		builder.append(")");
	}

	@Override @AvailMethod
	protected void o_ChildrenDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> action)
	{
		for (final AvailObject expression : object.expressionsTuple())
		{
			action.value(expression);
		}
	}

	@Override @AvailMethod
	protected void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<A_Phrase, A_Phrase> transformer)
	{
		A_Tuple expressions = object.expressionsTuple();
		for (int i = 1; i <= expressions.tupleSize(); i++)
		{
			expressions = expressions.tupleAtPuttingCanDestroy(
				i, transformer.valueNotNull(expressions.tupleAt(i)), true);
		}
		object.setSlot(EXPRESSIONS_TUPLE, expressions);
	}

	/**
	 * Create a new {@code ListPhraseDescriptor list phrase} with one more
	 * phrase added to the end of the list.
	 *
	 * @param object
	 *        The list phrase to extend.
	 * @param newPhrase
	 *        The phrase to append.
	 * @return
	 *         A new {@code ListPhraseDescriptor list phrase} with the phrase
	 *         appended.
	 */
	@Override @AvailMethod
	protected A_Phrase o_CopyWith (
		final AvailObject object,
		final A_Phrase newPhrase)
	{
		final A_Tuple oldTuple = object.slot(EXPRESSIONS_TUPLE);
		final A_Tuple newTuple = oldTuple.appendCanDestroy(
			newPhrase,
			true);
		return newListNode(newTuple);
	}

	@Override
	protected void o_EmitAllValuesOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Tuple childNodes = object.expressionsTuple();
		for (final A_Phrase expr : childNodes)
		{
			expr.emitValueOn(codeGenerator);
		}
	}

	@Override @AvailMethod
	protected void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		final A_Tuple childNodes = object.expressionsTuple();
		for (final A_Phrase expr : childNodes)
		{
			expr.emitValueOn(codeGenerator);
		}
		codeGenerator.emitMakeTuple(emptyTuple(), childNodes.tupleSize());
	}

	@Override @AvailMethod
	protected boolean o_EqualsPhrase (
		final AvailObject object,
		final A_Phrase aPhrase)
	{
		return !aPhrase.isMacroSubstitutionNode()
			&& object.phraseKind().equals(aPhrase.phraseKind())
			&& object.expressionsTuple().equals(aPhrase.expressionsTuple());
	}

	@Override
	protected A_Phrase o_ExpressionAt (final AvailObject object, final int index)
	{
		return object.slot(EXPRESSIONS_TUPLE).tupleAt(index);
	}

	@Override
	protected int o_ExpressionsSize (final AvailObject object)
	{
		return object.slot(EXPRESSIONS_TUPLE).tupleSize();
	}

	@Override @AvailMethod
	protected A_Tuple o_ExpressionsTuple (final AvailObject object)
	{
		return object.slot(EXPRESSIONS_TUPLE);
	}

	@Override @AvailMethod
	protected A_Type o_ExpressionType (final AvailObject object)
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
	protected int o_Hash (final AvailObject object)
	{
		return object.expressionsTuple().hash() ^ 0xC143E977;
	}

	@Override
	protected boolean o_HasSuperCast (final AvailObject object)
	{
		for (final A_Phrase phrase : object.slot(EXPRESSIONS_TUPLE))
		{
			if (phrase.hasSuperCast())
			{
				return true;
			}
		}
		return false;
	}

	@Override @AvailMethod
	protected boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		if (!super.o_IsInstanceOfKind(object, aType))
		{
			return false;
		}
		// Also check the list phrase type's subexpressions type.
		return !aType.isSubtypeOf(LIST_PHRASE.mostGeneralType())
			|| object.slot(EXPRESSIONS_TUPLE).isInstanceOf(
				aType.subexpressionsTupleType());
	}

	@Override
	protected A_Phrase o_LastExpression (final AvailObject object)
	{
		final A_Tuple tuple = object.slot(EXPRESSIONS_TUPLE);
		return tuple.tupleAt(tuple.tupleSize());
	}

	@Override
	protected void o_StatementsDo (
		final AvailObject object,
		final Continuation1NotNull<A_Phrase> continuation)
	{
		throw unsupportedOperationException();
	}

	@Override
	protected PhraseKind o_PhraseKind (final AvailObject object)
	{
		return LIST_PHRASE;
	}

	@Override
	protected A_Phrase o_StripMacro (final AvailObject object)
	{
		// Strip away macro substitution phrases inside my recursive list
		// structure.  This has to be done recursively over list phrases because
		// of the way the "leaf" phrases are checked for grammatical
		// restrictions, but the "root" phrases are what get passed into
		// functions.
		A_Tuple expressionsTuple =
			object.slot(EXPRESSIONS_TUPLE).makeImmutable();
		boolean anyStripped = false;
		for (int i = 1, end = expressionsTuple.tupleSize(); i <= end; i++)
		{
			final A_Phrase originalElement = expressionsTuple.tupleAt(i);
			final A_Phrase strippedElement = originalElement.stripMacro();
			if (!originalElement.equals(strippedElement))
			{
				expressionsTuple = expressionsTuple.tupleAtPuttingCanDestroy(
					i, strippedElement, true);
				anyStripped = true;
			}
		}
		if (anyStripped)
		{
			return newListNode(expressionsTuple);
		}
		return object;
	}

	@Override @AvailMethod
	protected A_Type o_SuperUnionType (final AvailObject object)
	{
		final A_Tuple expressions = object.slot(EXPRESSIONS_TUPLE);
		final int size = expressions.tupleSize();
		for (int i = 1; i <= size; i++)
		{
			final A_Type lookupType = expressions.tupleAt(i).superUnionType();
			if (!lookupType.isBottom())
			{
				// An element has a superunion type; build the tuple type.
				final A_Type [] types = new A_Type[size];
				Arrays.fill(types, 0, i - 1, bottom());
				types[i - 1] = lookupType;
				for (int j = i + 1; j <= size; j++)
				{
					types[j - 1] = expressions.tupleAt(j).superUnionType();
				}
				return tupleTypeForTypes(types);
			}
		}
		// The elements' superunion types were all bottom, so answer bottom.
		return bottom();
	}

	@Override @AvailMethod
	protected void o_ValidateLocally (
		final AvailObject object,
		final @Nullable A_Phrase parent)
	{
		// Do nothing.
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.LIST_PHRASE;
	}

	@Override
	protected A_Tuple o_Tokens (final AvailObject object)
	{
		final List<A_Token> tokens = new ArrayList<>();
		for (final A_Phrase expression : object.slot(EXPRESSIONS_TUPLE))
		{
			tokens.addAll(toList(expression.tokens()));
		}
		return tupleFromList(tokens);
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("list phrase");
		writer.write("expressions");
		object.slot(EXPRESSIONS_TUPLE).writeTo(writer);
		writer.endObject();
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("list phrase");
		writer.write("expressions");
		object.slot(EXPRESSIONS_TUPLE).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new list phrase from the given {@linkplain TupleDescriptor
	 * tuple} of {@linkplain PhraseDescriptor expressions}.
	 *
	 * @param expressions
	 *        The expressions to assemble into a list phrase.
	 * @return The resulting list phrase.
	 */
	public static AvailObject newListNode (final A_Tuple expressions)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(EXPRESSIONS_TUPLE, expressions);
		instance.setSlot(TUPLE_TYPE, nil);
		instance.makeShared();
		return instance;
	}

	/**
	 * Construct a new {@code ListPhraseDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ListPhraseDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.LIST_PHRASE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link ListPhraseDescriptor}. */
	private static final ListPhraseDescriptor mutable =
		new ListPhraseDescriptor(Mutability.MUTABLE);

	@Override
	protected ListPhraseDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link ListPhraseDescriptor}. */
	private static final ListPhraseDescriptor shared =
		new ListPhraseDescriptor(Mutability.SHARED);

	@Override
	protected ListPhraseDescriptor shared ()
	{
		return shared;
	}

	/** The empty {@link ListPhraseDescriptor list phrase}. */
	private static final AvailObject empty =
		newListNode(emptyTuple()).makeShared();

	/**
	 * Answer the empty list phrase.
	 *
	 * @return The empty list phrase.
	 */
	public static AvailObject emptyListNode ()
	{
		return empty;
	}
}
