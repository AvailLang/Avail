/**
 * DefinitionDescriptor.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * {@code DefinitionDescriptor} is an abstraction for things placed into a
 * {@linkplain MethodDescriptor method}.  They can be:
 * <ul>
 * <li>{@linkplain AbstractDefinitionDescriptor abstract declarations},</li>
 * <li>{@linkplain ForwardDefinitionDescriptor forward declarations},</li>
 * <li>{@linkplain MethodDefinitionDescriptor method definitions}, or</li>
 * <li>{@linkplain MacroDefinitionDescriptor macro definitions}.</li>
 * </ul>
 *
 * <p>
 * If a macro definition is present, it must be the only definition.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public abstract class DefinitionDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@link MethodDescriptor method} in which this definition occurs.
		 */
		DEFINITION_METHOD
	}

	@Override @AvailMethod
	public AvailObject o_DefinitionMethod (final AvailObject object)
	{
		return object.slot(ObjectSlots.DEFINITION_METHOD);
	}

	@Override @AvailMethod
	abstract A_Type o_BodySignature (final AvailObject object);

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	abstract int o_Hash (final AvailObject object);

	@Override @AvailMethod
	abstract A_Type o_Kind (final AvailObject object);

	@Override @AvailMethod
	boolean o_IsAbstractDefinition (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsForwardDefinition (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsMethodDefinition (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsMacroDefinition (final AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	abstract SerializerOperation o_SerializerOperation (
		final AvailObject object);

	/**
	 * Construct a new {@link DefinitionDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	protected DefinitionDescriptor (final Mutability mutability)
	{
		super(mutability);
	}
}
