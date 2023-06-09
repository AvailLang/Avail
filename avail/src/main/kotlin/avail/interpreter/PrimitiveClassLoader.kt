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
import java.net.URL
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
 */
class PrimitiveClassLoader constructor(
	jarUrl: URL,
	val moduleName: A_String,
	parent: ClassLoader = Primitive::class.java.classLoader
): URLClassLoader(arrayOf(jarUrl), parent)
{
	/**
	 * The set of [Primitive.PrimitiveHolder]s that were loaded by this
	 * [PrimitiveClassLoader].
	 */
	private val holders = mutableSetOf<Primitive.PrimitiveHolder>()
	override fun loadClass(name: String, resolve: Boolean): Class<*>
	{
		val c = super.loadClass(name, resolve)
		synchronized(this) {
			val primitiveName =
				Primitive.PrimitiveHolder.splitClassName(name).last()
			val holder = Primitive.PrimitiveHolder(primitiveName, name, this)
			Primitive.PrimitiveHolder.holdersByClassName[name] = holder
			Primitive.PrimitiveHolder.holdersByName[primitiveName] = holder
			holders.add(holder)
		}
		return c
	}

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
	}

	companion object
	{
		/**
		 * An object use to synchronize removal elements from [moduleToLoader].
		 */
		private object UnloadLock

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
			moduleToLoader[moduleName]?.toList()?.forEach {
				it.cleanup()
			}
			synchronized(UnloadLock)
			{
				moduleToLoader.remove(moduleName)
			}
		}

	}
}
