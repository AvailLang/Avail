/**
 * descriptor/MacroSignatureDescriptor.java
 * Copyright (c) 2011, Mark van Gulik.
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

import static com.avail.descriptor.AvailObject.error;
import java.util.List;
import com.avail.compiler.node.TupleNodeDescriptor;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.Interpreter;

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
 * point in letting it have requires/returns clauses, so these are forbidden.
 * Instead, the macro is expected to throw a suitable exception if it is being
 * used in an incorrect or unsupported manner.</p>
 *
 * <p>As with methods, repeated arguments of macros are indicated with chevrons
 * («») and the double-dagger (‡).  The type of such an argument for a method is
 * a tuple of tuples whose elements correspond to the underscores (_) and
 * chevron groups contained therein.  When exactly one underscore or chevron
 * group occurs within a group, then a simple tuple of values is expected
 * (rather than a tuple of tuples).  Macros expect tuples in a similar way, but
 * the bottom-level pieces being passed are parse nodes rather than values.
 * Thus, a macro operates on parse nodes, and tuples of tuples and parse nodes.
 * Note how this is different from operating on {@link TupleNodeDescriptor tuple
 * <em>nodes</em>}, which are parse nodes which produce tuples of values.</p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class MacroSignatureDescriptor extends SignatureDescriptor
{
	// TODO: Implement a clean mechanism for throwing/catching compiler
	// exceptions related to rejecting malformed arguments, etc.

	/**
	 * The layout of object slots for my instances.
	 */
	public enum ObjectSlots
	{
		/**
		 * The {@link ClosureDescriptor closure} to invoke to transform the
		 * argument parse nodes (and tuples of tuples and parse nodes) into a
		 * suitable replacement parse node.
		 */
		BODY_BLOCK,
	}

	/**
	 * This operation is not appropriate for a macro.
	 */
	@Override
	public AvailObject o_ComputeReturnTypeFromArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final Interpreter anAvailInterpreter)
	{
		error("Do not ask for the return type of a macro.");
		return null;
	}

	/**
	 * While it may be considered appropriate for macros, don't actually invoke
	 * this for macros.  Otherwise there could be confusion when methods and
	 * macros are mixed within an {@link ImplementationSetDescriptor
	 * implementation set}, causing inappropriate "inherited" validity checks.
	 */
	@Override
	public boolean o_IsValidForArgumentTypesInterpreter (
			final AvailObject object,
			final List<AvailObject> argTypes,
			final Interpreter interpreter)
	{
		error("Do not check argument validity of a macro invocation.");
		return false;
	}

	/**
	 * Answer my signature.
	 */
	@Override
	public AvailObject o_BodySignature (
			final AvailObject object)
	{
		return object.bodyBlock().type();
	}


	/**
	 * Setter for field bodyBlock.
	 */
	@Override
	public void o_BodyBlock (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.BODY_BLOCK, value);
	}

	/**
	 * Setter for field requiresBlock.
	 */
	@Override
	public void o_RequiresBlock (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.REQUIRES_BLOCK, value);
	}

	/**
	 * Setter for field returnsBlock.
	 */
	@Override
	public void o_ReturnsBlock (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.RETURNS_BLOCK, value);
	}

	/**
	 * Getter for field bodyBlock.
	 */
	@Override
	public AvailObject o_BodyBlock (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.BODY_BLOCK);
	}

	/**
	 * Getter for field requiresBlock.
	 */
	@Override
	public AvailObject o_RequiresBlock (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.REQUIRES_BLOCK);
	}

	/**
	 * Getter for field returnsBlock.
	 */
	@Override
	public AvailObject o_ReturnsBlock (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.RETURNS_BLOCK);
	}



	// operations

	@Override
	public AvailObject o_ExactType (
			final AvailObject object)
	{
		//  Answer the object's type.  Don't answer an ApproximateType.

		return Types.methodSignature.object();
	}

	@Override
	public int o_Hash (
			final AvailObject object)
	{
		final int hash =
			object.bodyBlock().hash() * 19
			+ object.requiresBlock().hash() * 13
			+ object.returnsBlock().hash() * 3;
		return hash;
	}

	@Override
	public AvailObject o_Type (
			final AvailObject object)
	{
		return Types.methodSignature.object();
	}



	// testing

	@Override
	public boolean o_IsImplementation (
			final AvailObject object)
	{
		return true;
	}

	/**
	 * Construct a new {@link MacroSignatureDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected MacroSignatureDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}

	/**
	 * The mutable {@link MacroSignatureDescriptor}.
	 */
	private final static MacroSignatureDescriptor mutable =
		new MacroSignatureDescriptor(true);

	/**
	 * Answer the mutable {@link MacroSignatureDescriptor}.
	 *
	 * @return The mutable {@link MacroSignatureDescriptor}.
	 */
	public static MacroSignatureDescriptor mutable ()
	{
		return mutable;
	}

	/**
	 * The immutable {@link MacroSignatureDescriptor}.
	 */
	private final static MacroSignatureDescriptor immutable =
		new MacroSignatureDescriptor(false);

	/**
	 * Answer the immutable {@link MacroSignatureDescriptor}.
	 *
	 * @return The immutable {@link MacroSignatureDescriptor}.
	 */
	public static MacroSignatureDescriptor immutable ()
	{
		return immutable;
	}
}
