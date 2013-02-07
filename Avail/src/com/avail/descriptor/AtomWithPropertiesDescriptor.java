/**
 * AtomWithPropertiesDescriptor.java
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

import static com.avail.descriptor.AtomWithPropertiesDescriptor.IntegerSlots.*;
import static com.avail.descriptor.AtomWithPropertiesDescriptor.ObjectSlots.*;
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
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @see AtomDescriptor
 * @see AtomWithPropertiesSharedDescriptor
 */
public class AtomWithPropertiesDescriptor
extends AtomDescriptor
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
			assert AtomDescriptor.IntegerSlots.HASH_OR_ZERO.ordinal()
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
	boolean allowsImmutableToMutableReferenceInField (final AbstractSlotsEnum e)
	{
		return super.allowsImmutableToMutableReferenceInField(e)
			|| e == HASH_OR_ZERO
			|| e == PROPERTY_MAP;
	}

	@Override
	AvailObject o_MakeShared (final AvailObject object)
	{
		assert !isShared();
		// The layout of the destination descriptor is the same, so nothing
		// special needs to happen, i.e., object doesn't need to become an
		// indirection.
		object.descriptor = AtomWithPropertiesSharedDescriptor.shared;
		object.slot(PROPERTY_MAP).makeShared();
		return object;
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
		final AvailObject object,
		final A_Atom key,
		final A_BasicObject value)
	{
		assert key.isAtom();
		A_Map map = object.slot(PROPERTY_MAP);
		if (value.equalsNil())
		{
			map = map.mapWithoutKeyCanDestroy(key, true);
		}
		else
		{
			map = map.mapAtPuttingCanDestroy(key, value, true);
		}
		if (isShared())
		{
			map = map.traversed().makeShared();
		}
		object.setSlot(PROPERTY_MAP, map);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Extract the property value of this atom at the specified key.  Return
	 * {@linkplain NilDescriptor#nil() nil} if no such
	 * property exists.
	 * </p>
	 */
	@Override @AvailMethod
	AvailObject o_GetAtomProperty (
		final AvailObject object,
		final A_Atom key)
	{
		assert key.isAtom();
		final A_Map map = object.slot(PROPERTY_MAP);
		if (map.hasKey(key))
		{
			return map.mapAt(key);
		}
		return NilDescriptor.nil();
	}

	/**
	 * Create a new atom with the given name.  The name is not globally unique,
	 * but serves to help to visually distinguish atoms.  In this class, the
	 * created object already has an empty property map.
	 *
	 * @param name
	 *            A string used to help identify the new atom.
	 * @param issuingModule
	 *            Which {@linkplain ModuleDescriptor module} was active when the
	 *            atom was created.
	 * @return
	 *            The new atom, not equal to any object in use before this
	 *            method was invoked.
	 */
	public static AvailObject create (
		final AvailObject name,
		final AvailObject issuingModule)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(NAME, name);
		instance.setSlot(ISSUING_MODULE, issuingModule);
		instance.setSlot(PROPERTY_MAP, MapDescriptor.empty());
		instance.setSlot(HASH_OR_ZERO, 0);
		instance.makeImmutable();
		return instance;
	}

	/**
	 * Create a new atom with the given name, module, and hash value.  The name
	 * is not globally unique, but serves to help to visually distinguish atoms.
	 * The hash value is provided to allow an existing {@linkplain
	 * AtomDescriptor simple atom} to be converted to an {@linkplain
	 * AtomWithPropertiesDescriptor atom with properties}.  The client can
	 * convert the original simple atom into an {@linkplain
	 * IndirectionDescriptor indirection} to the new atom with properties.
	 *
	 * @param name
	 *            A string used to help identify the new atom.
	 * @param issuingModule
	 *            The module that issued this atom.
	 * @param originalHash
	 *            The hash value that must be set for this atom, or zero if it
	 *            doesn't matter.
	 * @return
	 *            The new atom, not equal to any object in use before this
	 *            method was invoked.
	 */
	public static AvailObject createWithNameAndModuleAndHash (
		final AvailObject name,
		final AvailObject issuingModule,
		final int originalHash)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(NAME, name);
		instance.setSlot(ISSUING_MODULE, issuingModule);
		instance.setSlot(PROPERTY_MAP, MapDescriptor.empty());
		instance.setSlot(HASH_OR_ZERO, originalHash);
		instance.makeImmutable();
		return instance;
	}

	/**
	 * Construct a new {@link AtomWithPropertiesDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	protected AtomWithPropertiesDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link AtomWithPropertiesDescriptor}. */
	private static final AtomWithPropertiesDescriptor mutable =
		new AtomWithPropertiesDescriptor(Mutability.MUTABLE);

	@Override
	AtomWithPropertiesDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link AtomWithPropertiesDescriptor}. */
	private static final AtomWithPropertiesDescriptor immutable =
		new AtomWithPropertiesDescriptor(Mutability.IMMUTABLE);

	@Override
	AtomWithPropertiesDescriptor immutable ()
	{
		return immutable;
	}
}
