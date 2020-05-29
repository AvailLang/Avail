/*
 * PojoDescriptor.java
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

package com.avail.descriptor.pojos;

import com.avail.descriptor.JavaCompatibility.ObjectSlotsEnumJava;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.representation.Descriptor;
import com.avail.descriptor.representation.Mutability;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.PojoTypeDescriptor;
import com.avail.descriptor.types.TypeTag;
import com.avail.utility.json.JSONWriter;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;

import static com.avail.descriptor.pojos.PojoDescriptor.ObjectSlots.KIND;
import static com.avail.descriptor.pojos.PojoDescriptor.ObjectSlots.RAW_POJO;
import static com.avail.descriptor.pojos.RawPojoDescriptor.rawNullPojo;
import static com.avail.descriptor.representation.AvailObject.multiplier;
import static com.avail.descriptor.types.BottomPojoTypeDescriptor.pojoBottom;

/**
 * A {@code PojoDescriptor} describes a plain-old Java object (pojo) that is
 * accessible to an Avail programmer as an {@linkplain AvailObject Avail
 * object}. An Avail pojo comprises a {@linkplain RawPojoDescriptor raw pojo}
 * and a {@linkplain PojoTypeDescriptor pojo type} that describes the pojo
 * contextually.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class PojoDescriptor
extends Descriptor
{
	/** The layout of the object slots. */
	public enum ObjectSlots implements ObjectSlotsEnumJava
	{
		/**
		 * A {@linkplain RawPojoDescriptor raw pojo}.
		 */
		RAW_POJO,

		/**
		 * The {@linkplain PojoTypeDescriptor kind} of the {@linkplain
		 * PojoDescriptor descriptor}.
		 */
		KIND
	}

	@Override
	public boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsPojo(object);
	}

	@Override
	public boolean o_EqualsPojo (
		final AvailObject object,
		final AvailObject aPojo)
	{
		if (!object.slot(RAW_POJO).equals(aPojo.slot(RAW_POJO)))
		{
			return false;
		}
		if (!object.sameAddressAs(aPojo))
		{
			if (!isShared())
			{
				aPojo.makeImmutable();
				object.becomeIndirectionTo(aPojo);
			}
			else if (!aPojo.descriptor().isShared())
			{
				object.makeImmutable();
				aPojo.becomeIndirectionTo(object);
			}
		}
		return true;
	}

	@Override
	public int o_Hash (final AvailObject object)
	{
		int hash = object.slot(RAW_POJO).hash() ^ 0x749101DD;
		hash *= multiplier;
		hash += object.slot(KIND).hash();
		return hash;
	}

	@Override
	public boolean o_IsPojo (final AvailObject object)
	{
		return true;
	}

	@Override
	public A_Type o_Kind (final AvailObject object)
	{
		return object.slot(KIND);
	}

	@Override
	public Object o_MarshalToJava (
		final AvailObject object,
		final @Nullable Class<?> ignoredClassHint)
	{
		return object.slot(RAW_POJO).javaObject();
	}

	@Override
	public AvailObject o_RawPojo (final AvailObject object)
	{
		return object.slot(RAW_POJO);
	}

	@Override
	public @Nullable <T> T o_JavaObject (final AvailObject object)
	{
		return object.slot(RAW_POJO).javaObject();
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return false;
	}

	@Override
	public void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("pojo");
		writer.write("pojo type");
		object.slot(KIND).writeTo(writer);
		writer.write("description");
		writer.write(object.slot(RAW_POJO).<String>javaObject());
		writer.endObject();
	}

	@Override
	public void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("pojo");
		writer.write("pojo type");
		object.slot(KIND).writeSummaryTo(writer);
		writer.write("description");
		writer.write(object.slot(RAW_POJO).<String>javaObject());
		writer.endObject();
	}

	@Override
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		builder.append(
			object.slot(RAW_POJO).<Object>javaObject());
		builder.append(" ∈ ");
		object.slot(KIND).printOnAvoidingIndent(builder, recursionMap, indent);
	}

	/**
	 * Construct a new {@link PojoDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private PojoDescriptor (final Mutability mutability)
	{
		super(mutability, TypeTag.POJO_TAG, ObjectSlots.class, null);
	}

	/** The mutable {@link PojoDescriptor}. */
	private static final PojoDescriptor mutable =
		new PojoDescriptor(Mutability.MUTABLE);

	@Override
	public PojoDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link PojoDescriptor}. */
	private static final PojoDescriptor immutable =
		new PojoDescriptor(Mutability.IMMUTABLE);

	@Override
	public PojoDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link PojoDescriptor}. */
	private static final PojoDescriptor shared =
		new PojoDescriptor(Mutability.SHARED);

	@Override
	public PojoDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Create a new {@link AvailObject} that wraps the specified {@linkplain
	 * RawPojoDescriptor raw pojo} and has the specified {@linkplain
	 * PojoTypeDescriptor pojo type}.
	 *
	 * @param rawPojo A raw pojo.
	 * @param pojoType A pojo type.
	 * @return The new {@linkplain PojoDescriptor Avail pojo}.
	 */
	public static AvailObject newPojo (
		final AvailObject rawPojo,
		final A_Type pojoType)
	{
		final AvailObject newObject = mutable.create();
		newObject.setSlot(RAW_POJO, rawPojo);
		newObject.setSlot(KIND, pojoType);
		return newObject.makeImmutable();
	}

	/** The {@linkplain PojoDescriptor pojo} that wraps Java's {@code null}. */
	private static final AvailObject nullObject =
		newPojo(rawNullPojo(), pojoBottom()).makeShared();

	/**
	 * Answer the {@linkplain PojoDescriptor pojo} that wraps Java's
	 * {@code null}.
	 *
	 * @return The {@code null} pojo.
	 */
	public static AvailObject nullPojo ()
	{
		return nullObject;
	}
}
