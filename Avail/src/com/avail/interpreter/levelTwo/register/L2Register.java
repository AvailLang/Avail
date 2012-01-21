/**
 * L2Register.java
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

package com.avail.interpreter.levelTwo.register;

import com.avail.annotations.NotNull;
import com.avail.interpreter.levelTwo.*;

/**
 * {@code L2Register} models the conceptual use of a register by a {@linkplain
 * L2Operation level two Avail operation} in the {@linkplain L2Translator
 * translator}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class L2Register
{
	/**
	 * The {@linkplain L2RegisterIdentity identity} of this {@linkplain
	 * L2Register register}.
	 */
	private final @NotNull L2RegisterIdentity identity;

	/**
	 * Answer the {@linkplain L2RegisterIdentity identity} of this {@linkplain
	 * L2Register register}.
	 *
	 * @return The {@linkplain L2RegisterIdentity identity} of this {@linkplain
	 *         L2Register register}.
	 */
	public L2RegisterIdentity identity ()
	{
		return identity;
	}

	/**
	 * Construct a new {@link L2Register}.
	 */
	public L2Register ()
	{
		identity = new L2RegisterIdentity();
	}

	/**
	 * Construct a new {@link L2Register} identical to the specified register.
	 *
	 * @param register A {@linkplain L2Register register}.
	 */
	protected L2Register (final @NotNull L2Register register)
	{
		this.identity = register.identity;
		this.isLast = register.isLast;
		this.knowsIsLast = register.knowsIsLast;
	}

	/**
	 * Has the {@linkplain L2Register register} been used for the last time?
	 * This flag is set during flow analysis by the {@linkplain L2Translator
	 * translator}.
	 */
	boolean isLast = false;

	/**
	 * Does the {@linkplain L2Register register} know whether it has been used
	 * for the last time? This flag is set automatically by {@link
	 * #setIsLastUse(boolean)}.
	 */
	boolean knowsIsLast = false;

	/**
	 * Has the {@linkplain L2Register register} been used for the last time?
	 *
	 * @return {@code true} if flow analysis has determined that the {@linkplain
	 *         L2Register register} has been used for the last time, {@code
	 *         false} otherwise.
	 */
	public boolean isLastUse ()
	{
		// TODO: [MvG] Recover the flow analysis code from Smalltalk!
		assert !knowsIsLast;
		return isLast;
	}

	/**
	 * Record whether the {@linkplain L2Register register} has been used for the
	 * last time.
	 *
	 * @param isLast
	 *        {@code true} if flow analysis has determined that the {@linkplain
	 *        L2Register register} has been used for the last time, {@code
	 *        false} if the register may be used again.
	 */
	public void setIsLastUse (final boolean isLast)
	{
		// TODO: [MvG] Recover the flow analysis code from Smalltalk!
		assert !knowsIsLast;
		this.isLast = isLast;
		knowsIsLast = true;
	}

	@Override
	public String toString ()
	{
		final StringBuilder builder = new StringBuilder();
		if (identity.finalIndex() != -1)
		{
			builder.append("Reg[");
			builder.append(identity.finalIndex());
			builder.append("]");
		}
		else
		{
			builder.append("Reg_");
			builder.append(identity.printId());
		}
		if (knowsIsLast)
		{
			if (isLast)
			{
				builder.append(" isLast");
			}
			else
			{
				builder.append(" notLast");
			}
		}
		return builder.toString();
	}
}
