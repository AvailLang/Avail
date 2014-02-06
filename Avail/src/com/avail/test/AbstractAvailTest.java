/**
 * AbstractAvailTest.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static org.junit.Assert.*;
import java.io.*;
import org.junit.*;
import com.avail.AvailRuntime;
import com.avail.annotations.*;
import com.avail.builder.*;
import com.avail.compiler.AbstractAvailCompiler.*;
import com.avail.descriptor.*;
import com.avail.utility.*;
import com.avail.utility.evaluation.*;

/**
 * {@code AbstractAvailTest} defines state and behavior common to actual Avail
 * library test classes.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class AbstractAvailTest
{
	/**
	 * The {@linkplain ModuleRoots Avail module roots}. This should be set by a
	 * static initializer in each subclass.
	 */
	protected static @Nullable ModuleRoots roots;

	/** The {@linkplain AvailRuntime Avail runtime}. */
	private @Nullable AvailRuntime runtime;

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
		final ModuleRoots theRoots = roots;
		assert theRoots != null;
		final ModuleNameResolver theResolver =
			new RenamesFileParser(new StringReader(""), theRoots).parse();
		runtime = new AvailRuntime(theResolver);
	}

	/**
	 * Test fixture: clear all references to AvailObjects after a test.
	 */
	@After
	public void clearAllWellKnownObjects ()
	{
		final AvailRuntime theRuntime = runtime;
		if (theRuntime != null)
		{
			theRuntime.destroy();
			runtime = null;
		}
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
	protected void compile (final ModuleName target) throws Exception
	{
		final MutableOrNull<ModuleName> lastModule =
			new MutableOrNull<>();
		final AvailRuntime theRuntime = runtime;
		assert theRuntime != null;
		final AvailBuilder builder = new AvailBuilder(
			theRuntime,
			new CompilerProgressReporter()
			{
				@Override
				public void value (
					final @Nullable ModuleName moduleName,
					final @Nullable Long lineNumber,
					final @Nullable Long position,
					final @Nullable Long moduleSize)
				{
					assert lineNumber != null;
					System.out.printf("%nline %d", lineNumber);
				}
			},
			new Continuation3<ModuleName, Long, Long>()
			{
				@Override
				public void value (
					final @Nullable ModuleName moduleName,
					final @Nullable Long position,
					final @Nullable Long globalCodeSize)
				{
					assert moduleName != null;
					assert position != null;
					assert globalCodeSize != null;
					if (!moduleName.equals(lastModule.value))
					{
						lastModule.value = moduleName;
						System.out.printf(
							"%ncompiling %s ... [bytes remaining = %d]",
							moduleName,
							globalCodeSize - position);
					}

					System.out.printf(
						"%n(%.2f%%)",
						position * 100.0d / globalCodeSize);
				}
			});
		builder.buildTarget(target);
		assertFalse(builder.shouldStopBuild);
	}
}
