/**
 * MacroImplementationDescriptor.java
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

import static com.avail.descriptor.TypeDescriptor.Types.MACRO_SIGNATURE;
import static com.avail.descriptor.MacroImplementationDescriptor.ObjectSlots.*;
import com.avail.annotations.*;

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
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MacroImplementationDescriptor
extends ImplementationDescriptor
{
	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots implements ObjectSlotsEnum
	{
		/**
		 * The {@linkplain FunctionDescriptor function} to invoke to transform
		 * the argument parse nodes into a suitable replacement parse node.
		 */
		BODY_BLOCK,
	}

	/**
	 * Answer my signature.
	 */
	@Override @AvailMethod
	@NotNull AvailObject o_BodySignature (
		final @NotNull AvailObject object)
	{
		return object.bodyBlock().kind();
	}

	@Override @AvailMethod
	@NotNull AvailObject o_BodyBlock (
		final @NotNull AvailObject object)
	{
		return object.slot(BODY_BLOCK);
	}

	@Override @AvailMethod
	int o_Hash (
		final @NotNull AvailObject object)
	{
		final int hash = object.bodyBlock().hash() ^ 0x67f6ec56;
		return hash;
	}

	@Override @AvailMethod
	@NotNull AvailObject o_Kind (
		final @NotNull AvailObject object)
	{
		return MACRO_SIGNATURE.o();
	}

	@Override @AvailMethod
	boolean o_IsMacro (
		final @NotNull AvailObject object)
	{
		return true;
	}


	/**
	 * Create a new macro signature from the provided argument.
	 *
	 * @param bodyBlock
	 *            The body of the signature.  This will be invoked when a call
	 *            site is compiled, passing the sub<em>expressions</em> (
	 *            {@linkplain ParseNodeDescriptor parse nodes}) as arguments.
	 * @return
	 *            A macro signature.
	 */
	public static AvailObject create (
		final @NotNull AvailObject bodyBlock)
	{
		final AvailObject instance = mutable().create();
		instance.setSlot(BODY_BLOCK, bodyBlock);
		instance.makeImmutable();
		return instance;
	}


	/**
	 * Construct a new {@link MacroImplementationDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected MacroImplementationDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link MacroImplementationDescriptor}.
	 */
	private static final MacroImplementationDescriptor mutable =
		new MacroImplementationDescriptor(true);

	/**
	 * Answer the mutable {@link MacroImplementationDescriptor}.
	 *
	 * @return The mutable {@link MacroImplementationDescriptor}.
	 */
	public static MacroImplementationDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link MacroImplementationDescriptor}.
	 */
	private static final MacroImplementationDescriptor immutable =
		new MacroImplementationDescriptor(false);

	/**
	 * Answer the immutable {@link MacroImplementationDescriptor}.
	 *
	 * @return The immutable {@link MacroImplementationDescriptor}.
	 */
	public static MacroImplementationDescriptor immutable ()
	{
		return immutable;
	}
}
