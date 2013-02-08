/**
 * MacroDefinitionDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.MACRO_DEFINITION;
import static com.avail.descriptor.MacroDefinitionDescriptor.ObjectSlots.*;
import com.avail.annotations.*;
import com.avail.serialization.SerializerOperation;

/**
 * Macros are extremely hygienic in Avail.  They are defined almost exactly like
 * ordinary multimethods.  The first difference is which primitive is used to
 * define a macro versus a method.  The other difference is that instead of
 * generating code at an occurrence to call a method (a call site), the macro
 * body is immediately invoked, passing the parse nodes that occupy the
 * corresponding argument positions in the method/macro name.  The macro body
 * will then do what it does and return a suitable parse node.
 *
 * <p>Since the macro is essentially a compile-time construct, there's not much
 * point in letting it have semantic restrictions, so these are forbidden.
 * Instead, the macro is expected to throw a suitable exception if it is being
 * used in an incorrect or unsupported manner.</p>
 *
 * <p>As with methods, repeated arguments of macros are indicated with
 * guillemets («») and the double-dagger (‡).  The type of such an argument for
 * a method is a tuple of tuples whose elements correspond to the underscores
 * (_) and guillemet groups contained therein.  When exactly one underscore or
 * guillemet group occurs within a group, then a simple tuple of values is
 * expected (rather than a tuple of tuples).  Macros expect tuples in an
 * analogous way, but (1) the bottom-level pieces are always parse nodes, and
 * (2) the grouping is actually via {@link ListNodeDescriptor list nodes} rather
 * than tuples.  Thus, a macro always operates on parse nodes.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class MacroDefinitionDescriptor
extends DefinitionDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * Duplicated from parent.  The method in which this definition occurs.
		 */
		DEFINITION_METHOD,

		/**
		 * The {@linkplain FunctionDescriptor function} to invoke to transform
		 * the argument parse nodes into a suitable replacement parse node.
		 */
		BODY_BLOCK;

		static
		{
			assert DefinitionDescriptor.ObjectSlots.DEFINITION_METHOD.ordinal()
				== DEFINITION_METHOD.ordinal();
		}
	}

	/**
	 * Answer my signature.
	 */
	@Override @AvailMethod
	AvailObject o_BodySignature (
		final AvailObject object)
	{
		return object.bodyBlock().kind();
	}

	@Override @AvailMethod
	AvailObject o_BodyBlock (
		final AvailObject object)
	{
		return object.slot(BODY_BLOCK);
	}

	@Override @AvailMethod
	int o_Hash (
		final AvailObject object)
	{
		final int hash = object.bodyBlock().hash() ^ 0x67f6ec56;
		return hash;
	}

	@Override @AvailMethod
	AvailObject o_Kind (
		final AvailObject object)
	{
		return MACRO_DEFINITION.o();
	}

	@Override @AvailMethod
	boolean o_IsMacroDefinition (
		final AvailObject object)
	{
		return true;
	}

	@Override
	SerializerOperation o_SerializerOperation (final AvailObject object)
	{
		return SerializerOperation.MACRO_DEFINITION;
	}


	/**
	 * Create a new macro signature from the provided argument.
	 *
	 * @param method
	 *            The {@linkplain MethodDescriptor method} in which to define
	 *            this macro.
	 * @param bodyBlock
	 *            The body of the signature.  This will be invoked when a call
	 *            site is compiled, passing the sub<em>expressions</em> (
	 *            {@linkplain ParseNodeDescriptor parse nodes}) as arguments.
	 * @return
	 *            A macro signature.
	 */
	public static AvailObject create (
		final AvailObject method,
		final AvailObject bodyBlock)
	{
		final AvailObject instance = mutable.create();
		instance.setSlot(DEFINITION_METHOD, method);
		instance.setSlot(BODY_BLOCK, bodyBlock);
		instance.makeShared();
		return instance;
	}

	/**
	 * Construct a new {@link MacroDefinitionDescriptor}.
	 *
	 * @param mutability
	 *        The {@linkplain Mutability mutability} of the new descriptor.
	 */
	protected MacroDefinitionDescriptor (final Mutability mutability)
	{
		super(mutability);
	}

	/** The mutable {@link MacroDefinitionDescriptor}. */
	private static final MacroDefinitionDescriptor mutable =
		new MacroDefinitionDescriptor(Mutability.MUTABLE);

	@Override
	MacroDefinitionDescriptor mutable ()
	{
		return mutable;
	}

	@Override
	MacroDefinitionDescriptor immutable ()
	{
		// There is no immutable variant.
		return shared;
	}

	/** The shared {@link MacroDefinitionDescriptor}. */
	private static final MacroDefinitionDescriptor shared =
		new MacroDefinitionDescriptor(Mutability.SHARED);

	@Override
	MacroDefinitionDescriptor shared ()
	{
		return shared;
	}
}
