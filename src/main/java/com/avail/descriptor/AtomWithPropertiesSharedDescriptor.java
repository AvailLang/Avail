/*
 * AtomWithPropertiesSharedDescriptor.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
import com.avail.exceptions.MalformedMessageException;
import com.avail.serialization.Serializer;

import static com.avail.descriptor.AtomWithPropertiesSharedDescriptor.IntegerSlots.HASH_OR_ZERO;

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
			assert AtomWithPropertiesDescriptor.IntegerSlots.HASH_AND_MORE
					.ordinal()
				== HASH_AND_MORE.ordinal();
			assert AtomWithPropertiesDescriptor.IntegerSlots.HASH_OR_ZERO
				.isSamePlaceAs(HASH_OR_ZERO);
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
			assert AtomWithPropertiesDescriptor.ObjectSlots
				.PROPERTY_MAP_POJO.ordinal()
					== PROPERTY_MAP_POJO.ordinal();
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
		final int hash = object.slot(HASH_OR_ZERO);
		if (hash == 0)
		{
			synchronized (object)
			{
				return super.o_Hash(object);
			}
		}
		return hash;
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
		final A_Atom sharedKey = key.makeShared();
		final A_BasicObject sharedValue = value.makeShared();
		synchronized (object)
		{
			super.o_SetAtomProperty(object, sharedKey, sharedValue);
		}
	}

	@Override @AvailMethod
	A_Bundle o_BundleOrCreate (final AvailObject object)
		throws MalformedMessageException
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
	 * Construct a new {@code AtomWithPropertiesSharedDescriptor}.
	 *
	 * @param isSpecial
	 *        Whether this particular descriptor is used to represent an atom
	 *        that has special meaning to the Avail virtual machine.
	 * @param typeTag
	 *        The {@link TypeTag} to use in this descriptor.
	 */
	private AtomWithPropertiesSharedDescriptor (
		final boolean isSpecial,
		final TypeTag typeTag)
	{
		super(
			Mutability.SHARED, typeTag, ObjectSlots.class, IntegerSlots.class);
		this.isSpecial = isSpecial;
	}

	/** The shared {@link AtomWithPropertiesDescriptor}. */
	static final AtomWithPropertiesSharedDescriptor shared =
		new AtomWithPropertiesSharedDescriptor(false, TypeTag.ATOM_TAG);

	/** The sharedAndSpecial {@link AtomWithPropertiesDescriptor}. */
	static final AtomWithPropertiesSharedDescriptor sharedAndSpecial =
		new AtomWithPropertiesSharedDescriptor(true, TypeTag.ATOM_TAG);

	/** The descriptor reserved for the {@link AtomDescriptor#trueObject() true
	 * atom}. */
	static final AtomWithPropertiesSharedDescriptor sharedAndSpecialForTrue =
		new AtomWithPropertiesSharedDescriptor(true, TypeTag.TRUE_TAG);

	/** The descriptor reserved for the {@link AtomDescriptor#falseObject()
	 * false atom}. */
	static final AtomWithPropertiesSharedDescriptor sharedAndSpecialForFalse =
		new AtomWithPropertiesSharedDescriptor(true, TypeTag.FALSE_TAG);
}
