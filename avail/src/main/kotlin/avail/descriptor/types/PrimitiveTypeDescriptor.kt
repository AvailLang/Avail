/*
 * PrimitiveTypeDescriptor.kt
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

import avail.AvailRuntimeSupport
import avail.annotations.HideFieldInDebugger
import avail.annotations.ThreadSafe
import avail.compiler.AvailCompiler
import avail.descriptor.atoms.AtomDescriptor
import avail.descriptor.bundles.MessageBundleDescriptor
import avail.descriptor.bundles.MessageBundleTreeDescriptor
import avail.descriptor.character.CharacterDescriptor
import avail.descriptor.methods.AbstractDefinitionDescriptor
import avail.descriptor.methods.DefinitionDescriptor
import avail.descriptor.methods.ForwardDefinitionDescriptor
import avail.descriptor.methods.MethodDefinitionDescriptor
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.numbers.DoubleDescriptor
import avail.descriptor.numbers.FloatDescriptor
import avail.descriptor.numbers.InfinityDescriptor
import avail.descriptor.numbers.IntegerDescriptor
import avail.descriptor.parsing.DefinitionParsingPlanDescriptor
import avail.descriptor.parsing.LexerDescriptor
import avail.descriptor.parsing.ParsingPlanInProgressDescriptor
import avail.descriptor.pojos.PojoDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.BitField
import avail.descriptor.representation.IntegerSlotsEnum
import avail.descriptor.representation.Mutability
import avail.descriptor.representation.NilDescriptor
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.representation.ObjectSlotsEnum
import avail.descriptor.tokens.LiteralTokenDescriptor
import avail.descriptor.tokens.TokenDescriptor
import avail.descriptor.tuples.A_String.Companion.asNativeString
import avail.descriptor.tuples.StringDescriptor
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.types.A_Type.Companion.isSubtypeOf
import avail.descriptor.types.A_Type.Companion.isSupertypeOfPrimitiveTypeEnum
import avail.descriptor.types.A_Type.Companion.typeIntersectionOfPrimitiveTypeEnum
import avail.descriptor.types.A_Type.Companion.typeUnionOfPrimitiveTypeEnum
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.PrimitiveTypeDescriptor.IntegerSlots.Companion.HASH
import avail.descriptor.types.PrimitiveTypeDescriptor.ObjectSlots.NAME
import avail.descriptor.types.PrimitiveTypeDescriptor.ObjectSlots.PARENT
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ABSTRACT_DEFINITION
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.CHARACTER
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.Companion.all
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.DEFINITION
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.DEFINITION_PARSING_PLAN
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.DOUBLE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.FLOAT
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.FORWARD_DEFINITION
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.LEXER
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.MACRO_DEFINITION
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.MESSAGE_BUNDLE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.MESSAGE_BUNDLE_TREE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.METHOD
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.METHOD_DEFINITION
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.MODULE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NONTYPE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.PARSING_PLAN_IN_PROGRESS
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.RAW_POJO
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOKEN
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.TOP
import avail.descriptor.variables.VariableDescriptor
import avail.exceptions.unsupported
import avail.interpreter.execution.LexicalScanner
import avail.serialization.SerializerOperation
import avail.utility.iterableWith
import org.availlang.json.JSONWriter
import java.beans.MethodDescriptor
import java.util.IdentityHashMap

/**
 * The primitive types of Avail are different from the notion of primitive types
 * in other object-oriented languages. Traditionally, a compiler or virtual
 * machine encodes representation knowledge about and makes other special
 * provisions about its primitive types. Since *all* types are in a sense
 * provided by the Avail system, it has no special primitive types that fill
 * that role – they're *all* special.
 *
 * The primitive types in Avail include [any][Types.ANY], and various
 * specialties such as [atom][Types.ATOM] and [number][Types.NUMBER]. Type
 * hierarchies that have a natural root don't bother with a primitive type to
 * delimit the hierarchy, using the natural root itself.
 *
 * @property primitiveType
 *   The [primitive&#32;type][Types] represented by this descriptor.
 *
 * @constructor
 * Construct a new [shared][Mutability.SHARED] [PrimitiveTypeDescriptor].
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 *
 * @see [Types] all primitive types.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
open class PrimitiveTypeDescriptor
private constructor(
	mutability: Mutability,
	val primitiveType: Types
) : TypeDescriptor(
	mutability,
	primitiveType.typeTag,
	primitiveType.instanceTag,
	ObjectSlots::class.java,
	IntegerSlots::class.java)
{
	/**
	 * The layout of integer slots for my instances.
	 */
	enum class IntegerSlots : IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for caching the hash, and the upper 32 are
		 * for the ordinal of the primitive type.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		companion object
		{
			/**
			 * The hash, populated during construction.
			 */
			val HASH = BitField(HASH_AND_MORE, 0, 32) { null }
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	enum class ObjectSlots : ObjectSlotsEnum
	{
		/**
		 * The [name][StringDescriptor] of this primitive type.
		 */
		NAME,

		/**
		 * The parent type of this primitive type.
		 */
		PARENT
	}

	override fun printObjectOnAvoidingIndent(
		self: AvailObject,
		builder: StringBuilder,
		recursionMap: IdentityHashMap<A_BasicObject, Void>,
		indent: Int)
	{
		builder.append(self[NAME].asNativeString())
	}

	override fun o_Equals(self: AvailObject, another: A_BasicObject): Boolean =
		another.equalsPrimitiveType(self)

	// Primitive types compare by identity.
	override fun o_EqualsPrimitiveType(
		self: AvailObject,
		aPrimitiveType: A_Type): Boolean = self.sameAddressAs(aPrimitiveType)

	override fun o_Hash(self: AvailObject): Int = self[HASH]

	override fun o_Parent(self: AvailObject): A_BasicObject =
		self[PARENT]

	// Check if object (a type) is a subtype of aType (should also be a
	// type).
	override fun o_IsSubtypeOf(self: AvailObject, aType: A_Type): Boolean =
		aType.isSupertypeOfPrimitiveTypeEnum(extractEnum(self))

	// This primitive type is a supertype of aFiberType if and only if
	// this primitive type is a supertype of NONTYPE.
	override fun o_IsSupertypeOfFiberType(
		self: AvailObject,
		aType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	// This primitive type is a supertype of aFunctionType if and only if
	// this primitive type is a supertype of NONTYPE.
	override fun o_IsSupertypeOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	override fun o_IsSupertypeOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	// A primitive type is a supertype of a variable type if it is a
	// supertype of NONTYPE.
	override fun o_IsSupertypeOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	// A primitive type is a supertype of a continuation type if it is a
	// supertype of NONTYPE.
	override fun o_IsSupertypeOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	// A primitive type is a supertype of a compiled code type if it is a
	// supertype of NONTYPE.
	override fun o_IsSupertypeOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	// Parent of the top integer range type is number, so continue
	// searching there.
	override fun o_IsSupertypeOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NUMBER)

	// This primitive type is a supertype of aTokenType if and only if this
	// primitive type is a supertype of TOKEN.
	override fun o_IsSupertypeOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(TOKEN)

	// This primitive type is a supertype of aLiteralTokenType if and only
	// if this primitive type is a supertype of TOKEN.
	override fun o_IsSupertypeOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(TOKEN)

	// This primitive type is a supertype of aMapType if and only if this
	// primitive type is a supertype of NONTYPE.
	override fun o_IsSupertypeOfMapType(
		self: AvailObject,
		aMapType: AvailObject): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	// Check if I'm a supertype of the given eager object type. Only NONTYPE
	// and its ancestors are supertypes of an object type.
	override fun o_IsSupertypeOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	override fun o_IsSupertypeOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	override fun o_IsSupertypeOfPojoBottomType(
		self: AvailObject,
		aPojoType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	override fun o_IsSupertypeOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	override fun o_IsSupertypeOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): Boolean =
			primitiveTypeEnum.superTests[extractOrdinal(self)]

	// This primitive type is a supertype of aSetType if and only if this
	// primitive type is a supertype of NONTYPE.
	override fun o_IsSupertypeOfSetType(
		self: AvailObject,
		aSetType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	// This primitive type is a supertype of aTupleType if and only if this
	// primitive type is a supertype of NONTYPE.
	override fun o_IsSupertypeOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): Boolean =
			self.isSupertypeOfPrimitiveTypeEnum(NONTYPE)

	override fun o_IsSupertypeOfEnumerationType(
		self: AvailObject,
		anEnumerationType: A_Type): Boolean =
			topMeta().isSubtypeOf(self)

	@ThreadSafe
	override fun o_IsTop(self: AvailObject): Boolean = self.sameAddressAs(TOP.o)

	override fun o_MarshalToJava(self: AvailObject, classHint: Class<*>?): Any?
	{
		for (type in all)
		{
			if (self.equals(type.o))
			{
				return when (type)
				{
					TOP -> Void::class.java
					ANY -> Any::class.java
					DOUBLE -> Double::class.javaPrimitiveType
					FLOAT -> Float::class.javaPrimitiveType
					ABSTRACT_DEFINITION, ATOM, CHARACTER,
					DEFINITION_PARSING_PLAN, FORWARD_DEFINITION,
					LEXER, MACRO_DEFINITION, MESSAGE_BUNDLE,
					MESSAGE_BUNDLE_TREE, METHOD,
					METHOD_DEFINITION, MODULE, NONTYPE,
					NUMBER, PARSING_PLAN_IN_PROGRESS,
					RAW_POJO, DEFINITION, TOKEN ->
						super.o_MarshalToJava(self, classHint)
				}
			}
		}
		throw AssertionError(
			"All cases have been dealt with, and each forces a return")
	}

	override fun o_RangeIncludesLong(self: AvailObject, aLong: Long): Boolean =
		self.isSupertypeOfPrimitiveTypeEnum(NUMBER)

	// Most of the primitive types are already handled as special objects,
	// so this only kicks in as a backup.
	override fun o_SerializerOperation(self: AvailObject): SerializerOperation =
		SerializerOperation.ARBITRARY_PRIMITIVE_TYPE

	override fun o_TypeIntersection(self: AvailObject, another: A_Type): A_Type =
		another.typeIntersectionOfPrimitiveTypeEnum(extractEnum(self))

	override fun o_TypeIntersectionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type =
			if (NONTYPE.superTests[extractOrdinal(self)]) aListNodeType
			else bottom

	override fun o_TypeIntersectionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type =
			if (NONTYPE.superTests[extractOrdinal(self)]) aPhraseType
			else bottom

	override fun o_TypeIntersectionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			primitiveTypeEnum.intersectionTypes[extractOrdinal(self)]!!

	override fun o_TypeUnion(self: AvailObject, another: A_Type): A_Type =
		another.typeUnionOfPrimitiveTypeEnum(extractEnum(self))

	override fun o_TypeUnionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			primitiveTypeEnum.unionTypes[extractOrdinal(self)]!!

	override fun o_WriteTo(self: AvailObject, writer: JSONWriter)
	{
		writer.startObject()
		writer.write("kind")
		writer.write("${self[NAME].asNativeString().lowercase()} type")
		writer.endObject()
	}

	/**
	 * Complete the given partially-initialized primitive type.  Set the type's
	 * parent and (shared) descriptor.  Don't force the parent to be shared yet
	 * if it isn't.
	 *
	 * @param parentType
	 *   The parent of this object, not necessarily shared.
	 */
	fun finishInitializingPrimitiveTypeWithParent(
		self: AvailObject,
		parentType: A_Type)
	{
		assert(mutability === Mutability.SHARED)
		self[PARENT] = parentType
		self.setDescriptor(this)
	}

	override fun mutable(): PrimitiveTypeDescriptor = transientMutable

	override fun immutable(): PrimitiveTypeDescriptor = unsupported

	override fun shared(): PrimitiveTypeDescriptor
	{
		assert(mutability === Mutability.SHARED)
		return this
	}

	companion object
	{
		/** The total count of [Types] enum values. */
		const val typesEnumCount = 22

		/**
		 * Extract the [Types] enum value from this primitive
		 * type.
		 *
		 * @param self
		 *   The primitive type.
		 * @return
		 *   The [Types] enum value.
		 */
		private fun extractEnum(self: AvailObject): Types =
			(self.descriptor() as PrimitiveTypeDescriptor).primitiveType

		/**
		 * Extract the [Types] enum value's [Enum.ordinal] from
		 * this primitive type.
		 *
		 * @param self
		 *   The primitive type.
		 * @return
		 *   The [Types] enum value's ordinal.
		 */
		fun extractOrdinal(self: AvailObject): Int = extractEnum(self).ordinal

		/**
		 * Create a partially-initialized primitive type with the given name.
		 * The type's parent will be set later, to facilitate arbitrary
		 * construction order.  Set these fields to [nil][NilDescriptor] to
		 * ensure pointer safety.  We will make all such objects shared after
		 * all the linkages have been set up.
		 *
		 * @param typeNameString
		 *   The name to give the object being initialized.
		 * @return
		 *   The partially initialized type.
		 */
		fun createMutablePrimitiveObjectNamed(
			typeNameString: String
		): AvailObject = transientMutable.create {
			setSlot(NAME, stringFrom(typeNameString).makeShared())
			setSlot(PARENT, nil)
			setSlot(HASH, AvailRuntimeSupport.nextNonzeroHash())
		}

		/**
		 * The sole mutable [PrimitiveTypeDescriptor], only used during early
		 * instantiation.
		 */
		val transientMutable = PrimitiveTypeDescriptor(Mutability.MUTABLE, TOP)
	}

	/**
	 * The [Types] enumeration provides a place and a way
	 * to statically declare the upper stratum of Avail's type lattice,
	 * specifically all [primitive&#32;types][PrimitiveTypeDescriptor].
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 *
	 * @constructor
	 * Construct a new [Types] instance with the specified parent.  Use
	 * [PrimitiveTypeDescriptor] for the new type's descriptor.
	 *
	 * @param parent
	 *   The new type's parent, or `null` if the type has no parent.
	 * @param typeTag
	 *   The [TypeTag] that labels the kind of value that `o` is.
	 * @param instanceTag
	 *   The [TypeTag] that this type's actual *instances* will have.
	 */
	enum class Types
	constructor(
		val parent: Types?,
		val typeTag: TypeTag,
		val instanceTag: TypeTag,
		private val typeName: String? = null)
	{
		/**
		 * The most general type of the type lattice, also written ⊤ and
		 * pronounced "top".  All types are subtypes of this, and all objects
		 * are instances of it.  However, this top type has an additional role:
		 * No variable or argument may be of this type, so the only thing that
		 * can be done with the result of a function call of type ⊤ is to
		 * implicitly discard it.  This is a precise way of making the
		 * traditional distinction between functions and procedures. In fact,
		 * Avail requires all statements except the last one in a block to be of
		 * type ⊤, to ensure that functions are not accidentally used as
		 * procedures – and to ensure that the reader of the code knows it.
		 */
		TOP(null, TypeTag.TOP_TYPE_TAG, TypeTag.TOP_TAG, "⊤"),

		/**
		 * This is the second-most general type in Avail's type lattice.  It is
		 * the only direct descendant of [top&#32;(⊤)][TOP], and all types
		 * except ⊤ are subtypes of it.  Like ⊤, all Avail objects are instances
		 * of `ANY`. Technically there is also a [nil], but that is only used
		 * internally by the Avail machinery (e.g., the value of an unassigned
		 * [variable][VariableDescriptor]) and can never be manipulated by an
		 * Avail program.
		 */
		ANY(TOP, TypeTag.ANY_TYPE_TAG, TypeTag.TOP_TAG),

		/**
		 * This is the kind of all non-types.
		 */
		NONTYPE(ANY, TypeTag.NONTYPE_TYPE_TAG, TypeTag.NONTYPE_TAG),

		/**
		 * This is the kind of all [atoms][AtomDescriptor].  Atoms have fiat
		 * identity and their corresponding type structure is trivial.
		 */
		ATOM(NONTYPE, TypeTag.ATOM_TYPE_TAG, TypeTag.ATOM_TAG),

		/**
		 * This is the kind of all [characters][CharacterDescriptor], as defined
		 * by the [Unicode](http://www.unicode.org) standard.  Note that all
		 * characters in the supplementary multilingual planes are explicitly
		 * supported.
		 */
		CHARACTER(NONTYPE, TypeTag.NONTYPE_TYPE_TAG, TypeTag.CHARACTER_TAG),

		/**
		 * `Number` is the generalization of all numeric types, which includes
		 * [float][FLOAT], [double][DOUBLE], and the
		 * [integer&#32;types][IntegerRangeTypeDescriptor] (which can contain
		 * both [integers][IntegerDescriptor] and the signed
		 * [integral&#32;infinities][InfinityDescriptor]),
		 */
		NUMBER(NONTYPE, TypeTag.NUMBER_TYPE_TAG, TypeTag.NUMBER_TAG),

		/**
		 * The type of all double-precision floating point numbers.  This
		 * includes the double precision
		 * [positive][DoubleDescriptor.doublePositiveInfinity] and
		 * [negative][DoubleDescriptor.doubleNegativeInfinity] infinities and
		 * [Not-a-Number][DoubleDescriptor.doubleNotANumber].
		 */
		DOUBLE(NUMBER, TypeTag.NUMBER_TYPE_TAG, TypeTag.DOUBLE_TAG),

		/**
		 * The type of all single-precision floating point numbers.  This
		 * includes the single precision
		 * [positive][FloatDescriptor.floatPositiveInfinity] and
		 * [negative][FloatDescriptor.floatNegativeInfinity] infinities and
		 * [Not-a-Number][FloatDescriptor.floatNotANumber].
		 */
		FLOAT(NUMBER, TypeTag.NUMBER_TYPE_TAG, TypeTag.FLOAT_TAG),

		/**
		 * All [lexers][LexerDescriptor] are of this kind.
		 */
		LEXER(NONTYPE, TypeTag.NONTYPE_TYPE_TAG, TypeTag.LEXER_TAG),

		/**
		 * All [methods][MethodDescriptor] are of this kind.
		 */
		METHOD(NONTYPE, TypeTag.NONTYPE_TYPE_TAG, TypeTag.METHOD_TAG),

		/**
		 * This is the kind of all
		 * [message&#32;bundles][MessageBundleDescriptor], which are used during
		 * parsing of Avail code.
		 */
		MESSAGE_BUNDLE(NONTYPE, TypeTag.NONTYPE_TYPE_TAG, TypeTag.BUNDLE_TAG),

		/**
		 * This is the kind of all
		 * [definition&#32;parsing&#32;plans][DefinitionParsingPlanDescriptor],
		 * which are used during parsing of Avail code.
		 */
		DEFINITION_PARSING_PLAN(
			NONTYPE, TypeTag.NONTYPE_TYPE_TAG, TypeTag.PARSING_PLAN_TAG),

		/**
		 * The type of macro definitions.
		 */
		MACRO_DEFINITION(NONTYPE, TypeTag.NONTYPE_TYPE_TAG, TypeTag.MACRO_TAG),

		/**
		 * This is the kind of all
		 * [parsing-plans-in-progress][ParsingPlanInProgressDescriptor], which
		 * are used during parsing of Avail code.
		 */
		PARSING_PLAN_IN_PROGRESS(
			NONTYPE,
			TypeTag.NONTYPE_TYPE_TAG,
			TypeTag.PARSING_PLAN_IN_PROGRESS_TAG),

		/**
		 * This is the kind of all
		 * [message&#32;bundle&#32;trees][MessageBundleTreeDescriptor], which
		 * are lazily expanded during parallel parsing of Avail expressions.
		 * They collapse together the cost of parsing method or macro
		 * invocations that start with the same tokens and arguments.
		 */
		MESSAGE_BUNDLE_TREE(
			NONTYPE,
			TypeTag.NONTYPE_TYPE_TAG,
			TypeTag.BUNDLE_TREE_TAG),

		/**
		 * [Tokens][TokenDescriptor] all have the same kind, except for
		 * [literal&#32;tokens][LiteralTokenDescriptor], which are
		 * parametrically typed by the type of value they contain.  They are
		 * produced by a [LexicalScanner] and are consumed by the
		 * [AvailCompiler].
		 */
		TOKEN(NONTYPE, TypeTag.NONTYPE_TYPE_TAG, TypeTag.TOKEN_TAG),

		/**
		 * The general kind of [method signatures][DefinitionDescriptor].
		 */
		DEFINITION(NONTYPE, TypeTag.NONTYPE_TYPE_TAG, TypeTag.DEFINITION_TAG),

		/**
		 * The specific kind of a definition which is an
		 * [abstract&#32;declaration][AbstractDefinitionDescriptor] of a method.
		 */
		ABSTRACT_DEFINITION(
			DEFINITION, TypeTag.NONTYPE_TYPE_TAG, TypeTag.DEFINITION_TAG),

		/**
		 * The specific kind of definition which is a
		 * [forward&#32;declaration][ForwardDefinitionDescriptor].  Such
		 * declarations must be resolved by the end of the module in which they
		 * occur.
		 */
		FORWARD_DEFINITION(
			DEFINITION, TypeTag.NONTYPE_TYPE_TAG, TypeTag.DEFINITION_TAG),

		/**
		 * The specific kind of definition which is an actual
		 * [method&#32;function][MethodDefinitionDescriptor], by far the most
		 * common case.
		 */
		METHOD_DEFINITION(
			DEFINITION, TypeTag.NONTYPE_TYPE_TAG, TypeTag.DEFINITION_TAG),

		/**
		 * [Modules][ModuleDescriptor] are maintained mostly automatically by
		 * Avail's runtime environment.  Modules are not currently visible to
		 * the Avail programmer, but there may still be a need for modules to be
		 * placed in sets and maps maintained by the runtime, so the type story
		 * has to at least be consistent.
		 */
		MODULE(NONTYPE, TypeTag.NONTYPE_TYPE_TAG, TypeTag.MODULE_TAG),

		/**
		 * A [POJO][PojoDescriptor] is a Plain Old Java [Object].  Avail is able
		 * to interface to arbitrary Java code via its implementation of POJOs.
		 * POJOs contain (and conform to) their own POJO types, but that
		 * requires a separate concept of *raw* POJOs.  Avail code only works
		 * with the typed POJOs, but the Avail machinery has to be able to use
		 * the raw POJOs, placing them in sets and doing other things that
		 * occasionally require their kind to be extracted.
		 */
		RAW_POJO(NONTYPE, TypeTag.NONTYPE_TYPE_TAG, TypeTag.POJO_TAG);

		/**
		 * Create the [A_Type] associated with this [Types] entry.
		 */
		lateinit var o: AvailObject
			private set

		companion object
		{
			/**
			 * Stash a [List] of all `Types` enum values.
			 */
			val all = entries.toTypedArray()

			init
			{
				assert(all.size == typesEnumCount)
				// Build all the objects with null fields.
				assert(all.size == typesEnumCount)
				// Connect the objects.
				for (spec in all)
				{
					val o = createMutablePrimitiveObjectNamed(
						when (val typeName = spec.typeName)
						{
							null -> spec.name.lowercase().replace('_', ' ')
							else -> typeName
						})
					spec.o = o

					val descriptor = PrimitiveTypeDescriptor(
						Mutability.SHARED, spec)
					descriptor.finishInitializingPrimitiveTypeWithParent(
						o, spec.parent?.o ?: nil)
					spec.iterableWith { it.parent }.forEach { pointer ->
						val ancestorOrdinal = pointer.ordinal
						spec.superTests[ancestorOrdinal] = true
					}
				}
				// Precompute all type unions and type intersections.
				for (a in all)
				{
					for (b in all)
					{
						// First, compute the union.  Move both pointers up the
						// tree repeatedly until one is a supertype of the
						// other.  Use that supertype as the union.
						val bOrdinal: Int = b.ordinal
						var aAncestor: Types = a
						var bAncestor: Types = b
						val union: Types
						while (true)
						{
							val bAncestorOrdinal: Int = bAncestor.ordinal
							if (a.superTests[bAncestorOrdinal])
							{
								union = bAncestor
								break
							}
							if (b.superTests[aAncestor.ordinal])
							{
								union = aAncestor
								break
							}
							aAncestor = aAncestor.parent!!
							bAncestor = bAncestor.parent!!
							// Neither pointer can be null, because if one were
							// at  "top", it would have already been detected as
							// a supertype of the other.
						}
						a.unionTypes[bOrdinal] = union.o
						assert(a.superTests[union.ordinal])
						assert(b.superTests[union.ordinal])

						// Now compute the type intersection.  Note that since
						// the  types form a tree, any two types related by
						// sub/super typing have an intersection that's the
						// subtype, and all other type pairs have bottom as
						// their intersection.
						a.intersectionTypes[bOrdinal] =
							when
							{
								a.superTests[bOrdinal] -> a.o
								b.superTests[a.ordinal] -> b.o
								else -> bottom
							}
					}
				}
				// Now make all the objects shared.
				for (spec in all)
				{
					spec.o.makeShared()
				}
				// Sanity check them for metacovariance: a<=b -> a.type<=b.type
				for (spec in all)
				{
					if (spec.parent !== null)
					{
						assert(spec.o.isSubtypeOf(spec.parent.o))
						assert(spec.o.isInstanceOfKind(spec.parent.o.kind()))
					}
				}
			}
		}

		/**
		 * A boolean array where the entries correspond to ordinals of other
		 * Types. They are true precisely when the type with that ordinal is a
		 * supertype of the current type.
		 */
		val superTests = BooleanArray(typesEnumCount)

		/**
		 * An array of `A_Type`s, where the entries correspond to ordinals
		 * of other Types, and hold the unions of that type and the current
		 * type.
		 */
		val unionTypes = arrayOfNulls<A_Type>(typesEnumCount)

		/**
		 * An array of `A_Type`s, where the entries correspond to ordinals
		 * of other Types, and hold the intersection of that type and the
		 * current type.
		 */
		val intersectionTypes = arrayOfNulls<A_Type>(typesEnumCount)
	}
}
