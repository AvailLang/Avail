/*
 * ContinuationTypeDescriptor.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.descriptor.objects.A_BasicObject;
import com.avail.interpreter.primitive.controlflow.P_ExitContinuationWithResultIf;
import com.avail.interpreter.primitive.controlflow.P_RestartContinuationWithArguments;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;

import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.ContinuationTypeDescriptor.ObjectSlots.FUNCTION_TYPE;
import static com.avail.descriptor.FunctionTypeDescriptor.functionTypeFromArgumentTupleType;
import static com.avail.descriptor.FunctionTypeDescriptor.functionTypeReturning;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.SetDescriptor.emptySet;

/**
 * Continuation types are the types of {@linkplain ContinuationDescriptor
 * continuations}.  They contain information about the {@linkplain
 * FunctionTypeDescriptor types of function} that can appear on the top stack
 * frame for a continuation of this type.
 *
 * <p>
 * Continuations can be {@linkplain
 * P_RestartContinuationWithArguments restarted with a new tuple of
 * arguments}, so continuation types are contravariant with respect to their
 * function types' argument types.  Surprisingly, continuation types are also
 * contravariant with respect to their function types' return types.  This is
 * due to the capability to {@linkplain P_ExitContinuationWithResultIf exit} a
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
	protected A_Type o_FunctionType (final AvailObject object)
	{
		return object.slot(FUNCTION_TYPE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder aStream,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		aStream.append('$');
		object.functionType().printOnAvoidingIndent(
			aStream,
			recursionMap,
			(indent + 1));
	}

	@Override @AvailMethod
	protected boolean o_Equals (final AvailObject object, final A_BasicObject another)
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
	protected boolean o_EqualsContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		if (object.sameAddressAs(aContinuationType))
		{
			return true;
		}
		return aContinuationType.functionType().equals(object.functionType());
	}

	@Override @AvailMethod
	protected int o_Hash (final AvailObject object)
	{
		return object.functionType().hash() * 11 ^ 0x3E20409;
	}

	@Override @AvailMethod
	protected boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
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
	protected boolean o_IsSupertypeOfContinuationType (
		final AvailObject object,
		final A_Type aContinuationType)
	{
		final A_Type subFunctionType = aContinuationType.functionType();
		final A_Type superFunctionType = object.functionType();
		return
		superFunctionType.returnType().isSubtypeOf(
			subFunctionType.returnType())
			&& superFunctionType.argsTupleType().isSubtypeOf(
				subFunctionType.argsTupleType());
	}

	@Override
	protected boolean o_IsVacuousType (final AvailObject object)
	{
		return object.slot(FUNCTION_TYPE).isVacuousType();
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersection (
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
	protected A_Type o_TypeIntersectionOfContinuationType (
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
		final A_Type intersection = functionTypeFromArgumentTupleType(
			argsTupleType, returnType, emptySet());
		return continuationTypeForFunctionType(intersection);
	}

	@Override @AvailMethod
	protected A_Type o_TypeUnion (
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
	protected A_Type o_TypeUnionOfContinuationType (
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
		final A_Type union = functionTypeFromArgumentTupleType(
			functionType1.argsTupleType().typeIntersection(
				functionType2.argsTupleType()),
			functionType1.returnType().typeIntersection(
				functionType2.returnType()),
			emptySet());
		return continuationTypeForFunctionType(union);
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.CONTINUATION_TYPE;
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("continuation type");
		writer.write("function type");
		object.slot(FUNCTION_TYPE).writeTo(writer);
		writer.endObject();
	}

	/**
	 * Create a continuation type based on the passed
	 * {@linkplain FunctionTypeDescriptor function type}. Ignore the function
	 * type's exception set.
	 *
	 * @param functionType
	 *        A {@linkplain FunctionTypeDescriptor function type} on which to
	 *        base the new continuation type.
	 * @return A new continuation type.
	 */
	public static A_Type continuationTypeForFunctionType (
		final A_Type functionType)
	{
		final AvailObject result = mutable.create();
		result.setSlot(FUNCTION_TYPE, functionType.makeImmutable());
		result.makeImmutable();
		return result;
	}

	/**
	 * Construct a new {@code ContinuationTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ContinuationTypeDescriptor (final Mutability mutability)
	{
		super(
			mutability, TypeTag.CONTINUATION_TYPE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link ContinuationTypeDescriptor}. */
	private static final ContinuationTypeDescriptor mutable =
		new ContinuationTypeDescriptor(Mutability.MUTABLE);

	@Override
	protected ContinuationTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ContinuationTypeDescriptor}. */
	private static final ContinuationTypeDescriptor immutable =
		new ContinuationTypeDescriptor(Mutability.IMMUTABLE);

	@Override
	protected ContinuationTypeDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link ContinuationTypeDescriptor}. */
	private static final ContinuationTypeDescriptor shared =
		new ContinuationTypeDescriptor(Mutability.SHARED);

	@Override
	protected ContinuationTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * The most general continuation type.  Since continuation types are
	 * contravariant by argument types and contravariant by return type, the
	 * most general type is the one taking bottom as the arguments list
	 * (i.e., not specific enough to be able to call it), and having the return
	 * type bottom.
	 */
	private static final A_Type mostGeneralType =
		continuationTypeForFunctionType(
			functionTypeReturning(bottom())).makeShared();

	/**
	 * Answer the most general continuation type}.
	 *
	 * @return A continuation type which has no supertypes that are themselves
	 *         continuation types.
	 */
	public static A_Type mostGeneralContinuationType ()
	{
		return mostGeneralType;
	}

	/**
	 * The metatype for all continuation types.  In particular, it's just the
	 * {@linkplain InstanceTypeDescriptor instance type} for the
	 * {@link #mostGeneralContinuationType()}.
	 */
	private static final A_Type meta =
		instanceMeta(mostGeneralType).makeShared();

	/**
	 * Answer the metatype for all continuation types.
	 *
	 * @return The statically referenced metatype.
	 */
	public static A_Type continuationMeta ()
	{
		return meta;
	}
}
