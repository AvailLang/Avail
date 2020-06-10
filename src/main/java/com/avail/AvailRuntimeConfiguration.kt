/*
 * AvailRuntimeConfiguration.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail

import com.avail.descriptor.methods.MacroDefinitionDescriptor
import com.avail.descriptor.sets.A_Set
import com.avail.descriptor.sets.SetDescriptor.Companion.generateSetFrom
import com.avail.descriptor.tuples.StringDescriptor
import com.avail.interpreter.execution.Interpreter
import java.io.IOException
import java.util.*

/**
 * This class contains static state and methods related to the current running
 * configuration.
 */
object AvailRuntimeConfiguration
{
	/** The build version, set by the build process.  */
	private var buildVersion: String = "dev"

	/**
	 * Answer the build version, as set by the build process.
	 *
	 * @return
	 *   The build version, or `"dev"` if Avail is not running from a
	 *   distribution JAR.
	 */
	fun buildVersion(): String
	{
		return buildVersion
	}

	/**
	 * The active versions of the Avail virtual machine. These are the versions
	 * for which the virtual machine guarantees compatibility.
	 */
	private val activeVersions = arrayOf("1.4.0")

	/**
	 * Answer the active versions of the Avail virtual machine. These are the
	 * versions for which the virtual machine guarantees compatibility.
	 *
	 * @return
	 * The active versions.
	 */
	@JvmStatic
	fun activeVersions(): A_Set =
		generateSetFrom(activeVersions) { StringDescriptor.stringFrom(it) }

	/**
	 * Answer a short string indicating the most recent version of Avail
	 * supported by the current virtual machine.
	 *
	 * @return
	 *   A short [String].
	 */
	fun activeVersionSummary(): String =
		activeVersions[activeVersions.size - 1].split(" ".toRegex(), 2)
			.toTypedArray()[0]

	/** The number of available processors.  */
	@JvmField
	val availableProcessors = Runtime.getRuntime().availableProcessors()

	/**
	 * The maximum number of [Interpreter]s that can be constructed for
	 * this runtime.
	 */
	@JvmField
	val maxInterpreters = availableProcessors
	/**
	 * Whether to show all [macro][MacroDefinitionDescriptor] expansions as
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
		var version = "dev"
		try
		{
			ClassLoader.getSystemResourceAsStream(
				"resources/build-time.txt").use { resourceStream ->
				if (resourceStream != null)
				{
					Scanner(resourceStream).use { version = it.nextLine() }
				}
			}
		}
		catch (e: IOException)
		{
			version = "UNKNOWN"
		}
		buildVersion = version
	}
}