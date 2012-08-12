/**
 * BottomPojoTypeDescriptor.java
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
import com.avail.serialization.SerializerOperation;

/**
 * {@code BottomPojoTypeDescriptor} describes the type of Java {@code null},
 * which is implicitly an instance of every Java reference type. It therefore
 * describes the most specific Java reference type. Its only proper subtype is
 * Avail's own {@linkplain BottomTypeDescriptor bottom type}.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
final class BottomPojoTypeDescriptor
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
		final AvailObject aPojoType)
	{
		return aPojoType.isSupertypeOfPojoBottomType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		return aPojoType.equalsPojoBottomType();
	}

	@Override
	AvailObject o_JavaAncestors (final AvailObject object)
	{
		return NullDescriptor.nullObject();
	}

	@Override @AvailMethod
	AvailObject o_JavaClass (final AvailObject object)
	{
		return RawPojoDescriptor.rawNullObject();
	}

	@Override @AvailMethod
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		object.descriptor = immutable;
		return object;
	}

	@Override @AvailMethod
	AvailObject o_PojoSelfType (final AvailObject object)
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
	AvailObject o_TypeIntersectionOfPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		return object;
	}

	@Override
	AvailObject o_TypeIntersectionOfPojoFusedType (
		final AvailObject object,
		final AvailObject aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	AvailObject o_TypeIntersectionOfPojoUnfusedType (
		final AvailObject object,
		final AvailObject anUnfusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	AvailObject o_TypeUnionOfPojoType (
		final AvailObject object,
		final AvailObject aPojoType)
	{
		return aPojoType;
	}

	@Override
	AvailObject o_TypeUnionOfPojoFusedType (
		final AvailObject object,
		final AvailObject aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	AvailObject o_TypeUnionOfPojoUnfusedType (
		final AvailObject object,
		final AvailObject anUnfusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	AvailObject o_TypeVariables (final AvailObject object)
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
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("pojo ⊥");
	}

	/**
	 * Construct a new {@link BottomPojoTypeDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain BottomPojoTypeDescriptor descriptor}
	 *        represent a mutable object?
	 */
	public BottomPojoTypeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link BottomPojoTypeDescriptor}. */
	private final static BottomPojoTypeDescriptor mutable =
		new BottomPojoTypeDescriptor(true);

	/**
	 * Answer the mutable {@link BottomPojoTypeDescriptor}.
	 *
	 * @return The mutable {@code BottomPojoTypeDescriptor}.
	 */
	static BottomPojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link BottomPojoTypeDescriptor}. */
	private final static BottomPojoTypeDescriptor immutable =
		new BottomPojoTypeDescriptor(false);
}
