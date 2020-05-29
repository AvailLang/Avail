/*
 * BottomTypeDescriptor.java
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

import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.maps.A_Map;
import com.avail.descriptor.numbers.A_Number;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.sets.A_Set;
import com.avail.descriptor.sets.SetDescriptor;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.tuples.RepeatedElementTupleDescriptor;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;
import java.util.List;

import static com.avail.descriptor.numbers.InfinityDescriptor.negativeInfinity;
import static com.avail.descriptor.numbers.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.numbers.IntegerDescriptor.zero;
import static com.avail.descriptor.sets.SetDescriptor.emptySet;
import static com.avail.descriptor.tuples.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.types.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.types.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.types.TupleTypeDescriptor.mostGeneralTupleType;
import static com.avail.descriptor.types.TypeDescriptor.Types.ANY;
import static com.avail.descriptor.types.TypeDescriptor.Types.TOP;

/**
 * {@code BottomTypeDescriptor} represents Avail's most specific type, ⊥
 * (pronounced bottom). ⊥ is an abstract type; it cannot have any instances,
 * since its instances must be able to meaningfully perform all operations, and
 * this is clearly logically inconsistent.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class BottomTypeDescriptor
extends AbstractEnumerationTypeDescriptor
{
	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append("⊥");
	}

	/**
	 * Compute the type intersection of the object which is the bottom type,
	 * and the argument, which may be any type.
	 *
	 * @param object
	 *            The bottom type.
	 * @param another
	 *            Another type.
	 * @return
	 *            The most general type that is a subtype of both object and
	 *            another.
	 */
	@Override
	public A_Type computeIntersectionWith (
		final AvailObject object,
		final A_Type another)
	{
		// Easy -- it's always the type bottom.
		return object;
	}

	/**
	 * Compute the type union of the object which is the bottom type, and
	 * the argument, which may be any type.
	 *
	 * @param object
	 *            The bottom type.
	 * @param another
	 *            Another type.
	 * @return
	 *            The most specific type that is a supertype of both object and
	 *            another.
	 */
	@Override
	public A_Type computeUnionWith (
		final AvailObject object,
		final A_Type another)
	{
		// Easy -- it's always the other type.
		assert another.isType();
		return another;
	}

	@Override
	public boolean o_AcceptsArgTypesFromFunctionType (
		final AvailObject object,
		final A_Type functionType)
	{
		return true;
	}

	@Override
	public boolean o_AcceptsListOfArgTypes (
		final AvailObject object,
		final List<? extends A_Type> argTypes)
	{
		return true;
	}

	@Override
	public boolean o_AcceptsListOfArgValues (
		final AvailObject object,
		final List<? extends A_BasicObject> argValues)
	{
		return true;
	}

	@Override
	public boolean o_AcceptsTupleOfArgTypes (
		final AvailObject object,
		final A_Tuple argTypes)
	{
		return true;
	}

	@Override
	public boolean o_AcceptsTupleOfArguments (
		final AvailObject object,
		final A_Tuple arguments)
	{
		return true;
	}

	@Override
	public A_Type o_ArgsTupleType (final AvailObject object)
	{
		// Because ⊥ is a subtype of all other types, it is considered a
		// function type. In particular, if ⊥ is viewed as a function type, it
		// can take any number of arguments of any type (since there are no
		// complying function instances).
		return mostGeneralTupleType();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Even though bottom is a union-y type (and the most specific one), it
	 * technically "is" also a kind (a non-union-y type).  Thus, it's still
	 * technically correct to return bottom as the nearest kind.  Code that
	 * relies on this operation <em>not</em> returning a union-y type should
	 * deal with this one special case with correspondingly special logic.
	 * </p>
	 */
	@Override
	public A_Type o_ComputeSuperkind (final AvailObject object)
	{
		return object;
	}

	@Override
	public A_Type o_ContentType (final AvailObject object)
	{
		return object;
	}

	@Override
	public boolean o_CouldEverBeInvokedWith (
		final AvailObject object,
		final List<TypeRestriction> argRestrictions)
	{
		return true;
	}

	@Override
	public A_Set o_DeclaredExceptions (final AvailObject object)
	{
		return emptySet();
	}

	@Override
	public A_Type o_DefaultType (final AvailObject object)
	{
		// Since I'm a degenerate tuple type, I must answer ⊥.
		return object;
	}

	/**
	 * Bottom is an empty {@linkplain AbstractEnumerationTypeDescriptor
	 * enumeration}, so the answer is {@code false}.
	 */
	@Override
	public boolean o_EnumerationIncludesInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return false;
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
		final AvailObject object,
		final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Determine if the object is an {@linkplain
	 * AbstractEnumerationTypeDescriptor enumeration} over the given {@linkplain
	 * SetDescriptor set} of instances.  Since the object is the {@linkplain
	 * BottomTypeDescriptor bottom type}, just check if the set of instances is
	 * empty.
	 * </p>
	 */
	@Override
	public boolean o_EqualsEnumerationWithSet (
		final AvailObject object,
		final A_Set aSet)
	{
		return aSet.setSize() == 0;
	}

	@Override
	public A_Type o_ExpressionType (
		final AvailObject object)
	{
		return object;
	}

	@Override
	public A_Type o_FieldTypeAt (final AvailObject object, final A_Atom field)
	{
		// All fields would be present for this type, but they would have type
		// bottom.
		return object;
	}

	@Override
	public A_Map o_FieldTypeMap (final AvailObject object)
	{
		// TODO: [MvG] It's unclear what to return here. Maybe raise an
		// unchecked exception. Or if we ever implement more precise map types
		// containing key type -> value type pairs we might be able to change
		// the object type interface to use one of those instead of a map.
		throw unsupportedOperationException();
	}

	@Override
	public A_Type o_FunctionType (final AvailObject object)
	{
		return object;
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return 0x4a22a80a;
	}

	@Override
	public boolean o_HasObjectInstance (
		final AvailObject object,
		final AvailObject potentialInstance)
	{
		return false;
	}

	@Override
	public A_Number o_InstanceCount (final AvailObject object)
	{
		// ⊥ is the empty enumeration.
		return zero();
	}

	@Override
	public A_Set o_Instances (final AvailObject object)
	{
		// ⊥ is the empty enumeration.
		return emptySet();
	}

	@Override
	public boolean o_IsBottom (final AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_IsVacuousType (final AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_IsInstanceOf (
		final AvailObject object,
		final A_Type aType)
	{
		// Bottom is an instance of every metatype except for itself.
		assert aType.isType();
		if (object.equals(aType))
		{
			// Bottom is not an instance of itself.
			return false;
		}
		if (aType.isEnumeration())
		{
			return aType.enumerationIncludesInstance(object);
		}
		// Bottom is an instance of top and any.
		if (aType.isTop() || aType.equals(ANY.o()))
		{
			return true;
		}
		// Bottom is an instance of every meta (everything that inherits
		// from TYPE).
		return aType.isSubtypeOf(topMeta());
	}

	@Override
	public boolean o_IsInstanceOfKind (
		final AvailObject object,
		final A_Type aType)
	{
		assert !aType.isBottom();
		return aType.isSupertypeOfPrimitiveTypeEnum(ANY)
			|| aType.isSubtypeOf(topMeta());
	}

	@Override
	public boolean o_IsIntegerRangeType (final AvailObject object)
	{
		// Because ⊥ is a subtype of all other types, it is considered an
		// integer range type - in particular, the degenerate integer type
		// (∞..-∞).
		return true;
	}

	@Override
	public boolean o_IsLiteralTokenType (
		final AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_IsMapType (final AvailObject object)
	{
		// Because ⊥ is a subtype of all other types, it is considered a map
		// type - in particular, a degenerate map type. Its size range is ⊥, its
		// key type is ⊥, and its value type is ⊥.
		return true;
	}

	@Override
	public boolean o_IsPojoArrayType (final AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_IsPojoFusedType (final AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_IsPojoSelfType (final AvailObject object)
	{
		return false;
	}

	@Override
	public boolean o_IsPojoType (final AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_IsSetType (final AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return true;
	}

	@Override
	public boolean o_IsTupleType (final AvailObject object)
	{
		// Because ⊥ is a subtype of all other types, it is considered a tuple
		// type - in particular, a degenerate tuple type. Its size range is ⊥,
		// its leading type tuple is <>, and its default type is ⊥.
		return true;
	}

	@Override
	public A_Type o_KeyType (final AvailObject object)
	{
		// Answer what type my keys are. Since I'm a degenerate map type,
		// answer ⊥.
		return object;
	}

	@Override
	public A_Number o_LowerBound (final AvailObject object)
	{
		// Pretend we go from +∞ to -∞ exclusive. That should be a nice empty
		// range.
		return positiveInfinity();
	}

	@Override
	public boolean o_LowerInclusive (final AvailObject object)
	{
		// Pretend we go from +∞ to -∞ exclusive. That should be a nice empty
		// range.
		return false;
	}

	@Override
	public A_BasicObject o_Parent (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	public boolean o_RangeIncludesInt (
		final AvailObject object,
		final int anInt)
	{
		return false;
	}

	@Override
	public A_Type o_ReturnType (final AvailObject object)
	{
		return object;
	}

	@Override
	public SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.BOTTOM_TYPE;
	}

	@Override
	public A_Type o_SizeRange (final AvailObject object)
	{
		// Answer what sizes my instances can be. Since I'm a degenerate
		// map type, answer ⊥, a degenerate integer type.
		return object;
	}

	@Override
	public A_Type o_SubexpressionsTupleType (final AvailObject object)
	{
		// See ListPhraseDescriptor.
		return object;
	}

	@Override
	public A_Type o_TypeAtIndex (final AvailObject object, final int index)
	{
		// Answer what type the given index would have in an object instance of
		// me. Answer ⊥ if the index is out of bounds, which is always because
		// I'm a degenerate tuple type.
		return object;
	}

	@Override
	public A_Tuple o_TypeTuple (final AvailObject object)
	{
		// Since I'm a degenerate tuple type, I have no leading types.
		return emptyTuple();
	}

	@Override
	public A_Type o_UnionOfTypesAtThrough (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		// Answer the union of the types the given indices would have in an
		// object instance of me. Answer ⊥ if the index is out of bounds, which
		// is always because I'm a degenerate tuple type.
		return object;
	}

	@Override
	public A_Number o_UpperBound (final AvailObject object)
	{
		// Pretend we go from +∞ to -∞ exclusive. That should be a nice empty
		// range.
		return negativeInfinity();
	}

	@Override
	public boolean o_UpperInclusive (final AvailObject object)
	{
		// Pretend we go from +∞ to -∞ exclusive. That should be a nice empty
		// range.
		return false;
	}

	@Override
	public A_Type o_ValueType (
		final AvailObject object)
	{
		// Answer what type my values are. Since I'm a degenerate map type,
		// answer ⊥.
		return object;
	}

	@Override
	public A_Type o_ReadType (
		final AvailObject object)
	{
		return TOP.o();
	}

	@Override
	public A_Tuple o_TupleOfTypesFromTo (
		final AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		// Answer the tuple of types over the given range of indices.  Any
		// indices out of range for this tuple type will be ⊥.
		return RepeatedElementTupleDescriptor.createRepeatedElementTuple(
			endIndex - startIndex + 1,
			object);
	}

	@Override
	public void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("bottom");
		writer.endObject();
	}

	@Override
	public A_Type o_WriteType (final AvailObject object)
	{
		return bottom();
	}

	@Override
	public TypeTag o_ComputeTypeTag (final AvailObject object)
	{
		return TypeTag.BOTTOM_TYPE_TAG;
	}

	/**
	 * Construct a new {@code BottomTypeDescriptor}.
	 */
	private BottomTypeDescriptor ()
	{
		super(Mutability.SHARED, TypeTag.BOTTOM_TYPE_TAG, null, null);
	}

	@Override
	public BottomTypeDescriptor mutable ()
	{
		throw unsupportedOperationException();
	}

	@Override
	public BottomTypeDescriptor immutable ()
	{
		throw unsupportedOperationException();
	}

	/** The shared {@link BottomTypeDescriptor}. */
	private static final BottomTypeDescriptor shared =
		new BottomTypeDescriptor();

	@Override
	public BottomTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * The unique object that represents the type with no instances.
	 */
	private static final A_Type bottom = shared.create();

	/**
	 * Answer the unique type that has no instances.
	 *
	 * @return The type {@code bottom}.
	 */
	public static A_Type bottom ()
	{
		return bottom;
	}

	/** The meta-type with exactly one instance, {@link #bottom()}. */
	private static final A_Type bottomMeta = instanceMeta(bottom).makeShared();

	/**
	 * Answer the meta-type whose sole instance is the type {@link #bottom()}.
	 *
	 * @return The meta-type which is {@code bottom}'s type.
	 */
	public static A_Type bottomMeta ()
	{
		return bottomMeta;
	}
}
