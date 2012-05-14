/**
 * FillerDescriptor.java
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

import java.util.List;
import com.avail.annotations.NotNull;

/**
 * {@code FillerDescriptor} represents an unreachable {@link AvailObject} of
 * arbitrary size. It exists solely to occupy dead space during an object
 * traversal <em>(not implemented in Java as of 2010.11.17)</em>.
 */
public class FillerDescriptor
extends Descriptor
{
	@Override
	public void printObjectOnAvoidingIndent (
		final @NotNull AvailObject object,
		final @NotNull StringBuilder builder,
		final @NotNull List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("(*** a destroyed object ***)");
	}


	/**
	 * Construct a new {@link FillerDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected FillerDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * A mutable {@link FillerDescriptor}.
	 */
	final private static FillerDescriptor mutable = new FillerDescriptor(true);

	/**
	 * Answer a mutable {@link FillerDescriptor}.
	 *
	 * @return A mutable {@link FillerDescriptor}.
	 */
	public static FillerDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * An immutable {@link FillerDescriptor}.
	 */
	final private static FillerDescriptor immutable = new FillerDescriptor(false);

	/**
	 * Answer an immutable {@link FillerDescriptor}.
	 *
	 * @return An immutable {@link FillerDescriptor}.
	 */
	public static FillerDescriptor immutable ()
	{
		return immutable;
	}
}
