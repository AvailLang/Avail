/**
 * SemanticRestrictionDescriptor.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

import static com.avail.descriptor.SemanticRestrictionDescriptor.ObjectSlots.*;

import com.avail.annotations.AvailMethod;
import com.avail.interpreter.primitive.phrases.P_RejectParsing;

/**
 * A {@code SemanticRestrictionDescriptor semantic restriction} holds a function
 * to invoke when <em>compiling</em> a potential call site of a method.  The
 * arguments' static types at the call site are passed to the function, and if
 * successful it produces a result type used to further restrict the expected
 * type at that call site.  Or instead it may invoke {@link P_RejectParsing}
 * to cause that call site to be rejected as a possible parse, supplying a
 * message describing the nature of the rejection.  The message will be shown to
 * the user if no significant parsing beyond this point in the source file was
 * possible.  Raising an unhandled exception during execution of the semantic
 * restriction will similarly be wrapped with a suitable error string and
 * possibly a stack trace, leading to a parse rejection just as with the
 * explicit rejection primitive.
 *
 * <p>
 * If a semantic restriction's function parameters are not general enough to
 * accept the actual arguments (the static types of the arguments at the call
 * site), then simply don't run that semantic restriction.
 * </p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class SemanticRestrictionDescriptor
extends Descriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	implements ObjectSlotsEnum
	{
		/**
		 * The {@link FunctionDescriptor function} to invoke to determine the
		 * static suitability of the arguments at this call site, and to compute
		 * a stronger type bound for the call site's result type.
		 */
		FUNCTION,

		/**
		 * The {@link MethodDescriptor method} for which this is a semantic
		 * restriction.
		 */
		DEFINITION_METHOD,

		/**
		 * The {@link ModuleDescriptor module} in which this semantic
		 * restriction was added.
		 */
		DEFINITION_MODULE;
	}

	@Override @AvailMethod
	int o_Hash (final AvailObject object)
	{
		return (object.slot(FUNCTION).hash() ^ 0x0E0D9C10)
			+ object.slot(DEFINITION_METHOD).hash();
	}

	@Override @AvailMethod
	A_Function o_Function(final AvailObject object)
	{
		return object.slot(FUNCTION);
	}

	@Override
	A_Method o_DefinitionMethod (final AvailObject object)
	{
		return object.slot(DEFINITION_METHOD);
	}

	@Override @AvailMethod
	A_Module o_DefinitionModule (final AvailObject object)
	{
		return object.slot(DEFINITION_MODULE);
	}

	@Override @AvailMethod
	boolean o_Equals (final AvailObject object, final A_BasicObject another)
	{
		// Compare by identity.
		return object.sameAddressAs(another);
	}

	/**
	 * Construct a new {@link SemanticRestrictionDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	private SemanticRestrictionDescriptor (final Mutability mutability)
	{
		super(
			mutability,
			TypeTag.SEMANTIC_RESTRICTION_TAG,
			ObjectSlots.class,
			null);
	}

	/** The mutable {@link SemanticRestrictionDescriptor}. */
	private final static SemanticRestrictionDescriptor mutable =
		new SemanticRestrictionDescriptor(Mutability.MUTABLE);

	@Override
	SemanticRestrictionDescriptor mutable ()
	{
		return mutable;
	}

	/** The shared {@link SemanticRestrictionDescriptor}. */
	private final static SemanticRestrictionDescriptor shared =
		new SemanticRestrictionDescriptor(Mutability.SHARED);

	@Override
	SemanticRestrictionDescriptor immutable ()
	{
		// There is no immutable variant; answer the shared descriptor.
		return shared;
	}

	@Override
	SemanticRestrictionDescriptor shared ()
	{
		return shared;
	}

	/**
	 * Create a new {@linkplain SemanticRestrictionDescriptor semantic
	 * restriction} with the specified information.  Make it {@link
	 * Mutability#SHARED SHARED}.
	 *
	 * @param function
	 *            The {@linkplain FunctionDescriptor function} to run against a
	 *            call site's argument types.
	 * @param method
	 *            The {@linkplain MethodDescriptor method} for which this
	 *            semantic restriction applies.
	 * @param module
	 *            The {@linkplain ModuleDescriptor module} in which this
	 *            semantic restriction was defined.
	 * @return The new semantic restriction.
	 */
	public static A_SemanticRestriction create (
		final A_Function function,
		final A_Method method,
		final A_Module module)
	{
		final AvailObject result = mutable.create();
		result.setSlot(FUNCTION, function);
		result.setSlot(DEFINITION_METHOD, method);
		result.setSlot(DEFINITION_MODULE, module);
		result.makeShared();
		return result;
	}
}
