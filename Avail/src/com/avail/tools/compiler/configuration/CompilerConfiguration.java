/**
 * CompilerConfiguration.java
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

package com.avail.tools.compiler.configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.io.StringReader;
import com.avail.annotations.Nullable;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.ModuleRoots;
import com.avail.builder.RenamesFileParser;
import com.avail.builder.RenamesFileParserException;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.tools.configuration.Configuration;
import com.avail.tools.compiler.Compiler;

/**
 * A {@code CompilerConfiguration} instructs a {@linkplain Compiler compiler} on
 * the building of a target Avail {@linkplain ModuleDescriptor module}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class CompilerConfiguration
implements Configuration
{
	/**
	 * The {@linkplain ModuleRoots Avail roots} path.
	 */
	private String availRootsPath = "";

	/** The {@linkplain ModuleRoots Avail roots}. */
	private transient @Nullable ModuleRoots availRoots;

	/**
	 * Answer the {@linkplain ModuleRoots Avail roots} path.
	 *
	 * @return The Avail roots path.
	 */
	public String availRootsPath ()
	{
		return availRootsPath;
	}

	/**
	 * Set the {@linkplain ModuleRoots Avail roots} path.
	 *
	 * @param newPath
	 *        The replacement Avail roots path.
	 */
	public void setAvailRootsPath (final String newPath)
	{
		availRootsPath = newPath;
		availRoots = null;
	}

	/**
	 * Answer the {@linkplain ModuleRoots Avail roots}.
	 *
	 * @return The Avail roots.
	 */
	public ModuleRoots availRoots ()
	{
		ModuleRoots roots = availRoots;
		if (roots == null)
		{
			roots = new ModuleRoots(availRootsPath);
			availRoots = roots;
		}
		return roots;
	}

	/** The path to the {@linkplain RenamesFileParser renames file}. */
	private @Nullable String renamesFilePath;

	/** The {@linkplain ModuleNameResolver module name resolver}. */
	private transient @Nullable ModuleNameResolver moduleNameResolver;

	/**
	 * Answer the path to the {@linkplain RenamesFileParser renames file}.
	 *
	 * @return The renames file path.
	 */
	public String renamesFilePath ()
	{
		return renamesFilePath;
	}

	/**
	 * Set the path to the {@linkplain RenamesFileParser renames file}.
	 *
	 * @param newPath The renames file path.
	 */
	public void setRenamesFilePath (final String newPath)
	{
		renamesFilePath = newPath;
		moduleNameResolver = null;
	}

	/**
	 * Answer the {@linkplain ModuleNameResolver module name resolver} correct
	 * for the current {@linkplain CompilerConfiguration configuration}.
	 *
	 * @return A module name resolver.
	 * @throws FileNotFoundException
	 *         If the {@linkplain RenamesFileParser renames file path} has been
	 *         specified, but is invalid.
	 * @throws RenamesFileParserException
	 *         If the renames file is invalid.
	 */
	public ModuleNameResolver moduleNameResolver ()
		throws FileNotFoundException, RenamesFileParserException
	{
		ModuleNameResolver resolver = moduleNameResolver;
		if (resolver == null)
		{
			final Reader reader;
			final String path = renamesFilePath;
			if (path == null)
			{
				reader = new StringReader("");
			}
			else
			{
				final File file = new File(path);
				reader = new BufferedReader(new FileReader(file));
			}
			final RenamesFileParser renameParser = new RenamesFileParser(
				reader, availRoots());
			resolver = renameParser.parse();
			moduleNameResolver = resolver;
		}
		return resolver;
	}

	@Override
	public boolean isValid ()
	{
		// Just try to create a module name resolver. If this fails, then the
		// configuration is invalid. Otherwise, it should be okay.
		try
		{
			moduleNameResolver();
		}
		catch (final FileNotFoundException|RenamesFileParserException e)
		{
			return false;
		}
		return true;
	}
}
