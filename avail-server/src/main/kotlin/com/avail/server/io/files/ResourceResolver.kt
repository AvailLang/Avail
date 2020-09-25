/*
 * ResourceResolver.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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

package com.avail.server.io.files

import com.avail.builder.ModuleName
import com.avail.builder.ModuleNameResolver
import com.avail.builder.ModuleRoot

/**
 * A `ResourceResolver` is an abstraction used to create an abstraction that the
 * file-directory connected [ModuleNameResolver] can use in an abstract way,
 * making the "file system" replaceable w/ a generic resource whether it be
 * a local file system, a networked resource, database, jar file, etc.
 *
 * // TODO update the comment after moving the class to its appropriate final home
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
abstract class ResourceResolver (val moduleRoot: ModuleRoot)
{
	/**
	 * Provide the source of the provided [ModuleName] to the provided
	 * `consumer`.
	 *
	 * @param moduleName
	 *   The [ModuleName] to retrieve the source for.
	 * @param consumer
	 *   The consumer that accepts the module source bytes.
	 */
	abstract fun fetch (moduleName: ModuleName, consumer: (ByteArray) -> Unit)
}