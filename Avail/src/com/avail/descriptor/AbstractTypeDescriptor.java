/**
 * descriptor/AbstractTypeDescriptor.java Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of the contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
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
import com.avail.annotations.NotNull;

public abstract class AbstractTypeDescriptor
extends Descriptor
{
	@Override
	public abstract boolean o_AcceptsArgTypesFromClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject closureType);

	@Override
	public abstract boolean o_AcceptsArgumentTypesFromContinuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp,
		final int numArgs);

	@Override
	public abstract boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes);

	@Override
	public abstract boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argValues);

	@Override
	public abstract boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argTypes);

	@Override
	public abstract boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final @NotNull AvailObject arguments);

	@Override
	public abstract @NotNull AvailObject o_ArgsTupleType (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_CheckedExceptions (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_ClosureType (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_ContentType (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes);

	@Override
	public abstract @NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);

	@Override
	public abstract @NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object);

	@Override
	public abstract int o_Hash (final @NotNull AvailObject object);

	@Override
	public abstract boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance);

	@Override
	public abstract boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anotherObject);

	@Override
	public abstract boolean o_IsBetterRepresentationThanTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType);

	@Override
	public abstract boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType);

	@Override
	public abstract boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_IsMapType (final @NotNull AvailObject object);

	@Override
	public abstract boolean o_IsSetType (final @NotNull AvailObject object);

	@Override
	public abstract boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType);

	@Override
	public abstract boolean o_IsSupertypeOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType);

	@Override
	public abstract boolean o_IsSupertypeOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType);

	@Override
	public abstract boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType);

	@Override
	public abstract boolean o_IsSupertypeOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCode);

	@Override
	public abstract boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType);

	@Override
	public abstract boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType);

	@Override
	public abstract boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aLazyObjectType);

	@Override
	public abstract boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPrimitiveType);

	@Override
	public abstract boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType);

	@Override
	public abstract boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType);

	@Override
	public boolean o_IsSupertypeOfTerminates (
		final @NotNull AvailObject object)
	{
		// All types are supertypes of terminates.  Anything that's not a type
		// will end the VM.
		return true;
	}

	@Override
	public abstract boolean o_IsTupleType (final @NotNull AvailObject object);

	@Override
	public final boolean o_IsType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	public abstract @NotNull AvailObject o_KeyType (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_LowerInclusive (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_MyType (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_Name (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_Parent (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_ReturnType (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index);

	@Override
	public abstract @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);

	@Override
	public abstract
	@NotNull AvailObject o_TypeIntersectionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType);

	@Override
	public abstract @NotNull AvailObject o_TypeIntersectionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType);

	@Override
	public abstract @NotNull AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType);

	@Override
	public abstract @NotNull AvailObject o_TypeIntersectionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType);

	@Override
	public abstract @NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType);

	@Override
	public abstract @NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType);

	@Override
	public abstract @NotNull AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject someMeta);

	@Override
	public abstract @NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType);

	@Override
	public abstract @NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType);

	@Override
	public abstract @NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType);

	@Override
	public abstract @NotNull AvailObject o_TypeTuple (final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);

	@Override
	public abstract @NotNull AvailObject o_TypeUnionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType);

	@Override
	public abstract @NotNull AvailObject o_TypeUnionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType);

	@Override
	public abstract @NotNull AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType);

	@Override
	public abstract @NotNull AvailObject o_TypeUnionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aCompiledCodeType);

	@Override
	public abstract @NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType);

	@Override
	public abstract @NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType);

	@Override
	public abstract @NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType);

	@Override
	public abstract @NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType);

	@Override
	public abstract @NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType);

	@Override
	public abstract @NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex);

	@Override
	public abstract @NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_UpperInclusive (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_ValueType (
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
