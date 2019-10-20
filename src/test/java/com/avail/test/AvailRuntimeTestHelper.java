/*
 * AvailRuntimeTestHelper.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

import com.avail.AvailRuntime;
import com.avail.builder.AvailBuilder;
import com.avail.builder.ModuleName;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.ModuleRoot;
import com.avail.builder.ModuleRoots;
import com.avail.builder.RenamesFileParser;
import com.avail.builder.RenamesFileParserException;
import com.avail.builder.ResolvedModuleName;
import com.avail.builder.UnresolvedDependencyException;
import com.avail.io.TextInterface;
import com.avail.io.TextOutputChannel;
import com.avail.utility.IO;
import kotlin.Unit;

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

import static com.avail.utility.Casts.cast;
import static com.avail.utility.Nulls.stripNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Set up the infrastructure for loading Avail modules and running Avail code.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class AvailRuntimeTestHelper
{
	/** The {@linkplain ModuleNameResolver module name resolver}. */
	public final ModuleNameResolver resolver;

	/** The {@linkplain AvailRuntime Avail runtime}. */
	public final AvailRuntime runtime;

	/** The {@linkplain AvailBuilder Avail builder}. */
	public final AvailBuilder builder;

	/**
	 * A {@code TestErrorChannel} augments a {@link TextOutputChannel} with
	 * error detection.
	 */
	public static final class TestErrorChannel
	implements TextOutputChannel
	{
		/** The original {@linkplain TextOutputChannel error channel}. */
		private final TextOutputChannel errorChannel;

		/** Has an error been detected? */
		boolean errorDetected = false;

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
			b.getTextInterface().getErrorChannel());
		b.setTextInterface(new TextInterface(
			b.getTextInterface().getInputChannel(),
			b.getTextInterface().getOutputChannel(),
			errorChannel));
		return b;
	}

	/**
	 * Create {@link ModuleRoots} from the information supplied in the
	 * {@code availRoots} system property.
	 *
	 * @return The specified Avail roots.
	 */
	static ModuleRoots createModuleRoots ()
	{
		final @Nullable String rootsString = System.getProperty(
			"availRoots", null);
		if (rootsString == null)
		{
			fail("system property \"availRoots\" is not set");
		}
		return new ModuleRoots(rootsString);
	}

	/**
	 * Create a {@link ModuleNameResolver} using the already created {@linkplain
	 * ModuleRoots Avail roots} and an option renames file supplied in the
	 * {@code availRenames} system property.
	 *
	 * @param moduleRoots
	 *        The {@link ModuleRoots} used to map names to modules and packages
	 *        on the file system.
	 * @return The Avail module name resolver.
	 * @throws FileNotFoundException
	 * 	       If the renames file was specified but not found.
	 * @throws RenamesFileParserException
	 *         If the renames file exists but could not be interpreted correctly
	 *         for any reason.
	 */
	static ModuleNameResolver createModuleNameResolver (
		final ModuleRoots moduleRoots)
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
				reader = new BufferedReader(
					new InputStreamReader(
						new FileInputStream(renamesFile),
						StandardCharsets.UTF_8));
			}
			final RenamesFileParser renameParser =
				new RenamesFileParser(reader, moduleRoots);
			return renameParser.parse();
		}
		finally
		{
			IO.closeIfNotNull(reader);
		}
	}

	/**
	 * Create an {@link AvailRuntime} from the provided {@linkplain
	 * ModuleNameResolver Avail module name resolver}.
	 *
	 * @param resolver
	 *        The {@link ModuleNameResolver} for resolving module names.
	 * @return An Avail runtime.
	 */
	static AvailRuntime createAvailRuntime (final ModuleNameResolver resolver)
	{
		return new AvailRuntime(resolver);
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
	public AvailRuntimeTestHelper ()
	throws FileNotFoundException, RenamesFileParserException
	{
		resolver = createModuleNameResolver(createModuleRoots());
		runtime = createAvailRuntime(resolver);
		builder = createAvailBuilder();
	}

	/**
	 * Clear all Avail binary repositories.
	 */
	public void clearAllRepositories ()
	{
		resolver.getModuleRoots().getRoots().forEach(ModuleRoot::clearRepository);
	}

	/**
	 * Clear any error detected on the {@link TestErrorChannel}.
	 */
	public void clearError ()
	{
		final TestErrorChannel channel =
			cast(builder.getTextInterface().getErrorChannel());
		channel.errorDetected = false;
	}

	/**
	 * Shut down the {@link AvailRuntime} after the tests.
	 */
	public void tearDownRuntime ()
	{
		stripNull(runtime).destroy();
	}

	/**
	 * Was an error detected on the {@link TestErrorChannel}?
	 *
	 * @return {@code true} if an error was detected, {@code false} otherwise.
	 */
	public boolean errorDetected ()
	{
		final TestErrorChannel channel =
			cast(builder.getTextInterface().getErrorChannel());
		return channel.errorDetected;
	}

	/** The global status notification text. */
	private volatile String globalStatus = "";

	/**
	 * Create a global tracker to store information about the progress on all
	 * modules to be compiled.
	 *
	 * @param processedBytes
	 *        The total source size in bytes.
	 * @param totalBytes
	 *        The number of bytes of source that have been parsed and
	 *        executed so far.
	 * @return A global tracker.
	 */
	private void globalTrack (
		final long processedBytes,
		final long totalBytes)
	{
		final int perThousand = (int) ((processedBytes * 1000) / totalBytes);
		final float percent = perThousand / 10.0f;
		globalStatus = String.format(
			"\033[33mGlobal\033[0m - %5.1f%%", percent);
	}

	/**
	 * Create a local tracker to share information about the progress of the
	 * compilation of the current module.
	 *
	 * @param moduleName
	 *        The module's name.
	 * @param moduleSize
	 *        The module's source size in bytes.
	 * @param position
	 *        The number of bytes of the module source that have been parsed and
	 *        executed so far.
	 * @return A local tracker.
	 */
	private void localTrack (
		final ModuleName moduleName,
		final long moduleSize,
		final long position)
	{
		final int percent = (int) ((position * 100) / moduleSize);
		String modName = moduleName.getQualifiedName();
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
				position == moduleSize
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
	}

	/**
	 * Load a module with the given full name.  Answer whether the module was
	 * successfully loaded.
	 *
	 * @param moduleName
	 *        The name of the module.
	 * @throws UnresolvedDependencyException
	 *         If the module could not be resolved.
	 * @return {@code true} iff the module was successfully loaded.
	 */
	public boolean loadModule (final String moduleName)
	throws UnresolvedDependencyException
	{
		final ResolvedModuleName library = resolver.resolve(
			new ModuleName(moduleName), null);
		builder.buildTarget(
			library,
			(name, moduleSize, position) ->
			{
				localTrack(name, moduleSize, position);
				return Unit.INSTANCE;
			},
			(moduleBytes, totalBytes) ->
			{
				globalTrack(moduleBytes, totalBytes);
				return Unit.INSTANCE;
			},
			builder.getBuildProblemHandler());
		builder.checkStableInvariants();
		return builder.getLoadedModule(library) != null;
	}
}
