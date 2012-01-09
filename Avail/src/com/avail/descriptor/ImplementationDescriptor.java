/**
 * ImplementationDescriptor.java
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

package com.avail.descriptor;

import com.avail.annotations.*;

/**
 * {@code ImplementationDescriptor} is an abstraction for things placed into a
 * {@linkplain MethodDescriptor method}.  They can be:
 * <ul>
 * <li>{@linkplain AbstractDeclarationDescriptor abstract declarations},</li>
 * <li>{@linkplain ForwardDeclarationDescriptor forward declarations},</li>
 * <li>{@linkplain MethodImplementationDescriptor method implementations}, or</li>
 * <li>{@linkplain MacroImplementationDescriptor macro definitions}.</li>
 * </ul>
 *
 * <p>
 * If a macro definition is present, it must be the only signature.
 * </p>
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public abstract class ImplementationDescriptor
extends Descriptor
{
	@Override @AvailMethod
	abstract @NotNull AvailObject o_BodySignature (
		final @NotNull AvailObject object);

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Compare by address (identity) for now. Eventually we can introduce value
	 * semantics.
	 * </p>
	 */
	@Override @AvailMethod
	boolean o_Equals (
		final @NotNull AvailObject object,
		final @NotNull AvailObject another)
	{
		return another.traversed().sameAddressAs(object);
	}

	@Override @AvailMethod
	abstract int o_Hash (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	abstract @NotNull AvailObject o_Kind (
		final @NotNull AvailObject object);

	@Override @AvailMethod
	boolean o_IsAbstract (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsForward (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsMethod (
		final @NotNull AvailObject object)
	{
		return false;
	}

	@Override @AvailMethod
	boolean o_IsMacro (
		final @NotNull AvailObject object)
	{
		return false;
	}

	/**
	 * Construct a new {@link ImplementationDescriptor}.
	 *
	 * @param isMutable
	 *        Does the {@linkplain Descriptor descriptor} represent a mutable
	 *        object?
	 */
	protected ImplementationDescriptor (final boolean isMutable)
	{
		super(isMutable);
	}
}
