/**
 * compiler/AvailBuilder.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.compiler;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.descriptor.AvailModuleDescriptor;

/**
 * TODO: [TLS] Document this type!
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class AvailBuilder
{
	/**
	 * The {@linkplain AvailRuntime runtime} into which the {@linkplain
	 * AvailBuilder builder} will install the target {@linkplain
	 * AvailModuleDescriptor module} and its dependencies.
	 */
	private final @NotNull AvailRuntime runtime;

	/**
	 * The {@linkplain AvailModuleDescriptor module} {@linkplain File file} that
	 * the {@linkplain AvailBuilder builder} must (recursively) load into the
	 * {@linkplain AvailRuntime runtime}. 
	 */
	private final @NotNull File moduleFile;

	/**
	 * The {@linkplain File file} of {@linkplain AvailModuleDescriptor module}
	 * rename rules that the {@linkplain AvailBuilder builder} should apply to
	 * resolve abstract module names to absolute file references.
	 */
	private final File renamesFile;
	
	/**
	 * Construct a new {@link AvailBuilder}.
	 *
	 * @param runtime
	 *        The {@linkplain AvailRuntime runtime} into which the {@linkplain
	 *        AvailBuilder builder} will install the target {@linkplain
	 *        AvailModuleDescriptor module} and its dependencies.
	 * @param moduleFile
	 *        The {@linkplain AvailModuleDescriptor module} {@linkplain File
	 *        file} that the {@linkplain AvailBuilder builder} must
	 *        (recursively) load into the {@linkplain AvailRuntime runtime}. 
	 * @param renamesFile
	 *        The {@linkplain File file} of {@linkplain AvailModuleDescriptor
	 *        module} rename rules that the {@linkplain AvailBuilder builder}
	 *        should apply to resolve abstract module names to absolute file
	 *        references.
	 */
	public AvailBuilder (
		final @NotNull AvailRuntime runtime,
		final @NotNull File moduleFile,
		final File renamesFile)
	{
		this.runtime     = runtime;
		this.moduleFile  = moduleFile;
		this.renamesFile = renamesFile;
	}
	
	/**
	 * A {@linkplain Map map} from fully-qualified logical {@linkplain
	 * AvailModuleDescriptor module} paths to absolute {@linkplain File file}
	 * references.
	 */
	private final @NotNull Map<String, File> renames =
		new HashMap<String, File>();
	
	/**
	 * An ordered {@linkplain LinkedHashSet set} of unvisited {@linkplain
	 * AvailModuleDescriptor module} {@linkplain File files}.
	 */
	private final @NotNull LinkedHashSet<File> unvisited =
		new LinkedHashSet<File>();
	
	// TODO: [TLS] Complete implementation.
}
