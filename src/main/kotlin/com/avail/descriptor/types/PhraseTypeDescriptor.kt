/*
 * PhraseTypeDescriptor.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.descriptor.types

import com.avail.descriptor.phrases.A_Phrase
import com.avail.descriptor.phrases.AssignmentPhraseDescriptor
import com.avail.descriptor.phrases.BlockPhraseDescriptor
import com.avail.descriptor.phrases.DeclarationPhraseDescriptor
import com.avail.descriptor.phrases.FirstOfSequencePhraseDescriptor
import com.avail.descriptor.phrases.ListPhraseDescriptor
import com.avail.descriptor.phrases.LiteralPhraseDescriptor
import com.avail.descriptor.phrases.PermutedListPhraseDescriptor
import com.avail.descriptor.phrases.ReferencePhraseDescriptor
import com.avail.descriptor.phrases.SendPhraseDescriptor
import com.avail.descriptor.phrases.SequencePhraseDescriptor
import com.avail.descriptor.phrases.SuperCastPhraseDescriptor
import com.avail.descriptor.phrases.VariableUsePhraseDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AbstractSlotsEnum
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.AvailObject.Companion.multiplier
import com.avail.descriptor.representation.BitField
import com.avail.descriptor.representation.IntegerEnumSlotDescriptionEnum
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.isSupertypeOfPhraseType
import com.avail.descriptor.types.A_Type.Companion.phraseKind
import com.avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import com.avail.descriptor.types.A_Type.Companion.subexpressionsTupleType
import com.avail.descriptor.types.A_Type.Companion.typeIntersection
import com.avail.descriptor.types.A_Type.Companion.typeIntersectionOfPhraseType
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.A_Type.Companion.typeUnionOfPhraseType
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import com.avail.descriptor.types.ListPhraseTypeDescriptor.Companion.createListNodeType
import com.avail.descriptor.types.ListPhraseTypeDescriptor.Companion.createListNodeTypeNoCheck
import com.avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.literalTokenType
import com.avail.descriptor.types.PhraseTypeDescriptor.IntegerSlots.Companion.HASH_OR_ZERO
import com.avail.descriptor.types.PhraseTypeDescriptor.ObjectSlots.EXPRESSION_TYPE
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType
import com.avail.serialization.SerializerOperation
import com.avail.utility.json.JSONWriter
import java.util.IdentityHashMap

/**
 * Define the structure and behavior of phrase types.  The phrase types
 * are all parameterized by expression type, but they also have a relationship
 * to each other based on a fiat hierarchy.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new descriptor for this kind of phrase type.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param kind
 *   The `PhraseKind` of the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or `null` if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots layout, or `null` if there are no integer slots.
 */
