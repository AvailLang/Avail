/*
 * PrimitiveClassLoader.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter

import avail.builder.ModuleName
import avail.descriptor.tuples.A_String
import java.io.File
import java.net.URLClassLoader
import javax.annotation.concurrent.GuardedBy

/**
 * The [URLClassLoader] for loading external [Primitive]s through the
 * [Primitive] linking system.
 *
 * @author Richard Arriaga
 *
 * @property moduleName
 *   The fully qualified [A_String] name of the module that is responsible for
 *   creating this [PrimitiveClassLoader] for loading [Primitive]s from the
 *   target jar file.
 *
 * @constructor
 * Construct a [PrimitiveClassLoader].
 *
 * @param jarFile
 *   The [File] location of the jar file to load that contains the [Primitive]s.
 * @param moduleName
 *   The [A_String] module name of the module that created this class loader.
 * @param classNames
 *   The list of fully qualified [Primitive] class names as [A_String]s that
 *   represent [Primitive]s made available through this [PrimitiveClassLoader].
 * @param parent
 *   The parent [ClassLoader] of this [PrimitiveClassLoader].
 */
class PrimitiveClassLoader constructor(
	jarFile: File,
	val moduleName: A_String,
	classNames: Set<String>,
	parent: ClassLoader = Primitive::class.java.classLoader
): URLClassLoader(arrayOf(jarFile.toURI().toURL()), parent)
{
	/**
	 * The set of [Primitive.PrimitiveHolder]s that were loaded by this
	 * [PrimitiveClassLoader].
	 */
	private val holders = mutableSetOf<Primitive.PrimitiveHolder>()

	/**
	 * Cleanup all of the [Primitive.PrimitiveHolder]s loaded by this
	 * [PrimitiveClassLoader] then [close] this [PrimitiveClassLoader].
	 */
	fun cleanup ()
	{
		synchronized(this)
		{
			holders.toList().forEach { holder ->
				Primitive.PrimitiveHolder.holdersByName
					.remove(holder.name)
				Primitive.PrimitiveHolder.holdersByClassName
					.remove(holder.className)
			}
			close()
			holders.clear()
		}
	}

	init
	{
		moduleToLoader.computeIfAbsent(moduleName) { mutableSetOf() }.add(this)
		classNames.forEach {
			val primitiveName =
				Primitive.PrimitiveHolder.splitClassName(it).last()
					.split("P_").last()
			val holder =
				Primitive.PrimitiveHolder(primitiveName, it, this)
			Primitive.PrimitiveHolder.holdersByClassName[it] = holder
			Primitive.PrimitiveHolder.holdersByName[primitiveName] = holder
			holders.add(holder)
		}
	}

	companion object
	{
		/**
		 * An object use to synchronize removal elements from [moduleToLoader].
		 */
		private object UnloadLock

		/**
		 * The required name prefix for all all [Primitive]s.
		 */
		const val PRIMITIVE_NAME_PREFIX = "P_"

		/**
		 * The map that tracks all the [PrimitiveClassLoader]s loading primitive
		 * libraries for a given [ModuleName].
		 */
		@GuardedBy("UnloadLock")
		private val moduleToLoader =
			mutableMapOf<A_String, MutableSet<PrimitiveClassLoader>>()

		/**
		 * Unload all the [PrimitiveClassLoader]s loaded by the provided
		 * [ModuleName].
		 *
		 * @param moduleName
		 *   The [ModuleName] for the module to unload.
		 */
		fun unloadModuleClassLoaders (moduleName: A_String)
		{
			moduleToLoader[moduleName]?.let {
				it.toList().forEach { loader ->
					loader.cleanup()
				}
				synchronized(UnloadLock)
				{
					moduleToLoader.remove(moduleName)
				}
			}
		}

	}
}
