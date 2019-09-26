/*
 * BuildDirectoryTracer.java
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

package com.avail.builder;

import com.avail.compiler.AvailCompiler;
import com.avail.compiler.ModuleHeader;
import com.avail.compiler.problems.Problem;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.persistence.IndexedRepositoryManager.ModuleArchive;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersion;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersionKey;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation2NotNull;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function3;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static com.avail.builder.AvailBuilder.availExtension;
import static com.avail.utility.Nulls.stripNull;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static java.util.Collections.singleton;
import static java.util.Collections.sort;

/**
 * Used for scanning all modules in all visible Avail directories and their
 * subdirectories.
 */
final class BuildDirectoryTracer
{
	/** The {@link AvailBuilder} for which we're tracing. */
	AvailBuilder availBuilder;

	/** The trace requests that have been scheduled. */
	@GuardedBy("this")
	private final Set<Path> traceRequests = new HashSet<>();

	/** The traces that have been completed. */
	@GuardedBy("this")
	private final Set<Path> traceCompletions = new HashSet<>();

	/** A flag to indicate when all requests have been queued. */
	@GuardedBy("this")
	private boolean allQueued = false;

	/**
	 * How to indicate to the caller that the tracing has completed.  Note that
	 * this may be executed in another {@link Thread} than the one that started
	 * the trace.
	 */
	private final Continuation0 afterTraceCompletes;

	/**
	 * Create a new tracer.
	 *
	 * @param builder
	 *        The {@link AvailBuilder} for which we're tracing.
	 * @param originalAfterTraceCompletes
	 *        The {@link Continuation0} to run when after module has been
	 */
	BuildDirectoryTracer (
		final AvailBuilder builder,
		final Function0<Unit> originalAfterTraceCompletes)
	{
		this.availBuilder = builder;
		this.afterTraceCompletes = () ->
		{
			// Force each repository to commit, since we may have changed the
			// last-access order of some of the caches.
			final ModuleRoots moduleRoots = availBuilder.runtime.moduleRoots();
			for (final ModuleRoot root : moduleRoots.roots())
			{
				root.repository().commit();
			}
			originalAfterTraceCompletes.invoke();
		};
	}

