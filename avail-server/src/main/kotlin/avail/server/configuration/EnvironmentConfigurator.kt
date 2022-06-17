/*
 * EnvironmentConfigurator.kt
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

package avail.server.configuration

import avail.builder.ModuleRoots
import avail.builder.RenamesFileParser
import avail.utility.configuration.Configurator

/**
 * An `EnvironmentConfigurator` [updates][Configurator.updateConfiguration] a
 * [compiler&#32;configuration][AvailServerConfiguration] from the environment.
 *
 * The following environment variables are used by the compiler:
 *
 *  * **AVAIL_ROOTS**: The [Avail&#32;root][ModuleRoots], described by the
 *    following grammar:
 *  ```
 *  modulePath ::= binding ++ ";" ;
 *  binding ::= logicalRoot "=" objectRepository ("," sourceDirectory) ;
 *  logicalRoot ::= [^=;]+ ;
 *  objectRepository ::= [^;]+ ;
 *  sourceDirectory ::= [^;]+ ;
 *  ```
 *  `logicalRoot` represents a logical root name. `objectRepository` represents
 *  the absolute path of a binary module repository. `sourceDirectory`
 *  represents the absolute path of a package, i.e., a directory containing
 *  source modules, and may be sometimes be omitted (e.g., when compilation is
 *  not required).
 *
 *  * **AVAIL_RENAMES**: The path to the
 *  [renames&#32;file][RenamesFileParser].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `EnvironmentConfigurator` for the specified
 * [configuration][AvailServerConfiguration].
 *
 * @param configuration
 *   The compiler configuration.
 */
class EnvironmentConfigurator constructor(
	override val configuration: AvailServerConfiguration)
: Configurator<AvailServerConfiguration>
{

	/** Has the [configurator][CommandLineConfigurator] been run yet? */
	private var isConfigured: Boolean = false

	@Synchronized
	override fun updateConfiguration()
	{
		if (!isConfigured)
		{
			val availRootPath = System.getenv("AVAIL_ROOTS")
			if (availRootPath !== null)
			{
				configuration.availRootsPath = availRootPath
			}
			val renamesFilePath = System.getenv("AVAIL_RENAMES")
			if (renamesFilePath !== null)
			{
				configuration.renamesFilePath = renamesFilePath
			}
			isConfigured = true
		}
	}
}
