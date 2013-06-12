/**
 * Compiler.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

package com.avail.tools.compiler;

import java.io.FileNotFoundException;
import java.util.List;
import com.avail.AvailRuntime;
import com.avail.annotations.Nullable;
import com.avail.builder.AvailBuilder;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.ModuleRoot;
import com.avail.builder.RecursiveDependencyException;
import com.avail.builder.RenamesFileParserException;
import com.avail.builder.ResolvedModuleName;
import com.avail.builder.UnresolvedDependencyException;
import com.avail.compiler.AvailCompilerException;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.tools.compiler.configuration.CommandLineConfigurator;
import com.avail.tools.compiler.configuration.CompilerConfiguration;
import com.avail.tools.compiler.configuration.EnvironmentConfigurator;
import com.avail.tools.configuration.ConfigurationException;
import com.avail.utility.Continuation3;
import com.avail.utility.Continuation4;

/**
 * TODO: [LAS] Document Compiler!
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class Compiler
{
	/**
	 * Configure the {@linkplain Compiler compiler} to build the target
	 * {@linkplain ModuleDescriptor module}.
	 *
	 * @param args
	 *        The command-line arguments.
	 * @return A viable {@linkplain CompilerConfiguration compiler
	 *         configuration}.
	 * @throws ConfigurationException
	 *         If configuration fails for any reason.
	 */
	private static CompilerConfiguration configure (final String[] args)
		throws ConfigurationException
	{
		final CompilerConfiguration configuration = new CompilerConfiguration();
		// Update the configuration using the environment first.
		final EnvironmentConfigurator environmentConfigurator =
			new EnvironmentConfigurator(configuration);
		environmentConfigurator.updateConfiguration();
		// Update the configuration using the command-line arguments.
		final CommandLineConfigurator commandLineConfigurator =
			new CommandLineConfigurator(configuration, args, System.out);
		commandLineConfigurator.updateConfiguration();
		return configuration;
	}

	/**
	 * The entry point for command-line invocation of the Avail compiler.
	 *
	 * @param args The command-line arguments.
	 */
	public static void main (final String[] args)
	{
		final CompilerConfiguration configuration;
		final ModuleNameResolver resolver;

		try
		{
			configuration = configure(args);
			resolver = configuration.moduleNameResolver();
		}
		catch (final ConfigurationException |
					 FileNotFoundException |
					 RenamesFileParserException e)
		{
			// The command-line arguments were malformed, or
			// The arguments specified a missing file, or
			// The renames file did not parse correctly
			System.err.println(e.getMessage());
			return;
		}

		final ModuleName moduleName = configuration.targetModuleName();

		// Create a local tracker to store information about the progress of the
		// compilation of the current module.
		final Continuation4<ModuleName, Long, Long, Long> localTracker =
			new Continuation4<ModuleName, Long, Long, Long>()
			{
				@Override
				public void value (
					final @Nullable ModuleName module,
					final @Nullable Long lineNumber,
					final @Nullable Long parsePosition,
					final @Nullable Long moduleSize)
				{
					assert module != null;
					assert lineNumber != null;
					assert parsePosition != null;
					assert moduleSize != null;

					// TODO [LAS] Add actions for verbose mode
				}
			};

		// Create a global tracker to store information about the progress on
		// all modules to be compiled.
		final Continuation3<ModuleName, Long, Long> globalTracker =
			new Continuation3<ModuleName, Long, Long>()
			{
				@Override
				public void value (
					@Nullable final ModuleName module,
					@Nullable final Long processedBytes,
					@Nullable final Long totalBytes)
				{
					assert module != null;
					assert processedBytes != null;
					assert totalBytes != null;

					// TODO [LAS] Add actions for verbose mode
				}
			};

		final AvailRuntime runtime = new AvailRuntime(resolver);
		try
		{
			final AvailBuilder builder = new AvailBuilder(runtime);
			builder.build(moduleName, localTracker, globalTracker);
		}
		catch (final RecursiveDependencyException e)
		{
			final List<ResolvedModuleName> circuit = e.recursionPath();
			final StringBuilder sb = new StringBuilder(500);

			sb.append("ERROR: A recursive dependency was found in the build " +
				"path.");

			sb.append("\n\nCircuit entry point: ");
			sb.append(circuit.get(0).qualifiedName());

			sb.append("\nCircuit path: ");
			boolean firstTime = true;
			for (final ResolvedModuleName mod : circuit)
			{
				if (!firstTime)
				{
					sb.append(" < ");
				}
				else
				{
					firstTime = false;
				}
				sb.append(mod.localName());
			}

			final String errorMsg = sb.toString();
			System.err.println(errorMsg);
			return;
		}
		catch (final UnresolvedDependencyException e)
		{
			final StringBuilder sb = new StringBuilder(500);

			sb.append("ERROR: An unresolved dependency was found in the " +
				"build path.");

			sb.append("\n\nModule \"");
			sb.append(e.unresolvedModuleName());
			sb.append("\" was not found in");
			sb.append("\n    any of the parent packages of ");
			sb.append(e.referringModuleName());
			sb.append("\n    or any of the roots: ");
			boolean firstTime = true;
			for (final ModuleRoot root : configuration.availRoots())
			{
				if (!firstTime)
				{
					sb.append("\n                         ");
				}
				else
				{
					firstTime = false;
				}
				sb.append(root.toString());
			}

			final String errorMsg = sb.toString();
			System.err.println(errorMsg);
			return;
		}
		catch (final AvailCompilerException e)
		{
			// User code error.
			System.err.println(e.getMessage());
			return;
		}
		catch (final InterruptedException e)
		{
			// Programmer error within the compiler.
			e.printStackTrace();
			return;
		}
		finally
		{
			runtime.destroy();
		}

		// Successful compilation.
		// TODO: [LAS] If in verbose mode, print timing details of compilation "Build successful. (#.###s)"
	}
}