	/**
	 * Schedule a hierarchical tracing of all module files in all visible
	 * subdirectories.  Do not resolve the imports.  Ignore any modules that
	 * have syntax errors in their headers.  Update the repositories with
	 * the latest module version information, or at least cause the version
	 * caches to treat the current versions as having been accessed most
	 * recently.
	 *
	 * <p>Before a module header parsing starts, add the module name to
	 * traceRequests. When a module header's parsing is complete, add it to
	 * traceCompletions, and if the two now have the same size (and all files
	 * have been scanned), commit all repositories and invoke the {@link
	 * #afterTraceCompletes} that was provided in the constructor.</p>
	 *
	 * <p>Note that this method may return before the parsing completes, but
	 * {@link #afterTraceCompletes} will be invoked in some {@link Thread}
	 * either while or after this method runs.</p>
	 *
	 * @param moduleAction
	 *        What to do each time we've extracted or replayed a {@link
	 *        ModuleVersion} from a valid module file.  It's passed a {@link
	 *        Continuation0} to invoke when the module is considered effectively
	 *        processed.
	 */
	void traceAllModuleHeaders (
		final Function3<
			ResolvedModuleName, ModuleVersion, Function0<Unit>, Unit> moduleAction)
	{
		final ModuleRoots moduleRoots = availBuilder.runtime.moduleRoots();
		for (final ModuleRoot moduleRoot : moduleRoots)
		{
			final File rootDirectory = stripNull(moduleRoot.sourceDirectory());
			final Path rootPath = rootDirectory.toPath();
			@SuppressWarnings("TooBroadScope")
			final FileVisitor<Path> visitor = new FileVisitor<Path>()
			{
				@Override
				public FileVisitResult preVisitDirectory (
					final Path dir,
					final BasicFileAttributes unused)
				{
					if (dir.equals(rootPath))
					{
						// The base directory doesn't have the .avail
						// extension.
						return CONTINUE;
					}
					final String localName = dir.toFile().getName();
					if (localName.endsWith(availExtension))
					{
						return CONTINUE;
					}
					return SKIP_SUBTREE;
				}

				@Override
				public FileVisitResult visitFile (
					final Path file,
					final BasicFileAttributes unused)
				{
					final String localName = file.toFile().getName();
					if (!localName.endsWith(availExtension))
					{
						return CONTINUE;
					}
					// It's a module file.
					addTraceRequest(file);
					availBuilder.runtime.execute(
						0,
						() ->
						{
							final StringBuilder builder =
								new StringBuilder(100);
							builder.append("/");
							builder.append(moduleRoot.name());
							final Path relative = rootPath.relativize(file);
							for (final Path element : relative)
							{
								final String part = element.toString();
								builder.append("/");
								assert part.endsWith(availExtension);
								final String noExtension = part.substring(
									0,
									part.length() - availExtension.length());
								builder.append(noExtension);
							}
							final ModuleName moduleName =
								new ModuleName(builder.toString());
							final ResolvedModuleName resolved =
								new ResolvedModuleName(
									moduleName, moduleRoots, false);
							final AtomicBoolean ran = new AtomicBoolean(false);
							traceOneModuleHeader(
								resolved,
								moduleAction,
								() ->
								{
									final boolean oldRan = ran.getAndSet(true);
									assert !oldRan;
									indicateFileCompleted(file);
									return Unit.INSTANCE;
								});
						});
					return CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed (
					final Path file,
					final IOException exception)
				{
					// Ignore the exception and continue.  We're just trying to
					// populate the list of entry points, so it's not something
					// worth reporting.
					return CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory (
					final Path dir,
					final IOException e)
				{
					return CONTINUE;
				}
			};
			try
			{
				Files.walkFileTree(
					rootPath,
					singleton(FileVisitOption.FOLLOW_LINKS),
					Integer.MAX_VALUE,
					visitor);
			}
			catch (final IOException e)
			{
				// Ignore it.
			}
		}
		//noinspection SynchronizeOnThis
		synchronized (this)
		{
			allQueued = true;
			checkForCompletion();
		}
	}

	/**
	 * Add a module name to traceRequests while holding the monitor.
	 *
	 * @param modulePath The {@link Path} to add to the requests.
	 */
	synchronized void addTraceRequest (
		final Path modulePath)
	{
		final boolean added = traceRequests.add(modulePath);
		assert added : "Attempting to trace file " + modulePath + " twice";
	}

	/**
	 * While traceCompletions is still less than traceRequests, suspend the
	 * current {@link Thread}, honoring any {@link InterruptedException}.
	 *
	 * @return Whether the thread was interrupted.
	 */
	@Deprecated
	private synchronized boolean waitAndCheckInterrupts ()
	{
		final long period = 10000;
		long nextReportMillis = System.currentTimeMillis() + period;
		boolean interrupted = false;
		while (traceRequests.size() != traceCompletions.size())
		{
			try
			{
				// Wait for notification, interrupt, or timeout.  The
				// timeout is set a few milliseconds after the target time
				// to avoid repeated wake-ups from imprecision.
				//noinspection SynchronizeOnThis
				wait(nextReportMillis - System.currentTimeMillis() + 5);
				if (System.currentTimeMillis() > nextReportMillis)
				{
					final Set<Path> outstanding = new HashSet<>(traceRequests);
					outstanding.removeAll(traceCompletions);
					if (!outstanding.isEmpty())
					{
						final List<Path> sorted = new ArrayList<>(outstanding);
						sort(sorted);
						final StringBuilder builder = new StringBuilder();
						builder.append("Still tracing files:\n");
						for (final Path path : sorted)
						{
							builder.append('\t');
							builder.append(path);
							builder.append('\n');
						}
						System.err.print(builder);
					}
				}
				nextReportMillis = System.currentTimeMillis() + period;
			}
			catch (final InterruptedException e)
			{
				interrupted = true;
			}
		}
		return interrupted;
	}

	/**
	 * Examine the specified file, adding information about its header to
	 * its associated repository.  If this particular file version has
	 * already been traced, or if an error is encountered while fetching or
	 * parsing the header of the file, simply invoke the {@code
	 * completedAction}.  Otherwise, update the repository and then invoke
	 * the {@code completedAction}.
	 *
	 * @param resolvedName
	 *        The resolved name of the module file to examine.
	 * @param action
	 *        A {@link Continuation2NotNull} to perform with each
	 *        encountered ResolvedModuleName and the associated {@link
	 *        ModuleVersion}, if one can be produced without error by
	 *        parsing or replaying from the repository.
	 * @param completedAction
	 *        The {@link Continuation0} to execute exactly once when the
	 *        examination of this module file has completed.
	 */
	void traceOneModuleHeader (
		final ResolvedModuleName resolvedName,
		final Function3<
			ResolvedModuleName, ModuleVersion, Function0<Unit>, Unit> action,
		final Function0<Unit> completedAction)
	{
		final IndexedRepositoryManager repository = resolvedName.repository();
		repository.commitIfStaleChanges(AvailBuilder.maximumStaleRepositoryMs);
		final File sourceFile = resolvedName.sourceReference();
		final ModuleArchive archive = repository.getArchive(
			resolvedName.rootRelativeName());
		final byte [] digest = archive.digestForFile(resolvedName);
		final ModuleVersionKey versionKey =
			new ModuleVersionKey(resolvedName, digest);
		final @Nullable ModuleVersion existingVersion =
			archive.getVersion(versionKey);
		if (existingVersion != null)
		{
			// This version was already traced and recorded for a
			// subsequent replay... like right now.  Reuse it.
			action.invoke(resolvedName, existingVersion, completedAction);
			return;
		}
		// Trace the source and write it back to the repository.
		AvailCompiler.Companion.create(
			resolvedName,
			availBuilder.textInterface,
			availBuilder.pollForAbort,
			(moduleName, moduleSize, position) -> Unit.INSTANCE,
			compiler ->
			{
				compiler.getCompilationContext().getDiagnostics()
					.setSuccessAndFailureReporters(
						() -> Unit.INSTANCE,
						() ->
						{
							completedAction.invoke();
							return Unit.INSTANCE;
						});
				compiler.parseModuleHeader(
					afterHeader ->
					{
						final ModuleHeader header = stripNull(
							compiler.getCompilationContext().getModuleHeader());
						final List<String> importNames =
							header.getImportedModuleNames();
						final List<String> entryPoints =
							header.getEntryPointNames();
						final ModuleVersion newVersion =
							repository.new ModuleVersion(
								sourceFile.length(),
								importNames,
								entryPoints);
						availBuilder.serialize(header, newVersion);
						archive.putVersion(versionKey, newVersion);
						action.invoke(resolvedName, newVersion, completedAction);
						return Unit.INSTANCE;
					});
				return Unit.INSTANCE;
			},
			completedAction,
			new BuilderProblemHandler(availBuilder, "")
			{
				@Override
				public void handleGeneric (
					final Problem problem,
					final Function1<? super Boolean, Unit> decider)
				{
					// Simply ignore all problems when all we're doing is
					// trying to locate the entry points within any
					// syntactically valid modules.
					decider.invoke(false);
				}
			});
	}

	/**
	 * A module was just traced, so record that fact.  Note that the trace was
	 * either successful or unsuccessful.
	 *
	 * @param modulePath The {@link Path} for which a trace just completed.
	 */
	synchronized void indicateFileCompleted (
		final Path modulePath)
	{
		final boolean added = traceCompletions.add(modulePath);
		assert added : "Completed trace of file " + modulePath + " twice";
		AvailBuilder.log(
			Level.FINEST,
			"Build-directory traced one (%d/%d)",
			traceCompletions.size(),
			traceRequests.size());
		checkForCompletion();
	}

	/**
	 * A transition just happened that might indicate the entire trace has now
	 * completed.
	 */
	private void checkForCompletion ()
	{
		assert Thread.holdsLock(this);
		//noinspection FieldAccessNotGuarded
		if (allQueued && traceRequests.size() == traceCompletions.size())
		{
			afterTraceCompletes.value();
		}
	}
}
