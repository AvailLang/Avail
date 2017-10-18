/**
 * L2ObjectRegister.java
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

package com.avail.interpreter.levelTwo.register;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.operand.TypeRestriction;

import javax.annotation.Nullable;

/**
 * {@code L2ObjectRegister} models the conceptual usage of a register that can
 * store an {@link AvailObject}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class L2ObjectRegister
extends L2Register
{
	@Override
	public RegisterKind registerKind ()
	{
		return RegisterKind.OBJECT;
	}

	/**
	 * The {@link TypeRestriction} that constrains this register's content.
	 */
	private final TypeRestriction restriction;

	/**
	 * Construct a new {@code L2ObjectRegister}.
	 *
	 * @param debugValue
	 *        A value used to distinguish the new instance visually during
	 *        debugging of L2 translations.
	 * @param type
	 * 	      The type of value that is to be written to this register.
	 * @param constantOrNull
	 *        The exact value that is to be written to this register if known,
	 *        otherwise {@code null}.
	 */
	public L2ObjectRegister (
		final long debugValue,
		final A_Type type,
		final @Nullable A_BasicObject constantOrNull)
	{
		super(debugValue);
		this.restriction = new TypeRestriction(type, constantOrNull);
	}

	/**
	 * Answer this register's basic {@link TypeRestriction}.
	 *
	 * @return A {@link TypeRestriction}.
	 */
	public final TypeRestriction restriction ()
	{
		return restriction;
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("Reg");
		if (finalIndex() != -1)
		{
			builder.append("[");
			builder.append(finalIndex());
			builder.append("]");
		}
		builder.append("@");
		builder.append(uniqueValue);
		return builder.toString();
	}
}
