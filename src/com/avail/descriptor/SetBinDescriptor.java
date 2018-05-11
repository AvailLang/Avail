/*
 * SetBinDescriptor.java
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
import com.avail.descriptor.SetDescriptor.SetIterator;

import javax.annotation.Nullable;

import static com.avail.descriptor.SetBinDescriptor.IntegerSlots.BIN_HASH;

/**
 * This abstract class organizes the idea of nodes in a Bagwell Ideal Hash Tree
 * used to implement hashed maps.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public abstract class SetBinDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * The low 32 bits are used for the {@link #BIN_HASH}, but the upper
		 * 32 can be used by other {@link BitField}s in subclasses.
		 */
		@HideFieldInDebugger
		BIN_HASH_AND_MORE;

		/**
		 * A slot to hold the bin's hash value, or zero if it has not been
		 * computed.
		 */
		static final BitField BIN_HASH = bitField(BIN_HASH_AND_MORE, 0, 32);
	}

	@Override @AvailMethod
	int o_SetBinHash (final AvailObject object)
	{
		return object.slot(BIN_HASH);
	}

	@Override @AvailMethod
	boolean o_IsSetBin (final AvailObject object)
	{
		return true;
	}

	/**
	 * Asked of the top bin of a set.  If the set is large enough to be
	 * hashed then compute/cache the union's nearest kind, otherwise just
	 * answer nil.
	 */
	@Override @AvailMethod
	abstract boolean o_BinElementsAreAllInstancesOfKind (
		final AvailObject object, A_Type kind);

	@Override
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return false;
	}

	@Override
	abstract SetIterator o_SetBinIterator (
		final AvailObject object);

	/**
	 * The level of my objects in their enclosing bin trees. The top node is
	 * level 0 (using hash bits 0..5), and the bottom hashed node is level 5
	 * (using hash bits 30..34, the top three of which are always zero). There
	 * can be a level 6 {@linkplain LinearSetBinDescriptor linear bin}, but it
	 * represents elements which all have the same hash value, so it should
	 * never be hashed.
	 */
	final byte level;

	/**
	 * Construct a new {@link AtomWithPropertiesDescriptor}.
	 *
	 * @param mutability
	 *            The {@linkplain Mutability mutability} of the new descriptor.
	 * @param typeTag
	 *            The {@link TypeTag} for the new descriptor.
	 * @param objectSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            ObjectSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *            The Java {@link Class} which is a subclass of {@link
	 *            IntegerSlotsEnum} and defines this object's object slots
	 *            layout, or null if there are no integer slots.
	 * @param level
	 *            The depth of the bin in the hash tree.
	 */
	protected SetBinDescriptor (
		final Mutability mutability,
		final TypeTag typeTag,
		final @Nullable Class<? extends ObjectSlotsEnum> objectSlotsEnumClass,
		final @Nullable Class<? extends IntegerSlotsEnum> integerSlotsEnumClass,
		final int level)
	{
		super(mutability, typeTag, objectSlotsEnumClass, integerSlotsEnumClass);
		this.level = (byte) level;
	}
}
