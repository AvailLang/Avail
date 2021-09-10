/*
 * ListPhraseTypeDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
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
package com.avail.descriptor.types

import com.avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.combine4
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tupleFromArray
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfListNodeType
import com.avail.descriptor.types.A_Type.Companion.phraseKind
import com.avail.descriptor.types.A_Type.Companion.phraseKindIsUnder
import com.avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import com.avail.descriptor.types.A_Type.Companion.subexpressionsTupleType
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.A_Type.Companion.typeIntersectionOfListNodeType
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.A_Type.Companion.typeUnionOfListNodeType
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.inclusive
import com.avail.descriptor.types.ListPhraseTypeDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.types.ListPhraseTypeDescriptor.ObjectSlots.EXPRESSION_TYPE
import com.avail.descriptor.types.ListPhraseTypeDescriptor.ObjectSlots.SUBEXPRESSIONS_TUPLE_TYPE
import com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.mappingElementTypes
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrMoreOf
import com.avail.descriptor.types.TupleTypeDescriptor.Companion.zeroOrOneOf
import com.avail.serialization.SerializerOperation
import com.avail.utility.ifZero
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * Define the structure and behavior of
 * [list&#32;phrase&#32;types][PhraseKind.LIST_PHRASE].  List phrases are
 * phrases that produce a tuple from a particular tuple of any-yielding phrases.
 * Correspondingly, list phrase types organize the part of the phrase type
 * lattice related to list phrases.
 *
 * A list phrase type preserves more than the
 * [yield&#32;type][A_Type.phraseTypeExpressionType] of list phrases that comply
 * with it. It also preserves the types of the phrases in the tuple of
 * subexpressions (i.e., not just the types that those phrases yield).  For
 * example, a valid list phrase type might indicate that a complying list phrase
 * has a tuple of subexpressions with between 2 and 5 elements, where the first
 * subexpression must be a declaration and the other subexpressions are all
 * assignment phrases.
 *
 * This descriptor is also used for [ permuted list phrase
 * types][PhraseKind.PERMUTED_LIST_PHRASE].  In that case, the subexpressions
 * tuple type is for the permuted subexpressions, *not* the order that they
 * lexically occur.  The permutation itself is not captured by the type.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new descriptor for this kind of list phrase type.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param kind
 *   The [PhraseKind] of the new descriptor.
 */
