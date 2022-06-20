/*
 * UnreachableRootException.kt
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

import avail.resolver.ModuleRootResolver

/**
 * `UnreachableRootException` is a type of [UnresolvedDependencyException] that
 * is specifically for the case that the [ModuleRootResolver] is unable to reach
 * the source of the [ModuleRoot] at the [ModuleRootResolver.uri].
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @constructor
 * Construct a new `UnreachableRootException`.
 *
 * @param referringModuleName
 *   The name of the module whose dependency graph resolution triggered the
 *   failed root access.
 * @param unresolvedModuleName
 *   The name of the module which could not be resolved because of the failed
 *   root access.
 * @param unresolvedRootName
 *   The name of the root which could not be reached.
 * @param resolver
 *   The [ModuleRootResolver] with an unreachable `URI`.
 */
class UnreachableRootException internal constructor(
		referringModuleName: ResolvedModuleName?,
		unresolvedModuleName: String,
		@Suppress("MemberVisibilityCanBePrivate")
		unresolvedRootName: String,
		resolver: ModuleRootResolver?)
	: UnresolvedDependencyException(referringModuleName, unresolvedModuleName)
{
	override val message =
		if (resolver !== null)
		{
			"module \"$unresolvedModuleName\" belongs to the module root, " +
				"$unresolvedRootName, which is unreachable by the module root " +
				"resolver: ${resolver.uri}."
		}
		else
		{
			"module \"$unresolvedModuleName\" belongs to the module root, " +
				"$unresolvedRootName, which has no module root resolver that " +
				"can be used to reach the source."
		}
}
