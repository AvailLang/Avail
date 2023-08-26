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
import avail.interpreter.Primitive.PrimitiveHolder
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile
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
 * @param parent
 *   The parent [ClassLoader] of this [PrimitiveClassLoader].
 */
class PrimitiveClassLoader constructor(
	jarFile: File,
	val moduleName: A_String,
	parent: ClassLoader = Primitive::class.java.classLoader
): URLClassLoader(arrayOf(jarFile.toURI().toURL()), parent)
{
	/**
	 * The set of [Primitive.PrimitiveHolder]s that were loaded by this
	 * [PrimitiveClassLoader].
	 */
	private val holders = mutableSetOf<PrimitiveHolder>()

	/**
	 * The path to the linked Jar file.
	 */
	private val jarPath = jarFile.path

	/**
	 * Cleanup all of the [Primitive.PrimitiveHolder]s loaded by this
	 * [PrimitiveClassLoader] then [close] this [PrimitiveClassLoader].
	 */
	fun cleanup ()
	{
		synchronized(this)
		{
			holders.toList().forEach { holder ->
				PrimitiveHolder.holdersByName.remove(holder.name)
				PrimitiveHolder.holdersByClassName.remove(holder.className)
			}
			close()
			jarToModule.remove(jarPath)
			holders.clear()
		}
	}

	init
	{
		try
		{
			JarFile(jarFile).use { jar ->
				jar.entries().asIterator().forEach { entry ->
					if (!entry.name.endsWith(".class")) return@forEach
					val last = entry.name.split("/").last()
					if(last.startsWith(PRIMITIVE_NAME_PREFIX))
					{
						val c = entry.name
							.replace(".class", "")
							.replace("/", ".")
						val primitiveName =
							PrimitiveHolder.splitClassName(c).last()
								.split("P_").last()
						val holder =
							PrimitiveHolder(primitiveName, c, this)
						PrimitiveHolder.holdersByClassName[c] = holder
						PrimitiveHolder.holdersByName[primitiveName] = holder
						holders.add(holder)
					}
				}
			}
		}
		catch (e: Throwable)
		{
			// We don't care what the exception is here, we just need to
			// rollback the classes added to PrimitiveHolder.holdersByName and
			// PrimitiveHolder.holdersByClassName. The caller is responsible for
			// handling said exceptions.
			holders.forEach {
				PrimitiveHolder.holdersByClassName.remove(it.className)
				PrimitiveHolder.holdersByName.remove(it.name)
				throw e
			}
		}
		moduleToLoader.computeIfAbsent(moduleName) { mutableSetOf() }.add(this)
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
		 * The map that tracks all the linked Pojo jar file paths to the
		 * associated [A_String] module name that loaded them.
		 */
		@GuardedBy("UnloadLock")
		private val jarToModule = mutableMapOf<String, A_String>()

		/**
		 * Answer the [module name][A_String] of the module that has already
		 * linked the Jar file associated with the provided path.
		 *
		 * @param path
		 *   The path to the Jar file to check linkage for.
		 * @return
		 *   The [A_String] name of the linking module or `null` if not linked.
		 */
		fun jarLinked (path: String): A_String? = jarToModule[path]

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
