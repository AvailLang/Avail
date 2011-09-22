/**
 * descriptor/InstanceTypeDescriptor.java
 * Copyright (c) 2011, Mark van Gulik.
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

import static com.avail.descriptor.AvailObject.Multiplier;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static java.lang.Math.*;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.TypeDescriptor.Types;

/**
 * My instances are called <em>instance types</em>, the types of individual
 * objects.  In particular, whenever an object is asked for its {@linkplain
 * AbstractDescriptor#o_Kind(AvailObject) type}, it creates an {@linkplain
 * InstanceTypeDescriptor instance type} that wraps that object.  Only that
 * object is a member of that instance type, except in the case that the object
 * is itself a type, in which case subtypes of that object are also considered
 * instances of the instance type.
 *
 * <p>
 * This last provision is to support the property called
 * <em>metacovariance</em>, which states that types' types vary the same way as
 * the types:
 * <span style="border-width:thin; border-style:solid"><nobr>
 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr;
 * T(x)&sube;T(y))</nobr></span>.
 * </p>
 *
 * <p>
 * The uniform use of instance types trivially ensures the additional property
 * we call <em>metavariance</em>, which states that every type has a unique
 * type of its own:
 * <span style="border-width:thin; border-style:solid"><nobr>
 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&ne;y &equiv;
 * T(x)&ne;T(y))</nobr></span>.
 * Note that metavariance requires this to hold for all types, but instance
 * types ensure this condition holds for all objects.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class InstanceTypeDescriptor
extends AbstractUnionTypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@linkplain AvailObject object} for which I am the {@linkplain
		 * InstanceTypeDescriptor instance type}.
		 */
		INSTANCE
	}

	/**
	 * Answer the instance that the provided instance type contains.
	 *
	 * @param object An instance type.
	 * @return The instance represented by the given instance type.
	 */
	private static @NotNull AvailObject getInstance (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.INSTANCE);
	}


	/**
	 * Answer the kind that is nearest to the given object, an instance type.
	 *
	 * @param object
	 *            An instance type.
	 * @return
	 *            The kind (a type but not a union type) that is nearest the
	 *            specified instance type.
	 */
	private static @NotNull AvailObject getSuperkind (
		final @NotNull AvailObject object)
	{
		return getInstance(object).kind();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		getInstance(object).printOnAvoidingIndent(
			aStream,
			recursionList,
			indent + 1);
		aStream.append("'s type");
	}

	/**
	 * Compute the type intersection of the object which is an instance type,
	 * and the argument, which may or may not be an instance type (but must be a
	 * type).
	 *
	 * @param object
	 *            An instance type.
	 * @param another
	 *            Another type.
	 * @return
	 *            The most general type that is a subtype of both object and
	 *            another.
	 */
	final @NotNull AvailObject computeIntersectionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		AvailObject set = SetDescriptor.empty();
		final AvailObject instance = getInstance(object);
		if (another.isAbstractUnionType())
		{
			// Create a new union type containing all non-type elements that are
			// simultaneously present in object and another, plus the type
			// intersections of all pairs of types in the product of the sets.
			// This should even correctly deal with bottom as an element.
			if (instance.isType())
			{
				for (final AvailObject anotherElement : another.instances())
				{
					if (anotherElement.isType())
					{
						set = set.setWithElementCanDestroy(
							anotherElement.typeIntersection(instance),
							true);
					}
				}
			}
			else if (another.instances().hasElement(instance))
			{
				set = set.setWithElementCanDestroy(instance, true);
			}
		}
		else
		{
			// Keep the instance if it complies with another, which is not a
			// union type.
			if (instance.isInstanceOfKind(another))
			{
				set = set.setWithElementCanDestroy(instance, true);
			}
		}
		if (set.setSize() == 0)
		{
			// Decide whether this should be bottom or bottom's type
			// based on whether object and another are both metas.  Note that
			// object is a meta precisely when its instance is a type.  One more
			// thing:  The special case of another being bottom should not
			// be treated as being a meta for our purposes, even though
			// bottom technically is a meta.
			if (instance.isType()
				&& another.isInstanceOfKind(META.o())
				&& !another.equals(BottomTypeDescriptor.bottom()))
			{
				return InstanceTypeDescriptor.withInstance(
					BottomTypeDescriptor.bottom());
			}
		}
		return AbstractUnionTypeDescriptor.withInstances(set);
	}

	//TODO: Hack
	static int recursion = 0;

	/**
	 * Compute the type union of the object which is a union type, and
	 * the argument, which may or may not be a union type (but must be a type).
	 *
	 * @param object
	 *            An instance type.
	 * @param another
	 *            Another type.
	 * @return
	 *            The most specific type that is a supertype of both object and
	 *            another.
	 */
	@NotNull AvailObject computeUnionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		if (another.isAbstractUnionType())
		{
			// Create a new union type containing all elements from both union
			// types.
			return AbstractUnionTypeDescriptor.withInstances(
				another.instances().setWithElementCanDestroy(
					getInstance(object),
					false));
		}
		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		// Another isn't a union type or instance type or bottom, so reverse
		// the arguments.
		recursion++;
		try
		{
			if (recursion > 20)
			{
				System.out.println("Recursion in computeUnionWith");
			}
			return getSuperkind(object).typeUnion(another);
		}
		finally
		{
			recursion--;
		}
	}

	@Override
	public AvailObject o_ComputeSuperkind (final AvailObject object)
	{
		return getSuperkind(object);
	}


	/**
	 * Answer a new instance of this descriptor based on some object whose type
	 * it will represent.
	 *
	 * @param instance The object whose type to represent.
	 * @return An {@link AvailObject} representing the type of the argument.
	 */
	public static AvailObject withInstance (final AvailObject instance)
	{
		final AvailObject result = mutable().create();
		result.objectSlotPut(
			ObjectSlots.INSTANCE,
			instance.makeImmutable());
		return result;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * An instance type is only equal to another instance type, and only when
	 * they refer to equal instances.
	 * </p>
	 */
	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		final boolean equal = another.equalsInstanceTypeFor(
			getInstance(object));
		if (equal)
		{
			another.becomeIndirectionTo(object);
		}
		return equal;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * An instance type is only equal to another instance type, and only when
	 * they refer to equal instances.
	 * </p>
	 */
	@Override
	public boolean o_EqualsInstanceTypeFor (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObject)
	{
		return getInstance(object).equals(anObject);
	}

	/**
	 * The potentialInstance is a {@linkplain ObjectDescriptor user-defined
	 * object}.  See if it is an instance of the object.
	 */
	@Override
	public boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance)
	{
		return getInstance(object).equals(potentialInstance);
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		return (getInstance(object).hash() ^ 0x15d5b163) * Multiplier;
	}

	@Override
	public @NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object)
	{
		return getSuperkind(object).fieldTypeMap();
	}


	@Override
	public @NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object)
	{
		final AvailObject instance = getInstance(object);
		assert instance.isExtendedInteger();
		return instance;
	}

	@Override
	public boolean o_LowerInclusive (
		final @NotNull AvailObject object)
	{
		assert getInstance(object).isExtendedInteger();
		return true;
	}

	@Override
	public @NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object)
	{
		final AvailObject instance = getInstance(object);
		assert instance.isExtendedInteger();
		return instance;
	}

	@Override
	public boolean o_UpperInclusive (
		final @NotNull AvailObject object)
	{
		assert getInstance(object).isExtendedInteger();
		return true;
	}


	@Override
	public @NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		// This is only intended for a TupleType stand-in.  Answer what type the
		// given index would have in an object instance of me.  Answer
		// bottom if the index is out of bounds.
		final AvailObject tuple = getInstance(object);
		assert tuple.isTuple();
		if (1 <= index && index <= tuple.tupleSize())
		{
			return withInstance(tuple.tupleAt(index));
		}
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		// Answer the union of the types that object's instances could have in
		// the given range of indices.  Out-of-range indices are treated as
		// bottom, which don't affect the union (unless all indices are out
		// of range).
		final AvailObject tuple = getInstance(object);
		assert tuple.isTuple();
		assert startIndex <= endIndex;
		if (endIndex <= 0)
		{
			return BottomTypeDescriptor.bottom();
		}
		final int upperIndex = tuple.tupleSize();
		if (startIndex > upperIndex)
		{
			return BottomTypeDescriptor.bottom();
		}
		if (startIndex == endIndex)
		{
			return withInstance(tuple.tupleAt(startIndex));
		}
		AvailObject set = SetDescriptor.empty();
		for (
			int i = max(startIndex, 1), end = min(endIndex, upperIndex);
			i <= end;
			i++)
		{
			set = set.setWithElementCanDestroy(
				tuple.tupleAt(i),
				true);
		}
		return AbstractUnionTypeDescriptor.withInstances(set);
	}

	@Override
	public @NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object)
	{
		final AvailObject tuple = getInstance(object);
		assert tuple.isTuple();
		final int tupleSize = tuple.tupleSize();
		if (tupleSize == 0)
		{
			return BottomTypeDescriptor.bottom();
		}
		return withInstance(tuple.tupleAt(tupleSize));
	}

	@Override
	public @NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		final AvailObject instance = getInstance(object);
		if (instance.isTuple())
		{
			return IntegerRangeTypeDescriptor.singleInt(
				getInstance(object).tupleSize());
		}
		else if (instance.isSet())
		{
			return IntegerRangeTypeDescriptor.singleInt(
				getInstance(object).setSize());
		}
		else if (instance.isMap())
		{
			return IntegerRangeTypeDescriptor.singleInt(
				getInstance(object).mapSize());
		}
		assert false : "Unexpected instance for sizeRange";
		return NullDescriptor.nullObject();
	}

	@Override
	public @NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object)
	{
		assert getInstance(object).isTuple();
		return getSuperkind(object).typeTuple();
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return getInstance(object).isInstanceOf(aType);
	}

	@Override
	public boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object)
	{
		return getInstance(object).isExtendedInteger();
	}

	@Override
	public boolean o_IsMapType (
		final @NotNull AvailObject object)
	{
		return getInstance(object).isMap();
	}

	@Override
	public boolean o_IsSetType (
		final @NotNull AvailObject object)
	{
		return getInstance(object).isSet();
	}

	@Override
	public boolean o_IsTupleType (
		final @NotNull AvailObject object)
	{
		return getInstance(object).isTuple();
	}

	@Override
	public AvailObject o_Instances (final AvailObject object)
	{
		return SetDescriptor.empty().setWithElementCanDestroy(
			getInstance(object),
			true);
	}

	@Override
	public boolean o_AbstractUnionTypeIncludesInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		final AvailObject instance = getInstance(object);
		if (potentialInstance.equals(instance))
		{
			return true;
		}
		if (instance.isType() && potentialInstance.isType())
		{
			return potentialInstance.isSubtypeOf(instance);
		}
		return false;
	}

	@Override
	public boolean o_AcceptsArgTypesFromClosureType (
		final AvailObject object,
		final AvailObject closureType)
	{
		return getSuperkind(object).acceptsArgTypesFromClosureType(closureType);
	}

	@Override
	public boolean o_AcceptsArgumentTypesFromContinuation (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp,
		final int numArgs)
	{
		return getSuperkind(object).acceptsArgumentTypesFromContinuation(
				continuation,
				stackp,
				numArgs);
	}

	@Override
	public boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		return getSuperkind(object).acceptsListOfArgTypes(argTypes);
	}

	@Override
	public boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<AvailObject> argValues)
	{
		return getSuperkind(object).acceptsListOfArgValues(argValues);
	}

	@Override
	public boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final AvailObject argTypes)
	{
		return getSuperkind(object).acceptsTupleOfArgTypes(argTypes);
	}

	@Override
	public boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final AvailObject arguments)
	{
		return getSuperkind(object).acceptsTupleOfArguments(arguments);
	}

	@Override
	public AvailObject o_ArgsTupleType (final AvailObject object)
	{
		return getSuperkind(object).argsTupleType();
	}

	@Override
	public AvailObject o_CheckedExceptions (final AvailObject object)
	{
		return getSuperkind(object).checkedExceptions();
	}

	@Override
	public AvailObject o_ClosureType (final AvailObject object)
	{
		return getSuperkind(object).closureType();
	}

	@Override
	public AvailObject o_ContentType (final AvailObject object)
	{
		/*
		 * Wow, this is weird.  Ask a set for its type and you get an instance
		 * type that refers back to the set.  Ask that type for its contentType
		 * (since it's technically a set type) and it reports a union type
		 * whose set of instances is this set again.
		 */
		final AvailObject set = getInstance(object);
		assert set.isSet();
		return AbstractUnionTypeDescriptor.withInstances(set);
	}

	@Override
	public boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<AvailObject> argTypes)
	{
		return getSuperkind(object).couldEverBeInvokedWith(argTypes);
	}

	@Override
	public boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final AvailObject anotherObject)
	{
		// Instance types are pretty decent representations.
		return true;
	}

	@Override
	public boolean o_IsBetterRepresentationThanTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		// Instance types are pretty decent representations.
		return true;
	}

	@Override
	public boolean o_IsSupertypeOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject aLazyObjectType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfPrimitiveType (
		final AvailObject object,
		final AvailObject aPrimitiveType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		return false;
	}

	@Override
	public boolean o_IsSupertypeOfUnionMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return false;
	}

	@Override
	public AvailObject o_KeyType (final AvailObject object)
	{
		return getSuperkind(object).keyType();
	}

	@Override
	public AvailObject o_MyType (final AvailObject object)
	{
		return getSuperkind(object).myType();
	}

	@Override
	public AvailObject o_Name (final AvailObject object)
	{
		return getSuperkind(object).name();
	}

	@Override
	public AvailObject o_Parent (final AvailObject object)
	{
		//TODO Maybe think about this one.
		return getSuperkind(object).parent();
	}

	@Override
	public AvailObject o_ReturnType (final AvailObject object)
	{
		return getSuperkind(object).returnType();
	}

	@Override
	public AvailObject o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		if (getInstance(object).isInstanceOf(aContinuationType))
		{
			return object;
		}
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public AvailObject o_TypeIntersectionOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		if (getInstance(object).isInstanceOf(aCompiledCodeType))
		{
			return object;
		}
		return BottomTypeDescriptor.bottom();
	}

	@Override
	public AvailObject o_TypeUnionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType)
	{
		return getSuperkind(object).typeUnionOfContinuationType(
			aContinuationType);
	}

	@Override
	public AvailObject o_TypeUnionOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		return getSuperkind(object).typeUnionOfContinuationType(
			aCompiledCodeType);
	}

	@Override
	public AvailObject o_ValueType (final AvailObject object)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean o_IsUnionMeta (final @NotNull AvailObject object)
	{
		return getInstance(object).isAbstractUnionType();
	}

	@Override
	public @NotNull AvailObject o_InnerKind (final @NotNull AvailObject object)
	{
		assert object.isUnionMeta();
		final AvailObject instance = getInstance(object);
		return instance.isAbstractUnionType()
			? instance.computeSuperkind()
			: instance;
	}


	/**
	 * Construct a new {@link InstanceTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected InstanceTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link InstanceTypeDescriptor}.
	 */
	private final static AbstractUnionTypeDescriptor mutable =
		new InstanceTypeDescriptor(true);

	/**
	 * Answer the mutable {@link InstanceTypeDescriptor}.
	 *
	 * @return The mutable {@link InstanceTypeDescriptor}.
	 */
	public static AbstractUnionTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link InstanceTypeDescriptor}.
	 */
	private final static AbstractUnionTypeDescriptor immutable =
		new InstanceTypeDescriptor(false);

	/**
	 * Answer the immutable {@link InstanceTypeDescriptor}.
	 *
	 * @return The immutable {@link InstanceTypeDescriptor}.
	 */
	public static AbstractUnionTypeDescriptor immutable ()
	{
		return immutable;
	}
}
