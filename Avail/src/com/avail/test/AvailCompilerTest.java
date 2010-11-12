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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import org.junit.Before;
import org.junit.Test;
import com.avail.annotations.NotNull;
import com.avail.compiler.AvailCompiler;
import com.avail.compiler.AvailCompilerException;
import com.avail.compiler.Continuation2;
import com.avail.compiler.Mutable;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Interpreter;


/**
 * Broad test suite for the Avail compiler, interpreter, and library.
 * 
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public class AvailCompilerTest
{
	/** The root of the library path. */
	private static final String libraryPath = "avail/";

	/**
	 * Read and answer the text of the specified Avail source file.
	 * 
	 * @param sourcePath The path of the source file, relative to the
	 *                   {@linkplain #libraryPath library path}.
	 * @return The text of the specified Avail source file.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private String readSourceFile (final @NotNull String sourcePath)
	throws IOException
	{
		final File sourceFile = new File(libraryPath + sourcePath);
		final char[] sourceBuffer = new char[(int) sourceFile.length()];
		final Reader sourceReader = new BufferedReader(new InputStreamReader(
			new FileInputStream(sourceFile), Charset.forName("UTF-8")));

		int offset = 0;
		int bytesRead = -1;
		while ((bytesRead = sourceReader.read(
			sourceBuffer, offset, sourceBuffer.length - offset)) > 0)
		{
			offset += bytesRead;
		}

		return new String(sourceBuffer, 0, offset);
	}

	/**
	 * Test fixture: clear and then create all special objects well-known to the
	 * Avail runtime.
	 */
	@Before
	public void initializeAllWellKnownObjects ()
	{
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
	}

	/**
	 * Compile the specified Avail modules. The argument is expected to
	 * encapsulate an entire tier of the Avail library.
	 * 
	 * @param modulePaths The paths to the target Avail source files, each
	 *                    relative to the {@linkplain #libraryPath library
	 *                    path}.
	 * @throws IOException
	 *         If an {@linkplain IOException I/O exception} occurs.
	 */
	private void compileTier (final @NotNull String[] modulePaths)
	throws IOException
	{
		AvailCompiler compiler = new AvailCompiler();
		L2Interpreter interpreter = new L2Interpreter();
		final Mutable<String> source = new Mutable<String>();
		try
		{
			for (final String path : modulePaths)
			{
				final String pathWithExtension = path + ".avail";
				System.err.printf("now reading %s%n", path);
				source.value = readSourceFile(pathWithExtension);
				compiler.parseModuleFromStringInterpreterProgressBlock(
					source.value,
					interpreter,
					new Continuation2<Integer, Integer>()
					{
						@Override
						public void value (
							final Integer arg1,
							final Integer arg2)
						{
							System.err.printf(
								"read %d .. %d (%.2f%%)%n",
								arg1,
								arg2,
								(double) (arg2 * 100L) / source.value.length());
						}
					});
			}
		}
		catch (final AvailCompilerException e)
		{
			if (source.value != null)
			{
				final char[] sourceBuffer = source.value.toCharArray();
				final StringBuilder builder = new StringBuilder();
				System.err.append(new String(sourceBuffer, 0, e.position()));
				System.err.append(e.errorText());
				builder.append(
					new String(
						sourceBuffer,
						e.position(),
						sourceBuffer.length - e.position()));
				System.err.printf("%s%n", builder);
			}
		}
	}

	/** The Tier-0 modules. */
	private static final String[] tier0ModulePaths =
	{
		"0-bootstrapMinimumTheoretical",
		"0-bootstrapRaw"
	};

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
		compileTier(tier0ModulePaths);
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

	/** The Tier-1 modules. */
	private static final String[] tier1ModulePaths;

	static
	{
		final String[] modulePaths =
		{
			"1-integer",
			"1-container",
			"1-block",
			"1-method",
			"1-type",
			"1-tuple",
			"1-cyclicType",
			"1-compiledCode",
			"1-primType",
			"1-set",
			"1-boolean",
			"1-main",
			"1-test boolean",
			"1-test optimizer",
			"1-test precedence",
			"1-test"
		};

		final int size = tier0ModulePaths.length + modulePaths.length;
		tier1ModulePaths = new String[size];
		System.arraycopy(
			tier0ModulePaths, 0, tier1ModulePaths, 0, tier0ModulePaths.length);
		System.arraycopy(
			modulePaths,
			0,
			tier1ModulePaths,
			tier0ModulePaths.length,
			modulePaths.length);
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
		compileTier(tier1ModulePaths);
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

	/** The Tier-2 modules. */
	private static final String[] tier2ModulePaths;

	static
	{
		final String[] modulePaths =
		{
			"2-blockA",
			"2-container",
			"2-integer",
			"2-continuation",
			"2-tuple",
			"2-blockB",
			"2-set",
			"2-map",
			"2-character",
			"2-main",
			"2-test-blockA",
			"2-test integer",
			"2-test continuation",
			"2-test tuple",
			"2-test set",
			"2-test map",
			"2-test parser",
			"2-test"
		};

		final int size = tier1ModulePaths.length + modulePaths.length;
		tier2ModulePaths = new String[size];
		System.arraycopy(
			tier1ModulePaths, 0, tier2ModulePaths, 0, tier1ModulePaths.length);
		System.arraycopy(
			modulePaths,
			0,
			tier2ModulePaths,
			tier1ModulePaths.length,
			modulePaths.length);
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
		compileTier(tier2ModulePaths);
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

	/** The Tier-3 modules. */
	private static final String[] tier3ModulePaths;

	static
	{
		final String[] modulePaths =
		{
			"3-object",
			"3-exception",
			"3-main",
			"3-test objects",
			"3-test objectTypes",
			"3-test exceptions",
			"3-test"
		};

		final int size = tier2ModulePaths.length + modulePaths.length;
		tier3ModulePaths = new String[size];
		System.arraycopy(
			tier2ModulePaths, 0, tier3ModulePaths, 0, tier2ModulePaths.length);
		System.arraycopy(
			modulePaths,
			0,
			tier3ModulePaths,
			tier2ModulePaths.length,
			modulePaths.length);
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
		compileTier(tier3ModulePaths);
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

	/** The Tier-4 modules. */
	private static final String[] tier4ModulePaths;

	static
	{
		final String[] modulePaths =
		{
			"4-factory",
			"4-collection",
			"4-iterator",
			"4-tuple iterator",
			"4-graph",
			"4-main",
			"4-test tuple iterator",
			"4-test graph",
			"4-test",
			"Kernel"
		};

		final int size = tier3ModulePaths.length + modulePaths.length;
		tier4ModulePaths = new String[size];
		System.arraycopy(
			tier3ModulePaths, 0, tier4ModulePaths, 0, tier3ModulePaths.length);
		System.arraycopy(
			modulePaths,
			0,
			tier4ModulePaths,
			tier3ModulePaths.length,
			modulePaths.length);
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
		compileTier(tier4ModulePaths);
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

	/** The experimental modules. */
	private static final String[] experimentalModulePaths;

	static
	{
		final String[] modulePaths =
		{
			"Process-process",
			"Backtrack-main",
			"Process-semaphore",
			"Backtrack-test",
			"Compiler-nybblecode encoding",
			"Compiler-nybblecode generation",
			"Compiler-variables",
			"IO-file",
			"IO-output file",
			"Compiler-tokens",
			"Compiler-nybblecodes",
			"Reflection-methods",
			"Reflection-method validation",
			"Reflection-modules",
			"Reflection-parse nodes",
			"Reflection-main",
			"Compiler-parse nodes",
			"Compiler-instruction generator",
			"Compiler-parse tree",
			"IO-objects",
			"Compiler-lexical scanner",
			"IO-object dumping",
			"Compiler-parser",
			"Compiler-main",
			"Compiler-test parser",
			"Curry-main",
			"Curry-test",
			"IO-input file",
			"IO-test files",
			"IO-object loading",
			"IO-main",
			"IO-test dumping",
			"IO-test",
			"Kernel Tests",
			"test everything"
		};

		final int size = tier4ModulePaths.length + modulePaths.length;
		experimentalModulePaths = new String[size];
		System.arraycopy(
			tier4ModulePaths,
			0,
			experimentalModulePaths,
			0,
			tier4ModulePaths.length);
		System.arraycopy(
			modulePaths,
			0,
			experimentalModulePaths,
			tier4ModulePaths.length,
			modulePaths.length);
	}

	/**
	 * Test: Compile the experimental modules.
	 * 
	 * @throws Exception
	 *         If an {@linkplain Exception exception} occurs.
	 */
	@Test
	public void experimental () throws Exception
	{
		long startTime = System.currentTimeMillis();
		compileTier(experimentalModulePaths);
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}
}
