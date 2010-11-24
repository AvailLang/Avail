/**
 * descriptor/TerminatesTypeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

import com.avail.descriptor.AvailObject;
import com.avail.descriptor.InfinityDescriptor;
import com.avail.descriptor.TupleDescriptor;

@IntegerSlots("hash")
@ObjectSlots({
	"name",
	"parent",
	"myType"
})
public class TerminatesTypeDescriptor extends PrimitiveTypeDescriptor
{


	// operations-from integer range type

	@Override
	public AvailObject ObjectLowerBound (
			final AvailObject object)
	{
		//  Pretend we go from +INF to -INF exclusive.  That should be a nice empty range.

		return InfinityDescriptor.positiveInfinity();
	}

	@Override
	public boolean ObjectLowerInclusive (
			final AvailObject object)
	{
		//  Pretend we go from +INF to -INF exclusive.  That should be a nice empty range.

		return false;
	}

	@Override
	public AvailObject ObjectUpperBound (
			final AvailObject object)
	{
		//  Pretend we go from +INF to -INF exclusive.  That should be a nice empty range.

		return InfinityDescriptor.negativeInfinity();
	}

	@Override
	public boolean ObjectUpperInclusive (
			final AvailObject object)
	{
		//  Pretend we go from +INF to -INF exclusive.  That should be a nice empty range.

		return false;
	}



	// operations-from map type

	@Override
	public AvailObject ObjectKeyType (
			final AvailObject object)
	{
		//  Answer what type my keys are.  Since I'm the degenerate mapType called
		//  terminates, answer terminates.

		return Types.terminates.object();
	}

	@Override
	public AvailObject ObjectSizeRange (
			final AvailObject object)
	{
		//  Answer what sizes my instances can be.  Since I'm the degenerate mapType called
		//  terminates, answer the degenerate integerType called terminates.

		return Types.terminates.object();
	}

	@Override
	public AvailObject ObjectValueType (
			final AvailObject object)
	{
		//  Answer what type my values are.  Since I'm the degenerate mapType called
		//  terminates, answer terminates.

		return Types.terminates.object();
	}



	// operations-from tuple type

	@Override
	public AvailObject ObjectTypeAtIndex (
			final AvailObject object,
			final int index)
	{
		//  Answer what type the given index would have in an object instance of me.  Answer
		//  terminates if the index is out of bounds, which is always because I'm the degenerate
		//  tupleType called terminates.

		return Types.terminates.object();
	}

	@Override
	public AvailObject ObjectUnionOfTypesAtThrough (
			final AvailObject object,
			final int startIndex,
			final int endIndex)
	{
		//  Answer the union of the types the given indices would have in an object instance of me.
		//  Answer terminates if the index is out of bounds, which is always because I'm the degenerate
		//  tupleType called terminates.

		return Types.terminates.object();
	}

	@Override
	public AvailObject ObjectDefaultType (
			final AvailObject object)
	{
		//  To support the tupleType protcol, I must answer terminates now.

		return Types.terminates.object();
	}

	@Override
	public AvailObject ObjectTypeTuple (
			final AvailObject object)
	{
		//  To support the tupleType protcol, I must answer <> now.

		return TupleDescriptor.empty();
	}



	// operations-types

	@Override
	public boolean ObjectIsSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Check if object (type terminates) is a subtype of aType (should also be a type).
		//  Always true, but make sure aType is really a type.

		return aType.isSupertypeOfTerminates();
	}

	@Override
	public boolean ObjectIsSupertypeOfPrimitiveType (
			final AvailObject object,
			final AvailObject aPrimitiveType)
	{
		//  Check if object (terminates) is a supertype of aPrimitiveType (a primitive type).
		//  Never true, because terminates is the most specific type.

		return false;
	}

	@Override
	public AvailObject ObjectTypeIntersection (
			final AvailObject object,
			final AvailObject another)
	{
		//  Answer the most general type that is still at least as specific as these.
		//  That would always be terminates.

		assert another.isType();
		return object;
	}

	@Override
	public AvailObject ObjectTypeUnion (
			final AvailObject object,
			final AvailObject another)
	{
		//  Answer the most specific type that still includes both of these.
		//  That would be the other type, not terminates.

		assert another.isType();
		return another;
	}

	@Override
	public boolean ObjectIsCyclicType (
			final AvailObject object)
	{
		//  Because terminates is a subtype of all other types, it is even considered
		//  a cyclic type.  That does not mean terminates' type is terminates, though
		//  (it's terminatesType).

		return true;
	}

	@Override
	public boolean ObjectIsIntegerRangeType (
			final AvailObject object)
	{
		//  Because terminates is a subtype of all other types, it is even considered
		//  an integer range type - in particular, the degenerate integer type (INF..-INF).

		return true;
	}

	@Override
	public boolean ObjectIsListType (
			final AvailObject object)
	{
		//  Because terminates is a subtype of all other types, it is even considered
		//  a list type - in particular, the degenerate list type with terminates as its
		//  embedded tupleType.

		return true;
	}

	@Override
	public boolean ObjectIsMapType (
			final AvailObject object)
	{
		//  Because terminates is a subtype of all other types, it is even considered
		//  a map type - in particular, the degenerate map type.  Its sizeRange is
		//  terminates, its keyType is terminates, and its valueType is terminates.

		return true;
	}

	@Override
	public boolean ObjectIsTupleType (
			final AvailObject object)
	{
		//  Because terminates is a subtype of all other types, it is even considered
		//  a tuple type - in particular, the degenerate tuple type.  Its sizeRange is
		//  terminates, its typeTuple is <>, and its defaultType is terminates.

		return true;
	}

	/**
	 * Construct a new {@link TerminatesTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected TerminatesTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link TerminatesTypeDescriptor}.
	 */
	private final static TerminatesTypeDescriptor mutableDescriptor = new TerminatesTypeDescriptor(true);

	/**
	 * Answer the mutable {@link TerminatesTypeDescriptor}.
	 *
	 * @return The mutable {@link TerminatesTypeDescriptor}.
	 */
	public static TerminatesTypeDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link TerminatesTypeDescriptor}.
	 */
	private final static TerminatesTypeDescriptor immutableDescriptor = new TerminatesTypeDescriptor(false);

	/**
	 * Answer the immutable {@link TerminatesTypeDescriptor}.
	 *
	 * @return The immutable {@link TerminatesTypeDescriptor}.
	 */
	public static TerminatesTypeDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
