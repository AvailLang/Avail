/**
 * AbstractEnumerationTypeDescriptor.java
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

import java.util.List;
import com.avail.annotations.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;

/**
 * I represent the abstract concept of enumerations. In particular, every object
 * has a type which is effectively a singular enumeration, which has as
 * instances that object plus any subtypes if that object is a {@linkplain
 * TypeDescriptor type}). Such a singular enumeration is always represented via
 * the subclass {@link InstanceTypeDescriptor}. Enumerations with multiple
 * elements are always represented with an {@link EnumerationTypeDescriptor}.
 * Any object present in this element set (or a subtype of an element that's a
 * type) is considered an instance of this enumeration. The enumeration with no
 * elements (there's only one) uses {@link BottomTypeDescriptor}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public abstract class AbstractEnumerationTypeDescriptor
extends AbstractTypeDescriptor
{
	@Override @AvailMethod
	abstract @NotNull AvailObject o_InstanceCount (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_Instances (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);

	@Override @AvailMethod
	final boolean o_IsEnumeration (
		final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_Kind (final AvailObject object)
	{
		return TYPE.o();
	}

	/**
	 * Compute the type intersection of the {@linkplain AvailObject object}
	 * which is an {@linkplain AbstractEnumerationTypeDescriptor enumeration},
	 * and the argument, which may or may not be an enumeration (but must be a
	 * {@linkplain TypeDescriptor type}).
	 *
	 * @param object
	 *            An enumeration.
	 * @param another
	 *            Another type.
	 * @return
	 *            The most general type that is a subtype of both {@code object}
	 *            and {@code another}.
	 */
	abstract @NotNull AvailObject computeIntersectionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);

	/**
	 * Compute the type union of the {@linkplain AvailObject object} which is an
	 * {@linkplain AbstractEnumerationTypeDescriptor enumeration}, and the
	 * argument, which may or may not be an enumeration (but must be a
	 * {@linkplain TypeDescriptor type}).
	 *
	 * @param object
	 *            An enumeration.
	 * @param another
	 *            Another type.
	 * @return
	 *            The most general type that is a subtype of both {@code object}
	 *            and {@code another}.
	 */
	abstract @NotNull AvailObject computeUnionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another);

	/**
	 * Answer the kind (i.e., a type that's not an {@linkplain
	 * AbstractEnumerationTypeDescriptor enumeration}) that is closest to this
	 * type.  Fail if the object is {@linkplain BottomTypeDescriptor bottom}.
	 *
	 * @param object
	 *            The enumeration.
	 * @return
	 *            The closest supertype of the argument that isn't an
	 *            enumeration.
	 */
	@Override @AvailMethod
	abstract @NotNull AvailObject o_ComputeSuperkind (
		final @NotNull AvailObject object);


	@Override @AvailMethod
	final @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return computeIntersectionWith(object, another);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeIntersectionOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		// Make sure the passed canDestroy flag is dropped and false is passed
		// instead, because this object's layout isn't amenable to this kind of
		// modification.
		return computeIntersectionWith(object, aFunctionType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeIntersectionOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, aVariableType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeIntersectionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, anIntegerRangeType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeIntersectionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, aMapType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeIntersectionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, anObjectType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeIntersectionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, aPojoType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeIntersectionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, aSetType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeIntersectionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		// Answer the most general type that is still at least as specific as
		// these.  Make it exact first.
		return computeIntersectionWith(object, aTupleType);
	}


	@Override @AvailMethod
	final @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return computeUnionWith(object, another);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeUnionOfFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFunctionType)
	{
		return computeUnionWith(object, aFunctionType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeUnionOfVariableType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aVariableType)
	{
		return computeUnionWith(object, aVariableType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeUnionOfIntegerRangeType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anIntegerRangeType)
	{
		return computeUnionWith(object, anIntegerRangeType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeUnionOfMapType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aMapType)
	{
		return computeUnionWith(object, aMapType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeUnionOfObjectType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anObjectType)
	{
		return computeUnionWith(object, anObjectType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeUnionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return computeUnionWith(object, aPojoType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeUnionOfSetType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSetType)
	{
		return computeUnionWith(object, aSetType);
	}

	@Override @AvailMethod
	final @NotNull AvailObject o_TypeUnionOfTupleType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aTupleType)
	{
		return computeUnionWith(object, aTupleType);
	}

	@Override @AvailMethod
	boolean o_IsInstanceOf (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		if (aType.isEnumeration())
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

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		return object.kind().isSubtypeOf(aType);
	}

	@Override @AvailMethod
	abstract @NotNull AvailObject o_FieldTypeMap (
		final @NotNull AvailObject object);


	@Override @AvailMethod
	abstract @NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_LowerInclusive (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_UpperInclusive (
		final @NotNull AvailObject object);


	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType);

	@Override @AvailMethod
	abstract boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsMapType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsSetType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsTupleType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_AcceptsArgTypesFromFunctionType (
		final @NotNull AvailObject object,
		final AvailObject functionType);

	@Override @AvailMethod
	abstract boolean o_AcceptsArgumentTypesFromContinuation (
		final @NotNull AvailObject object,
		final AvailObject continuation,
		final int stackp,
		final int numArgs);

	@Override @AvailMethod
	abstract boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes);

	@Override @AvailMethod
	abstract boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final List<AvailObject> argValues);

	@Override @AvailMethod
	abstract boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final AvailObject argTypes);

	@Override @AvailMethod
	abstract boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final AvailObject arguments);

	@Override @AvailMethod
	abstract AvailObject o_ArgsTupleType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_CheckedExceptions (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_FunctionType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_ContentType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes);

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final @NotNull AvailObject object,
		final AvailObject anotherObject)
	{
		return true;
	}

	@Override @AvailMethod
	final boolean o_IsBetterRepresentationThanTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType)
	{
		return true;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfFunctionType (
		final @NotNull AvailObject object,
		final AvailObject aFunctionType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfVariableType (
		final @NotNull AvailObject object,
		final AvailObject aVariableType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfIntegerRangeType (
		final @NotNull AvailObject object,
		final AvailObject anIntegerRangeType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfMapType (
		final @NotNull AvailObject object,
		final AvailObject aMapType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfObjectType (
		final @NotNull AvailObject object,
		final AvailObject aLazyObjectType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfPrimitiveType (
		final @NotNull AvailObject object,
		final AvailObject aPrimitiveType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfSetType (
		final @NotNull AvailObject object,
		final AvailObject aSetType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfTupleType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfEnumerationType (
		final @NotNull AvailObject object,
		final AvailObject aTupleType)
	{
		return false;
	}

	@Override
	boolean o_IsSupertypeOfPojoBottomType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return false;
	}

	@Override @AvailMethod
	abstract AvailObject o_KeyType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_Name (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_Parent (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_ReturnType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType);

	@Override @AvailMethod
	abstract AvailObject o_TypeIntersectionOfParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType);

	@Override @AvailMethod
	abstract AvailObject o_TypeUnionOfParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType);

	@Override @AvailMethod
	abstract AvailObject o_ValueType (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract boolean o_EnumerationIncludesInstance (
		final @NotNull AvailObject object,
		final AvailObject potentialInstance);

	/**
	 * Answer a new object instance of this descriptor based on the set of
	 * objects that will be considered instances of that type. Normalize the
	 * cases where the set has zero or one elements to use {@link
	 * BottomTypeDescriptor} and {@link InstanceTypeDescriptor}, respectively.
	 *
	 * <p>
	 * Note that we also have to assure type union metainvariance, namely:
	 * &forall;<sub>x,y&isin;T</sub>&thinsp;(T(x)&cup;T(y) = T(x&cup;y)).
	 * Thus, if there are multiple instances which are types, use their type
	 * union as a single member in place of them.
	 * </p>
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
		AvailObject normalizedSet = instancesSet;
		if (setSize >= 2)
		{
			boolean anyTypes = false;
			for (final AvailObject element : instancesSet)
			{
				if (element.isType())
				{
					anyTypes = true;
					break;
				}
			}
			if (anyTypes)
			{
				AvailObject typesUnion = BottomTypeDescriptor.bottom();
				normalizedSet = SetDescriptor.empty();
				for (final AvailObject element : instancesSet)
				{
					if (element.isType())
					{
						typesUnion = typesUnion.typeUnion(element);
					}
					else
					{
						normalizedSet = normalizedSet.setWithElementCanDestroy(
							element,
							true);
					}
				}
				normalizedSet = normalizedSet.setWithElementCanDestroy(
					typesUnion,
					true);
				final int newSetSize = normalizedSet.setSize();
				if (newSetSize == setSize)
				{
					assert normalizedSet.equals(instancesSet);
					normalizedSet = instancesSet;
				}
				setSize = newSetSize;
			}
		}
		if (setSize <= 1)
		{
			for (final AvailObject element : normalizedSet)
			{
				return InstanceTypeDescriptor.on(element);
			}
			return BottomTypeDescriptor.bottom();
		}
		final AvailObject result = EnumerationTypeDescriptor.fromNormalizedSet(
			normalizedSet);
		return result;
	}

	/**
	 * Construct a new {@link AbstractEnumerationTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected AbstractEnumerationTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

}
