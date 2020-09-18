/*
 * StacksGlobalTag.kt
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

package com.avail.stacks.tags

import com.avail.stacks.LinkingFileMap
import com.avail.stacks.StacksErrorLog
import com.avail.stacks.tokens.QuotedStacksToken
import com.avail.utility.json.JSONWriter

/**
 * A global module variable comment tag
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
class StacksGlobalTag
/**
 * Construct a new [StacksGlobalTag].
 * @param globalName
 * The name of the global module variable
 * @param globalType
 * The name of the global module variable
 */
	(
	/**
	 * The name of the global module variable
	 */
	private val globalName: QuotedStacksToken,
	/**
	 * The name of the global module variable
	 */
	private val globalType: QuotedStacksToken) : StacksTag()
{

	/**
	 * @return the globalName
	 */
	fun globalName(): QuotedStacksToken
	{
		return globalName
	}

	/**
	 * @return the globalType
	 */
	fun globalType(): QuotedStacksToken
	{
		return globalType
	}

	override fun toJSON(
		linkingFileMap: LinkingFileMap,
		hashID: Int,
		errorLog: StacksErrorLog,
		position: Int,
		jsonWriter: JSONWriter)
	{
		// Is not called, do nothing.
	}

	override fun toString(): String
	{
		return "Global: $globalName"
	}
}
