/**
 * descriptor/ClosureTypeDescriptor.java
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
import com.avail.descriptor.ClosureTypeDescriptor;
import com.avail.descriptor.GeneralizedClosureTypeDescriptor;
import com.avail.descriptor.TypeDescriptor;
import java.util.ArrayList;
import java.util.List;

public class ClosureTypeDescriptor extends TypeDescriptor
{

	public enum IntegerSlots
	{
		HASH_OR_ZERO
	}

	public enum ObjectSlots
	{
		RETURN_TYPE,
		ARG_TYPE_AT_
	}


	@Override
	public AvailObject o_ArgTypeAt (
			final AvailObject object,
			final int subscript)
	{
		return object.objectSlotAt(ObjectSlots.ARG_TYPE_AT_, subscript);
	}

	@Override
	public void o_ArgTypeAtPut (
			final AvailObject object,
			final int subscript,
			final AvailObject value)
	{
		object.objectSlotAtPut(ObjectSlots.ARG_TYPE_AT_, subscript, value);
	}

	/**
	 * Setter for field hashOrZero.
	 */
	@Override
	public void o_HashOrZero (
			final AvailObject object,
			final int value)
	{
		object.integerSlotPut(IntegerSlots.HASH_OR_ZERO, value);
	}

	/**
	 * Setter for field returnType.
	 */
	@Override
	public void o_ReturnType (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.RETURN_TYPE, value);
	}

	/**
	 * Getter for field hashOrZero.
	 */
	@Override
	public int o_HashOrZero (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HASH_OR_ZERO);
	}

	/**
	 * Getter for field returnType.
	 */
	@Override
	public AvailObject o_ReturnType (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.RETURN_TYPE);
	}



	// GENERATED special mutable slots

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
			final Enum<?> e)
	{
		if (e == IntegerSlots.HASH_OR_ZERO)
		{
			return true;
		}
		return false;
	}



	// java printing

	@Override
	public void printObjectOnAvoidingIndent (
			final AvailObject object,
			final StringBuilder aStream,
			final List<AvailObject> recursionList,
			final int indent)
	{
		aStream.append('[');
		boolean anyBreaks;
		List<String> tempStrings;
		anyBreaks = false;
		tempStrings = new ArrayList<String>(object.numArgs());
		for (int i = 1; i <= object.numArgs(); i++)
		{
			String str = object.argTypeAt(i).toString();
			tempStrings.add(str);
			if (str.indexOf('\n') > -1)
			{
				anyBreaks = true;
			}
		}
		if (anyBreaks)
		{
			for (int i = 1, _end1 = object.numArgs(); i <= _end1; i++)
			{
				if (i > 1)
				{
					aStream.append(',');
				}
				aStream.append('\n');
				for (int _count2 = 1; _count2 <= indent; _count2++)
				{
					aStream.append('\t');
				}
				object.argTypeAt(i).printOnAvoidingIndent(
					aStream,
					recursionList,
					(indent + 1));
			}
		}
		else
		{
			for (int i = 1, _end3 = object.numArgs(); i <= _end3; i++)
			{
				if (i > 1)
				{
					aStream.append(", ");
				}
				aStream.append(tempStrings.get(i - 1));
			}
		}
		aStream.append("]->");
		object.returnType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}



	// operations

	@Override
	public boolean o_Equals (
			final AvailObject object,
			final AvailObject another)
	{
		return another.equalsClosureType(object);
	}

	@Override
	public boolean o_EqualsClosureType (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Closure types are equal iff they're the same numArgs with equal
		//  corresponding argument types and result types.

		if (object.sameAddressAs(aType))
		{
			return true;
		}
		if (object.hash() != aType.hash())
		{
			return false;
		}
		final short num = object.numArgs();
		if (num != aType.numArgs())
		{
			return false;
		}
		for (int i = 1; i <= num; i++)
		{
			if (!object.argTypeAt(i).equals(aType.argTypeAt(i)))
			{
				return false;
			}
		}
		if (!object.returnType().equals(aType.returnType()))
		{
			return false;
		}
		object.becomeIndirectionTo(aType);
		// There are at least 2 references now.
		aType.makeImmutable();
		return true;
	}

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		return Types.closureType.object();
	}

	
	/**
	 * The hash value is stored raw in the object's hashOrZero slot if it has
	 * been computed, otherwise that slot is zero.  If a zero is detected,
	 * compute the hash and store it in hashOrZero.  Note that the hash can
	 * (extremely rarely) be zero, in which case the hash must be computed on
	 * demand every time it is requested.  Answer the raw hash value.
	 */
	@Override
	public int o_Hash (
			final AvailObject object)
	{
		int hash = object.hashOrZero();
		if (hash == 0)
		{
			hash = 0x63FC934;
			hash ^= object.returnType().hash();
			for (int i = 1, _end1 = object.numArgs(); i <= _end1; i++)
			{
				final AvailObject argTypeObject = object.argTypeAt(i);
				hash = ((hash * 23) ^ argTypeObject.hash());
			}
			object.hashOrZero(hash);
		}
		return hash;
	}

	
	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		return Types.closureType.object();
	}


	/**
	 * Answer the number of arguments object's instances expect.
	 */
	@Override
	public short o_NumArgs (
			final AvailObject object)
	{
		return (short)(object.objectSlotsCount() - numberOfFixedObjectSlots);
	}



	/**
	 * Answer a mutable copy of me.  Always copy me, even if I was already
	 * mutable.  Make my subobjects immutable because they will be shared
	 * between the existing and new objects.
	 */
	@Override
	public AvailObject o_CopyMutable (
			final AvailObject object)
	{
		if (isMutable)
		{
			object.makeSubobjectsImmutable();
		}
		final AvailObject clone = AvailObject.newIndexedDescriptor(
			object.numArgs(),
			ClosureTypeDescriptor.mutableDescriptor());
		clone.returnType(object.returnType());
		for (int i = 1, _end1 = object.numArgs(); i <= _end1; i++)
		{
			clone.argTypeAtPut(i, object.argTypeAt(i));
		}
		clone.hashOrZero(object.hashOrZero());
		return clone;
	}



	// operations-type checking

	@Override
	public boolean o_AcceptsArgTypesFromClosureType (
			final AvailObject object,
			final AvailObject closureType)
	{
		//  Answer whether these are acceptable argument types for invoking a closure that's an instance of me.

		if (closureType.numArgs() != object.numArgs())
		{
			return false;
		}
		for (int i = 1, _end1 = closureType.numArgs(); i <= _end1; i++)
		{
			if (!closureType.argTypeAt(i).isSubtypeOf(object.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean o_AcceptsArgumentsFromContinuationStackp (
			final AvailObject object,
			final AvailObject continuation,
			final int stackp)
	{
		// The arguments have been pushed onto continuation's stack.  Answer
		// whether these arguments are acceptable for invoking a closure with
		// this type.
		final short numArgs = object.numArgs();
		for (int i = 1; i <= numArgs; i++)
		{
			AvailObject arg = continuation.stackAt(stackp + numArgs - i); 
			if (!arg.isInstanceOfSubtypeOf(object.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean o_AcceptsArgumentTypesFromContinuationStackp (
			final AvailObject object,
			final AvailObject continuation,
			final int stackp)
	{
		// The argument types have been pushed onto continuation's stack. 
		// Answer whether these arguments are acceptable for invoking a closure
		// with this type.
		final short numArgs = object.numArgs();
		for (int i = 1; i <= numArgs; i++)
		{
			final AvailObject argType =
				continuation.stackAt(stackp + numArgs - i); 
			if (!argType.isSubtypeOf(object.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean o_AcceptsArrayOfArgTypes (
			final AvailObject object,
			final List<AvailObject> argTypes)
	{
		// Answer whether these are acceptable argument types for invoking a
		// closure that's an instance of me.
		for (int i = 1, _end1 = object.numArgs(); i <= _end1; i++)
		{
			if (!argTypes.get(i - 1).isSubtypeOf(object.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean o_AcceptsArrayOfArgValues (
			final AvailObject object,
			final List<AvailObject> argValues)
	{
		// Answer whether these are acceptable arguments for invoking a closure
		// that's an instance of me.
		for (int i = 1, _end1 = object.numArgs(); i <= _end1; i++)
		{
			final AvailObject arg = argValues.get(i - 1);
			if (!arg.isInstanceOfSubtypeOf(object.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean o_AcceptsTupleOfArgTypes (
			final AvailObject object,
			final AvailObject argTypes)
	{
		// Answer whether these are acceptable argument types for invoking a
		// closure that's an instance of me.  There may be more entries in the
		// tuple than we are interested in.
		for (int i = 1, _end1 = object.numArgs(); i <= _end1; i++)
		{
			if (!argTypes.tupleAt(i).isSubtypeOf(object.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean o_AcceptsTupleOfArguments (
			final AvailObject object,
			final AvailObject arguments)
	{
		// Answer whether these are acceptable arguments for invoking a closure
		// that's an instance of me.  There may be more entries in the tuple
		// than we are interested in.
		for (int i = 1, _end1 = object.numArgs(); i <= _end1; i++)
		{
			if (!arguments.tupleAt(i).isInstanceOfSubtypeOf(object.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Answer whether this method could ever be invoked with the given argument
	 * types. Make sure to exclude methods where an argType and corresponding
	 * method argument type have no common descendant except terminates. In that
	 * case, no legal object could be passed that would cause the method to be
	 * invoked. Don't check the number of arguments here.
	 * <p>
	 * @see ImplementationSetDescriptor
	 */
	@Override
	public boolean o_CouldEverBeInvokedWith (
			final AvailObject object,
			final List<AvailObject> argTypes)
	{
		for (int i = 1, _end1 = object.numArgs(); i <= _end1; i++)
		{
			final AvailObject argType = object.argTypeAt(i);
			final AvailObject actualType = argTypes.get(i - 1);
			final AvailObject intersection = argType.typeIntersection(actualType);
			if (intersection.equals(Types.terminates.object()))
			{
				return false;
			}
		}
		return true;
	}



	// operations-types

	@Override
	public boolean o_IsSubtypeOf (
			final AvailObject object,
			final AvailObject aType)
	{
		//  Check if object (a type) is a subtype of aType (should also be a type).

		return aType.isSupertypeOfClosureType(object);
	}

	@Override
	public boolean o_IsSupertypeOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  Closure types are contravariant by arguments and covariant by return type.
		//  If argument count differs, they are incomparable (i.e., not a subclass).

		if (object.equals(aClosureType))
		{
			return true;
		}
		if (object.numArgs() != aClosureType.numArgs())
		{
			return false;
		}
		if (!aClosureType.returnType().isSubtypeOf(object.returnType()))
		{
			return false;
		}
		for (int i = 1, _end1 = object.numArgs(); i <= _end1; i++)
		{
			if (!object.argTypeAt(i).isSubtypeOf(aClosureType.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean o_IsSupertypeOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  Closure types are contravariant by arguments and covariant by return type.  Since
		//  no closure types are supertypes of generalized closure types, answer false.

		//  Same as for superclass, but pretend it's a special case for symmetry.
		return false;
	}

	@Override
	public AvailObject o_TypeIntersection (
			final AvailObject object,
			final AvailObject another)
	{
		//  Answer the most general type that is still at least as specific as these.

		if (object.isSubtypeOf(another))
		{
			return object;
		}
		if (another.isSubtypeOf(object))
		{
			return another;
		}
		return another.typeIntersectionOfClosureType(object);
	}

	@Override
	public AvailObject o_TypeIntersectionOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.

		return object.typeIntersectionOfClosureTypeCanDestroy(aClosureType, false);
	}

	@Override
	public AvailObject o_TypeIntersectionOfClosureTypeCanDestroy (
			final AvailObject object,
			final AvailObject aClosureType,
			final boolean canDestroy)
	{
		//  Answer the most general type that is still at least as specific as these.  The
		//  object can be destroyed if it's mutable and canDestroy is true.

		if (object.numArgs() != aClosureType.numArgs())
		{
			return Types.terminates.object();
		}
		if (!canDestroy || !isMutable)
		{
			return object.copyMutable().typeIntersectionOfClosureTypeCanDestroy(aClosureType, true);
		}
		//  It's mutable at this point.  Lock it to make sure a GC doesn't merge it back with an immutable...
		AvailObject.lock(object);
		object.returnType(object.returnType().typeIntersection(aClosureType.returnType()));
		for (int i = 1, _end1 = object.numArgs(); i <= _end1; i++)
		{
			object.argTypeAtPut(i, object.argTypeAt(i).typeUnion(aClosureType.argTypeAt(i)));
		}
		object.hashOrZero(0);
		AvailObject.unlock(object);
		return object;
	}

	@Override
	public AvailObject o_TypeIntersectionOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  The intersection
		//  of a closure type and a generalized closure type is always a closure type, so simply
		//  intersect the return types, and use the argument types verbatim.

		return object.typeIntersectionOfGeneralizedClosureTypeCanDestroy(aGeneralizedClosureType, false);
	}

	@Override
	public AvailObject o_TypeIntersectionOfGeneralizedClosureTypeCanDestroy (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType,
			final boolean canDestroy)
	{
		//  Answer the most general type that is still at least as specific as these.  The intersection
		//  of a closure type and a generalized closure type is always a closure type, so simply
		//  intersect the return types, and use the argument types verbatim.

		if (!canDestroy || !isMutable)
		{
			return object.copyMutable().typeIntersectionOfGeneralizedClosureTypeCanDestroy(aGeneralizedClosureType, true);
		}
		//  It's mutable at this point.  Lock it to make sure a GC doesn't merge it back with an immutable...
		AvailObject.lock(object);
		object.returnType(object.returnType().typeIntersection(aGeneralizedClosureType.returnType()));
		object.hashOrZero(0);
		AvailObject.unlock(object);
		return object;
	}

	@Override
	public AvailObject o_TypeUnion (
			final AvailObject object,
			final AvailObject another)
	{
		//  Answer the most specific type that is still at least as general as these.

		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return another.typeUnionOfClosureType(object);
	}

	@Override
	public AvailObject o_TypeUnionOfClosureType (
			final AvailObject object,
			final AvailObject aClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.

		return object.typeUnionOfClosureTypeCanDestroy(aClosureType, false);
	}

	@Override
	public AvailObject o_TypeUnionOfClosureTypeCanDestroy (
			final AvailObject object,
			final AvailObject aClosureType,
			final boolean canDestroy)
	{
		//  Answer the most specific type that is still at least as general as these.  The
		//  object can be destroyed if it's mutable and canDestroy is true.

		if (object.numArgs() != aClosureType.numArgs())
		{
			return GeneralizedClosureTypeDescriptor.generalizedClosureTypeForReturnType(object.returnType().typeUnion(aClosureType.returnType()));
		}
		if (!canDestroy || !isMutable)
		{
			return object.copyMutable().typeUnionOfClosureTypeCanDestroy(aClosureType, true);
		}
		//  It's mutable at this point.  Lock it to make sure a GC doesn't merge it back with an immutable...
		AvailObject.lock(object);
		object.returnType(object.returnType().typeUnion(aClosureType.returnType()));
		for (int i = 1, _end1 = object.numArgs(); i <= _end1; i++)
		{
			object.argTypeAtPut(i, object.argTypeAt(i).typeIntersection(aClosureType.argTypeAt(i)));
		}
		object.hashOrZero(0);
		AvailObject.unlock(object);
		return object;
	}

	@Override
	public AvailObject o_TypeUnionOfGeneralizedClosureType (
			final AvailObject object,
			final AvailObject aGeneralizedClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.  Respect
		//  the covariance of the return types.  Discard the argument information, because
		//  the union of a generalized closure type and a closure type is always a generalized
		//  closure type.

		return GeneralizedClosureTypeDescriptor.generalizedClosureTypeForReturnType(object.returnType().typeUnion(aGeneralizedClosureType.returnType()));
	}





	/* Object creation */
	public static AvailObject closureTypeForArgumentTypesReturnType (
			AvailObject argTypes,
			AvailObject returnType)
	{
		AvailObject type = AvailObject.newIndexedDescriptor (
			argTypes.tupleSize(),
			ClosureTypeDescriptor.mutableDescriptor());
		type.returnType(returnType);
		for (int i = argTypes.tupleSize(); i >= 1; -- i)
		{
			type.argTypeAtPut(i, argTypes.tupleAt(i));
		}
		type.hashOrZero(0);
		type.makeImmutable();
		return type;
	};

	/**
	 * Construct a new {@link ClosureTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ClosureTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ClosureTypeDescriptor}.
	 */
	private final static ClosureTypeDescriptor mutableDescriptor = new ClosureTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ClosureTypeDescriptor}.
	 *
	 * @return The mutable {@link ClosureTypeDescriptor}.
	 */
	public static ClosureTypeDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	/**
	 * The immutable {@link ClosureTypeDescriptor}.
	 */
	private final static ClosureTypeDescriptor immutableDescriptor = new ClosureTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ClosureTypeDescriptor}.
	 *
	 * @return The immutable {@link ClosureTypeDescriptor}.
	 */
	public static ClosureTypeDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}
}
