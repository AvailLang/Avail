/*
 * TypeDescriptor.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

import com.avail.compiler.AvailCompiler
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.AtomDescriptor
import com.avail.descriptor.bundles.MessageBundleDescriptor
import com.avail.descriptor.bundles.MessageBundleTreeDescriptor
import com.avail.descriptor.character.CharacterDescriptor
import com.avail.descriptor.maps.A_Map
import com.avail.descriptor.methods.AbstractDefinitionDescriptor
import com.avail.descriptor.methods.DefinitionDescriptor
import com.avail.descriptor.methods.ForwardDefinitionDescriptor
import com.avail.descriptor.methods.MethodDefinitionDescriptor
import com.avail.descriptor.module.ModuleDescriptor
import com.avail.descriptor.numbers.A_Number
import com.avail.descriptor.numbers.DoubleDescriptor
import com.avail.descriptor.numbers.FloatDescriptor
import com.avail.descriptor.numbers.InfinityDescriptor
import com.avail.descriptor.numbers.InfinityDescriptor.Companion.positiveInfinity
import com.avail.descriptor.numbers.IntegerDescriptor
import com.avail.descriptor.parsing.DefinitionParsingPlanDescriptor
import com.avail.descriptor.parsing.LexerDescriptor
import com.avail.descriptor.parsing.ParsingPlanInProgressDescriptor
import com.avail.descriptor.pojos.PojoDescriptor
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject
import com.avail.descriptor.representation.IntegerSlotsEnum
import com.avail.descriptor.representation.Mutability
import com.avail.descriptor.representation.NilDescriptor
import com.avail.descriptor.representation.ObjectSlotsEnum
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.tokens.LiteralTokenDescriptor
import com.avail.descriptor.tokens.TokenDescriptor
import com.avail.descriptor.tuples.A_Tuple
import com.avail.descriptor.types.A_Type.Companion.isSubtypeOf
import com.avail.descriptor.types.A_Type.Companion.typeUnion
import com.avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import com.avail.descriptor.types.TypeDescriptor.Types
import com.avail.descriptor.variables.VariableDescriptor
import com.avail.interpreter.execution.AvailLoader
import com.avail.interpreter.levelTwo.operand.TypeRestriction
import java.beans.MethodDescriptor

/**
 * Every object in Avail has a type.  Types are also Avail objects.  The types
 * are related to each other by the [subtype][A_Type.isSubtypeOf] relation
 * in such a way that they form a lattice.  The top of the lattice is
 * [⊤&#32;(pronounced&#32;&quot;top&quot;)][Types.TOP], which is the most
 * general type.  Every object conforms with this type, and every subtype is a
 * subtype of it.  The bottom of the lattice is
 * [⊥&#32;(pronounced&#32;&quot;bottom&quot;)][BottomTypeDescriptor], which is
 * the most specific type.  It has no instances, and it is a subtype of all
 * other types.
 *
 * The type lattice has a number of useful properties, such as closure under
 * type union.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 * Construct a new `TypeDescriptor`.
 *
 * @param mutability
 *   The [mutability][Mutability] of the new descriptor.
 * @param typeTag
 *   The [TypeTag] to embed in the new descriptor.
 * @param objectSlotsEnumClass
 *   The Java [Class] which is a subclass of [ObjectSlotsEnum] and defines this
 *   object's object slots layout, or `null` if there are no object slots.
 * @param integerSlotsEnumClass
 *   The Java [Class] which is a subclass of [IntegerSlotsEnum] and defines this
 *   object's object slots layout, or `null` if there are no integer slots.
 */
