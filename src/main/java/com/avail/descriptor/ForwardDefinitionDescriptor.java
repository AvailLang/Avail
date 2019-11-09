/*
 * ForwardDefinitionDescriptor.java
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

import com.avail.annotations.AvailMethod;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.serialization.SerializerOperation;
import com.avail.utility.json.JSONWriter;

import java.util.IdentityHashMap;

import static com.avail.descriptor.ForwardDefinitionDescriptor.ObjectSlots.BODY_SIGNATURE;
import static com.avail.descriptor.ForwardDefinitionDescriptor.ObjectSlots.DEFINITION_METHOD;
import static com.avail.descriptor.ForwardDefinitionDescriptor.ObjectSlots.MODULE;

/**
 * This is a forward declaration of a method.  An actual method must be defined
 * with the same signature before the end of the current module.
 *
 * <p>While a call with this method signature can be compiled after the forward
 * declaration, an attempt to actually call the method will result in an error
 * indicating this problem.</p>
 *
 * <p>Because of the nature of forward declarations, it is meaningless to
 * forward declare a macro, so this facility is not provided.  It's
 * meaningless because a "call-site" for a macro causes the body to execute
 * immediately.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class ForwardDefinitionDescriptor
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
		 * The {@linkplain FunctionTypeDescriptor function type} for which this
		 * signature is being specified.
		 */
		BODY_SIGNATURE;

		static
		{
			assert DefinitionDescriptor.ObjectSlots.DEFINITION_METHOD.ordinal()
				== DEFINITION_METHOD.ordinal();
			assert DefinitionDescriptor.ObjectSlots.MODULE.ordinal()
				== MODULE.ordinal();
		}
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final IdentityHashMap<A_BasicObject, Void> recursionMap,
		final int indent)
	{
		object.slot(DEFINITION_METHOD).chooseBundle(object.slot(MODULE))
			.message()
			.printOnAvoidingIndent(builder, recursionMap, indent);
		builder.append(' ');
		object.slot(BODY_SIGNATURE).printOnAvoidingIndent(
			builder, recursionMap, indent + 1);
	}

	@Override @AvailMethod
	A_Type o_BodySignature (final AvailObject object)
	{
		return object.slot(BODY_SIGNATURE);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(BODY_SIGNATURE).hash() * 19
			^ object.slot(DEFINITION_METHOD).hash() * 757;
	}

	@Override @AvailMethod
	A_Type o_Kind (final AvailObject object)
	{
		return Types.FORWARD_DEFINITION.o();
	}

	@Override @AvailMethod
	boolean o_IsForwardDefinition (final AvailObject object)
	{
		return true;
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.FORWARD_DEFINITION;
	}

	@Override
	void o_WriteTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("forward definition");
		writer.write("definition method");
		object.slot(DEFINITION_METHOD).methodName().writeTo(writer);
		writer.write("definition module");
		object.definitionModuleName().writeTo(writer);
		writer.write("body signature");
		object.slot(BODY_SIGNATURE).writeTo(writer);
		writer.endObject();
	}

	@Override
	void o_WriteSummaryTo (final AvailObject object, final JSONWriter writer)
	{
		writer.startObject();
		writer.write("kind");
		writer.write("forward definition");
		writer.write("definition method");
		object.slot(DEFINITION_METHOD).methodName().writeTo(writer);
		writer.write("definition module");
		object.definitionModuleName().writeTo(writer);
		writer.write("body signature");
		object.slot(BODY_SIGNATURE).writeSummaryTo(writer);
		writer.endObject();
	}

	/**
	 * Create a forward declaration signature for the given {@linkplain
	 * MethodDescriptor method} and {@linkplain FunctionTypeDescriptor function
	 * type}.
	 *
	 * @param definitionMethod
	 *        The method for which to declare a forward definition.
	 * @param definitionModule
	 *            The module in which this definition is added.
	 * @param bodySignature
	 *        The function type at which this forward definition should occur.
	 * @return The new forward declaration signature.
	 */
	public static AvailObject newForwardDefinition (
		final A_BasicObject definitionMethod,
		final A_Module definitionModule,
		final A_Type bodySignature)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(DEFINITION_METHOD, definitionMethod);
		instance.setSlot(MODULE, definitionModule);
		instance.setSlot(BODY_SIGNATURE, bodySignature);
		instance.makeShared();
		return instance;
	}

	/**
	 * Construct a new {@link ForwardDefinitionDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private ForwardDefinitionDescriptor (final Mutability mutability)
	{
		super(mutability, ObjectSlots.class, null);
	}

	/** The mutable {@link ForwardDefinitionDescriptor}. */
	private static final ForwardDefinitionDescriptor mutable =
		new ForwardDefinitionDescriptor(Mutability.MUTABLE);

	@Override
	ForwardDefinitionDescriptor mutable ()
	{
		return mutable;
	}

	@Override
	ForwardDefinitionDescriptor immutable ()
	{
		// There is no immutable variant.
		return shared;
	}

	/** The shared {@link ForwardDefinitionDescriptor}. */
	private static final ForwardDefinitionDescriptor shared =
		new ForwardDefinitionDescriptor(Mutability.SHARED);

	@Override
	ForwardDefinitionDescriptor shared ()
	{
		return shared;
	}
}
