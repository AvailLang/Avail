/**
 * descriptor/AtomWithPropertiesDescriptor.java
 * Copyright (c) 2011, Mark van Gulik.
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
 * {@link AvailObject#becomeIndirectionTo(AvailObject)} into a {@linkplain
 * AtomWithPropertiesDescriptor representation} that has a slot which contains
 * a map from property keys to property values.
 * </p>
 *
 * @see AtomDescriptor
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AtomWithPropertiesDescriptor
extends AtomDescriptor
{

	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The hash value of this {@linkplain AtomDescriptor atom}.  It is a
		 * random number (not 0), computed on demand.
		 */
		HASH_OR_ZERO
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * A string (non-uniquely) roughly identifying this atom.  It need not
		 * be unique among atoms.
		 */
		NAME,

		/**
		 * A map from this atom's property keys (atoms) to property values.
		 */
		PROPERTY_MAP
	}

	@Override
	public boolean allowsImmutableToMutableReferenceInField (
		final @NotNull AbstractSlotsEnum e)
	{
		return e == IntegerSlots.HASH_OR_ZERO
			|| e == ObjectSlots.PROPERTY_MAP;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Add or replace a property of this {@linkplain AtomDescriptor atom with
	 * properties}.
	 * </p>
	 */
	@Override @AvailMethod
	void o_SetAtomProperty (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key,
		final @NotNull AvailObject value)
	{
		assert key.isAtom();
		AvailObject map = object.objectSlot(ObjectSlots.PROPERTY_MAP);
		if (value.equalsNull())
		{
			map = map.mapWithoutKeyCanDestroy(key, true);
		}
		else
		{
			map = map.mapAtPuttingCanDestroy(key, value, true);
		}
		object.objectSlotPut(ObjectSlots.PROPERTY_MAP, map);
	}


	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Extract the property value of this atom at the specified key.  Return
	 * {@linkplain NullDescriptor#nullObject() the null object} if no such
	 * property exists.
	 * </p>
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_GetAtomProperty (
		final @NotNull AvailObject object,
		final @NotNull AvailObject key)
	{
		assert key.isAtom();
		final AvailObject map = object.objectSlot(ObjectSlots.PROPERTY_MAP);
		if (map.hasKey(key))
		{
			return map.mapAt(key);
		}
		return NullDescriptor.nullObject();
	}

	/**
	 * Create a new atom with the given name.  The name is not globally unique,
	 * but serves to help to visually distinguish atoms.
	 *
	 * @param name
	 *            A string used to help identify the new atom.
	 * @return
	 *            The new atom, not equal to any object in use before this
	 *            method was invoked.
	 */
	public static @NotNull AvailObject create (
		final @NotNull AvailObject name)
	{
		final AvailObject instance = mutable().create();
		instance.objectSlotPut(ObjectSlots.NAME, name);
		instance.objectSlotPut(ObjectSlots.PROPERTY_MAP, MapDescriptor.empty());
		instance.integerSlotPut(IntegerSlots.HASH_OR_ZERO, 0);
		instance.makeImmutable();
		return instance;
	}

	/**
	 * Create a new atom with the given name and hash value.  The name is not
	 * globally unique, but serves to help to visually distinguish atoms.  The
	 * hash value is provided to allow an existing {@linkplain AtomDescriptor
	 * simple atom} to be converted to an {@linkplain
	 * AtomWithPropertiesDescriptor atom with properties}.  The client can
	 * convert the original simple atom into an {@linkplain
	 * IndirectionDescriptor indirection} to the new atom with properties.
	 *
	 * @param name
	 *            A string used to help identify the new atom.
	 * @param originalHash
	 *            The hash value that must be set for this atom, or zero if it
	 *            doesn't matter.
	 * @return
	 *            The new atom, not equal to any object in use before this
	 *            method was invoked.
	 */
	public static @NotNull AvailObject createWithNameAndHash (
		final @NotNull AvailObject name,
		final int originalHash)
	{
		final AvailObject instance = mutable().create();
		instance.objectSlotPut(ObjectSlots.NAME, name);
		instance.objectSlotPut(ObjectSlots.PROPERTY_MAP, MapDescriptor.empty());
		instance.integerSlotPut( IntegerSlots.HASH_OR_ZERO, originalHash);
		instance.makeImmutable();
		return instance;
	}

	/**
	 * Construct a new {@link AtomWithPropertiesDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected AtomWithPropertiesDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link AtomWithPropertiesDescriptor}.
	 */
	private final static AtomWithPropertiesDescriptor mutable =
		new AtomWithPropertiesDescriptor(true);

	/**
	 * Answer the mutable {@link AtomWithPropertiesDescriptor}.
	 *
	 * @return The mutable {@link AtomWithPropertiesDescriptor}.
	 */
	public static AtomWithPropertiesDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link AtomWithPropertiesDescriptor}.
	 */
	private final static AtomWithPropertiesDescriptor immutable =
		new AtomWithPropertiesDescriptor(false);

	/**
	 * Answer the immutable {@link AtomWithPropertiesDescriptor}.
	 *
	 * @return The immutable {@link AtomWithPropertiesDescriptor}.
	 */
	public static AtomWithPropertiesDescriptor immutable ()
	{
		return immutable;
	}
}
