/*
 * ModuleRootErrorCodeRange.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.builder

import avail.error.ErrorCode
import avail.error.ErrorCodeRange
import avail.error.InvalidErrorCode
import avail.resolver.ModuleRootResolver

/**
 * A `ModuleRootErrorCodeRange` is an [ErrorCodeRange] that holds defined error
 * codes that involve failures while dealing with [ModuleRoot]s.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object ModuleRootErrorCodeRange : ErrorCodeRange
{
	override val name: String = "Module Root Error Code Range"

	override val range: IntRange = IntRange(10000, 19999)

	override fun errorCode(code: Int): ErrorCode
	{
		assert(code in range)
		return ModuleRootErrorCode.code(code - minCode)
	}
}

/**
 * `ModuleRootErrorCode` is an enum of [ErrorCode]s specific to errors involving
 * [ModuleRoot]s.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
enum class ModuleRootErrorCode(code: Int): ErrorCode
{
	/** An unspecified error has occurred. */
	UNSPECIFIED(10000),

	/**
	 * Located [ModuleRoot] has no [source location][ModuleRoot.resolver].
	 */
	NO_SOURCE_LOCATION(10001),

	/** Could not [find][ModuleRoots.moduleRootFor] [ModuleRoot]. */
	BAD_MODULE_ROOT(10002),

	/**
	 * The [ModuleRootResolver] could not be successfully [ModuleRoot].
	 */
	MODULE_ROOT_RESOLUTION_FAILED(10003);

	override val errorCodeRange: ErrorCodeRange
		get() = ModuleRootErrorCodeRange

	override val code: Int

	init
	{
		val expectedCode = ordinal + errorCodeRange.minCode
		require(code == expectedCode) {
			"ModuleRootErrorCode $name's provided code did not match the " +
				"ordinal + errorCodeRange.minCode: ($ordinal + " +
				"${errorCodeRange.minCode}). To ensure uniqueness the code " +
				"must be its ordinal position in the enum added to the range " +
				"minimum."
		}
		this.code = code
	}

	companion object
	{
		/**
		 * Answer the [ErrorCode] for the provided [ErrorCode.code].
		 *
		 * @param code
		 *   The integer value used to identify the [ModuleRootErrorCode].
		 * @return
		 *   The associated `ModuleRootErrorCode` or [InvalidErrorCode] if the
		 *   id is not found.
		 */
		fun code (code: Int): ErrorCode =
			when(code)
			{
				UNSPECIFIED.code -> UNSPECIFIED
				NO_SOURCE_LOCATION.code -> NO_SOURCE_LOCATION
				BAD_MODULE_ROOT.code -> BAD_MODULE_ROOT
				BAD_MODULE_ROOT.code -> BAD_MODULE_ROOT
				else -> InvalidErrorCode(code, ModuleRootErrorCodeRange)
			}
	}
}
