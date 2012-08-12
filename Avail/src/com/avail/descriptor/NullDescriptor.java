/**
 * NullDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.descriptor.MapDescriptor.*;
import com.avail.serialization.SerializerOperation;

/**
 * {@code NullDescriptor} implements the Avail {@linkplain #nullObject() null
 * object}, the sole instance of the invisible and uninstantiable root type, ⊤
 * (pronounced top).
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class NullDescriptor
extends Descriptor
{
	/** The sole instance of the {@linkplain #nullObject() null object}. */
	private static AvailObject soleInstance;

	/**
	 * Create the sole instance of the {@linkplain #nullObject() null object}.
	 */
	static void createWellKnownObjects ()
	{
		soleInstance = immutable().create();
	}

	/**
	 * Discard the sole instance of the {@linkplain #nullObject() null object}.
	 */
	static void clearWellKnownObjects ()
	{
		soleInstance = null;
	}

	/**
	 * Answer the sole instance of the null object.
	 *
	 * @return The sole instance of the null object.
	 */
	@ThreadSafe
	public static AvailObject nullObject ()
	{
		return soleInstance;
	}

	@Override
	@AvailMethod @ThreadSafe
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return another.equalsNull();
	}

	@Override
	@AvailMethod @ThreadSafe
	boolean o_EqualsNull (final AvailObject object)
	{
		//  There is only one top.
		return true;
	}

	@Override
	@AvailMethod @ThreadSafe
	int o_Hash (final AvailObject object)
	{
		// The null object should hash to zero, because the only place it can
		// appear in a data structure is as a filler object.  This currently
		// (as of July 1998) applies to sets, maps, variables, and
		// continuations.
		return 0;
	}

	@Override
	@AvailMethod @ThreadSafe
	AvailObject o_Kind (final AvailObject object)
	{
		return TOP.o();
	}

	@Override @AvailMethod
	AvailObject o_SetBinAddingElementHashLevelCanDestroy (
		final AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		// The null object can't be an actual member of a set, so if one
		// receives this message it must be the rootBin of a set (empty by
		// definition).  Answer the new element, which will become the new
		// rootBin, indicating a set of size one.
		if (!canDestroy)
		{
			elementObject.makeImmutable();
		}
		return elementObject;
	}

	@Override
	@AvailMethod @ThreadSafe
	boolean o_IsBinSubsetOf (
		final AvailObject object,
		final AvailObject potentialSuperset)
	{
		// Top can't actually be a member of a set, so treat it as a
		// structural component indicating an empty bin within a set.
		// Since it's empty, it is a subset of potentialSuperset.
		return true;
	}

	@Override
	@AvailMethod @ThreadSafe
	AvailObject o_BinRemoveElementHashCanDestroy (
		final AvailObject object,
		final AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{
		// The null object is acting as a bin of size zero, so the answer must
		// also be the null object.
		return NullDescriptor.nullObject();
	}

	@Override
	@AvailMethod @ThreadSafe
	int o_BinHash (final AvailObject object)
	{
		// The null object acting as a size-zero bin has a bin hash which is the
		// sum of the elements' hashes, which in this case is zero.
		return 0;
	}

	@Override
	@AvailMethod @ThreadSafe
	int o_BinSize (final AvailObject object)
	{
		// The null object acts as an empty bin.
		return 0;
	}

	@Override
	@AvailMethod @ThreadSafe
	AvailObject o_BinUnionKind (
		final AvailObject object)
	{
		// The null object acts as an empty bin.
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.NULL_OBJECT;
	}

	@Override
	AvailObject o_MapBinAtHashPutLevelCanDestroy (
		final AvailObject object,
		final AvailObject key,
		final int keyHash,
		final AvailObject value,
		final byte myLevel,
		final boolean canDestroy)
	{
		return LinearMapBinDescriptor.createSingle(
			key,
			keyHash,
			value,
			myLevel);
	}

	@Override
	AvailObject o_MapBinKeyUnionKind (
		final AvailObject object)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	AvailObject o_MapBinValueUnionKind (
		final AvailObject object)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	AvailObject o_MapBinAtHash (
		final AvailObject object,
		final AvailObject key,
		final int keyHash)
	{
		return nullObject();
	}

	@Override
	int o_MapBinKeysHash (
		final AvailObject object)
	{
		return 0;
	}

	@Override
	int o_MapBinValuesHash (
		final AvailObject object)
	{
		return 0;
	}

	@Override
	boolean o_BinElementsAreAllInstancesOfKind (
		final AvailObject object,
		final AvailObject kind)
	{
		// Nil is treated as an empty bin, so its members all satisfy.. any
		// property whatsoever.
		return true;
	}

	@Override
	public MapIterable o_MapBinIterable (
		final AvailObject object)
	{
		return new MapIterable()
		{
			@Override
			public final Entry next ()
			{
				throw new UnsupportedOperationException();
			}

			@Override
			public final boolean hasNext ()
			{
				return false;
			}
		};
	}

	@Override
	@ThreadSafe
	public void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("nil");
	}

	/**
	 * Construct a new {@link NullDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected NullDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/** The mutable {@link NullDescriptor}. */
	private static final NullDescriptor mutable =
		new NullDescriptor(true);

	/**
	 * Answer the mutable {@link NullDescriptor}.
	 *
	 * @return The mutable {@link NullDescriptor}.
	 */
	@ThreadSafe
	public static NullDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link NullDescriptor}. */
	private static final NullDescriptor immutable =
		new NullDescriptor(false);

	/**
	 * Answer an immutable {@link NullDescriptor}.
	 *
	 * @return An immutable {@link NullDescriptor}.
	 */
	@ThreadSafe
	public static NullDescriptor immutable ()
	{
		return immutable;
	}
}
