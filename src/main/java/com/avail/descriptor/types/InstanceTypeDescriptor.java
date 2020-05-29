/*
 * InstanceTypeDescriptor.java
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

import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.maps.A_Map;
import com.avail.descriptor.numbers.A_Number;
import com.avail.descriptor.objects.ObjectDescriptor;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.sets.A_Set;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.optimizer.jvm.CheckedMethod;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;
import java.util.List;

import static com.avail.descriptor.numbers.IntegerDescriptor.one;
import static com.avail.descriptor.representation.AvailObject.multiplier;
import static com.avail.descriptor.representation.NilDescriptor.nil;
import static com.avail.descriptor.sets.SetDescriptor.generateSetFrom;
import static com.avail.descriptor.sets.SetDescriptor.singletonSet;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.generateObjectTupleFrom;
import static com.avail.descriptor.types.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.types.InstanceTypeDescriptor.ObjectSlots.INSTANCE;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.singleInt;
import static com.avail.descriptor.types.TypeDescriptor.Types.ANY;
import static com.avail.optimizer.jvm.CheckedMethod.staticMethod;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * My instances are called <em>instance types</em>, the types of individual
 * objects.  In particular, whenever an object is asked for its {@linkplain
 * A_BasicObject#kind() type}, it creates an {@linkplain
 * InstanceTypeDescriptor instance type} that wraps that object.  Only that
 * object is a member of that instance type, except in the case that the object
 * is itself a type, in which case subtypes of that object are also considered
 * instances of the instance type.
 *
 * <p>
 * This last provision is to support the property called
 * <em>metacovariance</em>, which states that types' types vary the same way as
 * the types:
 * <span style="border-width:thin; border-style:solid; white-space:nowrap">
 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&sube;y &rarr;
 * T(x)&sube;T(y))</span>.
 * </p>
 *
 * <p>
 * The uniform use of instance types trivially ensures the additional property
 * we call <em>metavariance</em>, which states that every type has a unique
 * type of its own:
 * <span style="border-width:thin; border-style:solid; white-space:nowrap">
 * &forall;<sub>x,y&isin;T</sub>&thinsp;(x&ne;y &equiv;
 * T(x)&ne;T(y))</span>.
 * Note that metavariance requires this to hold for all types, but instance
 * types ensure this condition holds for all objects.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class InstanceTypeDescriptor
extends AbstractEnumerationTypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnumJava
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
	private static AvailObject getInstance (final AvailObject object)
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
	private static A_Type getSuperkind (final AvailObject object)
	{
		return getInstance(object).kind();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append("(");
		getInstance(object).printOnAvoidingIndent(
			aStream,
			recursionMap,
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
	@Override A_Type computeIntersectionWith (
		final AvailObject object,
		final A_Type another)
	{
		if (another.isEnumeration())
		{
			if (another.isInstanceMeta())
			{
				// Intersection of an instance type and an instance meta is
				// always bottom.
				return bottom();
			}
			// Create a new enumeration containing all elements that are
			// simultaneously present in object and another.
			if (another.instances().hasElement(getInstance(object)))
			{
				return object;
			}
			return bottom();
		}
		// Keep the instance if it complies with another, which is not an
		// enumeration.
		if (getInstance(object).isInstanceOfKind(another))
		{
			return object;
		}
		return bottom();
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
	@Override A_Type computeUnionWith (
		final AvailObject object,
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
			return
				enumerationWith(
					another.instances().setWithElementCanDestroy(
						getInstance(object), false));
		}
		// Another is a kind.
		if (getInstance(object).isInstanceOfKind(another))
		{
			return another;
		}
		return getSuperkind(object).typeUnion(another);
	}

	@Override
	public AvailObject o_Instance (final AvailObject object)
	{
		return getInstance(object);
	}

	@Override
	public A_Type o_ComputeSuperkind (final AvailObject object)
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
	@Override
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
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
	@Override
	public boolean o_EqualsInstanceTypeFor (
		final AvailObject object,
		final AvailObject anObject)
	{
		return getInstance(object).equals(anObject);
	}

	/**
	 * The potentialInstance is a {@linkplain ObjectDescriptor user-defined
	 * object}.  See if it is an instance of the object.
	 */
	@Override
	public boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return getInstance(object).equals(potentialInstance);
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return (getInstance(object).hash() ^ 0x15d5b163) * multiplier;
	}

	@Override
	public boolean o_IsInstanceOf (final AvailObject object, final A_Type aType)
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

	@Override
	public A_Type o_FieldTypeAt (final AvailObject object, final A_Atom field)
	{
		return getSuperkind(object).fieldTypeAt(field);
	}

	@Override
	public A_Map o_FieldTypeMap (final AvailObject object)
	{
		return getSuperkind(object).fieldTypeMap();
	}

	@Override
	public A_Tuple o_FieldTypeTuple (final AvailObject object)
	{
		return getSuperkind(object).fieldTypeTuple();
	}

	@Override
	public A_Number o_LowerBound (final AvailObject object)
	{
		final AvailObject instance = getInstance(object);
		assert instance.isExtendedInteger();
		return instance;
	}

	@Override
	public boolean o_LowerInclusive (final AvailObject object)
	{
		assert getInstance(object).isExtendedInteger();
		return true;
	}

	@Override
	public A_Number o_UpperBound (final AvailObject object)
	{
		final AvailObject instance = getInstance(object);
		assert instance.isExtendedInteger();
		return instance;
	}

	@Override
	public boolean o_UpperInclusive (final AvailObject object)
	{
		assert getInstance(object).isExtendedInteger();
		return true;
	}

	@Override
	public A_Type o_TypeAtIndex (final AvailObject object, final int index)
	{
		// This is only intended for a TupleType stand-in. Answer what type the
		// given index would have in an object instance of me. Answer ⊥ if the
		// index is out of bounds.
		final A_Tuple tuple = getInstance(object);
		assert tuple.isTuple();
		if (1 <= index && index <= tuple.tupleSize())
		{
			return instanceTypeOrMetaOn(tuple.tupleAt(index));
		}
		return bottom();
	}

	@Override
	public A_Type o_UnionOfTypesAtThrough (
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
			return bottom();
		}
		final int upperIndex = tuple.tupleSize();
		if (startIndex > upperIndex)
		{
			return bottom();
		}
		if (startIndex == endIndex)
		{
			return instanceTypeOrMetaOn(tuple.tupleAt(startIndex));
		}
		final int offset = max(startIndex, 1) - 1;
		final int size = min(endIndex, upperIndex) - offset;
		final A_Set set = generateSetFrom(size, i -> tuple.tupleAt(i + offset));
		return enumerationWith(set);
	}

	@Override
	public A_Type o_DefaultType (final AvailObject object)
	{
		final A_Tuple tuple = getInstance(object);
		assert tuple.isTuple();
		final int tupleSize = tuple.tupleSize();
		if (tupleSize == 0)
		{
			return bottom();
		}
		return instanceTypeOrMetaOn(tuple.tupleAt(tupleSize));
	}

	@Override
	public A_Type o_SizeRange (final AvailObject object)
	{
		final A_BasicObject instance = getInstance(object);
		if (instance.isTuple())
		{
			return singleInt(getInstance(object).tupleSize());
		}
		if (instance.isSet())
		{
			return singleInt(getInstance(object).setSize());
		}
		if (instance.isMap())
		{
			return singleInt(getInstance(object).mapSize());
		}
		assert false : "Unexpected instance for sizeRange";
		return nil;
	}

	@Override
	public A_Tuple o_TypeTuple (final AvailObject object)
	{
		assert getInstance(object).isTuple();
		return getSuperkind(object).typeTuple();
	}

	@Override
	public boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return getInstance(object).isInstanceOf(aType);
	}

	@Override
	public boolean o_IsIntegerRangeType (final AvailObject object)
	{
		return getInstance(object).isExtendedInteger();
	}

	@Override
	public boolean o_IsLiteralTokenType (final AvailObject object)
	{
		return getInstance(object).isLiteralToken();
	}

	@Override
	public boolean o_IsMapType (final AvailObject object)
	{
		return getInstance(object).isMap();
	}

	@Override
	public boolean o_IsSetType (final AvailObject object)
	{
		return getInstance(object).isSet();
	}

	@Override
	public boolean o_IsTupleType (final AvailObject object)
	{
		return getInstance(object).isTuple();
	}

	@Override
	public A_Number o_InstanceCount (final AvailObject object)
	{
		return one();
	}

	@Override
	public A_Set o_Instances (final AvailObject object)
	{
		return singletonSet(getInstance(object));
	}

	@Override
	public boolean o_EnumerationIncludesInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return potentialInstance.equals(getInstance(object));
	}

	@Override
	public boolean o_AcceptsArgTypesFromFunctionType (
		final AvailObject object,
		final A_Type functionType)
	{
		return getSuperkind(object).acceptsArgTypesFromFunctionType(
			functionType);
	}

	@Override
	public boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		return getSuperkind(object).acceptsListOfArgTypes(argTypes);
	}

	@Override
	public boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<? extends A_BasicObject> argValues)
	{
		return getSuperkind(object).acceptsListOfArgValues(argValues);
	}

	@Override
	public boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final A_Tuple argTypes)
	{
		return getSuperkind(object).acceptsTupleOfArgTypes(argTypes);
	}

	@Override
	public boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final A_Tuple arguments)
	{
		return getSuperkind(object).acceptsTupleOfArguments(arguments);
	}

	@Override
	public A_Type o_ArgsTupleType (final AvailObject object)
	{
		return getSuperkind(object).argsTupleType();
	}

	@Override
	public A_Set o_DeclaredExceptions (final AvailObject object)
	{
		return getSuperkind(object).declaredExceptions();
	}

	@Override
	public A_Type o_FunctionType (final AvailObject object)
	{
		return getSuperkind(object).functionType();
	}

	@Override
	public A_Type o_ContentType (final AvailObject object)
	{
		/*
		 * Wow, this is weird. Ask a set for its type and you get an instance
		 * type that refers back to the set.  Ask that type for its content type
		 * (since it's technically a set type) and it reports an enumeration
		 * whose sole instance is this set again.
		 */
		final A_Set set = getInstance(object);
		assert set.isSet();
		return enumerationWith(set);
	}

	@Override
	public boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<TypeRestriction> argRestrictions)
	{
		return getSuperkind(object).couldEverBeInvokedWith(argRestrictions);
	}

	@Override
	public A_Type o_KeyType (final AvailObject object)
	{
		return getSuperkind(object).keyType();
	}

	@Override
	public A_BasicObject o_Parent (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	public A_Type o_ReturnType (final AvailObject object)
	{
		return getSuperkind(object).returnType();
	}

	@Override
	public A_Type o_ValueType (final AvailObject object)
	{
		return getSuperkind(object).valueType();
	}

	@Override
	public A_Type o_ReadType (final AvailObject object)
	{
		return getSuperkind(object).readType();
	}

	@Override
	public A_Type o_WriteType (final AvailObject object)
	{
		return getSuperkind(object).writeType();
	}

	@Override
	public A_Type o_ExpressionType (final AvailObject object)
	{
		return getInstance(object).expressionType();
	}

	@Override
	public boolean o_RangeIncludesInt (final AvailObject object, final int anInt)
	{
		final AvailObject instance = getInstance(object);
		return instance.isInt() && instance.extractInt() == anInt;
	}

	@Override
	public SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.INSTANCE_TYPE;
	}

	@Override
	public A_Tuple o_TupleOfTypesFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		// Answer the tuple of types over the given range of indices.  Any
		// indices out of range for this tuple type will be ⊥.
		assert startIndex >= 1;
		final int size = endIndex - startIndex + 1;
		assert size >= 0;
		final A_Tuple tuple = getInstance(object);
		final int tupleSize = tuple.tupleSize();
		return generateObjectTupleFrom(
			size,
			i -> i <= tupleSize
				? instanceTypeOrMetaOn(tuple.tupleAt(i)).makeImmutable()
				: bottom());
	}

	@Override
	public void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		getSuperkind(object).writeTo(writer);
		writer.write("instances");
		object.instances().writeTo(writer);
		writer.endObject();
	}

	@Override
	public void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		getSuperkind(object).writeSummaryTo(writer);
		writer.write("instances");
		object.instances().writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	public TypeTag o_ComputeTypeTag (final AvailObject object)
	{
		return getInstance(object).typeTag().metaTag();
	}

	/**
	 * Construct a new {@code InstanceTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private InstanceTypeDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.UNKNOWN_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link InstanceTypeDescriptor}. */
	private static final AbstractEnumerationTypeDescriptor mutable =
		new InstanceTypeDescriptor(Mutability.MUTABLE);

	@Override
	public AbstractEnumerationTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link InstanceTypeDescriptor}. */
	private static final AbstractEnumerationTypeDescriptor immutable =
		new InstanceTypeDescriptor(Mutability.IMMUTABLE);

	@Override
	public AbstractEnumerationTypeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link InstanceTypeDescriptor}. */
	private static final AbstractEnumerationTypeDescriptor shared =
		new InstanceTypeDescriptor(Mutability.SHARED);

	@Override
	public AbstractEnumerationTypeDescriptor shared ()
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
	@ReferencedInGeneratedCode
	public static AvailObject instanceType (final A_BasicObject instance)
	{
		assert !instance.isType();
		assert !instance.equalsNil();
		final AvailObject result = mutable.create();
		instance.makeImmutable();
		result.setSlot(INSTANCE, instance);
		return result;
	}

	/**
	 * The {@link CheckedMethod} for {@link #instanceType(A_BasicObject)}.
	 */
	public static final CheckedMethod instanceTypeMethod = staticMethod(
		InstanceTypeDescriptor.class,
		"instanceType",
		AvailObject.class,
		A_BasicObject.class);
}
