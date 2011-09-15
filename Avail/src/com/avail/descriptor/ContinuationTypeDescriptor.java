/**
 * descriptor/ContinuationTypeDescriptor.java
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
import com.avail.interpreter.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;

/**
 * Continuation types are the types of {@linkplain ContinuationDescriptor
 * continuations}.  They contain information about the {@linkplain
 * ClosureTypeDescriptor types of closure} that can appear on the top stack
 * frame for a continuation of this type.
 *
 * <p>
 * Continuations can be {@linkplain
 * Primitive#prim56_RestartContinuationWithArguments restarted}
 * with a new set of arguments, so continuation types are contravariant with
 * respect to their closure types' argument types.  Surprisingly, continuation
 * types are also contravariant with respect to their closure types' return
 * types.  This is due to the capability to {@linkplain
 * Primitive#prim57_ExitContinuationWithResult exit} a continuation
 * with a specific value.
 * </p>
 *
 * <p>
 * TODO: Continuation types should be parameterizable with generalized closure
 * types.  This would allow prim58 (restart with the same arguments) to be
 * performed even if the specific argument types were not known, but prim56
 * (restart with new arguments) would be forbidden.  Prim57 (Exit with value)
 * would be unaffected.  Make sure to update type computations and type
 * compatibility tests appropriately to accommodate the contained generalized
 * closure types.
 * </p>
 *
 * <p>
 * TODO: If/when closure types support checked exceptions we won't need to
 * mention them in continuation types, since invoking a continuation in any way
 * (restart, exit, resume) causes exception obligations/permissions to be
 * instantly voided.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class ContinuationTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The type of closure that this {@linkplain ContinuationTypeDescriptor
		 * continuation type} supports.  Continuation types are contravariant
		 * with respect to the closure type's argument types, and, surprisingly,
		 * they are also contravariant with respect to the closure type's return
		 * type.
		 */
		CLOSURE_TYPE
	}

	@Override
	public @NotNull AvailObject o_ClosureType (
		final @NotNull AvailObject object)
	{
		return object.objectSlot(ObjectSlots.CLOSURE_TYPE);
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder aStream,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		aStream.append('$');
		object.closureType().printOnAvoidingIndent(
			aStream,
			recursionList,
			(indent + 1));
	}

	@Override
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsContinuationType(object);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Continuation types compare for equality by comparing their closureTypes.
	 * </p>
	 */
	@Override
	public boolean o_EqualsContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
	{
		if (object.sameAddressAs(aType))
		{
			return true;
		}
		return aType.closureType().equals(object.closureType());
	}

	@Override
	public int o_Hash (
		final @NotNull AvailObject object)
	{
		return object.closureType().hash() * 11 ^ 0x3E20409;
	}

	@Override
	public @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		//  Answer the object's type.

		return TYPE.o();
	}

	@Override
	public boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aType)
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
	 * contained closureType's arguments, and also contravariant with respect to
	 * the contained closureType's result type.
	 * </p>
	 */
	@Override
	public boolean o_IsSupertypeOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		final AvailObject subClosureType = aContinuationType.closureType();
		final AvailObject superClosureType = object.closureType();
		return
		superClosureType.returnType().isSubtypeOf(
			subClosureType.returnType())
			&& superClosureType.argsTupleType().isSubtypeOf(
				subClosureType.argsTupleType());
	}

	@Override
	public @NotNull AvailObject o_TypeIntersection (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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

	@Override
	public @NotNull AvailObject o_TypeIntersectionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		final AvailObject closType1 = object.closureType();
		final AvailObject closType2 = aContinuationType.closureType();
		if (closType1.equals(closType2))
		{
			return object;
		}
		if (closType1.numArgs() != closType2.numArgs())
		{
			return BottomTypeDescriptor.bottom();
		}
		final AvailObject intersection = ClosureTypeDescriptor.create(
			closType1.argsTupleType().typeUnion(closType2.argsTupleType()),
			closType1.returnType().typeUnion(closType2.returnType()));
		return forClosureType(intersection);
	}

	@Override
	public @NotNull AvailObject o_TypeUnion (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
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

	@Override
	public @NotNull AvailObject o_TypeUnionOfContinuationType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aContinuationType)
	{
		final AvailObject closType1 = object.closureType();
		final AvailObject closType2 = aContinuationType.closureType();
		if (closType1.equals(closType2))
		{
			// Optimization only
			return object;
		}
		final AvailObject union = ClosureTypeDescriptor.create(
			closType1.argsTupleType().typeIntersection(
				closType2.argsTupleType()),
				closType1.returnType().typeIntersection(closType2.returnType()));
		return forClosureType(union);
	}

	/**
	 * Create a {@linkplain ContinuationTypeDescriptor continuation type} based
	 * on the passed {@linkplain ClosureTypeDescriptor closure type}.  Ignore
	 * the closure type's exception set.
	 *
	 * @param closureType
	 *            A {@linkplain ClosureTypeDescriptor closure type} on which to
	 *            base the new {@linkplain ContinuationTypeDescriptor
	 *            continuation type}.
	 * @return
	 *            A new {@linkplain ContinuationTypeDescriptor}.
	 */
	public static @NotNull AvailObject forClosureType (
		final @NotNull AvailObject closureType)
	{
		closureType.makeImmutable();
		final AvailObject result = mutable().create();
		result.objectSlotPut(ObjectSlots.CLOSURE_TYPE, closureType);
		result.makeImmutable();
		return result;
	}

	/**
	 * @return
	 */
	public static AvailObject mostGeneralType ()
	{
		return MostGeneralType;
	}

	/**
	 * The most general continuation type.  Since continuation types are
	 * contravariant by argument types and contravariant by return type, the
	 * most general type is the one taking bottom as the arguments list
	 * (i.e., not specific enough to be able to call it), and having the return
	 * type bottom.
	 */
	private static AvailObject MostGeneralType;

	/**
	 * The metatype for all continuation types.  In particular, it's just the
	 * {@linkplain InstanceTypeDescriptor instance type} for the {@linkplain
	 * #MostGeneralType most general continuation type}.
	 */
	private static AvailObject Meta;

	/**
	 * Answer the metatype for all continuation types.
	 *
	 * @return The statically referenced metatype.
	 */
	public static AvailObject meta ()
	{
		return Meta;
	}

	public static void clearWellKnownObjects ()
	{
		MostGeneralType = null;
		Meta = null;
	}

	public static void createWellKnownObjects ()
	{
		MostGeneralType = forClosureType(
			ClosureTypeDescriptor.forReturnType(
				BottomTypeDescriptor.bottom()));
		MostGeneralType.makeImmutable();
		Meta = InstanceTypeDescriptor.withInstance(MostGeneralType);
		Meta.makeImmutable();
	}

	/**
	 * Construct a new {@link ContinuationTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ContinuationTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ContinuationTypeDescriptor}.
	 */
	private final static ContinuationTypeDescriptor mutable =
		new ContinuationTypeDescriptor(true);

	/**
	 * Answer the mutable {@link ContinuationTypeDescriptor}.
	 *
	 * @return The mutable {@link ContinuationTypeDescriptor}.
	 */
	public static ContinuationTypeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ContinuationTypeDescriptor}.
	 */
	private final static ContinuationTypeDescriptor immutable =
		new ContinuationTypeDescriptor(false);

	/**
	 * Answer the immutable {@link ContinuationTypeDescriptor}.
	 *
	 * @return The immutable {@link ContinuationTypeDescriptor}.
	 */
	public static ContinuationTypeDescriptor immutable ()
	{
		return immutable;
	}
}
