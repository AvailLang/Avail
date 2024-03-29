/*
 * UnresolvedRootException.kt
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

/**
 * `UnresolvedRootException` is a type of [UnresolvedDependencyException] that
 * is specifically for the case that the compiler could not find one of the
 * roots it expected to be present. This may be caused by:
 *
 *  * The target module name referring to a root not present in `AVAIL_ROOTS`,
 *    or
 *  * The compiler being unable to find a root accounted for in `AVAIL_ROOTS`
 *    because it has disappeared from the file system
 *
 * @property unresolvedRootName
 *   The name of the root that could not be found.
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `UnresolvedRootException`.
 *
 * @param referringModuleName
 *   The name of the module whose dependency graph resolution triggered the
 *   failed root access.
 * @param unresolvedModuleName
 *   The name of the module which could not be resolved because of the failed
 *   root access.
 * @param unresolvedRootName
 *   The name of the root which could not be resolved.
 */
class UnresolvedRootException internal constructor(
	referringModuleName: ResolvedModuleName?,
	unresolvedModuleName: String,
	@Suppress("MemberVisibilityCanBePrivate")
	val unresolvedRootName: String)
: UnresolvedDependencyException(referringModuleName, unresolvedModuleName)
