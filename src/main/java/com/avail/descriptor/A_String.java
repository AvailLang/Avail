/*
 * A_String.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

/**
 * {@code A_String} is an interface that specifies the string-specific
 * operations that an {@link AvailObject} must implement.  It's a sub-interface
 * of {@link A_Tuple} (which is itself a sub-interface of {@link A_BasicObject}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public interface A_String
extends A_Tuple
{
	/**
	 * Construct a Java {@linkplain String string} from the receiver, an Avail
	 * {@linkplain StringDescriptor string}.
	 *
	 * @return The corresponding Java string.
	 */
	String asNativeString ();

	/**
	 * Even though {@link #copyTupleFromToCanDestroy(int, int, boolean)} would
	 * perform the same activity, this method returns the stronger {@code
	 * A_String} type as a convenience, when the code knows it's working on
	 * strings.
	 *
	 * @param start
	 *        The start of the range to extract.
	 * @param end
	 *        The end of the range to extract.
	 * @param canDestroy
	 *        Whether the original object may be destroyed if mutable.
	 * @return The substring.
	 */
	A_String copyStringFromToCanDestroy (
		final int start,
		final int end,
		final boolean canDestroy);
}
