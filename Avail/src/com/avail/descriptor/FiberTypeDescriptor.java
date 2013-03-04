/**
 * FiberTypeDescriptor.java
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

import static com.avail.descriptor.FiberTypeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import java.util.List;
import com.avail.annotations.AvailMethod;
import com.avail.annotations.ThreadSafe;
import com.avail.serialization.SerializerOperation;

/**
 * {@code FiberTypeDescriptor} represents the type of a {@linkplain
 * FiberDescriptor fiber}. A fiber is typed by the return type of the base
 * {@linkplain FunctionDescriptor function} used to create it. Fiber types
 * provide first-order type safety, but are not perfectly sincerely. Switching
 * a fiber's {@linkplain ContinuationDescriptor continuation} to one whose base
 * function's return type is incompatible with the fiber's type is a runtime
 * error, and will be detected and reported when such a ({@linkplain
 * FiberDescriptor.ExecutionState#TERMINATED terminated}) fiber's result is
 * requested.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class FiberTypeDescriptor
extends TypeDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain FiberTypeDescriptor fiber type}'s result type.
		 */
		RESULT_TYPE
	}

	@Override
	A_Type o_ResultType (final AvailObject object)
	{
		return object.slot(RESULT_TYPE);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsFiberType(object);
	}

	@Override @AvailMethod
	boolean o_EqualsFiberType (final AvailObject object, final A_Type aType)
	{
		if (object.sameAddressAs(aType))
		{
			return true;
		}
		return aType.slot(RESULT_TYPE).equals(object.slot(RESULT_TYPE));
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(RESULT_TYPE).hash() * 1307 ^ 0xBC084A71;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfFiberType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfFiberType (
		final AvailObject object,
		final A_Type aType)
	{
		return aType.slot(RESULT_TYPE).isSubtypeOf(object.slot(RESULT_TYPE));
	}

	@Override @AvailMethod
	A_Type o_TypeIntersection (final AvailObject object, final A_Type another)
	{
		if (object.isSubtypeOf(another))
		{
			return object;
		}
		if (another.isSubtypeOf(object))
		{
			return another;
		}
		return another.typeIntersectionOfFiberType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfFiberType (
		final AvailObject object,
		final A_Type aType)
	{
		return FiberTypeDescriptor.forResultType(
			object.slot(RESULT_TYPE).typeIntersection(aType.slot(RESULT_TYPE)));
	}

	@Override @AvailMethod
	A_Type o_TypeUnion (final AvailObject object, final A_Type another)
	{
		if (object.isSubtypeOf(another))
		{
			return another;
		}
		if (another.isSubtypeOf(object))
		{
			return object;
		}
		return another.typeUnionOfFiberType(object);
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfFiberType (
		final AvailObject object,
		final A_Type aType)
	{
		return FiberTypeDescriptor.forResultType(
			object.slot(RESULT_TYPE).typeUnion(aType.slot(RESULT_TYPE)));
	}

	@Override @AvailMethod
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.FIBER_TYPE;
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Make the object shared.
			return object.makeShared();
		}
		return object;
	}

	@Override
	@ThreadSafe
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		builder.append("fiber→");
		object.slot(RESULT_TYPE).printOnAvoidingIndent(
			builder, recursionList, indent);
	}

	/**
	 * Construct a new {@link FiberTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public FiberTypeDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link FiberTypeDescriptor}. */
	static final FiberTypeDescriptor mutable =
		new FiberTypeDescriptor(Mutability.MUTABLE);

	@Override
	FiberTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link FiberTypeDescriptor}. */
	private static final FiberTypeDescriptor shared =
		new FiberTypeDescriptor(Mutability.SHARED);

	@Override
	FiberTypeDescriptor immutable ()
	{
		// There is no immutable variation.
		return shared;
	}

	@Override
	FiberTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Create a {@linkplain FiberTypeDescriptor fiber type} with the specified
	 * {@linkplain AvailObject#resultType() result type}.
	 *
	 * @param resultType
	 *        The result type.
	 * @return A new fiber type.
	 */
	public static AvailObject forResultType (final A_Type resultType)
	{
		final AvailObject result = mutable.create();
		result.setSlot(RESULT_TYPE, resultType.makeImmutable());
		result.makeShared();
		return result;
	}

	/**
	 * The most general {@linkplain FiberTypeDescriptor fiber type}.
	 */
	private static final A_Type mostGeneralType =
		forResultType(TOP.o()).makeShared();

	/**
	 * Answer the most general {@linkplain FiberDescriptor fiber type}.
	 *
	 * @return The most general fiber type.
	 */
	public static final A_Type mostGeneralType ()
	{
		return mostGeneralType;
	}

	/**
	 * The metatype for all {@linkplain FiberTypeDescriptor fiber types}.
	 */
	private static final A_Type meta =
		InstanceMetaDescriptor.on(mostGeneralType).makeShared();

	/**
	 * Answer the metatype for all {@linkplain FiberTypeDescriptor fiber types}.
	 *
	 * @return The metatype for all fiber types.
	 */
	public static final A_Type meta ()
	{
		return meta;
	}
}
