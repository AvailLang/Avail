/*
 * CodeLoggingPathData.kt
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
package avail.optimizer.jvm

import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.A_RawFunction.Companion.codeStartingLineNumber
import avail.descriptor.functions.A_RawFunction.Companion.methodName
import avail.descriptor.functions.A_RawFunction.Companion.module
import avail.descriptor.module.A_Module.Companion.moduleNameNative
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.A_String.Companion.asNativeString
import java.util.regex.Pattern

/**
 * The naming fields extracted from an [A_RawFunction] that are needed to
 * construct output directory paths and file names for L2 debug output.
 *
 * @property moduleName
 *   The last path segment of the module name, e.g. `"MyModule"`, or
 *   `"NoModule"` if unavailable.
 * @property methodName
 *   The method name as a native string, or `"DEFAULT"` for the unoptimized
 *   chunk.
 * @property lineNumber
 *   The starting source line number of the function, or `0` if unavailable.
 *
 * @constructor
 * Create a [CodeLoggingPathData] with the given fields.
 */
data class CodeLoggingPathData(
	val moduleName: String,
	val methodName: String,
	val lineNumber: Int)
{
	companion object
	{
		/** Strips the leading path segments from a module name. */
		private val moduleNameStripper: Pattern =
			Pattern.compile("^.*/([^/]+)$")

		/**
		 * Extract the [CodeLoggingPathData] needed for output path construction
		 * from the given [code].
		 *
		 * @param code
		 *   The [A_RawFunction] being compiled, or `null` for the default chunk.
		 * @return
		 *   The extracted [CodeLoggingPathData].
		 */
		fun from(code: A_RawFunction?): CodeLoggingPathData
		{
			val module = code?.module ?: nil
			val moduleName = when
			{
				module === nil -> "NoModule"
				else -> moduleNameStripper
					.matcher(module.moduleNameNative)
					.replaceAll("$1")
			}
			return CodeLoggingPathData(
				moduleName = moduleName,
				methodName = code?.methodName?.asNativeString() ?: "DEFAULT",
				lineNumber = code?.codeStartingLineNumber ?: 0)
		}
	}
}
