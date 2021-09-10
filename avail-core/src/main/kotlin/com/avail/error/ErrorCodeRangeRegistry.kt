/*
 * ErrorCodeRangeRegistry.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package com.avail.error

import com.avail.builder.ModuleRootErrorCodeRange
import com.avail.files.FileErrorCodeRange

/**
 * An `ErrorCodeRangeRegistry` is the source for all known [ErrorCodeRange]s
 * that Avail is aware of. Modules (e.g. avail-server) can register
 * `ErrorCodeRange`s with the `ErrorCodeRangeRegistry`.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object ErrorCodeRangeRegistry
{
	/**
	 * The [Map] of [ErrorCodeRange.range] to [ErrorCodeRange] that contains all
	 * the `ErrorCodeRange`s available to Avail.
	 */
	private val ranges = mutableMapOf<IntRange, ErrorCodeRange>()

	/**
	 * Register the provided [ErrorCodeRange].
	 *
	 * Throws an [IllegalStateException] if it overlaps with any existing
	 * `ErrorCodeRange`.
	 *
	 * @param errorCodeRange
	 *   The range to register.
	 */
	fun register (errorCodeRange: ErrorCodeRange)
	{
		if (ranges.containsKey(errorCodeRange.range))
		{
			val existing = ranges[errorCodeRange.range]!!
			throw IllegalStateException(
				"Attempted to add ${errorCodeRange.name} " +
					"(${errorCodeRange.range}), but ${existing.name} already " +
					"occupies that range.")
		}

		for (range in ranges.values)
		{
			if (range.range.first in errorCodeRange.range
				|| range.range.last in errorCodeRange.range)
			{
				throw IllegalStateException(
					"Attempted to add ${errorCodeRange.name} " +
						"(${errorCodeRange.range}), but ${range.name} already " +
						"occupies that range.")
			}
		}
		ranges[errorCodeRange.range] = errorCodeRange
	}

	init
	{
		register(StandardErrorCodeRange)
		register(ModuleRootErrorCodeRange)
		register(FileErrorCodeRange)
	}

	/**
	 * Answer the appropriate key into [ranges] for the provided proposed
	 * [ErrorCode.code].
	 *
	 * @param code
	 *   The code to look up.
	 * @return
	 *   The [IntRange] if the code matches a valid key; `null` otherwise.
	 */
	private fun inRange (code: Int): IntRange?
	{
		for (range in ranges.keys)
		{
			if (range.contains(code)) return range
		}
		return null
	}

	/**
	 * Answer the [ErrorCode] for the given [ErrorCode.code].
	 *
	 * @param code
	 *   The [ErrorCode.code] to lookup.
	 * @return
	 *   The associated `ErrorCode` or [InvalidErrorCode] if the provided code
	 *   is not valid.
	 */
	fun errorCode (code: Int): ErrorCode
	{
		val range = inRange(code)
			?: return InvalidErrorCode(code, InvalidErrorCodeRange(code))
		return ranges[range]!!.errorCode(code)
	}
}
