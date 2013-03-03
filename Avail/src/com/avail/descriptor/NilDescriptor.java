/**
 * NilDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.descriptor.MapDescriptor.*;
import com.avail.descriptor.SetDescriptor.*;
import com.avail.serialization.SerializerOperation;

/**
 * {@code NilDescriptor} implements the Avail {@linkplain #nil() null
 * object}, the sole instance of the invisible and uninstantiable root type, ⊤
 * (pronounced top).
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class NilDescriptor
extends Descriptor
{
	@Override
	@AvailMethod @ThreadSafe
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.equalsNil();
	}

	@Override
	@AvailMethod @ThreadSafe
	boolean o_EqualsNil (final AvailObject object)
	{
		//  There is only one Nil.
		return true;
	}

	@Override
	@AvailMethod @ThreadSafe
	int o_Hash (final AvailObject object)
	{
		// Nil should hash to zero, because the only place it can appear in a
		// data structure is as a filler object. This currently (as of July
		// 1998) applies to sets, maps, variables, and continuations.
		return 0;
	}

	@Override
	@AvailMethod @ThreadSafe
	A_Type o_Kind (final AvailObject object)
	{
		return TOP.o();
	}

	@Override @AvailMethod
	A_BasicObject o_SetBinAddingElementHashLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		// Nil can't be an actual member of a set, so if one receives this
		// message it must be the rootBin of a set (empty by definition). Answer
		// the new element, which will become the new rootBin, indicating a set
		// of size one.
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
		final A_Set potentialSuperset)
	{
		// Nil can't actually be a member of a set, so treat it as a structural
		// component indicating an empty bin within a set. Since it's empty, it
		// is a subset of potentialSuperset.
		return true;
	}

	@Override
	@AvailMethod @ThreadSafe
	AvailObject o_BinRemoveElementHashCanDestroy (
		final AvailObject object,
		final A_BasicObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{
		// Nil is acting as a bin of size zero, so the answer must also be nil.
		return NilDescriptor.nil();
	}

	@Override
	@AvailMethod @ThreadSafe
	int o_BinHash (final AvailObject object)
	{
		// Nil acting as a size-zero bin has a bin hash which is the sum of the
		// elements' hashes, which in this case is zero.
		return 0;
	}

	@Override
	@AvailMethod @ThreadSafe
	int o_BinSize (final AvailObject object)
	{
		// Nil acts as an empty bin.
		return 0;
	}

	@Override
	@AvailMethod @ThreadSafe
	A_Type o_BinUnionKind (final AvailObject object)
	{
		// Nil acts as an empty bin.
		return BottomTypeDescriptor.bottom();
	}

	@Override @AvailMethod @ThreadSafe
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.NIL;
	}

	@Override
	A_BasicObject o_MapBinAtHashPutLevelCanDestroy (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash,
		final A_BasicObject value,
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
	A_Type o_MapBinKeyUnionKind (final AvailObject object)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	A_Type o_MapBinValueUnionKind (final AvailObject object)
	{
		return BottomTypeDescriptor.bottom();
	}

	@Override
	AvailObject o_MapBinAtHash (
		final AvailObject object,
		final A_BasicObject key,
		final int keyHash)
	{
		return nil();
	}

	@Override
	int o_MapBinKeysHash (final AvailObject object)
	{
		return 0;
	}

	@Override
	int o_MapBinValuesHash (final AvailObject object)
	{
		return 0;
	}

	@Override
	boolean o_BinElementsAreAllInstancesOfKind (
		final AvailObject object,
		final A_Type kind)
	{
		// Nil is treated as an empty bin, so its members all satisfy.. any
		// property whatsoever.
		return true;
	}

	@Override
	MapIterable o_MapBinIterable (final AvailObject object)
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
	SetIterator o_SetBinIterator (final AvailObject object)
	{
		// Nil acts like a bin of size zero.
		return new SetDescriptor.SetIterator()
		{
			@Override
			public AvailObject next ()
			{
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean hasNext ()
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
	 * Construct a new {@link NilDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	protected NilDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link NilDescriptor}. */
	private static final NilDescriptor mutable =
		new NilDescriptor(Mutability.MUTABLE);

	@Override
	NilDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link NilDescriptor}. */
	private static final NilDescriptor immutable =
		new NilDescriptor(Mutability.IMMUTABLE);

	@Override
	NilDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link NilDescriptor}. */
	private static final NilDescriptor shared =
		new NilDescriptor(Mutability.SHARED);

	@Override
	NilDescriptor shared ()
	{
		return shared;
	}

	/** The sole instance of {@linkplain #nil() nil}. */
	private static final AvailObject soleInstance = shared.create();

	/**
	 * Answer the sole instance of nil.
	 *
	 * @return The sole instance of the nil.
	 */
	public static AvailObject nil ()
	{
		return soleInstance;
	}
}
