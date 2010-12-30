/**
 * com.avail.newcompiler/VariableUseNodeDescriptor.java
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

import com.avail.descriptor.AvailObject;
import com.avail.newcompiler.scanner.TokenDescriptor;

/**
 * My instances represent the use of some {@link DeclarationNodeDescriptor
 * declared entity}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class VariableUseNodeDescriptor extends ParseNodeDescriptor
{
	/**
	 * My slots of type {@link AvailObject}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@link TokenDescriptor token} that is a mention of the entity
		 * in question.
		 */
		TOKEN,

		/**
		 * The {@link DeclarationNodeDescriptor declaration} of the entity that
		 * is being mentioned.
		 */
		DECLARATION
	}

	/**
	 * My slots of type {@link Integer int}.
	 *
	 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
	 */
	public enum IntegerSlots
	{
		/**
		 * A flag indicating (with 0/1) whether this is the last use of the
		 * mentioned entity.
		 */
		IS_LAST_USE
	}

	/**
	 * Setter for field token.
	 */
	@Override
	public void o_Token (
		final AvailObject object,
		final AvailObject token)
	{
		object.objectSlotPut(ObjectSlots.TOKEN, token);
	}

	/**
	 * Getter for field token.
	 */
	@Override
	public AvailObject o_Token (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.TOKEN);
	}

	/**
	 * Setter for field declaration.
	 */
	@Override
	public void o_Declaration (
		final AvailObject object,
		final AvailObject declaration)
	{
		object.objectSlotPut(ObjectSlots.DECLARATION, declaration);
	}

	/**
	 * Getter for field declaration.
	 */
	@Override
	public AvailObject o_Declaration (
		final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.DECLARATION);
	}


	/**
	 * Construct a new {@link VariableUseNodeDescriptor}.
	 *
	 * @param isMutable Whether my {@linkplain AvailObject instances} can
	 *                  change.
	 */
	public VariableUseNodeDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link VariableUseNodeDescriptor}.
	 */
	private final static VariableUseNodeDescriptor mutable =
		new VariableUseNodeDescriptor(true);

	/**
	 * Answer the mutable {@link VariableUseNodeDescriptor}.
	 *
	 * @return The mutable {@link VariableUseNodeDescriptor}.
	 */
	public static VariableUseNodeDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link VariableUseNodeDescriptor}.
	 */
	private final static VariableUseNodeDescriptor immutable =
		new VariableUseNodeDescriptor(false);

	/**
	 * Answer the immutable {@link VariableUseNodeDescriptor}.
	 *
	 * @return The immutable {@link VariableUseNodeDescriptor}.
	 */
	public static VariableUseNodeDescriptor immutable ()
	{
		return immutable;
	}

}
