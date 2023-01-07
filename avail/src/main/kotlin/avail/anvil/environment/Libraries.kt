/*
 * Libraries.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
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

package avail.anvil.environment

import avail.anvil.projects.AvailStdLibVersion
import org.availlang.artifact.environment.AvailEnvironment
import java.io.File

// Utilities for working with Avail libraries.

/**
 * The Avail standard libraries in [AvailEnvironment.availHomeLibs].
 */
val availStandardLibraries: Array<File> get() =
	stdLibHome.let { home ->
		val dir = File(home)
		if (!dir.exists())
		{
			dir.mkdirs()
		}
		val m = mutableListOf<AvailStdLibVersion?>()
		val libs = dir.listFiles { _, name ->
			name.endsWith(".jar") &&
				name.startsWith("avail-stdlib")
		} ?: arrayOf()
		libs.sortByDescending {
			val v = it.name.split("avail-stdlib-")
				.last().split(".jar").first()
			AvailStdLibVersion.versionOrNull(v).apply { m.add(this) }
		}
		libs
	}
