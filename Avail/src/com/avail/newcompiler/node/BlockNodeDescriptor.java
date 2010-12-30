/**
 * com.avail.newcompiler/BlockNodeDescriptor.java
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
import com.avail.interpreter.Primitive;

/**
 * My instances represent occurrences of blocks (closures) encountered in code.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class BlockNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The block's tuple of argument declarations.
		 */
		ARGUMENTS_TUPLE,

		/**
		 * The tuple of statements contained in this block.
		 */
		STATEMENTS_TUPLE,

		/**
		 * The type this block is expected to return an instance of.
		 */
		RESULT_TYPE,

		/**
		 * A tuple of variables needed by this block.  This is set after the
		 * {@linkplain BlockNodeDescriptor block node} has already been
		 * created.
		 */
		NEEDED_VARIABLES
	}

	/**
	 * My slots of type {@link Integer int}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum IntegerSlots
	{
		/**
		 * The {@link Primitive primitive} number to invoke for this block.
		 */
		PRIMITIVE
	}

	/**
	 * Setter for field argumentsTuple.
	 */
	@Override
	public void o_ArgumentsTuple (
		final AvailObject object,
		final AvailObject argumentsTuple)
	{
		object.objectSlotPut(ObjectSlots.ARGUMENTS_TUPLE, argumentsTuple);
	}

	/**
	 * Getter for field argumentsTuple.
	 */
	@Override
	public AvailObject o_ArgumentsTuple (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.ARGUMENTS_TUPLE);
	}

	/**
	 * Setter for field statementsTuple.
	 */
	@Override
	public void o_StatementsTuple (
		final AvailObject object,
		final AvailObject statementsTuple)
	{
		object.objectSlotPut(ObjectSlots.STATEMENTS_TUPLE, statementsTuple);
	}

	/**
	 * Getter for field statementsTuple.
	 */
	@Override
	public AvailObject o_StatementsTuple (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.STATEMENTS_TUPLE);
	}

	/**
	 * Setter for field resultType.
	 */
	@Override
	public void o_ResultType (
		final AvailObject object,
		final AvailObject resultType)
	{
		object.objectSlotPut(ObjectSlots.RESULT_TYPE, resultType);
	}

	/**
	 * Getter for field resultType.
	 */
	@Override
	public AvailObject o_ResultType (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.RESULT_TYPE);
	}

	/**
	 * Setter for field neededVariables.
	 */
	@Override
	public void o_NeededVariables (
		final AvailObject object,
		final AvailObject neededVariables)
	{
		object.objectSlotPut(ObjectSlots.NEEDED_VARIABLES, neededVariables);
	}

	/**
	 * Getter for field neededVariables.
	 */
	@Override
	public AvailObject o_NeededVariables (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.NEEDED_VARIABLES);
	}

	/**
	 * Setter for field primitive.
	 */
	@Override
	public void o_Primitive (
		final AvailObject object,
		final int primitive)
	{
		object.integerSlotPut(IntegerSlots.PRIMITIVE, primitive);
	}

	/**
	 * Getter for field primitive.
	 */
	@Override
	public int o_Primitive (
		final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.PRIMITIVE);
	}



	/**
	 * Construct a new {@link BlockNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public BlockNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link BlockNodeDescriptor}.
	 */
	private final static BlockNodeDescriptor mutable =
		new BlockNodeDescriptor(true);

	/**
	 * Answer the mutable {@link BlockNodeDescriptor}.
	 *
	 * @return The mutable {@link BlockNodeDescriptor}.
	 */
	public static BlockNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link BlockNodeDescriptor}.
	 */
	private final static BlockNodeDescriptor immutable =
		new BlockNodeDescriptor(false);

	/**
	 * Answer the immutable {@link BlockNodeDescriptor}.
	 *
	 * @return The immutable {@link BlockNodeDescriptor}.
	 */
	public static BlockNodeDescriptor immutable ()
	{
		return immutable;
	}

}
