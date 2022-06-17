/*
 * JVMChunkClassLoader.kt
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
package avail.optimizer.jvm

import avail.interpreter.execution.Interpreter
import avail.interpreter.execution.Interpreter.Companion.log
import avail.interpreter.levelTwo.L2Chunk
import avail.optimizer.jvm.CheckedField.Companion.instanceField
import avail.utility.Strings.traceFor
import java.lang.reflect.InvocationTargetException
import java.util.logging.Level

/**
 * A `JVMChunkClassLoader` is created for each generated [JVMChunk], permitted
 * dynamic loading and unloading of each `JVMChunk` independently. The class
 * loader holds onto zero or many [objects][Object] for usage during static
 * initialization of the generated `JVMChunk`; these values are accessed from an
 * [array][parameters].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 * Construct a new `JVMChunkClassLoader` that delegates to the same
 * [ClassLoader] that loaded [JVMChunk].
 */
class JVMChunkClassLoader : ClassLoader(JVMChunk::class.java.classLoader)
{
	/**
	 * The parameters made available for the generated [JVMChunk] upon static
	 * initialization.
	 */
	@ReferencedInGeneratedCode
	@JvmField
	var parameters: Array<Any> = arrayOf()

	/**
	 * Answer an instance of a [JVMChunk] [implementation][Class] that is
	 * defined by the given bytes.
	 *
	 * @param chunkName
	 *   The name of the [L2Chunk].
	 * @param className
	 *   The class name.
	 * @param classBytes
	 *   The foundational class bytes.
	 * @param params
	 *   The values that should be bundled into this class
	 *   [loader][JVMChunkClassLoader] for static initialization of the
	 *   generated `JVMChunk`. These are accessible via the [parameters] field.
	 * @return
	 *   The newly constructed `JVMChunk` instance, or `null` if no such
	 *   instance could be constructed.
	 */
	fun newJVMChunkFrom(
		chunkName: String,
		className: String,
		classBytes: ByteArray,
		params: Array<Any>): JVMChunk?
	{
		// These need to become available now so that they are available during
		// loading of the generated class.
		parameters = params
		val cl = defineClass(
			className, classBytes, 0, classBytes.size)
		try
		{
			val constructor = cl.getConstructor()
			// Reflectively accessing the constructor forces the class to
			// actually load. The static initializer should have discarded the
			// parameters after assignment to static final fields of the
			// generated JVMChunk.
			val o = constructor.newInstance()
			return o as JVMChunk
		}
		catch (e: NoSuchMethodException)
		{
			log(
				Interpreter.loggerDebugJVM,
				Level.SEVERE,
				"Failed to load JVMChunk ({0}) from L2Chunk ({1}): {2}",
				className,
				chunkName,
				traceFor(e))
		}
		catch (e: InstantiationException)
		{
			log(
				Interpreter.loggerDebugJVM,
				Level.SEVERE,
				"Failed to load JVMChunk ({0}) from L2Chunk ({1}): {2}",
				className,
				chunkName,
				traceFor(e))
		}
		catch (e: IllegalAccessException)
		{
			log(
				Interpreter.loggerDebugJVM,
				Level.SEVERE,
				"Failed to load JVMChunk ({0}) from L2Chunk ({1}): {2}",
				className,
				chunkName,
				traceFor(e))
		}
		catch (e: InvocationTargetException)
		{
			log(
				Interpreter.loggerDebugJVM,
				Level.SEVERE,
				"Failed to load JVMChunk ({0}) from L2Chunk ({1}): {2}",
				className,
				chunkName,
				traceFor(e))
		}
		catch (e: ClassCastException)
		{
			log(
				Interpreter.loggerDebugJVM,
				Level.SEVERE,
				"Failed to load JVMChunk ({0}) from L2Chunk ({1}): {2}",
				className,
				chunkName,
				traceFor(e))
		}
		return null
	}

	companion object
	{
		/** The [CheckedField] for [parameters]. */
		val parametersField = instanceField(
			JVMChunkClassLoader::class.java,
			JVMChunkClassLoader::parameters.name,
			Array<Any>::class.java)
	}
}
