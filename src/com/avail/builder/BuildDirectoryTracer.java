/*
 * BuildDirectoryTracer.java
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

package com.avail.builder;
import com.avail.annotations.InnerAccess;
import com.avail.compiler.AvailCompiler;
import com.avail.compiler.ModuleHeader;
import com.avail.compiler.problems.Problem;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.persistence.IndexedRepositoryManager.ModuleArchive;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersion;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersionKey;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1NotNull;
import com.avail.utility.evaluation.Continuation2NotNull;

import javax.annotation.Nullable;
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
import java.util.logging.Level;

import static com.avail.utility.Nulls.stripNull;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;
import static java.util.Collections.singleton;
import static java.util.Collections.sort;

/**
 * Used for scanning all modules in all visible Avail directories and their
 * subdirectories.
 */
class BuildDirectoryTracer
{
	/** The {@link AvailBuilder} for which we're tracing. */
	AvailBuilder availBuilder;

	/**
	 * The trace requests that have been scheduled.
	 */
	private final Set<Path> traceRequests = new HashSet<>();

	/**
	 * The traces that have been completed.
	 */
	private final Set<Path> traceCompletions = new HashSet<>();

	/**
	 * Create a new tracer.
	 *
	 * @param builder The {@link AvailBuilder} for which we're tracing.
	 */
	BuildDirectoryTracer (final AvailBuilder builder)
	{
		this.availBuilder = builder;
	}

	/**
	 * Schedule a hierarchical tracing of all module files in all visible
	 * subdirectories.  Do not resolve the imports.  Ignore any modules that
	 * have syntax errors in their headers.  Update the repositories with
	 * the latest module version information, or at least cause the version
	 * caches to treat the current versions as having been accessed most
	 * recently.
	 *
	 * <p>When a module header parsing starts, add the module name to
	 * traceRequests. When a module header parsing is complete, add it to
	 * traceCompletions, and if the two now have the same size, send {@link
	 * #notifyAll()} to the {@link BuildTracer}.</p>
	 *
	 * <p>{@linkplain IndexedRepositoryManager#commit() Commit} all affected
	 * repositories at the end.  Return only after all relevant files have
	 * been scanned or looked up in the repositories, or failed somehow.</p>
	 *
	 * @param moduleAction
	 *        What to do each time we've extracted or replayed a {@link
	 *        ModuleVersion} from a valid module file.
	 */
	void traceAllModuleHeaders (
		final Continuation2NotNull<ResolvedModuleName, ModuleVersion>
			moduleAction)
	{
		final ModuleRoots moduleRoots = availBuilder.runtime.moduleRoots();
		for (final ModuleRoot moduleRoot : moduleRoots)
		{
			final File rootDirectory =
				stripNull(moduleRoot.sourceDirectory());
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
					if (localName.endsWith(AvailBuilder.availExtension))
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
					if (!localName.endsWith(AvailBuilder.availExtension))
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
								assert part.endsWith(AvailBuilder.availExtension);
								final String noExtension =
									part.substring(
										0,
										part.length()
											- AvailBuilder.availExtension.length());
								builder.append(noExtension);
							}
							final ModuleName moduleName =
								new ModuleName(builder.toString());
							final ResolvedModuleName resolved =
								new ResolvedModuleName(
									moduleName, moduleRoots, false);
							traceOneModuleHeader(
								resolved,
								moduleAction,
								() -> indicateTraceCompleted(file));
						});
					return CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed (
					final Path file,
					final IOException exception)
				{
					// Ignore the exception and continue.  We're just
					// trying to populate the list of entry points, so it's
					// not something worth reporting.
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
		final boolean interrupted = waitAndCheckInterrupts();
		// Force each repository to commit, since we may have changed the
		// last-access order of some of the caches.
		for (final ModuleRoot root : moduleRoots.roots())
		{
			root.repository().commit();
		}
		if (interrupted)
		{
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Add a module name to traceRequests while holding the monitor.
	 *
	 * @param modulePath The {@link Path} to add to the requests.
	 */
	@InnerAccess synchronized void addTraceRequest (
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
					final Set<Path> outstanding =
						new HashSet<>(traceRequests);
					outstanding.removeAll(traceCompletions);
					if (!outstanding.isEmpty())
					{
						final List<Path> sorted =
							new ArrayList<>(outstanding);
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
	@InnerAccess void traceOneModuleHeader (
		final ResolvedModuleName resolvedName,
		final Continuation2NotNull<ResolvedModuleName, ModuleVersion>
			action,
		final Continuation0 completedAction)
	{
		final IndexedRepositoryManager repository =
			resolvedName.repository();
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
			action.value(resolvedName, existingVersion);
			completedAction.value();
			return;
		}
		// Trace the source and write it back to the repository.
		AvailCompiler.create(
			resolvedName,
			availBuilder.textInterface,
			availBuilder.pollForAbort,
			(moduleName, moduleSize, position) ->
			{
				// do nothing.
			},
			compiler ->
			{
				compiler.compilationContext.diagnostics
					.setSuccessAndFailureReporters(
						() -> { }, completedAction);
				compiler.parseModuleHeader(
					afterHeader ->
					{
						final ModuleHeader header = stripNull(
							compiler.compilationContext.getModuleHeader());
						final List<String> importNames =
							header.importedModuleNames();
						final List<String> entryPoints =
							header.entryPointNames();
						final ModuleVersion newVersion =
							repository.new ModuleVersion(
								sourceFile.length(),
								importNames,
								entryPoints);
						availBuilder.serialize(header, newVersion);
						archive.putVersion(versionKey, newVersion);
						action.value(resolvedName, newVersion);
						completedAction.value();
					});
			},
			completedAction,
			new BuilderProblemHandler(availBuilder, "")
			{
				@Override
				protected void handleGeneric (
					final Problem problem,
					final Continuation1NotNull<Boolean> decider)
				{
					// Simply ignore all problems when all we're doing is
					// trying to locate the entry points within any
					// syntactically valid modules.
					decider.value(false);
				}
			});
	}

	/**
	 * A module was just traced, so record that fact.  Note that the
	 * trace was either successful or unsuccessful.
	 *
	 * @param modulePath The {@link Path} for which a trace just completed.
	 */
	@InnerAccess synchronized void indicateTraceCompleted (
		final Path modulePath)
	{
		final boolean added = traceCompletions.add(modulePath);
		assert added : "Completed trace of file " + modulePath + " twice";
		AvailBuilder.log(
			Level.FINEST,
			"Build-directory traced one (%d/%d)",
			traceCompletions,
			traceRequests);
		// Avoid spurious wake-ups.
		if (traceRequests.size() == traceCompletions.size())
		{
			//noinspection SynchronizeOnThis
			notifyAll();
		}
	}
}
