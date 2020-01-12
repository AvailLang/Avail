/*
 * ContinuationRegisterDumpDescriptor.java
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

import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.optimizer.jvm.CheckedMethod;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AvailObject.newObjectIndexedIntegerIndexedDescriptor;
import static com.avail.descriptor.ContinuationRegisterDumpDescriptor.IntegerSlots.INTEGER_SLOTS_;
import static com.avail.descriptor.ContinuationRegisterDumpDescriptor.ObjectSlots.OBJECT_SLOTS_;
import static com.avail.optimizer.jvm.CheckedMethod.javaLibraryStaticMethod;
import static com.avail.optimizer.jvm.CheckedMethod.staticMethod;

/**
 * A {@code ContinuationRegisterDumpDescriptor} instance holds a collection of
 * {@link AvailObject} and {@code long} slots for use by an {@link L2Chunk}.
 * It's typically stored in the
 * {@link ContinuationDescriptor.ObjectSlots#LEVEL_TWO_REGISTER_DUMP} slot of
 * a {@link ContinuationDescriptor continuation}.  The interpretation of its
 * fields depends on the {@link L2Chunk} that's both creating and consuming it.
 *
 * @author Mark van Gulik&lt;mark@availlang.org&gt;
 */
public final class ContinuationRegisterDumpDescriptor
extends Descriptor
{
	/**
	 * The layout of integer slots for my instances.
	 */
	public enum IntegerSlots
	implements IntegerSlotsEnum
	{
		/**
		 * A vector of {@code long} slots, to be interpreted by the
		 * {@link L2Chunk} that both creates and consumes it.
		 */
		INTEGER_SLOTS_;
	}

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * A vector of {@code AvailObject} slots, to be interpreted by the
		 * {@link L2Chunk} that both creates and consumes it.
		 */
		OBJECT_SLOTS_;
	}

	@Override
	public boolean o_ShowValueInNameForDebugger (final AvailObject object)
	{
		return false;
	}

	/**
	 * Create a new continuation with the given data.  The continuation should
	 * represent the state upon entering the new context - i.e., set the pc to
	 * the first instruction, clear the stack, and set up new local variables.
	 *
	 * @param objects
	 *        The array of {@link AvailObject}s to capture.
	 * @param longs
	 *        The array of {@code long}s to capture.
	 * @return The new register dump object.
	 */
	@ReferencedInGeneratedCode
	public static AvailObject createRegisterDump (
		final AvailObject[] objects,
		final long[] longs)
	{
		final AvailObject dump = newObjectIndexedIntegerIndexedDescriptor(
			objects.length, longs.length, mutable);
		dump.setSlotsFromArray(OBJECT_SLOTS_, 1, objects, 0, objects.length);
		dump.setSlotsFromArray(INTEGER_SLOTS_, 1, longs, 0, longs.length);
		return dump;
	}

	/** Access the method {@link #createRegisterDump(AvailObject[], long[])}. */
	public static CheckedMethod createRegisterDumpMethod = staticMethod(
		ContinuationRegisterDumpDescriptor.class,
		"createRegisterDump",
		AvailObject.class,
		AvailObject[].class,
		long[].class);

	/**
	 * Given a continuation register dump, extract the object at the given slot
	 * index.
	 *
	 * @param dump
	 *        The continuation register dump to extract from.
	 * @param index
	 *        The index at which to extract an {@link AvailObject}.
	 * @return The extracted {@link AvailObject}.
	 */
	@ReferencedInGeneratedCode
	public static AvailObject extractObjectAt (
		final AvailObject dump,
		final int index)
	{
		return dump.slot(OBJECT_SLOTS_, index);
	}

	/**
	 * A {@link CheckedMethod} for invoking the static method
	 * {@link #extractObjectAt(AvailObject, int)}.
	 */
	public static CheckedMethod extractObjectAtMethod = staticMethod(
		ContinuationRegisterDumpDescriptor.class,
		"extractObjectAt",
		AvailObject.class,
		AvailObject.class,
		int.class);

	/**
	 * Given a continuation register dump, extract the {@code long} at the given
	 * slot index.
	 *
	 * @param dump
	 *        The continuation register dump to extract from.
	 * @param index
	 *        The index at which to extract a {@code long}.
	 * @return The extracted {@code long}.
	 */
	@ReferencedInGeneratedCode
	public static long extractLongAt (
		final AvailObject dump,
		final int index)
	{
		return dump.slot(INTEGER_SLOTS_, index);
	}

	/**
	 * A {@link CheckedMethod} for invoking the static method
	 * {@link #extractLongAt(AvailObject, int)}.
	 */
	public static CheckedMethod extractLongAtMethod = staticMethod(
		ContinuationRegisterDumpDescriptor.class,
		"extractLongAt",
		long.class,
		AvailObject.class,
		int.class);

	/**
	 * Construct a new {@code ContinuationDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ContinuationRegisterDumpDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.UNKNOWN_TAG,
			ObjectSlots.class,
			IntegerSlots.class);
	}

	/** The mutable {@link ContinuationRegisterDumpDescriptor}. */
	private static final ContinuationRegisterDumpDescriptor mutable =
		new ContinuationRegisterDumpDescriptor(Mutability.MUTABLE);

	@Override
	protected ContinuationRegisterDumpDescriptor mutable ()
	{
		return mutable;
	}

	/** The immutable {@link ContinuationRegisterDumpDescriptor}. */
	private static final ContinuationRegisterDumpDescriptor immutable =
		new ContinuationRegisterDumpDescriptor(Mutability.IMMUTABLE);

	@Override
	protected ContinuationRegisterDumpDescriptor immutable ()
	{
		return immutable;
	}

	/** The shared {@link ContinuationRegisterDumpDescriptor}. */
	private static final ContinuationRegisterDumpDescriptor shared =
		new ContinuationRegisterDumpDescriptor(Mutability.SHARED);

	@Override
	protected ContinuationRegisterDumpDescriptor shared ()
	{
		return shared;
	}
}
