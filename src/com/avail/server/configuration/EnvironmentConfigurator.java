/**
 * EnvironmentConfigurator.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.server.configuration;

import com.avail.builder.ModuleRoots;
import com.avail.builder.RenamesFileParser;
import com.avail.tools.compiler.Compiler;
import com.avail.tools.compiler.configuration.CommandLineConfigurator;
import com.avail.utility.configuration.Configurator;

/**
 * An {@code EnvironmentConfigurator} {@linkplain
 * Configurator#updateConfiguration() updates} a {@linkplain
 * AvailServerConfiguration compiler configuration} from the environment.
 *
 * <p>The following environment variables are used by the {@linkplain
 * Compiler compiler}:</p>
 *
 * <ul>
 * <li><strong>AVAIL_ROOTS</strong>: The {@linkplain ModuleRoots Avail root
 * path}, described by the following grammar:
 * <p><pre>
 * modulePath ::= binding ++ ";" ;
 * binding ::= logicalRoot "=" objectRepository ("," sourceDirectory) ;
 * logicalRoot ::= [^=;]+ ;
 * objectRepository ::= [^;]+ ;
 * sourceDirectory ::= [^;]+ ;
 * </pre></p>
 * <p>{@code logicalRoot} represents a logical root name. {@code
 * objectRepository} represents the absolute path of a binary module repository.
 * {@code sourceDirectory} represents the absolute path of a package, i.e., a
 * directory containing source modules, and may be sometimes be omitted (e.g.,
 * when compilation is not required).</p></li>
 * <li><strong>AVAIL_RENAMES</strong>: The path to the {@linkplain
 * RenamesFileParser renames file}.</li>
 * </ul>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class EnvironmentConfigurator
implements Configurator<AvailServerConfiguration>
{
	/** The {@linkplain AvailServerConfiguration configuration}. */
	private final AvailServerConfiguration configuration;

	/**
	 * Has the {@linkplain CommandLineConfigurator configurator} been run yet?
	 */
	private boolean isConfigured;

	@Override
	public AvailServerConfiguration configuration ()
	{
		return configuration;
	}

	@Override
	public synchronized void updateConfiguration ()
	{
		if (!isConfigured)
		{
			final String availRootPath = System.getenv("AVAIL_ROOTS");
			if (availRootPath != null)
			{
				configuration.setAvailRootsPath(availRootPath);
			}
			final String renamesFilePath = System.getenv("AVAIL_RENAMES");
			if (renamesFilePath != null)
			{
				configuration.setRenamesFilePath(renamesFilePath);
			}
			isConfigured = true;
		}
	}

	/**
	 * Construct a new {@link EnvironmentConfigurator} for the specified
	 * {@linkplain AvailServerConfiguration configuration}.
	 *
	 * @param configuration
	 *        The compiler configuration.
	 */
	public EnvironmentConfigurator (
		final AvailServerConfiguration configuration)
	{
		this.configuration = configuration;
	}
}
