/*
 * DocumentationTracer.java
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

import com.avail.compiler.ModuleHeader;
import com.avail.compiler.problems.Problem;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.persistence.IndexedRepositoryManager;
import com.avail.persistence.IndexedRepositoryManager.ModuleArchive;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersion;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersionKey;
import com.avail.serialization.Deserializer;
import com.avail.serialization.MalformedSerialStreamException;
import com.avail.stacks.StacksGenerator;
import com.avail.utility.evaluation.Continuation0;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static com.avail.compiler.problems.ProblemType.INTERNAL;
import static com.avail.compiler.problems.ProblemType.TRACE;
import static com.avail.descriptor.FiberDescriptor.loaderPriority;

/**
 * Used for parallel documentation generation.
 */
final class DocumentationTracer
{
	/** The {@link AvailBuilder} for which to generate documentation. */
	final AvailBuilder availBuilder;

	/**
	 * The {@linkplain StacksGenerator Stacks documentation generator}.
	 */
	private final StacksGenerator generator;

	/**
	 * Construct a new {@code DocumentationTracer}.
	 *
	 * @param availBuilder
	 *        The {@link AvailBuilder} for which to generate documentation.
	 * @param documentationPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for documentation
	 *        and data files.
	 */
	DocumentationTracer (
		final AvailBuilder availBuilder,
		final Path documentationPath)
	{
		this.availBuilder = availBuilder;
		this.generator = new StacksGenerator(
			documentationPath, availBuilder.runtime.moduleNameResolver());
	}

	/**
	 * Get the {@linkplain ModuleVersion module version} for the {@linkplain
	 * ResolvedModuleName named} {@linkplain ModuleDescriptor module}.
	 *
	 * @param moduleName
	 *        A resolved module name.
	 * @return A module version, or {@code null} if no version was
	 *         available.
	 */
	private @Nullable ModuleVersion getVersion (
		final ResolvedModuleName moduleName)
	{
		final IndexedRepositoryManager repository =
			moduleName.repository();
		final ModuleArchive archive = repository.getArchive(
			moduleName.rootRelativeName());
		final byte [] digest = archive.digestForFile(moduleName);
		final ModuleVersionKey versionKey =
			new ModuleVersionKey(moduleName, digest);
		return archive.getVersion(versionKey);
	}

	/**
	 * Load {@linkplain CommentTokenDescriptor comments} for the {@linkplain
	 * ResolvedModuleName named} {@linkplain ModuleDescriptor module} into
	 * the {@linkplain StacksGenerator Stacks documentation generator}.
	 *
	 * @param moduleName
	 *        A module name.
	 * @param problemHandler
	 *        How to handle or report {@link Problem}s that arise during the
	 *        build.
	 * @param completionAction
	 *        What to do when comments have been loaded for the named
	 *        module (or an error occurs).
	 */
	void loadComments (
		final ResolvedModuleName moduleName,
		final ProblemHandler problemHandler,
		final Continuation0 completionAction)
	{
		final @Nullable ModuleVersion version = getVersion(moduleName);
		if (version == null || version.getComments() == null)
		{
			final Problem problem = new Problem(
				moduleName,
				1,
				0,
				TRACE,
				"Module \"{0}\" should have been compiled already",
				moduleName)
			{
				@Override
				public void abortCompilation ()
				{
					availBuilder.stopBuildReason("Comment loading failed");
					completionAction.value();
				}
			};
			problemHandler.handle(problem);
			return;
		}
		final @Nullable A_Tuple tuple;
		try
		{
			final @Nullable byte[] bytes = version.getComments();
			assert bytes != null;
			final ByteArrayInputStream in =
				AvailBuilder.validatedBytesFrom(bytes);
			final Deserializer
				deserializer = new Deserializer(in, availBuilder.runtime);
			tuple = deserializer.deserialize();
			assert tuple != null;
			assert tuple.isTuple();
			final @Nullable AvailObject residue =
				deserializer.deserialize();
			assert residue == null;
		}
		catch (final MalformedSerialStreamException e)
		{
			final Problem problem = new Problem(
				moduleName,
				1,
				0,
				INTERNAL,
				"Couldn''t deserialize comment tuple for module \"{0}\"",
				moduleName)
			{
				@Override
				public void abortCompilation ()
				{
					availBuilder.stopBuildReason(
						"Comment deserialization failed");
					completionAction.value();
				}
			};
			problemHandler.handle(problem);
			return;
		}
		final ModuleHeader header;
		try
		{
			final ByteArrayInputStream in =
				AvailBuilder.validatedBytesFrom(version.getModuleHeader());
			final Deserializer deserializer =
				new Deserializer(in, availBuilder.runtime);
			header = new ModuleHeader(moduleName);
			header.deserializeHeaderFrom(deserializer);
		}
		catch (final MalformedSerialStreamException e)
		{
			final Problem problem = new Problem(
				moduleName,
				1,
				0,
				INTERNAL,
				"Couldn''t deserialize header for module \"{0}\"",
				moduleName)
			{
				@Override
				public void abortCompilation ()
				{
					availBuilder.stopBuildReason(
						"Module header deserialization failed when "
						+ "loading comments");
					completionAction.value();
				}
			};
			problemHandler.handle(problem);
			return;
		}
		generator.add(header, tuple);
		completionAction.value();
	}

