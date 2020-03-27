/*
 * MethodDefinitionDescriptor.java
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package com.avail.descriptor.methods;

import com.avail.annotations.AvailMethod;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.Mutability;
import com.avail.descriptor.ObjectSlotsEnum;
import com.avail.descriptor.functions.A_Function;
import com.avail.descriptor.functions.FunctionDescriptor;
import com.avail.descriptor.types.A_Type;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import static com.avail.descriptor.methods.MethodDefinitionDescriptor.ObjectSlots.*;
import static com.avail.descriptor.types.TypeDescriptor.Types.METHOD_DEFINITION;

/**
 * An object instance of {@code MethodDefinitionDescriptor} represents a
 * function in the collection of available functions for this method hierarchy.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class MethodDefinitionDescriptor
extends DefinitionDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * Duplicated from parent.  The method in which this definition occurs.
		 */
		DEFINITION_METHOD,

		/**
		 * The {@link ModuleDescriptor module} in which this definition occurs.
		 */
		MODULE,

		/**
		 * The {@linkplain FunctionDescriptor function} to invoke when this
		 * message is sent with applicable arguments.
		 */
		BODY_BLOCK;

		static
		{
			assert DefinitionDescriptor.ObjectSlots.DEFINITION_METHOD.ordinal()
				== DEFINITION_METHOD.ordinal();
			assert DefinitionDescriptor.ObjectSlots.MODULE.ordinal()
				== MODULE.ordinal();
		}
	}

	@Override @AvailMethod
	protected A_Type o_BodySignature (final AvailObject object)
	{
		return object.bodyBlock().kind();
	}

	@Override @AvailMethod
	protected A_Function o_BodyBlock (final AvailObject object)
	{
		return object.slot(BODY_BLOCK);
	}

	@Override @AvailMethod
	protected int o_Hash (final AvailObject object)
	{
		return (object.bodyBlock().hash() * 19) ^ 0x70B2B1A9;
	}

	@Override @AvailMethod
	protected A_Type o_Kind (final AvailObject object)
	{
		return METHOD_DEFINITION.o();
	}

	@Override @AvailMethod
	protected boolean o_IsMethodDefinition (final AvailObject object)
	{
		return true;
	}

	@Override
	protected SerializerOperation o_SerializerOperation (
		final AvailObject object)
	{
		return SerializerOperation.METHOD_DEFINITION;
	}

	@Override
	protected void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("method definition");
		writer.write("definition method");
		object.slot(DEFINITION_METHOD).methodName().writeTo(writer);
		writer.write("definition module");
		object.definitionModuleName().writeTo(writer);
		writer.write("body block");
		object.slot(BODY_BLOCK).writeTo(writer);
		writer.endObject();
	}

	@Override
	protected void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("method definition");
		writer.write("definition method");
		object.slot(DEFINITION_METHOD).methodName().writeTo(writer);
		writer.write("definition module");
		object.definitionModuleName().writeTo(writer);
		writer.write("body block");
		object.slot(BODY_BLOCK).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a new method signature from the provided arguments.
	 *
	 * @param definitionMethod
	 *            The {@linkplain MethodDescriptor method} for which to create
	 *            a new method definition.
	 * @param definitionModule
	 *            The module in which this definition is added.
	 * @param bodyBlock
	 *            The body of the signature.  This will be invoked when the
	 *            message is sent, assuming the argument types match and there
	 *            is no more specific version.
	 * @return
	 *            A method signature.
	 */
	public static AvailObject newMethodDefinition (
		final A_Method definitionMethod,
		final A_Module definitionModule,
		final A_Function bodyBlock)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(DEFINITION_METHOD, definitionMethod);
		instance.setSlot(MODULE, definitionModule);
		instance.setSlot(BODY_BLOCK, bodyBlock);
		instance.makeShared();
		return instance;
	}

	/**
	 * Construct a new {@link MethodDefinitionDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private MethodDefinitionDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, null);
	}

	/** The mutable {@link MethodDefinitionDescriptor}. */
	private static final MethodDefinitionDescriptor mutable =
		new MethodDefinitionDescriptor(Mutability.MUTABLE);

	@Override
	public MethodDefinitionDescriptor mutable ()
	{
		return mutable;
	}

	@Override
	public MethodDefinitionDescriptor immutable ()
	{
		return shared;
	}

	/** The shared {@link MethodDefinitionDescriptor}. */
	private static final MethodDefinitionDescriptor shared =
		new MethodDefinitionDescriptor(Mutability.SHARED);

	@Override
	public MethodDefinitionDescriptor shared ()
	{
		return shared;
	}
}
