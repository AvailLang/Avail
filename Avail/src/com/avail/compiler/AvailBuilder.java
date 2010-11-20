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
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.descriptor.ModuleDescriptor;

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
	 * ModuleDescriptor module} and its dependencies.
	 */
	private final @NotNull AvailRuntime runtime;

	/**
	 * A {@linkplain Reader reader} on the {@linkplain ModuleDescriptor
	 * module} {@linkplain File file} that the {@linkplain AvailBuilder builder}
	 * must (recursively) load into the {@linkplain AvailRuntime runtime}. 
	 */
	private final @NotNull Reader moduleReader;

	/**
	 * A {@linkplain Reader reader} on the {@linkplain ModuleDescriptor
	 * module} rename rules that the {@linkplain AvailBuilder builder} should
	 * apply when resolving abstract module names to {@linkplain
	 * File#isAbsolute() absolute} {@linkplain File file references}.
	 */
	private final @NotNull Reader renamesReader;
	
	/**
	 * A {@linkplain Map map} from {@linkplain File#isAbsolute() absolute}
	 * {@linkplain File file references} to the {@linkplain Reader readers} that
	 * should be used to obtain substitution content.
	 */
	private final @NotNull Map<File, Reader> substitutionReaders;
	
	/**
	 * Construct a new {@link AvailBuilder}.
	 *
	 * @param runtime
	 *        The {@linkplain AvailRuntime runtime} into which the {@linkplain
	 *        AvailBuilder builder} will install the target {@linkplain
	 *        ModuleDescriptor module} and its dependencies.
	 * @param moduleReader
	 *        A {@linkplain Reader reader} on the {@linkplain
	 *        ModuleDescriptor module} {@linkplain File file} that the
	 *        {@linkplain AvailBuilder builder} must (recursively) load into the
	 *        {@linkplain AvailRuntime runtime}.
	 * @param renamesReader
	 *        A {@linkplain Reader reader} on the {@linkplain
	 *        ModuleDescriptor module} rename rules that the {@linkplain
	 *        AvailBuilder builder} should apply when resolving abstract module
	 *        names to {@linkplain File#isAbsolute() absolute} {@link File file
	 *        references}.
	 * @param substitutionReaders
	 *        A {@linkplain Map map} from {@linkplain File#isAbsolute()
	 *        absolute} {@linkplain File file references} to the {@linkplain
	 *        Reader readers} that should be used to obtain substitution
	 *        content.
	 */
	public AvailBuilder (
		final @NotNull AvailRuntime runtime,
		final @NotNull Reader moduleReader,
		final @NotNull Reader renamesReader,
		final @NotNull Map<File, Reader> substitutionReaders)
	{
		this.runtime             = runtime;
		this.moduleReader        = moduleReader;
		this.renamesReader       = renamesReader;
		this.substitutionReaders = substitutionReaders;
	}
	
	/**
	 * Construct a new {@link AvailBuilder}.
	 *
	 * @param runtime
	 *        The {@linkplain AvailRuntime runtime} into which the {@linkplain
	 *        AvailBuilder builder} will install the target {@linkplain
	 *        ModuleDescriptor module} and its dependencies.
	 * @param moduleReader
	 *        A {@linkplain Reader reader} on the {@linkplain
	 *        ModuleDescriptor module} {@linkplain File file} that the
	 *        {@linkplain AvailBuilder builder} must (recursively) load into the
	 *        {@linkplain AvailRuntime runtime}.
	 * @param renamesReader
	 *        A {@linkplain Reader reader} on the {@linkplain
	 *        ModuleDescriptor module} rename rules that the {@linkplain
	 *        AvailBuilder builder} should apply when resolving abstract module
	 *        names to {@linkplain File#isAbsolute() absolute} {@link File file
	 *        references}.
	 */
	public AvailBuilder (
		final @NotNull AvailRuntime runtime,
		final @NotNull Reader moduleReader,
		final @NotNull Reader renamesReader)
	{
		this(runtime, moduleReader, renamesReader, new HashMap<File, Reader>());
	}
	
	/**
	 * Construct a new {@link AvailBuilder}.
	 *
	 * @param runtime
	 *        The {@linkplain AvailRuntime runtime} into which the {@linkplain
	 *        AvailBuilder builder} will install the target {@linkplain
	 *        ModuleDescriptor module} and its dependencies.
	 * @param moduleReader
	 *        A {@linkplain Reader reader} on the {@linkplain
	 *        ModuleDescriptor module} {@linkplain File file} that the
	 *        {@linkplain AvailBuilder builder} must (recursively) load into the
	 *        {@linkplain AvailRuntime runtime}.
	 */
	public AvailBuilder (
		final @NotNull AvailRuntime runtime,
		final @NotNull Reader moduleReader)
	{
		this(
			runtime,
			moduleReader,
			new StringReader(""),
			new HashMap<File, Reader>());
	}
	
	/**
	 * A {@linkplain Map map} from fully-qualified logical {@linkplain
	 * ModuleDescriptor module} paths to absolute {@linkplain File file}
	 * references.
	 */
	private Map<String, File> renames;
	
	/**
	 * An ordered {@linkplain LinkedHashSet set} of unvisited {@linkplain
	 * ModuleDescriptor module} {@linkplain File files}.
	 */
	private final @NotNull LinkedHashSet<File> unvisited =
		new LinkedHashSet<File>();
	
	
	
	// TODO: [TLS] Complete implementation.
}
