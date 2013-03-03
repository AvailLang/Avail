/**
 * ContinuationTypeDescriptor.java
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

import java.util.List;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.interpreter.primitive.*;
import com.avail.serialization.SerializerOperation;

/**
 * Continuation types are the types of {@linkplain ContinuationDescriptor
 * continuations}.  They contain information about the {@linkplain
 * FunctionTypeDescriptor types of function} that can appear on the top stack
 * frame for a continuation of this type.
 *
 * <p>
 * Continuations can be {@linkplain
 * P_056_RestartContinuationWithArguments restarted with a new tuple of
 * arguments}, so continuation types are contravariant with respect to their
 * function types' argument types.  Surprisingly, continuation types are also
 * contravariant with respect to their function types' return types.  This is
 * due to the capability to {@linkplain P_057_ExitContinuationWithResult exit} a
 * continuation with a specific value.
 * </p>
 *
 * <p>
 * TODO: [MvG] If/when function types support checked exceptions we won't need
 * to mention them in continuation types, since invoking a continuation in any
 * way (restart, exit, resume) causes exception obligations/permissions to be
 * instantly voided.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ContinuationTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The type of function that this {@linkplain ContinuationTypeDescriptor
		 * continuation type} supports.  Continuation types are contravariant
		 * with respect to the function type's argument types, and,
		 * surprisingly, they are also contravariant with respect to the
		 * function type's return type.
		 */
		FUNCTION_TYPE
	}

	@Override @AvailMethod
	A_Type o_FunctionType (final AvailObject object)
	{
		return object.slot(ObjectSlots.FUNCTION_TYPE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append('$');
		object.functionType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsContinuationType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Continuation types compare for equality by comparing their function
	 * types.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_EqualsContinuationType (
		final AvailObject object,
		final A_Type aType)
	{
		if (object.sameAddressAs(aType))
		{
			return true;
		}
		return aType.functionType().equals(object.functionType());
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.functionType().hash() * 11 ^ 0x3E20409;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfContinuationType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Since the only things that can be done with continuations are to restart
	 * them or to exit them, continuation subtypes must accept any values that
	 * could be passed as arguments or as the return value to the supertype.
	 * Therefore, continuation types must be contravariant with respect to the
	 * contained functionType's arguments, and also contravariant with respect
	 * to the contained functionType's result type.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		final A_Type subFunctionType = aContinuationType.functionType();
		final A_BasicObject superFunctionType = object.functionType();
		return
		superFunctionType.returnType().isSubtypeOf(
			subFunctionType.returnType())
			&& superFunctionType.argsTupleType().isSubtypeOf(
				subFunctionType.argsTupleType());
	}

	@Override @AvailMethod
	A_Type o_TypeIntersection (
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
		return another.typeIntersectionOfContinuationType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		final A_Type functionType1 = object.functionType();
		final A_Type functionType2 = aContinuationType.functionType();
		if (functionType1.equals(functionType2))
		{
			return object;
		}
		final A_Type argsTupleType =
			functionType1.argsTupleType().typeIntersection(
				functionType2.argsTupleType());
		final A_Type returnType = functionType1.returnType().typeIntersection(
			functionType2.returnType());
		final A_Type intersection =
			FunctionTypeDescriptor.createWithArgumentTupleType(
				argsTupleType,
				returnType,
				SetDescriptor.empty());
		return forFunctionType(intersection);
	}

	@Override @AvailMethod
	A_Type o_TypeUnion (
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
		return another.typeUnionOfContinuationType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		final A_Type functionType1 = object.functionType();
		final A_Type functionType2 = aContinuationType.functionType();
		if (functionType1.equals(functionType2))
		{
			// Optimization only
			return object;
		}
		final A_Type union = FunctionTypeDescriptor.createWithArgumentTupleType(
			functionType1.argsTupleType().typeIntersection(
				functionType2.argsTupleType()),
			functionType1.returnType().typeIntersection(
				functionType2.returnType()),
			SetDescriptor.empty());
		return forFunctionType(union);
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.CONTINUATION_TYPE;
	}

	/**
	 * Create a {@linkplain ContinuationTypeDescriptor continuation type} based
	 * on the passed {@linkplain FunctionTypeDescriptor function type}. Ignore
	 * the function type's exception set.
	 *
	 * @param functionType
	 *        A {@linkplain FunctionTypeDescriptor function type} on which to
	 *        base the new {@linkplain ContinuationTypeDescriptor continuation
	 *        type}.
	 * @return A new {@linkplain ContinuationTypeDescriptor}.
	 */
	public static A_Type forFunctionType (final A_Type functionType)
	{
		final AvailObject result = mutable.create();
		result.setSlot(ObjectSlots.FUNCTION_TYPE, functionType.makeImmutable());
		result.makeImmutable();
		return result;
	}

	/**
	 * The most general continuation type.  Since continuation types are
	 * contravariant by argument types and contravariant by return type, the
	 * most general type is the one taking bottom as the arguments list
	 * (i.e., not specific enough to be able to call it), and having the return
	 * type bottom.
	 */
	private static @Nullable A_Type mostGeneralType;

	/**
	 * Answer the most general {@linkplain ContinuationTypeDescriptor
	 * continuation type}.
	 *
	 * @return A {@linkplain ContinuationTypeDescriptor continuation type} which
	 *         has no supertypes that are themselves continuation types.
	 */
	public static A_Type mostGeneralType ()
	{
		final A_Type type = mostGeneralType;
		assert type != null;
		return type;
	}

	/**
	 * The metatype for all continuation types.  In particular, it's just the
	 * {@linkplain InstanceTypeDescriptor instance type} for the {@linkplain
	 * #mostGeneralType most general continuation type}.
	 */
	private static @Nullable A_Type meta;

	/**
	 * Answer the metatype for all continuation types.
	 *
	 * @return The statically referenced metatype.
	 */
	public static A_Type meta ()
	{
		final A_Type type = meta;
		assert type != null;
		return type;
	}

	/**
	 * Create any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void createWellKnownObjects ()
	{
		mostGeneralType = forFunctionType(
			FunctionTypeDescriptor.forReturnType(
				BottomTypeDescriptor.bottom())).makeShared();
		meta = InstanceMetaDescriptor.on(mostGeneralType()).makeShared();
	}

	/**
	 * Destroy or reset any instances statically well-known to the {@linkplain
	 * AvailRuntime Avail runtime system}.
	 */
	public static void clearWellKnownObjects ()
	{
		mostGeneralType = null;
		meta = null;
	}

	/**
	 * Construct a new {@link ContinuationTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ContinuationTypeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link ContinuationTypeDescriptor}. */
	private static final ContinuationTypeDescriptor mutable =
		new ContinuationTypeDescriptor(Mutability.MUTABLE);

	@Override
	ContinuationTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ContinuationTypeDescriptor}. */
	private static final ContinuationTypeDescriptor immutable =
		new ContinuationTypeDescriptor(Mutability.IMMUTABLE);

	@Override
	ContinuationTypeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link ContinuationTypeDescriptor}. */
	private static final ContinuationTypeDescriptor shared =
		new ContinuationTypeDescriptor(Mutability.SHARED);

	@Override
	ContinuationTypeDescriptor shared ()
	{
		return shared;
	}
}
