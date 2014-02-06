/**
 * AtomWithPropertiesSharedDescriptor.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import com.avail.annotations.*;
import com.avail.serialization.Serializer;

/**
 * An {@code atom} is an object that has identity by fiat, i.e., it is
 * distinguished from all other objects by the fact of its creation event and
 * the history of what happens to its references.  Not all objects in Avail have
 * that property (hence the acronym Advanced Value And Identity Language),
 * unlike most object-oriented programming languages.
 *
 * <p>
 * At any time an atom can have properties associated with it.  A property is
 * an association between another atom, known as the property key, and the value
 * of that property, any Avail object.  Atoms without properties have a
 * {@linkplain AtomDescriptor representation} that does not include a slot for
 * the properties information, but adding a property causes it to transform (via
 * {@link AvailObject#becomeIndirectionTo(A_BasicObject)} into a {@linkplain
 * AtomWithPropertiesDescriptor representation} that has a slot which contains
 * a map from property keys to property values.
 * </p>
 *
 * <p>
 * {@linkplain AvailObject Objects} using this descriptor are {@linkplain
 * Mutability#SHARED shared}.
 * </p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see AtomDescriptor
 * @see AtomWithPropertiesDescriptor
 */
final class AtomWithPropertiesSharedDescriptor
extends AtomWithPropertiesDescriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The hash value of this {@linkplain AtomDescriptor atom}.  It is a
		 * random number (not 0), computed on demand.
		 */
		@HideFieldInDebugger
		HASH_OR_ZERO;

		static
		{
			assert AtomDescriptor.IntegerSlots.HASH.ordinal()
				== HASH_OR_ZERO.ordinal();
		}
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A string (non-uniquely) roughly identifying this atom.  It need not
		 * be unique among atoms.  Must have the same ordinal as {@link
		 * AtomDescriptor.ObjectSlots#NAME}.
		 */
		NAME,

		/**
		 * The {@linkplain ModuleDescriptor module} that was active when this
		 * atom was issued.  This information is crucial to {@linkplain
		 * Serializer serialization}.  Must have the same ordinal as
		 * {@link AtomDescriptor.ObjectSlots#NAME}.
		 */
		ISSUING_MODULE,

		/**
		 * A map from this atom's property keys (atoms) to property values.
		 */
		PROPERTY_MAP;

		static
		{
			assert AtomDescriptor.ObjectSlots.NAME.ordinal()
				== NAME.ordinal();
			assert AtomDescriptor.ObjectSlots.ISSUING_MODULE.ordinal()
				== ISSUING_MODULE.ordinal();
		}
	}

	@Override
	AvailObject o_MakeImmutable (final AvailObject object)
	{
		// Do nothing; just answer the (shared) receiver.
		return object;
	}

	@Override @AvailMethod
	AvailObject o_MakeShared (final AvailObject object)
	{
		return object;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		synchronized (object)
		{
			return super.o_Hash(object);
		}
	}

	@Override @AvailMethod
	AvailObject o_GetAtomProperty (
		final AvailObject object,
		final A_Atom key)
	{
		synchronized (object)
		{
			return super.o_GetAtomProperty(object, key);
		}
	}

	@Override @AvailMethod
	void o_SetAtomProperty (
		final AvailObject object,
		final A_Atom key,
		final A_BasicObject value)
	{
		synchronized (object)
		{
			super.o_SetAtomProperty(
				object,
				key.traversed().makeShared(),
				value.traversed().makeShared());
		}
	}

	@Override @AvailMethod
	A_Bundle o_BundleOrCreate (final AvailObject object)
	{
		synchronized (object)
		{
			return super.o_BundleOrCreate(object);
		}
	}

	@Override @AvailMethod
	A_Bundle o_BundleOrNil (final AvailObject object)
	{
		synchronized (object)
		{
			return super.o_BundleOrNil(object);
		}
	}

	@Override
	boolean o_IsAtomSpecial (final AvailObject object)
	{
		return isSpecial;
	}

	/**
	 * Whether this descriptor is the one used to indicate atoms that are
	 * explicitly defined by the Avail virtual machine.
	 */
	final boolean isSpecial;

	/**
	 * Construct a new {@link AtomWithPropertiesSharedDescriptor}.
	 *
	 * @param isSpecial
	 *            Whether this particular descriptor is used to represent
	 *            an atom that has special meaning to the Avail virtual machine.
	 */
	private AtomWithPropertiesSharedDescriptor (
		final boolean isSpecial)
	{
		super(Mutability.SHARED, ObjectSlots.class, IntegerSlots.class);
		this.isSpecial = isSpecial;
	}

	/** The shared {@link AtomWithPropertiesDescriptor}. */
	static final AtomWithPropertiesSharedDescriptor shared =
		new AtomWithPropertiesSharedDescriptor(false);

	/** The sharedAndSpecial {@link AtomWithPropertiesDescriptor}. */
	static final AtomWithPropertiesSharedDescriptor sharedAndSpecial =
		new AtomWithPropertiesSharedDescriptor(true);
}
