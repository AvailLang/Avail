/**
 * descriptor/BottomTypeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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
import com.avail.annotations.NotNull;
import static com.avail.descriptor.TypeDescriptor.Types.*;

public class BottomTypeDescriptor
extends AbstractUnionTypeDescriptor
{
	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("bottom");
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
		final boolean equal = another.equalsUnionTypeWithSet(
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
	 * Determine if the object is a {@linkplain AbstractUnionTypeDescriptor
	 * union type} over the given set of instances.  Since the object is the
	 * {@linkplain BottomTypeDescriptor bottom type}, just check if the set of
	 * instances is empty.
	 * </p>
	 */
	@Override
	public boolean o_EqualsUnionTypeWithSet (
		final @NotNull AvailObject object,
		final AvailObject aSet)
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
	final @NotNull AvailObject computeIntersectionWith (
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
	@NotNull AvailObject computeUnionWith (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		// Easy -- it's always the other type.
		assert another.isType();
		return another;
	}

	@Override
	public @NotNull AvailObject o_LowerBound (
		final @NotNull AvailObject object)
	{
		//  Pretend we go from +INF to -INF exclusive.  That should be a nice empty range.

		return InfinityDescriptor.positiveInfinity();
	}

	@Override
	public boolean o_LowerInclusive (
		final @NotNull AvailObject object)
	{
		//  Pretend we go from +INF to -INF exclusive.  That should be a nice empty range.

		return false;
	}

	@Override
	public @NotNull AvailObject o_UpperBound (
		final @NotNull AvailObject object)
	{
		//  Pretend we go from +INF to -INF exclusive.  That should be a nice empty range.

		return InfinityDescriptor.negativeInfinity();
	}

	@Override
	public boolean o_UpperInclusive (
		final @NotNull AvailObject object)
	{
		//  Pretend we go from +INF to -INF exclusive.  That should be a nice empty range.

		return false;
	}

	@Override
	public @NotNull AvailObject o_KeyType (
		final @NotNull AvailObject object)
	{
		//  Answer what type my keys are.  Since I'm the degenerate mapType called
		//  bottom, answer bottom.

		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_SizeRange (
		final @NotNull AvailObject object)
	{
		//  Answer what sizes my instances can be.  Since I'm the degenerate mapType called
		//  bottom, answer the degenerate integerType called bottom.

		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_ValueType (
		final @NotNull AvailObject object)
	{
		//  Answer what type my values are.  Since I'm the degenerate mapType called
		//  bottom, answer bottom.

		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeAtIndex (
		final @NotNull AvailObject object,
		final int index)
	{
		//  Answer what type the given index would have in an object instance of me.  Answer
		//  bottom if the index is out of bounds, which is always because I'm the degenerate
		//  tupleType called bottom.

		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_UnionOfTypesAtThrough (
		final @NotNull AvailObject object,
		final int startIndex,
		final int endIndex)
	{
		//  Answer the union of the types the given indices would have in an object instance of me.
		//  Answer bottom if the index is out of bounds, which is always because I'm the degenerate
		//  tupleType called bottom.

		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_DefaultType (
		final @NotNull AvailObject object)
	{
		//  To support the tupleType protocol, I must answer bottom now.

		return BottomTypeDescriptor.bottom();
	}

	@Override
	public @NotNull AvailObject o_TypeTuple (
		final @NotNull AvailObject object)
	{
		//  To support the tupleType protocol, I must answer <> now.

		return TupleDescriptor.empty();
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		// Check if object (bottom) is a subtype of aType (should also be a
		// type).  Always true, but make sure aType is really a type.
		return aType.isSupertypeOfBottom();
	}

	@Override
	public boolean o_IsIntegerRangeType (
		final @NotNull AvailObject object)
	{
		//  Because bottom is a subtype of all other types, it is even considered
		//  an integer range type - in particular, the degenerate integer type (INF..-INF).

		return true;
	}

	@Override
	public boolean o_IsMapType (
		final @NotNull AvailObject object)
	{
		//  Because bottom is a subtype of all other types, it is even considered
		//  a map type - in particular, the degenerate map type.  Its sizeRange is
		//  bottom, its keyType is bottom, and its valueType is bottom.

		return true;
	}

	@Override
	public boolean o_IsTupleType (
		final @NotNull AvailObject object)
	{
		//  Because bottom is a subtype of all other types, it is even considered
		//  a tuple type - in particular, the degenerate tuple type.  Its sizeRange is
		//  bottom, its typeTuple is <>, and its defaultType is bottom.

		return true;
	}

	@Override
	public AvailObject o_ArgsTupleType (
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
	private final static BottomTypeDescriptor mutable =
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
	private final static BottomTypeDescriptor immutable =
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

	@Override
	public AvailObject o_Instances (final AvailObject object)
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
	@Override
	public AvailObject o_ComputeSuperkind (final AvailObject object)
	{
		return object;
	}

	@Override
	public AvailObject o_FieldTypeMap (final AvailObject object)
	{
		// TODO It's unclear what to return here.  Maybe raise an unchecked
		// exception.
		return null;
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		return 0x4a22a80a;
	}

	@Override
	public boolean o_HasObjectInstance (
		final @NotNull AvailObject object,
		final AvailObject potentialInstance)
	{
		return false;
	}

	@Override
	public boolean o_IsSetType (final AvailObject object)
	{
		return true;
	}

	@Override
	public boolean o_AcceptsArgTypesFromFunctionType (
		final @NotNull AvailObject object,
		final AvailObject functionType)
	{
		return true;
	}

	@Override
	public boolean o_AcceptsArgumentTypesFromContinuation (
		final @NotNull AvailObject object,
		final AvailObject continuation,
		final int stackp,
		final int numArgs)
	{
		return true;
	}

	@Override
	public boolean o_AcceptsListOfArgTypes (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		return true;
	}

	@Override
	public boolean o_AcceptsListOfArgValues (
		final @NotNull AvailObject object,
		final List<AvailObject> argValues)
	{
		return true;
	}

	@Override
	public boolean o_AcceptsTupleOfArgTypes (
		final @NotNull AvailObject object,
		final AvailObject argTypes)
	{
		return true;
	}

	@Override
	public boolean o_AcceptsTupleOfArguments (
		final @NotNull AvailObject object,
		final AvailObject arguments)
	{
		return true;
	}

	@Override
	public AvailObject o_CheckedExceptions (final AvailObject object)
	{
		return SetDescriptor.empty();
	}

	@Override
	public AvailObject o_FunctionType (final AvailObject object)
	{
		return object;
	}

	@Override
	public AvailObject o_ContentType (final AvailObject object)
	{
		return object;
	}

	@Override
	public boolean o_CouldEverBeInvokedWith (
		final @NotNull AvailObject object,
		final List<AvailObject> argTypes)
	{
		return true;
	}

	@Override
	public boolean o_IsInstanceOf (
		final @NotNull AvailObject object,
		final AvailObject aType)
	{
		// Bottom is an instance of every metatype except for itself.
		assert aType.isType();
		if (object.equals(aType))
		{
			// Bottom is not an instance of itself.
			return false;
		}
		if (aType.isAbstractUnionType())
		{
			return aType.abstractUnionTypeIncludesInstance(object);
		}
		// Bottom is an instance of top and any.
		if (aType.equals(TOP.o()) || aType.equals(ANY.o()))
		{
			return true;
		}
		// Bottom is an instance of every meta (everything that inherits
		// from TYPE).
		return aType.isSubtypeOf(TYPE.o());
	}

	@Override
	public boolean o_IsInstanceOfKind (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		assert !aType.equals(bottom);
		return aType.equals(TOP.o())
			|| aType.equals(ANY.o())
			|| aType.isSubtypeOf(TYPE.o());
	}

	@Override
	public AvailObject o_Name (final @NotNull AvailObject object)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AvailObject o_Parent (final @NotNull AvailObject object)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AvailObject o_ReturnType (final AvailObject object)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType)
	{
		return object;
	}

	@Override
	public AvailObject o_TypeIntersectionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		return object;
	}

	@Override
	public AvailObject o_TypeIntersectionOfParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType)
	{
		return object;
	}

	@Override
	public AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final AvailObject aContinuationType)
	{
		return aContinuationType;
	}

	@Override
	public AvailObject o_TypeUnionOfCompiledCodeType (
		final @NotNull AvailObject object,
		final AvailObject aCompiledCodeType)
	{
		return aCompiledCodeType;
	}

	@Override
	public AvailObject o_TypeUnionOfParseNodeType (
		final @NotNull AvailObject object,
		final AvailObject aParseNodeType)
	{
		return aParseNodeType;
	}

	/**
	 * Bottom is an empty union type, so the answer is no.
	 */
	@Override
	public boolean o_AbstractUnionTypeIncludesInstance (
		final @NotNull AvailObject object,
		final AvailObject potentialInstance)
	{
		return false;
	}

	@Override
	public @NotNull AvailObject o_InnerKind (final @NotNull AvailObject object)
	{
		return object;
	}

	@Override
	public boolean o_IsUnionMeta (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override
	public @NotNull AvailObject o_ReadType (
		final @NotNull AvailObject object)
	{
		return TOP.o();
	}

	@Override
	public @NotNull AvailObject o_WriteType (
		final @NotNull AvailObject object)
	{
		return bottom;
	}
}
