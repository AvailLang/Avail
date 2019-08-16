/*
 * AvailBuilder.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
 *   may be used to endorse or promote products derived from this software without
 *   specific prior written permission.
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

package com.avail.builder;

import com.avail.AvailRuntime;
import com.avail.annotations.InnerAccess;
import com.avail.compiler.AvailCompiler;
import com.avail.compiler.AvailCompiler.CompilerProgressReporter;
import com.avail.compiler.AvailCompiler.GlobalProgressReporter;
import com.avail.compiler.FiberTerminationException;
import com.avail.compiler.ModuleHeader;
import com.avail.compiler.ModuleImport;
import com.avail.compiler.problems.Problem;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_String;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.io.TextInterface;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.persistence.IndexedRepositoryManager.ModuleArchive;
import com.avail.persistence.IndexedRepositoryManager.ModuleCompilation;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersion;
import com.avail.serialization.MalformedSerialStreamException;
import com.avail.serialization.Serializer;
import com.avail.utility.Graph;
import com.avail.utility.Locks.Auto;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Continuation2;
import com.avail.utility.evaluation.Continuation2NotNull;
import com.avail.utility.evaluation.Continuation3NotNull;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static com.avail.compiler.problems.ProblemType.EXECUTION;
import static com.avail.compiler.problems.ProblemType.PARSE;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY;
import static com.avail.descriptor.FiberDescriptor.commandPriority;
import static com.avail.descriptor.FiberDescriptor.newFiber;
import static com.avail.descriptor.FunctionDescriptor.createFunctionForPhrase;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.ModuleDescriptor.newModule;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.interpreter.Interpreter.debugWorkUnits;
import static com.avail.interpreter.Interpreter.runOutermostFunction;
import static com.avail.utility.Locks.auto;
import static com.avail.utility.StackPrinter.trace;
import static java.lang.String.format;
import static java.util.Collections.*;

/**
 * An {@code AvailBuilder} {@linkplain AvailCompiler compiles} and
 * installs into an {@linkplain AvailRuntime Avail runtime} a target
 * {@linkplain ModuleDescriptor module} and each of its dependencies.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class AvailBuilder
{
	/** The {@linkplain Logger logger}. */
	private static final Logger logger = Logger.getLogger(
		AvailBuilder.class.getName());

	/**
	 * Whether to debug the builder.
	 */
	private static final boolean debugBuilder = false;

	/** A lock for safely manipulating internals of this builder. */
	private final ReadWriteLock builderLock = new ReentrantReadWriteLock();

	/**
	 * Log the specified message if {@linkplain #debugBuilder debugging} is
	 * enabled.
	 *
	 * @param level
	 *        The {@linkplain Level severity level}.
	 * @param format
	 *        The format string.
	 * @param args
	 *        The format arguments.
	 */
	static void log (
		final Level level,
		final String format,
		final Object... args)
	{
		if (debugBuilder)
		{
			if (logger.isLoggable(level))
			{
				logger.log(level, format(format, args));
			}
		}
	}

	/**
	 * Log the specified message if {@linkplain #debugBuilder debugging} is
	 * enabled.
	 *
	 * @param level
	 *        The {@linkplain Level severity level}.
	 * @param exception
	 *        The {@linkplain Throwable exception} that motivated this log
	 *        entry.
	 * @param format
	 *        The format string.
	 * @param args
	 *        The format arguments.
	 */
	@SuppressWarnings("SameParameterValue")
	static void log (
		final Level level,
		final Throwable exception,
		final String format,
		final Object... args)
	{
		if (debugBuilder)
		{
			if (logger.isLoggable(level))
			{
				logger.log(level, format(format, args), exception);
			}
		}
	}

	/**
	 * The maximum age, in milliseconds, that changes should be left uncommitted
	 * in the repository.  A higher value saves space by causing the updated
	 * metadata to be rewritten at a slower rate, but the next build may have to
	 * repeat a bit more work if the previous build attempt failed before its
	 * data could be committed.
	 */
	static final long maximumStaleRepositoryMs = 2000L;

	/**
	 * The file extension for an Avail source {@linkplain ModuleDescriptor
	 * module}.
	 */
	static final String availExtension = ModuleNameResolver.availExtension;

	/**
	 * The {@linkplain AvailRuntime runtime} into which the
	 * {@linkplain AvailBuilder builder} will install the target
	 * {@linkplain ModuleDescriptor module} and its dependencies.
	 */
	public final AvailRuntime runtime;

	/**
	 * The {@linkplain TextInterface text interface} for the {@linkplain
	 * AvailBuilder builder} and downstream components.
	 */
	public TextInterface textInterface;

	/**
	 * Set the {@linkplain TextInterface text interface} for the {@linkplain
	 * AvailBuilder builder} and downstream components.
	 *
	 * @param textInterface
	 *        The text interface.
	 */
	public void setTextInterface (final TextInterface textInterface)
	{
		this.textInterface = textInterface;
	}

	/**
	 * A {@link Graph} of {@link ResolvedModuleName}s, representing the
	 * relationships between all modules currently loaded or involved in the
	 * current build action.  Modules are only added here after they have been
	 * locally traced successfully.
	 */
	public final Graph<ResolvedModuleName> moduleGraph = new Graph<>();

	/**
	 * A map from each {@link ResolvedModuleName} to its currently loaded
	 * {@link LoadedModule}.
	 */
	private final Map<ResolvedModuleName, LoadedModule> allLoadedModules =
		synchronizedMap(new HashMap<>());

	/** Whom to notify when modules load and unload. */
	private final Set<Continuation2<LoadedModule, Boolean>> subscriptions =
		new HashSet<>();

	/**
	 * Record a new party to notify about module loading and unloading.
	 *
	 * @param subscription What to invoke during loads and unloads.
	 */
	public void subscribeToModuleLoading (
		final Continuation2<LoadedModule, Boolean> subscription)
	{
		subscriptions.add(subscription);
	}

	/**
	 * No longer notify the specified party about module loading and unloading.
	 *
	 * @param subscription What to no longer invoke during loads and unloads.
	 */
	@SuppressWarnings("unused")
	public void unsubscribeToModuleLoading (
		final Continuation2<LoadedModule, Boolean> subscription)
	{
		subscriptions.remove(subscription);
	}

	/**
	 * Return a list of modules that are currently loaded.  The returned list is
	 * a snapshot of the state and does not change due to subsequent loads or
	 * unloads.
	 *
	 * @return The list of modules currently loaded.
	 */
	public List<LoadedModule> loadedModulesCopy ()
	{
		try (final Auto ignore = auto(builderLock.readLock()))
		{
			return new ArrayList<>(allLoadedModules.values());
		}
	}

	/**
	 * Look up the currently loaded module with the specified {@linkplain
	 * ResolvedModuleName resolved module name}.  Return {@code null} if the
	 * module is not currently loaded.
	 *
	 * @param resolvedModuleName The name of the module to locate.
	 * @return The loaded module or null.
	 */
	public @Nullable LoadedModule getLoadedModule (
		final ResolvedModuleName resolvedModuleName)
	{
		try (final Auto ignore = auto(builderLock.readLock()))
		{
			return allLoadedModules.get(resolvedModuleName);
		}
	}

	/**
	 * Record a freshly loaded module.  Notify subscribers.
	 *
	 * @param resolvedModuleName The module's resolved name.
	 * @param loadedModule The loaded module.
	 */
	void putLoadedModule (
		final ResolvedModuleName resolvedModuleName,
		final LoadedModule loadedModule)
	{
		try (final Auto ignore = auto(builderLock.writeLock()))
		{
			allLoadedModules.put(resolvedModuleName, loadedModule);
			for (final Continuation2<LoadedModule, Boolean> subscription
				: subscriptions)
			{
				subscription.value(loadedModule, true);
			}
		}
	}

	/**
	 * Record the fresh unloading of a module with the given name.  Notify
	 * subscribers.
	 *
	 * @param resolvedModuleName The unloaded module's resolved name.
	 */
	void removeLoadedModule (
		final ResolvedModuleName resolvedModuleName)
	{
		try (final Auto ignore = auto(builderLock.writeLock()))
		{
			final LoadedModule loadedModule =
				allLoadedModules.get(resolvedModuleName);
			allLoadedModules.remove(resolvedModuleName);
			subscriptions.forEach(
				subscription -> subscription.value(loadedModule, false));
		}
	}

	/**
	 * Reconcile the {@link #moduleGraph} against the loaded modules, removing
	 * any modules from the graph that are not currently loaded.
	 */
	void trimGraphToLoadedModules ()
	{
		for (final ResolvedModuleName moduleName :
			new ArrayList<>(moduleGraph.vertices()))
		{
			if (getLoadedModule(moduleName) == null)
			{
				moduleGraph.exciseVertex(moduleName);
			}
		}
		assert moduleGraph.vertexCount() == allLoadedModules.size();
	}

	/**
	 * Check any invariants of the builder that should hold when it is idle.
	 */
	public void checkStableInvariants ()
	{
		final A_Map loadedRuntimeModules = runtime.loadedModules();
		final int moduleGraphSize = moduleGraph.vertexCount();
		final int allLoadedModulesSize = allLoadedModules.size();
		final int loadedRuntimeModulesSize = loadedRuntimeModules.mapSize();
		assert moduleGraphSize == allLoadedModulesSize;
		assert moduleGraphSize == loadedRuntimeModulesSize;
		for (final ResolvedModuleName graphModuleName :
			new ArrayList<>(moduleGraph.vertices()))
		{
			final A_String qualifiedAvailName =
				stringFrom(graphModuleName.qualifiedName());
			assert allLoadedModules.containsKey(graphModuleName);
			assert loadedRuntimeModules.hasKey(qualifiedAvailName);
			assert allLoadedModules.get(graphModuleName).module.equals(
				loadedRuntimeModules.mapAt(qualifiedAvailName));
		}
	}

	/**
	 * If not-null, an indication of why the current build should stop.
	 * Otherwise the current build should continue.
	 */
	final AtomicReference<String> stopBuildReason = new AtomicReference<>();

	/**
	 * Answer whether the current build should stop.
	 *
	 * @return Whether the build should stop.
	 */
	public boolean shouldStopBuild ()
	{
		return stopBuildReason.get() != null;
	}

	/**
	 * Answer why the current build should stop.  Answer null if a stop is not
	 * currently requested.
	 *
	 * @return Why the build should stop (or null).
	 */
	public @Nullable String stopBuildReason ()
	{
		return stopBuildReason.get();
	}

	/**
	 * Answer why the current build should stop.  Answer null if a stop is not
	 * currently requested.
	 *
	 * @param why Why the build should stop (or null).
	 */
	public void stopBuildReason (final String why)
	{
		try (final Auto ignore = auto(builderLock.writeLock()))
		{
			final @Nullable String old = stopBuildReason.getAndSet(why);
			if (debugWorkUnits)
			{
				System.out.println(
					"*****************************************************\n" +
					"*** stopBuildReason: " + old + " → " + why + "\n" +
					"*****************************************************");
			}
		}
	}

	/**
	 * Clear the indication that the build should stop, presumably in
	 * preparation for the next build.
	 */
	public void clearShouldStopBuild ()
	{
		stopBuildReason.set(null);
	}

	/** Create a {@link BooleanSupplier} for polling for abort requests. */
	public final BooleanSupplier pollForAbort = this::shouldStopBuild;

	/**
	 * Cancel the build at the next convenient stopping point for each module.
	 */
	public void cancel ()
	{
		stopBuildReason("Canceled");
	}

	/**
	 * Given a byte array, compute the {@link CRC32} checksum and append
	 * the {@code int} value as four bytes (Big Endian), answering the
	 * new augmented byte array.
	 *
	 * @param bytes The input bytes.
	 * @return The bytes followed by the checksum.
	 */
	static byte[] appendCRC (final byte[] bytes)
	{
		final CRC32 checksum = new CRC32();
		checksum.update(bytes);
		final int checksumInt = (int) checksum.getValue();
		final ByteBuffer combined = ByteBuffer.allocate(bytes.length + 4);
		combined.put(bytes);
		combined.putInt(checksumInt);
		final byte[] combinedBytes = new byte[bytes.length + 4];
		combined.flip();
		combined.get(combinedBytes);
		return combinedBytes;
	}

	/**
	 * Given an array of bytes, check that the last four bytes, when
	 * treated as a Big Endian unsigned int, agree with the {@link
	 * CRC32} checksum of the bytes excluding the last four.  Fail if
	 * they disagree.  Answer a ByteArrayInputStream on the bytes
	 * excluding the last four.
	 *
	 * @param bytes An array of bytes.
	 * @return A ByteArrayInputStream on the non-CRC portion of the
	 *         bytes.
	 * @throws MalformedSerialStreamException If the CRC check fails.
	 */
	@InnerAccess
	public static ByteArrayInputStream validatedBytesFrom (final byte[] bytes)
		throws MalformedSerialStreamException
	{
		final int storedChecksum =
			ByteBuffer.wrap(bytes).getInt(bytes.length - 4);
		final Checksum checksum = new CRC32();
		checksum.update(bytes, 0, bytes.length - 4);
		if ((int) checksum.getValue() != storedChecksum)
		{
			throw new MalformedSerialStreamException(null);
		}
		return new ByteArrayInputStream(bytes, 0, bytes.length - 4);
	}

	/**
	 * Serialize the specified {@linkplain ModuleHeader module header} into the
	 * {@linkplain ModuleVersion module version}.
	 *
	 * @param header
	 *        A module header.
	 * @param version
	 *        A module version.
	 */
	@InnerAccess void serialize (
		final ModuleHeader header,
		final ModuleVersion version)
	{
		final ByteArrayOutputStream out = new ByteArrayOutputStream(1000);
		final Serializer serializer = new Serializer(out);
		header.serializeHeaderOn(serializer);
		final byte[] bytes = appendCRC(out.toByteArray());
		version.putModuleHeader(bytes);
	}

	/**
	 * How to handle problems during a build.
	 */
	@InnerAccess final ProblemHandler buildProblemHandler;

	/**
	 * How to handle problems during command execution.
	 */
	@InnerAccess final ProblemHandler commandProblemHandler;

	/**
	 * A LoadedModule holds state about what the builder knows about a currently
	 * loaded Avail module.
	 */
	public static class LoadedModule
	{
		/**
		 * The resolved name of this module.
		 */
		public final ResolvedModuleName name;

		/**
		 * The cryptographic {@link ModuleArchive#digestForFile(
		 * ResolvedModuleName) digest} of this module's source code when it was
		 * compiled.
		 */
		@InnerAccess final byte [] sourceDigest;

		/**
		 * The actual {@link A_Module} that was plugged into the {@link
		 * AvailRuntime}.
		 */
		@InnerAccess final A_Module module;

		/** This module's version, which corresponds to the source code. */
		final ModuleVersion version;

		/**
		 * The {@link ModuleCompilation} which was loaded for this module.  This
		 * indicates when the compilation happened, and where in the {@linkplain
		 * IndexedRepositoryManager repository} we can find the {@link
		 * Serializer serialized} module content.
		 */
		final ModuleCompilation compilation;

		/**
		 * Whether this module has been flagged for deletion by the {@link
		 * BuildUnloader}.
		 */
		boolean deletionRequest = false;

		/**
		 * Answer the entry points defined by this loaded module.  Since the
		 * header structure does not depend on syntax declared in other modules,
		 * the entry points are a property of the {@link ModuleVersion}.  That's
		 * the entity associated with particular module source code.
		 *
		 * @return The {@link List} of {@link String}s that are entry points.
		 */
		public List<String> entryPoints ()
		{
			return version.getEntryPoints();
		}

		/**
		 * Construct a new {@code LoadedModule} to represent
		 * information about an Avail module that has been loaded.
		 *
		 * @param name
		 *        The {@linkplain ResolvedModuleName name} of the module.
		 * @param sourceDigest
		 *        The module source's cryptographic digest.
		 * @param module
		 *        The actual {@link A_Module} loaded in the {@link
		 *        AvailRuntime}.
		 * @param version
		 *        The version of the module source.
		 * @param compilation
		 *        Information about the specific {@link ModuleCompilation} that
		 *        is loaded.
		 */
		public LoadedModule (
			final ResolvedModuleName name,
			final byte [] sourceDigest,
			final A_Module module,
			final ModuleVersion version,
			final ModuleCompilation compilation)
		{
			this.name = name;
			this.sourceDigest = sourceDigest.clone();
			this.module = module;
			this.version = version;
			this.compilation = compilation;
		}
	}

	/**
	 * A {@code ModuleTree} is used to capture the structure of nested packages
	 * for use by the graph layout engine.
	 */
	static class ModuleTree
	{
		/** The parent {@link ModuleTree}, or null if this is a root. */
		private @Nullable ModuleTree parent;

		/**
		 * The list of children of this package or module (in which case it must
		 * be empty.
		 */
		@InnerAccess final List<ModuleTree> children = new ArrayList<>();

		/** The private node name to use in the graph layout. */
		@InnerAccess final String node;

		/** The textual label of the corresponding node in the graph layout. */
		@InnerAccess final String label;

		/**
		 * Add a child to this node.
		 *
		 * @param child The {@code ModuleTree} to add as a child.
		 */
		void addChild (final ModuleTree child)
		{
			children.add(child);
			child.parent = this;
		}

		/**
		 * Answer the parent {@code ModuleTree}, or null if this is a root.
		 *
		 * @return The parent {@code ModuleTree} or null.
		 */
		@Nullable ModuleTree parent ()
		{
			return parent;
		}

		/**
		 * Compute a quoted label based on the stored {@link #label} {@link
		 * String}, compensating for Graphviz's idiosyncrasies about delimiters.
		 * In particular, only quotes should be escaped with a backslash.  A
		 * little thought shows that this means a backslash can't be the last
		 * character of the quoted string, so we work around it by appending a
		 * space in that case, which will generally be close enough.
		 *
		 * @return A {@link String} suitable for use as a label.
		 */
		@SuppressWarnings("DynamicRegexReplaceableByCompiledPattern")
		String safeLabel ()
		{
			final String addendum = label.charAt(label.length() - 1) == '\\'
				? " "
				: "";
			return "\"" + label.replaceAll("\"", "\\\"") + addendum + "\"";
		}

		/**
		 * The represented module's {@link ResolvedModuleName resolved name}.
		 */
		@InnerAccess final @Nullable ResolvedModuleName resolvedModuleName;

		/**
		 * Construct a new {@code ModuleTree}.
		 *
		 * @param node The node name.
		 * @param label The label to present.
		 * @param resolvedModuleName The represented {@link ResolvedModuleName}.
		 */
		@InnerAccess ModuleTree (
			final String node,
			final String label,
			final @Nullable ResolvedModuleName resolvedModuleName)
		{
			this.node = node;
			this.label = label;
			this.resolvedModuleName = resolvedModuleName;
		}

		/**
		 * Enumerate the modules in this tree, invoking the {@code enter}
		 * callback before visiting children, and invoking the {@code exit}
		 * callback after.  Both callbacks take an {@code int} indicating the
		 * current depth in the tree, relative to the initial passed value.
		 *
		 * @param enter What to do before each node.
		 * @param exit What to do after each node.
		 * @param depth The depth of the current invocation.
		 */
		void recursiveDo (
			final Continuation2NotNull<ModuleTree, Integer> enter,
			final Continuation2NotNull<ModuleTree, Integer> exit,
			final int depth)
		{
			enter.value(this, depth);
			final int nextDepth = depth + 1;
			for (final ModuleTree child : children)
			{
				child.recursiveDo(enter, exit, nextDepth);
			}
			exit.value(this, depth);
		}
	}

	/**
	 * Construct an {@code AvailBuilder} for the provided runtime.
	 *
	 * @param runtime
	 *        The {@link AvailRuntime} in which to load modules and execute
	 *        commands.
	 */
	@SuppressWarnings("ThisEscapedInObjectConstruction")
	public AvailBuilder (final AvailRuntime runtime)
	{
		this.runtime = runtime;
		this.textInterface = runtime.textInterface();
		buildProblemHandler = new BuilderProblemHandler(
			this, "[%s]: module \"%s\", line %d:%n%s%n");
		commandProblemHandler = new BuilderProblemHandler(
			this, "[%1$s]: %4$s%n");
	}

	/**
	 * Build the {@linkplain ModuleDescriptor target} and its dependencies.
	 *
	 * @param target
	 *        The {@linkplain ModuleName canonical name} of the module that the
	 *        builder must (recursively) load into the {@link AvailRuntime}.
	 * @param localTracker
	 *        A {@linkplain CompilerProgressReporter continuation} that accepts
	 *        <ol>
	 *        <li>the name of the module currently undergoing {@linkplain
	 *        AvailCompiler compilation} as part of the recursive build
	 *        of target,</li>
	 *        <li>the current line number within the current module,</li>
	 *        <li>the position of the ongoing parse (in bytes), and</li>
	 *        <li>the most recently compiled {@linkplain A_Phrase phrase}.</li>
	 *        </ol>
	 * @param globalTracker
	 *        A {@link GlobalProgressReporter} that accepts
	 *        <ol>
	 *        <li>the number of bytes globally processed, and</li>
	 *        <li>the global size (in bytes) of all modules that will be
	 *        built.</li>
	 *        </ol>
	 * @param originalAfterAll
	 *        What to do after building everything.  This may run in another
	 *        {@link Thread}, possibly long after this method returns.
	 */
	public void buildTargetThen (
		final ModuleName target,
		final CompilerProgressReporter localTracker,
		final GlobalProgressReporter globalTracker,
		final Continuation0 originalAfterAll)
	{
		final AtomicBoolean ran = new AtomicBoolean(false);
		final Continuation0 safeAfterAll = () ->
		{
			final boolean old = ran.getAndSet(true);
			assert !old;
			trimGraphToLoadedModules();
			originalAfterAll.value();
		};
		clearShouldStopBuild();
		new BuildUnloader(this).unloadModified();
		if (shouldStopBuild())
		{
			safeAfterAll.value();
			return;
		}
		new BuildTracer(this).traceThen(
			target,
			() ->
			{
				if (shouldStopBuild())
				{
					safeAfterAll.value();
					return;
				}
				final BuildLoader buildLoader =
					new BuildLoader(this, localTracker, globalTracker);
				buildLoader.loadThen(safeAfterAll);
			});
	}

	/**
	 * Build the {@linkplain ModuleDescriptor target} and its dependencies.
	 * Block the current {@link Thread} until it's done.
	 *
	 * @param target
	 *        The {@linkplain ModuleName canonical name} of the module that the
	 *        builder must (recursively) load into the {@link AvailRuntime}.
	 * @param localTracker
	 *        A {@linkplain CompilerProgressReporter continuation} that accepts
	 *        <ol>
	 *        <li>the name of the module currently undergoing {@linkplain
	 *        AvailCompiler compilation} as part of the recursive build
	 *        of target,</li>
	 *        <li>the current line number within the current module,</li>
	 *        <li>the position of the ongoing parse (in bytes), and</li>
	 *        <li>the most recently compiled {@linkplain A_Phrase phrase}.</li>
	 *        </ol>
	 * @param globalTracker
	 *        A {@link GlobalProgressReporter} that accepts
	 *        <ol>
	 *        <li>the number of bytes globally processed, and</li>
	 *        <li>the global size (in bytes) of all modules that will be
	 *        built.</li>
	 *        </ol>
	 */
	public void buildTarget (
		final ModuleName target,
		final CompilerProgressReporter localTracker,
		final GlobalProgressReporter globalTracker)
	{
		final Semaphore semaphore = new Semaphore(0);
		buildTargetThen(
			target, localTracker, globalTracker, semaphore::release);
		semaphore.acquireUninterruptibly();
	}

	/**
	 * Unload the {@linkplain ModuleDescriptor target module} and its
	 * dependents.  If {@code null} is provided, unload all modules.
	 *
	 * @param target
	 *        The {@linkplain ResolvedModuleName resolved name} of the module to
	 *        be unloaded, or {@code null} to unload all modules.
	 */
	public void unloadTarget (final @Nullable ResolvedModuleName target)
	{
		clearShouldStopBuild();
		new BuildUnloader(this).unload(target);
	}

	/**
	 * Generate Stacks documentation for the {@linkplain ModuleDescriptor
	 * target} and its dependencies.
	 *
	 * @param target
	 *        The {@code ModuleName canonical name} of the module for which
	 *        the {@code AvailBuilder builder} must (recursively) generate
	 *        documentation.
	 * @param documentationPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for Stacks
	 *        documentation and data files.
	 */
	public void generateDocumentation (
		final ModuleName target,
		final Path documentationPath)
	{
		clearShouldStopBuild();
		final BuildTracer tracer = new BuildTracer(this);
		tracer.traceThen(
			target,
			() ->
			{
				final DocumentationTracer documentationTracer =
					new DocumentationTracer(this, documentationPath);
				if (!shouldStopBuild())
				{
					documentationTracer.load();
				}
				if (!shouldStopBuild())
				{
					documentationTracer.generate(target);
				}
				trimGraphToLoadedModules();
			});
	}

	/**
	 * Generate a graph.
	 *
	 * @param target
	 *        The resolved name of the module whose ancestors to trace.
	 * @param destinationFile
	 *        Where to write the .gv <strong>dot</strong> format file.
	 */
	public void generateGraph (
		final ResolvedModuleName target,
		final File destinationFile)
	{
		clearShouldStopBuild();
		final BuildTracer tracer = new BuildTracer(this);
		try
		{
			tracer.traceThen(
				target,
				() ->
				{
					final GraphTracer graphTracer = new GraphTracer(
						this, target, destinationFile);
					if (!shouldStopBuild())
					{
						graphTracer.traceGraph();
					}
				});
		}
		finally
		{
			trimGraphToLoadedModules();
		}
	}

	/**
	 * Scan all module files in all visible source directories, passing each
	 * {@link ResolvedModuleName} and corresponding {@link ModuleVersion} to the
	 * provided {@link Continuation2NotNull}.
	 *
	 * <p>Note that the action may be invoked in multiple {@link Thread}s
	 * simultaneously, so the client may need to provide suitable
	 * synchronization.</p>
	 *
	 * <p>Also note that the method only returns after all tracing has
	 * completed.</p>
	 *
	 * @param action
	 *        What to do with each module version.  The third argument to it is
	 *        a {@link Continuation0} to invoke when the module is considered
	 *        processed.
	 */
	public void traceDirectories (
		final Continuation3NotNull
			<ResolvedModuleName, ModuleVersion, Continuation0> action)
	{
		final Semaphore semaphore = new Semaphore(0);
		traceDirectories(action, semaphore::release);
		// Trace is not currently interruptible.
		semaphore.acquireUninterruptibly();
	}

	/**
	 * Scan all module files in all visible source directories, passing each
	 * {@link ResolvedModuleName} and corresponding {@link ModuleVersion} to the
	 * provided {@link Continuation2NotNull}.
	 *
	 * <p>Note that the action may be invoked in multiple {@link Thread}s
	 * simultaneously, so the client may need to provide suitable
	 * synchronization.</p>
	 *
	 * <p>The method may return before tracing has completed, but {@code
	 * afterAll} will eventually be invoked in some {@link Thread} after all
	 * modules have been processed.</p>
	 *
	 * @param action
	 *        What to do with each module version.  A {@link Continuation0} will
	 *        be passed, which should be evaluated to indicate the module has
	 *        been processed.
	 * @param afterAll
	 *        What to do after all of the modules have been processed.
	 */
	public void traceDirectories (
		final Continuation3NotNull
			<ResolvedModuleName, ModuleVersion, Continuation0> action,
		final Continuation0 afterAll)
	{
		new BuildDirectoryTracer(this, afterAll).traceAllModuleHeaders(action);
	}

	/**
	 * An {@code EntryPoint} represents a compiled command. It is used to
	 * disambiguate commands.
	 */
	public static final class CompiledCommand
	{
		/**
		 * The {@linkplain ResolvedModuleName module name} of the {@linkplain
		 * LoadedModule module} that declares the entry point.
		 */
		public final ResolvedModuleName moduleName;

		/** The name of the entry point. */
		public final String entryPointName;

		/**
		 * The compiled {@linkplain A_Phrase phrase} that sends this entry
		 * point.
		 */
		public final A_Phrase phrase;

		@Override
		public String toString ()
		{
			return moduleName.qualifiedName() + " : " + entryPointName;
		}

		/**
		 * Construct a new {@code CompiledCommand}.
		 *
		 * @param moduleName
		 *        The {@linkplain ResolvedModuleName module name} of the
		 *        {@linkplain LoadedModule module} that declares the entry
		 *        point.
		 * @param entryPointName
		 *        The name of the entry point.
		 * @param phrase
		 *        The compiled {@linkplain A_Phrase phrase} that sends this
		 *        entry point.
		 */
		@InnerAccess CompiledCommand (
			final ResolvedModuleName moduleName,
			final String entryPointName,
			final A_Phrase phrase)
		{
			this.moduleName = moduleName;
			this.entryPointName = entryPointName;
			this.phrase = phrase;
		}
	}

	/**
	 * Attempt to unambiguously parse a command.  Each currently loaded module
	 * that defines at least one entry point takes a shot at parsing the
	 * command.  If more than one is successful, report the ambiguity via the
	 * {@code onFailure} continuation.  If none are successful, report the
	 * failure.  If there was exactly one, compile it into a function and invoke
	 * it in a new fiber.  If the function evaluation succeeds, run the {@code
	 * onSuccess} continuation with the function's result, except that if the
	 * function has static type ⊤ always pass {@code nil} instead of the actual
	 * value returned by the function.  If the function evaluation failed,
	 * report the failure.
	 *
	 * @param command
	 *        The command to attempt to parse and run.
	 * @param onAmbiguity
	 *        What to do if the entry point is ambiguous. Accepts a {@linkplain
	 *        List list} of {@linkplain CompiledCommand compiled commands} and
	 *        the {@linkplain Continuation1 continuation} to invoke with the
	 *        selected command (or {@code null} if no command should be run).
	 * @param onSuccess
	 *        What to do if the command parsed and ran to completion.  It should
	 *        be passed both the result of execution and a {@linkplain
	 *        Continuation1 cleanup continuation} to invoke with a {@linkplain
	 *        Continuation0 post-cleanup continuation}.
	 * @param onFailure
	 *        What to do otherwise.
	 */
	public void attemptCommand (
		final String command,
		final Continuation2NotNull<
				List<CompiledCommand>,
				Continuation1NotNull<CompiledCommand>>
			onAmbiguity,
		final Continuation2NotNull<
				AvailObject,
				Continuation1NotNull<Continuation0>>
			onSuccess,
		final Continuation0 onFailure)
	{
		clearShouldStopBuild();
		runtime.execute(
			commandPriority,
			() -> scheduleAttemptCommand(
				command, onAmbiguity, onSuccess, onFailure));
	}

	/**
	 * Schedule an attempt to unambiguously parse a command. Each currently
	 * loaded module that defines at least one entry point takes a shot at
	 * parsing the command.  If more than one is successful, report the
	 * ambiguity via the {@code onFailure} continuation.  If none are
	 * successful, report the failure.  If there was exactly one, compile it
	 * into a function and invoke it in a new fiber.  If the function evaluation
	 * succeeds, run the {@code onSuccess} continuation with the function's
	 * result, except that if the function has static type ⊤ always pass {@code
	 * nil} instead of the actual value returned by the function.  If the
	 * function evaluation failed, report the failure.
	 *
	 * @param command
	 *        The command to attempt to parse and run.
	 * @param onAmbiguity
	 *        What to do if the entry point is ambiguous. Accepts a {@linkplain
	 *        List list} of {@linkplain CompiledCommand compiled commands} and
	 *        the {@linkplain Continuation1NotNull continuation} to invoke with
	 *        the selected command (or {@code null} if no command should be
	 *        run).
	 * @param onSuccess
	 *        What to do if the command parsed and ran to completion.  It should
	 *        be passed both the result of execution and a {@linkplain
	 *        Continuation1NotNull cleanup continuation} to invoke with a
	 *        {@linkplain Continuation0 post-cleanup continuation}.
	 * @param onFailure
	 *        What to do otherwise.
	 */
	@InnerAccess void scheduleAttemptCommand (
		final String command,
		final Continuation2NotNull<
				List<CompiledCommand>,
				Continuation1NotNull<CompiledCommand>>
			onAmbiguity,
		final Continuation2NotNull<
				AvailObject,
				Continuation1NotNull<Continuation0>>
			onSuccess,
		final Continuation0 onFailure)
	{
		final Set<LoadedModule> modulesWithEntryPoints = new HashSet<>();
		for (final LoadedModule loadedModule : loadedModulesCopy())
		{
			if (!loadedModule.entryPoints().isEmpty())
			{
				modulesWithEntryPoints.add(loadedModule);
			}
		}
		if (modulesWithEntryPoints.isEmpty())
		{
			final Problem problem = new Problem(
				null,
				1,
				1,
				EXECUTION,
				"No entry points are defined by loaded modules")
			{
				@Override
				public void abortCompilation ()
				{
					onFailure.value();
				}
			};
			commandProblemHandler.handle(problem);
			return;
		}

		final Map<LoadedModule, List<A_Phrase>> allSolutions =
			synchronizedMap(new HashMap<>());
		final List<Continuation1NotNull<Continuation0>> allCleanups =
			synchronizedList(new ArrayList<>());
		final Map<LoadedModule, List<Problem>> allProblems =
			synchronizedMap(new HashMap<>());
		final AtomicInteger outstanding =
			new AtomicInteger(modulesWithEntryPoints.size());
		final Continuation0 decrement = () ->
		{
			if (outstanding.decrementAndGet() == 0)
			{
				processParsedCommand(
					allSolutions,  // no longer changing
					allProblems,   // no longer changing
					onAmbiguity,
					onSuccess,
					parallelCombine(allCleanups),  // no longer changing
					onFailure);
			}
		};

		for (final LoadedModule loadedModule : modulesWithEntryPoints)
		{
			final A_Module module = newModule(
				stringFrom(
					loadedModule.module.moduleName().asNativeString()
						+ " (command)"));
			final ModuleImport moduleImport =
				ModuleImport.extend(loadedModule.module);
			final ModuleHeader header = new ModuleHeader(loadedModule.name);
			header.importedModules.add(moduleImport);
			header.applyToModule(module, runtime);
			module.addImportedNames(
				loadedModule.module.entryPoints().valuesAsTuple().asSet());
			final AvailCompiler compiler = new AvailCompiler(
				header,
				module,
				stringFrom(command),
				textInterface,
				pollForAbort,
				(moduleName, moduleSize, position) ->
				{
					// do nothing.
				},
				new BuilderProblemHandler(this, "«collection only»")
				{
					@Override
					protected void handleGeneric (
						final Problem problem,
						final Continuation1NotNull<Boolean> decider)
					{
						// Clone the problem message into a new problem to avoid
						// running any cleanup associated with aborting the
						// problem a second time.
						final Problem copy = new Problem(
							problem.moduleName,
							problem.lineNumber,
							problem.characterInFile,
							problem.type,
							"{0}",
							problem.toString())
						{
							@Override
							protected void abortCompilation ()
							{
								// Do nothing.
							}
						};
						allProblems.compute(
							loadedModule,
							(k, oldV) -> {
								final List<Problem> v =
									oldV == null ? new ArrayList<>() : oldV;
								v.add(copy);
								return v;
							});
						decider.value(false);
					}

					@Override
					public void handleInternal (
						final Problem problem,
						final Continuation1NotNull<Boolean> decider)
					{
						textInterface.errorChannel().write(
							problem.toString(),
							null,
							new CompletionHandler<Integer, Void>()
							{
								@Override
								public void completed (
									final @Nullable Integer result,
									final @Nullable Void attachment)
								{
									handleGeneric(problem, decider);
								}

								@Override
								public void failed (
									final @Nullable Throwable exc,
									final @Nullable Void attachment)
								{
									handleGeneric(problem, decider);
								}
							});
					}

					@Override
					public void handleExternal (
						final Problem problem,
						final Continuation1NotNull<Boolean> decider)
					{
						// Same as handleInternal (2015.04.24)
						textInterface.errorChannel().write(
							problem.toString(),
							null,
							new CompletionHandler<Integer, Void>()
							{
								@Override
								public void completed (
									final @Nullable Integer result,
									final @Nullable Void attachment)
								{
									handleGeneric(problem, decider);
								}

								@Override
								public void failed (
									final @Nullable Throwable exc,
									final @Nullable Void attachment)
								{
									handleGeneric(problem, decider);
								}
							});
					}
				});
			compiler.parseCommand(
				(solutions, cleanup) ->
				{
					allSolutions.put(loadedModule, solutions);
					allCleanups.add(cleanup);
					decrement.value();
				},
				decrement);
		}
	}

	/**
	 * Given a {@linkplain Collection collection} of {@linkplain
	 * Continuation1NotNull continuation}s, each of which expects a {@linkplain
	 * Continuation0 continuation} (called the post-continuation activity) that
	 * instructs it on how to proceed when it has completed, produce a single
	 * continuation that evaluates this collection in parallel and defers the
	 * post-continuation activity until every member has completed.
	 *
	 * @param continuations
	 *        A collection of continuations.
	 * @return The combined continuation.
	 */
	@InnerAccess Continuation1NotNull<Continuation0> parallelCombine (
		final Collection<Continuation1NotNull<Continuation0>> continuations)
	{
		final AtomicInteger count = new AtomicInteger(continuations.size());
		return postAction ->
		{
			final Continuation0 decrement = () ->
			{
				if (count.decrementAndGet() == 0)
				{
					postAction.value();
				}
			};
			for (final Continuation1NotNull<Continuation0> continuation :
				continuations)
			{
				runtime.execute(
					commandPriority, () -> continuation.value(decrement));
			}
		};
	}

	/**
	 * Process a parsed command, executing it if there is a single
	 * unambiguous entry point send.
	 *
	 * @param solutions
	 *        A {@linkplain Map map} from {@linkplain LoadedModule loaded
	 *        modules} to the {@linkplain A_Phrase solutions} that they
	 *        produced.
	 * @param problems
	 *        A map from loaded modules to the {@linkplain Problem problems}
	 *        that they encountered.
	 * @param onAmbiguity
	 *        What to do if the entry point is ambiguous. Accepts a {@linkplain
	 *        List list} of {@linkplain CompiledCommand compiled commands} and
	 *        the {@linkplain Continuation1 continuation} to invoke with the
	 *        selected command (or {@code null} if no command should be run).
	 * @param onSuccess
	 *        What to do with the result of a successful unambiguous command.
	 * @param postSuccessCleanup
	 *        How to cleanup after running a successful unambiguous command.
	 * @param onFailure
	 *        What to do after a failure.
	 */
	@InnerAccess void processParsedCommand (
		final Map<LoadedModule, List<A_Phrase>> solutions,
		final Map<LoadedModule, List<Problem>> problems,
		final Continuation2NotNull<
				List<CompiledCommand>,
				Continuation1NotNull<CompiledCommand>>
			onAmbiguity,
		final Continuation2NotNull<
				AvailObject,
				Continuation1NotNull<Continuation0>>
			onSuccess,
		final Continuation1NotNull<Continuation0> postSuccessCleanup,
		final Continuation0 onFailure)
	{
		if (solutions.isEmpty())
		{
			// There were no solutions, so report every problem that was
			// encountered.  Actually, choose the modules that tied for the
			// deepest parse, and only show those problems.
			long deepestPosition = Long.MIN_VALUE;
			final List<Problem> deepestProblems = new ArrayList<>();
			for (final Entry<LoadedModule, List<Problem>> entry :
				problems.entrySet())
			{
				for (final Problem problem : entry.getValue())
				{
					if (problem.characterInFile > deepestPosition)
					{
						deepestPosition = problem.characterInFile;
						deepestProblems.clear();
					}
					if (problem.characterInFile == deepestPosition)
					{
						deepestProblems.add(problem);
					}
				}
			}
			for (final Problem problem : deepestProblems)
			{
				buildProblemHandler.handle(problem);
			}
			onFailure.value();
			return;
		}
		// Filter the solutions to invocations of entry points.
		final List<CompiledCommand> commands = new ArrayList<>();
		for (final Entry<LoadedModule, List<A_Phrase>> entry :
			solutions.entrySet())
		{
			final List<String> moduleEntryPoints = entry.getKey().entryPoints();
			for (final A_Phrase solution : entry.getValue())
			{
				if (solution.isInstanceOfKind(SEND_PHRASE.mostGeneralType()))
				{
					final A_Atom name = solution.apparentSendName();
					final String nameString = name.atomName().asNativeString();
					if (moduleEntryPoints.contains(nameString))
					{
						commands.add(new CompiledCommand(
							entry.getKey().name,
							nameString,
							solution));
					}
				}
			}
		}
		// If there were no commands, then report a problem.
		if (commands.isEmpty())
		{
			final Problem problem = new Problem(
				null,
				1,
				1,
				PARSE,
				"The command could be parsed, but not as an invocation of "
				+ "an entry point.")
			{
				@Override
				public void abortCompilation ()
				{
					onFailure.value();
				}
			};
			commandProblemHandler.handle(problem);
			return;
		}

		final Continuation1NotNull<CompiledCommand> unambiguous =
			command ->
			{
				final A_Phrase phrase = command.phrase;
				final A_Function function =
					createFunctionForPhrase(phrase, nil, 1);
				final A_Fiber fiber = newFiber(
					function.kind().returnType(),
					commandPriority,
					() ->
						formatString("Running command: %s", phrase));
				A_Map fiberGlobals = fiber.fiberGlobals();
				fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
					CLIENT_DATA_GLOBAL_KEY.atom, emptyMap(), true);
				fiber.fiberGlobals(fiberGlobals);
				fiber.textInterface(textInterface);
				fiber.setSuccessAndFailureContinuations(
					result -> onSuccess.value(result, postSuccessCleanup),
					e ->
					{
						if (!(e instanceof FiberTerminationException))
						{
							final Problem problem = new Problem(
								null,
								1,
								1,
								EXECUTION,
								"Error executing command:{0}\n{1}",
								e.getMessage() != null
									? " " + e.getMessage()
									: "",
								trace(e))
							{
								@Override
								public void abortCompilation ()
								{
									onFailure.value();
								}
							};
							commandProblemHandler.handle(problem);
						}
						else
						{
							onFailure.value();
						}
					});
				runOutermostFunction(runtime, fiber, function, emptyList());
			};

		// If the command was unambiguous, then go ahead and run it.
		if (commands.size() == 1)
		{
			unambiguous.value(commands.get(0));
			return;
		}

		// Otherwise, report the possible commands for disambiguation.
		onAmbiguity.value(commands, unambiguous);
	}
}
