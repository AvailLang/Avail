/**
 * AbstractAvailTest.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

import java.io.*;
import org.junit.Before;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.compiler.*;
import com.avail.descriptor.*;
import com.avail.utility.*;

/**
 * {@code AbstractAvailTest} defines state and behavior common to actual Avail
 * library test classes.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class AbstractAvailTest
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

	/**
	 * The {@linkplain ModuleRoots Avail module roots}. This should be set by a
	 * static initializer in each subclass.
	 */
	protected static @NotNull ModuleRoots roots;

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
	protected void compile (final @NotNull ModuleName target)
		throws Exception
	{
		try
		{
			final Mutable<ModuleName> lastModule = new Mutable<ModuleName>();
			final AvailBuilder builder = new AvailBuilder(runtime, target);
			builder.buildTarget(
				new Continuation4<ModuleName, Long, Long, Long>()
				{
					@Override
					public void value (
						final @NotNull ModuleName moduleName,
						final @NotNull Long lineNumber,
						final @NotNull Long position,
						final @NotNull Long moduleSize)
					{
						 System.out.printf("[line %d]%n", lineNumber);
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
								"compiling %s ... [bytes remaining = %d]%n",
								moduleName,
								globalCodeSize - position);
						}

						System.out.printf(
							"(%.2f%% done)",
							position * 100.0d / globalCodeSize);
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
				sourceBuffer, 0, (int) e.endOfErrorLine()));
			System.err.append(e.getMessage());
			builder.append(new String(
				sourceBuffer,
				(int) e.endOfErrorLine(),
				Math.min(100, sourceBuffer.length - (int) e.endOfErrorLine())));
			builder.append("...\n");
			System.err.printf("%s%n", builder);
			throw e;
		}
	}
}
