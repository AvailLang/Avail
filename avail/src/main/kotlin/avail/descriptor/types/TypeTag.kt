/*
 * TypeTag.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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
package avail.descriptor.types

import avail.descriptor.atoms.AtomDescriptor.Companion.falseObject
import avail.descriptor.atoms.AtomDescriptor.Companion.trueObject
import avail.descriptor.maps.A_Map.Companion.mapSize
import avail.descriptor.numbers.InfinityDescriptor.Companion.negativeInfinity
import avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import avail.descriptor.numbers.IntegerDescriptor.Companion.fromInt
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.A_Set.Companion.setSize
import avail.descriptor.tuples.A_Tuple.Companion.tupleSize
import avail.descriptor.types.A_Type.Companion.argsTupleType
import avail.descriptor.types.A_Type.Companion.contentType
import avail.descriptor.types.A_Type.Companion.functionType
import avail.descriptor.types.A_Type.Companion.keyType
import avail.descriptor.types.A_Type.Companion.phraseTypeExpressionType
import avail.descriptor.types.A_Type.Companion.readType
import avail.descriptor.types.A_Type.Companion.returnType
import avail.descriptor.types.A_Type.Companion.sizeRange
import avail.descriptor.types.A_Type.Companion.valueType
import avail.descriptor.types.A_Type.Companion.writeType
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.instanceTypeOrMetaOn
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.CompiledCodeTypeDescriptor.Companion.mostGeneralCompiledCodeType
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.continuationMeta
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.falseType
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.trueType
import avail.descriptor.types.FiberTypeDescriptor.Companion.mostGeneralFiberType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.mostGeneralFunctionType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.extendedIntegers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.integers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.naturalNumbers
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.LiteralTokenTypeDescriptor.Companion.mostGeneralLiteralTokenType
import avail.descriptor.types.MapTypeDescriptor.Companion.mostGeneralMapType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PojoTypeDescriptor.Companion.mostGeneralPojoType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.SetTypeDescriptor.Companion.mostGeneralSetType
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.descriptor.types.TypeTag.Modifier
import avail.descriptor.types.TypeTag.Modifier.Abstract
import avail.descriptor.types.TypeTag.Modifier.Co
import avail.descriptor.types.TypeTag.Modifier.Contra
import avail.descriptor.types.TypeTag.Modifier.Sup
import avail.descriptor.types.TypeTag.Modifier.Unique
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableMeta
import avail.descriptor.types.VariableTypeDescriptor.Companion.mostGeneralVariableType

/**
 * `TypeTag` is an enumeration that corresponds with the basic type structure of
 * Avail's type lattice.  Even though the type lattice contains an infinite
 * collection of infinitely long chains of types, some of which have an infinite
 * number of direct ancestors and direct descendants, we're still able to
 * extract a pragmatic tree of types from the lattice.
 *
 * Since this restricted set of types form a tree, they're defined in such an
 * order that all of a type's descendants follow it.  Since this is recursively
 * true, the types are effectively listed in depth-last order.  The ordinals are
 * assigned in the order of definition, but each type keeps track of the maximum
 * ordinal of all of its descendants (which occupy a contiguous span of ordinals
 * just after the type's ordinal).  We can test if type A is a subtype of B by
 * checking if a.ordinal ≥ b.ordinal and a.highOrdinal ≤ b.highOrdinal.  For a
 * proper subtype test, we turn the first condition into an inequality.
 *
 * @property parent
 *   The parent of this [TypeTag].
 *
 * @constructor
 * Construct a new enum instance.  Must be listed in hierarchical order.
 *
 * @param instance
 *   The most general instance of this [TypeTag].
 * @param modifiers
 *   The vararg list of [Modifier]s, which set flags or declare covariant and
 *   contravariant relationships.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
enum class TypeTag
constructor(
	val parent: TypeTag?,
	instance: TypeTag? = null,
	vararg modifiers: Modifier)
{
	UNKNOWN_TAG(null, instance = UNKNOWN_TAG, Abstract, Sup { Types.TOP.o }),
	TOP_TAG(null, null, Abstract, Sup { Types.TOP.o }),
	NIL_TAG(TOP_TAG, null, Unique { nil }, Sup { Types.TOP.o }),
	NONTYPE_TAG(TOP_TAG, null, Sup { Types.NONTYPE.o }),
	ATOM_TAG(NONTYPE_TAG, null, Sup { Types.ATOM.o }),
	BOOLEAN_TAG(ATOM_TAG, null, Abstract, Sup { booleanType }),
	TRUE_TAG(BOOLEAN_TAG, null, Unique { trueObject }),
	FALSE_TAG(BOOLEAN_TAG, null, Unique { falseObject }),
	BUNDLE_TAG(NONTYPE_TAG, null, Sup { Types.MESSAGE_BUNDLE.o }),
	BUNDLE_TREE_TAG(NONTYPE_TAG, null, Sup { Types.MESSAGE_BUNDLE_TREE.o }),
	CHARACTER_TAG(NONTYPE_TAG, null, Sup { Types.CHARACTER.o }),
	CONTINUATION_TAG(NONTYPE_TAG, null, Sup { mostGeneralContinuationType }),
	DEFINITION_TAG(NONTYPE_TAG, null, Sup { Types.DEFINITION.o }),
	FIBER_TAG(NONTYPE_TAG, null, Sup { mostGeneralFiberType() }),
	FUNCTION_TAG(NONTYPE_TAG, null, Sup { mostGeneralFunctionType() }),
	LEXER_TAG(NONTYPE_TAG, null, Sup { Types.LEXER.o }),
	MACRO_TAG(NONTYPE_TAG, null, Sup { Types.MACRO_DEFINITION.o }),
	MAP_TAG(NONTYPE_TAG, null, Sup { mostGeneralMapType() }),
	METHOD_TAG(NONTYPE_TAG, null, Sup { Types.METHOD.o }),
	MODULE_TAG(NONTYPE_TAG, null, Sup { Types.MODULE.o }),
	RESOURCE_TAG(NONTYPE_TAG, null, Sup { Types.RESOURCE.o }),
	NUMBER_TAG(NONTYPE_TAG, null, Abstract, Sup { Types.NUMBER.o }),
	EXTENDED_INTEGER_TAG(NUMBER_TAG, null, Abstract, Sup { extendedIntegers }),
	INTEGER_TAG(EXTENDED_INTEGER_TAG, null, Sup { integers }),
	WHOLE_NUMBER_TAG(INTEGER_TAG, null, Sup { wholeNumbers }),
	NATURAL_NUMBER_TAG(WHOLE_NUMBER_TAG, null, Sup { naturalNumbers }),
	NEGATIVE_INFINITY_TAG(
		EXTENDED_INTEGER_TAG, null, Unique { negativeInfinity }),
	POSITIVE_INFINITY_TAG(
		EXTENDED_INTEGER_TAG, null, Unique { positiveInfinity }),
	FLOAT_TAG(NUMBER_TAG, null, Sup { Types.FLOAT.o }),
	DOUBLE_TAG(NUMBER_TAG, null, Sup { Types.DOUBLE.o }),
	OBJECT_TAG(NONTYPE_TAG, null, Sup { mostGeneralObjectType }),
	PARSING_PLAN_TAG(
		NONTYPE_TAG, null, Sup { Types.DEFINITION_PARSING_PLAN.o }),
	PARSING_PLAN_IN_PROGRESS_TAG(
		NONTYPE_TAG, null, Sup {Types.PARSING_PLAN_IN_PROGRESS.o }),
	PHRASE_TAG(
		NONTYPE_TAG,
		null,
		Abstract,
		Sup { PhraseKind.PARSE_PHRASE.mostGeneralType }),
	EXPRESSION_PHRASE_TAG(
		PHRASE_TAG,
		null,
		Abstract,
		Sup { PhraseKind.EXPRESSION_PHRASE.mostGeneralType}),
	MARKER_PHRASE_TAG(
		EXPRESSION_PHRASE_TAG,
		null,
		Sup { PhraseKind.MARKER_PHRASE.mostGeneralType }),
	ASSIGNMENT_PHRASE_TAG(
		EXPRESSION_PHRASE_TAG,
		null,
		Sup { PhraseKind.ASSIGNMENT_PHRASE.mostGeneralType }),
	BLOCK_PHRASE_TAG(
		EXPRESSION_PHRASE_TAG,
		null,
		Sup { PhraseKind.BLOCK_PHRASE.mostGeneralType }),
	LITERAL_PHRASE_TAG(
		EXPRESSION_PHRASE_TAG,
		null,
		Sup { PhraseKind.LITERAL_PHRASE.mostGeneralType }),
	REFERENCE_PHRASE_TAG(
		EXPRESSION_PHRASE_TAG,
		null,
		Sup { PhraseKind.REFERENCE_PHRASE.mostGeneralType }),
	SUPER_CAST_PHRASE_TAG(
		EXPRESSION_PHRASE_TAG,
		null,
		Sup { PhraseKind.SUPER_CAST_PHRASE.mostGeneralType }),
	SEND_PHRASE_TAG(
		EXPRESSION_PHRASE_TAG,
		null,
		Sup { PhraseKind.SEND_PHRASE.mostGeneralType }),
	LIST_PHRASE_TAG(
		EXPRESSION_PHRASE_TAG,
		null,
		Sup { PhraseKind.LIST_PHRASE.mostGeneralType }),
	PERMUTED_LIST_PHRASE_TAG(
		LIST_PHRASE_TAG,
		null,
		Sup { PhraseKind.PERMUTED_LIST_PHRASE.mostGeneralType }),
	VARIABLE_USE_PHRASE_TAG(
		EXPRESSION_PHRASE_TAG,
		null,
		Sup { PhraseKind.VARIABLE_USE_PHRASE.mostGeneralType }),
	SEQUENCE_AS_EXPRESSION_PHRASE_TAG(
		EXPRESSION_PHRASE_TAG,
		null,
		Sup { PhraseKind.SEQUENCE_AS_EXPRESSION_PHRASE.mostGeneralType }),
	STATEMENT_PHRASE_TAG(
		PHRASE_TAG,
		null,
		Abstract,
		Sup { PhraseKind.STATEMENT_PHRASE.mostGeneralType }),
	SEQUENCE_PHRASE_TAG(
		STATEMENT_PHRASE_TAG,
		null,
		Sup { PhraseKind.SEQUENCE_PHRASE.mostGeneralType }),
	FIRST_OF_SEQUENCE_PHRASE_TAG(
		STATEMENT_PHRASE_TAG,
		null,
		Sup { PhraseKind.FIRST_OF_SEQUENCE_PHRASE.mostGeneralType }),
	DECLARATION_PHRASE_TAG(
		STATEMENT_PHRASE_TAG,
		null,
		Sup { PhraseKind.STATEMENT_PHRASE.mostGeneralType }),
	ARGUMENT_PHRASE_TAG(
		DECLARATION_PHRASE_TAG,
		null,
		Sup { PhraseKind.ARGUMENT_PHRASE.mostGeneralType }),
	LABEL_PHRASE_TAG(
		DECLARATION_PHRASE_TAG,
		null,
		Sup { PhraseKind.LABEL_PHRASE.mostGeneralType }),
	LOCAL_VARIABLE_PHRASE_TAG(
		DECLARATION_PHRASE_TAG,
		null,
		Sup { PhraseKind.LOCAL_VARIABLE_PHRASE.mostGeneralType }),
	LOCAL_CONSTANT_PHRASE_TAG(
		DECLARATION_PHRASE_TAG,
		null,
		Sup { PhraseKind.LOCAL_CONSTANT_PHRASE.mostGeneralType }),
	MODULE_VARIABLE_PHRASE_TAG(
		DECLARATION_PHRASE_TAG,
		null,
		Sup { PhraseKind.MODULE_VARIABLE_PHRASE.mostGeneralType }),
	MODULE_CONSTANT_PHRASE_TAG(
		DECLARATION_PHRASE_TAG,
		null,
		Sup { PhraseKind.MODULE_CONSTANT_PHRASE.mostGeneralType }),
	PRIMITIVE_FAILURE_REASON_PHRASE_TAG(
		DECLARATION_PHRASE_TAG,
		null,
		Sup { PhraseKind.PRIMITIVE_FAILURE_REASON_PHRASE.mostGeneralType }),
	EXPRESSION_AS_STATEMENT_PHRASE_TAG(
		STATEMENT_PHRASE_TAG,
		null,
		Sup { PhraseKind.EXPRESSION_AS_STATEMENT_PHRASE.mostGeneralType }),
	MACRO_SUBSTITUTION_PHRASE_TAG(
		PHRASE_TAG,
		null,
		Sup { PhraseKind.MACRO_SUBSTITUTION_PHRASE.mostGeneralType }),
	POJO_TAG(NONTYPE_TAG, null, Sup { mostGeneralPojoType() }),
	RAW_FUNCTION_TAG(NONTYPE_TAG, null, Sup { mostGeneralCompiledCodeType() }),
	SET_TAG(NONTYPE_TAG, null, Sup { mostGeneralSetType() }),
	TOKEN_TAG(NONTYPE_TAG, null, Sup { Types.TOKEN.o }),
	LITERAL_TOKEN_TAG(TOKEN_TAG, null, Sup { mostGeneralLiteralTokenType() }),
	TUPLE_TAG(NONTYPE_TAG, null, Sup { mostGeneralTupleType }),
	VARIABLE_TAG(NONTYPE_TAG, null, Sup { mostGeneralVariableType }),

	// All the rest are the tags for types...
	TOP_TYPE_TAG(
		TOP_TAG, instance = TOP_TAG, Sup { instanceMeta(Types.TOP.o) }),
	ANY_TYPE_TAG(TOP_TYPE_TAG, null, Sup { instanceMeta(Types.ANY.o) }),
	NONTYPE_TYPE_TAG(
		ANY_TYPE_TAG,
		instance = NONTYPE_TAG,
		Sup { instanceMeta(Types.NONTYPE.o) }),
	ATOM_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = ATOM_TAG,
		Sup { instanceMeta(Types.ATOM.o) }),
	BOOLEAN_TYPE_TAG(
		ATOM_TYPE_TAG,
		instance = BOOLEAN_TAG,
		Sup { instanceMeta(booleanType) }),
	TRUE_TYPE_TAG(
		BOOLEAN_TYPE_TAG,
		instance = TRUE_TAG,
		Sup { instanceMeta(trueType) }),
	FALSE_TYPE_TAG(
		BOOLEAN_TYPE_TAG,
		instance = FALSE_TAG,
		Sup { instanceMeta(falseType) }),
	SET_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = SET_TAG,
		Sup { instanceMeta(mostGeneralSetType()) },
		Co("size", part = { fromInt(setSize) }) { sizeRange },
		Co("element") { contentType }),
	POJO_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = POJO_TAG,
		Sup { instanceMeta(mostGeneralPojoType()) }),
	NUMBER_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = NUMBER_TAG,
		Sup { instanceMeta(Types.NUMBER.o) }),
	EXTENDED_INTEGER_TYPE_TAG(
		NUMBER_TYPE_TAG,
		instance = EXTENDED_INTEGER_TAG,
		Sup { instanceMeta(extendedIntegers) }),
	PHRASE_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = PHRASE_TAG,
		Sup { instanceMeta(PhraseKind.PARSE_PHRASE.mostGeneralType) },
		Co("yields") { phraseTypeExpressionType }),
	LIST_PHRASE_TYPE_TAG(
		PHRASE_TYPE_TAG,
		instance = LIST_PHRASE_TAG,
		Sup { instanceMeta(PhraseKind.LIST_PHRASE.mostGeneralType) }),
	VARIABLE_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = VARIABLE_TAG,
		Sup { mostGeneralVariableMeta },
		Co("read") { readType },
		Contra("write") { writeType }),
	FUNCTION_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = FUNCTION_TAG,
		Sup { instanceMeta(mostGeneralFunctionType()) },
		Contra("arguments") { argsTupleType },
		Co("return") { returnType }),
	OBJECT_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = OBJECT_TAG,
		Sup { mostGeneralObjectMeta}),
	MAP_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = MAP_TAG,
		Sup { instanceMeta(mostGeneralMapType()) },
		Co("size", part = { fromInt(mapSize) }) { sizeRange },
		Co("key") { keyType },
		Co("value") { valueType }),
	TOKEN_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = TOKEN_TAG,
		Sup { instanceMeta(Types.TOKEN.o) }),
	LITERAL_TOKEN_TYPE_TAG(
		TOKEN_TYPE_TAG,
		instance = LITERAL_TOKEN_TAG,
		Sup { instanceMeta(mostGeneralLiteralTokenType()) }),
	TUPLE_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = TUPLE_TAG,
		Sup { instanceMeta(mostGeneralTupleType) },
		Co("size", part = { fromInt(tupleSize) }) { sizeRange }),
	CONTINUATION_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = CONTINUATION_TAG,
		Sup { continuationMeta },
		Contra("arguments") { functionType.argsTupleType },
		Contra("result") { functionType.returnType }),
	RAW_FUNCTION_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = RAW_FUNCTION_TAG,
		Sup { instanceMeta(mostGeneralCompiledCodeType()) },
		Co("functionType") { functionType }),
	FIBER_TYPE_TAG(
		NONTYPE_TYPE_TAG,
		instance = FIBER_TAG,
		Sup { instanceMeta(mostGeneralFiberType()) },
		Co("result") { resultType() }),
	META_TAG(
		ANY_TYPE_TAG,
		instance = TOP_TYPE_TAG,
		Sup { instanceMeta(instanceMeta(Types.TOP.o)) }),
	BOTTOM_TYPE_TAG(
		TOP_TYPE_TAG,
		NIL_TAG,
		Unique { bottom });

	/**
	 * The number of ancestors of this [TypeTag]
	 */
	val depth: Int = if (parent == null) 0 else parent.depth + 1

	/**
	 * If object X has tag T, then X's type has tag T.metaTag.
	 */
	var metaTag: TypeTag? = null
		private set

	/**
	 * A flag set during initialization, indicating this [TypeTag] has no
	 * direct instances.
	 */
	var isAbstract = false

	/**
	 * Some [TypeTag]s occur in exactly one [AvailObject].  If so, this field is
	 * a lambda that produces that value.
	 */
	private var uniqueProducer: (() -> A_BasicObject)? = null

	/**
	 * Some [TypeTag]s occur in exactly one [AvailObject].  If so, this field
	 * will be initialized to a lambda that produces the value.
	 */
	val uniqueValue by lazy { uniqueProducer?.invoke() as AvailObject? }

	/**
	 * Every [TypeTag] has a (potentially infinite) collection of values that
	 * use that tag.  This is a lambda that produces the least upper bound
	 * [A_Type] that constrains all those values.
	 */
	private lateinit var supremumProducer: (() -> A_Type)

	/**
	 * Every [TypeTag] has a (potentially infinite) collection of values that
	 * use that tag.  After initialization, this property has the least upper
	 * bound [A_Type] that constrains all those values.
	 */
	val supremum by lazy { supremumProducer() }

	/**
	 * The array of [Co]variant relationships defined during construction.
	 */
	val covariants = mutableListOf<Co>()

	/**
	 * The array of [Contra]variant relationships defined during construction.
	 */
	val contravariants = mutableListOf<Contra>()

	init
	{
		instance?.metaTag = this
		modifiers.forEach { it.applyTo(this) }
	}

	sealed class Modifier
	{
		/**
		 * An indicator that the TypeTag has no direct instances.
		 */
		object Abstract : Modifier()
		{
			override fun applyTo(typeTag: TypeTag)
			{
				typeTag.isAbstract = true
			}
		}

		/**
		 * An indicator that the TypeTag has exactly one instance.  A lambda to
		 * produce that instance is provided, and will be executed once, when
		 * first requested.
		 *
		 * If the [Unique] value is provided, the [Sup] (supremum) is
		 * automatically set accordingly.
		 */
		class Unique(val value: ()->A_BasicObject) : Modifier()
		{
			override fun applyTo(typeTag: TypeTag)
			{
				typeTag.uniqueProducer = value
				typeTag.supremumProducer =
					{ instanceTypeOrMetaOn(typeTag.uniqueValue!!) }
			}
		}

		/**
		 * Every [TypeTag] has a (potentially infinite) collection of values
		 * that use that tag.  This specifies a lambda that produces the least
		 * upper bound [A_Type] that constrains all those values.
		 *
		 * The lambda will be provided at most once, and only when needed.
		 */
		class Sup constructor(val value: ()->A_Type) : Modifier()
		{
			override fun applyTo(typeTag: TypeTag)
			{
				typeTag.supremumProducer = value
			}
		}

		/**
		 * The mechanism for expressing covariant and contravariant
		 * relationships for types.  Each [TypeTag] that represents a region of
		 * the [type][A_Type] lattice can define [Co]variant and [Contra]variant
		 * relations that apply to types that have that `TypeTag`.
		 *
		 * @property name
		 *   The symbolic name of the variant, for descriptive purposes.
		 * @property traverse
		 *   A function that extracts a co-/contravariant type from the original
		 *   type, where this [Variant] is applicable.  For example, given
		 *   something with the tag [SET_TYPE_TAG] (the [TypeTag] for set
		 *   types), the set type's element type can be extracted by running the
		 *   [traverse] function of the "element" covariant type parameter.
		 */
		abstract class Variant constructor(
			val name: String,
			val traverse: A_Type.()->A_Type
		) : Modifier()

		/**
		 * A Covariant relationship.  When a [TypeTag] declares such a
		 * relationship, then for all A and B having that `TypeTag`, if A ⊆ B,
		 * then traverse(A) ⊆ traverse(B), where [traverse] is a function
		 * provided by the [Co] instance.
		 *
		 * @property part
		 *   An optional function mapping an *instance* of a type into an
		 *   instance of the type's covariant property.  This doesn't always
		 *   make sense, as the object doesn't necessarily have some subobject
		 *   that is homomorphic to the covariant relationship.  In that case,
		 *   the default `null` is used instead of the function, which indicates
		 *   a mechanism for producing such a subobject is unavailable.
		 */
		class Co(
			name: String,
			val part: (AvailObject.()->A_BasicObject)? = null,
			traverse: A_Type.()->A_Type
		) : Variant(name, traverse)
		{
			override fun applyTo(typeTag: TypeTag)
			{
				typeTag.covariants.add(this)
			}
		}

		/**
		 * A Contravariant relationship.  When a [TypeTag] declares such a
		 * relationship, then for all A and B having that `TypeTag`, if A ⊆ B,
		 * then traverse(B) ⊆ traverse(A), where [traverse] is a function
		 * provided by the [Contra] instance.
		 */
		class Contra(
			name: String,
			traverse: A_Type.()->A_Type
		) : Variant(name, traverse)
		{
			override fun applyTo(typeTag: TypeTag)
			{
				typeTag.contravariants.add(this)
			}
		}

		/** Adjust the TypeTag to accommodate this modifier. */
		abstract fun applyTo(typeTag: TypeTag)
	}

	/**
	 * The highest ordinal value of all of this [TypeTag]'s descendants,
	 * including itself.  The descendants' ordinals must start just after the
	 * current [TypeTag]'s ordinal, and be contiguously numbered.  Since the
	 * ordinals are assigned by the [Enum] mechanism, that means a [TypeTag]
	 * definition must be followed immediately by each of its children and their
	 * descendants, which is prefix tree order.
	 */
	var highOrdinal: Int = -1
		private set

	@Suppress("unused")
	fun isSubtagOf (otherTag: TypeTag): Boolean =
		((ordinal >= otherTag.ordinal && highOrdinal <= otherTag.highOrdinal)
			|| (this == BOTTOM_TYPE_TAG
				&& otherTag.ordinal >= TOP_TYPE_TAG.ordinal))

	fun commonAncestorWith (other: TypeTag?): TypeTag
	{
		if (this == other)
		{
			return this
		}
		val myParent = parent
		if (depth > other!!.depth)
		{
			assert(myParent !== null)
			return myParent!!.commonAncestorWith(other)
		}
		val otherParent = other.parent
		if (other.depth > depth)
		{
			assert(otherParent !== null)
			return otherParent!!.commonAncestorWith(this)
		}
		assert(myParent !== null && otherParent !== null)
		assert(this != UNKNOWN_TAG && other != UNKNOWN_TAG)
		return myParent!!.commonAncestorWith(otherParent)
	}

	companion object
	{
		/** Capture the enumeration values in a private array. */
		private val all = entries.toTypedArray()

		/** The number of [TypeTag]s. */
		val count = all.size

		/** Look up the [TypeTag] with the given ordinal. */
		fun tagFromOrdinal(ordinal: Int) = all[ordinal]

		init
		{
			all.forEach { tag ->
				if (tag.metaTag === null && tag != UNKNOWN_TAG)
				{
					tag.metaTag = tag.parent!!.metaTag
				}
			}
			// Working backwards, set the highOrdinal of any tag that hasn't
			// had a child set it already, implying it has no children.  Then
			// attempt to copy the highOrdinal into the parent's highOrdinal, if
			// it hasn't already been set yet.
			all.reversed().forEach { tag ->
				if (tag.highOrdinal == -1)
					tag.highOrdinal = tag.ordinal
				tag.parent?.let { parent ->
					if (parent.highOrdinal == -1)
						parent.highOrdinal = tag.highOrdinal
				}
			}
		}
	}
}
