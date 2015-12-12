/**
 * L2IntegerRegister.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

import com.avail.interpreter.Interpreter;


/**
 * {@code L2IntegerRegister} models the conceptual usage of a register that can
 * store a machine integer.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class L2IntegerRegister
extends L2Register
{
	/**
	 * Construct a new {@link L2IntegerRegister}.
	 *
	 * @param debugValue A value used to distinguish the new instance visually.
	 */
	public L2IntegerRegister (final long debugValue)
	{
		super(debugValue);
	}

	/**
	 * Construct a new {@link L2IntegerRegister}, pre-colored to a particular
	 * integer register number.
	 *
	 * @param debugValue A value used to distinguish the new instance visually.
	 * @param index The index to which to constrain the register.
	 * @return The new register.
	 */
	public static L2IntegerRegister precolored (
		final long debugValue,
		final int index)
	{
		final L2IntegerRegister register = new L2IntegerRegister(debugValue);
		register.setFinalIndex(index);
		return register;
	}

	/**
	 * Read the value of this register from the provided {@link Interpreter}.
	 *
	 * @param interpreter An Interpreter.
	 * @return The {@code int} value of this integer register.
	 */
	public final int in (final Interpreter interpreter)
	{
		return interpreter.integerAt(finalIndex());
	}

	/**
	 * Replace the value of this register within the provided {@link
	 * Interpreter}.
	 *
	 * @param newValue The value to write.
	 * @param interpreter The Interpreter.
	 */
	public final void set (final int newValue, final Interpreter interpreter)
	{
		interpreter.integerAtPut(finalIndex(), newValue);
	}

	@Override
	public String toString ()
	{
		return "Int" + super.toString();
	}
}