/*
 * FiberTypeDescriptor.java
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

import com.avail.annotations.AvailMethod;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.FiberDescriptor;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.Mutability;
import com.avail.descriptor.ObjectSlotsEnum;
import com.avail.descriptor.functions.ContinuationDescriptor;
import com.avail.descriptor.functions.FunctionDescriptor;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;

import static com.avail.descriptor.types.FiberTypeDescriptor.ObjectSlots.RESULT_TYPE;
import static com.avail.descriptor.types.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.types.TypeDescriptor.Types.TOP;

/**
 * {@code FiberTypeDescriptor} represents the type of a {@linkplain
 * FiberDescriptor fiber}. A fiber is typed by the return type of the base
 * {@linkplain FunctionDescriptor function} used to create it. Fiber types
 * provide first-order type safety, but are not perfectly sincerely. Switching
 * a fiber's {@linkplain ContinuationDescriptor continuation} to one whose base
 * function's return type is incompatible with the fiber's type is a runtime
 * error, and will be detected and reported when such a ({@linkplain
 * ExecutionState#TERMINATED terminated}) fiber's result is
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
	protected A_Type o_ResultType (final AvailObject object)
	{
		return object.slot(RESULT_TYPE);
	}

	@Override @AvailMethod
	protected boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsFiberType(object);
	}

	@Override @AvailMethod
	protected boolean o_EqualsFiberType (final AvailObject object, final A_Type aType)
	{
		return object.sameAddressAs(aType)
			|| aType.resultType().equals(object.slot(RESULT_TYPE));
	}

	@Override @AvailMethod
	protected int o_Hash (final AvailObject object)
	{
		return object.slot(RESULT_TYPE).hash() * 1307 ^ 0xBC084A71;
	}

	@Override @AvailMethod
	protected boolean o_IsSubtypeOf (final AvailObject object, final A_Type aType)
	{
		return aType.isSupertypeOfFiberType(object);
	}

	@Override @AvailMethod
	protected boolean o_IsSupertypeOfFiberType (
		final AvailObject object,
		final A_Type aType)
	{
		return aType.resultType().isSubtypeOf(object.slot(RESULT_TYPE));
	}

	@Override @AvailMethod
	protected A_Type o_TypeIntersection (final AvailObject object, final A_Type another)
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
	protected A_Type o_TypeIntersectionOfFiberType (
		final AvailObject object,
		final A_Type aType)
	{
		return fiberType(
			object.slot(RESULT_TYPE).typeIntersection(aType.resultType()));
	}

	@Override @AvailMethod
	protected A_Type o_TypeUnion (final AvailObject object, final A_Type another)
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
	protected A_Type o_TypeUnionOfFiberType (
		final AvailObject object,
		final A_Type aType)
	{
		return fiberType(
			object.slot(RESULT_TYPE).typeUnion(aType.resultType()));
	}

	@Override @AvailMethod
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.FIBER_TYPE;
	}

	@Override @AvailMethod
	protected AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// Make the object shared.
			return object.makeShared();
		}
		return object;
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("fiber type");
		writer.write("result type");
		object.slot(RESULT_TYPE).writeTo(writer);
		writer.endObject();
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("fiber type");
		writer.write("result type");
		object.slot(RESULT_TYPE).writeSummaryTo(writer);
		writer.endObject();
	}

	@Override
	@ThreadSafe
	protected void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append("fiber→");
		object.slot(RESULT_TYPE).printOnAvoidingIndent(
			builder, recursionMap, indent);
	}

	/**
	 * Construct a new {@link FiberTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public FiberTypeDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.FIBER_TYPE_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link FiberTypeDescriptor}. */
	static final FiberTypeDescriptor mutable =
		new FiberTypeDescriptor(Mutability.MUTABLE);

	@Override
	public FiberTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link FiberTypeDescriptor}. */
	private static final FiberTypeDescriptor shared =
		new FiberTypeDescriptor(Mutability.SHARED);

	@Override
	public FiberTypeDescriptor immutable ()
	{
		// There is no immutable variation.
		return shared;
	}

	@Override
	public FiberTypeDescriptor shared ()
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
	public static AvailObject fiberType (final A_Type resultType)
	{
		final AvailObject result = mutable.create();
		result.setSlot(RESULT_TYPE, resultType.makeImmutable());
		result.makeShared();
		return result;
	}

	/**
	 * The most general {@linkplain FiberTypeDescriptor fiber type}.
	 */
	private static final A_Type mostGeneralFiberType =
		fiberType(TOP.o()).makeShared();

	/**
	 * Answer the most general {@linkplain FiberDescriptor fiber type}.
	 *
	 * @return The most general fiber type.
	 */
	public static A_Type mostGeneralFiberType ()
	{
		return mostGeneralFiberType;
	}

	/**
	 * The metatype for all {@linkplain FiberTypeDescriptor fiber types}.
	 */
	private static final A_Type meta =
		instanceMeta(mostGeneralFiberType).makeShared();

	/**
	 * Answer the metatype for all {@linkplain FiberTypeDescriptor fiber types}.
	 *
	 * @return The metatype for all fiber types.
	 */
	public static A_Type fiberMeta ()
	{
		return meta;
	}
}
