/**
 * InstanceTypeDescriptor.java
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

import static com.avail.descriptor.InstanceTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.AvailObject.multiplier;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static java.lang.Math.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

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
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class InstanceTypeDescriptor
extends AbstractEnumerationTypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
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
	private static AvailObject getInstance (final A_Type object)
	{
		return object.slot(INSTANCE);
	}


	/**
	 * Answer the kind that is nearest to the given object, an {@linkplain
	 * InstanceTypeDescriptor instance type}.
	 *
	 * @param object
	 *        An instance type.
	 * @return
	 *        The kind (a {@linkplain TypeDescriptor type} but not an
	 *        {@linkplain AbstractEnumerationTypeDescriptor enumeration}) that
	 *        is nearest the specified instance type.
	 */
	private static A_Type getSuperkind (final A_Type object)
	{
		return getInstance(object).kind();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append("(");
		getInstance(object).printOnAvoidingIndent(
			aStream,
			recursionList,
			indent);
		aStream.append(")'s type");
	}

	/**
	 * Compute the type intersection of the object which is an instance type,
	 * and the argument, which may or may not be an instance type (but must be a
	 * type).
	 *
	 * @param object
	 *        An instance type.
	 * @param another
	 *        Another type.
	 * @return
	 *        The most general type that is a subtype of both object and
	 *        another.
	 */
	@Override final
	A_Type computeIntersectionWith (
		final A_Type object,
		final A_Type another)
	{
		if (another.isEnumeration())
		{
			if (another.isInstanceMeta())
			{
				// Intersection of an instance type and an instance meta is
				// always bottom.
				return BottomTypeDescriptor.bottom();
			}
			// Create a new enumeration containing all elements that are
			// simultaneously present in object and another.
			if (another.instances().hasElement(getInstance(object)))
			{
				return object;
			}
			return BottomTypeDescriptor.bottom();
		}
		// Keep the instance if it complies with another, which is not an
		// enumeration.
		if (getInstance(object).isInstanceOfKind(another))
		{
			return object;
		}
		return BottomTypeDescriptor.bottom();
	}

	/**
	 * Compute the type union of the object, which is an {@linkplain
	 * InstanceTypeDescriptor instance type}, and the argument, which may or may
	 * not be an {@linkplain AbstractEnumerationTypeDescriptor enumeration} (but
	 * must be a {@linkplain TypeDescriptor type}).
	 *
	 * @param object
	 *        An instance type.
	 * @param another
	 *        Another type.
	 * @return
	 *        The most specific type that is a supertype of both {@code object}
	 *        and {@code another}.
	 */
	@Override final
	A_Type computeUnionWith (
		final A_Type object,
		final A_Type another)
	{
		if (another.isEnumeration())
		{
			if (another.isInstanceMeta())
			{
				// Union of an instance type and an instance meta is any.
				return ANY.o();
			}
			// Create a new enumeration containing all elements from both
			// enumerations.
			return AbstractEnumerationTypeDescriptor.withInstances(
				another.instances().setWithElementCanDestroy(
					getInstance(object),
					false));
		}
		// Another is a kind.
		if (getInstance(object).isInstanceOfKind(another))
		{
			return another;
		}
		return getSuperkind(object).typeUnion(another);
	}

	@Override
	AvailObject o_Instance (final AvailObject object)
	{
		return getInstance(object);
	}

	@Override @AvailMethod
	A_Type o_ComputeSuperkind (final AvailObject object)
	{
		return getSuperkind(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * An instance type is only equal to another instance type, and only when
	 * they refer to equal instances.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		final boolean equal = another.equalsInstanceTypeFor(
			getInstance(object));
		if (equal)
		{
			if (!isShared())
			{
				another.makeImmutable();
				object.becomeIndirectionTo(another);
			}
			else if (!another.descriptor().isShared())
			{
				object.makeImmutable();
				another.becomeIndirectionTo(object);
			}
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
	@Override @AvailMethod
	boolean o_EqualsInstanceTypeFor (
		final AvailObject object,
		final AvailObject anObject)
	{
		return getInstance(object).equals(anObject);
	}

	/**
	 * The potentialInstance is a {@linkplain ObjectDescriptor user-defined
	 * object}.  See if it is an instance of the object.
	 */
	@Override @AvailMethod
	boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return getInstance(object).equals(potentialInstance);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return (getInstance(object).hash() ^ 0x15d5b163) * multiplier;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOf (final AvailObject object, final A_Type aType)
	{
		if (aType.isInstanceMeta())
		{
			// I'm a singular enumeration of a non-type, and aType is an
			// instance meta (the only sort of meta that exists these
			// days -- 2012.07.17).  See if my instance (a non-type) is an
			// instance of aType's instance (a type).
			return getInstance(object).isInstanceOf(aType.instance());
		}
		// I'm a singular enumeration of a non-type, so I could only be an
		// instance of a meta (already excluded), or of ANY or TOP.
		return aType.isSupertypeOfPrimitiveTypeEnum(ANY);
	}

	@Override @AvailMethod
	A_Map o_FieldTypeMap (final AvailObject object)
	{
		return getSuperkind(object).fieldTypeMap();
	}

	@Override @AvailMethod
	A_Number o_LowerBound (final AvailObject object)
	{
		final AvailObject instance = getInstance(object);
		assert instance.isExtendedInteger();
		return instance;
	}

	@Override @AvailMethod
	boolean o_LowerInclusive (final AvailObject object)
	{
		assert getInstance(object).isExtendedInteger();
		return true;
	}

	@Override @AvailMethod
	A_Number o_UpperBound (final AvailObject object)
	{
		final AvailObject instance = getInstance(object);
		assert instance.isExtendedInteger();
		return instance;
	}

	@Override @AvailMethod
	boolean o_UpperInclusive (final AvailObject object)
	{
		assert getInstance(object).isExtendedInteger();
		return true;
	}

	@Override @AvailMethod
	A_Type o_TypeAtIndex (final AvailObject object, final int index)
	{
		// This is only intended for a TupleType stand-in. Answer what type the
		// given index would have in an object instance of me. Answer ⊥ if the
		// index is out of bounds.
		final A_Tuple tuple = getInstance(object);
		assert tuple.isTuple();
		if (1 <= index && index <= tuple.tupleSize())
		{
			return AbstractEnumerationTypeDescriptor.withInstance(
				tuple.tupleAt(index));
		}
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	A_Type o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		// Answer the union of the types that object's instances could have in
		// the given range of indices. Out-of-range indices are treated as ⊥,
		// which don't affect the union (unless all indices are out of range).
		final A_Tuple tuple = getInstance(object);
		assert tuple.isTuple();
		if (startIndex > endIndex)
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
			return AbstractEnumerationTypeDescriptor.withInstance(
				tuple.tupleAt(startIndex));
		}
		A_Set set = SetDescriptor.empty();
		for (
			int i = max(startIndex, 1), end = min(endIndex, upperIndex);
			i <= end;
			i++)
		{
			set = set.setWithElementCanDestroy(
				tuple.tupleAt(i),
				true);
		}
		return AbstractEnumerationTypeDescriptor.withInstances(set);
	}

	@Override @AvailMethod
	A_Type o_DefaultType (final AvailObject object)
	{
		final A_Tuple tuple = getInstance(object);
		assert tuple.isTuple();
		final int tupleSize = tuple.tupleSize();
		if (tupleSize == 0)
		{
			return BottomTypeDescriptor.bottom();
		}
		return AbstractEnumerationTypeDescriptor.withInstance(
			tuple.tupleAt(tupleSize));
	}

	@Override @AvailMethod
	A_Type o_SizeRange (final AvailObject object)
	{
		final A_BasicObject instance = getInstance(object);
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
		return NilDescriptor.nil();
	}

	@Override @AvailMethod
	A_Tuple o_TypeTuple (final AvailObject object)
	{
		assert getInstance(object).isTuple();
		return getSuperkind(object).typeTuple();
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return getInstance(object).isInstanceOf(aType);
	}

	@Override @AvailMethod
	boolean o_IsIntegerRangeType (final AvailObject object)
	{
		return getInstance(object).isExtendedInteger();
	}

	@Override @AvailMethod
	boolean o_IsLiteralTokenType (final AvailObject object)
	{
		return getInstance(object).isLiteralToken();
	}

	@Override @AvailMethod
	boolean o_IsMapType (final AvailObject object)
	{
		return getInstance(object).isMap();
	}

	@Override @AvailMethod
	boolean o_IsSetType (final AvailObject object)
	{
		return getInstance(object).isSet();
	}

	@Override @AvailMethod
	boolean o_IsTupleType (final AvailObject object)
	{
		return getInstance(object).isTuple();
	}

	@Override @AvailMethod
	A_Number o_InstanceCount (final AvailObject object)
	{
		return IntegerDescriptor.one();
	}

	@Override @AvailMethod
	A_Set o_Instances (final AvailObject object)
	{
		return SetDescriptor.empty().setWithElementCanDestroy(
			getInstance(object),
			true);
	}

	@Override @AvailMethod
	boolean o_EnumerationIncludesInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return potentialInstance.equals(getInstance(object));
	}

	@Override @AvailMethod
	boolean o_AcceptsArgTypesFromFunctionType (
		final AvailObject object,
		final A_Type functionType)
	{
		return getSuperkind(object).acceptsArgTypesFromFunctionType(functionType);
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<A_Type> argTypes)
	{
		return getSuperkind(object).acceptsListOfArgTypes(argTypes);
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<? extends A_BasicObject> argValues)
	{
		return getSuperkind(object).acceptsListOfArgValues(argValues);
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final A_Tuple argTypes)
	{
		return getSuperkind(object).acceptsTupleOfArgTypes(argTypes);
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final A_Tuple arguments)
	{
		return getSuperkind(object).acceptsTupleOfArguments(arguments);
	}

	@Override @AvailMethod
	A_Type o_ArgsTupleType (final AvailObject object)
	{
		return getSuperkind(object).argsTupleType();
	}

	@Override @AvailMethod
	A_Set o_DeclaredExceptions (final AvailObject object)
	{
		return getSuperkind(object).declaredExceptions();
	}

	@Override @AvailMethod
	A_Type o_FunctionType (final AvailObject object)
	{
		return getSuperkind(object).functionType();
	}

	@Override @AvailMethod
	A_Type o_ContentType (final AvailObject object)
	{
		/*
		 * Wow, this is weird. Ask a set for its type and you get an instance
		 * type that refers back to the set.  Ask that type for its content type
		 * (since it's technically a set type) and it reports an enumeration
		 * whose sole instance is this set again.
		 */
		final AvailObject set = getInstance(object);
		assert set.isSet();
		return AbstractEnumerationTypeDescriptor.withInstances(set);
	}

	@Override @AvailMethod
	boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		return getSuperkind(object).couldEverBeInvokedWith(argTypes);
	}

	@Override @AvailMethod
	A_Type o_KeyType (final AvailObject object)
	{
		return getSuperkind(object).keyType();
	}

	@Override @AvailMethod
	AvailObject o_Name (final AvailObject object)
	{
		return getSuperkind(object).name();
	}

	@Override @AvailMethod
	A_BasicObject o_Parent (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_ReturnType (final AvailObject object)
	{
		return getSuperkind(object).returnType();
	}

	@Override @AvailMethod
	A_Type o_ValueType (final AvailObject object)
	{
		return getSuperkind(object).valueType();
	}

	@Override @AvailMethod
	A_Type o_ReadType (final AvailObject object)
	{
		return getSuperkind(object).readType();
	}

	@Override @AvailMethod
	A_Type o_WriteType (final AvailObject object)
	{
		return getSuperkind(object).writeType();
	}

	@Override @AvailMethod
	A_Type o_ExpressionType (final AvailObject object)
	{
		return getInstance(object).expressionType();
	}

	@Override
	boolean o_RangeIncludesInt (final AvailObject object, final int anInt)
	{
		final AvailObject instance = getInstance(object);
		return instance.isInt() && instance.extractInt() == anInt;
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.INSTANCE_TYPE;
	}

	/**
	 * Construct a new {@link InstanceTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private InstanceTypeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link InstanceTypeDescriptor}. */
	private static final AbstractEnumerationTypeDescriptor mutable =
		new InstanceTypeDescriptor(Mutability.MUTABLE);

	@Override
	AbstractEnumerationTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link InstanceTypeDescriptor}. */
	private static final AbstractEnumerationTypeDescriptor immutable =
		new InstanceTypeDescriptor(Mutability.IMMUTABLE);

	@Override
	AbstractEnumerationTypeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link InstanceTypeDescriptor}. */
	private static final AbstractEnumerationTypeDescriptor shared =
		new InstanceTypeDescriptor(Mutability.SHARED);

	@Override
	AbstractEnumerationTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Answer a new instance of this descriptor based on some object whose type
	 * it will represent.
	 *
	 * @param instance The object whose type to represent.
	 * @return An {@link AvailObject} representing the type of the argument.
	 */
	public static AvailObject on (final A_BasicObject instance)
	{
		assert !instance.isType();
		final AvailObject result = mutable.create();
		instance.makeImmutable();
		result.setSlot(INSTANCE, instance);
		return result;
	}
}
