/*
 * AvailTest.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.builder.AvailBuilder;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.ModuleRoots;
import com.avail.builder.RenamesFileParser;
import com.avail.builder.RenamesFileParserException;
import com.avail.builder.ResolvedModuleName;
import com.avail.builder.UnresolvedDependencyException;
import com.avail.compiler.AvailCompiler.CompilerProgressReporter;
import com.avail.io.TextInterface;
import com.avail.io.TextOutputChannel;
import com.avail.utility.IO;
import com.avail.utility.Mutable;
import com.avail.utility.evaluation.Continuation2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Build the Avail standard library and run all Avail test units.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@TestInstance(Lifecycle.PER_CLASS)
public class AvailTest
{
	/** The {@linkplain ModuleRoots Avail roots}. */
	final ModuleRoots roots;

	/**
	 * Create {@link ModuleRoots} from the information supplied in the
	 * {@code availRoots} system property.
	 *
	 * @return The specified Avail roots.
	 */
	private static ModuleRoots createModuleRoots ()
	{
		final @Nullable String rootsString = System.getProperty(
			"availRoots", null);
		if (rootsString == null)
		{
			fail("system property \"availRoots\" is not set");
		}
		return new ModuleRoots(rootsString);
	}

	/** The {@linkplain ModuleNameResolver module name resolver}. */
	final ModuleNameResolver resolver;

	/**
	 * Create a {@link ModuleNameResolver} using the already created {@linkplain
	 * ModuleRoots Avail roots} and an option renames file supplied in the
	 * {@code availRenames} system property.
	 *
	 * @return The Avail module name resolver.
	 * @throws FileNotFoundException
	 *         If the renames file was specified but not found.
	 * @throws RenamesFileParserException
	 *         If the renames file exists but could not be interpreted correctly
	 *         for any reason.
	 */
	private ModuleNameResolver createModuleNameResolver ()
	throws FileNotFoundException, RenamesFileParserException
	{
		@Nullable Reader reader = null;
		try
		{
			final String renames = System.getProperty("availRenames", null);
			if (renames == null)
			{
				reader = new StringReader("");
			}
			else
			{
				final File renamesFile = new File(renames);
				//noinspection IOResourceOpenedButNotSafelyClosed
				reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(renamesFile), StandardCharsets.UTF_8));
			}
			final RenamesFileParser renameParser = new RenamesFileParser(
				reader, roots);
			return renameParser.parse();
		}
		finally
		{
			IO.closeIfNotNull(reader);
		}
	}

	/** The {@linkplain AvailRuntime Avail runtime}. */
	final AvailRuntime runtime;

	/**
	 * Create an {@link AvailRuntime} from the previously created {@linkplain
	 * ModuleNameResolver Avail module name resolver}.
	 *
	 * @return An Avail runtime.
	 */
	private AvailRuntime createAvailRuntime ()
	{
		return new AvailRuntime(resolver);
	}

	/** The {@linkplain AvailBuilder Avail builder}. */
	final AvailBuilder builder;

	/**
	 * A {@code TestErrorChannel} augments a {@link TextOutputChannel} with
	 * error detection.
	 */
	private static final class TestErrorChannel
	implements TextOutputChannel
	{
		/** The original {@linkplain TextOutputChannel error channel}. */
		private final TextOutputChannel errorChannel;

		/** Has an error been detected? */
		public boolean errorDetected = false;

		/**
		 * Construct a {@code TestErrorChannel} that decorates the specified
		 * {@link TextOutputChannel} with error detection.
		 *
		 * @param errorChannel
		 *        The underlying channel.
		 */
		TestErrorChannel (final TextOutputChannel errorChannel)
		{
			this.errorChannel = errorChannel;
		}

		@Override
		public <A> void write (
			final CharBuffer buffer,
			@Nullable final A attachment,
			final CompletionHandler<Integer, A> handler)
		{
			errorDetected = true;
			errorChannel.write(buffer, attachment, handler);
		}

		@Override
		public <A> void write (
			final String data,
			@Nullable final A attachment,
			final CompletionHandler<Integer, A> handler)
		{
			errorDetected = true;
			errorChannel.write(data, attachment, handler);
		}

		@Override
		public boolean isOpen ()
		{
			return errorChannel.isOpen();
		}

		@Override
		public void close () throws IOException
		{
			errorChannel.close();
		}
	}

	/**
	 * Create an {@link AvailBuilder} from the previously created {@linkplain
	 * AvailRuntime Avail runtime}.
	 *
	 * @return An Avail builder.
	 */
	private AvailBuilder createAvailBuilder ()
	{
		final AvailBuilder b = new AvailBuilder(runtime);
		@SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
		final TestErrorChannel errorChannel = new TestErrorChannel(
			b.textInterface.errorChannel());
		b.setTextInterface(new TextInterface(
			b.textInterface.inputChannel(),
			b.textInterface.outputChannel(),
			errorChannel));
		return b;
	}

	/**
	 * Construct an {@code AvailTest}.
	 *
	 * @throws FileNotFoundException
	 *         If the renames file was specified but not found.
	 * @throws RenamesFileParserException
	 *         If the renames file exists but could not be interpreted correctly
	 *         for any reason.
	 */
	@SuppressWarnings("unused")
	public AvailTest ()
	throws FileNotFoundException, RenamesFileParserException
	{
		roots = createModuleRoots();
		resolver = createModuleNameResolver();
		runtime = createAvailRuntime();
		builder = createAvailBuilder();
	}

	/**
	 * Clear all repositories iff the {@code clearAllRepositories} system
	 * property is defined.
	 */
	@BeforeAll
	private void maybeClearAllRepositories ()
	{
		if (System.getProperty("clearAllRepositories", null) != null)
		{
			clearAllRepositories();
		}
	}

	/**
	 * Clear all Avail binary repositories.
	 */
	private void clearAllRepositories ()
	{
		resolver.moduleRoots().roots().forEach(root ->
			root.repository().clear());
	}

	/**
	 * Clear any error detected on the {@link TestErrorChannel}.
	 */
	@BeforeEach
	private void clearError ()
	{
		((TestErrorChannel) builder
			.textInterface
			.errorChannel()).errorDetected = false;
	}

	/**
	 * Was an error detected on the {@link TestErrorChannel}?
	 *
	 * @return {@code true} if an error was detected, {@code false} otherwise.
	 */
	private boolean errorDetected ()
	{
		return ((TestErrorChannel) builder
			.textInterface
			.errorChannel()).errorDetected;
	}

	/** The global status notification text. */
	@InnerAccess static volatile String globalStatus = "";

	/**
	 * Create a global tracker to store information about the progress on all
	 * modules to be compiled.
	 *
	 * @return A global tracker.
	 */
	private static Continuation2<Long, Long> globalTracker ()
	{
		return (processedBytes, totalBytes) ->
		{
			assert processedBytes != null;
			assert totalBytes != null;

			final int perThousand =
				(int) ((processedBytes * 1000) / totalBytes);
			final float percent = perThousand / 10.0f;
			globalStatus = String.format(
				"\033[33mGlobal\033[0m - %5.1f%%", percent);
		};
	}

	/**
	 * Create a local tracker to share information about the progress of the
	 * compilation of the current module.
	 *
	 * @return A local tracker.
	 */
	private static CompilerProgressReporter localTracker ()
	{
		return (module, moduleSize, position) ->
		{
			assert module != null;
			assert moduleSize != null;
			assert position != null;

			final int percent = (int) ((position * 100) / moduleSize);
			String modName = module.qualifiedName();
			final int maxModuleNameLength = 61;
			final int len = modName.length();
			if (len > maxModuleNameLength)
			{
				modName = "…" + modName.substring(
					len - maxModuleNameLength + 1, len);
			}
			final String status = String.format(
				"%s  |  \033[34m%-61s\033[0m - %3d%%",
				globalStatus,
				modName,
				percent);
			if (System.console() != null)
			{
				final int statusLength = status.length();
				final String finalStatus =
					position.equals(moduleSize)
						? "\n"
						: String.format(
							"%s\033[%dD\033[K",
							status,
							statusLength);
				System.out.print(finalStatus);
			}
			else
			{
				System.out.println(status);
			}
		};
	}

	@DisplayName("Avail standard libraries")
	@ParameterizedTest
	@ValueSource(strings =
		{
			"/avail/Avail",
			"/avail/Convenient ASCII",
			"/avail/Dimensional Analysis",
			"/avail/Hypertext",
			"/avail/Internationalization and Localization",
			"/avail/Availuator",
			"/examples/Examples"
		})
	public void testLoadStandardLibraries (final String moduleName)
	throws UnresolvedDependencyException
	{
		final ResolvedModuleName library = resolver.resolve(
			new ModuleName(moduleName), null);
		builder.buildTarget(library, localTracker(), globalTracker());
		builder.checkStableInvariants();
		assertNotNull(builder.getLoadedModule(library));
		assertFalse(errorDetected());
	}

	@DisplayName("Invalid modules")
	@ParameterizedTest
	@ValueSource(strings =
		{
			"/experimental/builder tests/MutuallyRecursive1",
			"/experimental/builder tests/MutuallyRecursive2",
			"/experimental/builder tests/ShouldFailCompilation",
			"/experimental/builder tests/ShouldFailDuplicateImportVersion",
			"/experimental/builder tests/ShouldFailDuplicateName",
			"/experimental/builder tests/ShouldFailDuplicateVersion",
			"/experimental/builder tests/ShouldFailPragmas",
			"/experimental/builder tests/ShouldFailScanner",
			"/experimental/builder tests/ShouldFailTrace",
			"/experimental/builder tests/ShouldFailWithWrongModuleName"
		})
	public void testBuildInvalidModules (final String moduleName)
	throws UnresolvedDependencyException
	{
		final ResolvedModuleName library = resolver.resolve(
			new ModuleName(moduleName), null);
		builder.buildTarget(library, localTracker(), globalTracker());
		builder.checkStableInvariants();
		assertNull(builder.getLoadedModule(library));
		assertTrue(errorDetected());
	}

	@DisplayName("Avail library unit tests")
	@Test
	public void testAvailUnitTests ()
	throws UnresolvedDependencyException
	{
		final ResolvedModuleName library = resolver.resolve(
			new ModuleName("/avail/Avail Tests"), null);
		builder.buildTarget(library, localTracker(), globalTracker());
		builder.checkStableInvariants();
		assertNotNull(builder.getLoadedModule(library));
		final Semaphore semaphore = new Semaphore(0);
		final Mutable<Boolean> ok = new Mutable<>(false);
		builder.attemptCommand(
			"Run all tests",
			(commands, proceed) ->
			{
				assert commands != null;
				assert proceed != null;
				proceed.value(commands.get(0));
			},
			(result, cleanup) ->
			{
				assert result != null;
				assert cleanup != null;
				cleanup.value(() ->
				{
					ok.value = true;
					semaphore.release();
				});
			},
			semaphore::release
		);
		semaphore.acquireUninterruptibly();
		assertTrue(ok.value);
		assertFalse(errorDetected());
		// TODO: [TLS] Runners.avail needs to be reworked so that Avail unit
		// test failures show up on standard error instead of standard output,
		// otherwise this test isn't nearly as useful as it could be.
	}
}
