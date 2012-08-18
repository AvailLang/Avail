/**
 * MapBinDescriptor.java
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

import com.avail.annotations.*;
import com.avail.descriptor.MapDescriptor.MapIterable;

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
	public enum IntegerSlots implements IntegerSlotsEnum
	{
		/**
		 * The sum of the hashes of the keys recursively within this bin.
		 */
		KEYS_HASH,

		/**
		 * The sum of the hashes of the values recursively within this bin.
		 */
		VALUES_HASH_OR_ZERO
	}

	@Override @AvailMethod
	int o_MapBinKeysHash (
		final AvailObject object)
	{
		return object.slot(IntegerSlots.KEYS_HASH);
	}

	@Override @AvailMethod
	abstract int o_MapBinValuesHash (
		final AvailObject object);

	@Override
	boolean o_IsHashedMapBin (
		final AvailObject object)
	{
		return false;
	}

	@Override
	abstract AvailObject o_MapBinAtHash (
		final AvailObject object,
		final AvailObject key,
		final int keyHash);

	@Override
	public boolean o_ShowValueInNameForDebugger (
		final AvailObject object)
	{
		return false;
	}

	@Override
	abstract MapIterable o_MapBinIterable (
		final AvailObject object);

	/**
	 * The level of my objects in their enclosing bin trees.
	 *
	 * @see #level()
	 */
	final byte level;

	/**
	 * Answer what level this descriptor's objects occupy in their hierarchy.
	 * The top node is level 0 (using hash bits 0..4), and the bottom hashed
	 * node is level 6 (using hash bits 30..34, the top three of which are
	 * always zero).  There can be a level 7 {@linkplain LinearMapBinDescriptor
	 * linear bin}, but it represents elements which all have the same hash
	 * value, so it should never be hashed.
	 *
	 * @return The descriptor's level in a bin tree.
	 */
	byte level ()
	{
		return level;
	}

	/**
	 * Construct a new {@link MapBinDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 * @param level The depth of the bin in the hash tree.
	 */
	protected MapBinDescriptor (
		final boolean isMutable,
		final int level)
	{
		super(isMutable);
		this.level = (byte) level;
	}
}
