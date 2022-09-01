/*
 * A_Type.kt
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

import avail.descriptor.functions.FunctionDescriptor
import avail.descriptor.maps.A_Map
import avail.descriptor.methods.StylerDescriptor.SystemStyle
import avail.descriptor.numbers.A_Number
import avail.descriptor.objects.ObjectLayoutVariant
import avail.descriptor.objects.ObjectTypeDescriptor
import avail.descriptor.phrases.A_Phrase
import avail.descriptor.phrases.BlockPhraseDescriptor
import avail.descriptor.phrases.PhraseDescriptor
import avail.descriptor.phrases.SendPhraseDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.sets.A_Set
import avail.descriptor.tokens.TokenDescriptor
import avail.descriptor.tuples.A_Tuple
import avail.descriptor.tuples.TupleDescriptor
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottomMeta
import avail.descriptor.types.EnumerationTypeDescriptor.Companion.booleanType
import avail.descriptor.types.InstanceMetaDescriptor.Companion.instanceMeta
import avail.descriptor.types.InstanceMetaDescriptor.Companion.topMeta
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.PARSE_PHRASE
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.CHARACTER
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.NUMBER
import avail.interpreter.levelTwo.operand.TypeRestriction
import avail.optimizer.jvm.CheckedMethod
import avail.optimizer.jvm.CheckedMethod.Companion.staticMethod
import avail.optimizer.jvm.ReferencedInGeneratedCode

/**
 * `A_Type` is an interface that specifies the operations specific to all of
 * Avail's types.  It's a sub-interface of [A_BasicObject], the interface that
 * defines the behavior that all AvailObjects are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Type : A_BasicObject
{
	/**
	 * Answer whether this type is ⊥ ([bottom][BottomTypeDescriptor]), the most
	 * specific type.
	 *
	 * @return
	 *   Whether the type is bottom.
	 */
	val isBottom: Boolean

	/**
	 * @return
	 */
	val isEnumeration: Boolean

	/**
	 * Answer whether this type is ⊤ ([top][PrimitiveTypeDescriptor.Types.TOP]),
	 * the most general type.
	 *
	 * @return
	 *   Whether the type is type.
	 */
	val isTop: Boolean

	/**
	 * Answer whether this type is known to have no instances.  For example, the
	 * [bottom&#32;type][BottomTypeDescriptor] (denoted ⊥) has no instances.
	 *
	 * @return
	 *   Whether the type is known to have no instances.
	 */
	val isVacuousType: Boolean

	companion object
	{
		/**
		 * Answer whether the [argument&#32;types][argsTupleType]
		 * supported by the specified
		 * [function&#32;type][FunctionTypeDescriptor] are acceptable argument
		 * types for invoking a [function][FunctionDescriptor] whose type is the
		 * receiver.
		 *
		 * @param functionType
		 *   A function type.
		 * @return
		 *   `true` if the arguments of the receiver are, pairwise, more general
		 *   than those of `functionType`, `false` otherwise.
		 */
		fun A_Type.acceptsArgTypesFromFunctionType(
			functionType: A_Type
		): Boolean = dispatch {
			o_AcceptsArgTypesFromFunctionType(it, functionType)
		}

		/**
		 * Answer whether these are acceptable
		 * [argument&#32;types][TypeDescriptor] for invoking a
		 * [function][FunctionDescriptor] whose type is the receiver.
		 *
		 * @param argTypes
		 *   A list containing the argument types to be checked.
		 * @return
		 *   `true` if the arguments of the receiver are, pairwise, more general
		 *   than those within the `argTypes` list, `false` otherwise.
		 */
		fun A_Type.acceptsListOfArgTypes(
			argTypes: List<A_Type>
		): Boolean = dispatch { o_AcceptsListOfArgTypes(it, argTypes) }

		/**
		 * Answer whether these are acceptable arguments for invoking a
		 * [function][FunctionDescriptor] whose type is the receiver.
		 *
		 * @param argValues
		 *   A list containing the argument values to be checked.
		 * @return
		 *   `true` if the arguments of the receiver are, pairwise, more general
		 *   than the types of the values within the `argValues` list, `false`
		 *   otherwise.
		 */
		fun A_Type.acceptsListOfArgValues(
			argValues: List<A_BasicObject>
		): Boolean = dispatch { o_AcceptsListOfArgValues(it, argValues) }

		/**
		 * Answer whether these are acceptable
		 * [argument&#32;types][TypeDescriptor] for invoking a
		 * [function][FunctionDescriptor] that is an instance of the receiver.
		 * There may be more entries in the [tuple][TupleDescriptor] than are
		 * required by the [function type][FunctionTypeDescriptor].
		 *
		 * @param argTypes
		 *   A tuple containing the argument types to be checked.
		 * @return
		 *   `true` if the arguments of the receiver are, pairwise, more general
		 *   than the corresponding elements of the `argTypes` tuple, `false`
		 *   otherwise.
		 */
		fun A_Type.acceptsTupleOfArgTypes(argTypes: A_Tuple): Boolean =
			dispatch { o_AcceptsTupleOfArgTypes(it, argTypes) }

		/**
		 * Answer whether these are acceptable arguments for invoking a
		 * [function][FunctionDescriptor] that is an instance of the receiver.
		 * There may be more entries in the [tuple][TupleDescriptor] than are
		 * required by the [function type][FunctionTypeDescriptor].
		 *
		 * @param arguments
		 *   A tuple containing the argument values to be checked.
		 * @return
		 *   `true` if the arguments of the receiver are, pairwise, more general
		 *   than the types of the corresponding elements of the `arguments`
		 *   tuple, `false` otherwise.
		 */
		fun A_Type.acceptsTupleOfArguments(arguments: A_Tuple): Boolean =
			dispatch { o_AcceptsTupleOfArguments(it, arguments) }

		/**
		 * Answer the tuple type describing this function type's argument types.
		 *
		 * @return
		 *   The tuple type for a function type's arguments.
		 */
		val A_Type.argsTupleType: A_Type
			get() = dispatch { o_ArgsTupleType(it) }

		/**
		 * Given an [A_Type], compute the most specific [TypeTag] that is
		 * ensured for instances of that type.
		 *
		 * @return
		 *   The [TypeTag] of this type's instances.
		 */
		fun A_Type.computeInstanceTag(): TypeTag =
			dispatch { o_ComputeInstanceTag(it) }

		/**
		 * Answer the type of elements that this set type's sets may hold.
		 *
		 * @return
		 *   The set type's content type.
		 */
		val A_Type.contentType: A_Type get() = dispatch { o_ContentType(it) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param argRestrictions
		 * @return
		 */
		fun A_Type.couldEverBeInvokedWith(
			argRestrictions: List<TypeRestriction>
		): Boolean = dispatch { o_CouldEverBeInvokedWith(it, argRestrictions) }

		/**
		 * Also declared in [A_Phrase] for
		 * [block&#32;phrases][BlockPhraseDescriptor] and
		 * [send&#32;phrases][SendPhraseDescriptor].
		 *
		 * @return
		 *   The set of declared exception types.
		 */
		val A_Type.declaredExceptions: A_Set
			get() = dispatch { o_DeclaredExceptions(it) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @return
		 */
		val A_Type.defaultType: A_Type
			get() = dispatch { o_DefaultType(it) }

		/**
		 * Return the phrase type's expression type, which is the type of object
		 * that will be produced by phrases of that type.
		 *
		 * Also implemented in [A_Phrase] (for phrase instances).
		 *
		 * @return
		 *   The [type][TypeDescriptor] of the [AvailObject] that will be
		 *   produced by this type of phrase.
		 */
		val A_Type.phraseTypeExpressionType: A_Type
			get() = dispatch { o_PhraseTypeExpressionType(it) }

		/**
		 * Given an [object&#32;type][ObjectTypeDescriptor], answer its map from
		 * fields to types.
		 *
		 * @return
		 *   The map of field types.
		 */
		val A_Type.fieldTypeMap: A_Map get() = dispatch { o_FieldTypeMap(it) }

		/**
		 * @return
		 */
		val A_Type.functionType: A_Type get() = dispatch { o_FunctionType(it) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aType
		 * @return
		 */
		fun A_Type.isSubtypeOf(aType: A_Type): Boolean =
			dispatch { o_IsSubtypeOf(it, aType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aVariableType
		 * @return
		 */
		fun A_Type.isSupertypeOfVariableType(aVariableType: A_Type): Boolean =
			dispatch { o_IsSupertypeOfVariableType(it, aVariableType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aContinuationType
		 * @return
		 */
		fun A_Type.isSupertypeOfContinuationType(
			aContinuationType: A_Type
		): Boolean = dispatch {
			o_IsSupertypeOfContinuationType(it, aContinuationType)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aFiberType
		 * @return
		 */
		fun A_Type.isSupertypeOfFiberType(aFiberType: A_Type): Boolean =
			dispatch { o_IsSupertypeOfFiberType(it, aFiberType) }

		/**
		 * Dispatch to the descriptor.
		 * @param aFunctionType
		 * @return
		 */
		fun A_Type.isSupertypeOfFunctionType(aFunctionType: A_Type): Boolean =
			dispatch { o_IsSupertypeOfFunctionType(it, aFunctionType) }

		/**
		 * Dispatch to the descriptor.
		 * @param anIntegerRangeType
		 * @return
		 */
		fun A_Type.isSupertypeOfIntegerRangeType(
			anIntegerRangeType: A_Type
		): Boolean = dispatch {
			o_IsSupertypeOfIntegerRangeType(it, anIntegerRangeType)
		}

		/**
		 * Dispatch to the descriptor.
		 * @param aTokenType
		 * @return
		 */
		fun A_Type.isSupertypeOfTokenType(aTokenType: A_Type): Boolean =
			dispatch { o_IsSupertypeOfTokenType(it, aTokenType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aLiteralTokenType
		 * @return
		 */
		fun A_Type.isSupertypeOfLiteralTokenType(
			aLiteralTokenType: A_Type
		): Boolean = dispatch {
			o_IsSupertypeOfLiteralTokenType(it, aLiteralTokenType)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aMapType
		 * @return
		 */
		fun A_Type.isSupertypeOfMapType(aMapType: AvailObject): Boolean =
			dispatch { o_IsSupertypeOfMapType(it, aMapType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param anObjectType
		 * @return
		 */
		fun A_Type.isSupertypeOfObjectType(anObjectType: AvailObject): Boolean =
			dispatch { o_IsSupertypeOfObjectType(it, anObjectType) }

		/**
		 * @param aPhraseType
		 * @return
		 */
		fun A_Type.isSupertypeOfPhraseType(aPhraseType: A_Type): Boolean =
			dispatch { o_IsSupertypeOfPhraseType(it, aPhraseType) }

		/**
		 * Dispatch to the descriptor
		 *
		 * @param aPojoType
		 * @return
		 */
		fun A_Type.isSupertypeOfPojoType(aPojoType: A_Type): Boolean =
			dispatch { o_IsSupertypeOfPojoType(it, aPojoType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param primitiveTypeEnum
		 * @return
		 */
		fun A_Type.isSupertypeOfPrimitiveTypeEnum(
			primitiveTypeEnum: PrimitiveTypeDescriptor.Types
		): Boolean = dispatch {
			o_IsSupertypeOfPrimitiveTypeEnum(it, primitiveTypeEnum)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aSetType
		 * @return
		 */
		fun A_Type.isSupertypeOfSetType(aSetType: A_Type): Boolean =
			dispatch { o_IsSupertypeOfSetType(it, aSetType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aTupleType
		 * @return
		 */
		fun A_Type.isSupertypeOfTupleType(aTupleType: A_Type): Boolean =
			dispatch { o_IsSupertypeOfTupleType(it, aTupleType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param anEnumerationType
		 * @return
		 */
		fun A_Type.isSupertypeOfEnumerationType(
			anEnumerationType: A_Type
		): Boolean = dispatch {
			o_IsSupertypeOfEnumerationType(it, anEnumerationType)
		}

		/**
		 * @param aCompiledCodeType
		 * @return
		 */
		fun A_Type.isSupertypeOfCompiledCodeType(
			aCompiledCodeType: A_Type
		): Boolean = dispatch {
			o_IsSupertypeOfCompiledCodeType(it, aCompiledCodeType)
		}

		/**
		 * @param aPojoType
		 * @return
		 */
		fun A_Type.isSupertypeOfPojoBottomType(aPojoType: A_Type): Boolean =
			dispatch { o_IsSupertypeOfPojoBottomType(it, aPojoType) }

		/**
		 * Dispatch to the descriptor.
		 * @return
		 */
		val A_Type.keyType: A_Type get() = dispatch { o_KeyType(it) }

		/**
		 * Dispatch to the descriptor.
		 * @return
		 */
		val A_Type.lowerBound: A_Number get() = dispatch { o_LowerBound(it) }

		/**
		 * Dispatch to the descriptor.
		 * @return
		 */
		val A_Type.lowerInclusive: Boolean
			get() = dispatch { o_LowerInclusive(it) }

		/**
		 * Extract the [ObjectLayoutVariant] from an object type.
		 */
		val A_Type.objectTypeVariant: ObjectLayoutVariant
			get() = dispatch { o_ObjectTypeVariant(it) }

		/**
		 * Dispatch to the descriptor.
		 * @return
		 */
		val A_Type.parent: A_BasicObject get() = dispatch { o_Parent(it) }

		/**
		 * Also declared in [A_Phrase] for [phrases][PhraseDescriptor], not just
		 * phrase types.
		 *
		 * @return
		 *   Answer the phrase's PhraseKind.
		 */
		val A_Type.phraseKind: PhraseKind get() = dispatch { o_PhraseKind(it) }

		/**
		 * @return
		 */
		val A_Type.readType: A_Type get() = dispatch { o_ReadType(it) }

		/**
		 * Also declared in [A_Phrase] for
		 * [block&#32;phrases][BlockPhraseDescriptor] and
		 * [send&#32;phrases][SendPhraseDescriptor].
		 *
		 * @return
		 */
		val A_Type.returnType: A_Type get() = dispatch { o_ReturnType(it) }

		/**
		 * Dispatch to the descriptor.
		 * @return
		 */
		val A_Type.sizeRange: A_Type get() = dispatch { o_SizeRange(it) }

		/**
		 * Answer a type that includes *at least* all values from the receiver
		 * that aren't also in the [typeToRemove].  Note that the answer can be
		 * conservatively large, and is typically just the receiver, even if the
		 * types overlap.
		 *
		 * The resulting type *must* include every element of the receiver that
		 * isn't in [typeToRemove], but it *may* include additional elements
		 * that are in both the receiver and [typeToRemove].  It must not
		 * include elements that were not in the receiver.
		 *
		 * The receiver may be destroyed or recycled if it's mutable.
		 *
		 * This provides an opportunity to tighten the bounds for integer
		 * ranges, or reduce the element and size bounds on map/tuple/set types.
		 */
		fun A_Type.trimType(typeToRemove: A_Type) =
			dispatch { o_TrimType(it, typeToRemove) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param startIndex
		 * @param endIndex
		 * @return
		 */
		fun A_Type.tupleOfTypesFromTo(startIndex: Int, endIndex: Int): A_Tuple =
			dispatch { o_TupleOfTypesFromTo(it, startIndex, endIndex) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param index
		 * @return
		 */
		fun A_Type.typeAtIndex(index: Int): A_Type =
			dispatch { o_TypeAtIndex(it, index) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @return
		 */
		val A_Type.typeTuple: A_Tuple get() = dispatch { o_TypeTuple(it) }

		/**
		 * @param aCompiledCodeType
		 * @return
		 */
		fun A_Type.typeUnionOfCompiledCodeType(
			aCompiledCodeType: A_Type
		): A_Type = dispatch {
			o_TypeUnionOfCompiledCodeType(it, aCompiledCodeType)
		}

		/**
		 * @param aFusedPojoType
		 * @return
		 */
		fun A_Type.typeIntersectionOfPojoFusedType(
			aFusedPojoType: A_Type
		): A_Type = dispatch {
			o_TypeIntersectionOfPojoFusedType(it, aFusedPojoType)
		}

		/**
		 * @param anUnfusedPojoType
		 * @return
		 */
		fun A_Type.typeIntersectionOfPojoUnfusedType(
			anUnfusedPojoType: A_Type
		): A_Type = dispatch {
			o_TypeIntersectionOfPojoUnfusedType(it, anUnfusedPojoType)
		}

		/**
		 * @param aFusedPojoType
		 * @return
		 */
		fun A_Type.typeUnionOfPojoFusedType(aFusedPojoType: A_Type): A_Type =
			dispatch { o_TypeUnionOfPojoFusedType(it, aFusedPojoType) }

		/**
		 * @param anUnfusedPojoType
		 * @return
		 */
		fun A_Type.typeUnionOfPojoUnfusedType(
			anUnfusedPojoType: A_Type
		): A_Type = dispatch {
			o_TypeUnionOfPojoUnfusedType(it, anUnfusedPojoType)
		}

		/**
		 * @param aTokenType
		 * @return
		 */
		fun A_Type.typeIntersectionOfTokenType(aTokenType: A_Type): A_Type =
			dispatch { o_TypeIntersectionOfTokenType(it, aTokenType) }

		/**
		 * @param aLiteralTokenType
		 * @return
		 */
		fun A_Type.typeIntersectionOfLiteralTokenType(
			aLiteralTokenType: A_Type
		): A_Type = dispatch {
			o_TypeIntersectionOfLiteralTokenType(it, aLiteralTokenType)
		}

		/**
		 * @param aTokenType
		 * @return
		 */
		fun A_Type.typeUnionOfTokenType(aTokenType: A_Type): A_Type =
			dispatch { o_TypeUnionOfTokenType(it, aTokenType) }

		/**
		 * @param aLiteralTokenType
		 * @return
		 */
		fun A_Type.typeUnionOfLiteralTokenType(
			aLiteralTokenType: A_Type
		): A_Type = dispatch {
			o_TypeUnionOfLiteralTokenType(it, aLiteralTokenType)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param another
		 * @return
		 */
		fun A_Type.typeIntersection(another: A_Type): A_Type =
			dispatch { o_TypeIntersection(it, another) }

		/**
		 * @param aCompiledCodeType
		 * @return
		 */
		fun A_Type.typeIntersectionOfCompiledCodeType(
			aCompiledCodeType: A_Type
		): A_Type = dispatch {
			o_TypeIntersectionOfCompiledCodeType(it, aCompiledCodeType)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aFiberType
		 * @return
		 */
		fun A_Type.typeIntersectionOfFiberType(aFiberType: A_Type): A_Type =
			dispatch { o_TypeIntersectionOfFiberType(it, aFiberType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aFunctionType
		 * @return
		 */
		fun A_Type.typeIntersectionOfFunctionType(
			aFunctionType: A_Type
		): A_Type = dispatch {
			o_TypeIntersectionOfFunctionType(it, aFunctionType)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aListNodeType
		 * @return
		 */
		fun A_Type.typeIntersectionOfListNodeType(
			aListNodeType: A_Type
		): A_Type = dispatch {
			o_TypeIntersectionOfListNodeType(it, aListNodeType)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aVariableType
		 * @return
		 */
		fun A_Type.typeIntersectionOfVariableType(
			aVariableType: A_Type
		): A_Type = dispatch {
			o_TypeIntersectionOfVariableType(it, aVariableType)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aContinuationType
		 * @return
		 */
		fun A_Type.typeIntersectionOfContinuationType(
			aContinuationType: A_Type
		): A_Type = dispatch {
			o_TypeIntersectionOfContinuationType(it, aContinuationType)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param anIntegerRangeType
		 * @return
		 */
		fun A_Type.typeIntersectionOfIntegerRangeType(
			anIntegerRangeType: A_Type
		): A_Type = dispatch {
			o_TypeIntersectionOfIntegerRangeType(it, anIntegerRangeType)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aMapType
		 * @return
		 */
		fun A_Type.typeIntersectionOfMapType(aMapType: A_Type): A_Type =
			dispatch { o_TypeIntersectionOfMapType(it, aMapType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param anObjectType
		 * @return
		 */
		fun A_Type.typeIntersectionOfObjectType(
			anObjectType: AvailObject
		): A_Type = dispatch {
			o_TypeIntersectionOfObjectType(it, anObjectType)
		}

		/**
		 * @param aPhraseType
		 * @return
		 */
		fun A_Type.typeIntersectionOfPhraseType(aPhraseType: A_Type): A_Type =
			dispatch { o_TypeIntersectionOfPhraseType(it, aPhraseType) }

		/**
		 * @param aPojoType
		 * @return
		 */
		fun A_Type.typeIntersectionOfPojoType(aPojoType: A_Type): A_Type =
			dispatch { o_TypeIntersectionOfPojoType(it, aPojoType) }

		/**
		 * @param primitiveTypeEnum
		 * @return
		 */
		fun A_Type.typeIntersectionOfPrimitiveTypeEnum(
			primitiveTypeEnum: PrimitiveTypeDescriptor.Types
		): A_Type = dispatch {
			o_TypeIntersectionOfPrimitiveTypeEnum(it, primitiveTypeEnum)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aSetType
		 * @return
		 */
		fun A_Type.typeIntersectionOfSetType(aSetType: A_Type): A_Type =
			dispatch { o_TypeIntersectionOfSetType(it, aSetType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aTupleType
		 * @return
		 */
		fun A_Type.typeIntersectionOfTupleType(aTupleType: A_Type): A_Type =
			dispatch { o_TypeIntersectionOfTupleType(it, aTupleType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param another
		 * @return
		 */
		fun A_Type.typeUnion(another: A_Type): A_Type =
			dispatch { o_TypeUnion(it, another) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aFiberType
		 * @return
		 */
		fun A_Type.typeUnionOfFiberType(aFiberType: A_Type): A_Type =
			dispatch { o_TypeUnionOfFiberType(it, aFiberType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aFunctionType
		 * @return
		 */
		fun A_Type.typeUnionOfFunctionType(aFunctionType: A_Type): A_Type =
			dispatch { o_TypeUnionOfFunctionType(it, aFunctionType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aVariableType
		 * @return
		 */
		fun A_Type.typeUnionOfVariableType(aVariableType: A_Type): A_Type =
			dispatch { o_TypeUnionOfVariableType(it, aVariableType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aContinuationType
		 * @return
		 */
		fun A_Type.typeUnionOfContinuationType(
			aContinuationType: A_Type
		): A_Type = dispatch {
			o_TypeUnionOfContinuationType(it, aContinuationType)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param anIntegerRangeType
		 * @return
		 */
		fun A_Type.typeUnionOfIntegerRangeType(
			anIntegerRangeType: A_Type
		): A_Type = dispatch {
			o_TypeUnionOfIntegerRangeType(it, anIntegerRangeType)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aListNodeType
		 * @return
		 */
		fun A_Type.typeUnionOfListNodeType(aListNodeType: A_Type): A_Type =
			dispatch { o_TypeUnionOfListNodeType(it, aListNodeType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aMapType
		 * @return
		 */
		fun A_Type.typeUnionOfMapType(aMapType: A_Type): A_Type =
			dispatch { o_TypeUnionOfMapType(it, aMapType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param anObjectType
		 * @return
		 */
		fun A_Type.typeUnionOfObjectType(anObjectType: AvailObject): A_Type =
			dispatch { o_TypeUnionOfObjectType(it, anObjectType) }

		/**
		 * @param aPhraseType
		 * @return
		 */
		fun A_Type.typeUnionOfPhraseType(aPhraseType: A_Type): A_Type =
			dispatch { o_TypeUnionOfPhraseType(it, aPhraseType) }

		/**
		 * @param aPojoType
		 * @return
		 */
		fun A_Type.typeUnionOfPojoType(aPojoType: A_Type): A_Type =
			dispatch { o_TypeUnionOfPojoType(it, aPojoType) }

		/**
		 * @param primitiveTypeEnum
		 * @return
		 */
		fun A_Type.typeUnionOfPrimitiveTypeEnum(
			primitiveTypeEnum: PrimitiveTypeDescriptor.Types
		): A_Type = dispatch {
			o_TypeUnionOfPrimitiveTypeEnum(it, primitiveTypeEnum)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aSetType
		 * @return
		 */
		fun A_Type.typeUnionOfSetType(aSetType: A_Type): A_Type =
			dispatch { o_TypeUnionOfSetType(it, aSetType) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param aTupleType
		 * @return
		 */
		fun A_Type.typeUnionOfTupleType(aTupleType: A_Type): A_Type =
			dispatch { o_TypeUnionOfTupleType(it, aTupleType) }

		/**
		 * @return
		 */
		val A_Type.typeVariables: A_Map get() = dispatch { o_TypeVariables(it) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param startIndex
		 * @param endIndex
		 * @return
		 */
		fun A_Type.unionOfTypesAtThrough(
			startIndex: Int,
			endIndex: Int
		): A_Type = dispatch {
			o_UnionOfTypesAtThrough(it, startIndex, endIndex)
		}

		/**
		 * Dispatch to the descriptor.
		 *
		 * @return
		 */
		val A_Type.upperBound: A_Number get() = dispatch { o_UpperBound(it) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @return
		 */
		val A_Type.upperInclusive: Boolean
			get() = dispatch { o_UpperInclusive(it) }

		/**
		 * @return
		 */
		val A_Type.writeType: A_Type get() = dispatch { o_WriteType(it) }

		/**
		 * Compute a [type][TypeDescriptor] that is an ancestor of the receiver,
		 * but is not an [enumeration][AbstractEnumerationTypeDescriptor].
		 * Choose the most specific such type.  Fail if the receiver is not
		 * itself an enumeration.  Also fail if the receiver is
		 * [bottom][BottomTypeDescriptor].
		 *
		 * @return
		 *   The must specific non-union supertype.
		 */
		fun A_Type.computeSuperkind(): A_Type =
			dispatch { o_ComputeSuperkind(it) }

		/**
		 * @return
		 */
		val A_Type.valueType: A_Type get() = dispatch { o_ValueType(it) }

		/**
		 * @return
		 */
		val A_Type.fieldTypeTuple: A_Tuple
			get() = dispatch { o_FieldTypeTuple(it) }

		/**
		 * Dispatch to the descriptor.
		 *
		 * @param potentialInstance
		 * @return
		 */
		fun A_Type.hasObjectInstance(potentialInstance: AvailObject): Boolean =
			dispatch { o_HasObjectInstance(it, potentialInstance) }

		/**
		 * @return
		 */
		val A_Type.instance: AvailObject get() = dispatch { o_Instance(it) }

		/**
		 * @return
		 */
		val A_Type.instanceCount: A_Number
			get() = dispatch { o_InstanceCount(it) }

		/**
		 * @return
		 */
		val A_Type.instances: A_Set get() = dispatch { o_Instances(it) }

		/**
		 * @return
		 */
		val A_Type.tokenType: TokenDescriptor.TokenType
			get() = dispatch { o_TokenType(it) }

		/**
		 * @return
		 */
		val A_Type.literalType: A_Type get() = dispatch { o_LiteralType(it) }

		/**
		 * @param aLong
		 * @return
		 */
		fun A_Type.rangeIncludesLong(aLong: Long): Boolean =
			dispatch { o_RangeIncludesLong(it, aLong) }

		/**
		 * Also declared in A_Phrase, so the same operation applies both to
		 * phrases and to phrase types.
		 *
		 * @param expectedPhraseKind
		 *   The [PhraseKind] to test this phrase type against.
		 * @return
		 *   Whether the receiver, a phrase type, has a [phraseKind] at or
		 *   below the specified [PhraseKind].
		 */
		fun A_Type.phraseKindIsUnder(expectedPhraseKind: PhraseKind): Boolean =
			dispatch { o_PhraseKindIsUnder(it, expectedPhraseKind) }

		/**
		 * Answer the type of the subexpressions tuple that instances (list
		 * phrases) of me (a list phrase type) must have.
		 *
		 * @return
		 *   A tuple type of phrases.
		 */
		val A_Type.subexpressionsTupleType: A_Type
			get() = dispatch { o_SubexpressionsTupleType(it) }

		/**
		 * Answer whether the receiver, a type, is a supertype of the given
		 * [list&#32;phrase&#32;type][ListPhraseTypeDescriptor].
		 *
		 * @param aListNodeType
		 *   The list phrase type.
		 * @return
		 *   Whether the receiver is a supertype of the given type.
		 */
		fun A_Type.isSupertypeOfListNodeType(aListNodeType: A_Type): Boolean =
			dispatch { o_IsSupertypeOfListNodeType(it, aListNodeType) }

		/**
		 * Given an [A_Type], determine which TypeTag its *instances* will
		 * comply with.
		 */
		val A_Type.instanceTag: TypeTag
			get() = dispatch { o_InstanceTag(it) }

		/**
		 * The appropriate [SystemStyle] for the receiver, based on whether it's
		 * a metatype or just an ordinary type.  Answer `null` if the type does
		 * not indicate any particular system style.
		 */
		val A_Type.systemStyleForType get() =
			when
			{
				isSubtypeOf(bottomMeta) -> SystemStyle.TYPE
				isSubtypeOf(instanceMeta(bottomMeta)) -> SystemStyle.METATYPE
				isSubtypeOf(instanceMeta(PARSE_PHRASE.mostGeneralType)) ->
					SystemStyle.PHRASE_TYPE
				isSubtypeOf(instanceMeta(topMeta())) -> SystemStyle.METATYPE
				isSubtypeOf(PARSE_PHRASE.mostGeneralType) ->
					SystemStyle.PHRASE
				isSubtypeOf(NUMBER.o) -> SystemStyle.NUMERIC_LITERAL
				isSubtypeOf(CHARACTER.o) -> SystemStyle.CHARACTER_LITERAL
				isSubtypeOf(booleanType) -> SystemStyle.BOOLEAN_LITERAL
				isSubtypeOf(ATOM.o) -> SystemStyle.ATOM_LITERAL
				isInstanceMeta -> SystemStyle.TYPE
				else -> null
			}

		// Static methods referenced from generated code.

		/**
		 * Static method to extract the [argsTupleType] of a function type.
		 */
		@ReferencedInGeneratedCode
		@JvmStatic
		fun argsTupleTypeStatic(self: AvailObject): AvailObject =
			self.descriptor().o_ArgsTupleType(self) as AvailObject

		/** The [CheckedMethod] for [argsTupleTypeStatic]. */
		val argsTupleTypeMethod = staticMethod(
			A_Type::class.java,
			::argsTupleTypeStatic.name,
			AvailObject::class.java,
			AvailObject::class.java)

		@ReferencedInGeneratedCode
		@JvmStatic
		fun isSubtypeOfStatic(self: AvailObject, aType: A_Type): Boolean =
			self.descriptor().o_IsSubtypeOf(self, aType)

		/** The [CheckedMethod] for [isSubtypeOfStatic]. */
		val isSubtypeOfMethod = staticMethod(
			A_Type::class.java,
			::isSubtypeOfStatic.name,
			Boolean::class.javaPrimitiveType!!,
			AvailObject::class.java,
			A_Type::class.java)

		@ReferencedInGeneratedCode
		@JvmStatic
		fun typeAtIndexStatic(self: AvailObject, index: Int): AvailObject =
			self.descriptor().o_TypeAtIndex(self, index) as AvailObject

		/** The [CheckedMethod] for [typeAtIndexStatic]. */
		val typeAtIndexMethod = staticMethod(
			A_Type::class.java,
			::typeAtIndexStatic.name,
			AvailObject::class.java,
			AvailObject::class.java,
			Int::class.javaPrimitiveType!!)

		@ReferencedInGeneratedCode
		@JvmStatic
		fun typeUnionStatic(self: AvailObject, another: A_Type): AvailObject =
			self.descriptor().o_TypeUnion(self, another) as AvailObject

		/** The [CheckedMethod] for [typeUnionStatic]. */
		val typeUnionMethod = staticMethod(
			A_Type::class.java,
			::typeUnionStatic.name,
			AvailObject::class.java,
			AvailObject::class.java,
			A_Type::class.java)
	}
}
