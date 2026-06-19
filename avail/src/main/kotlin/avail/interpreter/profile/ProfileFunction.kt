/*
 * ProfileFunction.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter.profile;

import avail.descriptor.representation.A_RawFunction
import avail.descriptor.representation.AvailObject.Companion.combine3

/**
 * A [ProfileFunction] holds information about an [A_RawFunction] that was
 * invoked one or more times and recorded by [ProfileCollector]s, and
 * reconstructed via a [ProfileReconstructor] inside a tree of [ProfileNode]s.
 * Note that it does not contain an actual reference to an [A_RawFunction],
 * since profiles must be viewable when different code, or even when no code,
 * has been loaded.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ProfileFunction(
	val functionName: String,
	val moduleName: String,
	val line: Int)
{
	/** Capture the hash to speed up comparisons. */
	val hash = combine3(functionName.hashCode(), moduleName.hashCode(), line)

	override fun hashCode(): Int = hash

	override fun equals(other: Any?): Boolean
	{
		if (this === other) return true
		if (other !is ProfileFunction) return false
		if (hash != other.hash) return false
		if (line != other.line) return false
		if (moduleName != other.moduleName) return false
		if (functionName != other.functionName) return false
		return true
	}
}
