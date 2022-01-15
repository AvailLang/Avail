/*
 * Versions.kt
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

/**
 * The central source of all versions. This ranges from dependency versions
 * to language level versions.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object Versions
{
	/** The version of Kotlin to be used by Avail. */
	const val kotlin = "1.5.31"

	/** The Kotlin Desktop Compose version [org.jetbrains.compose]. */
	const val compose = "1.0.0"

	/** The Avail version. */
	const val avail = "1.6.0"

	/** The `com.github.johnrengelman.shadow` version. */
	const val shadow = "6.0.0"

	/** The `org.jetbrains:annotations` version. */
	const val kotlinAnnotations="22.0.0"

	/** The `org.ow2.asm` version. */
	const val asmVersion="9.2"

	/** The `com.github.weisj:darklaf-core` version.*/
	const val darklafVersion = "2.7.2"

	/** The `io.methvin:directory-watcher` version. */
	const val directoryWatcherVersion = "0.15.0"

	/** The `com.google.code.findbugs:jsr305` version. */
	const val jsrVersion = "3.0.2"

	/** The `org.junit.jupiter:junit-jupiter` version. */
	const val junitVersion = "5.7.2"

	/** The `org.apache.tika:tika-core` version. */
	const val tikaVersion = "2.0.0"

	/** The language level version of Kotlin. */
	const val kotlinLanguage = "1.5"

	/** The JVM target version for Kotlin. */
	const val jvmTarget = "11"

	/** The JVM target version for Kotlin. */
	const val intellij = "1.3.0"

	/**
	 * The list of compile-time arguments to be used during Kotlin compilation.
	 */
	val freeCompilerArgs = listOf("-Xjvm-default=compatibility")

	/** The language level version of Kotlin. */
	const val languageVersion = kotlinLanguage
}
