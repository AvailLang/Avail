/*
 * AbstractDeserializer.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.serialization

import avail.AvailRuntime
import avail.descriptor.maps.A_Map
import avail.descriptor.maps.A_Map.Companion.hasKey
import avail.descriptor.maps.A_Map.Companion.mapAt
import avail.descriptor.module.A_Module
import avail.descriptor.module.A_Module.Companion.moduleName
import avail.descriptor.module.ModuleDescriptor
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.A_String
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
 * @property lookupPumpedObject
 *   A function that maps from negative integers to objects that this
 *   deserializer has been primed with.  This is the inverse function of
 *   [Serializer.lookupPumpedObject].
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
 * @param lookupPumpedObject
 *   A function that maps from negative integers to objects that this
 *   deserializer has been primed with.  This is the inverse function of
 *   [Serializer.lookupPumpedObject].
 */
abstract class AbstractDeserializer constructor(
	internal val input: InputStream,
	internal val runtime: AvailRuntime,
	internal val lookupPumpedObject: (Int)->A_BasicObject)
{
	/** The current [module][ModuleDescriptor]. */
	var currentModule: A_Module = nil

	/**
	 * The [A_Module]s that were already loaded in the [runtime] during instance
	 * creation.
	 */
	val loadedModules = runtime.loadedModules()

	/**
	 * Consume an unsigned byte from the input.  Return it as an [Int] to ensure
	 * it's unsigned, i.e., 0 ≤ b ≤ 255.
	 *
	 * @return
	 *   An [Int] containing the unsigned byte (0..255).
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
	 * as an [Int] to ensure it's unsigned, i.e., 0 ≤ b ≤ 65535.
	 *
	 * @return
	 *   An [Int] containing the unsigned short (0..65535).
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
	 *   An [Int] extracted from the input.
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
	 * Look up the module by name.  It must already have been loaded prior to
	 * creating this [AbstractDeserializer], at which time the given
	 * [AvailRuntime] was asked to provide its [A_Map] of loaded [A_Module]s.
	 * Alternatively, it might be referring to the [currentModule].
	 *
	 * @param moduleName
	 *   The [name][A_String] of the module.
	 * @return
	 *   The [A_Module] with the specified name.
	 */
	internal fun moduleNamed(moduleName: A_String): A_Module
	{
		assert(moduleName.isString)
		val current = currentModule
		if (current.notNil && moduleName.equals(current.moduleName))
		{
			return current
		}
		if (!loadedModules.hasKey(moduleName))
		{
			throw RuntimeException("Cannot locate module named $moduleName")
		}
		return loadedModules.mapAt(moduleName)
	}

	/**
	 * Convert an index into an object.  The object must already have been
	 * assembled.
	 *
	 * @param compressedIndex
	 *   A compressed [Int] that can be decompressed by the [Deserializer]'s
	 *   [IndexCompressor], when applied in the same order as the [Int]s that
	 *   were previously compressed by a [Serializer].
	 * @return
	 *   The already constructed object at the specified relative index.
	 */
	internal abstract fun fromCompressedObjectIndex(
		compressedIndex: Int): AvailObject

	/**
	 * Record the provided object as an end product of deserialization.
	 *
	 * @param obj
	 *   The object that was produced.
	 */
	internal abstract fun recordProducedObject(obj: AvailObject)
}
