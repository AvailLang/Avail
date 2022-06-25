/*
 * AvailRuntimeConfiguration.kt
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
package avail

import avail.descriptor.methods.MacroDescriptor
import avail.interpreter.execution.Interpreter
import java.io.IOException
import java.util.Date

/**
 * This class contains static state and methods related to the current running
 * configuration.
 */
@Suppress("unused")
object AvailRuntimeConfiguration
{
	/** The build version, set by the build process. */
	@Suppress("MemberVisibilityCanBePrivate")
	val buildVersion: String

	/**
	 * The active versions of the Avail virtual machine. These are the versions
	 * for which the virtual machine guarantees compatibility.
	 */
	val activeVersions = arrayOf("Avail-1.6.1")

	/**
	 * Answer a short string indicating the most recent version of Avail
	 * supported by the current virtual machine.
	 *
	 * @return
	 *   A short [String].
	 */
	val activeVersionSummary: String
		get() = activeVersions.last().split(" ".toRegex(), 2)
			.toTypedArray()[0]

	/** The number of available processors. */
	val availableProcessors = Runtime.getRuntime().availableProcessors()

	/**
	 * The maximum number of [Interpreter]s that can be constructed for
	 * this runtime.
	 */
	val maxInterpreters = availableProcessors

	/**
	 * Whether to show all [macro][MacroDescriptor] expansions as
	 * they happen.
	 */
	var debugMacroExpansions = false

	/**
	 * Whether to show detailed compiler trace information.
	 */
	var debugCompilerSteps = false

	/*
	 * Initialize the build version from a resource bundled with the
	 * distribution JAR.
	 */
	init
	{
		buildVersion = try
		{
			// Look up this very class as a resource.
			val url = javaClass.getResource(javaClass.simpleName + ".class")
			// Extract its last modification date from the jar or .class file.
			val lastModified = Date(url.openConnection().lastModified)
			lastModified.toString()
		}
		catch (e: IOException)
		{
			"UNKNOWN"
		}
	}
}
