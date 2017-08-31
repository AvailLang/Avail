/**
 * AbstractTypeDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import java.util.List;

import com.avail.annotations.AvailMethod;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.serialization.SerializerOperation;
import javax.annotation.Nullable;

/**
 * {@code AbstractTypeDescriptor} explicitly defines the responsibilities of all
 * {@linkplain TypeDescriptor Avail types}. Many of these operations are
 * actually undefined in subclasses, in clear violation of the Liskov
 * substitution principle, yet this organization is still useful to see the
 * aggregate capabilities of Avail types.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class AbstractTypeDescriptor
extends Descriptor
{
	@Override @AvailMethod
	abstract boolean o_AcceptsArgTypesFromFunctionType (
		final AvailObject object,
		final A_Type functionType);

	@Override @AvailMethod
	abstract boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes);

	@Override @AvailMethod
	abstract boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<? extends A_BasicObject> argValues);

	@Override @AvailMethod
	abstract boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final A_Tuple argTypes);

	@Override @AvailMethod
	abstract boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final A_Tuple arguments);

	@Override @AvailMethod
	abstract A_Type o_ArgsTupleType (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Set o_DeclaredExceptions (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_FunctionType (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_ContentType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<? extends A_Type> argTypes);

	@Override @AvailMethod
	abstract A_Type o_DefaultType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another);

	@Override @AvailMethod
	abstract A_Map o_FieldTypeMap (
		final AvailObject object);

	@Override @AvailMethod
	abstract int o_Hash (final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance);

	@Override @AvailMethod
	abstract A_Number o_InstanceCount (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final A_BasicObject anotherObject);

	@Override @AvailMethod
	abstract int o_RepresentationCostOfTupleType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType);

	@Override @AvailMethod
	abstract boolean o_IsIntegerRangeType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsMapType (final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsSetType (final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsSubtypeOf (
		final AvailObject object,
		final A_Type aType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfFiberType (
		final AvailObject object,
		final A_Type aFunctionType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final A_Type aVariableType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCode);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final A_Type aPojoType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfPojoBottomType (
		final AvailObject object,
		final A_Type aPojoType);

	@Override @AvailMethod
	boolean o_IsSupertypeOfBottom (
		final AvailObject object)
	{
		// All types are supertypes of bottom.
		return true;
	}

	@Override @AvailMethod
	abstract boolean o_IsTupleType (final AvailObject object);

	@Override @AvailMethod
	final boolean o_IsType (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	abstract A_Type o_KeyType (
		final AvailObject object);

	@Override @AvailMethod
	A_Type o_Kind (
		final AvailObject object)
	{
		// A type's kind is always ANY, since there are no more metatypes that
		// are kinds.
		return Types.ANY.o();
	}

	@Override @AvailMethod
	abstract A_Number o_LowerBound (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_LowerInclusive (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_BasicObject o_Parent (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_ReturnType (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_SizeRange (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_TypeAtIndex (
		final AvailObject object,
		final int index);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfFiberType (
		final AvailObject object,
		final A_Type aFiberType);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfLiteralTokenType (
		AvailObject object,
		A_Type aLiteralTokenType);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfMapType (
		final AvailObject object,
		final A_Type aMapType);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType);

	@Override
	abstract A_Type o_TypeIntersectionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType);

	@Override
	abstract A_Type o_TypeIntersectionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfSetType (
		final AvailObject object,
		final A_Type aSetType);

	@Override @AvailMethod
	abstract A_Type o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType);

	@Override @AvailMethod
	abstract A_Tuple o_TypeTuple (final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfFiberType (
		final AvailObject object,
		final A_Type aFiberType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfListNodeType (
		final AvailObject object,
		final A_Type aListNodeType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfMapType (
		final AvailObject object,
		final A_Type aMapType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType);

	@Override
	abstract A_Type o_TypeUnionOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfSetType (
		final AvailObject object,
		final A_Type aSetType);

	@Override
	abstract A_Type o_TypeUnionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType);

	@Override @AvailMethod
	abstract A_Type o_TypeUnionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType);

	@Override @AvailMethod
	abstract A_Type o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex);

	@Override @AvailMethod
	abstract A_Number o_UpperBound (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_UpperInclusive (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_ValueType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_RangeIncludesInt (
		final AvailObject object,
		final int anInt);

	@Override @AvailMethod
	abstract SerializerOperation o_SerializerOperation (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsBottom (final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsTop (final AvailObject object);

	/**
	 * Construct a new {@link AbstractTypeDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param typeTag
	 *            The {@link TypeTag} to embed in the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 */
	protected AbstractTypeDescriptor (
		final Mutability mutability,
		final TypeTag typeTag,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
	}
}
