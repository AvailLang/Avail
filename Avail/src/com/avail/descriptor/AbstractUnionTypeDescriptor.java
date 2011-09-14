/**
 * descriptor/AbstractUnionTypeDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.NotNull;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.UnionTypeDescriptor.ObjectSlots;

/**
 * I represent the abstract concept of instance and union types.  In particular,
 * every object has a type which is effectively a singular union type, which has
 * as instances exactly that object (plus any subtypes of that object if it's a
 * type).  Such a singular union type is always represented via the subclass
 * {@link InstanceTypeDescriptor}.  Union types with a set of two or more
 * elements are always represented with a {@link UnionTypeDescriptor}.  Any
 * object present in this element set (or a subtype of an element that's a type)
 * is considered an instance of this union type.  The union type
 * with no elements (there's only one) uses TerminatesTypeDescriptor.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public abstract class AbstractUnionTypeDescriptor
extends AbstractTypeDescriptor
{

	/**
	 * Answer a new instance of this descriptor based on the set of objects that
	 * will be considered instances of that type. Normalize the cases where the
	 * set has zero or one elements to use {@link TerminatesTypeDescriptor} and
	 * {@link InstanceTypeDescriptor}, respectively. Leave out objects that are
	 * types for which a supertype is also present.
	 *
	 * @param instancesSet
	 *            The {@linkplain SetDescriptor set} of objects which are to be
	 *            instances of the new type.
	 * @return An {@link AvailObject} representing the type whose instances are
	 *         those objects specified in the argument.
	 */
	public static AvailObject withInstances (final AvailObject instancesSet)
	{
		int setSize = instancesSet.setSize();
		AvailObject normalizedSet;
		if (setSize >= 2)
		{
			AvailObject justTypes = SetDescriptor.empty();
			normalizedSet = SetDescriptor.empty();
			for (final AvailObject element : instancesSet)
			{
				if (element.isType())
				{
					justTypes = justTypes.setWithElementCanDestroy(
						element,
						true);
				}
				else
				{
					normalizedSet = normalizedSet.setWithElementCanDestroy(
						element,
						true);
				}
			}
			outer: for (final AvailObject type : justTypes)
			{
				for (final AvailObject potentialSupertype : justTypes)
				{
					if (!type.equals(potentialSupertype)
						&& type.isSubtypeOf(potentialSupertype))
					{
						continue outer;
					}
				}
				normalizedSet = normalizedSet.setWithElementCanDestroy(
					type,
					true);
			}
			final int newSetSize = normalizedSet.setSize();
			if (newSetSize == setSize)
			{
				normalizedSet = instancesSet;
			}
			setSize = newSetSize;
		}
		else
		{
			normalizedSet = instancesSet;
		}
		if (setSize <= 1)
		{
			for (final AvailObject element : normalizedSet)
			{
				return InstanceTypeDescriptor.withInstance(element);
			}
			return TerminatesTypeDescriptor.terminates();
		}
		assert instancesSet.setSize() > 1;
		final AvailObject result = UnionTypeDescriptor.mutable().create();
		result.objectSlotPut(
			ObjectSlots.INSTANCES,
			normalizedSet.makeImmutable());
		result.objectSlotPut(
			ObjectSlots.CACHED_SUPERKIND,
			NullDescriptor.nullObject());
		return result;
	}



	@Override
	public abstract @NotNull AvailObject o_Instances (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);

	@Override
	public final boolean o_IsAbstractUnionType (
		final @NotNull AvailObject object)
	{
		return true;
	}


	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The nearest kind of any instance/union/terminates type is the {@linkplain
	 * PrimitiveTypeDescriptor primitive} metatype called {@link
	 * Types#UNION_TYPE UNION_TYPE}.
	 * </p>
	 */
	@Override
	public final AvailObject o_Kind (final AvailObject object)
	{
		return UNION_TYPE.o();
	}

	/**
	 * Compute the type intersection of the object which is a union type, and
	 * the argument, which may or may not be a union type (but must be a type).
	 *
	 * @param object
	 *            A union type.
	 * @param another
	 *            Another type.
	 * @return
	 *            The most general type that is a subtype of both object and
	 *            another.
	 */
	abstract @NotNull AvailObject computeIntersectionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);


	/**
	 * Compute the type union of the object which is a union type, and
	 * the argument, which may or may not be a union type (but must be a type).
	 *
	 * @param object
	 *            A union type.
	 * @param another
	 *            Another type.
	 * @return
	 *            The most specific type that is a supertype of both object and
	 *            another.
	 */
	abstract @NotNull AvailObject computeUnionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);


	/**
	 * Answer the kind (i.e., a type that's not an abstract union type) that is
	 * closest to this type.  Fail if the object is {@linkplain
	 * TerminatesTypeDescriptor terminates}.
	 *
	 * @param object
	 *            The union type.
	 * @return
	 *            The closest supertype of the argument that isn't a union type.
	 */
	@Override
	public abstract @NotNull AvailObject o_ComputeSuperkind (
		final @NotNull AvailObject object);


	@Override
	public final @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return computeIntersectionWith(object, another);
	}

	@Override
	public final @NotNull AvailObject o_TypeIntersectionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		// Make sure the passed canDestroy flag is dropped and false is passed
		// instead, because this object's layout isn't amenable to this kind of
		// modification.
		return computeIntersectionWith(object, aClosureType);
	}

	@Override
	public final @NotNull AvailObject o_TypeIntersectionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, aContainerType);
	}

	@Override
	public final @NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, anIntegerRangeType);
	}

	@Override
	public final @NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, aMapType);
	}

	@Override
	public final @NotNull AvailObject o_TypeIntersectionOfMeta (
		final @NotNull AvailObject object,
		final @NotNull AvailObject someMeta)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Since metas intersect at terminatesType rather than
		// terminates, we must be very careful to override this properly.
		return computeIntersectionWith(object, someMeta);
	}

	@Override
	public final @NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, anObjectType);
	}

	@Override
	public final @NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, aSetType);
	}

	@Override
	public final @NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, aTupleType);
	}


	@Override
	public final @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return computeUnionWith(object, another);
	}

	@Override
	public final @NotNull AvailObject o_TypeUnionOfClosureType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aClosureType)
	{
		return computeUnionWith(object, aClosureType);
	}

	@Override
	public final @NotNull AvailObject o_TypeUnionOfContainerType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContainerType)
	{
		return computeUnionWith(object, aContainerType);
	}

	@Override
	public final @NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return computeUnionWith(object, anIntegerRangeType);
	}

	@Override
	public final @NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return computeUnionWith(object, aMapType);
	}

	@Override
	public final @NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		return computeUnionWith(object, anObjectType);
	}

	@Override
	public final @NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return computeUnionWith(object, aSetType);
	}

	@Override
	public final @NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return computeUnionWith(object, aTupleType);
	}


	@Override
	public boolean o_IsInstanceOf (
		final AvailObject object,
		final AvailObject aType)
	{
		if (aType.isAbstractUnionType())
		{
			final AvailObject myInstances = object.instances();
			AvailObject interestingTypeInstances = SetDescriptor.empty();
			for (final AvailObject typeInstance : aType.instances())
			{
				if (typeInstance.isType())
				{
					interestingTypeInstances =
						interestingTypeInstances.setWithElementCanDestroy(
							typeInstance,
							true);
				}
			}
			outer: for (final AvailObject myInstance : myInstances)
			{
				for (final AvailObject typeInstance : interestingTypeInstances)
				{
					if (myInstance.isInstanceOf(typeInstance))
					{
						continue outer;
					}
				}
				return false;
			}
			// All of my instances are instances of the types that are instances
			// of aType.
			return true;
		}
		return object.isInstanceOfKind(aType);
	}


	@Override
	public abstract @NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object);


	@Override
	public abstract @NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_LowerInclusive (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_UpperInclusive (
		final @NotNull AvailObject object);


	@Override
	public abstract @NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index);

	@Override
	public abstract @NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex);

	@Override
	public abstract @NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object);

	@Override
	public abstract @NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType);

	@Override
	public abstract boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_IsMapType (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_IsSetType (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_IsTupleType (
		final @NotNull AvailObject object);

	@Override
	public abstract boolean o_AcceptsArgTypesFromClosureType (
		final AvailObject object,
		final AvailObject closureType);

	@Override
	public abstract boolean o_AcceptsArgumentTypesFromContinuation (
		final AvailObject object,
		final AvailObject continuation,
		final int stackp,
		final int numArgs);

	@Override
	public abstract boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<AvailObject> argTypes);

	@Override
	public abstract boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<AvailObject> argValues);

	@Override
	public abstract boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final AvailObject argTypes);

	@Override
	public abstract boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final AvailObject arguments);

	@Override
	public abstract AvailObject o_ArgsTupleType (
		final AvailObject object);

	@Override
	public abstract AvailObject o_CheckedExceptions (
		final AvailObject object);

	@Override
	public abstract AvailObject o_ClosureType (
		final AvailObject object);

	@Override
	public abstract AvailObject o_ContentType (
		final AvailObject object);

	@Override
	public abstract boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<AvailObject> argTypes);

	@Override
	public abstract boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final AvailObject anotherObject);

	@Override
	public abstract boolean o_IsBetterRepresentationThanTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	@Override
	public abstract boolean o_IsInstanceOfKind (
		final AvailObject object,
		final AvailObject aType);

	@Override
	public abstract boolean o_IsSupertypeOfClosureType (
		final AvailObject object,
		final AvailObject aClosureType);

	@Override
	public abstract boolean o_IsSupertypeOfContainerType (
		final AvailObject object,
		final AvailObject aContainerType);

	@Override
	public abstract boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType);

	@Override
	public abstract boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType);

	@Override
	public abstract boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final AvailObject anIntegerRangeType);

	@Override
	public abstract boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType);

	@Override
	public abstract boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final AvailObject aLazyObjectType);

	@Override
	public abstract boolean o_IsSupertypeOfPrimitiveType (
		final AvailObject object,
		final AvailObject aPrimitiveType);

	@Override
	public abstract boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType);

	@Override
	public abstract boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType);

	@Override
	public abstract AvailObject o_KeyType (
		final AvailObject object);

	@Override
	public abstract AvailObject o_MyType (
		final AvailObject object);

	@Override
	public abstract AvailObject o_Name (
		final AvailObject object);

	@Override
	public abstract AvailObject o_Parent (
		final AvailObject object);

	@Override
	public abstract AvailObject o_ReturnType (
		final AvailObject object);

	@Override
	public abstract AvailObject o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType);

	@Override
	public abstract AvailObject o_TypeIntersectionOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType);

	@Override
	public abstract AvailObject o_TypeUnionOfContinuationType (
		final AvailObject object,
		final AvailObject aContinuationType);

	@Override
	public abstract AvailObject o_TypeUnionOfCompiledCodeType (
		final AvailObject object,
		final AvailObject aCompiledCodeType);

	@Override
	public abstract AvailObject o_ValueType (
		final AvailObject object);

	@Override
	public abstract boolean o_AbstractUnionTypeIncludesInstance (
		final AvailObject object,
		final AvailObject potentialInstance);

	/**
	 * Construct a new {@link AbstractUnionTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected AbstractUnionTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

}
