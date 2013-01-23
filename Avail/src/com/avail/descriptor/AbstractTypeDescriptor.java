/**
 * AbstractTypeDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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
import com.avail.annotations.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.serialization.SerializerOperation;

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
		final AvailObject functionType);

	@Override @AvailMethod
	abstract boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<AvailObject> argTypes);

	@Override @AvailMethod
	abstract boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<AvailObject> argValues);

	@Override @AvailMethod
	abstract boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final AvailObject argTypes);

	@Override @AvailMethod
	abstract boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final AvailObject arguments);

	@Override @AvailMethod
	abstract AvailObject o_ArgsTupleType (
		final AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_DeclaredExceptions (
		final AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_FunctionType (
		final AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_ContentType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<AvailObject> argTypes);

	@Override @AvailMethod
	abstract AvailObject o_DefaultType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_Equals (
		final AvailObject object,
		final AvailObject another);

	@Override @AvailMethod
	abstract AvailObject o_FieldTypeMap (
		final AvailObject object);

	@Override @AvailMethod
	abstract int o_Hash (final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance);

	@Override @AvailMethod
	abstract AvailObject o_InstanceCount (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final AvailObject anotherObject);

	@Override @AvailMethod
	abstract boolean o_IsBetterRepresentationThanTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	@Override @AvailMethod
	abstract boolean o_IsInstanceOfKind (
		final AvailObject object,
		final AvailObject aType);

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
		final AvailObject aType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfFunctionType (
		final AvailObject object,
		final AvailObject aFunctionType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final AvailObject aVariableType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCode);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfLiteralTokenType (
		final AvailObject object,
		final AvailObject aLiteralTokenType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject aLazyObjectType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfParseNodeType (
		final AvailObject object,
		final AvailObject aParseNodeType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final AvailObject aPojoType);

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
		final AvailObject aPojoType);

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
	abstract AvailObject o_KeyType (
		final AvailObject object);

	@Override @AvailMethod
	AvailObject o_Kind (
		final AvailObject object)
	{
		// A type's kind is always ANY, since there are no more metatypes that
		// are kinds.
		return Types.ANY.o();
	}

	@Override @AvailMethod
	abstract AvailObject o_LowerBound (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_LowerInclusive (
		final AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_Name (
		final AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_Parent (
		final AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_ReturnType (
		final AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_SizeRange (
		final AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_TypeAtIndex (
		final AvailObject object,
		final int index);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersection (
		final AvailObject object,
		final AvailObject another);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfFunctionType (
		final AvailObject object,
		final AvailObject aFunctionType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfVariableType (
		final AvailObject object,
		final AvailObject aVariableType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfLiteralTokenType (
		AvailObject object,
		AvailObject aLiteralTokenType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfMapType (
		final AvailObject object,
		final AvailObject aMapType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfParseNodeType (
		final AvailObject object,
		final AvailObject aParseNodeType);

	@Override
	abstract AvailObject o_TypeIntersectionOfPojoType (
		final AvailObject object,
		final AvailObject aPojoType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfSetType (
		final AvailObject object,
		final AvailObject aSetType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	@Override @AvailMethod
	abstract AvailObject o_TypeTuple (final AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnion (
		final AvailObject object,
		final AvailObject another);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfFunctionType (
		final AvailObject object,
		final AvailObject aFunctionType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfVariableType (
		final AvailObject object,
		final AvailObject aVariableType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfLiteralTokenType (
		AvailObject object,
		AvailObject aLiteralTokenType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfMapType (
		final AvailObject object,
		final AvailObject aMapType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfObjectType (
		final AvailObject object,
		final AvailObject anObjectType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfParseNodeType (
		final AvailObject object,
		final AvailObject aParseNodeType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfSetType (
		final AvailObject object,
		final AvailObject aSetType);

	@Override
	abstract AvailObject o_TypeUnionOfPojoType (
		final AvailObject object,
		final AvailObject aPojoType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	@Override @AvailMethod
	abstract AvailObject o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex);

	@Override @AvailMethod
	abstract AvailObject o_UpperBound (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_UpperInclusive (
		final AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_ValueType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_RangeIncludesInt (
		final AvailObject object,
		final int anInt);

	@Override @AvailMethod
	abstract SerializerOperation o_SerializerOperation (
		final AvailObject object);

	/**
	 * Construct a new {@link TypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	protected AbstractTypeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}
}
