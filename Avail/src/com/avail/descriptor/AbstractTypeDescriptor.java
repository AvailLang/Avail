/**
 * AbstractTypeDescriptor.java Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

/**
 * {@code AbstractTypeDescriptor} explicitly defines the responsibilities of all
 * {@linkplain TypeDescriptor Avail types}. Many of these operations are
 * actually undefined in subclasses, in clear violation of the Liskov
 * substitution principle, yet this organization is still useful to see the
 * aggregate capabilities of Avail types.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public abstract class AbstractTypeDescriptor
extends Descriptor
{
	@Override @AvailMethod
	abstract boolean o_AcceptsArgTypesFromFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject functionType);

	@Override @AvailMethod
	abstract boolean o_AcceptsArgumentTypesFromContinuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp,
		final int numArgs);

	@Override @AvailMethod
	abstract boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes);

	@Override @AvailMethod
	abstract boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argValues);

	@Override @AvailMethod
	abstract boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argTypes);

	@Override @AvailMethod
	abstract boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final @NotNull AvailObject arguments);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_ArgsTupleType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_CheckedExceptions (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_FunctionType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_ContentType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract int o_Hash (final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_InstanceCount (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherObject);

	@Override @AvailMethod
	abstract boolean o_IsBetterRepresentationThanTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType);

	@Override @AvailMethod
	abstract boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType);

	@Override @AvailMethod
	abstract boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsMapType (final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsSetType (final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCode);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aLazyObjectType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType);

	@Override @AvailMethod
	abstract boolean o_IsSupertypeOfPojoBottomType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType);

	@Override @AvailMethod
	boolean o_IsSupertypeOfBottom (
		final @NotNull AvailObject object)
	{
		// All types are supertypes of bottom.
		return true;
	}

	@Override @AvailMethod
	abstract boolean o_IsTupleType (final @NotNull AvailObject object);

	@Override @AvailMethod
	final boolean o_IsType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	abstract @NotNull AvailObject o_KeyType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_LowerInclusive (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_Name (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_Parent (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_ReturnType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeIntersectionOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeIntersectionOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeIntersectionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType);

	@Override
	abstract AvailObject o_TypeIntersectionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeTuple (final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnionOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnionOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnionOfParseNodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aParseNodeType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType);

	@Override
	abstract @NotNull AvailObject o_TypeUnionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_UpperInclusive (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_ValueType (
		final @NotNull AvailObject object);

	/**
	 * Construct a new {@link TypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected AbstractTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}
}
