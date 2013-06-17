/**
 * Scope.java
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

package com.avail.interpreter.jvm;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A {@code Scope} bounds the lifetime of one or more {@linkplain LocalVariable
 * local variables}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class Scope
{
	/**
	 * The local variable index, measured in {@linkplain
	 * LocalVariable#slotUnits() slot units}.
	 */
	private int localIndex;

	/**
	 * Construct a new {@link Scope}.
	 *
	 * @param localIndex
	 *        The local variable index, measured in {@linkplain
	 *        LocalVariable#slotUnits() slot units}.
	 */
	Scope (final int localIndex)
	{
		this.localIndex = localIndex;
	}

	/**
	 * Answer a new {@linkplain Scope scope} that is nested within the receiver.
	 *
	 * @return A new scope.
	 */
	Scope newInnerScope ()
	{
		return new Scope(localIndex);
	}

	/** A {@linkplain Map map} from local variable names to indices. */
	private final Set<LocalVariable> localVariables = new HashSet<>();

	/**
	 * Answer a new {@linkplain LocalVariable local variable} in this
	 * {@linkplain Scope scope}, reusing free slots.
	 *
	 * @param name
	 *        The name of the local variable.
	 * @param type
	 *        The {@linkplain Class type} of the local variable. This must be
	 *        either a {@linkplain Class#isPrimitive() primitive} type or
	 *        {@link Object Object.class} for reference or return address types.
	 * @return A new local variable.
	 */
	LocalVariable newLocalVariable (final String name, final Class<?> type)
	{
		final LocalVariable local = new LocalVariable(name, type, localIndex);
		localIndex += LocalVariable.slotUnits(type);
		localVariables.add(local);
		return local;
	}

	/**
	 * Exit this {@linkplain Scope scope}, retiring all {@linkplain
	 * LocalVariable local variables} defined herein.
	 */
	void exit ()
	{
		for (final LocalVariable local : localVariables)
		{
			local.retire();
		}
	}
}
