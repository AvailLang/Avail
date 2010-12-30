/**
 * com.avail.newcompiler/AssignmentNodeDescriptor.java
 * Copyright (c) 2010, Mark van Gulik.
 * All rights reserved.
 *
 * modification, are permitted provided that the following conditions are met:
 * Redistribution and use in source and binary forms, with or without
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

import com.avail.descriptor.*;

/**
 * My instances represent {@link ParseNodeDescriptor parse nodes} which will
 * generate tuples directly at runtime.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class TupleNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@link TupleDescriptor tuple} of {@link ParseNodeDescriptor
		 * parse nodes} that produce the values that will be aggregated into a
		 * tuple at runtime.
		 */
		EXPRESSIONS_TUPLE,

		/**
		 * The static type of the tuple that will be generated.
		 */
		TUPLE_TYPE
	}


	/**
	 * Setter for field expressionsTuple.
	 */
	@Override
	public void o_ExpressionsTuple (
		final AvailObject object,
		final AvailObject expressionsTuple)
	{
		object.objectSlotPut(ObjectSlots.EXPRESSIONS_TUPLE, expressionsTuple);
	}

	/**
	 * Getter for field expressionsTuple.
	 */
	@Override
	public AvailObject o_ExpressionsTuple (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.EXPRESSIONS_TUPLE);
	}

	/**
	 * Setter for field tupleType.
	 */
	@Override
	public void o_TupleType (
		final AvailObject object,
		final AvailObject tupleType)
	{
		object.objectSlotPut(ObjectSlots.TUPLE_TYPE, tupleType);
	}

	/**
	 * Getter for field tupleType.
	 */
	@Override
	public AvailObject o_TupleType (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.TUPLE_TYPE);
	}


	/**
	 * Construct a new {@link TupleNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public TupleNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link TupleNodeDescriptor}.
	 */
	private final static TupleNodeDescriptor mutable =
		new TupleNodeDescriptor(true);

	/**
	 * Answer the mutable {@link TupleNodeDescriptor}.
	 *
	 * @return The mutable {@link TupleNodeDescriptor}.
	 */
	public static TupleNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link TupleNodeDescriptor}.
	 */
	private final static TupleNodeDescriptor immutable =
		new TupleNodeDescriptor(false);

	/**
	 * Answer the immutable {@link TupleNodeDescriptor}.
	 *
	 * @return The immutable {@link TupleNodeDescriptor}.
	 */
	public static TupleNodeDescriptor immutable ()
	{
		return immutable;
	}

}
