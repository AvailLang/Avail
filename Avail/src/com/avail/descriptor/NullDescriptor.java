/**
 * descriptor/NullDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
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

/**
 * {@code NullDescriptor} implements the Avail {@linkplain #nullObject() null
 * object}, the sole instance of the invisible and uninstantiable root type.
 *
 * @author Mark van Gulik &lt;ghoul6@gmail.com&gt;
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
	public static @NotNull AvailObject nullObject ()
	{
		return soleInstance;
	}

	@Override
	@ThreadSafe
	public boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsNull();
	}

	@Override
	@ThreadSafe
	public boolean o_EqualsNull (final @NotNull AvailObject object)
	{
		//  There is only one top.
		return true;
	}

	@Override
	@ThreadSafe
	public boolean o_EqualsNullOrBlank (final @NotNull AvailObject object)
	{
		// There is only one top.
		return true;
	}

	@Override
	@ThreadSafe
	public int o_Hash (final @NotNull AvailObject object)
	{
		// The null object should hash to zero, because the only place it can
		// appear in a data structure is as a filler object.  This currently
		// (as of July 1998) applies to sets, maps, containers, and
		// continuations.
		return 0;
	}

	@Override
	@ThreadSafe
	public @NotNull AvailObject o_Kind (final @NotNull AvailObject object)
	{
		return TOP.o();
	}

	@Override
	public @NotNull AvailObject o_BinAddingElementHashLevelCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
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
	@ThreadSafe
	public boolean o_IsBinSubsetOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialSuperset)
	{
		// Top can't actually be a member of a set, so treat it as a
		// structural component indicating an empty bin within a set.
		// Since it's empty, it is a subset of potentialSuperset.
		return true;
	}

	@Override
	@ThreadSafe
	public @NotNull AvailObject o_BinRemoveElementHashCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{
		// The null object is acting as a bin of size zero, so the answer must
		// also be the null object.
		return NullDescriptor.nullObject();
	}

	@Override
	@ThreadSafe
	public int o_PopulateTupleStartingAt (
		final @NotNull AvailObject object,
		final @NotNull AvailObject mutableTuple,
		final int startingIndex)
	{
		// The null object acts as an empty bin, so do nothing.
		assert mutableTuple.descriptor().isMutable();
		return startingIndex;
	}

	@Override
	@ThreadSafe
	public int o_BinHash (final @NotNull AvailObject object)
	{
		// The null object acting as a size-zero bin has a bin hash which is the
		// sum of the elements' hashes, which in this case is zero.
		return 0;
	}

	@Override
	@ThreadSafe
	public int o_BinSize (final @NotNull AvailObject object)
	{
		// The null object acts as an empty bin.
		return 0;
	}

	@Override
	@ThreadSafe
	public @NotNull AvailObject o_BinUnionKind (
		final @NotNull AvailObject object)
	{
		// The null object acts as an empty bin.
		return BottomTypeDescriptor.bottom();
	}

	@Override
	@ThreadSafe
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("Null");
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
	private final static @NotNull NullDescriptor mutable =
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
	private final static NullDescriptor immutable =
		new NullDescriptor(false);

	/**
	 * Answer an immutable {@link NullDescriptor}.
	 *
	 * @return An immutable {@link NullDescriptor}.
	 */
	@ThreadSafe
	public static @NotNull NullDescriptor immutable ()
	{
		return immutable;
	}
}
