/**
 * AbstractEnumerationTypeDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import java.util.*;
import com.avail.annotations.*;
import com.avail.descriptor.TypeDescriptor.Types;
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
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class AbstractEnumerationTypeDescriptor
extends AbstractTypeDescriptor
{
	@Override @AvailMethod
	abstract A_Number o_InstanceCount (final AvailObject object);

	@Override @AvailMethod
	abstract A_Set o_Instances (final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_Equals (
		final AvailObject object,
		final A_BasicObject another);

	@Override @AvailMethod
	final boolean o_IsEnumeration (final AvailObject object)
	{
		return true;
	}

	/**
	 * Compute the type intersection of the {@linkplain AvailObject object}
	 * which is an {@linkplain AbstractEnumerationTypeDescriptor enumeration},
	 * and the argument, which may or may not be an enumeration (but must be a
	 * {@linkplain TypeDescriptor type}).
	 *
	 * @param object
	 *        An enumeration.
	 * @param another
	 *        Another type.
	 * @return The most general type that is a subtype of both {@code object}
	 *         and {@code another}.
	 */
	abstract A_Type computeIntersectionWith (
		final A_Type object,
		final A_Type another);

	/**
	 * Compute the type union of the {@linkplain AvailObject object} which is an
	 * {@linkplain AbstractEnumerationTypeDescriptor enumeration}, and the
	 * argument, which may or may not be an enumeration (but must be a
	 * {@linkplain TypeDescriptor type}).
	 *
	 * @param object
	 *        An enumeration.
	 * @param another
	 *        Another type.
	 * @return The most general type that is a subtype of both {@code object}
	 *         and {@code another}.
	 */
	abstract A_Type computeUnionWith (
		final A_Type object,
		final A_Type another);

	/**
	 * Answer the kind (i.e., a type that's not an {@linkplain
	 * AbstractEnumerationTypeDescriptor enumeration}) that is closest to this
	 * type.  Fail if the object is {@linkplain BottomTypeDescriptor bottom}.
	 *
	 * @param object
	 *        The enumeration.
	 * @return The closest supertype of the argument that isn't an
	 *         enumeration.
	 */
	@Override @AvailMethod
	abstract A_Type o_ComputeSuperkind (final AvailObject object);


	@Override @AvailMethod
	final A_Type o_TypeIntersection (
		final AvailObject object,
		final A_Type another)
	{
		return computeIntersectionWith(object, another);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return computeIntersectionWith(object, aContinuationType);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return computeIntersectionWith(object, aCompiledCodeType);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return computeIntersectionWith(object, aFiberType);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return computeIntersectionWith(object, aFunctionType);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return computeIntersectionWith(object, anIntegerRangeType);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return computeIntersectionWith(object, aLiteralTokenType);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		return computeIntersectionWith(object, aMapType);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		return computeIntersectionWith(object, aParseNodeType);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		return computeIntersectionWith(object, aVariableType);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfObjectType (
		final AvailObject object,
		final A_Type anObjectType)
	{
		return computeIntersectionWith(object, anObjectType);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return computeIntersectionWith(object, aPojoType);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		return computeIntersectionWith(object, aSetType);
	}

	@Override @AvailMethod
	final A_Type o_TypeIntersectionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return computeIntersectionWith(object, aTupleType);
	}


	@Override @AvailMethod
	final A_Type o_TypeUnion (
		final AvailObject object,
		final A_Type another)
	{
		return computeUnionWith(object, another);
	}

	@Override @AvailMethod
	final A_Type o_TypeUnionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return computeUnionWith(object, aContinuationType);
	}

	@Override @AvailMethod
	final A_Type o_TypeUnionOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return computeUnionWith(object, aCompiledCodeType);
	}


	@Override @AvailMethod
	final A_Type o_TypeUnionOfLiteralTokenType (
		final AvailObject object,
		final A_Type aLiteralTokenType)
	{
		return computeUnionWith(object, aLiteralTokenType);
	}

	@Override @AvailMethod
	final A_Type o_TypeUnionOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return computeUnionWith(object, aFiberType);
	}

	@Override @AvailMethod
	final A_Type o_TypeUnionOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return computeUnionWith(object, aFunctionType);
	}

	@Override @AvailMethod
	final A_Type o_TypeUnionOfVariableType (
		final AvailObject object,
		final A_Type aVariableType)
	{
		return computeUnionWith(object, aVariableType);
	}

	@Override @AvailMethod
	final A_Type o_TypeUnionOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return computeUnionWith(object, anIntegerRangeType);
	}

	@Override @AvailMethod
	final A_Type o_TypeUnionOfMapType (
		final AvailObject object,
		final A_Type aMapType)
	{
		return computeUnionWith(object, aMapType);
	}

	@Override @AvailMethod
	final A_Type o_TypeUnionOfObjectType (
		final AvailObject object,
		final A_Type anObjectType)
	{
		return computeUnionWith(object, anObjectType);
	}

	@Override @AvailMethod
	final A_Type o_TypeUnionOfParseNodeType (
		final AvailObject object,
		final A_Type aParseNodeType)
	{
		return computeUnionWith(object, aParseNodeType);
	}

	@Override @AvailMethod
	final A_Type o_TypeUnionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return computeUnionWith(object, aPojoType);
	}

	@Override @AvailMethod
	final A_Type o_TypeUnionOfSetType (
		final AvailObject object,
		final A_Type aSetType)
	{
		return computeUnionWith(object, aSetType);
	}

	@Override @AvailMethod
	final A_Type o_TypeUnionOfTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return computeUnionWith(object, aTupleType);
	}

	@Override @AvailMethod
	abstract boolean o_IsInstanceOf (
		final AvailObject object,
		final A_Type aType);

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		return object.kind().isSubtypeOf(aType);
	}

	@Override @AvailMethod
	abstract A_Map o_FieldTypeMap (
		final AvailObject object);


	@Override @AvailMethod
	abstract A_Number o_LowerBound (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_LowerInclusive (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Number o_UpperBound (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_UpperInclusive (
		final AvailObject object);


	@Override @AvailMethod
	abstract A_Type o_TypeAtIndex (
		final AvailObject object,
		final int index);

	@Override @AvailMethod
	abstract A_Type o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex);

	@Override @AvailMethod
	abstract A_Type o_DefaultType (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_SizeRange (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Tuple o_TypeTuple (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsSubtypeOf (
		final AvailObject object,
		final A_Type aType);

	@Override @AvailMethod
	abstract boolean o_IsIntegerRangeType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsLiteralTokenType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsMapType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsSetType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_IsTupleType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_AcceptsArgTypesFromFunctionType (
		final AvailObject object,
		final A_Type functionType);

	@Override @AvailMethod
	abstract boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<A_Type> argTypes);

	@Override @AvailMethod
	abstract boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<? extends A_BasicObject> argValues);

	@Override @AvailMethod
	abstract boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final A_Tuple argTypes);

	@Override @AvailMethod
	abstract boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final A_Tuple arguments);

	@Override @AvailMethod
	abstract A_Type o_ArgsTupleType (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Set o_DeclaredExceptions (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_FunctionType (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_ContentType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<? extends A_Type> argTypes);

	@Override @AvailMethod
	boolean o_IsBetterRepresentationThan (
		final AvailObject object,
		final A_BasicObject anotherObject)
	{
		return true;
	}

	@Override @AvailMethod
	final boolean o_IsBetterRepresentationThanTupleType (
		final AvailObject object,
		final A_Type aTupleType)
	{
		return true;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfFiberType (
		final AvailObject object,
		final A_Type aFiberType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfFunctionType (
		final AvailObject object,
		final A_Type aFunctionType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfVariableType (
		final AvailObject object,
		final A_BasicObject aVariableType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfCompiledCodeType (
		final AvailObject object,
		final A_Type aCompiledCodeType)
	{
		return false;
	}

	@Override
	boolean o_IsSupertypeOfLiteralTokenType (
		final AvailObject object,
		final A_BasicObject aLiteralTokenType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfIntegerRangeType (
		final AvailObject object,
		final A_Type anIntegerRangeType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfMapType (
		final AvailObject object,
		final AvailObject aMapType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfObjectType (
		final AvailObject object,
		final A_BasicObject aLazyObjectType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfParseNodeType (
		final AvailObject object,
		final AvailObject aParseNodeType)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final A_BasicObject aPojoType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfPrimitiveTypeEnum (
		final AvailObject object,
		final Types primitiveTypeEnum)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfSetType (
		final AvailObject object,
		final AvailObject aSetType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfTupleType (
		final AvailObject object,
		final AvailObject aTupleType)
	{
		return false;
	}

	@Override @AvailMethod
	final boolean o_IsSupertypeOfEnumerationType (
		final AvailObject object,
		final A_BasicObject aTupleType)
	{
		return false;
	}

	@Override
	boolean o_IsSupertypeOfPojoBottomType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return false;
	}

	@Override @AvailMethod
	abstract A_Type o_KeyType (
		final AvailObject object);

	@Override @AvailMethod
	abstract AvailObject o_Name (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_BasicObject o_Parent (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_ReturnType (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_ValueType (
		final AvailObject object);

	@Override @AvailMethod
	abstract boolean o_EnumerationIncludesInstance (
		final AvailObject object,
		final AvailObject potentialInstance);


	@Override @AvailMethod
	abstract A_Type o_ReadType (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_WriteType (
		final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_ExpressionType (
		final AvailObject object);

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
	public static A_Type withInstances (final A_Set instancesSet)
	{
		final int setSize = instancesSet.setSize();
		if (setSize == 0)
		{
			return BottomTypeDescriptor.bottom();
		}
		int typeCount = 0;
		for (final A_BasicObject element : instancesSet)
		{
			if (element.isType())
			{
				typeCount++;
			}
		}
		if (typeCount == 0)
		{
			if (setSize == 1)
			{
				return InstanceTypeDescriptor.on(
					instancesSet.iterator().next());
			}
			return EnumerationTypeDescriptor.fromNormalizedSet(
				instancesSet);
		}
		if (typeCount == setSize)
		{
			// They're all types.
			final Iterator<AvailObject> iterator = instancesSet.iterator();
			A_Type typesUnion = iterator.next();
			while (iterator.hasNext())
			{
				typesUnion = typesUnion.typeUnion(iterator.next());
			}
			return InstanceMetaDescriptor.on(typesUnion);
		}
		// It's a mix of types and non-types.
		return ANY.o();
	}

	/**
	 * Answer a new object instance of this descriptor based on the single
	 * object that will be considered an instance of that type. If a type is
	 * specified, all subtypes will also be considered instances of that type.
	 *
	 * @param instance
	 *            The {@linkplain AvailObject object} which is to be an instance
	 *            of the new type.
	 * @return An {@link AvailObject} representing the type whose instance is
	 *         the object specified in the argument.
	 */
	public static A_Type withInstance (final A_BasicObject instance)
	{
		if (instance.isType())
		{
			return InstanceMetaDescriptor.on((A_Type)instance);
		}
		return InstanceTypeDescriptor.on(instance);
	}

	/**
	 * Construct a new {@link AbstractEnumerationTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	protected AbstractEnumerationTypeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}
}
