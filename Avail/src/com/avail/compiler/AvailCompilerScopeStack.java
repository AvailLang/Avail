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

import com.avail.descriptor.AvailObject;
import com.avail.oldcompiler.AvailVariableDeclarationNode;

public class AvailCompilerScopeStack
{
	AvailObject _name;
	AvailVariableDeclarationNode _declaration;
	AvailCompilerScopeStack _next;


	// accessing

	AvailVariableDeclarationNode declaration ()
	{
		return _declaration;
	}

	void declaration (
			final AvailVariableDeclarationNode anAvailVariableDeclarationNode)
	{

		_declaration = anAvailVariableDeclarationNode;
	}

	AvailObject name ()
	{
		return _name;
	}

	void name (
			final AvailObject availString)
	{

		_name = availString;
	}

	AvailCompilerScopeStack next ()
	{
		return _next;
	}

	void next (
			final AvailCompilerScopeStack anAvailCompilerScopeStack)
	{

		_next = anAvailCompilerScopeStack;
	}





	// Constructor

	AvailCompilerScopeStack (
		final AvailVariableDeclarationNode declaration,
		final AvailCompilerScopeStack next)
	{
		if (declaration == null)
		{
			_declaration = null;
			_name = null;
		}
		else
		{
			_declaration = declaration;
			_name = declaration.name().string();
			assert _name.isString();
		}
		_next = next;
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
		AvailCompilerScopeStack next = this;
		while (next != null)
		{
			builder.append(next._name != null ? next._name : "(null)");
			next = next._next;
			if (next != null)
			{
				builder.append(", ");
			}
		}
		builder.append(']');
		return builder.toString();
	}
}
