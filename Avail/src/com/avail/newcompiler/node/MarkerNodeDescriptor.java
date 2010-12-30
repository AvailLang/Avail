/**
 * com.avail.newcompiler/MarkerNodeDescriptor.java
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

package com.avail.newcompiler.node;

import com.avail.descriptor.AvailObject;
import com.avail.oldcompiler.AvailParseNode;

/**
 * My instances represent a parsing marker that can be pushed onto the parse
 * stack.  It should never occur as part of a composite {@link AvailParseNode},
 * and is not capable of emitting code.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MarkerNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@link MarkerNodeDescriptor marker} being wrapped in a form
		 * suitable for the parse stack.
		 */
		MARKER_VALUE
	}


	/**
	 * Setter for field markerValue.
	 */
	@Override
	public void o_MarkerValue (
		final AvailObject object,
		final AvailObject markerValue)
	{
		object.objectSlotPut(ObjectSlots.MARKER_VALUE, markerValue);
	}

	/**
	 * Getter for field markerValue.
	 */
	@Override
	public AvailObject o_MarkerValue (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.MARKER_VALUE);
	}


	/**
	 * Construct a new {@link MarkerNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public MarkerNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link MarkerNodeDescriptor}.
	 */
	private final static MarkerNodeDescriptor mutable =
		new MarkerNodeDescriptor(true);

	/**
	 * Answer the mutable {@link MarkerNodeDescriptor}.
	 *
	 * @return The mutable {@link MarkerNodeDescriptor}.
	 */
	public static MarkerNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link MarkerNodeDescriptor}.
	 */
	private final static MarkerNodeDescriptor immutable =
		new MarkerNodeDescriptor(false);

	/**
	 * Answer the immutable {@link MarkerNodeDescriptor}.
	 *
	 * @return The immutable {@link MarkerNodeDescriptor}.
	 */
	public static MarkerNodeDescriptor immutable ()
	{
		return immutable;
	}

}