abstract class TypeDescriptor protected constructor(
	mutability: Mutability,
	typeTag: TypeTag,
	objectSlotsEnumClass: Class<out ObjectSlotsEnum>?,
	integerSlotsEnumClass: Class<out IntegerSlotsEnum>?)
		: AbstractTypeDescriptor(
			mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass)
{
	/**
	 * The `TypeDescriptor.Types` enumeration provides a place and a way
	 * to statically declare the upper stratum of Avail's type lattice,
	 * specifically all [primitive&#32;types][PrimitiveTypeDescriptor].
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	enum class Types
	{
		/**
		 * The [most&#32;general&#32;type][TopTypeDescriptor] of the type
		 * lattice, also written ⊤ and pronounced "top".  All types are subtypes
		 * of this, and all objects are instances of it.  However, this top type
		 * has an additional role:  No variable or argument may be of this type,
		 * so the only thing that can be done with the result of a function call
		 * of type ⊤ is to implicitly discard it.  This is a precise way of
		 * making the traditional distinction between functions and procedures.
		 * In fact, Avail requires all statements except the last one in a block
		 * to be of type ⊤, to ensure that functions are not accidentally used
		 * as procedures – and to ensure that the reader of the code knows it.
		 */
		TOP(TypeTag.TOP_TYPE_TAG),

		/**
		 * This is the second-most general type in Avail's type lattice.  It is
		 * the only direct descendant of [top&#32;(⊤)][TOP], and all types
		 * except ⊤ are subtypes of it.  Like ⊤, all Avail objects are instances
		 * of `ANY`. Technically there is also a [nil][NilDescriptor.nil], but
		 * that is only used internally by the Avail machinery (e.g., the value
		 * of an unassigned [variable][VariableDescriptor]) and can never be
		 * manipulated by an Avail program.
		 */
		ANY(TOP, TypeTag.ANY_TYPE_TAG),

		/**
		 * This is the kind of all non-types.
		 */
		NONTYPE(ANY, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * This is the kind of all [atoms][AtomDescriptor].  Atoms have fiat
		 * identity and their corresponding type structure is trivial.
		 */
		ATOM(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * This is the kind of all [characters][CharacterDescriptor], as defined
		 * by the [Unicode](http://www.unicode.org) standard.  Note that all
		 * characters in the supplementary multilingual planes are explicitly
		 * supported.
		 */
		CHARACTER(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * `Number` is the generalization of all numeric types, which includes
		 * [float][FLOAT], [double][DOUBLE], and the
		 * [integer&#32;types][IntegerRangeTypeDescriptor] (which can contain
		 * both [integers][IntegerDescriptor] and the signed
		 * [integral&#32;infinities][InfinityDescriptor]),
		 */
		NUMBER(NONTYPE, TypeTag.NUMBER_TYPE_TAG),

		/**
		 * The type of all double-precision floating point numbers.  This
		 * includes the double precision
		 * [positive][DoubleDescriptor.doublePositiveInfinity] and
		 * [negative][DoubleDescriptor.doubleNegativeInfinity] infinities and
		 * [Not-a-Number][DoubleDescriptor.doubleNotANumber].
		 */
		DOUBLE(NUMBER, TypeTag.NUMBER_TYPE_TAG),

		/**
		 * The type of all single-precision floating point numbers.  This
		 * includes the single precision
		 * [positive][FloatDescriptor.floatPositiveInfinity] and
		 * [negative][FloatDescriptor.floatNegativeInfinity] infinities and
		 * [Not-a-Number][FloatDescriptor.floatNotANumber].
		 */
		FLOAT(NUMBER, TypeTag.NUMBER_TYPE_TAG),

		/**
		 * All [lexers][LexerDescriptor] are of this kind.
		 */
		LEXER(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * All [methods][MethodDescriptor] are of this kind.
		 */
		METHOD(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * This is the kind of all
		 * [message&#32;bundles][MessageBundleDescriptor], which are used during
		 * parsing of Avail code.
		 */
		MESSAGE_BUNDLE(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * This is the kind of all
		 * [definition&#32;parsing&#32;plans][DefinitionParsingPlanDescriptor],
		 * which are used during parsing of Avail code.
		 */
		DEFINITION_PARSING_PLAN(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * The type of macro definitions.
		 */
		MACRO_DEFINITION(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * This is the kind of all
		 * [parsing-plans-in-progress][ParsingPlanInProgressDescriptor], which
		 * are used during parsing of Avail code.
		 */
		PARSING_PLAN_IN_PROGRESS(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * This is the kind of all
		 * [message&#32;bundle&#32;trees][MessageBundleTreeDescriptor], which
		 * are lazily expanded during parallel parsing of Avail expressions.
		 * They collapse together the cost of parsing method or macro
		 * invocations that start with the same tokens and arguments.
		 */
		MESSAGE_BUNDLE_TREE(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * [Tokens][TokenDescriptor] all have the same kind, except for
		 * [literal&#32;tokens][LiteralTokenDescriptor], which are
		 * parametrically typed by the type of value they contain.  They are
		 * produced by a [lexical&#32;scanner][AvailLoader.LexicalScanner] and
		 * are consumed by the [parser][AvailCompiler].
		 */
		TOKEN(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * The general kind of [method signatures][DefinitionDescriptor].
		 */
		DEFINITION(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * The specific kind of a definition which is an
		 * [abstract&#32;declaration][AbstractDefinitionDescriptor] of a method.
		 */
		ABSTRACT_DEFINITION(DEFINITION, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * The specific kind of definition which is a
		 * [forward&#32;declaration][ForwardDefinitionDescriptor].  Such
		 * declarations must be resolved by the end of the module in which they
		 * occur.
		 */
		FORWARD_DEFINITION(DEFINITION, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * The specific kind of definition which is an actual
		 * [method&#32;function][MethodDefinitionDescriptor], by far the most
		 * common case.
		 */
		METHOD_DEFINITION(DEFINITION, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * [Modules][ModuleDescriptor] are maintained mostly automatically by
		 * Avail's runtime environment.  Modules are not currently visible to
		 * the Avail programmer, but there may still be a need for modules to be
		 * placed in sets and maps maintained by the runtime, so the type story
		 * has to at least be consistent.
		 */
		MODULE(NONTYPE, TypeTag.NONTYPE_TYPE_TAG),

		/**
		 * A [POJO][PojoDescriptor] is a Plain Old Java [Object].  Avail is able
		 * to interface to arbitrary Java code via its implementation of POJOs.
		 * POJOs contain (and conform to) their own POJO types, but that
		 * requires a separate concept of *raw* POJOs.  Avail code only works
		 * with the typed POJOs, but the Avail machinery has to be able to use
		 * the raw POJOs, placing them in sets and doing other things that
		 * occasionally require their kind to be extracted.
		 */
		RAW_POJO(NONTYPE, TypeTag.NONTYPE_TYPE_TAG);

		companion object
		{
			/** The total count of [Types] enum values.  */
			const val enumCount = 22

			/**
			 * Stash a [List] of all `Types` enum values.
			 */
			private val all = values().toList()

			/**
			 * Answer the previously stashed [List] of all `Types`.
			 *
			 * @return
			 *   The immutable [List] of all `Types`.
			 */
			fun all(): List<Types> = all

			init
			{
				assert(values().size == enumCount)
				// Build all the objects with null fields.
				assert(all.size == enumCount)
				// Connect the objects.
				for (spec in all)
				{
					val o: AvailObject = spec.o
					val descriptor =
						if (spec == TOP)
						{
							TopTypeDescriptor(spec.typeTag, spec)
						}
						else
						{
							PrimitiveTypeDescriptor(spec.typeTag, spec)
						}
					descriptor.finishInitializingPrimitiveTypeWithParent(
						o,
						if (spec.parent === null)
						{
							NilDescriptor.nil
						}
						else
						{
							spec.parent.o
						})
					var pointer: Types? = spec
					while (pointer !== null)
					{
						val ancestorOrdinal: Int = pointer.ordinal
						spec.superTests[ancestorOrdinal] = true
						pointer = pointer.parent
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
		 * The [Types] object representing this type's supertype.
		 */
		val parent: Types?

		/** The [TypeTag] for this primitive type.  */
		val typeTag: TypeTag

		/**
		 * The [AvailObject] itself that this represents.
		 */
		val o: AvailObject

		/**
		 * A boolean array where the entries correspond to ordinals of other
		 * Types. They are true precisely when the type with that ordinal is a
		 * supertype of the current type.
		 */
		val superTests = BooleanArray(enumCount)

		/**
		 * An array of `A_Type`s, where the entries correspond to ordinals
		 * of other Types, and hold the unions of that type and the current
		 * type.
		 */
		val unionTypes = arrayOfNulls<A_Type>(enumCount)

		/**
		 * An array of `A_Type`s, where the entries correspond to ordinals
		 * of other Types, and hold the intersection of that type and the
		 * current type.
		 */
		val intersectionTypes = arrayOfNulls<A_Type>(enumCount)

		/**
		 * Construct the new top `Types` instance.
		 *
		 * @param typeTag
		 *   The [TypeTag] for this primitive type's descriptor.
		 */
		constructor(typeTag: TypeTag)
		{
			parent = null
			this.typeTag = typeTag
			o = PrimitiveTypeDescriptor.createMutablePrimitiveObjectNamed("⊤")
		}

		/**
		 * Construct a new `Types` instance with the specified parent.  Use
		 * [PrimitiveTypeDescriptor] for the new type's descriptor.
		 *
		 * @param parent
		 *   The new type's parent, or `null` if the type has no parent.
		 * @param typeTag
		 *   The [TypeTag] that labels the kind of value that `o` is.
		 */
		constructor(parent: Types?, typeTag: TypeTag)
		{
			this.parent = parent
			this.typeTag = typeTag
			o = PrimitiveTypeDescriptor.createMutablePrimitiveObjectNamed(
				name.lowercase().replace('_', ' '))
		}
	}

	override fun o_AcceptsArgTypesFromFunctionType(
		self: AvailObject,
		functionType: A_Type): Boolean = unsupported

	override fun o_AcceptsListOfArgTypes(
		self: AvailObject,
		argTypes: List<A_Type>): Boolean = unsupported

	override fun o_AcceptsListOfArgValues(
		self: AvailObject,
		argValues: List<A_BasicObject>): Boolean = unsupported

	override fun o_AcceptsTupleOfArgTypes(
		self: AvailObject,
		argTypes: A_Tuple): Boolean = unsupported

	override fun o_AcceptsTupleOfArguments(
		self: AvailObject,
		arguments: A_Tuple): Boolean = unsupported

	override fun o_ArgsTupleType(self: AvailObject): A_Type = unsupported

	override fun o_ContentType(self: AvailObject): A_Type = unsupported

	override fun o_CouldEverBeInvokedWith(
		self: AvailObject,
		argRestrictions: List<TypeRestriction>): Boolean = unsupported

	override fun o_DeclaredExceptions(self: AvailObject): A_Set = unsupported

	override fun o_DefaultType(self: AvailObject): A_Type = unsupported

	abstract override fun o_Equals(
		self: AvailObject,
		another: A_BasicObject): Boolean

	override fun o_FunctionType(self: AvailObject): A_Type = unsupported

	override fun o_FieldTypeAt(self: AvailObject, field: A_Atom): A_Type =
		unsupported

	override fun o_FieldTypeAtOrNull(
		self: AvailObject,
		field: A_Atom
	): A_Type? = unsupported

	override fun o_FieldTypeMap(self: AvailObject): A_Map = unsupported

	override fun o_HasObjectInstance(
		self: AvailObject,
		potentialInstance: AvailObject): Boolean = false

	override fun o_InstanceCount(self: AvailObject): A_Number = positiveInfinity

	override fun o_IsBetterRepresentationThan(
		self: AvailObject,
		anotherObject: A_BasicObject): Boolean = true

	override fun o_RepresentationCostOfTupleType(
		self: AvailObject): Int = unsupported

	override fun o_IsBottom(self: AvailObject): Boolean = false

	override fun o_IsVacuousType(self: AvailObject): Boolean = false

	override fun o_IsInstanceOfKind(
		self: AvailObject,
		aType: A_Type
	): Boolean = self.kind().isSubtypeOf(aType)

	override fun o_IsIntegerRangeType(self: AvailObject): Boolean = false

	override fun o_IsMapType(self: AvailObject): Boolean = false

	override fun o_IsSetType(self: AvailObject): Boolean = false

	override fun o_IsSupertypeOfFiberType(
		self: AvailObject,
		aType: A_Type): Boolean = false

	override fun o_IsSupertypeOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): Boolean = false

	// By default, nothing is a supertype of a variable type unless it
	// states otherwise.
	override fun o_IsSupertypeOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): Boolean = false

	override fun o_IsSupertypeOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): Boolean = false

	override fun o_IsSupertypeOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): Boolean = false

	// By default, nothing is a supertype of an integer range type unless
	// it states otherwise.
	override fun o_IsSupertypeOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): Boolean = false

	// By default, nothing is a supertype of a list phrase type unless it
	// states otherwise.
	override fun o_IsSupertypeOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): Boolean = false

	// By default, nothing is a supertype of a token type unless it states
	// otherwise.
	override fun o_IsSupertypeOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): Boolean = false

	// By default, nothing is a supertype of a literal token type unless it
	// states otherwise.
	override fun o_IsSupertypeOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): Boolean = false

	override fun o_IsSupertypeOfMapType(
		self: AvailObject,
		aMapType: AvailObject): Boolean = false

	// By default, nothing is a supertype of an eager object type unless it
	// states otherwise.
	override fun o_IsSupertypeOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): Boolean = false

	override fun o_IsSupertypeOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): Boolean = false

	override fun o_IsSupertypeOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): Boolean = false

	/* Check if object (some specialized type) is a supertype of
	 * aPrimitiveType (some primitive type).  The only primitive type this
	 * specialized type could be a supertype of is bottom, but
	 * bottom doesn't dispatch this message.  Overridden in
	 * PrimitiveTypeDescriptor.
	 */
	override fun o_IsSupertypeOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): Boolean = false

	override fun o_IsSupertypeOfSetType(
		self: AvailObject,
		aSetType: A_Type): Boolean = false

	override fun o_IsSupertypeOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): Boolean = false

	override fun o_IsSupertypeOfEnumerationType(
		self: AvailObject,
		anEnumerationType: A_Type): Boolean = false

	override fun o_IsSupertypeOfPojoBottomType(
		self: AvailObject,
		aPojoType: A_Type): Boolean = false

	override fun o_IsTop(self: AvailObject): Boolean = false

	override fun o_IsTupleType(self: AvailObject): Boolean = false

	override fun o_KeyType(self: AvailObject): A_Type = unsupported

	override fun o_LowerBound(self: AvailObject): A_Number = unsupported

	override fun o_LowerInclusive(self: AvailObject): Boolean = unsupported

	// Most Avail types are opaque to Java, and can be characterized by the
	// class of AvailObject.
	override fun o_MarshalToJava(
		self: AvailObject,
		classHint: Class<*>?
	): Any? = AvailObject::class.java

	override fun o_Parent(self: AvailObject): A_BasicObject = unsupported

	override fun o_RangeIncludesLong(self: AvailObject, aLong: Long) = false

	override fun o_ReturnType(self: AvailObject): A_Type = unsupported

	override fun o_SizeRange(self: AvailObject): A_Type = unsupported

	override fun o_TrimType(self: AvailObject, typeToRemove: A_Type): A_Type
	{
		if (self.isSubtypeOf(typeToRemove)) return bottom
		return self
	}

	override fun o_TypeAtIndex(self: AvailObject, index: Int): A_Type =
		unsupported

	override fun o_TypeIntersectionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfMapType(
		self: AvailObject,
		aMapType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): A_Type = bottom

	override fun o_TypeIntersectionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type =
			if (Types.NONTYPE.superTests[primitiveTypeEnum.ordinal]) self
			else bottom


	override fun o_TypeIntersectionOfSetType(
		self: AvailObject,
		aSetType: A_Type): A_Type = bottom

	override fun o_TypeIntersectionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): A_Type = bottom

	override fun o_TypeTuple(self: AvailObject): A_Tuple = unsupported

	override fun o_TypeUnionOfFiberType(
		self: AvailObject,
		aFiberType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfFunctionType(
		self: AvailObject,
		aFunctionType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfVariableType(
		self: AvailObject,
		aVariableType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfContinuationType(
		self: AvailObject,
		aContinuationType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfCompiledCodeType(
		self: AvailObject,
		aCompiledCodeType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfIntegerRangeType(
		self: AvailObject,
		anIntegerRangeType: A_Type): A_Type = self.typeUnion(Types.NUMBER.o)

	override fun o_TypeUnionOfListNodeType(
		self: AvailObject,
		aListNodeType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfTokenType(
		self: AvailObject,
		aTokenType: A_Type): A_Type = self.typeUnion(Types.TOKEN.o)

	override fun o_TypeUnionOfLiteralTokenType(
		self: AvailObject,
		aLiteralTokenType: A_Type): A_Type = self.typeUnion(Types.TOKEN.o)

	override fun o_TypeUnionOfMapType(
		self: AvailObject,
		aMapType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfObjectType(
		self: AvailObject,
		anObjectType: AvailObject): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfPhraseType(
		self: AvailObject,
		aPhraseType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfPojoType(
		self: AvailObject,
		aPojoType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfPrimitiveTypeEnum(
		self: AvailObject,
		primitiveTypeEnum: Types): A_Type
	{
		var anotherAncestor = primitiveTypeEnum
		while (true)
		{
			if (self.isSubtypeOf(anotherAncestor.o))
			{
				return anotherAncestor.o
			}
			anotherAncestor = anotherAncestor.parent!!
		}
	}

	override fun o_TypeUnionOfSetType(
		self: AvailObject,
		aSetType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_TypeUnionOfTupleType(
		self: AvailObject,
		aTupleType: A_Type): A_Type = self.typeUnion(Types.NONTYPE.o)

	override fun o_UnionOfTypesAtThrough(
		self: AvailObject,
		startIndex: Int,
		endIndex: Int): A_Type = unsupported

	override fun o_UpperBound(
		self: AvailObject): A_Number = unsupported

	override fun o_UpperInclusive(self: AvailObject): Boolean = unsupported

	override fun o_ValueType(self: AvailObject): A_Type = unsupported

	companion object
	{
		/**
		 * Answer whether the first type is a proper subtype of the second type,
		 * meaning that it's a subtype but not equal.
		 *
		 * @param type1
		 *   The purported subtype.
		 * @param type2
		 *   The purported supertype.
		 * @return
		 *   If type1 is a subtype of but not equal to type2.
		 */
		fun isProperSubtype(
			type1: A_Type,
			type2: A_Type): Boolean
		{
			return !type1.equals(type2) && type1.isSubtypeOf(type2)
		}
	}
}
