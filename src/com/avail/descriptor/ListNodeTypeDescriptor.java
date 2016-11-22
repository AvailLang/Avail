/**
 * ListNodeTypeDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.ListNodeTypeDescriptor.IntegerSlots.*;
import static com.avail.descriptor.ListNodeTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import java.util.IdentityHashMap;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.evaluation.Transformer1;
import com.avail.utility.json.JSONWriter;

/**
 * Define the structure and behavior of {@link ParseNodeKind#LIST_NODE list
 * phrase types}.  List phrases are phrases that produce a tuple from a
 * particular tuple of any-yielding phrases.  Correspondingly, list phrase types
 * organize the part of the phrase type lattice related to list phrases.
 *
 * <p>A list phrase type preserves more than the {@link A_Type#expressionType()
 * yield type} of list phrases that comply with it.  It also preserves the types
 * of the phrases in the tuple of subexpressions (i.e., not just the types that
 * those phrases yield).  For example, a valid list phrase type might indicate
 * that a complying list phrase has a tuple of subexpressions with between 2 and
 * 5 elements, where the first subexpression must be a declaration and the other
 * subexpressions are all assignment phrases.</p>
 *
 * <p>This descriptor is also used for {@link ParseNodeKind#PERMUTED_LIST_NODE
 * permuted list phrase types}.  In that case, the subexpressions tuple type
 * is for the permuted subexpressions, <em>not</em> the order that they
 * lexically occur.  The permutation itself is not captured by the type.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ListNodeTypeDescriptor
extends ParseNodeTypeDescriptor
{
	/**
	 * My slots of type {@linkplain Integer int}.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for caching the hash, and the upper 32 are
		 * for the parse node kind.
		 */
		@HideFieldInDebugger
		HASH_AND_KIND;

		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		static final BitField HASH_OR_ZERO = bitField(HASH_AND_KIND, 0, 32);

		/**
		 * The {@linkplain ParseNodeKind kind} of parse node, encoded as an
		 * {@code int}.
		 */
		@EnumField(describedBy=ParseNodeKind.class)
		static final BitField KIND = bitField(HASH_AND_KIND, 32, 32);

		static
		{
			assert ParseNodeTypeDescriptor.IntegerSlots.HASH_AND_KIND.ordinal()
				== HASH_AND_KIND.ordinal();
			assert ParseNodeTypeDescriptor.IntegerSlots.HASH_OR_ZERO
				.isSamePlaceAs(HASH_OR_ZERO);
			assert ParseNodeTypeDescriptor.IntegerSlots.KIND
				.isSamePlaceAs(KIND);
		}
	}

	/**
	 * My slots of type {@link AvailObject}.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
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
			assert ParseNodeTypeDescriptor.ObjectSlots.EXPRESSION_TYPE.ordinal()
				== EXPRESSION_TYPE.ordinal();
		}
	}

	@Override
	boolean allowsImmutableToMutableReferenceInField (
		final AbstractSlotsEnum e)
	{
		// Only the hash part may change (be set lazily), not the kind.
		return e == HASH_AND_KIND;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@linkplain ListNodeTypeDescriptor list phrase types} are equal when they
	 * have the same expression type and same tuple type of subexpressions.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		assert object.parseNodeKindIsUnder(LIST_NODE);
		return another.equalsListNodeType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * {@linkplain ListNodeTypeDescriptor list phrase types} are equal when they
	 * are of the same kind and have the same expression type and the same
	 * subexpressions tuple type.  However, aParseNodeType can't be a list
	 * phrase type like the receiver is.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_EqualsParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		assert !aParseNodeType.parseNodeKindIsUnder(LIST_NODE);
		return false;
 	}

	@Override
	boolean o_EqualsListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		assert aListNodeType.parseNodeKindIsUnder(LIST_NODE);
		return object.parseNodeKind() == aListNodeType.parseNodeKind()
			&& object.slot(EXPRESSION_TYPE).equals(
				aListNodeType.expressionType())
			&& object.slot(SUBEXPRESSIONS_TUPLE_TYPE).equals(
				aListNodeType.subexpressionsTupleType());
	}

	@Override
	int o_Hash (final AvailObject object)
	{
		int hash = object.slot(HASH_OR_ZERO);
		if (hash == 0)
		{
			hash = object.slot(EXPRESSION_TYPE).hash();
			hash *= multiplier;
			hash -= object.slot(KIND);
			hash *= multiplier;
			hash ^= object.slot(SUBEXPRESSIONS_TUPLE_TYPE).hash();
			hash *= multiplier;
			object.setSlot(HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override
	A_Type o_SubexpressionsTupleType (final AvailObject object)
	{
		return object.slot(SUBEXPRESSIONS_TUPLE_TYPE);
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfListNodeType(object);
	}

	@Override
	@AvailMethod
	boolean o_IsSupertypeOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		return aListNodeType.parseNodeKindIsUnder(object.parseNodeKind())
			&& aListNodeType.expressionType().isSubtypeOf(
				object.slot(EXPRESSION_TYPE))
			&& aListNodeType.subexpressionsTupleType().isSubtypeOf(
				object.slot(SUBEXPRESSIONS_TUPLE_TYPE));
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		assert !aParseNodeType.parseNodeKindIsUnder(LIST_NODE);
		return false;
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.LIST_NODE_TYPE;
	}

	@Override @AvailMethod
	A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		return another.typeIntersectionOfListNodeType(object);
	}

	@Override
	A_Type o_TypeIntersectionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		// Intersection of two list node types.
		final @Nullable ParseNodeKind intersectionKind =
			object.parseNodeKind().commonDescendantWith(
				aListNodeType.parseNodeKind());
		if (intersectionKind == null)
		{
			return BottomTypeDescriptor.bottom();
		}
		assert intersectionKind.isSubkindOf(LIST_NODE);
		return createListNodeType(
			intersectionKind,
			object.expressionType().typeIntersection(
				aListNodeType.expressionType()),
			object.subexpressionsTupleType().typeIntersection(
				aListNodeType.subexpressionsTupleType()));
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		final ParseNodeKind otherKind = aParseNodeType.parseNodeKind();
		assert !otherKind.isSubkindOf(LIST_NODE);
		final ParseNodeKind intersectionKind = otherKind.commonDescendantWith(
			object.parseNodeKind());
		if (intersectionKind == null)
		{
			return BottomTypeDescriptor.bottom();
		}
		assert intersectionKind.isSubkindOf(LIST_NODE);
		return createListNodeType(
			intersectionKind,
			object.expressionType().typeIntersection(
				aParseNodeType.expressionType()),
			object.subexpressionsTupleType());
	}

	@Override
	A_Type o_TypeUnion (final AvailObject object, final A_Type another)
	{
		return another.typeUnionOfListNodeType(object);
	}

	@Override
	A_Type o_TypeUnionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType)
	{
		// Union of two list node types.
		final ParseNodeKind objectKind = object.parseNodeKind();
		final ParseNodeKind otherKind = aListNodeType.parseNodeKind();
		assert otherKind.isSubkindOf(LIST_NODE);
		final ParseNodeKind unionKind = objectKind.commonAncestorWith(
			otherKind);
		assert unionKind.isSubkindOf(LIST_NODE);
		return createListNodeType(
			unionKind,
			object.expressionType().typeUnion(aListNodeType.expressionType()),
			object.subexpressionsTupleType().typeUnion(
				aListNodeType.subexpressionsTupleType()));
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		// Union of a list node type and a non-list parse node type is a
		// non-list node type.
		final ParseNodeKind objectKind = object.parseNodeKind();
		final ParseNodeKind otherKind = aParseNodeType.parseNodeKind();
		final ParseNodeKind unionKind = objectKind.commonAncestorWith(
			otherKind);
		assert !unionKind.isSubkindOf(LIST_NODE);
		return unionKind.create(
			object.expressionType().typeUnion(aParseNodeType.expressionType()));
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write(object.parseNodeKind().jsonName);
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
	 * Create a list phrase type with the given yield type and the given tuple
	 * type of expression types.  Canonize the resulting type by combining the
	 * mutual element constraints.
	 *
	 * @param kind
	 *        The {@link ParseNodeKind} to instantiate.  This must be {@link
	 *        ParseNodeKind#LIST_NODE} or a subkind.
	 * @param yieldType
	 *        The tuple type that the list phrase will yield.
	 * @param subexpressionsTupleType
	 *        The tuple type of types of expression phrases that are the
	 *        sub-phrases of the list phrase type.
	 * @return A canonized list phrase type.
	 */
	public static A_Type createListNodeType (
		final ParseNodeKind kind,
		final A_Type yieldType,
		final A_Type subexpressionsTupleType)
	{
		assert kind.isSubkindOf(LIST_NODE);
		assert yieldType.isTupleType();
		assert subexpressionsTupleType.isTupleType();
		final A_Type yieldTypesAsPhrases =
			TupleTypeDescriptor.mappingElementTypes(
				yieldType,
				new Transformer1<A_Type, A_Type>()
				{
					@Override
					public @Nullable A_Type value (
						final @Nullable A_Type elementType)
					{
						assert elementType != null;
						return PARSE_NODE.create(elementType);
					}
				});
		final A_Type phraseTypesAsYields =
			TupleTypeDescriptor.mappingElementTypes(
				subexpressionsTupleType,
				new Transformer1<A_Type, A_Type>()
				{
					@Override
					public @Nullable A_Type value (
						final @Nullable A_Type subexpressionType)
					{
						assert subexpressionType != null;
						final AbstractDescriptor descriptorTraversed =
							subexpressionType.traversed().descriptor;
						assert descriptorTraversed
								instanceof ParseNodeTypeDescriptor
							|| descriptorTraversed
								instanceof BottomTypeDescriptor;
						return subexpressionType.expressionType();
					}
				});
		return createListNodeTypeNoCheck(
			kind,
			yieldType.typeIntersection(phraseTypesAsYields),
			subexpressionsTupleType.typeIntersection(yieldTypesAsPhrases));
	}

	/**
	 * Create a list phrase type with the given yield type and the given tuple
	 * type of expression types.  Assume the two types have already been made
	 * mutually canonical: They both represent constraints on the elements, so
	 * they should already be taking each other's restriction into account.
	 *
	 * @param listNodeEnumKind
	 *        The partially initialized value {@link ParseNodeKind#LIST_NODE}.
	 * @param yieldType
	 *        The tuple type that the list phrase will yield.
	 * @param subexpressionsTupleType
	 *        The tuple type of types of expression phrases that are the
	 *        sub-phrases of the list phrase type.  For a permuted list phrase,
	 *        this field relates to the permuted subexpressions, <em>not</em>
	 *        the lexical order of subexpressions.  Thus, it is always in the
	 *        same order as the yieldType.
	 * @return A list phrase type.
	 */
	static A_Type createListNodeTypeNoCheck (
		final ParseNodeKind listNodeEnumKind,
		final A_Type yieldType,
		final A_Type subexpressionsTupleType)
	{
		// Can't verify this, because LIST_NODE might not exist yet.
		// assert listNodeEnumKind.isSubkindOf(LIST_NODE);
		assert yieldType.isTupleType();
		assert subexpressionsTupleType.isTupleType();

		final AvailObject type = listNodeTypeMutable.create();
		type.setSlot(KIND, listNodeEnumKind.ordinal());
		type.setSlot(EXPRESSION_TYPE, yieldType.makeImmutable());
		type.setSlot(SUBEXPRESSIONS_TUPLE_TYPE, subexpressionsTupleType);
		return type;
	}

	/** The empty list phrase's type. */
	public static A_Type empty()
	{
		return createListNodeTypeNoCheck(
			LIST_NODE,
			TupleTypeDescriptor.forTypes(),
			TupleTypeDescriptor.forTypes());
	}

	/**
	 * Construct a new {@link ListNodeTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ListNodeTypeDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, IntegerSlots.class);
	}

	/** The mutable {@link ListNodeTypeDescriptor}. */
	private static final ListNodeTypeDescriptor listNodeTypeMutable =
		new ListNodeTypeDescriptor(Mutability.MUTABLE);

	@Override
	ListNodeTypeDescriptor mutable ()
	{
		return listNodeTypeMutable;
	}

	/** The shared {@link ListNodeTypeDescriptor}. */
	private static final ListNodeTypeDescriptor shared =
		new ListNodeTypeDescriptor(Mutability.SHARED);

	@Override
	ListNodeTypeDescriptor immutable ()
	{
		// There is no immutable descriptor.
		return shared;
	}

	@Override
	ListNodeTypeDescriptor shared ()
	{
		return shared;
	}
}
