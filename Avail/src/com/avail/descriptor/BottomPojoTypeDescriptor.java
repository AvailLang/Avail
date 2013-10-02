/**
 * BottomPojoTypeDescriptor.java
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
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * {@code BottomPojoTypeDescriptor} describes the type of Java {@code null},
 * which is implicitly an instance of every Java reference type. It therefore
 * describes the most specific Java reference type. Its only proper subtype is
 * Avail's own {@linkplain BottomTypeDescriptor bottom type}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class BottomPojoTypeDescriptor
extends PojoTypeDescriptor
{
	@Override @AvailMethod
	boolean o_EqualsPojoBottomType (final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_EqualsPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		return aPojoType.equalsPojoBottomType();
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return 0x496FFE01;
	}

	@Override @AvailMethod
	boolean o_IsAbstract (final AvailObject object)
	{
		// Pojo bottom has an instance: null.
		return false;
	}

	@Override
	boolean o_IsPojoArrayType (final AvailObject object)
	{
		// Pojo bottom is the most specific pojo type, so it is also a pojo
		// array type.
		return true;
	}

	@Override @AvailMethod
	boolean o_IsPojoFusedType (final AvailObject object)
	{
		// Pojo bottom is the intersection of any two unrelated pojo types, so
		// it is a pojo fusion type.
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return aPojoType.isSupertypeOfPojoBottomType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final A_BasicObject aPojoType)
	{
		return aPojoType.equalsPojoBottomType();
	}

	@Override
	AvailObject o_JavaAncestors (final AvailObject object)
	{
		return NilDescriptor.nil();
	}

	@Override @AvailMethod
	AvailObject o_JavaClass (final AvailObject object)
	{
		return RawPojoDescriptor.rawNullObject();
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		if (isMutable())
		{
			// There is no immutable descriptor.
			object.descriptor = shared;
		}
		return object;
	}

	@Override @AvailMethod
	AvailObject o_MakeShared (final AvailObject object)
	{
		if (!isShared())
		{
			object.descriptor = shared;
		}
		return object;
	}

	@Override @AvailMethod
	A_Type o_PojoSelfType (final AvailObject object)
	{
		// The pojo bottom type is its own self type.
		return object;
	}

	@Override
	Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return Object.class;
	}

	@Override @AvailMethod
	A_Type o_TypeIntersectionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return object;
	}

	@Override
	A_Type o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	A_Type o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	A_Type o_TypeUnionOfPojoType (
		final AvailObject object,
		final A_Type aPojoType)
	{
		return aPojoType;
	}

	@Override
	A_Type o_TypeUnionOfPojoFusedType (
		final AvailObject object,
		final A_Type aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	A_Type o_TypeUnionOfPojoUnfusedType (
		final AvailObject object,
		final A_Type anUnfusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	A_Map o_TypeVariables (final AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.BOTTOM_POJO_TYPE;
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<A_BasicObject> recursionList,
		final int indent)
	{
		builder.append("pojo ⊥");
	}

	/**
	 * Construct a new {@link BottomPojoTypeDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	public BottomPojoTypeDescriptor (final Mutability mutability)
	{
		super(mutability, null, null);
	}

	/** The mutable {@link BottomPojoTypeDescriptor}. */
	static final BottomPojoTypeDescriptor mutable =
		new BottomPojoTypeDescriptor(Mutability.MUTABLE);

	@Override
	BottomPojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link BottomPojoTypeDescriptor}. */
	private static final BottomPojoTypeDescriptor shared =
		new BottomPojoTypeDescriptor(Mutability.SHARED);

	@Override
	BottomPojoTypeDescriptor immutable ()
	{
		// There is no immutable descriptor, just a shared one.
		return shared;
	}

	@Override
	BottomPojoTypeDescriptor shared ()
	{
		return shared;
	}

	/**
	 * The most specific {@linkplain PojoTypeDescriptor pojo type},
	 * other than {@linkplain BottomTypeDescriptor#bottom() bottom}.
	 */
	private static final A_Type pojoBottom =
		BottomPojoTypeDescriptor.mutable.create().makeShared();

	/**
	 * Answer the most specific {@linkplain PojoTypeDescriptor pojo
	 * type}, other than {@linkplain BottomTypeDescriptor#bottom() bottom}.
	 *
	 * @return The most specific pojo type.
	 */
	public static A_Type pojoBottom ()
	{
		return pojoBottom;
	}
}
