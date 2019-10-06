/*
 * AtomWithPropertiesDescriptor.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

import com.avail.annotations.AvailMethod;
import com.avail.annotations.HideFieldInDebugger;
import com.avail.serialization.Serializer;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import static com.avail.descriptor.AtomWithPropertiesDescriptor.IntegerSlots.HASH_AND_MORE;
import static com.avail.descriptor.AtomWithPropertiesDescriptor.IntegerSlots.HASH_OR_ZERO;
import static com.avail.descriptor.AtomWithPropertiesDescriptor.ObjectSlots.ISSUING_MODULE;
import static com.avail.descriptor.AtomWithPropertiesDescriptor.ObjectSlots.NAME;
import static com.avail.descriptor.AtomWithPropertiesDescriptor.ObjectSlots.PROPERTY_MAP_POJO;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.RawPojoDescriptor.identityPojo;
import static java.util.Collections.synchronizedMap;

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
 * {@link AvailObject#becomeIndirectionTo(A_BasicObject)} into a {@code
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
		 * The low 32 bits are used for the {@link #HASH_OR_ZERO}, but the upper
		 * 32 can be used by other {@link BitField}s in subclasses.
		 */
		@HideFieldInDebugger
		HASH_AND_MORE;

		/**
		 * A slot to hold the hash value, or zero if it has not been computed.
		 * The hash of an atom is a random number, computed once.
		 */
		static final BitField HASH_OR_ZERO = bitField(HASH_AND_MORE, 0, 32);

		static
		{
			assert AtomDescriptor.IntegerSlots.HASH_AND_MORE.ordinal()
				== HASH_AND_MORE.ordinal();
			assert AtomDescriptor.IntegerSlots.HASH_OR_ZERO.isSamePlaceAs(
				HASH_OR_ZERO);
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
		 * A weak map from this atom's property keys (atoms) to property values.
		 */
		PROPERTY_MAP_POJO;

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
			|| e == HASH_AND_MORE
			|| e == PROPERTY_MAP_POJO;
	}

	@Override
	AvailObject o_MakeShared (final AvailObject object)
	{
		assert !isShared();
		// The layout of the destination descriptor is the same, so nothing
		// special needs to happen, i.e., object doesn't need to become an
		// indirection.
		final AvailObject propertyMapPojo = object.slot(PROPERTY_MAP_POJO);
		propertyMapPojo.makeShared();
		final Map<A_Atom, AvailObject> propertyMap =
			propertyMapPojo.javaObjectNotNull();
		for (final Entry<A_Atom, AvailObject> entry :
			propertyMap.entrySet())
		{
			entry.getKey().makeShared();
			entry.setValue(entry.getValue().makeShared());
		}
		object.descriptor = AtomWithPropertiesSharedDescriptor.shared;
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
		final A_BasicObject propertyMapPojo = object.slot(PROPERTY_MAP_POJO);
		final Map<A_Atom, A_BasicObject> propertyMap =
			propertyMapPojo.javaObjectNotNull();
		if (value.equalsNil())
		{
			propertyMap.remove(key);
		}
		else
		{
			propertyMap.put(key.makeImmutable(), value.makeImmutable());
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Extract the property value of this atom at the specified key.  Return
	 * {@linkplain NilDescriptor#nil nil} if no such property exists.
	 * </p>
	 */
	@Override @AvailMethod
	AvailObject o_GetAtomProperty (
		final AvailObject object,
		final A_Atom key)
	{
		assert key.isAtom();
		final A_BasicObject propertyMapPojo = object.slot(PROPERTY_MAP_POJO);
		final Map<A_Atom, AvailObject> propertyMap =
			propertyMapPojo.javaObjectNotNull();
		final @Nullable AvailObject value = propertyMap.get(key);
		return value == null ? nil : value;
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
	public static AvailObject createAtomWithProperties (
		final A_String name,
		final A_Module issuingModule)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(NAME, name);
		instance.setSlot(ISSUING_MODULE, issuingModule);
		instance.setSlot(
			PROPERTY_MAP_POJO,
			identityPojo(
				synchronizedMap(new WeakHashMap<A_Atom, A_BasicObject>())));
		instance.setSlot(HASH_OR_ZERO, 0);
		return instance.makeShared();
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
		final A_String name,
		final A_Module issuingModule,
		final int originalHash)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(NAME, name);
		instance.setSlot(ISSUING_MODULE, issuingModule);
		instance.setSlot(
			PROPERTY_MAP_POJO,
			identityPojo(
				synchronizedMap(new WeakHashMap<A_Atom, A_BasicObject>())));
		instance.setSlot(HASH_OR_ZERO, originalHash);
		return instance.makeShared();
	}

	/**
	 * Construct a new {@code AtomWithPropertiesDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param typeTag
	 *        The {@link TypeTag} to use in this descriptor.
	 * @param objectSlotsEnumClass
	 *        The Java {@link Class} which is a subclass of {@link
	 *        ObjectSlotsEnum} and defines this object's object slots layout, or
	 *        null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *        The Java {@link Class} which is a subclass of {@link
	 *        IntegerSlotsEnum} and defines this object's integer slots layout,
	 *        or null if there are no integer slots.
	 */
	protected AtomWithPropertiesDescriptor (
		final Mutability mutability,
		final TypeTag typeTag,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass)
	{
		super(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
	}

	/** The mutable {@link AtomWithPropertiesDescriptor}. */
	private static final AtomWithPropertiesDescriptor mutable =
		new AtomWithPropertiesDescriptor(
			Mutability.MUTABLE,
			TypeTag.ATOM_TAG,
			ObjectSlots.class,
			IntegerSlots.class);

	@Override
	AtomWithPropertiesDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link AtomWithPropertiesDescriptor}. */
	private static final AtomWithPropertiesDescriptor immutable =
		new AtomWithPropertiesDescriptor(
			Mutability.IMMUTABLE,
			TypeTag.ATOM_TAG,
			ObjectSlots.class,
			IntegerSlots.class);

	@Override
	AtomWithPropertiesDescriptor immutable ()
	{
		return immutable;
	}
}
