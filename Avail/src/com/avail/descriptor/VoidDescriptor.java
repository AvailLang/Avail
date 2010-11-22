/**
 * descriptor/VoidDescriptor.java
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

import com.avail.annotations.NotNull;
import com.avail.annotations.ThreadSafe;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.descriptor.VoidDescriptor;
import java.util.List;

/**
 * {@code VoidDescriptor} implements the Avail {@linkplain #voidObject() void
 * object}, the sole instance of the invisible and uninstantiable root type.
 *
 * @author Mark van Gulik &lt;ghoul6@gmail.com&gt;
 */
public class VoidDescriptor
extends Descriptor
{
	/** The sole instance of the {@linkplain #voidObject() void object}. */
	private static AvailObject soleInstance;

	/**
	 * Create the sole instance of the {@linkplain #voidObject() void object}.
	 */
	static void createWellKnownObjects ()
	{
		soleInstance = AvailObject.newIndexedDescriptor(
			0, immutableDescriptor());
	}

	/**
	 * Discard the sole instance of the {@linkplain #voidObject() void object}.
	 */
	static void clearWellKnownObjects ()
	{
		soleInstance = null;
	}

	/**
	 * Answer the sole instance of the void object.
	 *
	 * @return The sole instance of the void object.
	 */
	@ThreadSafe
	public static @NotNull AvailObject voidObject ()
	{
		return soleInstance;
	}

	@Override
	@ThreadSafe
	public boolean ObjectEquals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.equalsVoid();
	}

	@Override
	@ThreadSafe
	public boolean ObjectEqualsVoid (final @NotNull AvailObject object)
	{
		//  There is only one void.
		return true;
	}

	@Override
	@ThreadSafe
	public boolean ObjectEqualsVoidOrBlank (final @NotNull AvailObject object)
	{
		// There is only one void.
		return true;
	}

	@Override
	@ThreadSafe
	public @NotNull AvailObject ObjectExactType (
		final @NotNull AvailObject object)
	{
		return Types.voidType.object();
	}

	@Override
	@ThreadSafe
	public int ObjectHash (final @NotNull AvailObject object)
	{
		// The void object should hash to zero, because the only place it can
		// appear in a data structure is as a filler object.  This currently
		// (as of July 1998) applies to sets, maps, containers, and
		// continuations.
		return 0;
	}

	@Override
	@ThreadSafe
	public @NotNull AvailObject ObjectType (final @NotNull AvailObject object)
	{
		return Types.voidType.object();
	}

	@Override
	public @NotNull AvailObject ObjectBinAddingElementHashLevelCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash,
		final byte myLevel,
		final boolean canDestroy)
	{
		// The voidObject can't be an actual member of a set, so if one
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
	public boolean ObjectIsBinSubsetOf (
		final @NotNull AvailObject object,
		final @NotNull AvailObject potentialSuperset)
	{
		// Void can't actually be a member of a set, so treat it as a
		// structural component indicating an empty bin within a set.
		// Since it's empty, it is a subset of potentialSuperset.
		return true;
	}

	@Override
	@ThreadSafe
	public @NotNull AvailObject ObjectBinRemoveElementHashCanDestroy (
		final @NotNull AvailObject object,
		final @NotNull AvailObject elementObject,
		final int elementObjectHash,
		final boolean canDestroy)
	{
		// The void object is acting as a bin of size zero, so the answer must
		// also be the void object.
		return VoidDescriptor.voidObject();
	}

	@Override
	@ThreadSafe
	public int ObjectPopulateTupleStartingAt (
		final @NotNull AvailObject object,
		final @NotNull AvailObject mutableTuple,
		final int startingIndex)
	{
		// The void object acts as an empty bin, so do nothing.
		assert mutableTuple.descriptor().isMutable();
		return startingIndex;
	}

	@Override
	@ThreadSafe
	public int ObjectBinHash (final @NotNull AvailObject object)
	{
		// The void object acting as a size-zero bin has a bin hash which is the
		// sum of the elements' hashes, which in this case is zero.
		return 0;
	}

	@Override
	@ThreadSafe
	public int ObjectBinSize (final @NotNull AvailObject object)
	{
		// The void object acts as an empty bin.
		return 0;
	}

	@Override
	@ThreadSafe
	public @NotNull AvailObject ObjectBinUnionType (final @NotNull AvailObject object)
	{
		// The void object acts as an empty bin.
		return Types.terminates.object();
	}

	@Override
	@ThreadSafe
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("VoidDescriptor void");
	}

	/**
	 * Construct a new {@link VoidDescriptor}.
	 *
	 * @param myId The id of the {@linkplain Descriptor descriptor}.
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected VoidDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	final static @NotNull VoidDescriptor mutableDescriptor =
		new VoidDescriptor(true);

	/**
	 * Answer a mutable {@link VoidDescriptor}.
	 *
	 * @return A mutable {@link VoidDescriptor}.
	 */
	@ThreadSafe
	public static VoidDescriptor mutableDescriptor ()
	{
		return mutableDescriptor;
	}

	final static VoidDescriptor immutableDescriptor =
		new VoidDescriptor(false);

	/**
	 * Answer an immutable {@link VoidDescriptor}.
	 *
	 * @return An immutable {@link VoidDescriptor}.
	 */
	@ThreadSafe
	public static @NotNull VoidDescriptor immutableDescriptor ()
	{
		return immutableDescriptor;
	}

}
