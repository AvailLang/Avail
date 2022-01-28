/*
 * Anvil.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package org.availlang.ide.anvil

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import java.io.File
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.jar.Manifest

/**
 * Object that maintains universal Anvil state.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
internal object Anvil
{
	/**
	 * The name of the Avail home directory. This is where Avail lives.
	 */
	private const val AVAIL_HOME = ".avail"

	/**
	 * The name of the Avail library home directory. This is where shared Avail
	 * libraries are kept.
	 */
	private const val AVAIL_LIBS = "lib"

	/**
	 * The name of the Avail standard libraries home directory. This is where
	 * versions of the Avail standard library are kept.
	 */
	private const val AVAIL_STDLIBS = "$AVAIL_LIBS/stdlib"

	/**
	 * The name of the Avail standard library JAR file.
	 */
	private const val AVAIL_STDLIB_JAR_NAME = "avail-standard-library.jar"

	/**
	 * The name of the Avail standard repositories home directory. This is the
	 * default location for repositories to be written to.
	 */
	private const val AVAIL_REPOS = "repositories"

	/**
	 * The enumeration of supported Avail library types.
	 */
	enum class LibraryType constructor(val scheme: String)
	{
		/**
		 * The library consists of an actual directory of module files.
		 */
		FILES("file://"),

		/**
		 * The library has been bundled into a [JAR][JarFile].
		 */
		JAR("jar:");
	}

	/**
	 * The set of versions of the standard library that are available. Each
	 * version represents a directory in [availStdLibs], inside of which is the
	 * corresponding version of the library.
	 */
	val stdLibVersionsAvailableMap = mutableMapOf<String, LibraryType>()

	/**
	 * The Avail version [Array] that contains the
	 * [available versions][stdLibVersionsAvailableMap].
	 */
	val stdLibVersionsAvailable: Array<String> get() =
		stdLibVersionsAvailableMap.keys.toList().sorted().toTypedArray()

	/**
	 * The full path to [AVAIL_HOME]. This is relative to in the user's
	 * home directory (`System.getProperty("user.home")`):
	 * `{USER_HOME}/.avail`.
	 */
	private val availHome: String by lazy {
		"${System.getProperty("user.home")}/$AVAIL_HOME"
	}

	/**
	 * The full path to [AVAIL_LIBS]. This is relative to in the user's
	 * home directory (`System.getProperty("user.home")`):
	 * `{USER_HOME}/.avail/lib`.
	 */
	private val availHomeLibs: String by lazy {
		"$availHome/$AVAIL_LIBS"
	}

	/**
	 * The full path to [AVAIL_STDLIBS]. This is relative to in the user's
	 * home directory (`System.getProperty("user.home")`):
	 * `{USER_HOME}/.avail/lib/stdlib`.
	 */
	private val availStdLibs: String by lazy {
		"$availHome/$AVAIL_STDLIBS"
	}

	/**
	 * The full path to [AVAIL_REPOS]. This is relative to in the user's
	 * home directory (`System.getProperty("user.home")`):
	 * `{USER_HOME}/.avail/repositories`.
	 */
	val availRepos: String by lazy {
		"$availHome/$AVAIL_REPOS"
	}

	/**
	 * Initialize the [availHome] directory. This creates directories:
	 * * [availHome]
	 * * [availRepos]
	 * * [availHomeLibs]
	 * * [availStdLibs]
	 * if they don't already exist.
	 */
	internal fun initializeAvailHome ()
	{
		val homeDir = File(availHome)
		if (!homeDir.exists())
		{
			homeDir.mkdir()
		}
		val reposDir = File(availRepos)
		if (!reposDir.exists())
		{
			reposDir.mkdir()
		}
		val libsDir = File(availHomeLibs)
		if (!libsDir.exists())
		{
			libsDir.mkdir()
		}
		val stdlibsDir = File(availStdLibs)
		if (!stdlibsDir.exists())
		{
			stdlibsDir.mkdir()
		}
	}

	/**
	 * Identify the Avail standard libraries that are present in [availStdLibs].
	 * The result are added to [stdLibVersionsAvailableMap].
	 */
	internal fun identifyAvailableLibraries ()
	{
		val standardLibsDir = File(availStdLibs)
		if (standardLibsDir.exists())
		{
			standardLibsDir.list().forEach {
				val dir = File("$availStdLibs/$it")
				if (!dir.isDirectory) { return@forEach }
				val jar = File("$availStdLibs/$it/$AVAIL_STDLIB_JAR_NAME")
				if (jar.exists())
				{
					stdLibVersionsAvailableMap[it] = LibraryType.JAR
					return@forEach
				}
				val availRoot = File("$availStdLibs/$it/avail")
				if (availRoot.exists())
				{
					stdLibVersionsAvailableMap[it] = LibraryType.FILES
					return@forEach
				}
			}
		}
	}

	/**
	 * The unique id that represents this plugin. It must match the id
	 * in `plugin.xml`.
	 */
	private const val PLUGIN_ID: String = "avail.plugin.avail-intellij-plugin"

	/**
	 * The [IdeaPluginDescriptor] for the Anvil plugin.
	 */
	private val plugin: IdeaPluginDescriptor get () =
		PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))!!

	/**
	 * The directory of this plugin.
	 */
	private val pluginDirectory: Path get() = plugin.pluginPath

	/**
	 * Add the plugin packaged library to [AVAIL_STDLIBS] if it isn't already
	 * present.
	 */
	internal fun conditionallyAddStandardLib ()
	{
		val stdLibPath = "$pluginDirectory/lib/$AVAIL_STDLIB_JAR_NAME"
		val file = File(stdLibPath)
		if (!file.exists()) { return }
		val jarFile = JarFile(file)
		val manifest: Manifest = jarFile.manifest
		val attrs = manifest.mainAttributes
		val version = attrs.getValue("Implementation-Version") ?: return
		if (!stdLibVersionsAvailableMap.containsKey(version))
		{
			val newDir = File("$availStdLibs/$version")
			newDir.mkdir()
			file.copyTo(File("$availStdLibs/$version/$AVAIL_STDLIB_JAR_NAME"))
			stdLibVersionsAvailableMap[version] = LibraryType.JAR
		}
		return
	}
}
