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
import com.avail.compiler.AvailCompiler;
import com.avail.compiler.AvailCompilerException;
import com.avail.compiler.Continuation2;
import com.avail.compiler.Mutable;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.levelTwo.L2Interpreter;


/**
 * @author avail
 */
public class AvailCompilerTest
{
	private static final String libraryPath = "avail/";

	private String readSourceFile (final String sourcePath) throws IOException
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

	@Before
	public void initializeAllWellKnownObjects ()
	{
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
	}

	private void compileTier (final String[] modulePaths) throws IOException
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
				builder.append(sourceBuffer, 0, e.position());
				builder.append(e.errorText());
				builder.append(
					sourceBuffer,
					e.position(),
					sourceBuffer.length - e.position());
				System.err.printf("%s%n", builder);
			}
		}
	}

	private static final String[] tier0ModulePaths =
	{
		"0-bootstrapMinimumTheoretical",
		"0-bootstrapRaw"
	};

	@Test
	public void tier0 () throws Exception
	{
		long startTime = System.currentTimeMillis();
		compileTier(tier0ModulePaths);
		System.err.printf("time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

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

		tier1ModulePaths = new String[
             tier0ModulePaths.length + modulePaths.length];
		System.arraycopy(
			tier0ModulePaths, 0, tier1ModulePaths, 0, tier0ModulePaths.length);
		System.arraycopy(
			modulePaths,
			0,
			tier1ModulePaths,
			tier0ModulePaths.length,
			modulePaths.length);
	}

	@Test
	public void tier1 () throws Exception
	{
		long startTime = System.currentTimeMillis();
		compileTier(tier1ModulePaths);
		System.err.printf("time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

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

		tier2ModulePaths = new String[
			tier1ModulePaths.length + modulePaths.length];
		System.arraycopy(
			tier1ModulePaths, 0, tier2ModulePaths, 0, tier1ModulePaths.length);
		System.arraycopy(
			modulePaths,
			0,
			tier2ModulePaths,
			tier1ModulePaths.length,
			modulePaths.length);
	}

	@Test
	public void tier2 () throws Exception
	{
		long startTime = System.currentTimeMillis();
		compileTier(tier2ModulePaths);
		System.err.printf("time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

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

		tier3ModulePaths = new String[
			tier2ModulePaths.length + modulePaths.length];
		System.arraycopy(
			tier2ModulePaths, 0, tier3ModulePaths, 0, tier2ModulePaths.length);
		System.arraycopy(
			modulePaths,
			0,
			tier3ModulePaths,
			tier2ModulePaths.length,
			modulePaths.length);
	}

	@Test
	public void tier3 () throws Exception
	{
		long startTime = System.currentTimeMillis();
		compileTier(tier3ModulePaths);
		System.err.printf("time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}

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

		tier4ModulePaths = new String[
			tier3ModulePaths.length + modulePaths.length];
		System.arraycopy(
			tier3ModulePaths, 0, tier4ModulePaths, 0, tier3ModulePaths.length);
		System.arraycopy(
			modulePaths,
			0,
			tier4ModulePaths,
			tier3ModulePaths.length,
			modulePaths.length);
	}

	@Test
	public void tier4 () throws Exception
	{
		long startTime = System.currentTimeMillis();
		compileTier(tier4ModulePaths);
		System.err.printf(
			"time elapsed = %d%n", System.currentTimeMillis() - startTime);
	}
}
