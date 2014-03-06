/**
 * StacksGenerator.java
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

package com.avail.stacks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import com.avail.builder.ModuleName;
import com.avail.compiler.AbstractAvailCompiler.ModuleHeader;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.CommentTokenDescriptor;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.TupleDescriptor;

/**
 * TODO: Document StacksGenerator!
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
public class StacksGenerator
{
	/**
	 * A map of {@linkplain ResolvedModuleName module names} to a list of
	 * all the method names exported from said module
	 */
	HashMap<A_String,A_Set> moduleToExportedMethodsMap;

	/**
	 * A map of {@linkplain ResolvedModuleName module names} to a list of
	 * all the method names exported from said module
	 */
	HashMap<A_String,StacksModuleComment> moduleToComments;

	/**
	 * Construct a new {@link StacksGenerator}.
	 *
	 */
	public StacksGenerator()
	{
		this.moduleToComments =
			new HashMap<A_String,StacksModuleComment>();

		this.moduleToExportedMethodsMap =
			new HashMap<A_String,A_Set>();
	}

	/**
	 * Inform the {@linkplain StacksGenerator generator} about the documentation
	 * and linkage of a {@linkplain ModuleDescriptor module}.
	 *
	 * @param header
	 *        The {@linkplain ModuleHeader header} of the module.
	 * @param commentTokens
	 *        The complete {@linkplain TupleDescriptor collection} of
	 *        {@linkplain CommentTokenDescriptor comments} produced for the
	 *        given module.
	 */
	public synchronized void add (
		final ModuleHeader header,
		final A_Tuple commentTokens)
	{
		// TODO [RAA]: Implement this.
	}

	/**
	 * Generate complete Stacks documentation.
	 *
	 * @param outermostModule
	 *        The outermost {@linkplain ModuleDescriptor module} for the
	 *        generation request.
	 * @param modulesPath
	 *        The {@linkplain Path path} to the output {@linkplain
	 *        BasicFileAttributes#isDirectory() directory} for module-oriented
	 *        documentation and data files.
	 * @param categoriesPath
	 *        The path to the output directory for category-oriented
	 *        documentation and data files.
	 * @param errorLogPath
	 *        The path to the Stacks error log for accumulation of Avail
	 *        documentation errors.
	 * @throws IllegalArgumentException
	 *         If the modules path or the categories path exist but do not
	 *         specify a directory, or if the error log path exists and does not
	 *         specify a regular file.
	 */
	public synchronized void generate (
		final ModuleName outermostModule,
		final Path modulesPath,
		final Path categoriesPath,
		final Path errorLogPath)
			throws IllegalArgumentException
	{
		if (Files.exists(modulesPath) && !Files.isDirectory(modulesPath))
		{
			throw new IllegalArgumentException(
				modulesPath + " exists and is not a directory");
		}
		// TODO [RAA]: Implement the other two argument checking cases.
		// TODO [RAA]: Implement everything else.
	}

	/**
	 * Clear all internal data structures and reinitialize the {@linkplain
	 * StacksGenerator generator} for subsequent usage.
	 */
	public synchronized void clear ()
	{
		// TODO [RAA]: Implement this.
	}
}
