/*
 * SourceCodeInfo.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.anvil

import avail.AvailRuntime
import avail.AvailRuntimeSupport.AvailLazyFuture
import avail.builder.ModuleName
import avail.compiler.AvailCompiler.Companion.normalizeLineEnds
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.moduleNameNative
import avail.resolver.ResolverReference
import java.util.Collections.synchronizedMap

/**
 * Information about the source code for some module.
 *
 * @constructor
 * Create a new record with the given content.
 */
class SourceCodeInfo
constructor (
	val runtime: AvailRuntime,
	val resolverReference: ResolverReference)
{
	/** Lazily extract the module's source code. */
	private val exactSource = AvailLazyFuture<String>(runtime) {
			withExactSource ->
		resolverReference.readFileString(
			false,
			{ string, _ -> withExactSource(string) },
			{ errorCode, _ ->
				// Cheesy, but good enough.
				withExactSource("Cannot retrieve source: $errorCode")
			})
	}

	/**
	 * Lazily normalize the line ends of the module source to be linefeed.
	 * Answer the normalized source and the line delimiter that was actually
	 * found in the original source.
	 */
	val sourceAndDelimiter = AvailLazyFuture<Pair<String, String>>(runtime) {
			withSourceAndDelimiter ->
		exactSource.withValue { theExactSource ->
			withSourceAndDelimiter(normalizeLineEnds(theExactSource))
		}
	}

	/**
	 * Lazily calculate where all the line ends are.
	 */
	val lineEnds = AvailLazyFuture<List<Int>>(runtime) { withLineEnds ->
		sourceAndDelimiter.withValue { (string, _) ->
			val ends = string.withIndex()
				.filter { it.value == '\n' }
				.map(IndexedValue<Char>::index)
			withLineEnds(ends)
		}
	}

	companion object
	{
		/**
		 * A cache of the source code for [A_Module]s, each with a [List] of
		 * positions in the string where a linefeed occurs.
		 */
		private val sourceCache =
			synchronizedMap<A_Module, SourceCodeInfo>(mutableMapOf())

		/**
		 * Extract the source of the current frame's module, along with the
		 * file's delimiter, and a list of positions where linefeeds are.
		 */
		fun sourceWithInfoThen(
			runtime: AvailRuntime,
			module: A_Module,
			then: (
				source: String,
				lineDelimiter: String,
				lineEnds: List<Int>)->Unit)
		{
			val resolverReference = runtime.moduleNameResolver
				.resolve(ModuleName(module.moduleNameNative), null)
				.resolverReference
			val info = sourceCache.computeIfAbsent(module) {
				SourceCodeInfo(runtime, resolverReference)
			}
			info.sourceAndDelimiter.withValue { (source, lineDelimiter) ->
				info.lineEnds.withValue { lineEnds ->
					then(source, lineDelimiter, lineEnds)
				}
			}
		}
	}
}