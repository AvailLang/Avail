/*
 * BuildDirectoryTracer.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.builder

import avail.compiler.AvailCompiler
import avail.compiler.problems.Problem
import avail.descriptor.fiber.FiberDescriptor.Companion.tracerPriority
import avail.error.ErrorCode
import avail.persistence.cache.record.ModuleVersion
import avail.persistence.cache.record.ModuleVersionKey
import avail.resolver.ModuleRootResolver
import avail.utility.parallelDoThen
import java.net.URI
import java.util.Collections.synchronizedSet
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
class BuildDirectoryTracer constructor(
	var availBuilder: AvailBuilder,
	originalAfterTraceCompletes: ()->Unit)
{
	/** The trace requests that have been scheduled. */
	@GuardedBy("this")
	private val traceRequests =
		synchronizedSet(mutableSetOf<Pair<ModuleRootResolver, URI>>())

	/** The traces that have been completed. */
	@GuardedBy("this")
	private val traceCompletions =
		synchronizedSet(mutableSetOf<Pair<ModuleRootResolver, URI>>())

	/** A flag to indicate when all requests have been queued. */
	@GuardedBy("this")
	private var allQueued = false

	/**
	 * How to indicate to the caller that the tracing has completed.  Note that
	 * this may be executed in another [Thread] than the one that started the
	 * trace.
	 */
	private val afterTraceCompletes =
	{
		// Force each repository to commit, since we may have changed the
		// last-access order of some of the caches.
		val moduleRoots = availBuilder.runtime.moduleRoots()
		for (root in moduleRoots.roots)
		{
			root.repository.commit()
		}
		originalAfterTraceCompletes()
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
		moduleRoots.toList().parallelDoThen(
			action = { root, after ->
				traceAllModuleHeaders(
					root.resolver,
					moduleAction,
					{ _, _, _ ->
						// Ignore exceptions during header tracing.
					},
					{ after() })
			},
			then = {
				synchronized(this) {
					allQueued = true
					checkForCompletion()
				}
			})
	}

	/**
	 * Schedule a hierarchical tracing of all module files in all visible
	 * subdirectories of the associated [ModuleRoot].  Do not resolve the
	 * imports.  Ignore any modules that have syntax errors in their headers.
	 * Update the repositories with the latest module version information, or at
	 * least cause the version caches to treat the current versions as having
	 * been accessed most recently.
	 *
	 * Before a module header parsing starts, add the module name to the
	 * [BuildDirectoryTracer] trace requests. When a module header's parsing is
	 * complete, add it to trace completions.
	 *
	 * @param resolver
	 *   The [ModuleRootResolver] used for reading the files.
	 * @param moduleAction
	 *   What to do each time we've extracted or replayed a [ModuleVersion] from
	 *   a valid module file.  It's passed a function to invoke when the module
	 *   is considered effectively processed.
	 * @param moduleFailureHandler
	 *   A function that accepts the relative path of a file that failed the
	 *   trace, an [ErrorCode] that describes the nature of the failure and an
	 *   `nullable` [Throwable]. This is called once for each individual module
	 *   that failed tracing; hence this failure handler can be called many
	 *   times for multiple failed modules.
	 * @param afterAllQueued
	 *   The lambda to run after all modules are queued. It accepts the number
	 *   of module root module references visited.
	 */
	private fun traceAllModuleHeaders(
		resolver: ModuleRootResolver,
		moduleAction: (ResolvedModuleName, ModuleVersion, ()->Unit)->Unit,
		moduleFailureHandler: (String, ErrorCode, Throwable?) -> Unit,
		afterAllQueued: (Int) -> Unit)
	{
		resolver.provideModuleRootTree(
			successHandler = { refRoot ->
				refRoot.walkChildrenThen(
					visitResources = false,
					withReference = { visited ->
						if (!visited.hasModuleHeader)
						{
							// We don't want to trace packages.
							return@walkChildrenThen
						}
						require(visited.isModule)
						{
							"BuildDirectoryTracer only operates on packages " +
								"and modules, but received $visited"
						}
						// It's a module file.
						addTraceRequest(resolver, visited.uri)
						// It wasn't already scanned yet (multiple scans
						// could happen with overlapping roots).
						availBuilder.runtime.execute(tracerPriority) {
							val resolved = ResolvedModuleName(
								ModuleName(visited.qualifiedName),
								availBuilder.runtime.moduleRoots(),
								visited,
								false)
							val ran = AtomicBoolean(false)
							traceOneModuleHeader(resolved, moduleAction) {
								val oldRan = ran.getAndSet(true)
								assert(!oldRan) {
									"${visited.localName} already ran " +
										"BuildDirectoryTracer.traceOneModuleHeader"
								}
								indicateFileCompleted(resolver, visited.uri)
							}
						}
					},
					afterAllVisited = afterAllQueued)
			},
			failureHandler = { code, ex ->
				moduleFailureHandler(
					"Could not get ${resolver.moduleRoot.name} ResolverReference",
					code,
					ex)
			})
	}

	/**
	 * Add a module name to traceRequests while holding the monitor.
	 *
	 * @param moduleRootResolver
	 *   The [ModuleRootResolver] that distinguishes this trace of a file from
	 *   others, in case module root directories overlap.
	 * @param moduleURI
	 *   The [URI] to add to the requests.
	 */
	@Synchronized
	fun addTraceRequest(
		moduleRootResolver: ModuleRootResolver,
		moduleURI: URI)
	{
		val added = traceRequests.add(moduleRootResolver to moduleURI)
		assert(added) { "Attempting to trace file $moduleURI twice" }
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
	private fun traceOneModuleHeader(
		resolvedName: ResolvedModuleName,
		action: (ResolvedModuleName, ModuleVersion, ()->Unit)->Unit,
		completedAction: ()->Unit)
	{
		val repository = resolvedName.repository
		repository.commitIfStaleChanges(AvailBuilder.maximumStaleRepositoryMs)
		val sourceReference = resolvedName.resolverReference
		val archive = repository.getArchive(resolvedName.rootRelativeName)
		archive.digestForFile(
			resolvedModuleName = resolvedName,
			forceRefreshDigest = false,
			withDigest = { digest ->
				val versionKey = ModuleVersionKey(resolvedName, digest)
				val existingVersion = archive.getVersion(versionKey)
				if (existingVersion !== null)
				{
					// This version was already traced and recorded for a
					// subsequent replay... like right now.  Reuse it.
					action(resolvedName, existingVersion, completedAction)
					return@digestForFile
				}
				// Trace the source and write it back to the repository.
				AvailCompiler.create(
					resolvedName,
					availBuilder.runtime,
					availBuilder.textInterface,
					availBuilder.pollForAbort,
					{ _, _, _, _, _ -> },
					completedAction,
					object : BuilderProblemHandler(availBuilder, "")
					{
						override fun handleGeneric(
							problem: Problem,
							decider: (Boolean)->Unit)
						{
							// Simply ignore all problems when all we're doing
							// is trying to locate the entry points within any
							// syntactically valid modules.
							decider(false)
						}
					}
				) { compiler ->
					compiler.compilationContext.diagnostics
						.setSuccessAndFailureReporters({}, completedAction)
					compiler.parseModuleHeader { _, _ ->
						val header = compiler.compilationContext.moduleHeader!!
						val importNames = header.importedModuleNames
						val entryPoints = header.entryPointNames
						val corpora = header.corpora
						val newVersion = ModuleVersion(
							repository,
							sourceReference.size,
							importNames,
							entryPoints,
							corpora)
						availBuilder.serialize(header, newVersion)
						archive.putVersion(versionKey, newVersion)
						action(resolvedName, newVersion, completedAction)
					}
				}
			}
		) { code, ex ->
			// TODO figure out what to do with these!!! Probably report them?
			System.err.println(
				"Received ErrorCode: $code with exception:\n")
			ex?.printStackTrace()
		}
	}

	/**
	 * A module was just traced, so record that fact.  Note that the trace was
	 * either successful or unsuccessful.
	 *
	 * @param moduleRootResolver
	 *   The [ModuleRootResolver] that distinguishes this trace of a file from
	 *   others, in case module root directories overlap.
	 * @param moduleURI
	 *   The [URI] for which a trace just completed.
	 */
	@Synchronized
	fun indicateFileCompleted(
		moduleRootResolver: ModuleRootResolver,
		moduleURI: URI)
	{
		val added = traceCompletions.add(moduleRootResolver to moduleURI)
		require(added) {
			"Completed trace of file $moduleURI twice"
		}
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

		if (allQueued
			&& traceRequests.minus(traceCompletions).isEmpty())
		{
			afterTraceCompletes()
		}
	}
}
