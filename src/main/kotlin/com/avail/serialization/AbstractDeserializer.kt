/*
 * AbstractDeserializer.kt
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

package com.avail.serialization

import com.avail.AvailRuntime
import com.avail.descriptor.*
import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.tuples.A_String
import java.io.IOException
import java.io.InputStream

/**
 * An `AbstractDeserializer` consumes a stream of bytes to reconstruct objects
 * that had been previously [serialized][Serializer.serialize] with a
 * [Serializer].
 *
 * @property input
 *   The stream from which bytes are read.
 * @property runtime
 *   The [AvailRuntime] whose scope is used to decode references to constructs
 *   that need to be looked up rather than re-instantiated.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `Deserializer`.
 *
 * @param input
 *   An [InputStream] from which to reconstruct objects.
 * @param runtime
 *   The [AvailRuntime] from which to locate well-known objects during
 *   deserialization.
 */
abstract class AbstractDeserializer constructor(
	protected val input: InputStream,
	val runtime: AvailRuntime)
{
	/** The current [module][ModuleDescriptor]. */
	var currentModule: A_Module = nil

	/**
	 * Consume an unsigned byte from the input.  Return it as an `int` to ensure
	 * it's unsigned, i.e., 0 ≤ b ≤ 255.
	 *
	 * @return
	 *   An `int` containing the unsigned byte (0..255).
	 */
	fun readByte(): Int =
		try
		{
			input.read()
		}
		catch (e: IOException)
		{
			throw RuntimeException(e)
		}

	/**
	 * Consume an unsigned short from the input in big endian order.  Return it
	 * as an `int` to ensure it's unsigned, i.e., 0 ≤ b ≤ 65535.
	 *
	 * @return
	 *   An `int` containing the unsigned short (0..65535).
	 */
	fun readShort(): Int =
		try
		{
			(input.read() shl 8) + input.read()
		}
		catch (e: IOException)
		{
			throw RuntimeException(e)
		}

	/**
	 * Consume an int from the input in big endian order.
	 *
	 * @return
	 *   An `int` extracted from the input.
	 */
	fun readInt(): Int =
		try
		{
			(input.read() shl 24) +
				(input.read() shl 16) +
				(input.read() shl 8) +
				input.read()
		}
		catch (e: IOException)
		{
			throw RuntimeException(e)
		}

	/**
	 * Look up the module of the receiver's [AvailRuntime] which has the given
	 * name.
	 *
	 * @param moduleName
	 *   The [name][StringDescriptor] of the module.
	 * @return
	 *   The module with the specified name.
	 */
	internal fun moduleNamed(moduleName: A_String): A_Module
	{
		assert(moduleName.isString)
		val current = currentModule
		if (!current.equalsNil() && moduleName.equals(current.moduleName()))
		{
			return current
		}
		if (!runtime.includesModuleNamed(moduleName))
		{
			throw RuntimeException("Cannot locate module named $moduleName")
		}
		return runtime.moduleAt(moduleName)
	}

	/**
	 * Convert an index into an object.  The object must already have been
	 * assembled.
	 *
	 * @param index
	 *   The zero-based index at which to fetch the object.
	 * @return
	 *   The already constructed object at the specified index.
	 */
	internal abstract fun objectFromIndex(index: Int): AvailObject

	/**
	 * Record the provided object as an end product of deserialization.
	 *
	 * @param object
	 *   The object that was produced.
	 */
	internal abstract fun recordProducedObject(obj: AvailObject)
}
