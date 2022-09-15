/*
 * AvailEnvironment.kt
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

package avail.environment.projects

import avail.environment.launcher.AvailLaunchWindow
import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.AvailEnvironment.availHome
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.json.JSONFriendly
import org.availlang.json.jsonObject
import org.availlang.json.jsonPrettyPrintWriter
import java.io.File

/**
 * The interface that defines the state expected of the configuration for the
 * Avail environment on a specific machine. This data is stored in the
 * [AvailEnvironment.availHome] directory in the file
 * `avail-global-config.json`.
 *
 * @author Richard Arriaga
 */
sealed interface GlobalAvailConfiguration: JSONFriendly
{
	/**
	 * The serialization version of this [GlobalAvailConfiguration] which
	 * represents the structure of the [JSONFriendly]-based configuration file
	 * that represents this [GlobalAvailConfiguration].
	 */
	val serializationVersion: Int

	/**
	 * The [KnownAvailProject.id] of the project marked to be opened
	 * automatically at launch bypassing the [AvailLaunchWindow] or `null` if
	 * only the [AvailLaunchWindow] should be opened.
	 */
	var favorite: String?

	/** The set of [KnownAvailProject]s. */
	val knownProjects: MutableSet<KnownAvailProject>

	/**
	 * The default available templates that should be available when editing 
	 * Avail source modules in the workbench, as a map from template names 
	 * (corresponding to user inputs) to template expansions. Zero or one caret 
	 * insertion (⁁) may appear in each expansion.
	 */
	val globalTemplates: MutableMap<String, String>

	/**
	 * The path to the default Avail standard library to be used in projects or
	 * `null` if none set.
	 */
	var defaultStandardLibrary: String?

	/**
	 * The [knownProjects] sorted by [KnownAvailProject.name] in ascending
	 * alphabetical order.
	 */
	val knownProjectsByAlphaAscending get() =
		knownProjects.toList().sortedBy { it.name }

	/**
	 * The [knownProjects] sorted by [KnownAvailProject.name] in descending
	 * alphabetical order.
	 */
	val knownProjectsByAlphaDescending get() =
		knownProjects.toList().sortedByDescending { it.name }

	/**
	 * The [knownProjects] sorted by [KnownAvailProject.lastOpened] in ascending
	 * order.
	 */
	val knownProjectsByLastOpenedAscending get() =
		knownProjects.toList().sortedBy { it.lastOpened }

	/**
	 * The [knownProjects] sorted by [KnownAvailProject.lastOpened] in
	 * descending order.
	 */
	val knownProjectsByLastOpenedDescending get() =
		knownProjects.toList().sortedByDescending { it.lastOpened }

	/**
	 * Add the provided [AvailProject] to this [GlobalAvailConfiguration] as a
	 * [KnownAvailProject].
	 *
	 * @param project
	 *   The [AvailProject] to add.
	 * @param path
	 *   The path to the [AvailProject] file.
	 */
	fun add (project: AvailProject, path: String)
	{
		knownProjects.firstOrNull { it.id == project.id }?.let {
			it.name = project.name
			it.projectConfigFile = path
			it.lastOpened = System.currentTimeMillis()
		} ?: knownProjects.add(
			KnownAvailProject(
				project.name, project.id, path, System.currentTimeMillis()))
		saveToDisk()
	}

	/**
	 * Remove the [KnownAvailProject] with the given [KnownAvailProject.id].
	 *
	 * @param id
	 *   The [KnownAvailProject.id] of the [KnownAvailProject] to remove.
	 */
	fun removeProject (id: String)
	{
		if(knownProjects.removeIf { it.id == id })
		{
			saveToDisk()
		}
	}

	/**
	 * The String contents of this [GlobalAvailConfiguration] that can be
	 * written to disk.
	 */
	@Suppress("unused")
	val fileContent: String get() =
		jsonPrettyPrintWriter {
			this@GlobalAvailConfiguration.writeTo(this)
		}.toString()

	/**
	 * Write this [GlobalAvailConfiguration] to disk.
	 */
	fun saveToDisk ()
	{
		File(configFilePath).writeText(fileContent)
	}

	/**
	 * Reset the [globalTemplates] to the default. This clears all current
	 * [globalTemplates] before adding back the [defaultTemplates].
	 */
	fun resetToDefaultTemplates ()
	{
		globalTemplates.clear()
		globalTemplates.putAll(defaultTemplates)
	}

	/**
	 * Add all the [defaultTemplates] to the [globalTemplates]. This will have
	 * the effect of replacing any [globalTemplates] with the same template name
	 * as a default template. Otherwise, all previous [globalTemplates] that
	 * don't share a name with one of the [defaultTemplates] will remain the
	 * same.
	 */
	fun addAllDefaultTemplates ()
	{
		globalTemplates.putAll(defaultTemplates)
	}

