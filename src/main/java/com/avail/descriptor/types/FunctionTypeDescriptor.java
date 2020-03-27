/*
 * FunctionTypeDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.descriptor.types;

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.*;
import com.avail.descriptor.functions.FunctionDescriptor;
import com.avail.descriptor.numbers.A_Number;
import com.avail.descriptor.objects.ObjectTypeDescriptor;
import com.avail.descriptor.AbstractDescriptor;
import com.avail.descriptor.representation.AbstractSlotsEnum;
import com.avail.descriptor.representation.BitField;
import com.avail.descriptor.representation.IntegerSlotsEnum;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.representation.ObjectSlotsEnum;
import com.avail.descriptor.sets.A_Set;
import com.avail.descriptor.sets.SetDescriptor;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.tuples.TupleDescriptor;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.sets.SetDescriptor.emptySet;
import static com.avail.descriptor.types.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.types.FunctionTypeDescriptor.IntegerSlots.HASH_AND_MORE;
import static com.avail.descriptor.types.FunctionTypeDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.types.FunctionTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.types.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.singleInt;
import static com.avail.descriptor.types.TupleTypeDescriptor.tupleTypeForSizesTypesDefaultType;
import static com.avail.descriptor.types.TypeDescriptor.Types.TOP;
import static com.avail.utility.Strings.newlineTab;

/**
 * Function types are the types of {@linkplain FunctionDescriptor functions}.
 * They contain information about the {@linkplain TypeDescriptor types} of
 * arguments that may be accepted, the types of {@linkplain AvailObject values}
 * that may be produced upon successful execution, and the types of exceptions
 * that may be raised to signal unsuccessful execution.
 *
 * <p>Function types are contravariant by {@linkplain A_Type#argsTupleType()
 * argument types}, covariant by {@linkplain A_Type#returnType() return type},
 * and covariant by the coverage of types that are members of the {@linkplain
 * A_Type#declaredExceptions() exception set}.  I.e., if there is a type in the
 * exception set of A that isn't equal to or a subtype of an element of the
 * exception set of B, then A can't be a subtype of B.</p>
 *
 * <p>Note that the {@link A_Type#argsTupleType()} can be {@linkplain
 * BottomTypeDescriptor#bottom() bottom} (⊥) instead of a tuple type.  Because
 * bottom is more specific than any tuple type, the resulting function type is
 * considered more general than one with a tuple type (if the other variances
 * also hold).</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class FunctionTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for caching the hash, but the upper 32 can
		 * be used by other {@link BitField}s in subclasses.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * The hash, or zero ({@code 0}) if the hash has not yet been computed.
		 */
		static final BitField HASH_OR_ZERO = AbstractDescriptor
			.bitField(HASH_AND_MORE, 0, 32);
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The normalized {@linkplain SetDescriptor set} of checked exceptions
		 * that may be raised by message sends performed from within a
		 * {@linkplain FunctionDescriptor function} described by this
		 * function type.
		 */
		DECLARED_EXCEPTIONS,

		/**
		 * The most general {@linkplain TypeDescriptor type} of {@linkplain
		 * AvailObject value} that may be produced by a successful completion of
		 * a {@linkplain FunctionDescriptor function} described by this
		 * function type.
		 */
		RETURN_TYPE,

		/**
		 * A tuple type indicating what kinds of arguments this type's instances
		 * will accept.
		 */
		ARGS_TUPLE_TYPE
	}

	@Override
	protected boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return e == HASH_AND_MORE;
	}

	/**
	 * Prettily print the specified {@linkplain List list} of {@linkplain
	 * AvailObject objects} to the specified {@linkplain StringBuilder stream}.
	 *
	 * @param objects The objects to print.
	 * @param aStream Where to print the objects.
	 * @param recursionMap Which ancestor objects are currently being printed.
	 * @param indent What level to indent subsequent lines.
	 */
	private static void printListOnAvoidingIndent (
		final List<A_BasicObject> objects,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		final int objectCount = objects.size();
		boolean anyBreaks = false;
		final List<String> tempStrings = new ArrayList<>(objectCount);
		for (final @Nullable A_BasicObject elem : objects)
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
				newlineTab(aStream, indent);
				final A_BasicObject item = objects.get(i);
				if (item != null)
				{
					item.printOnAvoidingIndent(
						aStream,
						recursionMap,
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
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append('[');
		final List<A_BasicObject> list = new ArrayList<>();
		final A_Type tupleType = object.argsTupleType();
		if (tupleType.isBottom())
		{
			aStream.append("…");
		}
		else
		{
			final A_Number minObject = tupleType.sizeRange().lowerBound();
			final A_Number maxObject = tupleType.sizeRange().upperBound();
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
					if (!maxObject.equalsInt(max))
					{
						// Add "..., 5" or whatever the max size is (or
						// infinity).
						list.add(null);
						list.add(maxObject);
					}
				}
				printListOnAvoidingIndent(list, aStream, recursionMap, indent);
			}
			else
			{
				aStream.append("?");
			}
		}
		aStream.append("]→");
		object.returnType().printOnAvoidingIndent(
			aStream, recursionMap, indent + 1);
		if (object.declaredExceptions().setSize() > 0)
		{
			aStream.append("^");
			list.clear();
			for (final AvailObject elem : object.declaredExceptions())
			{
				list.add(elem);
			}
			printListOnAvoidingIndent(list, aStream, recursionMap, indent);
		}
	}

	/**
	 * The hash value is stored raw in the object's {@linkplain
	 * IntegerSlots#HASH_OR_ZERO hashOrZero} slot if it has been computed,
	 * otherwise that slot is zero. If a zero is detected, compute the hash and
	 * store it in hashOrZero. Note that the hash can (extremely rarely) be
	 * zero, in which case the hash must be computed on demand every time it is
	 * requested. Answer the raw hash value.
	 *
	 * @param object The object.
	 * @return The hash.
	 */
	private static int hash (final AvailObject object)
	{
		int hash = object.slot(HASH_OR_ZERO);
		if (hash == 0)
		{
			hash = 0x163FC934;
			hash += object.slot(RETURN_TYPE).hash();
			hash *= multiplier;
			hash ^= object.slot(DECLARED_EXCEPTIONS).hash();
			hash *= multiplier;
			hash ^= object.slot(ARGS_TUPLE_TYPE).hash();
			hash += 0x10447107;
			object.setSlot(HASH_OR_ZERO, hash);
		}
		return hash;
	}

	@Override @AvailMethod
	protected boolean o_AcceptsArgTypesFromFunctionType (
		final AvailObject object,
		final A_Type functionType)
	{
		return functionType.argsTupleType().isSubtypeOf(
			object.slot(ARGS_TUPLE_TYPE));
	}

	@Override @AvailMethod
	protected boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		final A_Type tupleType = object.slot(ARGS_TUPLE_TYPE);
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
	protected boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<? extends A_BasicObject> argValues)
	{
		final A_Type tupleType = object.slot(ARGS_TUPLE_TYPE);
		for (int i = 1, end = argValues.size(); i <= end; i++)
		{
			final A_BasicObject arg = argValues.get(i - 1);
			if (!arg.isInstanceOf(tupleType.typeAtIndex(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	protected boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final A_Tuple argTypes)
	{
		final A_Type tupleType = object.slot(ARGS_TUPLE_TYPE);
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
	protected boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final A_Tuple arguments)
	{
		return arguments.isInstanceOf(object.slot(ARGS_TUPLE_TYPE));
	}

	@Override @AvailMethod
	protected A_Type o_ArgsTupleType (final AvailObject object)
	{
		return object.slot(ARGS_TUPLE_TYPE);
	}

	@Override @AvailMethod
	protected boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<TypeRestriction> argRestrictions)
	{
		final A_Type tupleType = object.slot(ARGS_TUPLE_TYPE);
		for (int i = 1, end = argRestrictions.size(); i <= end; i++)
		{
			if (!argRestrictions.get(i - 1).intersectsType(
				tupleType.typeAtIndex(i)))
			{
				return false;
			}
		}
		return true;
	}

	@Override @AvailMethod
	protected A_Set o_DeclaredExceptions (final AvailObject object)
	{
		return object.slot(DECLARED_EXCEPTIONS);
	}

	@Override @AvailMethod
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsFunctionType(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsFunctionType (
		final AvailObject object,
		final A_Type aType)
	{
		if (object.sameAddressAs(aType))
		{
			return true;
		}
		if (object.hash() != aType.hash())
		{
			return false;
		}
		if (!object.slot(ARGS_TUPLE_TYPE).equals(aType.argsTupleType()))
		{
			return false;
		}
		if (!object.slot(RETURN_TYPE).equals(aType.returnType()))
		{
			return false;
		}
		if (!object.slot(DECLARED_EXCEPTIONS).equals(
			aType.declaredExceptions()))
		{
			return false;
		}
		if (!isShared())
		{
			aType.makeImmutable();
			object.becomeIndirectionTo(aType);
		}
		else if (!aType.descriptor().isShared())
		{
			object.makeImmutable();
			aType.becomeIndirectionTo(object);
		}
		return true;
	}

	@Override @AvailMethod
	public int o_Hash (final AvailObject object)
	{
		if (isShared())
		{
			synchronized (object)
			{
				return hash(object);
			}
		}
		return hash(object);
	}

	@Override @AvailMethod
	protected boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfFunctionType(object);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		if (object.equals(aFunctionType))
		{
			return true;
		}
		if (!aFunctionType.returnType().isSubtypeOf(object.slot(RETURN_TYPE)))
		{
			return false;
		}
		final A_Set inners = object.slot(DECLARED_EXCEPTIONS);
		// A ⊆ B if everything A can throw was declared by B
		each_outer:
		for (final A_Type outer : aFunctionType.declaredExceptions())
		{
			for (final A_Type inner : inners)
			{
				if (outer.isSubtypeOf(inner))
				{
					continue each_outer;
				}
			}
			return false;
		}
		return object.slot(ARGS_TUPLE_TYPE).isSubtypeOf(
			aFunctionType.argsTupleType());
	}

	@Override
	protected boolean o_IsVacuousType (final AvailObject object)
	{
		final A_Type argsTupleType = object.slot(ARGS_TUPLE_TYPE);
		final A_Type sizeRange = argsTupleType.sizeRange();
		return sizeRange.lowerBound().lessThan(sizeRange.upperBound());
	}

	@Override @AvailMethod
	protected A_Type o_ReturnType (final AvailObject object)
	{
		return object.slot(RETURN_TYPE);
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
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
	protected A_Type o_TypeIntersectionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		final A_Type tupleTypeUnion =
			object.slot(ARGS_TUPLE_TYPE).typeUnion(
				aFunctionType.argsTupleType());
		final A_Type returnType =
			object.slot(RETURN_TYPE).typeIntersection(
				aFunctionType.returnType());
		A_Set exceptions = emptySet();
		for (final A_Type outer : object.slot(DECLARED_EXCEPTIONS))
		{
			for (final A_Type inner : aFunctionType.declaredExceptions())
			{
				exceptions = exceptions.setWithElementCanDestroy(
					outer.typeIntersection(inner),
					true);
			}
		}
		exceptions = normalizeExceptionSet(exceptions);
		return functionTypeFromArgumentTupleType(
			tupleTypeUnion,
			returnType,
			exceptions);
	}

	@Override @AvailMethod
	protected A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
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
	protected A_Type o_TypeUnionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		// Subobjects may be shared with result.
		object.makeSubobjectsImmutable();
		final A_Type tupleTypeIntersection =
			object.slot(ARGS_TUPLE_TYPE).typeIntersection(
				aFunctionType.argsTupleType());
		final A_Type returnType =
			object.slot(RETURN_TYPE).typeUnion(aFunctionType.returnType());
		final A_Set exceptions = normalizeExceptionSet(
			object.slot(DECLARED_EXCEPTIONS).setUnionCanDestroy(
				aFunctionType.declaredExceptions(), true));
		return functionTypeFromArgumentTupleType(
			tupleTypeIntersection,
			returnType,
			exceptions);
	}

	@Override @AvailMethod @ThreadSafe
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.FUNCTION_TYPE;
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("function type");
		writer.write("arguments type");
		object.slot(ARGS_TUPLE_TYPE).writeSummaryTo(writer);
		writer.write("return type");
		object.slot(RETURN_TYPE).writeSummaryTo(writer);
		writer.write("declared exceptions");
		object.slot(DECLARED_EXCEPTIONS).writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("function type");
		writer.write("arguments type");
		object.slot(ARGS_TUPLE_TYPE).writeTo(writer);
		writer.write("return type");
		object.slot(RETURN_TYPE).writeTo(writer);
		writer.write("declared exceptions");
		object.slot(DECLARED_EXCEPTIONS).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Construct a new function type.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private FunctionTypeDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.FUNCTION_TYPE_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link FunctionTypeDescriptor}. */
	private static final FunctionTypeDescriptor mutable =
		new FunctionTypeDescriptor(Mutability.MUTABLE);

	@Override
	public FunctionTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link FunctionTypeDescriptor}. */
	private static final FunctionTypeDescriptor immutable =
		new FunctionTypeDescriptor(Mutability.IMMUTABLE);

	@Override
	public FunctionTypeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link FunctionTypeDescriptor}. */
	private static final FunctionTypeDescriptor shared =
		new FunctionTypeDescriptor(Mutability.SHARED);

	@Override
	public FunctionTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * The most general function type.
	 */
	private static final A_Type mostGeneralType =
		functionTypeReturning(TOP.o()).makeShared();

	/**
	 * Answer the top (i.e., most general) function type.
	 *
	 * @return The function type "[…]→⊤".
	 */
	public static A_Type mostGeneralFunctionType ()
	{
		return mostGeneralType;
	}

	/**
	 * The metatype of any function types.
	 */
	private static final A_Type meta =
		instanceMeta(mostGeneralType).makeShared();

	/**
	 * Answer the metatype for all function types.  This is just an {@linkplain
	 * InstanceTypeDescriptor instance type} on the {@linkplain
	 * #mostGeneralFunctionType() most general type}.
	 *
	 * @return The (meta-)type of the function type "[…]→⊤".
	 */
	public static A_Type functionMeta ()
	{
		return meta;
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
	private static A_Set normalizeExceptionSet (
		final A_Set exceptionSet)
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
			if (exceptionSet.iterator().next().isBottom())
			{
				return emptySet();
			}
			return exceptionSet;
		}

		// Actually normalize the set. That is, eliminate types for which a
		// supertype is already present. Also, eliminate bottom.
		A_Set normalizedSet = emptySet();
		each_outer:
		for (final AvailObject outer : exceptionSet)
		{
			if (!outer.isBottom())
			{
				for (final AvailObject inner : exceptionSet)
				{
					if (outer.isSubtypeOf(inner))
					{
						continue each_outer;
					}
				}
				normalizedSet = normalizedSet.setWithElementCanDestroy(
					outer, true);
			}
		}

		return normalizedSet;
	}

	/**
	 * Answer a new function type whose instances accept arguments which, if
	 * collected in a tuple, match the specified {@linkplain TupleTypeDescriptor
	 * tuple type}.  The instances of this function type should also produce
	 * values that conform to the return type, and may only raise checked
	 * exceptions whose instances are subtypes of one or more members of the
	 * supplied exception set.
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
	 * @return A function type.
	 */
	public static A_Type functionTypeFromArgumentTupleType (
		final A_Type argsTupleType,
		final A_Type returnType,
		final A_Set exceptionSet)
	{
		assert argsTupleType.isTupleType();
		final A_Set exceptionsReduced = normalizeExceptionSet(exceptionSet);
		final AvailObject type = mutable.create();
		type.setSlot(ARGS_TUPLE_TYPE, argsTupleType);
		type.setSlot(DECLARED_EXCEPTIONS, exceptionsReduced);
		type.setSlot(RETURN_TYPE, returnType);
		type.setSlot(HASH_OR_ZERO, 0);
		type.makeImmutable();
		return type;
	}

	/**
	 * Answer a new function type whose instances accept arguments whose types
	 * conform to the corresponding entries in the provided tuple of types,
	 * produce values that conform to the return type, and may only raise
	 * checked exceptions whose instances are subtypes of one or more members of
	 * the supplied exception set.
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
	 * @return A function type.
	 */
	public static A_Type functionType (
		final A_Tuple argTypes,
		final A_Type returnType,
		final A_Set exceptionSet)
	{
		final A_Type tupleType = tupleTypeForSizesTypesDefaultType(
			singleInt(argTypes.tupleSize()), argTypes, bottom());
		return functionTypeFromArgumentTupleType(
			tupleType, returnType, exceptionSet);
	}

	/**
	 * Answer a new function type whose instances accept arguments whose types
	 * conform to the corresponding entries in the provided tuple of types,
	 * produce values that conform to the return type, and raise no checked
	 * exceptions.
	 *
	 * @param argTypes
	 *        A {@linkplain TupleDescriptor tuple} of {@linkplain TypeDescriptor
	 *        types} of the arguments that instances should accept.
	 * @param returnType
	 *        The {@linkplain TypeDescriptor type} of value that an instance
	 *        should produce.
	 * @return A function type.
	 */
	public static A_Type functionType (
		final A_Tuple argTypes,
		final A_Type returnType)
	{
		return functionType(argTypes, returnType, emptySet());
	}

	/**
	 * Answer a new function type that doesn't specify how many arguments its
	 * conforming functions have.  This is a useful kind of function type for
	 * discussing things like a general function invocation operation.
	 *
	 * @param returnType
	 *        The type of object returned by a function that conforms to the
	 *        function type being defined.
	 * @return A function type.
	 */
	public static A_Type functionTypeReturning (
		final A_Type returnType)
	{
		return functionTypeFromArgumentTupleType(
			bottom(),
			returnType,
			// TODO: [MvG] Probably should allow any exception.
			emptySet());
	}
}
