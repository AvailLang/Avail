/**
 * test/AvailCompilerTest.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.junit.Before;
import org.junit.Test;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.compiler.AvailBuilder;
import com.avail.compiler.AvailCompilerException;
import com.avail.compiler.Continuation3;
import com.avail.compiler.ModuleName;
import com.avail.compiler.ModuleNameResolver;
import com.avail.compiler.ModuleRoots;
import com.avail.compiler.Mutable;
import com.avail.compiler.RenamesFileParser;
import com.avail.compiler.RenamesFileParserException;
import com.avail.compiler.ResolvedModuleName;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ModuleDescriptor;

/**
 * Broad test suite for the Avail compiler, interpreter, and library.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class AvailCompilerTest
{
	/**
	 * Read and answer the text of the specified {@linkplain ModuleDescriptor
	 * Avail module}.
	 *
	 * @param sourceFile
	 *        A {@linkplain File file reference} to an {@linkplain
	 *        ModuleDescriptor Avail module}.
	 * @return The text of the specified Avail source file, or {@code null} if
	 *         the source could not be retrieved.
	 */
	private static String readSourceFile (
		final @NotNull File sourceFile)
	{
		try
		{
			final char[] sourceBuffer = new char[(int) sourceFile.length()];
			final Reader sourceReader =
				new BufferedReader(new FileReader(sourceFile));

			int offset = 0;
			int bytesRead = -1;
			while ((bytesRead = sourceReader.read(
				sourceBuffer, offset, sourceBuffer.length - offset)) > 0)
			{
				offset += bytesRead;
			}

			return new String(sourceBuffer, 0, offset);
		}
		catch (final IOException e)
		{
			return null;
		}
	}

	/** The {@linkplain ModuleRoots Avail module roots}. */
	private static final @NotNull ModuleRoots roots =
		new ModuleRoots("avail=" + new File("avail").getAbsolutePath());

	/** The {@linkplain ModuleNameResolver module name resolver}. */
	private ModuleNameResolver resolver;

	/** The {@linkplain AvailRuntime Avail runtime}. */
	private AvailRuntime runtime;

	/**
	 * Test fixture: clear and then create all special objects well-known to the
	 * Avail runtime.
	 *
	 * @throws RenamesFileParserException
	 *         Never happens.
	 */
	@Before
	public void initializeAllWellKnownObjects ()
		throws RenamesFileParserException
	{
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
		resolver = new RenamesFileParser(new StringReader(""), roots).parse();
		runtime = new AvailRuntime(resolver);
	}

	/**
	 * Compile the specified Avail {@linkplain ModuleDescriptor module}.
	 *
	 * @param target
	 *        The {@linkplain ModuleName fully-qualified name} of the target
	 *        {@linkplain ModuleDescriptor module}.
	 * @throws Exception
	 *         If an {@linkplain Exception exception} occurs.
	 */
	private void compile (final @NotNull ModuleName target)
		throws Exception
	{
		try
		{
			final Mutable<ModuleName> lastModule = new Mutable<ModuleName>();
			final AvailBuilder builder = new AvailBuilder(runtime, target);
			builder.buildTarget(
				new Continuation3<ModuleName, Long, Long>()
				{
					@Override
					public void value (
						final @NotNull ModuleName moduleName,
						final @NotNull Long position,
						final @NotNull Long moduleSize)
					{
						// Do nothing.
					}
				},
				new Continuation3<ModuleName, Long, Long>()
				{
					@Override
					public void value (
						final @NotNull ModuleName moduleName,
						final @NotNull Long position,
						final @NotNull Long globalCodeSize)
					{
						if (!moduleName.equals(lastModule.value))
						{
							lastModule.value = moduleName;
							System.out.printf(
								"now compiling %s ... [bytes remaining = %d]%n",
								moduleName,
								globalCodeSize - position);
						}

						System.out.printf(
							"\t... bytes processed = %d (%.2f%% done)%n",
							position,
							(double) ((position * 100L) / globalCodeSize));
					}
				});
		}
		catch (final AvailCompilerException e)
		{
			final ResolvedModuleName resolvedName =
				resolver.resolve(e.moduleName());
			if (resolvedName == null)
			{
				System.err.printf("%s%n", e.getMessage());
				throw e;
			}

			final String source = readSourceFile(resolvedName.fileReference());
			if (source == null)
			{
				System.err.printf("%s%n", e.getMessage());
				throw e;
			}

			final char[] sourceBuffer = source.toCharArray();
			final StringBuilder builder = new StringBuilder();
			System.err.append(new String(
				sourceBuffer, 0, (int) e.position()));
			System.err.append(e.getMessage());
			builder.append(new String(
				sourceBuffer,
				(int) e.position(),
				sourceBuffer.length - (int) e.position()));
			System.err.printf("%s%n", builder);
		}
	}

	/**
	 * Test: Compile the Tier-0 modules.
	 *
	 * @throws Exception
	 *         If an {@linkplain Exception exception} occurs.
	 */
	@Test
	public void tier0 () throws Exception
	{
		long startTime = System.currentTimeMillis();
		compile(new ModuleName(
			"/avail/Kernel/Tier-4/Tier-3/Tier-2/Tier-1/Tier-0"));
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

	/**
	 * Test: Compile the Tier-1 modules.
	 *
	 * @throws Exception
	 *         If an {@linkplain Exception exception} occurs.
	 */
	@Test
	public void tier1 () throws Exception
	{
		long startTime = System.currentTimeMillis();
		compile(new ModuleName("/avail/Kernel/Tier-4/Tier-3/Tier-2/Tier-1"));
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

	/**
	 * Test: Compile the Tier-2 modules.
	 *
	 * @throws Exception
	 *         If an {@linkplain Exception exception} occurs.
	 */
	@Test
	public void tier2 () throws Exception
	{
		long startTime = System.currentTimeMillis();
		compile(new ModuleName("/avail/Kernel/Tier-4/Tier-3/Tier-2"));
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

	/**
	 * Test: Compile the Tier-3 modules.
	 *
	 * @throws Exception
	 *         If an {@linkplain Exception exception} occurs.
	 */
	@Test
	public void tier3 () throws Exception
	{
		long startTime = System.currentTimeMillis();
		compile(new ModuleName("/avail/Kernel/Tier-4/Tier-3"));
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

	/**
	 * Test: Compile the Tier-4 modules.
	 *
	 * @throws Exception
	 *         If an {@linkplain Exception exception} occurs.
	 */
	@Test
	public void tier4 () throws Exception
	{
		long startTime = System.currentTimeMillis();
		compile(new ModuleName("/avail/Kernel/Tier-4"));
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

	/**
	 * Test: Compile all non-experimental modules.
	 *
	 * @throws Exception
	 *         If an {@linkplain Exception exception} occurs.
	 */
	@Test
	public void everything () throws Exception
	{
		long startTime = System.currentTimeMillis();
		compile(new ModuleName("/avail/Test-Everything"));
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

}
