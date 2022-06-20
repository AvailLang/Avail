/*
 * Versions.kt
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

/**
 * The central source of all versions. This ranges from dependency versions
 * to language level versions.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object Versions
{
	/** The version of Kotlin to be used by Avail. */
	const val kotlin = "1.6.20"

	/** The JVM target version for Kotlin. */
	const val jvmTarget = 17

	/** The JVM target version for Kotlin. */
	const val jvmTargetString = jvmTarget.toString()

	/**
	 * The stripe release version of avail jars:
	 *  * `avail`
	 *  * `avail-workbench`
	 *  * `avail-stdlib`
	 *  * `avail-storage`
	 *  * `avail-json`
	 *
	 *  This represents the version of the `avail-plugin`.
	 */
	const val avail = "1.6.1.rc1"
//
//	/**
//	 * The SLF4J No-Op package used to prevent SLF4J warning from being printed.
//	 *
//	 * See: [http://www.slf4j.org/codes.html#noProviders]
//	 */
//	const val slf4jnop = "1.7.33"
}
