/*
 * JSONException.kt
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

package com.avail.utility.json

import java.io.IOException

/**
 * A [JSONReader] throws a `JSONException` if anything goes wrong during the
 * reading of a JSON document.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
abstract class JSONException : RuntimeException
{
	/** Construct a new `JSONException`. */
	protected constructor()
	{
		// No implementation.
	}

	/**
	 * Construct a new `JSONException`.
	 *
	 * @param cause
	 *   The causal exception.
	 */
	protected constructor(cause: Exception) : super(cause)
}

/**
 * A `JSONIOException` is an [unchecked][RuntimeException] that wraps an
 * [IOException].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `JSONIOException`.
 *
 * @param cause
 *   The causal exception.
 */
class JSONIOException internal constructor(cause: Exception)
	: JSONException(cause)

/**
 * A [JSONReader] throws a `MalformedJSONException` if an invalid construct is
 * encountered during the parsing of a JSON document.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `MalformedJSONException`.
 */
class MalformedJSONException internal constructor() : JSONException()
