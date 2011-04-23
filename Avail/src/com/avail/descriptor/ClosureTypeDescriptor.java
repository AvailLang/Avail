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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.*;
import com.avail.annotations.NotNull;

/**
 * Closure types are the types of {@linkplain ClosureDescriptor closures}. They
 * contain information about the {@linkplain TypeDescriptor types} of arguments
 * that may be accepted, the types of {@linkplain AvailObject values} that may
 * be produced upon successful execution, and the types of exceptions that may
 * be raised to signal unsuccessful execution.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class ClosureTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	{
		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		HASH_OR_ZERO
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The normalized {@linkplain SetDescriptor set} of checked exceptions
		 * that may be raised by message sends performed from within a
		 * {@linkplain ClosureDescriptor closure} described by this {@linkplain
		 * ClosureTypeDescriptor closure type}.
		 */
		CHECKED_EXCEPTIONS,

		/**
		 * The most general {@linkplain TypeDescriptor type} of {@linkplain
		 * AvailObject value} that may be produced by a successful completion of
		 * a {@linkplain ClosureDescriptor closure} described by this
		 * {@linkplain ClosureTypeDescriptor closure type}.
		 */
		RETURN_TYPE,

		/**
		 * A vector of the most general {@linkplain TypeDescriptor types} of
		 * {@linkplain AvailObject arguments}, in left-to-right order, that may
		 * be accepted by a {@linkplain ClosureDescriptor closure} described by
		 * this {@linkplain ClosureTypeDescriptor closure type}.
		 */
		ARG_TYPE_AT_
	}

	@Override
	public int o_HashOrZero (
		final @NotNull AvailObject object)
	{
		return object.integerSlot(IntegerSlots.HASH_OR_ZERO);
	}

	@Override
	public void o_HashOrZero (
		final @NotNull AvailObject object,
		final int value)
	{
		object.integerSlotPut(IntegerSlots.HASH_OR_ZERO, value);
	}

	@Override
	public @NotNull AvailObject o_CheckedExceptions (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CHECKED_EXCEPTIONS);
	}

	@Override
	public void o_CheckedExceptions (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.CHECKED_EXCEPTIONS, value);
	}

	@Override
	public @NotNull AvailObject o_ReturnType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.RETURN_TYPE);
	}

	@Override
	public void o_ReturnType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.RETURN_TYPE, value);
	}

	@Override
	public @NotNull AvailObject o_ArgTypeAt (
		final @NotNull AvailObject object,
		final int subscript)
	{
		return object.objectSlotAt(ObjectSlots.ARG_TYPE_AT_, subscript);
	}

	@Override
	public void o_ArgTypeAtPut (
		final @NotNull AvailObject object,
		final int subscript,
		final @NotNull AvailObject value)
	{
		object.objectSlotAtPut(ObjectSlots.ARG_TYPE_AT_, subscript, value);
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull Enum<?> e)
	{
		return e == IntegerSlots.HASH_OR_ZERO;
	}

	/**
	 * Prettily print the specified {@linkplain List list} of {@linkplain
	 * AvailObject objects} to the specified {@linkplain StringBuilder stream}.
	 *
	 * @param objects The objects to print.
	 * @param aStream Where to print the objects.
	 * @param recursionList Which ancestor objects are currently being printed.
	 * @param indent What level to indent subsequent lines.
	 */
	private static void printListOnAvoidingIndent (
		final @NotNull List<AvailObject> objects,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		final int objectCount = objects.size();
		boolean anyBreaks;
		List<String> tempStrings;
		anyBreaks = false;
		tempStrings = new ArrayList<String>(objectCount);
		for (final AvailObject elem : objects)
		{
			final String str = elem.toString();
			tempStrings.add(str);
			if (str.indexOf('\n') > -1)
			{
				anyBreaks = true;
			}
		}
		if (anyBreaks)
		{
			for (int i = 0; i < objectCount; i++)
			{
				if (i > 0)
				{
					aStream.append(',');
				}
				aStream.append('\n');
				for (int count = 1; count <= indent; count++)
				{
					aStream.append('\t');
				}
				objects.get(i).printOnAvoidingIndent(
					aStream,
					recursionList,
					(indent + 1));
			}
		}
		else
		{
			for (int i = 0; i < objectCount; i++)
			{
				if (i > 0)
				{
					aStream.append(", ");
				}
				aStream.append(tempStrings.get(i - 1));
			}
		}
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append('[');
		List<AvailObject> list =
			new ArrayList<AvailObject>(object.numArgs());
		for (int i = 1; i <= object.numArgs(); i++)
		{
			list.add(object.argTypeAt(i));
		}
		printListOnAvoidingIndent(list, aStream, recursionList, indent);
		aStream.append("]->");
		object.returnType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		aStream.append(" raises {");
		list = new ArrayList<AvailObject>(object.checkedExceptions().setSize());
		for (final AvailObject elem : object.checkedExceptions())
		{
			list.add(elem);
		}
		printListOnAvoidingIndent(list, aStream, recursionList, indent);
		aStream.append('}');
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsClosureType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Two {@linkplain ClosureTypeDescriptor closure types} are {@linkplain
	 * AvailObject#equals(AvailObject) equal} IFF:</p>
	 *
	 * <p><ul>
	 * <li>They have the same {@linkplain AvailObject#numArgs() number of
	 * arguments},</li>
	 * <li>The {@linkplain AvailObject#argTypeAt(int) argument types}
	 * correspond,</li>
	 * <li>The {@linkplain AvailObject#returnType() return types}
	 * correspond, and</li>
	 * <li>The {@linkplain AvailObject#checkedExceptions() raise types}
	 * correspond.</li>
	 * </ul></p>
	 */
	@Override
	public boolean o_EqualsClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
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
		if (!object.checkedExceptions().equals(aType.checkedExceptions()))
		{
			return false;
		}
		// There are at least 2 references now.
		object.becomeIndirectionTo(aType);
		aType.makeImmutable();
		return true;
	}

	@Override
	public @NotNull AvailObject o_ExactType (
		final @NotNull AvailObject object)
	{
		return CLOSURE_TYPE.o();
	}

	/**
	 * The hash value is stored raw in the object's {@linkplain
	 * IntegerSlots#HASH_OR_ZERO hashOrZero} slot if it has been computed,
	 * otherwise that slot is zero. If a zero is detected, compute the hash and
	 * store it in hashOrZero. Note that the hash can (extremely rarely) be
	 * zero, in which case the hash must be computed on demand every time it is
	 * requested. Answer the raw hash value.
	 */
	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		int hash = object.hashOrZero();
		if (hash == 0)
		{
			hash = 0x63FC934;
			hash ^= object.returnType().hash();
			hash = hash * 23 ^ object.checkedExceptions().hash();
			for (int i = 1, end = object.numArgs(); i <= end; i++)
			{
				final AvailObject argTypeObject = object.argTypeAt(i);
				hash = hash * 23 ^ argTypeObject.hash();
			}
			object.hashOrZero(hash);
		}
		return hash;
	}

	@Override
	public @NotNull AvailObject o_Type (
		final @NotNull AvailObject object)
	{
		return CLOSURE_TYPE.o();
	}

	/**
	 * Answer the number of arguments expected by instances of the specified
	 * {@linkplain ClosureTypeDescriptor object}.
	 */
	@Override
	public short o_NumArgs (
		final @NotNull AvailObject object)
	{
		return (short)(object.objectSlotsCount() - numberOfFixedObjectSlots);
	}

	/**
	 * Answer a mutable copy of me. Always copy me, even if I was already
	 * mutable. Make my subobjects immutable because they will be shared
	 * between the existing and new objects.
	 */
	@Override
	public @NotNull AvailObject o_CopyMutable (
		final @NotNull AvailObject object)
	{
		if (isMutable)
		{
			object.makeSubobjectsImmutable();
		}
		final AvailObject clone = mutable().create(
			object.numArgs());
		clone.checkedExceptions(object.checkedExceptions());
		clone.returnType(object.returnType());
		for (int i = 1, end = object.numArgs(); i <= end; i++)
		{
			clone.argTypeAtPut(i, object.argTypeAt(i));
		}
		clone.hashOrZero(object.hashOrZero());
		return clone;
	}

	/**
	 * Answer whether the {@linkplain AvailObject#argTypeAt(int) argument types}
	 * supported by the specified {@linkplain ClosureTypeDescriptor closure
	 * type} are acceptable argument types for invoking a {@linkplain
	 * ClosureDescriptor closure} that is an instance of {@code object}.
	 */
	@Override
	public boolean o_AcceptsArgTypesFromClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject closureType)
	{
		if (closureType.numArgs() != object.numArgs())
		{
			return false;
		}
		for (int i = 1, end = closureType.numArgs(); i <= end; i++)
		{
			if (!closureType.argTypeAt(i).isSubtypeOf(object.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * The arguments have been pushed onto the specified {@linkplain
	 * ContinuationDescriptor continuation}'s stack. Answer whether these
	 * arguments are acceptable for invoking a {@linkplain ClosureDescriptor
	 * closure} that is an instance of {@code object}.
	 */
	@Override
	public boolean o_AcceptsArgumentsFromContinuationStackp (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp)
	{
		final short numArgs = object.numArgs();
		for (int i = 1; i <= numArgs; i++)
		{
			final AvailObject arg = continuation.stackAt(stackp + numArgs - i);
			if (!arg.isInstanceOfSubtypeOf(object.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * The {@linkplain TypeDescriptor argument types} have been pushed onto
	 * the specified {@linkplain ContinuationDescriptor continuation}'s stack.
	 * Answer whether these arguments are acceptable for invoking a {@linkplain
	 * ClosureDescriptor closure} that is an instance of {@code object}.
	 */
	@Override
	public boolean o_AcceptsArgumentTypesFromContinuationStackp (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp)
	{
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

	/**
	 * Answer whether these are acceptable {@linkplain TypeDescriptor argument
	 * types} for invoking a {@linkplain ClosureDescriptor closure} that is an
	 * instance of {@code object}.
	 */
	@Override
	public boolean o_AcceptsArrayOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		for (int i = 1, end = object.numArgs(); i <= end; i++)
		{
			if (!argTypes.get(i - 1).isSubtypeOf(object.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Answer whether these are acceptable arguments for invoking a {@linkplain
	 * ClosureDescriptor closure} that is an instance of {@code object}.
	 */
	@Override
	public boolean o_AcceptsArrayOfArgValues (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argValues)
	{
		for (int i = 1, end = object.numArgs(); i <= end; i++)
		{
			final AvailObject arg = argValues.get(i - 1);
			if (!arg.isInstanceOfSubtypeOf(object.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Answer whether these are acceptable {@linkplain TypeDescriptor argument
	 * types} for invoking a {@linkplain ClosureDescriptor closure} that is an
	 * instance of {@code object}. There may be more entries in the {@linkplain
	 * TupleDescriptor tuple} than are required by the {@linkplain
	 * ClosureTypeDescriptor closure type}.
	 */
	@Override
	public boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argTypes)
	{
		for (int i = 1, end = object.numArgs(); i <= end; i++)
		{
			if (!argTypes.tupleAt(i).isSubtypeOf(object.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Answer whether these are acceptable arguments for invoking a {@linkplain
	 * ClosureDescriptor closure} that is an instance of {@code object}. There
	 * may be more entries in the {@linkplain TupleDescriptor tuple} than are
	 * required by the {@linkplain ClosureTypeDescriptor closure type}.
	 */
	@Override
	public boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final @NotNull AvailObject arguments)
	{
		for (int i = 1, end = object.numArgs(); i <= end; i++)
		{
			if (!arguments.tupleAt(i).isInstanceOfSubtypeOf(
				object.argTypeAt(i)))
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
	 *
	 * @see ImplementationSetDescriptor
	 */
	@Override
	public boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		for (int i = 1, end = object.numArgs(); i <= end; i++)
		{
			final AvailObject argType = object.argTypeAt(i);
			final AvailObject actualType = argTypes.get(i - 1);
			final AvailObject intersection =
				argType.typeIntersection(actualType);
			if (intersection.equals(TERMINATES.o()))
			{
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfClosureType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * {@linkplain ClosureTypeDescriptor Closure types} are contravariant by
	 * {@linkplain AvailObject#argTypeAt(int) argument types}, covariant by
	 * {@linkplain AvailObject#returnType() return type}, and covariant by
	 * normalized {@linkplain AvailObject#checkedExceptions() raise types}. If
	 * {@linkplain AvailObject#numArgs() argument count} differs, they are
	 * incomparable (i.e., not a subclass).
	 */
	@Override
	public boolean o_IsSupertypeOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
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
		for (final AvailObject outer : aClosureType.checkedExceptions())
		{
			boolean anyCompatible = false;
			for (final AvailObject inner : object.checkedExceptions())
			{
				if (outer.isSubtypeOf(inner))
				{
					anyCompatible = true;
					break;
				}
			}
			if (!anyCompatible)
			{
				return false;
			}
		}
		for (int i = 1, end = object.numArgs(); i <= end; i++)
		{
			if (!object.argTypeAt(i).isSubtypeOf(aClosureType.argTypeAt(i)))
			{
				return false;
			}
		}
		return true;
	}

	// TODO: [TLS] Eliminate generalized closure type after refactoring closure
	// type to specify a tuple type of argument types (not varargs).
	@Override
	public boolean o_IsSupertypeOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		//  Closure types are contravariant by arguments and covariant by return type.  Since
		//  no closure types are supertypes of generalized closure types, answer false.

		//  Same as for superclass, but pretend it's a special case for symmetry.
		return false;
	}

	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
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
	public @NotNull AvailObject o_TypeIntersectionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		return object.typeIntersectionOfClosureTypeCanDestroy(
			aClosureType, false);
	}

	/**
	 * Normalize the specified exception {@linkplain SetDescriptor set} by
	 * eliminating terminates and types for which a supertype is also present.
	 *
	 * @param exceptionSet
	 *        An exception {@linkplain SetDescriptor set}. Must include only
	 *        {@linkplain TypeDescriptor types}.
	 * @return A normalized exception {@linkplain SetDescriptor set}.
	 * @see AvailObject#checkedExceptions()
	 */
	private static @NotNull AvailObject normalizeExceptionSet (
		final @NotNull AvailObject exceptionSet)
	{
		// This is probably the most common case -- no checked exceptions.
		// Return the argument.
		if (exceptionSet.setSize() == 0)
		{
			return exceptionSet;
		}

		// This is probably the next most common case -- just one checked
		// exception. If the element is terminates, then exclude it.
		if (exceptionSet.setSize() == 1)
		{
			if (exceptionSet.iterator().next().equals(TERMINATES.o()))
			{
				return SetDescriptor.empty();
			}
			return exceptionSet;
		}

		// Actually normalize the set. That is, eliminate types for which a
		// supertype is already present. Also, eliminate terminates.
		AvailObject normalizedSet = SetDescriptor.empty();
		for (final AvailObject outer : exceptionSet)
		{
			if (!outer.equals(TERMINATES.o()))
			{
				boolean subsumed = false;
				for (final AvailObject inner : exceptionSet)
				{
					if (outer.isSubtypeOf(inner))
					{
						subsumed = true;
						break;
					}
				}
				if (!subsumed)
				{
					normalizedSet = normalizedSet.setWithElementCanDestroy(
						outer, true);
				}
			}
		}

		return normalizedSet;
	}

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfClosureTypeCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType,
		final boolean canDestroy)
	{
		// TODO: [TLS] Update this when argument types are a tuple type.
		if (object.numArgs() != aClosureType.numArgs())
		{
			return TERMINATES.o();
		}
		if (!canDestroy || !isMutable)
		{
			return object.copyMutable().typeIntersectionOfClosureTypeCanDestroy(
				aClosureType, true);
		}

		// It's mutable at this point. Lock it to make sure a GC doesn't merge
		// it back with an immutable...
		AvailObject.lock(object);
		AvailObject set = SetDescriptor.empty();
		for (final AvailObject outer : object.checkedExceptions())
		{
			for (final AvailObject inner : aClosureType.checkedExceptions())
			{
				set = set.setWithElementCanDestroy(
					outer.typeIntersection(inner), true);
			}
		}
		object.checkedExceptions(normalizeExceptionSet(set));
		object.returnType(
			object.returnType().typeIntersection(aClosureType.returnType()));
		for (int i = 1, end = object.numArgs(); i <= end; i++)
		{
			object.argTypeAtPut(
				i, object.argTypeAt(i).typeUnion(aClosureType.argTypeAt(i)));
		}
		object.hashOrZero(0);
		AvailObject.unlock(object);
		return object;
	}

	// TODO: [TLS] Eliminate generalized closure type after refactoring closure
	// type to specify a tuple type of argument types (not varargs).
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		//  Answer the most general type that is still at least as specific as these.  The intersection
		//  of a closure type and a generalized closure type is always a closure type, so simply
		//  intersect the return types, and use the argument types verbatim.

		return object.typeIntersectionOfGeneralizedClosureTypeCanDestroy(aGeneralizedClosureType, false);
	}

	// TODO: [TLS] Eliminate generalized closure type after refactoring closure
	// type to specify a tuple type of argument types (not varargs).
	@Override
	public @NotNull AvailObject o_TypeIntersectionOfGeneralizedClosureTypeCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType,
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
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
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
	public @NotNull AvailObject o_TypeUnionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		return object.typeUnionOfClosureTypeCanDestroy(aClosureType, false);
	}

	@Override
	public @NotNull AvailObject o_TypeUnionOfClosureTypeCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType,
		final boolean canDestroy)
	{
		// TODO: [TLS] Update this when argument types are a tuple type.
		if (object.numArgs() != aClosureType.numArgs())
		{
			return GeneralizedClosureTypeDescriptor.forReturnType(
				object.returnType().typeUnion(aClosureType.returnType()));
		}

		if (!canDestroy || !isMutable)
		{
			return object.copyMutable().typeUnionOfClosureTypeCanDestroy(
				aClosureType, true);
		}

		// It's mutable at this point. Lock it to make sure a GC doesn't merge
		// it back with an immutable...
		AvailObject.lock(object);
		object.checkedExceptions(normalizeExceptionSet(
			object.checkedExceptions().setUnionCanDestroy(
				aClosureType.checkedExceptions(), false)));
		object.returnType(
			object.returnType().typeUnion(aClosureType.returnType()));
		for (int i = 1, end = object.numArgs(); i <= end; i++)
		{
			object.argTypeAtPut(
				i,
				object.argTypeAt(i).typeIntersection(
					aClosureType.argTypeAt(i)));
		}
		object.hashOrZero(0);
		AvailObject.unlock(object);
		return object;
	}

	// TODO: [TLS] Eliminate generalized closure type after refactoring closure
	// type to specify a tuple type of argument types (not varargs).
	@Override
	public @NotNull AvailObject o_TypeUnionOfGeneralizedClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aGeneralizedClosureType)
	{
		//  Answer the most specific type that is still at least as general as these.  Respect
		//  the covariance of the return types.  Discard the argument information, because
		//  the union of a generalized closure type and a closure type is always a generalized
		//  closure type.

		return GeneralizedClosureTypeDescriptor.forReturnType(object.returnType().typeUnion(aGeneralizedClosureType.returnType()));
	}

	/**
	 * Answer a new {@linkplain ClosureTypeDescriptor closure type} whose
	 * instances accept arguments of the specified {@linkplain TypeDescriptor
	 * types} and produce {@linkplain AvailObject values} that conform to the
	 * return type.
	 *
	 * @param argTypes
	 *        A {@linkplain TupleDescriptor tuple} of {@linkplain TypeDescriptor
	 *        types} of the arguments that instances should accept.
	 * @param returnType
	 *        The {@linkplain TypeDescriptor type} of value that an instance
	 *        should produce.
	 * @return A {@linkplain ClosureTypeDescriptor closure type}.
	 */
	public static @NotNull AvailObject create (
		final @NotNull AvailObject argTypes,
		final @NotNull AvailObject returnType)
	{
		final AvailObject type = mutable().create(
			argTypes.tupleSize());
		type.checkedExceptions(SetDescriptor.empty());
		type.returnType(returnType);
		for (int i = argTypes.tupleSize(); i >= 1; -- i)
		{
			type.argTypeAtPut(i, argTypes.tupleAt(i));
		}
		type.hashOrZero(0);
		type.makeImmutable();
		return type;
	}

	/**
	 * Answer a new {@linkplain ClosureTypeDescriptor closure type} whose
	 * instances accept arguments of the specified {@linkplain TypeDescriptor
	 * types}, produce {@linkplain AvailObject values} that conform to the
	 * return type, and may only raise checked exceptions whose instances are
	 * subtypes of one or more members of the supplied exception set.
	 *
	 * @param argTypes
	 *        A {@linkplain TupleDescriptor tuple} of {@linkplain TypeDescriptor
	 *        types} of the arguments that instances should accept.
	 * @param returnType
	 *        The {@linkplain TypeDescriptor type} of value that an instance
	 *        should produce.
	 * @param exceptionSet
	 *        The {@linkplain SetDescriptor set} of checked {@linkplain
	 *        ObjectTypeDescriptor exception types} that an instance may raise.
	 * @return A {@linkplain ClosureTypeDescriptor closure type}.
	 */
	public static @NotNull AvailObject create (
		final @NotNull AvailObject argTypes,
		final @NotNull AvailObject returnType,
		final @NotNull AvailObject exceptionSet)
	{
		final AvailObject type = mutable().create(
			argTypes.tupleSize());
		type.checkedExceptions(normalizeExceptionSet(exceptionSet));
		type.returnType(returnType);
		for (int i = argTypes.tupleSize(); i >= 1; -- i)
		{
			type.argTypeAtPut(i, argTypes.tupleAt(i));
		}
		type.hashOrZero(0);
		type.makeImmutable();
		return type;
	}

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
	private final static ClosureTypeDescriptor mutable =
		new ClosureTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ClosureTypeDescriptor}.
	 *
	 * @return The mutable {@link ClosureTypeDescriptor}.
	 */
	public static ClosureTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ClosureTypeDescriptor}.
	 */
	private final static ClosureTypeDescriptor immutable =
		new ClosureTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ClosureTypeDescriptor}.
	 *
	 * @return The immutable {@link ClosureTypeDescriptor}.
	 */
	public static ClosureTypeDescriptor immutable ()
	{
		return immutable;
	}
}
