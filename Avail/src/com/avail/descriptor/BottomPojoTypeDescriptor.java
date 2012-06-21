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
	boolean o_EqualsPojoBottomType (final @NotNull AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	boolean o_EqualsPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return aPojoType.equalsPojoBottomType();
	}

	@Override @AvailMethod
	int o_Hash (final @NotNull AvailObject object)
	{
		return 0x496FFE01;
	}

	@Override @AvailMethod
	boolean o_IsAbstract (final @NotNull AvailObject object)
	{
		// Pojo bottom has an instance: null.
		return false;
	}

	@Override
	boolean o_IsPojoArrayType (final @NotNull AvailObject object)
	{
		// Pojo bottom is the most specific pojo type, so it is also a pojo
		// array type.
		return true;
	}

	@Override @AvailMethod
	boolean o_IsPojoFusedType (final @NotNull AvailObject object)
	{
		// Pojo bottom is the intersection of any two unrelated pojo types, so
		// it is a pojo fusion type.
		return true;
	}

	@Override @AvailMethod
	boolean o_IsSubtypeOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return aPojoType.isSupertypeOfPojoBottomType(object);
	}

	@Override @AvailMethod
	boolean o_IsSupertypeOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return aPojoType.equalsPojoBottomType();
	}

	@Override
	@NotNull AvailObject o_JavaAncestors (final @NotNull AvailObject object)
	{
		return NullDescriptor.nullObject();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_JavaClass (final @NotNull AvailObject object)
	{
		return RawPojoDescriptor.rawNullObject();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_MakeImmutable (final @NotNull AvailObject object)
	{
		object.descriptor = immutable;
		return object;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_PojoSelfType (final @NotNull AvailObject object)
	{
		// The pojo bottom type is its own self type.
		return object;
	}

	@Override
	Object o_MarshalToJava (
		final @NotNull AvailObject object,
		final Class<?> ignoredClassHint)
	{
		return Object.class;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeIntersectionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return object;
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfPojoFusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	@NotNull AvailObject o_TypeIntersectionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_TypeUnionOfPojoType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aPojoType)
	{
		return aPojoType;
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfPojoFusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject aFusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	@NotNull AvailObject o_TypeUnionOfPojoUnfusedType (
		final @NotNull AvailObject object,
		final @NotNull AvailObject anUnfusedPojoType)
	{
		throw unsupportedOperationException();
	}

	@Override
	@NotNull AvailObject o_TypeVariables (final @NotNull AvailObject object)
	{
		throw unsupportedOperationException();
	}

	@Override
	void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
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
	private final static @NotNull BottomPojoTypeDescriptor mutable =
		new BottomPojoTypeDescriptor(true);

	/**
	 * Answer the mutable {@link BottomPojoTypeDescriptor}.
	 *
	 * @return The mutable {@code BottomPojoTypeDescriptor}.
	 */
	static @NotNull BottomPojoTypeDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link BottomPojoTypeDescriptor}. */
	private final static @NotNull BottomPojoTypeDescriptor immutable =
		new BottomPojoTypeDescriptor(false);
}
