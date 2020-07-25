/*
 * BuildDirectoryTracer.kt
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

package com.avail.builder

import com.avail.builder.ModuleNameResolver.Companion.availExtension
import com.avail.compiler.AvailCompiler
import com.avail.compiler.problems.Problem
import com.avail.persistence.Repository.ModuleVersion
import com.avail.persistence.Repository.ModuleVersionKey
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.FileVisitResult.SKIP_SUBTREE
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.Collections.sort
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import javax.annotation.concurrent.GuardedBy

/**
 * Used for scanning all modules in all visible Avail directories and their
 * subdirectories.
 *
 * @property availBuilder
 *   The [AvailBuilder] for which we're tracing.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Create a new tracer.
 *
 * @param availBuilder
 *   The [AvailBuilder] for which we're tracing.
 * @param originalAfterTraceCompletes
 *   The function to run when after module has been
 */
internal class BuildDirectoryTracer constructor(
	var availBuilder: AvailBuilder,
	originalAfterTraceCompletes: ()->Unit)
{
	/** The trace requests that have been scheduled.  */
	@GuardedBy("this")
	private val traceRequests = HashSet<Path>()

	/** The traces that have been completed.  */
	@GuardedBy("this")
	private val traceCompletions = HashSet<Path>()

	/** A flag to indicate when all requests have been queued.  */
	@GuardedBy("this")
	private var allQueued = false

	/**
	 * How to indicate to the caller that the tracing has completed.  Note that
	 * this may be executed in another [Thread] than the one that started the
	 * trace.
	 */
	private val afterTraceCompletes: ()->Unit

	init
	{
		this.afterTraceCompletes = {
			// Force each repository to commit, since we may have changed the
			// last-access order of some of the caches.
			val moduleRoots = availBuilder.runtime.moduleRoots()
			for (root in moduleRoots.roots)
			{
				root.repository.commit()
			}
			originalAfterTraceCompletes()
		}
	}

	/**
	 * Schedule a hierarchical tracing of all module files in all visible
	 * subdirectories.  Do not resolve the imports.  Ignore any modules that
	 * have syntax errors in their headers.  Update the repositories with the
	 * latest module version information, or at least cause the version caches
	 * to treat the current versions as having been accessed most recently.
	 *
	 * Before a module header parsing starts, add the module name to
	 * traceRequests. When a module header's parsing is complete, add it to
	 * traceCompletions, and if the two now have the same size (and all files
	 * have been scanned), commit all repositories and invoke the
	 * [afterTraceCompletes] that was provided in the constructor.
	 *
	 * Note that this method may return before the parsing completes, but
	 * [afterTraceCompletes] will be invoked in some [Thread] either while or
	 * after this method runs.
	 *
	 * @param moduleAction
	 *   What to do each time we've extracted or replayed a [ModuleVersion] from
	 *   a valid module file.  It's passed a function to invoke when the module
	 *   is considered effectively processed.
	 */
	fun traceAllModuleHeaders(
		moduleAction: (ResolvedModuleName, ModuleVersion, ()->Unit)->Unit)
	{
		val moduleRoots = availBuilder.runtime.moduleRoots()
		for (moduleRoot in moduleRoots)
		{
			val rootDirectory = moduleRoot.sourceDirectory!!
			val rootPath = rootDirectory.toPath()
			val visitor = object : FileVisitor<Path>
			{
				override fun preVisitDirectory(
					dir: Path,
					unused: BasicFileAttributes): FileVisitResult
				{
					if (dir == rootPath)
					{
						// The base directory doesn't have the .avail
						// extension.
						return CONTINUE
					}
					val localName = dir.toFile().name
					return if (localName.endsWith(availExtension))
					{
						CONTINUE
					}
					else SKIP_SUBTREE
				}

				override fun visitFile(
					file: Path,
					unused: BasicFileAttributes): FileVisitResult
				{
					val localName = file.toFile().name
					if (!localName.endsWith(availExtension))
					{
						return CONTINUE
					}
					// It's a module file.
					addTraceRequest(file)
					availBuilder.runtime.execute(0) {
						val builder = StringBuilder(100)
						builder.append("/")
						builder.append(moduleRoot.name)
						val relative = rootPath.relativize(file)
						for (element in relative)
						{
							val part = element.toString()
							builder.append("/")
							assert(part.endsWith(availExtension))
							val noExtension = part.substring(
								0,
								part.length - availExtension.length)
							builder.append(noExtension)
						}
						val moduleName = ModuleName(builder.toString())
						val resolved = ResolvedModuleName(
							moduleName, moduleRoots, false)
						val ran = AtomicBoolean(false)
						traceOneModuleHeader(
							resolved,
							moduleAction,
							{
								val oldRan = ran.getAndSet(true)
								assert(!oldRan)
								indicateFileCompleted(file)
							})
					}
					return CONTINUE
				}

				override fun visitFileFailed(
					file: Path,
					exception: IOException): FileVisitResult
				{
					// Ignore the exception and continue.  We're just trying to
					// populate the list of entry points, so it's not something
					// worth reporting.
					return CONTINUE
				}

				override fun postVisitDirectory(
					dir: Path,
					e: IOException?): FileVisitResult
				{
					return CONTINUE
				}
			}
			try
			{
				Files.walkFileTree(
					rootPath,
					setOf(FileVisitOption.FOLLOW_LINKS),
					Integer.MAX_VALUE,
					visitor)
			}
			catch (e: IOException)
			{
				// Ignore it.
			}
		}

		synchronized(this) {
			allQueued = true
			checkForCompletion()
		}
	}

	/**
	 * Add a module name to traceRequests while holding the monitor.
	 *
	 * @param modulePath
	 *   The [Path] to add to the requests.
	 */
	@Synchronized
	fun addTraceRequest(modulePath: Path)
	{
		val added = traceRequests.add(modulePath)
		assert(added) { "Attempting to trace file $modulePath twice" }
	}

	/**
	 * While traceCompletions is still less than traceRequests, suspend the
	 * current [Thread], honoring any [InterruptedException].
	 *
	 * @return
	 *   Whether the thread was interrupted.
	 */
	@Deprecated("")
	@Synchronized
	private fun waitAndCheckInterrupts(): Boolean
	{
		val period: Long = 10000
		var nextReportMillis = System.currentTimeMillis() + period
		var interrupted = false
		while (traceRequests.size != traceCompletions.size)
		{
			try
			{
				// Wait for notification, interrupt, or timeout.  The
				// timeout is set a few milliseconds after the target time
				// to avoid repeated wake-ups from imprecision.
				@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
				(this as Object).wait(
					nextReportMillis - System.currentTimeMillis() + 5)
				if (System.currentTimeMillis() > nextReportMillis)
				{
					val outstanding = HashSet(traceRequests)
					outstanding.removeAll(traceCompletions)
					if (outstanding.isNotEmpty())
					{
						val sorted = ArrayList(outstanding)
						sort(sorted)
						val builder = StringBuilder()
						builder.append("Still tracing files:\n")
						for (path in sorted)
						{
							builder.append('\t')
							builder.append(path)
							builder.append('\n')
						}
						System.err.print(builder)
					}
				}
				nextReportMillis = System.currentTimeMillis() + period
			}
			catch (e: InterruptedException)
			{
				interrupted = true
			}
		}
		return interrupted
	}

	/**
	 * Examine the specified file, adding information about its header to its
	 * associated repository.  If this particular file version has already been
	 * traced, or if an error is encountered while fetching or parsing the
	 * header of the file, simply invoke the `completedAction`.  Otherwise,
	 * update the repository and then invoke the `completedAction`.
	 *
	 * @param resolvedName
	 *   The resolved name of the module file to examine.
	 * @param action
	 *   A function to perform with each encountered ResolvedModuleName and the
	 *   associated [ModuleVersion], if one can be produced without error by
	 *   parsing or replaying from the repository.
	 * @param completedAction
	 *   The function to execute exactly once when the examination of this
	 *   module file has completed.
	 */
	fun traceOneModuleHeader(
		resolvedName: ResolvedModuleName,
		action: (ResolvedModuleName, ModuleVersion, ()->Unit)->Unit,
		completedAction: ()->Unit)
	{
		val repository = resolvedName.repository
		repository.commitIfStaleChanges(AvailBuilder.maximumStaleRepositoryMs)
		val sourceFile = resolvedName.sourceReference
		val archive = repository.getArchive(
			resolvedName.rootRelativeName)
		val digest = archive.digestForFile(resolvedName)
		val versionKey = ModuleVersionKey(resolvedName, digest)
		val existingVersion = archive.getVersion(versionKey)
		if (existingVersion !== null)
		{
			// This version was already traced and recorded for a subsequent
			// replay... like right now.  Reuse it.
			action(resolvedName, existingVersion, completedAction)
			return
		}
		// Trace the source and write it back to the repository.
		AvailCompiler.create(
			resolvedName,
			availBuilder.textInterface,
			availBuilder.pollForAbort,
			{ _, _, _, _ -> },
			completedAction,
			object : BuilderProblemHandler(availBuilder, "")
			{
				override fun handleGeneric(
					problem: Problem,
					decider: (Boolean)->Unit)
				{
					// Simply ignore all problems when all we're doing is trying
					// to locate the entry points within any syntactically valid
					// modules.
					decider(false)
				}
			}
		) {
			compiler ->
			compiler.compilationContext.diagnostics
				.setSuccessAndFailureReporters({}, completedAction)
			compiler.parseModuleHeader {
				val header = compiler.compilationContext.moduleHeader!!
				val importNames = header.importedModuleNames
				val entryPoints = header.entryPointNames
				val newVersion = repository.ModuleVersion(
					sourceFile.length(),
					importNames,
					entryPoints)
				availBuilder.serialize(header, newVersion)
				archive.putVersion(versionKey, newVersion)
				action(resolvedName, newVersion, completedAction)
			}
		}
	}

	/**
	 * A module was just traced, so record that fact.  Note that the trace was
	 * either successful or unsuccessful.
	 *
	 * @param modulePath
	 *   The [Path] for which a trace just completed.
	 */
	@Synchronized
	fun indicateFileCompleted(modulePath: Path)
	{
		val added = traceCompletions.add(modulePath)
		assert(added) { "Completed trace of file $modulePath twice" }
		AvailBuilder.log(
			Level.FINEST,
			"Build-directory traced one (%d/%d)",
			traceCompletions.size,
			traceRequests.size)
		checkForCompletion()
	}

	/**
	 * A transition just happened that might indicate the entire trace has now
	 * completed.
	 */
	private fun checkForCompletion()
	{
		assert(Thread.holdsLock(this))

		if (allQueued && traceRequests.size == traceCompletions.size)
		{
			afterTraceCompletes()
		}
	}
}