@Suppress("LeakingThis")
open class PhraseTypeDescriptor protected constructor(
	mutability: Mutability,
	/** The `PhraseKind` of instances that use this descriptor.  */
	protected val kind: PhraseKind,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum?>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum?>?
) : TypeDescriptor(
	mutability, kind.typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
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
			@JvmField
			val HASH_OR_ZERO = BitField(HASH_AND_MORE, 0, 32)
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
		EXPRESSION_TYPE
	}

	/**
	 * My hierarchy of kinds of phrases.
	 *
	 * @property jsonName
	 *   The JSON name of this type.
	 * @property parentKind
	 *   The kind of phrase that this kind is a child of.
	 * @property typeTag
	 *   The type tag associated with phrases of this kind.
	 *
	 * @constructor
	 * Construct a new `PhraseKind`.
	 *
	 * @param jsonName
	 *   The JSON name of this type.
	 * @param parentKind
	 *   The kind of phrase for which this is a subkind.
	 * @param typeTag
	 *   The type tag associated with phrases of this kind.
	 */
	enum class PhraseKind constructor(
		val jsonName: String,
		private val parentKind: PhraseKind?,
		val typeTag: TypeTag) : IntegerEnumSlotDescriptionEnum
	{
		/** The root phrase kind.  */
		PARSE_PHRASE("phrase type", null, TypeTag.PHRASE_TAG),

		/** The kind of a parse marker.  */
		MARKER_PHRASE(
			"marker phrase type", PARSE_PHRASE, TypeTag.MARKER_PHRASE_TAG),

		/** The abstract parent kind of all expression phrases.  */
		EXPRESSION_PHRASE(
			"expression phrase type", PARSE_PHRASE,
			TypeTag.EXPRESSION_PHRASE_TAG),

		/**
		 * The kind of an [assignment&#32;phrase][AssignmentPhraseDescriptor].
		 */
		ASSIGNMENT_PHRASE(
			"assignment phrase type", EXPRESSION_PHRASE,
			TypeTag.ASSIGNMENT_PHRASE_TAG),

		/** The kind of a [block&#32;phrase][BlockPhraseDescriptor].  */
		BLOCK_PHRASE(
			"block phrase type", EXPRESSION_PHRASE,
			TypeTag.BLOCK_PHRASE_TAG)
		{
			override fun mostGeneralYieldType(): A_Type =
				mostGeneralFunctionType()
		},

		/**
		 * The kind of a [literal&#32;phrase][LiteralPhraseDescriptor].
		 */
		LITERAL_PHRASE(
			"literal phrase type", EXPRESSION_PHRASE,
			TypeTag.LITERAL_PHRASE_TAG)
		{
			override fun mostGeneralYieldType(): A_Type = ANY.o
		},

		/**
		 * The kind of a [reference&#32;phrase][ReferencePhraseDescriptor].
		 */
		REFERENCE_PHRASE(
			"variable reference phrase type", EXPRESSION_PHRASE,
			TypeTag.REFERENCE_PHRASE_TAG)
		{
			override fun mostGeneralYieldType(): A_Type =
				mostGeneralVariableType()
		},

		/**
		 * The kind of a [super&#32;cast&#32;phrase][SuperCastPhraseDescriptor].
		 */
		SUPER_CAST_PHRASE(
			"super cast phrase", EXPRESSION_PHRASE,
			TypeTag.SUPER_CAST_PHRASE_TAG)
		{
			override fun mostGeneralYieldType(): A_Type = ANY.o
		},

		/** The kind of a [send&#32;phrase][SendPhraseDescriptor].  */
		SEND_PHRASE(
			"send phrase type", EXPRESSION_PHRASE, TypeTag.SEND_PHRASE_TAG),

		/** The kind of a [list&#32;phrase][ListPhraseDescriptor].  */
		LIST_PHRASE(
			"list phrase type", EXPRESSION_PHRASE, TypeTag.LIST_PHRASE_TAG)
		{
			/** Create a descriptor for this kind.  */
			override fun createDescriptor(
				mutability: Mutability): PhraseTypeDescriptor =
					ListPhraseTypeDescriptor(mutability, this)

			override fun mostGeneralYieldType(): A_Type =
				TupleTypeDescriptor.mostGeneralTupleType()

			override fun createNoCheck(yieldType: A_Type): A_Type
			{
				val listNodeKind: PhraseKind = this
				val subexpressionsTupleType =
					TupleTypeDescriptor.tupleTypeFromTupleOfTypes(yieldType)
						{ yt: A_Type -> PARSE_PHRASE.create(yt) }
				return createListNodeTypeNoCheck(
					listNodeKind, yieldType, subexpressionsTupleType)
			}
		},

		/**
		 * The kind of a
		 * [permuted&#32;list&#32;phrase][PermutedListPhraseDescriptor].
		 */
		PERMUTED_LIST_PHRASE(
			"permuted list phrase type",
			LIST_PHRASE,
			TypeTag.PERMUTED_LIST_PHRASE_TAG)
		{
			/** Create a descriptor for this kind.  */
			override fun createDescriptor(
				mutability: Mutability): PhraseTypeDescriptor =
					ListPhraseTypeDescriptor(mutability, this)

			override fun mostGeneralYieldType(): A_Type =
				TupleTypeDescriptor.mostGeneralTupleType()

			override fun createNoCheck(
				yieldType: A_Type): A_Type
			{
				val listNodeKind: PhraseKind = this
				val subexpressionsTupleType =
					TupleTypeDescriptor.tupleTypeFromTupleOfTypes(yieldType)
						{ yt: A_Type -> PARSE_PHRASE.create(yt) }
				return createListNodeTypeNoCheck(
					listNodeKind, yieldType, subexpressionsTupleType)
			}
		},

		/**
		 * The kind of a
		 * [variable&#32;use&#32;phrase][VariableUsePhraseDescriptor].
		 */
		VARIABLE_USE_PHRASE(
			"variable use phrase type",
			EXPRESSION_PHRASE,
			TypeTag.VARIABLE_TAG)
		{
			override fun mostGeneralYieldType(): A_Type = ANY.o
		},

		/** A phrase that does not produce a result.  */
		STATEMENT_PHRASE(
			"statement phrase type", PARSE_PHRASE,
			TypeTag.STATEMENT_PHRASE_TAG),

		/**
		 * The kind of a [sequence&#32;phrase][SequencePhraseDescriptor].
		 */
		SEQUENCE_PHRASE(
			"sequence phrase type", STATEMENT_PHRASE,
			TypeTag.SEQUENCE_PHRASE_TAG),

		/**
		 * The kind of a
		 * [first-of-sequence&#32;phrase][FirstOfSequencePhraseDescriptor].
		 */
		FIRST_OF_SEQUENCE_PHRASE(
			"first-of-sequence phrase type", STATEMENT_PHRASE,
			TypeTag.FIRST_OF_SEQUENCE_PHRASE_TAG),

		/**
		 * The kind of a [declaration&#32;phrase][DeclarationPhraseDescriptor].
		 */
		DECLARATION_PHRASE(
			"declaration phrase type", STATEMENT_PHRASE,
			TypeTag.DECLARATION_PHRASE_TAG),

		/** The kind of an argument declaration phrase.  */
		ARGUMENT_PHRASE(
			"argument phrase type", DECLARATION_PHRASE,
			TypeTag.ARGUMENT_PHRASE_TAG),

		/** The kind of a label declaration phrase.  */
		LABEL_PHRASE(
			"label phrase type", DECLARATION_PHRASE,
			TypeTag.LABEL_PHRASE_TAG),

		/** The kind of a local variable declaration phrase.  */
		LOCAL_VARIABLE_PHRASE(
			"local variable phrase type", DECLARATION_PHRASE,
			TypeTag.LOCAL_VARIABLE_PHRASE_TAG),

		/** The kind of a local constant declaration phrase.  */
		LOCAL_CONSTANT_PHRASE(
			"local constant phrase type", DECLARATION_PHRASE,
			TypeTag.LOCAL_CONSTANT_PHRASE_TAG),

		/** The kind of a module variable declaration phrase.  */
		MODULE_VARIABLE_PHRASE(
			"module variable phrase type", DECLARATION_PHRASE,
			TypeTag.MODULE_VARIABLE_PHRASE_TAG),

		/** The kind of a module constant declaration phrase.  */
		MODULE_CONSTANT_PHRASE(
			"module constant phrase type", DECLARATION_PHRASE,
			TypeTag.MODULE_CONSTANT_PHRASE_TAG),

		/** The kind of a primitive failure reason variable declaration.  */
		PRIMITIVE_FAILURE_REASON_PHRASE(
			"primitive failure reason phrase type", DECLARATION_PHRASE,
			TypeTag.PRIMITIVE_FAILURE_REASON_PHRASE_TAG),

		/**
		 * A statement phrase built from an expression.  At the moment, only
		 * assignments and sends can be expression-as-statement phrases.
		 */
		EXPRESSION_AS_STATEMENT_PHRASE(
			"expression as statement phrase type", STATEMENT_PHRASE,
			TypeTag.EXPRESSION_AS_STATEMENT_PHRASE_TAG),

		/** The result of a macro substitution.  */
		MACRO_SUBSTITUTION_PHRASE(
			"macro substitution phrase type", PARSE_PHRASE,
			TypeTag.MACRO_SUBSTITUTION_PHRASE_TAG);

		override fun fieldName(): String = name

		override fun fieldOrdinal(): Int = ordinal

		/**
		 * Answer the kind of phrase of which this object is the type.
		 *
		 * @return
		 *   My parent phrase kind.
		 */
		fun parentKind(): PhraseKind? =  parentKind

		/**
		 * The most general inner type for this kind of phrase.
		 *
		 * @return
		 *   The most general inner type for this kind of phrase.
		 */
		open fun mostGeneralYieldType(): A_Type = Types.TOP.o

		/**
		 * The depth of this object in the PhraseKind hierarchy.
		 */
		var depth = 0

		/**
		 * Create a descriptor for this kind.
		 *
		 * @param mutability
		 *   The [Mutability] of the descriptor.
		 * @return
		 *   The new descriptor.
		 */
		open fun createDescriptor(
			mutability: Mutability): PhraseTypeDescriptor =
				PhraseTypeDescriptor(
					mutability,
					this,
					ObjectSlots::class.java,
					IntegerSlots::class.java)

		/**
		 * Create a [phrase&#32;type][PhraseTypeDescriptor] given the yield type
		 * (the type of object produced by the expression).
		 *
		 * @param yieldType
		 *   The type of object that will be produced by an expression which is
		 *   of the type being constructed.
		 * @return
		 *   The new phrase type, whose kind is the receiver.
		 */
		fun create(
			yieldType: A_Type): A_Type
		{
			assert(yieldType.isSubtypeOf(mostGeneralYieldType()))
			return createNoCheck(yieldType)
		}

		/**
		 * Create a [phrase&#32;type][PhraseTypeDescriptor] given the yield type
		 * (the type of object produced by the expression).
		 *
		 * @param yieldType
		 *   The type of object that will be produced by an expression which is
		 *   of the type being constructed.
		 * @return
		 *   The new phrase type, whose kind is the receiver.
		 */
		open fun createNoCheck(yieldType: A_Type): A_Type =
			mutableDescriptor.create {
				setSlot(EXPRESSION_TYPE, yieldType.makeImmutable())
			}

		/** The descriptor for mutable instances of this kind.  */
		val mutableDescriptor: PhraseTypeDescriptor

		/** The descriptor for shared instances of this kind.  */
		val sharedDescriptor: PhraseTypeDescriptor

		/** The most general type for this kind of phrase. */
		private val mostGeneralType: A_Type

		init
		{
			depth = if (parentKind !== null)
			{
				parentKind.depth + 1
			}
			else
			{
				0
			}
			mutableDescriptor = createDescriptor(Mutability.MUTABLE)
			sharedDescriptor = createDescriptor(Mutability.SHARED)
			mostGeneralType = createNoCheck(mostGeneralYieldType())
		}

		/**
		 * Answer a [phrase&#32;type][PhraseTypeDescriptor] whose kind is the
		 * receiver and whose expression type is
		 * [top][TypeDescriptor.Types.TOP]. This is the most general phrase type
		 * of that kind.
		 *
		 * @return
		 *   The new phrase type, whose kind is the receiver and whose
		 *   expression type is [top][TypeDescriptor.Types.TOP].
		 */
		fun mostGeneralType(): A_Type = mostGeneralType

		/**
		 * Answer the `PhraseKind` that is the nearest common ancestor
		 * to both the receiver and the argument.  Compute it rather than look
		 * it up, since this is used to populate the lookup table.
		 *
		 * @param other
		 *   The other `PhraseKind`.
		 * @return
		 *   The nearest common ancestor (a `PhraseKind`).
		 */
		private fun computeCommonAncestorWith(
			other: PhraseKind): PhraseKind
		{
			var a = this
			var b = other
			while (a !== b)
			{
				val diff = b.depth - a.depth
				if (diff <= 0)
				{
					a = a.parentKind()!!
				}
				if (diff >= 0)
				{
					b = b.parentKind()!!
				}
			}
			return a
		}

		/**
		 * Answer the `PhraseKind` that is the nearest common ancestor
		 * to both the receiver and the argument.  Only use this after static
		 * initialization has completed.
		 *
		 * @param other
		 *   The other `PhraseKind`.
		 * @return
		 *   The nearest common ancestor (a `PhraseKind`).
		 */
		fun commonAncestorWith(other: PhraseKind): PhraseKind =
			commonAncestors[ordinal * all.size + other.ordinal]!!

		/**
		 * Answer the `PhraseKind` that is the nearest common descendant
		 * to both the receiver and the argument.  Only use this after static
		 * initialization has completed.
		 *
		 * @param other
		 *   The other `PhraseKind`.
		 * @return
		 *   The nearest common descendant (a `PhraseKind`), or `null` if there
		 *   are no common descendants.
		 */
		fun commonDescendantWith(other: PhraseKind): PhraseKind? =
			commonDescendants[ordinal * all.size + other.ordinal]

		companion object
		{
			/** An array of all `PhraseKind` enumeration values.  */
			private val all = values()

			/**
			 * Answer an array of all `PhraseKind` enumeration values.
			 *
			 * @return
			 *   An array of all `PhraseKind` enum values.  Do not modify the
			 *   array.
			 */
			@JvmStatic
			fun all(): Array<PhraseKind> = all.clone()

			/**
			 * Answer the `PhraseKind` enumeration value having the given
			 * ordinal `int`.  The supplied ordinal must be valid.
			 *
			 * @param ordinal
			 *   The ordinal to look up.
			 * @return
			 *   The indicated `PhraseKind`.
			 */
			fun lookup(ordinal: Int): PhraseKind = all[ordinal]

			/**
			 * An array where the value at [(t1 * #values) + t2] indicates the
			 * nearest common ancestor of the kinds with ordinals t1 and t2.
			 * Note that this matrix is symmetric about its diagonal (i.e., it
			 * equals its transpose).
			 */
			private val commonAncestors =
				arrayOfNulls<PhraseKind>(all.size * all.size)

			init
			{
				// Populate the entire commonAncestors matrix.
				for (kind1 in all)
				{
					for (kind2 in all)
					{
						val index: Int =
							kind1.ordinal * all.size + kind2.ordinal
						commonAncestors[index] =
							kind1.computeCommonAncestorWith(kind2)
					}
				}
			}

			/**
			 * An array where the value at [(t1 * #values) + t2] indicates the
			 * nearest common descendant of the kinds with ordinals t1 and t2,
			 * or `null` if the kinds have no common descendant.  Note that this
			 * matrix is symmetric about its diagonal (i.e., it equals its
			 * transpose).
			 */
			private val commonDescendants =
				arrayOfNulls<PhraseKind>(all.size * all.size)

			init
			{
				// Populate the entire commonDescendants matrix.
				for (kind1 in all)
				{
					for (kind2 in all)
					{
						// The kinds form a tree, so either kind1 is an ancestor
						// of kind2, kind2 is an ancestor of kind1, or they have
						// no common descent.
						val index: Int =
							kind1.ordinal * all.size + kind2.ordinal
						val ancestor = commonAncestors[index]
						commonDescendants[index] =
							when
							{
								ancestor === kind1 -> kind2
								ancestor === kind2 -> kind1
								else -> null
							}
					}
				}
			}
		}

		/**
		 * Answer whether this is a subkind of (or equal to) the specified
		 * `PhraseKind`.
		 *
		 * @param purportedParent
		 * The kind that may be the ancestor.
		 * @return
		 * Whether the receiver descends from the argument.
		 */
		fun isSubkindOf(purportedParent: PhraseKind): Boolean
		{
			val index = ordinal * all.size + purportedParent.ordinal
			return commonAncestors[index] === purportedParent
		}
	}

	override fun allowsImmutableToMutableReferenceInField(
		e: AbstractSlotsEnum): Boolean
	{
		// Only the hash part may change (be set lazily), not the kind.
		return e === IntegerSlots.HASH_AND_MORE
	}

	/**
	 * Return the type of object that would be produced by a phrase of this
	 * type.
	 *
	 * @return
	 *   The [type][TypeDescriptor] of the [AvailObject] that will be produced
	 *   by a phrase of this type.
	 */
	override fun o_PhraseTypeExpressionType(self: AvailObject): A_Type =
		self.slot(EXPRESSION_TYPE)

	/**
	 * {@inheritDoc}
	 *
	 * Phrase types are equal when they are of the same kind and have the
	 * same expression type.
	 */
	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsPhraseType(self)

	/**
	 * {@inheritDoc}
	 *
	 * Phrase types are equal when they are of the same kind and have the
	 * same expression type.
	 */
	override fun o_EqualsPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): Boolean =
			(kind === aPhraseType.phraseKind()
		        && self.slot(EXPRESSION_TYPE).equals(
					aPhraseType.phraseTypeExpressionType()))

	/**
	 * Subclasses of `PhraseTypeDescriptor` must implement [phrases][A_Phrase]
	 * must implement [A_BasicObject.hash].
	 */
	override fun o_Hash(self: AvailObject): Int
	{
		var hash = self.slot(HASH_OR_ZERO)
		if (hash == 0)
		{
			hash = (self.slot(EXPRESSION_TYPE).hash()
				xor kind.ordinal * multiplier)
			self.setSlot(HASH_OR_ZERO, hash)
		}
		return hash
	}

	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfPhraseType(self)

	override fun o_IsSupertypeOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): Boolean =
			(PhraseKind.LIST_PHRASE.isSubkindOf(kind)
		        && aListNodeType.phraseTypeExpressionType().isSubtypeOf(
					self.phraseTypeExpressionType()))

	override fun o_IsSupertypeOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): Boolean
	{
		val otherKind = aPhraseType.phraseKind()
		return (otherKind.isSubkindOf(kind)
		        && aPhraseType.phraseTypeExpressionType().isSubtypeOf(
			self.phraseTypeExpressionType()))
	}

	/**
	 * Return the [phrase&#32;kind][PhraseKind] that this phrase type
	 * implements.
	 *
	 * @return
	 *   The [kind][PhraseKind] of phrase that the object is.
	 */
	override fun o_PhraseKind(self: AvailObject): PhraseKind =  kind

	override fun o_PhraseKindIsUnder(
		self: AvailObject,
		expectedPhraseKind: PhraseKind): Boolean =
			kind.isSubkindOf(expectedPhraseKind)

	override fun o_SerializerOperation(
		self: AvailObject): SerializerOperation =
			SerializerOperation.PARSE_NODE_TYPE

	override fun o_SubexpressionsTupleType(self: AvailObject): A_Type =
		// Only applicable if the expression type is a tuple type.
		TupleTypeDescriptor.tupleTypeFromTupleOfTypes(
			self.slot(EXPRESSION_TYPE)) { yieldType: A_Type ->
				PhraseKind.PARSE_PHRASE.create(yieldType)
		}

	override fun o_TypeIntersection(
		self: AvailObject,
		another: A_Type): A_Type = another.typeIntersectionOfPhraseType(self)

	override fun o_TypeIntersectionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type
	{
		// Intersection of two list phrase types.
		val intersectionKind = kind.commonDescendantWith(
			aListNodeType.phraseKind())
		                       ?: return bottom
		assert(intersectionKind.isSubkindOf(PhraseKind.LIST_PHRASE))
		return createListNodeType(
			intersectionKind,
			self.phraseTypeExpressionType().typeIntersection(
				aListNodeType.phraseTypeExpressionType()),
			aListNodeType.subexpressionsTupleType())
	}

	override fun o_TypeIntersectionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type
	{
		val intersectionKind =
			kind.commonDescendantWith(aPhraseType.phraseKind()) ?: return bottom
		assert(!intersectionKind.isSubkindOf(PhraseKind.LIST_PHRASE))
		// It should be safe to assume the mostGeneralType() of a subkind is
		// always a subtype of the mostGeneralType() of a superkind.
		return intersectionKind.createNoCheck(
			self.slot(EXPRESSION_TYPE).typeIntersection(
				aPhraseType.phraseTypeExpressionType()))
	}

	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type =
		another.typeUnionOfPhraseType(self)

	override fun o_TypeUnionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type
	{
		// Union of a non-list phrase type and a list phrase type is a non-list
		// phrase type.
		val otherKind = aListNodeType.phraseKind()
		assert(otherKind.isSubkindOf(PhraseKind.LIST_PHRASE))
		val unionKind = kind.commonAncestorWith(otherKind)
		assert(!unionKind.isSubkindOf(PhraseKind.LIST_PHRASE))
		return unionKind.create(
			self.phraseTypeExpressionType().typeUnion(
				aListNodeType.phraseTypeExpressionType()))
	}

	override fun o_TypeUnionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type
	{
		val unionKind = kind.commonAncestorWith(
			aPhraseType.phraseKind())
		return unionKind.createNoCheck(
			self.slot(EXPRESSION_TYPE).typeUnion(
				aPhraseType.phraseTypeExpressionType()))
	}

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write(kind.jsonName)
		writer.write("expression type")
		self.slot(EXPRESSION_TYPE).writeTo(writer)
		writer.endObject()
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		if (kind === PhraseKind.PARSE_PHRASE)
		{
			builder.append("phrase")
		}
		else
		{
			val name = kind.name.toLowerCase().replace('_', ' ')
			builder.append(name)
		}
		builder.append('⇒')
		self.phraseTypeExpressionType().printOnAvoidingIndent(
			builder, recursionMap, indent + 1)
	}

	/**
	 * Constants that are phrase types.  These must be initialized only after
	 * the `PhraseTypeDescriptor`s have been created.
	 */
	object Constants
	{
		/** The phrase type for string literals.  */
		val stringLiteralType: A_Type = PhraseKind.LITERAL_PHRASE.create(
			literalTokenType(TupleTypeDescriptor.stringType())).makeShared()
	}

	override fun mutable(): PhraseTypeDescriptor = kind.mutableDescriptor

	// There are no immutable descriptors.
	override fun immutable(): PhraseTypeDescriptor = kind.sharedDescriptor

	override fun shared(): PhraseTypeDescriptor = kind.sharedDescriptor
}
