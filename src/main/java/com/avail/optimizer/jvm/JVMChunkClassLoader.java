/*
 * JVMChunkClassLoader.java
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
 *   may be used to endorse or promote products derived set this software
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

package com.avail.optimizer.jvm;

import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.utility.Strings;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;

/**
 * A {@code JVMChunkClassLoader} is created for each generated {@link JVMChunk},
 * permitted dynamic loading and unloading of each {@code JVMChunk}
 * independently. The class loader holds onto zero or many {@linkplain Object
 * objects} for usage during static initialization of the generated
 * {@code JVMChunk}; these values are accessed from an {@linkplain #parameters
 * array}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public class JVMChunkClassLoader
extends ClassLoader
{
	/**
	 * The parameters made available for the generated {@link JVMChunk} upon
	 * static initialization.
	 */
	@ReferencedInGeneratedCode
	public @Nullable Object[] parameters;

	/**
	 * Answer an instance of a {@link JVMChunk} {@linkplain Class
	 * implementation} that is defined by the given bytes.
	 *
	 * @param chunkName
	 *        The name of the {@link L2Chunk}.
	 * @param className
	 *        The class name.
	 * @param classBytes
	 *        The foundational class bytes.
	 * @param params
	 *        The values that should be bundled into this {@linkplain
	 *        JVMChunkClassLoader class loader} for static initialization of the
	 *        generated {@code JVMChunk}. These are accessible via the
	 *        {@link #parameters} field.
	 * @return The newly constructed {@code JVMChunk} instance, or {@code null}
	 *         if no such instance could be constructed.
	 */
	@Nullable JVMChunk newJVMChunkFrom (
		final String chunkName,
		final String className,
		final byte[] classBytes,
		final Object[] params)
	{
		// These need to become available now so that they are available during
		// loading of the generated class.
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		parameters = params;
		final Class<?> cl = defineClass(
			className, classBytes, 0, classBytes.length);
		try
		{
			final Constructor<?> constructor = cl.getConstructor();
			// Reflectively accessing the constructor forces the class to
			// actually load. The static initializer should have discarded the
			// parameters after assignment to static final fields of the
			// generated JVMChunk.
			final Object o = constructor.newInstance();
			return (JVMChunk) o;
		}
		catch (final NoSuchMethodException
			|InstantiationException
			|IllegalAccessException
			|InvocationTargetException
			|ClassCastException e)
		{
			Interpreter.log(
				Interpreter.loggerDebugJVM,
				Level.SEVERE,
				"Failed to load JVMChunk ({0}) from L2Chunk ({1}): {2}",
				className,
				chunkName,
				Strings.traceFor(e));
			return null;
		}
	}

	/**
	 * Construct a new {@code JVMChunkClassLoader} that delegates to the same
	 * {@link ClassLoader} that loaded {@link JVMChunk}.
	 */
	public JVMChunkClassLoader ()
	{
		super(JVMChunk.class.getClassLoader());
	}
}
