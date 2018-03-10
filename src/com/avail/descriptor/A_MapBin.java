/*
 * A_MapBin.java
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

import com.avail.descriptor.MapDescriptor.MapIterable;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * {@code A_MapBin} is a collection of keys and their associated values, which
 * makes up some or part of a {@link A_Map map}.
 *
 * Bins below a particular scale ({@link
 * LinearMapBinDescriptor#thresholdToHash}) are usually represented via {@link
 * LinearMapBinDescriptor}, which is primarily an arbitrarily ordered
 * alternating sequence of keys and their associated values.  The hashes of the
 * keys are also stored for performance, among other things.
 *
 * <p>Above that threshold a {@link HashedMapBinDescriptor} is used, which
 * organizes the key-value pairs into a tree based on their hash values.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_MapBin
extends A_BasicObject
{
	boolean isHashedMapBin ();

	@Nullable AvailObject mapBinAtHash (
		final A_BasicObject key,
		final int keyHash);

	A_MapBin mapBinAtHashPutLevelCanDestroy (
		final A_BasicObject key,
		final int keyHash,
		final A_BasicObject value,
		final byte myLevel,
		final boolean canDestroy);

	MapIterable mapBinIterable ();

	int mapBinKeysHash ();

	A_Type mapBinKeyUnionKind ();

	A_MapBin mapBinRemoveKeyHashCanDestroy (
		final A_BasicObject key,
		final int keyHash,
		final boolean canDestroy);

	int mapBinSize ();

	int mapBinValuesHash ();

	A_Type mapBinValueUnionKind ();

	void forEachInMapBin (
		final BiConsumer<? super AvailObject, ? super AvailObject> action);
}
