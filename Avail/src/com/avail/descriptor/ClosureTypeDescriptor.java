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
		 * A tuple type indicating what kinds of arguments this type's instances
		 * will accept.
		 */
		ARGS_TUPLE_TYPE
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
	public @NotNull AvailObject o_ArgsTupleType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.ARGS_TUPLE_TYPE);
	}

	@Override
	public void o_ArgsTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject tupleType)
	{
		object.objectSlotPut(ObjectSlots.ARGS_TUPLE_TYPE, tupleType);
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
			final String str = elem != null ? elem.toString() : "…";
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
				final AvailObject item = objects.get(i);
				if (item != null)
				{
					item.printOnAvoidingIndent(
						aStream,
						recursionList,
						indent + 1);
				}
				else
				{
					aStream.append("…");
				}
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
				aStream.append(tempStrings.get(i));
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
		final List<AvailObject> list = new ArrayList<AvailObject>();
		final AvailObject tupleType = object.argsTupleType();
		if (tupleType.equals(TERMINATES.o()))
		{
			aStream.append("...");
		}
		else
		{
			final AvailObject minObject = tupleType.sizeRange().lowerBound();
			final AvailObject maxObject = tupleType.sizeRange().upperBound();
			if (minObject.isInt())
			{
				final int min = minObject.extractInt();
				for (int i = 1; i <= min; i++)
				{
					list.add(tupleType.typeAtIndex(i));
				}
				if (!minObject.equals(maxObject))
				{
					// Add "..., opt1, opt2" etc. to show the optional arguments.
					list.add(null);
					int max = tupleType.typeTuple().tupleSize() + 1;
					max = Math.max(max, min + 1);
					for (int i = min + 1; i <= max; i++)
					{
						list.add(tupleType.typeAtIndex(i));
					}
					if (!maxObject.equals(IntegerDescriptor.fromInt(max)))
					{
						// Add "..., 5" or whatever the max size is (or infinity).
						list.add(null);
						list.add(maxObject);
					}
				}
				printListOnAvoidingIndent(list, aStream, recursionList, indent);
			}
			else
			{
				aStream.append("?");
			}
		}
		aStream.append("]->");
		object.returnType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		if (object.checkedExceptions().setSize() > 0)
		{
			aStream.append("^");
			list.clear();
			for (final AvailObject elem : object.checkedExceptions())
			{
				list.add(elem);
			}
			printListOnAvoidingIndent(list, aStream, recursionList, indent);
		}
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
	 * <li>The {@linkplain AvailObject#argsTupleType() argument types}
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
		if (!object.argsTupleType().equals(aType.argsTupleType()))
		{
			return false;
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
			hash = hash * 29 ^ object.argsTupleType().hash();
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
		final AvailObject clone = mutable().create();
		clone.objectSlotPut(ObjectSlots.ARGS_TUPLE_TYPE,
			object.objectSlot(ObjectSlots.ARGS_TUPLE_TYPE));
		clone.objectSlotPut(ObjectSlots.CHECKED_EXCEPTIONS,
			object.objectSlot(ObjectSlots.CHECKED_EXCEPTIONS));
		clone.objectSlotPut(ObjectSlots.RETURN_TYPE,
			object.objectSlot(ObjectSlots.RETURN_TYPE));
		clone.hashOrZero(object.hashOrZero());
		return clone;
	}

	/**
	 * Answer whether the {@linkplain AvailObject#argsTupleType() argument
	 * types} supported by the specified {@linkplain ClosureTypeDescriptor
	 * closure type} are acceptable argument types for invoking a {@linkplain
	 * ClosureDescriptor closure} whose type is the {@code object}.
	 */
	@Override
	public boolean o_AcceptsArgTypesFromClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject closureType)
	{
		return closureType.argsTupleType().isSubtypeOf(object.argsTupleType());
	}

	/**
	 * The {@linkplain TypeDescriptor argument types} have been pushed onto
	 * the specified {@linkplain ContinuationDescriptor continuation}'s stack.
	 * Answer whether these arguments are acceptable for invoking a {@linkplain
	 * ClosureDescriptor closure} whose type is the {@code object}.
	 */
	@Override
	public boolean o_AcceptsArgumentTypesFromContinuation (
		final @NotNull AvailObject object,
		final @NotNull AvailObject continuation,
		final int stackp,
		final int numArgs)
	{
		final AvailObject tupleType = object.argsTupleType();
		for (int i = 1; i <= numArgs; i++)
		{
			final AvailObject argType =
				continuation.stackAt(stackp + numArgs - i);
			if (!argType.isSubtypeOf(tupleType.typeAtIndex(i)))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Answer whether these are acceptable {@linkplain TypeDescriptor argument
	 * types} for invoking a {@linkplain ClosureDescriptor closure} whose type
	 * is the {@code object}.
	 */
	@Override
	public boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argTypes)
	{
		final AvailObject tupleType = object.argsTupleType();
		for (int i = 1, end = argTypes.size(); i <= end; i++)
		{
			if (!argTypes.get(i - 1).isSubtypeOf(tupleType.typeAtIndex(i)))
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Answer whether these are acceptable arguments for invoking a {@linkplain
	 * ClosureDescriptor closure} whose type is the {@code object}.
	 */
	@Override
	public boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argValues)
	{
		final AvailObject tupleType = object.argsTupleType();
		for (int i = 1, end = argValues.size(); i <= end; i++)
		{
			final AvailObject arg = argValues.get(i - 1);
			if (!arg.isInstanceOfSubtypeOf(tupleType.typeAtIndex(i)))
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
		final AvailObject tupleType = object.argsTupleType();
		for (int i = 1, end = object.numArgs(); i <= end; i++)
		{
			if (!argTypes.tupleAt(i).isSubtypeOf(tupleType.typeAtIndex(i)))
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
		return arguments.isInstanceOfSubtypeOf(object.argsTupleType());
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
		final AvailObject tupleType = object.argsTupleType();
		for (int i = 1, end = argTypes.size(); i <= end; i++)
		{
			final AvailObject argType = tupleType.typeAtIndex(i);
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
	 * {@linkplain AvailObject#argsTupleType() argument types}, covariant by
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
		return object.argsTupleType().isSubtypeOf(aClosureType.argsTupleType());
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

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfClosureTypeCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType,
		final boolean canDestroy)
	{
		if (!canDestroy || !isMutable)
		{
			return object.copyMutable().typeIntersectionOfClosureTypeCanDestroy(
				aClosureType, true);
		}

		final AvailObject tupleTypeUnion =
			object.objectSlot(ObjectSlots.ARGS_TUPLE_TYPE).typeUnion(
				aClosureType.objectSlot(ObjectSlots.ARGS_TUPLE_TYPE));
		final AvailObject returnType =
			object.returnType().typeIntersection(aClosureType.returnType());
		AvailObject exceptions = SetDescriptor.empty();
		for (final AvailObject outer : object.checkedExceptions())
		{
			for (final AvailObject inner : aClosureType.checkedExceptions())
			{
				exceptions = exceptions.setWithElementCanDestroy(
					outer.typeIntersection(inner),
					true);
			}
		}
		exceptions = normalizeExceptionSet(exceptions);
		object.argsTupleType(tupleTypeUnion);
		object.checkedExceptions(exceptions);
		object.returnType(returnType);
		object.hashOrZero(0);
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
		if (!canDestroy || !isMutable)
		{
			return object.copyMutable().typeUnionOfClosureTypeCanDestroy(
				aClosureType, true);
		}

		final AvailObject tupleTypeIntersection =
			object.objectSlot(ObjectSlots.ARGS_TUPLE_TYPE).typeIntersection(
				aClosureType.objectSlot(ObjectSlots.ARGS_TUPLE_TYPE));
		final AvailObject returnType =
			object.returnType().typeUnion(aClosureType.returnType());
		final AvailObject exceptions = normalizeExceptionSet(
			object.checkedExceptions().setUnionCanDestroy(
				aClosureType.checkedExceptions(), true));
		object.argsTupleType(tupleTypeIntersection);
		object.checkedExceptions(exceptions);
		object.returnType(returnType);
		object.hashOrZero(0);
		return object;
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
						outer,
						true);
				}
			}
		}

		return normalizedSet;
	}

	/**
	 * Answer a new {@linkplain ClosureTypeDescriptor closure type} whose
	 * instances accept arguments which, if collected in a tuple, match the
	 * specified {@linkplain TupleTypeDescriptor tuple type}.  The instances of
	 * this closure type should also produce {@linkplain AvailObject values}
	 * that conform to the return type, and may only raise checked exceptions
	 * whose instances are subtypes of one or more members of the supplied
	 * exception set.
	 *
	 * @param argsTupleType
	 *        A {@linkplain TupleTypeDescriptor tuple type} describing the
	 *        {@linkplain TypeDescriptor types} of the arguments that instances
	 *        should accept.
	 * @param returnType
	 *        The {@linkplain TypeDescriptor type} of value that an instance
	 *        should produce.
	 * @param exceptionSet
	 *        The {@linkplain SetDescriptor set} of checked {@linkplain
	 *        ObjectTypeDescriptor exception types} that an instance may raise.
	 * @return A {@linkplain ClosureTypeDescriptor closure type}.
	 */
	public static @NotNull AvailObject createWithArgumentTupleType (
		final @NotNull AvailObject argsTupleType,
		final @NotNull AvailObject returnType,
		final @NotNull AvailObject exceptionSet)
	{
		assert argsTupleType.isInstanceOfSubtypeOf(TUPLE_TYPE.o());
		final AvailObject exceptionsReduced =
			normalizeExceptionSet(exceptionSet);
		final AvailObject type = mutable().create();
		type.objectSlotPut(ObjectSlots.ARGS_TUPLE_TYPE, argsTupleType);
		type.objectSlotPut(ObjectSlots.CHECKED_EXCEPTIONS, exceptionsReduced);
		type.objectSlotPut(ObjectSlots.RETURN_TYPE, returnType);
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
		final AvailObject tupleType =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.singleInteger(
					IntegerDescriptor.fromInt(argTypes.tupleSize())),
				argTypes,
				TERMINATES.o());
		return createWithArgumentTupleType(tupleType, returnType, exceptionSet);
	}

	/**
	 * Answer a new {@linkplain ClosureTypeDescriptor closure type} whose
	 * instances accept arguments of the specified {@linkplain TypeDescriptor
	 * types}, produce {@linkplain AvailObject values} that conform to the
	 * return type, and raise <em>no</em> checked exceptions.
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
		return create(
			argTypes,
			returnType,
			SetDescriptor.empty());
	}

	/**
	 * Answer a new {@linkplain ClosureTypeDescriptor closure type} whose
	 * instances can't be invoked because they have an impossible number of
	 * arguments specified.
	 *
	 * @param returnType
	 *            The type of object returned by a closure that conforms to the
	 *            {@linkplain ClosureTypeDescriptor closure type} being defined.
	 * @return
	 *            A {@linkplain ClosureTypeDescriptor closure type} that contains
	 */
	public static @NotNull AvailObject forReturnType (
		final @NotNull AvailObject returnType)
	{
		return createWithArgumentTupleType(
			TERMINATES.o(),
			returnType,
			SetDescriptor.empty());//TODO: Probably should allow any exception.
	}

	/**
	 * The most general {@linkplain ClosureTypeDescriptor closure type}.
	 */
	private static AvailObject TopInstance;

	/**
	 * Create the top (i.e., most general) {@linkplain ClosureTypeDescriptor
	 * closure type}.
	 */
	static void createWellKnownObjects ()
	{
		TopInstance = forReturnType(VOID_TYPE.o());
	}

	/**
	 * Clear any static references to publicly accessible objects.
	 */
	static void clearWellKnownObjects ()
	{
		TopInstance = null;
	}

	/**
	 * Answer the top (i.e., most general) {@linkplain ClosureTypeDescriptor
	 * closure type}.
	 *
	 * @return The closure type "[...]->void".
	 */
	public static AvailObject topInstance ()
	{
		return TopInstance;
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
