/*
 * A_String.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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
package com.avail.descriptor.tuples

import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.representation.AvailObject

/**
 * `A_String` is an interface that specifies the string-specific operations that
 * an [AvailObject] must implement.  It's a sub-interface of [A_Tuple] (which is
 * itself a sub-interface of [A_BasicObject].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_String : A_Tuple
{
	/**
	 * Construct a Java [string][String] from the receiver, an Avail
	 * [string][StringDescriptor].
	 *
	 * @return
	 *   The corresponding Java string.
	 */
	fun asNativeString(): String

	/**
	 * Even though [copyTupleFromToCanDestroy] would perform the same activity,
	 * this method returns the stronger `A_String` type as a convenience, when
	 * the code knows it's working on strings.
	 *
	 * @param start
	 *   The start of the range to extract.
	 * @param end
	 *   The end of the range to extract.
	 * @param canDestroy
	 *   Whether the original object may be destroyed if mutable.
	 * @return
	 *   The substring.
	 */
	fun copyStringFromToCanDestroy(
		start: Int,
		end: Int,
		canDestroy: Boolean): A_String
}