	companion object
	{
		/**
		 * The version that represents the current structure under which Avail
		 * libs are packaged in the artifact.
		 */
		private const val CURRENT_PROJECT_VERSION = 1

		/**
		 * The Avail configuration file name.
		 */
		private const val CONFIG_FILE_NAME = "avail-global-config.json"

		/**
		 * The path to the [GlobalAvailConfiguration] file on disk.
		 */
		private val configFilePath get() =
			"$availHome${File.separator}$CONFIG_FILE_NAME"

		private val emptyConfig: GlobalAvailConfiguration get() =
			GlobalAvailConfigurationV1()

		/**
		 * @return
		 *   The [GlobalAvailConfiguration] read from the
		 *   [AvailEnvironment.availHome] or the [emptyConfig] if not found or
		 *   malformed.
		 */
		fun getGlobalConfig (): GlobalAvailConfiguration
		{
			val file = File(configFilePath)
			if (!file.exists())
			{
				val config = emptyConfig
				config.addAllDefaultTemplates()
				config.saveToDisk()
				return config
			}
			val obj =
				try
				{
					val contents = file.readText()
					jsonObject(contents)
				}
				catch (e: Throwable)
				{
					System.err.println(
						"Malformed Global Avail Configuration File: " +
							"$configFilePath\n. Delete file and restart avail")
					e.printStackTrace()
					throw e
				}
			val version = obj.getNumber(
				GlobalAvailConfiguration::serializationVersion.name).int

			return when (version)
			{
				1 -> GlobalAvailConfigurationV1.from(obj)
				else ->
					throw IllegalStateException("Invalid Global Avail " +
						"Configuration File: Version $version is not in the " +
						"valid range of known global config file versions," +
						" [1, ${CURRENT_PROJECT_VERSION}].")
			}
		}

		/**
		 * The default [globalTemplates].
		 */
		internal val defaultTemplates = mutableMapOf(
			"a" to  "∀",
			"bottom" to  "⊥",
			"ceiling" to  "⌈⁁⌉",
			"celsius" to  "℃",
			"cent" to  "¢",
			"conjunction" to  "∧",
			"convert" to  "≍",
			"copyright" to  "©",
			"cubed" to  "³",
			"degree" to  "°",
			"delta" to  "Δ",
			"disjunction" to  "∨",
			"divide" to  "÷",
			"dot" to  "·",
			"doubledagger" to  "‡",
			"doubleexclamation" to  "‼",
			"doublequestion" to  "⁇",
			"doublequotes" to  "“⁁”",
			"downarrow" to  "↓",
			"downtack" to  "⊤",
			"e" to  "∃",
			"elementof" to  "∈",
			"ellipsis" to  "…",
			"endash" to  "–",
			"emdash" to  "—",
			"emptyset" to  "∅",
			"enumeration" to  "{⁁}ᵀ",
			"equivalent" to  "≍",
			"exists" to  "∃",
			"floor" to  "⌊⁁⌋",
			"forall" to  "∀",
			"gte" to  "≥",
			"guillemets" to  "«⁁»",
			"infinity" to  "∞",
			"interpunct" to  "•",
			"intersection" to  "∩",
			"leftarrow" to  "←",
			"leftceiling" to  "⌈",
			"leftdoublequote" to  "“",
			"leftfloor" to  "⌊",
			"leftguillemet" to  "«",
			"leftsinglequote" to  "‘",
			"lte" to  "≤",
			"manicule" to  "\uD83D\uDC49",
			"map" to  "{⁁}",
			"maplet" to  "↦",
			"mdash" to  "—",
			"memberof" to  "∈",
			"mu" to  "µ",
			"ndash" to  "–",
			"ne" to  "≠",
			"not" to  "¬",
			"notelementof" to  "∉",
			"notmemberof" to  "∉",
			"notsubsetorequal" to  "⊈",
			"notsupersetorequal" to  "⊉",
			"o1" to  "①",
			"o2" to  "②",
			"o3" to  "③",
			"o4" to  "④",
			"o5" to  "⑤",
			"o6" to  "⑥",
			"o7" to  "⑦",
			"o8" to  "⑧",
			"o9" to  "⑨",
			"pi" to  "π",
			"picapital" to  "∏",
			"plusminus" to  "±",
			"prefix" to  "§",
			"rightarrow" to  "→",
			"rightceiling" to  "⌉",
			"rightdoublequote" to  "”",
			"rightfloor" to  "⌋",
			"rightsinglequote" to  "’",
			"rightguillemet" to  "»",
			"root" to  "√",
			"section" to  "§",
			"set" to  "{⁁}",
			"sigma" to  "∑",
			"singledagger" to  "†",
			"singlequotes" to  "‘⁁’",
			"squared" to  "²",
			"subsetorequal" to  "⊆",
			"sum" to  "∑",
			"supersetorequal" to  "⊇",
			"supert" to  "ᵀ",
			"swoop" to  "⤷",
			"t" to  "ᵀ",
			"thereexists" to  "∃",
			"thinspace" to  " ",
			"times" to  "×",
			"top" to  "⊤",
			"tuple" to  "<⁁>",
			"union" to  "∪",
			"uparrow" to  "↑",
			"uptack" to  "⊥",
			"xor" to  "⊕",
			"yields" to  "⇒",
			"1" to  "¹",
			"2" to  "²",
			"3" to  "³",
			"4" to  "⁴",
			"5" to  "⁵",
			"6" to  "⁶",
			"7" to  "⁷",
			"8" to  "⁸",
			"9" to  "⁹",
			"->" to  "→",
			"=>" to  "⇒",
			"<-" to  "←",
			"*" to  "×",
			"/" to  "÷",
			"<=" to  "≤",
			">=" to  "≥",
			"!" to  "¬",
			"!!" to  "‼",
			"!=" to  "≠",
			"..." to  "…",
			"??" to  "⁇")
	}
}
