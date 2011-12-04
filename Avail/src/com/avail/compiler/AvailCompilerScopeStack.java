/**
 * compiler/AvailCompilerScopeStack.java
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

package com.avail.compiler;

import com.avail.descriptor.*;

/**
 * An {@code AvailCompilerScopeStack} is an immutable collection of bindings
 * that are in scope at some point during the parsing of an Avail {@link
 * ParseNodeDescriptor expression}.  This collection can be nondestructively
 * extended simply by invoking the {@linkplain
 * AvailCompilerScopeStack#AvailCompilerScopeStack(AvailObject,
 * AvailCompilerScopeStack) constructor} with suitable arguments.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class AvailCompilerScopeStack
{
	/**
	 * The {@linkplain StringDescriptor name} of the most recent
	 * declaration.
	 */
	private AvailObject name;

	/**
	 * The most recent {@linkplain DeclarationNodeDescriptor declaration node}.
	 */
	private AvailObject declaration;

	/**
	 * The previous scope, i.e., the declarations that were in scope before the
	 * current one was added.
	 */
	private final AvailCompilerScopeStack next;


	/**
	 * Answer the most recently added {@linkplain DeclarationNodeDescriptor
	 * declaration}.
	 *
	 * @return The must recently added declaration.
	 */
	AvailObject declaration ()
	{
		return declaration;
	}

	/**
	 * Answer the {@linkplain StringDescriptor name} of the most recently
	 * added {@linkplain DeclarationNodeDescriptor declaration}.
	 *
	 * @return A string naming the most recently encountered declaration.
	 */
	AvailObject name ()
	{
		return name;
	}

	/**
	 * Answer the remainder of the {@linkplain AvailCompilerScopeStack scope
	 * stack} to be searched.
	 *
	 * @return The next AvailCompilerScopeStack to search.
	 */
	AvailCompilerScopeStack next ()
	{
		return next;
	}

	/**
	 * Look up the given {@linkplain StringDescriptor Avail string} to
	 * locate the {@linkplain DeclarationNodeDescriptor declaration} with the
	 * same name, or {@code null} if there is no such declaration.
	 *
	 * @param stack
	 *            The {@link AvailCompilerScopeStack} in which to look up the
	 *            name.
	 * @param nameToLookUp
	 *            The name of the declaration to look up.
	 * @return
	 *            The specified declaration node or null.
	 */
	static final AvailObject lookupDeclaration (
		final AvailCompilerScopeStack stack,
		final AvailObject nameToLookUp)
	{
		AvailCompilerScopeStack here = stack;
		while (here.name != null)
		{
			if (here.name().equals(nameToLookUp))
			{
				return here.declaration;
			}
			here = here.next;
		}
		return null;
	}



	/**
	 * Construct a new {@link AvailCompilerScopeStack} without affecting the
	 * existing declarations.
	 *
	 * @param newDeclaration
	 *            The {@linkplain DeclarationNodeDescriptor declaration} to
	 *            push.
	 * @param previousStack
	 *            The existing {@code AvailCompilerScopeStack} to extend.
	 */
	AvailCompilerScopeStack (
		final AvailObject newDeclaration,
		final AvailCompilerScopeStack previousStack)
	{
		if (newDeclaration == null)
		{
			declaration = null;
			name = null;
		}
		else
		{
			declaration = newDeclaration;
			name = newDeclaration.token().string();
			assert name.isString();
		}
		next = previousStack;
	}

	/**
	 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
	 */
	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append(getClass().getSimpleName());
		builder.append(" [");
		AvailCompilerScopeStack here = this;
		while (here != null)
		{
			builder.append(here.name != null ? here.name : "(null)");
			here = here.next;
			if (here != null)
			{
				builder.append(", ");
			}
		}
		builder.append(']');
		return builder.toString();
	}
}
