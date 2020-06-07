/*
 * ListPhraseTypeDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.descriptor.types;

import com.avail.annotations.HideFieldInDebugger;
import com.avail.descriptor.JavaCompatibility.IntegerSlotsEnumJava;
import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AbstractDescriptor;
import com.avail.descriptor.representation.AbstractSlotsEnum;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.Mutability;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.numbers.IntegerDescriptor.fromInt;
import static com.avail.descriptor.representation.AvailObject.multiplier;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tupleFromArray;
import static com.avail.descriptor.types.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.types.ListPhraseTypeDescriptor.IntegerSlots.HASH_AND_MORE;
import static com.avail.descriptor.types.ListPhraseTypeDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.types.ListPhraseTypeDescriptor.ObjectSlots.EXPRESSION_TYPE;
import static com.avail.descriptor.types.ListPhraseTypeDescriptor.ObjectSlots.SUBEXPRESSIONS_TUPLE_TYPE;
import static com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE;
import static com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE;
import static com.avail.descriptor.types.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.types.TupleTypeDescriptor.tupleTypeForTypes;
import static com.avail.descriptor.types.TupleTypeDescriptor.tupleTypeFromTupleOfTypes;
import static com.avail.descriptor.types.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.descriptor.types.TupleTypeDescriptor.zeroOrOneOf;

/**
 * Define the structure and behavior of {@link PhraseKind#LIST_PHRASE list&#32;phrase&#32;types}.  List phrases are phrases that produce a tuple from a particular tuple of any-yielding phrases.  Correspondingly, list phrase types organize the part of the phrase type lattice related to list phrases.
 *
 * A list phrase type preserves more than the {@link A_Type#expressionType() yield&#32;type} of list phrases that comply with it.  It also preserves the types of the phrases in the tuple of subexpressions (i.e., not just the types that those phrases yield).  For example, a valid list phrase type might indicate that a complying list phrase has a tuple of subexpressions with between 2 and 5 elements, where the first subexpression must be a declaration and the other subexpressions are all assignment phrases.
 *
 * <p>This descriptor is also used for {@link PhraseKind#PERMUTED_LIST_PHRASE
 * permuted list phrase types}.  In that case, the subexpressions tuple type
 * is for the permuted subexpressions, <em>not</em> the order that they
 * lexically occur.  The permutation itself is not captured by the type.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ListPhraseTypeDescriptor
extends PhraseTypeDescriptor
{
	/**
	 * My slots of type {@linkplain Integer int}.
	 */
	public enum IntegerSlots implements IntegerSlotsEnumJava
	{
		/**
		 * The low 32 bits are used for caching the hash.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		public static final BitField
			HASH_OR_ZERO = new BitField(HASH_AND_MORE, 0, 32);

		static
		{
			assert PhraseTypeDescriptor.IntegerSlots.HASH_AND_MORE.ordinal()
				== HASH_AND_MORE.ordinal();
			assert PhraseTypeDescriptor.IntegerSlots.HASH_OR_ZERO
				.isSamePlaceAs(HASH_OR_ZERO);
		}
	}

	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * The type of value that this expression would produce.
		 */
		EXPRESSION_TYPE,

		/**
		 * The type of the tuple of subexpressions in a list phrase that
		 * complies with this list phrase type.  Note that for a permuted list
		 * phrase type, this tuple of subexpressions is already permuted; its
		 * elements are in the same order as in the {@link #EXPRESSION_TYPE}.
		 */
		SUBEXPRESSIONS_TUPLE_TYPE;

		static
		{
			assert PhraseTypeDescriptor.ObjectSlots.EXPRESSION_TYPE.ordinal()
				== EXPRESSION_TYPE.ordinal();
		}
	}

	@Override
	protected boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		// Only the hash part may change (be set lazily), not other bit fields.
		return e == HASH_AND_MORE;
	}

	/**
	 * {@inheritDoc}
	 *
	 * List phrase types are equal when they have the same expression type
	 * and same tuple type of subexpressions.
	 */
	@Override
	public boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another)
	{
		assert object.phraseKindIsUnder(LIST_PHRASE);
		return another.equalsListNodeType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * List phrase types are equal when they are of the same kind and have
	 * the same expression type and the same subexpressions tuple type.
	 * However, aPhraseType can't be a list phrase type like the receiver
	 * is.
	 */
	@Override
	public boolean o_EqualsPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		assert !aPhraseType.phraseKindIsUnder(LIST_PHRASE);
		return false;
	}

	@Override
	public boolean o_EqualsListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		assert aListNodeType.phraseKindIsUnder(LIST_PHRASE);
		return object.phraseKind() == aListNodeType.phraseKind()
			&& object.slot(EXPRESSION_TYPE).equals(
				aListNodeType.expressionType())
			&& object.slot(SUBEXPRESSIONS_TUPLE_TYPE).equals(
				aListNodeType.subexpressionsTupleType());
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		int hash = object.slot(HASH_OR_ZERO);
		if (hash == 0)
		{
			hash = object.slot(EXPRESSION_TYPE).hash();
			hash *= multiplier;
			hash -= kind.ordinal();
			hash *= multiplier;
			hash ^= object.slot(SUBEXPRESSIONS_TUPLE_TYPE).hash();
			hash *= multiplier;
			object.setSlot(HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override
	public A_Type o_SubexpressionsTupleType (final AvailObject object)
	{
		return object.slot(SUBEXPRESSIONS_TUPLE_TYPE);
	}

	@Override
	public boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfListNodeType(object);
	}

	@Override
	public boolean o_IsSupertypeOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return aListNodeType.phraseKindIsUnder(object.phraseKind())
			&& aListNodeType.expressionType().isSubtypeOf(
				object.slot(EXPRESSION_TYPE))
			&& aListNodeType.subexpressionsTupleType().isSubtypeOf(
				object.slot(SUBEXPRESSIONS_TUPLE_TYPE));
	}

	@Override
	public boolean o_IsSupertypeOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		assert !aPhraseType.phraseKindIsUnder(LIST_PHRASE);
		return false;
	}

	@Override
	public SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.LIST_NODE_TYPE;
	}

	@Override
	public A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		return another.typeIntersectionOfListNodeType(object);
	}

	@Override
	public A_Type o_TypeIntersectionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		// Intersection of two list phrase types.
		final @Nullable PhraseKind intersectionKind =
			object.phraseKind().commonDescendantWith(
				aListNodeType.phraseKind());
		if (intersectionKind == null)
		{
			return bottom();
		}
		assert intersectionKind.isSubkindOf(LIST_PHRASE);
		return createListNodeType(
			intersectionKind,
			object.expressionType().typeIntersection(
				aListNodeType.expressionType()),
			object.subexpressionsTupleType().typeIntersection(
				aListNodeType.subexpressionsTupleType()));
	}

	@Override
	public A_Type o_TypeIntersectionOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		final PhraseKind otherKind = aPhraseType.phraseKind();
		assert !otherKind.isSubkindOf(LIST_PHRASE);
		final @Nullable PhraseKind intersectionKind =
			otherKind.commonDescendantWith(object.phraseKind());
		if (intersectionKind == null)
		{
			return bottom();
		}
		assert intersectionKind.isSubkindOf(LIST_PHRASE);
		return createListNodeType(
			intersectionKind,
			object.expressionType().typeIntersection(
				aPhraseType.expressionType()),
			object.subexpressionsTupleType());
	}

	@Override
	public A_Type o_TypeUnion (final AvailObject object, final A_Type another)
	{
		return another.typeUnionOfListNodeType(object);
	}

	@Override
	public A_Type o_TypeUnionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		// Union of two list phrase types.
		final PhraseKind objectKind = object.phraseKind();
		final PhraseKind otherKind = aListNodeType.phraseKind();
		assert otherKind.isSubkindOf(LIST_PHRASE);
		final PhraseKind unionKind = objectKind.commonAncestorWith(
			otherKind);
		assert unionKind.isSubkindOf(LIST_PHRASE);
		return createListNodeType(
			unionKind,
			object.expressionType().typeUnion(aListNodeType.expressionType()),
			object.subexpressionsTupleType().typeUnion(
				aListNodeType.subexpressionsTupleType()));
	}

	@Override
	public A_Type o_TypeUnionOfPhraseType (
		final AvailObject object,
		final A_Type aPhraseType)
	{
		// Union of a list phrase type and a non-list phrase type is a
		// non-list phrase type.
		final PhraseKind objectKind = object.phraseKind();
		final PhraseKind otherKind = aPhraseType.phraseKind();
		final PhraseKind unionKind = objectKind.commonAncestorWith(
			otherKind);
		assert !unionKind.isSubkindOf(LIST_PHRASE);
		return unionKind.create(
			object.expressionType().typeUnion(aPhraseType.expressionType()));
	}

	@Override
	public void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write(object.phraseKind().jsonName);
		writer.write("expression type");
		object.slot(EXPRESSION_TYPE).writeTo(writer);
		writer.write("subexpressions tuple type");
		object.slot(SUBEXPRESSIONS_TUPLE_TYPE).writeTo(writer);
		writer.endObject();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		super.printObjectOnAvoidingIndent(
			object, builder, recursionMap, indent);
		builder.append(" (subexpressions tuple type=");
		object.subexpressionsTupleType().printOnAvoidingIndent(
			builder, recursionMap, indent + 1);
		builder.append(")");
	}

	/**
	 * Create a list phrase type matching zero or more occurrences of arbitrary
	 * phrases that yield the given type.
	 *
	 * @param type
	 * The type yielded by each element of the list phrase type.
	 * @return
	 * A list phrase type.
	 */
	public static A_Type zeroOrMoreList (final A_Type type)
	{
		return createListNodeType(zeroOrMoreOf(type));
	}

	/**
	 * Create a list phrase type matching zero or one occurrences of arbitrary
	 * phrases that yield the given type.
	 *
	 * @param type
	 * The type yielded by each element of the list phrase type.
	 * @return
	 * A list phrase type.
	 */
	public static A_Type zeroOrOneList (final A_Type type)
	{
		return createListNodeType(zeroOrOneOf(type));
	}

	/**
	 * Given an array of types, create the most general list phrase type which
	 * has a yield type matching those types as a tuple.
	 *
	 * @param types
	 * The array of types yielded by corresponding elements of the list phrase type.
	 * @return
	 * A list phrase type.
	 */
	public static A_Type list (final A_Type... types)
	{
		return createListNodeType(tupleTypeForTypes(types));
	}

	/**
	 * Given an array of types, create the most general list phrase type which
	 * has a yield type matching those types as a tuple, but where the size can
	 * vary from the given minimum size to the array's size.
	 *
	 * @param minimumSize
	 *        How small the list is permitted to be.
	 * @param types
	 *        The array of types yielded by corresponding elements of the list phrase type.
	 * @return
	 * A list phrase type.
	 */
	public static A_Type listPrefix (
		final int minimumSize,
		final A_Type... types)
	{
		return createListNodeType(
			tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor
					.inclusive(fromInt(minimumSize), fromInt(types.length)),
				tupleFromArray(types),
				bottom()));
	}

	/**
	 * Create a list phrase type with the given yield type and the given tuple
	 * type of expression types.  Canonize the resulting type by combining the
	 * mutual element constraints.
	 *
	 * @param kind
	 *        The {@link PhraseKind} to instantiate.  This must be {@link PhraseKind#LIST_PHRASE} or a subkind.
	 * @param yieldType
	 *        The tuple type that the list phrase will yield.
	 * @param subexpressionsTupleType
	 *        The tuple type of types of expression phrases that are the sub-phrases of the list phrase type.
	 * @return
	 * A canonized list phrase type.
	 */
	public static A_Type createListNodeType (
		final PhraseKind kind,
		final A_Type yieldType,
		final A_Type subexpressionsTupleType)
	{
		assert kind.isSubkindOf(LIST_PHRASE);
		assert yieldType.isTupleType();
		assert subexpressionsTupleType.isTupleType();
		yieldType.makeImmutable();
		subexpressionsTupleType.makeImmutable();
		final A_Type yieldTypesAsPhrases =
			tupleTypeFromTupleOfTypes(
				yieldType,
				elementType ->
				{
					assert elementType != null;
					return PARSE_PHRASE.create(elementType);
				});
		final A_Type phraseTypesAsYields =
			tupleTypeFromTupleOfTypes(
				subexpressionsTupleType,
				subexpressionType ->
				{
//					assert subexpressionType != null;
					final AbstractDescriptor descriptorTraversed =
						subexpressionType.traversed().descriptor();
					assert descriptorTraversed
							instanceof PhraseTypeDescriptor
						|| descriptorTraversed
							instanceof BottomTypeDescriptor;
					return subexpressionType.expressionType();
				});
		return createListNodeTypeNoCheck(
			kind,
			yieldType.typeIntersection(phraseTypesAsYields),
			subexpressionsTupleType.typeIntersection(yieldTypesAsPhrases));
	}

	/**
	 * Create a list phrase type with the given tuple type of expression types.
	 *
	 * @param subexpressionsTupleType
	 *        The tuple type of types of expression phrases that are the sub-phrases of the list phrase type.
	 * @return
	 * A canonized list phrase type.
	 */
	public static A_Type createListNodeType (
		final A_Type subexpressionsTupleType)
	{
		assert subexpressionsTupleType.isTupleType();
		final A_Type phraseTypesAsYields =
			tupleTypeFromTupleOfTypes(
				subexpressionsTupleType,
				subexpressionType ->
				{
					assert subexpressionType != null;
					final AbstractDescriptor descriptorTraversed =
						subexpressionType.traversed().descriptor();
					assert descriptorTraversed
						instanceof PhraseTypeDescriptor
						|| descriptorTraversed
						instanceof BottomTypeDescriptor;
					return subexpressionType.expressionType();
				});
		return createListNodeTypeNoCheck(
			LIST_PHRASE, phraseTypesAsYields, subexpressionsTupleType);
	}

	/**
	 * Create a list phrase type with the given yield type and the given tuple
	 * type of expression types.  Assume the two types have already been made
	 * mutually canonical: They both represent constraints on the elements, so
	 * they should already be taking each other's restriction into account.
	 *
	 * @param listNodeEnumKind
	 *        The partially initialized value {@link PhraseKind#LIST_PHRASE}.
	 * @param yieldType
	 *        The tuple type that the list phrase will yield.
	 * @param subexpressionsTupleType
	 *        The tuple type of types of expression phrases that are the sub-phrases of the list phrase type.  For a permuted list phrase, this field relates to the permuted subexpressions, <em>not</em> the lexical order of subexpressions.  Thus, it is always in the same order as the yieldType.
	 * @return
	 * A list phrase type.
	 */
	static A_Type createListNodeTypeNoCheck (
		final PhraseKind listNodeEnumKind,
		final A_Type yieldType,
		final A_Type subexpressionsTupleType)
	{
		// Can't verify this, because LIST_NODE might not exist yet.
		// assert listNodeEnumKind.isSubkindOf(LIST_NODE);
		assert yieldType.isTupleType();
		assert subexpressionsTupleType.isTupleType();
		final AvailObject type = listNodeEnumKind.mutableDescriptor.create();
		type.setSlot(EXPRESSION_TYPE, yieldType.makeImmutable());
		type.setSlot(SUBEXPRESSIONS_TUPLE_TYPE, subexpressionsTupleType);
		return type;
	}

	/** A static inner type that delays initialization until first use. */
	private static final class Empty
	{
		/** The empty list phrase's type. */
		static final A_Type empty =
			createListNodeTypeNoCheck(
				LIST_PHRASE, tupleTypeForTypes(), tupleTypeForTypes()
			).makeShared();

		/** Hide constructor to avoid unintentional instantiation. */
		private Empty ()
		{
			// Avoid unintentional instantiation.
		}
	}

	/**
	 * Answer the empty list phrase's type.
	 *
	 * @return
	 * The {@linkplain ListPhraseTypeDescriptor list&#32;phrase&#32;type} for the empty list phrase.
	 */
	public static A_Type emptyListPhraseType ()
	{
		return Empty.empty;
	}

	/**
	 * Construct a new descriptor for this kind of list phrase type.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param kind
	 *            The {@link PhraseKind} of the new descriptor.
	 */
	ListPhraseTypeDescriptor (
		final Mutability mutability,
		final PhraseKind kind)
	{
		super(mutability, kind, ObjectSlots.class, IntegerSlots.class);
	}
}
