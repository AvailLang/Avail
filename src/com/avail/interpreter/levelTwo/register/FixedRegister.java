/**
 * L2Translator.java
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

import com.avail.descriptor.NilDescriptor;

/**
 * A collection of representatives of {@link L2ObjectRegister}s, occupying
 * indices prior to any non-fixed registers.
 */
public enum FixedRegister
{
	/**
	 * The enumeration value representing the fixed register reserved for
	 * holding Avail's {@link NilDescriptor#nil() nil value}. Read only.
	 */
	NULL,

	@Deprecated CALLER,
	@Deprecated FUNCTION,
	@Deprecated PRIMITIVE_FAILURE,

	;

	/** An array of all {@link FixedRegister} enumeration values. */
	private static final FixedRegister[] all = values();

	/**
	 * Answer an array of all {@code FixedRegister} enumeration values.
	 *
	 * @return An array of all {@code FixedRegister } enum values.  Do not
	 *         modify the array.
	 */
	public static FixedRegister[] all ()
	{
		return all;
	}

	/**
	 * Record statically how many fixed registers exist.
	 */
	private static final int count = all.length;

	/**
	 * Answer how many fixed registers exist.
	 *
	 * @return The number of instances in this enum.
	 */
	public static int fixedRegisterCount ()
	{
		return count;
	}
}
