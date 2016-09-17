/**
 * AvailServerConfiguration.java
 * Copyright © 1993-2016, The Avail Foundation, LLC.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.Nullable;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.ModuleRoots;
import com.avail.builder.RenamesFileParser;
import com.avail.builder.RenamesFileParserException;
import com.avail.server.AvailServer;
import com.avail.tools.compiler.configuration.CompilerConfiguration;
import com.avail.utility.configuration.Configuration;

/**
 * An {@code AvailServerConfiguration} specifies the operational parameters of
 * an {@link AvailServer}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailServerConfiguration
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
		final String path = renamesFilePath;
		assert path != null;
		return path;
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
				reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(file), StandardCharsets.UTF_8));
			}
			final RenamesFileParser renameParser = new RenamesFileParser(
				reader, availRoots());
			resolver = renameParser.parse();
			moduleNameResolver = resolver;
		}
		return resolver;
	}

	/**
	 * The path to the web root, or {@code null} if document requests should not
	 * be honored by the {@link AvailServer}.
	 */
	private @Nullable String documentPath;

	/** The server authority. */
	private String serverAuthority = "localhost";

	/**
	 * Answer the server authority.
	 *
	 * @return The server authority.
	 */
	public String serverAuthority ()
	{
		return serverAuthority;
	}

	/**
	 * Set the server authority.
	 *
	 * @param serverAuthority
	 *        The server authority.
	 */
	public void setServerAuthority (final String serverAuthority)
	{
		this.serverAuthority = serverAuthority;
	}

	/** The server port. */
	private int serverPort = 40000;

	/**
	 * Answer the server port.
	 *
	 * @return The server port.
	 */
	public int serverPort ()
	{
		return serverPort;
	}

	/**
	 * Set the server port.
	 *
	 * @param serverPort
	 *        The server port.
	 */
	public void setServerPort (final int serverPort)
	{
		this.serverPort = serverPort;
	}

	/**
	 * Answer the path to the web root, or {@code null} if document requests
	 * should not be honored by the {@link AvailServer}.
	 *
	 * @return The path to the web root, or {@code null} if document requests
	 *         should not be honored by the {@link AvailServer}.
	 */
	public @Nullable String documentPath ()
	{
		return documentPath;
	}

	/**
	 * Set the document path to the web root.
	 *
	 * @param documentPath
	 *        The path to the web root, or {@code null} if document requests
	 *        should not be honored by the {@link AvailServer}.
	 */
	public void setDocumentPath (final String documentPath)
	{
		this.documentPath = documentPath;
	}

	/**
	 * Should the {@linkplain AvailServer server} serve up documents within the
	 * web root?
	 *
	 * @return {@code true} if the server should serve up documents from the web
	 *         root, {@code false} otherwise.
	 */
	public boolean shouldServeDocuments ()
	{
		return documentPath != null;
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