class ListPhraseTypeDescriptor internal constructor(
	mutability: Mutability,
	kind: PhraseKind
) : PhraseTypeDescriptor(
	mutability, kind, ObjectSlots::class.java, IntegerSlots::class.java)
{
	/**
	 * My slots of type [int][Integer].
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for caching the hash.
		 */
		HASH_AND_MORE;

		companion object
		{
			/**
			 * The hash, or zero (`0`) if the hash has not yet been computed.
			 */
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)

			init
			{
				assert(PhraseTypeDescriptor.IntegerSlots.HASH_AND_MORE.ordinal
							== HASH_AND_MORE.ordinal)
				assert(PhraseTypeDescriptor.IntegerSlots.HASH_OR_ZERO
							.isSamePlaceAs(HASH_OR_ZERO))
			}
		}
	}

	/**
	 * My slots of type [AvailObject].
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The type of value that this expression would produce.
		 */
		EXPRESSION_TYPE,

		/**
		 * The type of the tuple of subexpressions in a list phrase that
		 * complies with this list phrase type.  Note that for a permuted list
		 * phrase type, this tuple of subexpressions is already permuted; its
		 * elements are in the same order as in the [EXPRESSION_TYPE].
		 */
		SUBEXPRESSIONS_TUPLE_TYPE;

		companion object
		{
			init
			{
				assert(PhraseTypeDescriptor.ObjectSlots.EXPRESSION_TYPE.ordinal
						== EXPRESSION_TYPE.ordinal)
			}
		}
	}

	// Only the hash part may change (be set lazily), not other bit fields.
	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum): Boolean =
			 e === IntegerSlots.HASH_AND_MORE

	/**
	 * {@inheritDoc}
	 *
	 * List phrase types are equal when they have the same expression type
	 * and same tuple type of subexpressions.
	 */
	override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject): Boolean
	{
		assert(self.phraseKindIsUnder(PhraseKind.LIST_PHRASE))
		return another.equalsListNodeType(self)
	}

	/**
	 * {@inheritDoc}
	 *
	 * List phrase types are equal when they are of the same kind and have
	 * the same expression type and the same subexpressions tuple type.
	 * However, aPhraseType can't be a list phrase type like the receiver
	 * is.
	 */
	override fun o_EqualsPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): Boolean
	{
		assert(!aPhraseType.phraseKindIsUnder(PhraseKind.LIST_PHRASE))
		return false
	}

	override fun o_EqualsListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): Boolean
	{
		assert(aListNodeType.phraseKindIsUnder(PhraseKind.LIST_PHRASE))
		return (self.phraseKind === aListNodeType.phraseKind
			&& self.slot(EXPRESSION_TYPE).equals(
				aListNodeType.phraseTypeExpressionType)
			&& self.slot(SUBEXPRESSIONS_TUPLE_TYPE).equals(
				aListNodeType.subexpressionsTupleType))
	}

	override fun o_Hash(self: AvailObject): Int =
		self.slot(HASH_OR_ZERO).ifZero {
			combine4(
				self.slot(EXPRESSION_TYPE).hash(),
				kind.ordinal,
				self.slot(SUBEXPRESSIONS_TUPLE_TYPE).hash(),
				0x6d386470
			).also { self.setSlot(HASH_OR_ZERO, it) }
		}

	override fun o_SubexpressionsTupleType(self: AvailObject): A_Type =
		self.slot(SUBEXPRESSIONS_TUPLE_TYPE)

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfListNodeType(self)

	override fun o_IsSupertypeOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type
	): Boolean =
		(aListNodeType.phraseKindIsUnder(self.phraseKind)
			&& aListNodeType.phraseTypeExpressionType.isSubtypeOf(
				self.slot(EXPRESSION_TYPE))
			&& aListNodeType.subexpressionsTupleType.isSubtypeOf(
				self.slot(SUBEXPRESSIONS_TUPLE_TYPE)))

	override fun o_IsSupertypeOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): Boolean
	{
		assert(!aPhraseType.phraseKindIsUnder(PhraseKind.LIST_PHRASE))
		return false
	}

	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.LIST_NODE_TYPE

	override fun o_TypeIntersection(self: AvailObject, another: A_Type): A_Type =
		another.typeIntersectionOfListNodeType(self)

	override fun o_TypeIntersectionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type
	{
		// Intersection of two list phrase types.
		val intersectionKind = self.phraseKind.commonDescendantWith(
			aListNodeType.phraseKind)
								?: return bottom
		assert(intersectionKind.isSubkindOf(PhraseKind.LIST_PHRASE))
		return createListPhraseType(
			intersectionKind,
			self.phraseTypeExpressionType.typeIntersection(
				aListNodeType.phraseTypeExpressionType),
			self.subexpressionsTupleType.typeIntersection(
				aListNodeType.subexpressionsTupleType))
	}

	override fun o_TypeIntersectionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type
	{
		val otherKind = aPhraseType.phraseKind
		assert(!otherKind.isSubkindOf(PhraseKind.LIST_PHRASE))
		val intersectionKind =
			otherKind.commonDescendantWith(self.phraseKind) ?: return bottom
		assert(intersectionKind.isSubkindOf(PhraseKind.LIST_PHRASE))
		return createListPhraseType(
			intersectionKind,
			self.phraseTypeExpressionType.typeIntersection(
				aPhraseType.phraseTypeExpressionType),
			self.subexpressionsTupleType)
	}

	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type =
		another.typeUnionOfListNodeType(self)

	override fun o_TypeUnionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type
	{
		// Union of two list phrase types.
		val objectKind = self.phraseKind
		val otherKind = aListNodeType.phraseKind
		assert(otherKind.isSubkindOf(PhraseKind.LIST_PHRASE))
		val unionKind = objectKind.commonAncestorWith(
			otherKind)
		assert(unionKind.isSubkindOf(PhraseKind.LIST_PHRASE))
		return createListPhraseType(
			unionKind,
			self.phraseTypeExpressionType.typeUnion(
				aListNodeType.phraseTypeExpressionType),
			self.subexpressionsTupleType.typeUnion(
				aListNodeType.subexpressionsTupleType))
	}

	override fun o_TypeUnionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type
	{
		// Union of a list phrase type and a non-list phrase type is a
		// non-list phrase type.
		val objectKind = self.phraseKind
		val otherKind = aPhraseType.phraseKind
		val unionKind = objectKind.commonAncestorWith(
			otherKind)
		assert(!unionKind.isSubkindOf(PhraseKind.LIST_PHRASE))
		return unionKind.create(
			self.phraseTypeExpressionType.typeUnion(
				aPhraseType.phraseTypeExpressionType))
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write(self.phraseKind.jsonName)
		writer.write("expression type")
		self.slot(EXPRESSION_TYPE).writeTo(writer)
		writer.write("subexpressions tuple type")
		self.slot(SUBEXPRESSIONS_TUPLE_TYPE).writeTo(writer)
		writer.endObject()
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		super.printObjectOnAvoidingIndent(
			self, builder, recursionMap, indent)
		builder.append(" (subexpressions tuple type=")
		self.subexpressionsTupleType.printOnAvoidingIndent(
			builder, recursionMap, indent + 1)
		builder.append(")")
	}

	/** A static inner type that delays initialization until first use. */
	private object Empty
	{
		/** The empty list phrase's type. */
		val empty: A_Type = createListNodeTypeNoCheck(
			PhraseKind.LIST_PHRASE,
			tupleTypeForTypes(),
			tupleTypeForTypes()
		).makeShared()
	}

	companion object
	{
		/**
		 * Create a list phrase type matching zero or more occurrences of
		 * arbitrary phrases that yield the given type.
		 *
		 * @param type
		 *   The type yielded by each element of the list phrase type.
		 * @return
		 *   A list phrase type.
		 */
		fun zeroOrMoreList(type: A_Type): A_Type =
			createListPhraseType(zeroOrMoreOf(type))

		/**
		 * Create a list phrase type matching zero or one occurrences of
		 * arbitrary phrases that yield the given type.
		 *
		 * @param type
		 *   The type yielded by each element of the list phrase type.
		 * @return
		 *   A list phrase type.
		 */
		fun zeroOrOneList(type: A_Type): A_Type =
			createListPhraseType(zeroOrOneOf(type))

		/**
		 * Given an array of types, create the most general list phrase type
		 * which has a yield type matching those types as a tuple.
		 *
		 * @param types
		 *   The array of types yielded by corresponding elements of the list
		 *   phrase type.
		 * @return
		 *   A list phrase type.
		 */
		fun list(vararg types: A_Type): A_Type =
			createListPhraseType(tupleTypeForTypes(*types))

		/**
		 * Given an array of types, create the most general list phrase type
		 * which has a yield type matching those types as a tuple, but where the
		 * size can vary from the given minimum size to the array's size.
		 *
		 * @param minimumSize
		 *   How small the list is permitted to be.
		 * @param types
		 *   The array of types yielded by corresponding elements of the list
		 *   phrase type.
		 * @return
		 *   A list phrase type.
		 */
		fun listPrefix(minimumSize: Int, vararg types: A_Type): A_Type =
			createListPhraseType(
				tupleTypeForSizesTypesDefaultType(
					inclusive(fromInt(minimumSize), fromInt(types.size)),
					tupleFromArray(*types),
					bottom))

		/**
		 * Create a list phrase type with the given yield type and the given
		 * tuple type of expression types.  Canonize the resulting type by
		 * combining the mutual element constraints.
		 *
		 * @param kind
		 *   The [PhraseKind] to instantiate.  This must be
		 *   [PhraseKind.LIST_PHRASE] or a subkind.
		 * @param yieldType
		 *   The tuple type that the list phrase will yield.
		 * @param subexpressionsTupleType
		 *   The tuple type of types of expression phrases that are the
		 *   sub-phrases of the list phrase type.
		 * @return
		 *   A canonized list phrase type.
		 */
		fun createListPhraseType(
			kind: PhraseKind,
			yieldType: A_Type,
			subexpressionsTupleType: A_Type): A_Type
		{
			assert(kind.isSubkindOf(PhraseKind.LIST_PHRASE))
			assert(yieldType.isTupleType)
			assert(subexpressionsTupleType.isTupleType)
			yieldType.makeImmutable()
			subexpressionsTupleType.makeImmutable()
			val yieldTypesAsPhrases =
				mappingElementTypes(yieldType) {
				PhraseKind.PARSE_PHRASE.create(it)
			}
			val phraseTypesAsYields =
				mappingElementTypes(subexpressionsTupleType) {
					val descriptorTraversed = it.traversed().descriptor()
					assert(
						descriptorTraversed is PhraseTypeDescriptor
							|| descriptorTraversed is BottomTypeDescriptor)
					it.phraseTypeExpressionType
				}
			return createListNodeTypeNoCheck(
				kind,
				yieldType.typeIntersection(phraseTypesAsYields),
				subexpressionsTupleType.typeIntersection(yieldTypesAsPhrases))
		}

		/**
		 * Create a list phrase type with the given tuple type of expression
		 * types.
		 *
		 * @param subexpressionsTupleType
		 *   The tuple type of types of expression phrases that are the
		 *   sub-phrases of the list phrase type.
		 * @return
		 *   A canonized list phrase type.
		 */
		fun createListPhraseType(subexpressionsTupleType: A_Type): A_Type
		{
			assert(subexpressionsTupleType.isTupleType)
			val phraseTypesAsYields =
				mappingElementTypes(subexpressionsTupleType) {
					val descriptorTraversed = it.traversed().descriptor()
					assert(
						descriptorTraversed is PhraseTypeDescriptor
							|| descriptorTraversed is BottomTypeDescriptor)
					it.phraseTypeExpressionType
				}
			return createListNodeTypeNoCheck(
				PhraseKind.LIST_PHRASE,
				phraseTypesAsYields,
				subexpressionsTupleType)
		}

		/**
		 * Create a list phrase type with the given yield type and the given
		 * tuple type of expression types.  Assume the two types have already
		 * been made mutually canonical: They both represent constraints on the
		 * elements, so they should already be taking each other's restriction
		 * into account.
		 *
		 * @param listNodeEnumKind
		 *   The partially initialized value [PhraseKind.LIST_PHRASE].
		 * @param yieldType
		 *   The tuple type that the list phrase will yield.
		 * @param subexpressionsTupleType
		 *   The tuple type of types of expression phrases that are the
		 *   sub-phrases of the list phrase type.  For a permuted list phrase,
		 *   this field relates to the permuted subexpressions, *not* the
		 *   lexical order of subexpressions.  Thus, it is always in the same
		 *   order as the yieldType.
		 * @return
		 *   A list phrase type.
		 */
		fun createListNodeTypeNoCheck(
			listNodeEnumKind: PhraseKind,
			yieldType: A_Type,
			subexpressionsTupleType: A_Type): A_Type
		{
			// Can't verify this, because LIST_NODE might not exist yet.
			// assert(listNodeEnumKind.isSubkindOf(LIST_NODE))
			assert(yieldType.isTupleType)
			assert(subexpressionsTupleType.isTupleType)
			return listNodeEnumKind.mutableDescriptor.create {
				setSlot(EXPRESSION_TYPE, yieldType.makeImmutable())
				setSlot(SUBEXPRESSIONS_TUPLE_TYPE, subexpressionsTupleType)
			}
		}

		/**
		 * Answer the empty list phrase's type.
		 *
		 * @return
		 *   The [list&#32;phrase&#32;type][ListPhraseTypeDescriptor] for the
		 *   empty list phrase.
		 */
		fun emptyListPhraseType(): A_Type = Empty.empty
	}
}