	/**
	 * Schedule a load of the {@linkplain CommentTokenDescriptor comments}
	 * for the {@linkplain ResolvedModuleName named} {@linkplain
	 * ModuleDescriptor module}.
	 *
	 * @param moduleName
	 *        A module name.
	 * @param problemHandler
	 *        How to handle or report {@link Problem}s that arise during the
	 *        build.
	 * @param completionAction
	 *        What to do when comments have been loaded for the named
	 *        module.
	 */
	private void scheduleLoadComments (
		final ResolvedModuleName moduleName,
		final ProblemHandler problemHandler,
		final Continuation0 completionAction)
	{
		// Avoid scheduling new tasks if an exception has happened.
		if (availBuilder.shouldStopBuild())
		{
			completionAction.value();
			return;
		}
		availBuilder.runtime.execute(
			loaderPriority,
			() ->
			{
				if (availBuilder.shouldStopBuild())
				{
					// An exception has been encountered since the
					// earlier check.  Exit quickly.
					completionAction.value();
				}
				else
				{
					loadComments(moduleName, problemHandler, completionAction);
				}
			});
	}

	/**
	 * Load the {@linkplain CommentTokenDescriptor comments} for all {@linkplain
	 * ModuleDescriptor modules} in the {@link AvailBuilder#moduleGraph}.
	 *
	 * @param problemHandler
	 *        How to handle or report {@link Problem}s that arise during the
	 *        build.
	 */
	void load (
		final ProblemHandler problemHandler)
	{
		availBuilder.moduleGraph.parallelVisit(
			(moduleName, completionAction) ->
				scheduleLoadComments(
					moduleName, problemHandler, completionAction));
	}

	/**
	 * Generate Stacks documentation.
	 *
	 * @param target
	 *        The outermost {@linkplain ModuleDescriptor module} for the
	 *        generation request.
	 * @param problemHandler
	 *        How to handle or report {@link Problem}s that arise during the
	 *        build.
	 */
	void generate (
		final ModuleName target,
		final ProblemHandler problemHandler)
	{
		try
		{
			generator.generate(availBuilder.runtime, target);
		}
		catch (final Exception e)
		{
			final Problem problem = new Problem(
				target,
				1,
				0,
				TRACE,
				"Could not generate Stacks documentation: {0}",
				e.getLocalizedMessage())
			{
				@Override
				public void abortCompilation ()
				{
					availBuilder.stopBuildReason(
						"Unable to generate Stacks documentation");
				}
			};
			problemHandler.handle(problem);
		}
	}
}
