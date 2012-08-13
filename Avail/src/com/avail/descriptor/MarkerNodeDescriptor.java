/**
 * MarkerNodeDescriptor.java
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

import static com.avail.descriptor.MarkerNodeDescriptor.ObjectSlots.*;
import static com.avail.descriptor.AvailObject.error;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.utility.*;

/**
 * My instances represent a parsing marker that can be pushed onto the parse
 * stack.  It should never occur as part of a composite {@linkplain
 * ParseNodeDescriptor parse node}, and is not capable of emitting code.
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
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain MarkerNodeDescriptor marker} being wrapped in a form
		 * suitable for the parse stack.
		 */
		MARKER_VALUE
	}


	/**
	 * Getter for field markerValue.
	 */
	@Override @AvailMethod
	AvailObject o_MarkerValue (
		final AvailObject object)
	{
		return object.slot(MARKER_VALUE);
	}


	@Override @AvailMethod
	AvailObject o_ExpressionType (final AvailObject object)
	{
		// This shouldn't make a difference.
		return TOP.o();
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		builder.append("Marker(");
		builder.append(object.markerValue());
		builder.append(")");
	}

	@Override @AvailMethod
	void o_EmitValueOn (
		final AvailObject object,
		final AvailCodeGenerator codeGenerator)
	{
		assert false : "A marker node can not generate code.";
	}


	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.markerValue().hash() ^ 0xCBCACACC;
	}

	@Override @AvailMethod
	boolean o_Equals (
		final AvailObject object,
		final AvailObject another)
	{
		return object.kind().equals(another.kind())
			&& object.markerValue().equals(another.markerValue());
	}

	@Override
	ParseNodeKind o_ParseNodeKind (
		final AvailObject object)
	{
		return MARKER_NODE;
	}

	@Override
	boolean o_IsMarkerNode (
		final AvailObject object)
	{
		return true;
	}

	@Override @AvailMethod
	void o_ChildrenMap (
		final AvailObject object,
		final Transformer1<AvailObject, AvailObject> aBlock)
	{
		error("Marker nodes should not be mapped.");
	}

	@Override @AvailMethod
	void o_ChildrenDo (
		final AvailObject object,
		final Continuation1<AvailObject> aBlock)
	{
		error("Marker nodes should not be iterated over.");
	}

	@Override @AvailMethod
	void o_ValidateLocally (
		final AvailObject object,
		final @Nullable AvailObject parent)
	{
		error("Marker nodes should not validateLocally.");
	}

	/**
	 * Create a {@linkplain MarkerNodeDescriptor marker node} wrapping the given
	 * {@link AvailObject}.
	 *
	 * @param markerValue The value to wrap.
	 * @return A new immutable marker node.
	 */
	public static AvailObject create (
		final AvailObject markerValue)
	{
		final AvailObject instance = mutable().create();
		instance.setSlot(MARKER_VALUE, markerValue);
		instance.makeImmutable();
		return instance;
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
	private static final MarkerNodeDescriptor mutable =
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
	private static final MarkerNodeDescriptor immutable =
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
