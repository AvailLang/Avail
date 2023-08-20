/*
 * AvailRoot.kt
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

package org.availlang.artifact.roots

import org.availlang.artifact.environment.AvailEnvironment
import org.availlang.artifact.environment.location.AvailLocation
import org.availlang.artifact.environment.project.AvailProject
import org.availlang.artifact.environment.project.AvailProjectRoot
import org.availlang.artifact.environment.project.LocalSettings
import org.availlang.artifact.environment.project.Palette
import org.availlang.artifact.environment.project.TemplateGroup
import org.availlang.artifact.environment.project.StylingGroup
import org.availlang.artifact.manifest.AvailRootManifest
import java.security.MessageDigest

/**
 * `AvailRoot` represents an Avail source root.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property name
 *   The name of the root.
 * @property location
 *   The [AvailLocation] of the root.
 * @property digestAlgorithm
 *   The [MessageDigest] algorithm to use to create the digests for all the
 *   root's contents. This must be a valid algorithm accessible from
 *   [java.security.MessageDigest.getInstance].
 * @property availModuleExtensions
 *   The file extensions that signify files that should be treated as Avail
 *   modules.
 * @property entryPoints
 *   The Avail entry points exposed by this root.
 * @property templateGroup
 *   The templates that should be available when editing Avail source
 *   modules in the workbench.
 * @property styles
 *   The default stylesheet for this root. Symbolic names are resolved against
 *   the accompanying [Palette].
 * @property description
 *   An optional description of the root.
 * @property action
 *   A lambda that accepts this [AvailRoot] and is executed after all roots have
 *   been added.
 *
 * @constructor
 * Construct an [AvailRoot].
 *
 * @param name
 *   The name of the root.
 * @param location
 *   The [AvailLocation] of the root.
 * @param digestAlgorithm
 *   The [MessageDigest] algorithm to use to create the digests for all the
 *   root's contents. This must be a valid algorithm accessible from
 *   [java.security.MessageDigest.getInstance].
 * @param availModuleExtensions
 *   The file extensions that signify files that should be treated as Avail
 *   modules.
 * @param entryPoints
 *   The Avail entry points exposed by this root.
 * @param templateGroup
 *   The templates that should be available when editing Avail source
 *   modules in the workbench.
 * @param styles
 *   The [StylingGroup] for this [AvailRoot].
 * @param description
 *   An optional description of the root.
 * @param action
 *   A lambda that accepts this [AvailRoot] and is executed after all roots have
 *   been added.
 */
open class AvailRoot constructor(
	val name: String,
	val location: AvailLocation,
	val digestAlgorithm: String = "SHA-256",
	val availModuleExtensions: MutableList<String> = mutableListOf("avail"),
	val entryPoints: MutableList<String> = mutableListOf(),
	val templateGroup: TemplateGroup = TemplateGroup(),
	val styles: StylingGroup = StylingGroup(),
	val description: String = "",
	var action: (AvailRoot) -> Unit = {}
) : Comparable<AvailRoot>
{
	/**
	 * Construct an [AvailRoot].
	 *
	 * @param location
	 *   The [AvailLocation] of the root.
	 * @param manifestRoot
	 *   The [AvailRootManifest] that describes this [AvailRoot].
	 * @param action
	 *   A lambda that accepts this [AvailRoot] and is executed after all roots have
	 *   been added.
	 */
	@Suppress("unused")
	constructor(
		location: AvailLocation,
		manifestRoot: AvailRootManifest,
		action: (AvailRoot) -> Unit
	): this(
		manifestRoot.name,
		location,
		manifestRoot.digestAlgorithm,
		manifestRoot.availModuleExtensions,
		manifestRoot.entryPoints,
		manifestRoot.templates,
		manifestRoot.styles,
		manifestRoot.description,
		action)

	/**
	 * The String absolute path to location of the root.
	 */
	val absolutePath: String = location.fullPathNoPrefix


	/** The VM Options, `-DavailRoot`, root string. */
	val rootString: String by lazy { "$name=$absolutePath" }

	/**
	 * The printable configuration for this root.
	 */
	open val configString: String get() = "\n\t$name ($absolutePath)"

	/**
	 * Create an [AvailRootManifest] from this [AvailRoot].
	 */
	@Suppress("MemberVisibilityCanBePrivate")
	val manifest: AvailRootManifest get() =
		AvailRootManifest(
			name,
			availModuleExtensions,
			entryPoints,
			templateGroup.markedForInclusion,
			styles,
			description,
			digestAlgorithm)

	/**
	 * Create a new [AvailProjectRoot] from this [AvailRoot].
	 *
	 * @param projectFileName
	 *   The name of the [AvailProject] configuration file.
	 * @param projectDirectory
	 *   The root directory of the [AvailProject].
	 * @return
	 *   A new [AvailProjectRoot].
	 */
	@Suppress("unused")
	fun createProjectRoot(
		projectFileName: String,
		projectDirectory: String
	): AvailProjectRoot =
		AvailProjectRoot(
			AvailEnvironment.projectRootConfigPath(
				projectFileName,
				name,
				projectDirectory),
			projectDirectory,
			name,
			location,
			LocalSettings(
				AvailEnvironment.projectRootConfigPath(
					projectFileName,
					name,
					projectDirectory)),
			styles,
			templateGroup,
			availModuleExtensions)

	// Module packages always come before modules.
	override fun compareTo(other: AvailRoot): Int =
		when
		{
			this is CreateAvailRoot && other is CreateAvailRoot ||
			this !is CreateAvailRoot && other !is CreateAvailRoot ->
				name.compareTo(other.name)
			this is CreateAvailRoot -> 1
			else ->
				// Therefore: this !is CreateAvailRoot && other is CreateAvailRoot
				-1
		}

	override fun toString(): String = rootString

	override fun equals(other: Any?): Boolean =
		when
		{
			this === other -> true
			other !is AvailRoot -> false
			name != other.name -> false
			absolutePath != other.absolutePath -> false
			else -> true
		}

	override fun hashCode(): Int
	{
		var result = name.hashCode()
		result = 31 * result + absolutePath.hashCode()
		return result
	}
}
