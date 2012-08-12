/**
 * ForwardDeclarationDescriptor.java
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

import static com.avail.descriptor.ForwardDeclarationDescriptor.ObjectSlots.*;
import java.util.List;
import com.avail.annotations.*;
import com.avail.descriptor.TypeDescriptor.Types;

/**
 * This is a forward declaration of a method.  An actual method must be declared
 * with the same signature before the end of the current module.
 *
 * <p>While a call to this method signature can be compiled after the forward
 * declaration, an attempt to actually call the method will result in an error
 * indicating this problem.</p>
 *
 * <p>Because of the nature of forward declarations, it is meaningless to
 * forward declare a macro, so this facility is not provided.  It's
 * meaningless because a "call-site" for a macro causes the body to execute
 * immediately.</p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class ForwardDeclarationDescriptor
extends ImplementationDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain MethodDescriptor method} {@linkplain AtomDescriptor
		 * name}.
		 */
		METHOD_NAME,

		/**
		 * The signature being forward-declared.  This is a {@linkplain
		 * FunctionTypeDescriptor function type}.
		 */
		BODY_SIGNATURE
	}

	@Override @AvailMethod
	AvailObject o_BodySignature (final AvailObject object)
	{
		return object.slot(BODY_SIGNATURE);
	}

	@Override @AvailMethod
	AvailObject o_Signature (final AvailObject object)
	{
		return object.slot(BODY_SIGNATURE);
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return object.slot(BODY_SIGNATURE).hash() * 19
			^ object.slot(METHOD_NAME).hash() * 757;
	}

	@Override @AvailMethod
	AvailObject o_Kind (
		final AvailObject object)
	{
		return Types.FORWARD_SIGNATURE.o();
	}

	@Override @AvailMethod
	boolean o_IsForward (final AvailObject object)
	{
		return true;
	}

	@Override
	void printObjectOnAvoidingIndent (
		final AvailObject object,
		final StringBuilder builder,
		final List<AvailObject> recursionList,
		final int indent)
	{
		object.slot(METHOD_NAME).printOnAvoidingIndent(
			builder, recursionList, indent);
		builder.append(' ');
		object.slot(BODY_SIGNATURE).printOnAvoidingIndent(
			builder, recursionList, indent + 1);
	}

	/**
	 * Create a forward declaration signature for the given {@linkplain
	 * MethodDescriptor method} {@linkplain AtomDescriptor name} and {@linkplain
	 * FunctionTypeDescriptor function type}.
	 *
	 * @param methodName
	 *        The method name.
	 * @param bodySignature
	 *        The function type at which this signature should occur within
	 *        a {@linkplain MethodDescriptor method}.
	 * @return The new forward declaration signature.
	 */
	public static AvailObject create (
		final AvailObject methodName,
		final AvailObject bodySignature)
	{
		final AvailObject instance = mutable().create();
		instance.setSlot(ObjectSlots.METHOD_NAME, methodName);
		instance.setSlot(ObjectSlots.BODY_SIGNATURE, bodySignature);
		instance.makeImmutable();
		return instance;
	}

	/**
	 * Construct a new {@link ForwardDeclarationDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ForwardDeclarationDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link ForwardDeclarationDescriptor}.
	 */
	private static final ForwardDeclarationDescriptor mutable =
		new ForwardDeclarationDescriptor(true);

	/**
	 * Answer the mutable {@link ForwardDeclarationDescriptor}.
	 *
	 * @return The mutable {@link ForwardDeclarationDescriptor}.
	 */
	public static ForwardDeclarationDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link ForwardDeclarationDescriptor}.
	 */
	private static final ForwardDeclarationDescriptor immutable =
		new ForwardDeclarationDescriptor(false);

	/**
	 * Answer the immutable {@link ForwardDeclarationDescriptor}.
	 *
	 * @return The immutable {@link ForwardDeclarationDescriptor}.
	 */
	public static ForwardDeclarationDescriptor immutable ()
	{
		return immutable;
	}
}
