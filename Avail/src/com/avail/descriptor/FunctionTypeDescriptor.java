/**
 * FunctionTypeDescriptor.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * Function types are the types of {@linkplain FunctionDescriptor functions}.
 * They contain information about the {@linkplain TypeDescriptor types} of
 * arguments that may be accepted, the types of {@linkplain AvailObject values}
 * that may be produced upon successful execution, and the types of exceptions
 * that may be raised to signal unsuccessful execution.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class FunctionTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		HASH_OR_ZERO
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The normalized {@linkplain SetDescriptor set} of checked exceptions
		 * that may be raised by message sends performed from within a
		 * {@linkplain FunctionDescriptor function} described by this
		 * {@linkplain FunctionTypeDescriptor function type}.
		 */
		CHECKED_EXCEPTIONS,

		/**
		 * The most general {@linkplain TypeDescriptor type} of {@linkplain
		 * AvailObject value} that may be produced by a successful completion of
		 * a {@linkplain FunctionDescriptor function} described by this
		 * {@linkplain FunctionTypeDescriptor function type}.
		 */
		RETURN_TYPE,

		/**
		 * A tuple type indicating what kinds of arguments this type's instances
		 * will accept.
		 */
		ARGS_TUPLE_TYPE
	}

	@Override @AvailMethod
	int o_HashOrZero (
		final @NotNull AvailObject object)
	{
		return object.slot(IntegerSlots.HASH_OR_ZERO);
	}

	@Override @AvailMethod
	void o_HashOrZero (
		final @NotNull AvailObject object,
		final int value)
	{
		object.setSlot(IntegerSlots.HASH_OR_ZERO, value);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_DeclaredExceptions (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.CHECKED_EXCEPTIONS);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ReturnType (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.RETURN_TYPE);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ArgsTupleType (
		final @NotNull AvailObject object)
	{
		return object.slot(ObjectSlots.ARGS_TUPLE_TYPE);
	}

	@Override boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
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
		if (tupleType.equals(BottomTypeDescriptor.bottom()))
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
					// Add "..., opt1, opt2" etc. to show the optional
					// arguments.
					list.add(null);
					int max = tupleType.typeTuple().tupleSize() + 1;
					max = Math.max(max, min + 1);
					for (int i = min + 1; i <= max; i++)
					{
						list.add(tupleType.typeAtIndex(i));
					}
					if (!maxObject.equals(IntegerDescriptor.fromInt(max)))
					{
						// Add "..., 5" or whatever the max size is (or
						// infinity).
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
		aStream.append("]→");
		object.returnType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
		if (object.declaredExceptions().setSize() > 0)
		{
			aStream.append("^");
			list.clear();
			for (final AvailObject elem : object.declaredExceptions())
			{
				list.add(elem);
			}
			printListOnAvoidingIndent(list, aStream, recursionList, indent);
		}
	}

	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsFunctionType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsFunctionType (
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
		if (!object.declaredExceptions().equals(aType.declaredExceptions()))
		{
			return false;
		}
		// There are at least 2 references now.
		object.becomeIndirectionTo(aType);
		aType.makeImmutable();
		return true;
	}

	/**
	 * The hash value is stored raw in the object's {@linkplain
	 * IntegerSlots#HASH_OR_ZERO hashOrZero} slot if it has been computed,
	 * otherwise that slot is zero. If a zero is detected, compute the hash and
	 * store it in hashOrZero. Note that the hash can (extremely rarely) be
	 * zero, in which case the hash must be computed on demand every time it is
	 * requested. Answer the raw hash value.
	 */
	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		int hash = object.hashOrZero();
		if (hash == 0)
		{
			hash = 0x63FC934;
			hash ^= object.returnType().hash();
			hash = hash * 23 ^ object.declaredExceptions().hash();
			hash = hash * 29 ^ object.argsTupleType().hash();
			object.hashOrZero(hash);
		}
		return hash;
	}

	@Override @AvailMethod
	boolean o_AcceptsArgTypesFromFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject functionType)
	{
		return functionType.argsTupleType().isSubtypeOf(object.argsTupleType());
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgTypes (
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

	@Override @AvailMethod
	boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final @NotNull List<AvailObject> argValues)
	{
		final AvailObject tupleType = object.argsTupleType();
		for (int i = 1, end = argValues.size(); i <= end; i++)
		{
			final AvailObject arg = argValues.get(i - 1);
			if (!arg.isInstanceOf(tupleType.typeAtIndex(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argTypes)
	{
		final AvailObject tupleType = object.argsTupleType();
		for (int i = 1, end = argTypes.tupleSize(); i <= end; i++)
		{
			if (!argTypes.tupleAt(i).isSubtypeOf(tupleType.typeAtIndex(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final @NotNull AvailObject arguments)
	{
		return arguments.isInstanceOf(object.argsTupleType());
	}

	/**
	 * Answer whether this method could ever be invoked with the given argument
	 * types. Make sure to exclude methods where an argType and corresponding
	 * method argument type have no common descendant except bottom. In that
	 * case, no legal object could be passed that would cause the method to be
	 * invoked. Don't check the number of arguments here.
	 *
	 * @see MethodDescriptor
	 */
	@Override @AvailMethod
	boolean o_CouldEverBeInvokedWith (
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
			if (intersection.equals(BottomTypeDescriptor.bottom()))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return aType.isSupertypeOfFunctionType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * {@linkplain FunctionTypeDescriptor Function types} are contravariant by
	 * {@linkplain AvailObject#argsTupleType() argument types}, covariant by
	 * {@linkplain AvailObject#returnType() return type}, and covariant by
	 * normalized {@linkplain AvailObject#declaredExceptions() raise types}. If
	 * {@linkplain AvailObject#numArgs() argument count} differs, they are
	 * incomparable (i.e., not a subclass).
	 */
	@Override @AvailMethod
	boolean o_IsSupertypeOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		if (object.equals(aFunctionType))
		{
			return true;
		}
		if (!aFunctionType.returnType().isSubtypeOf(object.returnType()))
		{
			return false;
		}
		each_outer:
		for (final AvailObject outer : aFunctionType.declaredExceptions())
		{
			for (final AvailObject inner : object.declaredExceptions())
			{
				if (outer.isSubtypeOf(inner))
				{
					continue each_outer;
				}
			}
			return false;
		}
		return object.argsTupleType().isSubtypeOf(aFunctionType.argsTupleType());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersection (
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
		return another.typeIntersectionOfFunctionType(object);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		final AvailObject tupleTypeUnion =
			object.slot(ObjectSlots.ARGS_TUPLE_TYPE).typeUnion(
				aFunctionType.slot(ObjectSlots.ARGS_TUPLE_TYPE));
		final AvailObject returnType =
			object.returnType().typeIntersection(aFunctionType.returnType());
		AvailObject exceptions = SetDescriptor.empty();
		for (final AvailObject outer : object.declaredExceptions())
		{
			for (final AvailObject inner : aFunctionType.declaredExceptions())
			{
				exceptions = exceptions.setWithElementCanDestroy(
					outer.typeIntersection(inner),
					true);
			}
		}
		exceptions = normalizeExceptionSet(exceptions);
		return createWithArgumentTupleType(
			tupleTypeUnion,
			returnType,
			exceptions);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnion (
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
		return another.typeUnionOfFunctionType(object);
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		// Subobjects may be shared with result.
		object.makeSubobjectsImmutable();
		final AvailObject tupleTypeIntersection =
			object.slot(ObjectSlots.ARGS_TUPLE_TYPE).typeIntersection(
				aFunctionType.slot(ObjectSlots.ARGS_TUPLE_TYPE));
		final AvailObject returnType =
			object.returnType().typeUnion(aFunctionType.returnType());
		final AvailObject exceptions = normalizeExceptionSet(
			object.declaredExceptions().setUnionCanDestroy(
				aFunctionType.declaredExceptions(), true));
		return createWithArgumentTupleType(
			tupleTypeIntersection,
			returnType,
			exceptions);
	}

	@Override @AvailMethod @ThreadSafe
	@NotNull SerializerOperation o_SerializerOperation (
		final @NotNull AvailObject object)
	{
		return SerializerOperation.FUNCTION_TYPE;
	}

	/**
	 * Normalize the specified exception {@linkplain SetDescriptor set} by
	 * eliminating bottom and types for which a supertype is also present.
	 *
	 * @param exceptionSet
	 *        An exception {@linkplain SetDescriptor set}. Must include only
	 *        {@linkplain TypeDescriptor types}.
	 * @return A normalized exception {@linkplain SetDescriptor set}.
	 * @see AvailObject#declaredExceptions()
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
		// exception. If the element is bottom, then exclude it.
		if (exceptionSet.setSize() == 1)
		{
			if (exceptionSet.iterator().next().equals(
				BottomTypeDescriptor.bottom()))
			{
				return SetDescriptor.empty();
			}
			return exceptionSet;
		}

		// Actually normalize the set. That is, eliminate types for which a
		// supertype is already present. Also, eliminate bottom.
		AvailObject normalizedSet = SetDescriptor.empty();
		each_outer:
		for (final AvailObject outer : exceptionSet)
		{
			if (!outer.equals(BottomTypeDescriptor.bottom()))
			{
				for (final AvailObject inner : exceptionSet)
				{
					if (outer.isSubtypeOf(inner))
					{
						continue each_outer;
					}
				}
				normalizedSet = normalizedSet.setWithElementCanDestroy(
					outer,
					true);
			}
		}

		return normalizedSet;
	}

	/**
	 * Answer a new {@linkplain FunctionTypeDescriptor function type} whose
	 * instances accept arguments which, if collected in a tuple, match the
	 * specified {@linkplain TupleTypeDescriptor tuple type}.  The instances of
	 * this function type should also produce {@linkplain AvailObject values}
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
	 * @return A {@linkplain FunctionTypeDescriptor function type}.
	 */
	public static @NotNull AvailObject createWithArgumentTupleType (
		final @NotNull AvailObject argsTupleType,
		final @NotNull AvailObject returnType,
		final @NotNull AvailObject exceptionSet)
	{
		assert argsTupleType.isTupleType();
		final AvailObject exceptionsReduced =
			normalizeExceptionSet(exceptionSet);
		final AvailObject type = mutable().create();
		type.setSlot(ObjectSlots.ARGS_TUPLE_TYPE, argsTupleType);
		type.setSlot(ObjectSlots.CHECKED_EXCEPTIONS, exceptionsReduced);
		type.setSlot(ObjectSlots.RETURN_TYPE, returnType);
		type.hashOrZero(0);
		type.makeImmutable();
		return type;
	}

	/**
	 * Answer a new {@linkplain FunctionTypeDescriptor function type} whose
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
	 * @return A {@linkplain FunctionTypeDescriptor function type}.
	 */
	public static @NotNull AvailObject create (
		final @NotNull AvailObject argTypes,
		final @NotNull AvailObject returnType,
		final @NotNull AvailObject exceptionSet)
	{
		final AvailObject tupleType =
			TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType(
				IntegerRangeTypeDescriptor.singleInt(
					argTypes.tupleSize()),
				argTypes,
				BottomTypeDescriptor.bottom());
		return createWithArgumentTupleType(tupleType, returnType, exceptionSet);
	}

	/**
	 * Answer a new {@linkplain FunctionTypeDescriptor function type} whose
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
	 * @return A {@linkplain FunctionTypeDescriptor function type}.
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
	 * Answer a new {@linkplain FunctionTypeDescriptor function type} whose
	 * instances can't be invoked because they have an impossible number of
	 * arguments specified.
	 *
	 * @param returnType
	 *            The type of object returned by a function that conforms to the
	 *            {@linkplain FunctionTypeDescriptor function type} being defined.
	 * @return
	 *            A {@linkplain FunctionTypeDescriptor function type}
	 */
	public static @NotNull AvailObject forReturnType (
		final @NotNull AvailObject returnType)
	{
		return createWithArgumentTupleType(
			BottomTypeDescriptor.bottom(),
			returnType,
			// TODO: [MvG] Probably should allow any exception.
			SetDescriptor.empty());
	}

	/**
	 * The most general {@linkplain FunctionTypeDescriptor function type}.
	 */
	private static AvailObject MostGeneralType;

	/**
	 * The metatype of any function types.
	 */
	private static AvailObject Meta;

	/**
	 * Create the top (i.e., most general) {@linkplain FunctionTypeDescriptor
	 * function type}.
	 */
	static void createWellKnownObjects ()
	{
		MostGeneralType = forReturnType(TOP.o());
		MostGeneralType.makeImmutable();
		Meta = InstanceTypeDescriptor.on(MostGeneralType);
		Meta.makeImmutable();
	}

	/**
	 * Clear any static references to publicly accessible objects.
	 */
	static void clearWellKnownObjects ()
	{
		MostGeneralType = null;
		Meta = null;
	}

	/**
	 * Answer the top (i.e., most general) {@linkplain FunctionTypeDescriptor
	 * function type}.
	 *
	 * @return The function type "[...]->top".
	 */
	public static AvailObject mostGeneralType ()
	{
		return MostGeneralType;
	}

	/**
	 * Answer the metatype for all function types.  This is just an {@linkplain
	 * InstanceTypeDescriptor instance type} on the {@linkplain
	 * #mostGeneralType() most general type}.
	 *
	 * @return The function type "[...]->top".
	 */
	public static AvailObject meta ()
	{
		return Meta;
	}


	/**
	 * Construct a new {@link FunctionTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected FunctionTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link FunctionTypeDescriptor}.
	 */
	private static final FunctionTypeDescriptor mutable =
		new FunctionTypeDescriptor(true);

	/**
	 * Answer the mutable {@link FunctionTypeDescriptor}.
	 *
	 * @return The mutable {@link FunctionTypeDescriptor}.
	 */
	public static FunctionTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link FunctionTypeDescriptor}.
	 */
	private static final FunctionTypeDescriptor immutable =
		new FunctionTypeDescriptor(false);

	/**
	 * Answer the immutable {@link FunctionTypeDescriptor}.
	 *
	 * @return The immutable {@link FunctionTypeDescriptor}.
	 */
	public static FunctionTypeDescriptor immutable ()
	{
		return immutable;
	}
}
