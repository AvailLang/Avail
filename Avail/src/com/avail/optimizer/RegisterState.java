/**
 * RegisterState.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.optimizer;

import java.util.*;
import com.avail.annotations.Nullable;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * This class maintains information about one {@linkplain L2Register} on behalf
 * of a {@link RegisterSet} held by an {@linkplain L2Instruction} inside an
 * {@linkplain L2Chunk} constructed by the {@link L2Translator}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class RegisterState
{
	/**
	 * The exact value in this register.  If the exact value is not known, this
	 * field is {@code null}.
	 */
	public @Nullable AvailObject constant;

	/** The type of value in this register. */
	public @Nullable A_Type type;

	/**
	 * The list of other registers that are known to have the same value (if
	 * any).  These occur in the order in which the registers acquired the
	 * common value.
	 *
	 * <p>
	 * The inverse map is kept in {@link #invertedOrigins}, to more
	 * efficiently disconnect this information.
	 * </p>
	 */
	public final List<L2Register> origins = new ArrayList<>();

	/**
	 * The inverse of {@link #origins}.  For each key, the value is the
	 * collection of registers that this value has been copied into (and not yet
	 * been overwritten).
	 */
	public final Set<L2Register> invertedOrigins = new HashSet<>();

	/**
	 * The {@link Set} of {@link L2Instruction}s that may have provided the
	 * current value in that register.  There may be more than one such
	 * instruction due to multiple paths converging by jumping to labels.
	 */
	public final Set<L2Instruction> sourceInstructions = new HashSet<>();

	/**
	 * Answer whether this register contains a constant at the current code
	 * generation point.
	 *
	 * @return Whether this register has most recently been assigned a constant.
	 */
	public boolean hasConstant ()
	{
		return constant != null;
	}

	/**
	 * Construct a new {@link RegisterState}.
	 */
	RegisterState ()
	{
		// Dealt with by individual field declarations.
	}

	/**
	 * Copy a {@link RegisterState}.
	 *
	 * @param original The original RegisterSet to copy.
	 */
	RegisterState (final RegisterState original)
	{
		this.constant = original.constant;
		this.type = original.type;
		this.origins.addAll(original.origins);
		this.invertedOrigins.addAll(original.invertedOrigins);
		this.sourceInstructions.addAll(original.sourceInstructions);
	}
}
