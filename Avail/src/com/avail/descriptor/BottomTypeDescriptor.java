/**
 * BottomTypeDescriptor.java
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
 * {@code BottomTypeDescriptor} represents Avail's most specific type, ⊥
 * (pronounced bottom). ⊥ is an abstract type; it cannot have any instances,
 * since its instances must be able to meaningfully perform all operations, and
 * this is clearly logically inconsistent.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class BottomTypeDescriptor
extends AbstractEnumerationTypeDescriptor
{
	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("⊥");
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
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		final boolean equal = another.equalsEnumerationWithSet(
			SetDescriptor.empty());
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
	 * Determine if the object is an {@linkplain
	 * AbstractEnumerationTypeDescriptor enumeration} over the given {@linkplain
	 * SetDescriptor set} of instances.  Since the object is the {@linkplain
	 * BottomTypeDescriptor bottom type}, just check if the set of instances is
	 * empty.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_EqualsEnumerationWithSet (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aSet)
	{
		return aSet.setSize() == 0;
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
	@Override final
	@NotNull AvailObject computeIntersectionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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
	@Override final
	@NotNull AvailObject computeUnionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		// Easy -- it's always the other type.
		assert another.isType();
		return another;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object)
	{
		//  Pretend we go from +INF to -INF exclusive.  That should be a nice empty range.

		return InfinityDescriptor.positiveInfinity();
	}

	@Override @AvailMethod
	boolean o_LowerInclusive (
		final @NotNull AvailObject object)
	{
		//  Pretend we go from +INF to -INF exclusive.  That should be a nice empty range.

		return false;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object)
	{
		//  Pretend we go from +INF to -INF exclusive.  That should be a nice empty range.

		return InfinityDescriptor.negativeInfinity();
	}

	@Override @AvailMethod
	boolean o_UpperInclusive (
		final @NotNull AvailObject object)
	{
		//  Pretend we go from +INF to -INF exclusive.  That should be a nice empty range.

		return false;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_KeyType (
		final @NotNull AvailObject object)
	{
		//  Answer what type my keys are.  Since I'm the degenerate mapType called
		//  bottom, answer bottom.

		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		//  Answer what sizes my instances can be.  Since I'm the degenerate mapType called
		//  bottom, answer the degenerate integerType called bottom.

		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ValueType (
		final @NotNull AvailObject object)
	{
		//  Answer what type my values are.  Since I'm the degenerate mapType called
		//  bottom, answer bottom.

		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		//  Answer what type the given index would have in an object instance of me.  Answer
		//  bottom if the index is out of bounds, which is always because I'm the degenerate
		//  tupleType called bottom.

		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		//  Answer the union of the types the given indices would have in an object instance of me.
		//  Answer bottom if the index is out of bounds, which is always because I'm the degenerate
		//  tupleType called bottom.

		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object)
	{
		//  To support the tupleType protocol, I must answer bottom now.

		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object)
	{
		//  To support the tupleType protocol, I must answer <> now.

		return TupleDescriptor.empty();
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		// Check if object (bottom) is a subtype of aType (should also be a
		// type).  Always true, but make sure aType is really a type.
		return aType.isSupertypeOfBottom();
	}

	@Override @AvailMethod
	boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object)
	{
		//  Because bottom is a subtype of all other types, it is even considered
		//  an integer range type - in particular, the degenerate integer type (INF..-INF).

		return true;
	}

	@Override @AvailMethod
	boolean o_IsMapType (
		final @NotNull AvailObject object)
	{
		//  Because bottom is a subtype of all other types, it is even considered
		//  a map type - in particular, the degenerate map type.  Its sizeRange is
		//  bottom, its keyType is bottom, and its valueType is bottom.

		return true;
	}

	@Override @AvailMethod
	boolean o_IsTupleType (
		final @NotNull AvailObject object)
	{
		//  Because bottom is a subtype of all other types, it is even considered
		//  a tuple type - in particular, the degenerate tuple type.  Its sizeRange is
		//  bottom, its typeTuple is <>, and its defaultType is bottom.

		return true;
	}

	@Override @AvailMethod
	AvailObject o_ArgsTupleType (
		final @NotNull AvailObject object)
	{
		// Because bottom is a subtype of all other types, it is even
		// considered a function type.  In particular, if bottom is viewed
		// as a function type, it can take any number of arguments of any type
		// (since there are no complying function instances).

		return TupleTypeDescriptor.mostGeneralType();
	}


	/**
	 * The unique object that represents the type with no instances.
	 */
	private static AvailObject bottom;

	/**
	 * Answer the unique type that has no instances.
	 *
	 * @return The type {@code bottom}.
	 */
	public static AvailObject bottom ()
	{
		return bottom;
	}

	/**
	 * Create the unique object that represents the type with no instances.
	 */
	static void createWellKnownObjects ()
	{
		bottom = mutable().create();
	}

	/**
	 * Discard any statically held objects.
	 */
	static void clearWellKnownObjects ()
	{
		bottom = null;
	}



	/**
	 * Construct a new {@link BottomTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected BottomTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link BottomTypeDescriptor}.
	 */
	private static final BottomTypeDescriptor mutable =
		new BottomTypeDescriptor(true);

	/**
	 * Answer the mutable {@link BottomTypeDescriptor}.
	 *
	 * @return The mutable {@link BottomTypeDescriptor}.
	 */
	public static BottomTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link BottomTypeDescriptor}.
	 */
	private static final BottomTypeDescriptor immutable =
		new BottomTypeDescriptor(false);

	/**
	 * Answer the immutable {@link BottomTypeDescriptor}.
	 *
	 * @return The immutable {@link BottomTypeDescriptor}.
	 */
	public static BottomTypeDescriptor immutable ()
	{
		return immutable;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_InstanceCount (final @NotNull AvailObject object)
	{
		return IntegerDescriptor.zero();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Instances (final @NotNull AvailObject object)
	{
		return SetDescriptor.empty();
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
	@Override @AvailMethod
	AvailObject o_ComputeSuperkind (final @NotNull AvailObject object)
	{
		return object;
	}

	@Override @AvailMethod
	AvailObject o_FieldTypeMap (final @NotNull AvailObject object)
	{
		// TODO: [MvG] It's unclear what to return here.  Maybe raise an
		// unchecked exception.  Or if we ever implement more precise map types
		// containing key type -> value type pairs we might be able to change
		// the object type interface to use one of those instead of a map.
		return null;
	}

	@Override @AvailMethod
	int o_Hash (final @NotNull AvailObject object)
	{
		return 0x4a22a80a;
	}

	@Override @AvailMethod
	boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsSetType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_AcceptsArgTypesFromFunctionType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject functionType)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final List<AvailObject> argValues)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final @NotNull AvailObject argTypes)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final @NotNull AvailObject arguments)
	{
		return true;
	}

	@Override @AvailMethod
	AvailObject o_DeclaredExceptions (final @NotNull AvailObject object)
	{
		return SetDescriptor.empty();
	}

	@Override @AvailMethod
	AvailObject o_FunctionType (final @NotNull AvailObject object)
	{
		return object;
	}

	@Override @AvailMethod
	AvailObject o_ContentType (final @NotNull AvailObject object)
	{
		return object;
	}

	@Override @AvailMethod
	boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_IsInstanceOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
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
		if (aType.equals(TOP.o()) || aType.equals(ANY.o()))
		{
			return true;
		}
		// Bottom is an instance of every meta (everything that inherits
		// from TYPE).
		return aType.isSubtypeOf(InstanceMetaDescriptor.topMeta());
	}

	@Override @AvailMethod
	boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		assert !aType.equals(bottom);
		return aType.equals(TOP.o())
			|| aType.equals(ANY.o())
			|| aType.isSubtypeOf(InstanceMetaDescriptor.topMeta());
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Name (final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Parent (final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ReturnType (final @NotNull AvailObject object)
	{
		return object;
	}


	/**
	 * Bottom is an empty {@linkplain AbstractEnumerationTypeDescriptor
	 * enumeration}, so the answer is {@code false}.
	 */
	@Override @AvailMethod
	boolean o_EnumerationIncludesInstance (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialInstance)
	{
		return false;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_ReadType (
		final @NotNull AvailObject object)
	{
		return TOP.o();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_WriteType (
		final @NotNull AvailObject object)
	{
		return bottom;
	}

	@Override
	boolean o_IsPojoType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	boolean o_IsPojoFusedType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	boolean o_IsPojoArrayType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	boolean o_IsPojoSelfType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	@NotNull AvailObject o_ExpressionType (
		final @NotNull AvailObject object)
	{
		return object;
	}

	@Override
	boolean o_IsLiteralTokenType (
		final @NotNull AvailObject object)
	{
		return true;
	}
}
