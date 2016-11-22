/**
 * MapBinDescriptor.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import static com.avail.descriptor.MapBinDescriptor.IntegerSlots.*;

import com.avail.annotations.AvailMethod;
import com.avail.descriptor.MapDescriptor.MapIterable;
import org.jetbrains.annotations.Nullable;

/**
 * This abstract class organizes the idea of nodes in a Bagwell Ideal Hash Tree
 * used to implement hashed maps.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class MapBinDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * A long holding {@link BitField}s containing the combined keys hash
		 * and the combined values hash or zero.
		 */
		COMBINED_HASHES;

		/**
		 * The sum of the hashes of the elements recursively within this bin.
		 */
		public static BitField KEYS_HASH = bitField(
			COMBINED_HASHES, 0, 32);

		/**
		 * The sum of the hashes of the elements recursively within this bin,
		 * or zero if not computed.
		 */
		public static BitField VALUES_HASH_OR_ZERO = bitField(
			COMBINED_HASHES, 32, 32);
	}

	@Override @AvailMethod
	int o_MapBinKeysHash (final AvailObject object)
	{
		return object.slot(KEYS_HASH);
	}

	@Override @AvailMethod
	abstract int o_MapBinValuesHash (final AvailObject object);

	@Override
	boolean o_IsHashedMapBin (final AvailObject object)
	{
		return false;
	}

	@Override
	abstract AvailObject o_MapBinAtHash (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash);

	@Override
	public boolean o_ShowValueInNameForDebugger (
		final AvailObject object)
	{
		return false;
	}

	@Override
	abstract MapIterable o_MapBinIterable (final AvailObject object);

	/**
	 * The level of my objects in their enclosing bin trees. The top node is
	 * level 0 (using hash bits 0..5), and the bottom hashed node is level 5
	 * (using hash bits 30..35, the top four of which are always zero). There
	 * can be a level 6 {@linkplain LinearMapBinDescriptor linear bin}, but it
	 * represents elements which all have the same hash value, so it should
	 * never be hashed.
	 */
	final byte level;

	/**
	 * Construct a new {@link MapBinDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 * @param typeTag
	 *        The {@link TypeTag} to embed in the new descriptor.
	 * @param objectSlotsEnumClass
	 *        The Java {@link Class} which is a subclass of {@link
	 *        ObjectSlotsEnum} and defines this object's object slots layout, or
	 *        null if there are no object slots.
	 * @param integerSlotsEnumClass
	 *        The Java {@link Class} which is a subclass of {@link
	 *        IntegerSlotsEnum} and defines this object's integer slots layout,
	 *        or null if there are no integer slots.
	 * @param level
	 *        The depth of the bin in the hash tree.
	 */
	protected MapBinDescriptor (
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
