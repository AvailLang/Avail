/*
 * ResourceTypeManager.kt
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

package org.availlang.artifact

import org.availlang.artifact.ResourceTypeManager.HeaderlessExtension
import org.availlang.artifact.environment.project.AvailProjectRoot
import org.availlang.artifact.roots.AvailRoot
import org.availlang.json.JSONFriendly
import org.availlang.json.JSONObject
import org.availlang.json.JSONWriter
import java.io.File
import java.net.URI
import java.util.Enumeration
import java.util.zip.ZipEntry

/**
 * Manages the identification and creation of [ResourceType]s for an
 * [AvailProjectRoot].
 *
 * @author Richard Arriaga
 *
 * @property moduleFileExtension
 *   The file extension for full Avail [modules][ResourceType.Module] for the
 *   associated [AvailProjectRoot]. A common experience for Avail as a general
 *   programming language defaults the file extension to `"avail"`, however this
 *   can be set to any extension desired. The motivation to change this from
 *   the default `avail` will likely be for one of two reasons:
 *   1. It's neat!
 *   2. You are providing a DSL as a library, but you still need a package
 *   [ResourceType.Representative] in each of the [ResourceType.Package]s where
 *   the DSL [ResourceType.HeaderlessModule]s will be linked inside the
 *   [package representative's][ResourceType.Representative] `Corpus` section of
 *   the header so that the DSL modules are recognized and built by Avail.
 * @property headerlessExtensions
 *   The set of [HeaderlessExtension]s supported by the associated
 *   [AvailProjectRoot].
 */
data class ResourceTypeManager(
	val moduleFileExtension: String = "avail",
	val headerlessExtensions: Set<HeaderlessExtension> = setOf()
): JSONFriendly
{
	/**
	 * The [ResourceType.Module] for the associated [AvailRoot].
	 */
	val moduleType: ResourceType.Module =
		ResourceType.Module(moduleFileExtension)

	/**
	 * The [ResourceType.Module] for the associated [AvailRoot].
	 */
	val representativeType: ResourceType.Representative =
		ResourceType.Representative(moduleFileExtension)

	/**
	 * The [ResourceType.Package] that uses the [moduleFileExtension].
	 */
	private val packageTypeWithExtension =
		ResourceType.Package(moduleFileExtension)

	/**
	 * The [ResourceType.Package] that does not use the [moduleFileExtension].
	 */
	private val packageTypeWithoutExtension =
		ResourceType.Package("")

	/**
	 * Answer the [ResourceType.Package] for a given directory's name that is
	 * already known to be a [ResourceType.Package].
	 *
	 * @param dirName
	 *   The name of the directory to get the [ResourceType.Package] for.
	 * @return
	 *   The [ResourceType.Package] for this directory.
	 */
	fun packageType (dirName: String): ResourceType.Package =
		if (dirName.endsWith(moduleFileExtension))
		{
			packageTypeWithExtension
		}
		else
		{
			packageTypeWithoutExtension
		}

	/**
	 * Cleanse the given name of all [moduleFileExtension]s in the path.
	 *
	 * @param fileName
	 *   The file name path to cleanse.
	 * @return
	 *   The file name with all usages of [moduleFileExtension] removed.
	 */
	fun cleanseAsQualifiedName (fileName: String) =
		fileName.split("/")
			.joinToString("/")
			{
				it.removeSuffix(".$moduleFileExtension")
			}

	/**
	 * Answer a qualified name for the given [URI] that points to a file in
	 * the [AvailRoot].
	 *
	 * @param rootName
	 *   The [AvailRoot.name] of the root this is relative to.
	 * @param rootUri
	 *   The [URI] path to the [AvailRoot]
	 * @param targetURI
	 *   The [URI] to transform into qualified name.
	 * @return The qualified name.
	 */
	fun getQualifiedName(
		rootName: String,
		rootUri: URI,
		targetURI: String,
		resourceType: ResourceType
	): String
	{
		// Re-normalize the uri path, which must a be file-like URI.
		val uriPath = File(rootUri.path).toString()
		assert(targetURI.startsWith(uriPath)) {
			"$targetURI is not in ModuleRoot, $rootName"
		}
		val relative = targetURI.split("$uriPath/")[1]
		val extension =
			when(resourceType)
			{
				is ResourceType.FileExtension -> resourceType.fileExtension
				else -> ""
			}
		val cleansedRelative =
			cleanseAsQualifiedName(relative).removeSuffix(".$extension")
		return "/$rootName" +
			(if (relative.startsWith("/")) "" else "/") +
			cleansedRelative
	}

	/**
	 * Answers an Avail module resource type; one of
	 * - [ResourceType.Module]
	 * - [ResourceType.HeaderModule]
	 * - [ResourceType.HeaderlessModule]
	 *
	 * @param fileName
	 *   The name of the file to get the Avail [ResourceType] for.
	 * @return
	 *   The Avail module [ResourceType] or `null` if the file does not
	 *   represent a module file.
	 */
	private fun availModuleResourceType(fileName: String): ResourceType? =
		if (fileName.endsWith(moduleFileExtension))
		{
			moduleType
		}
		else
		{
			headerlessExtensions
				.map { it.resourceType(fileName) }
				.firstNotNullOfOrNull { it }
		}

	/**
	 * Answer the appropriate [ResourceType] for the provided [File].
	 *
	 * @param file
	 *   The [File] to check.
	 * @return
	 *   The [ResourceType] that represents the file.
	 */
	fun determineResourceType (file: File): ResourceType
	{
		val fileName = file.name
		return if (file.isDirectory)
		{
			when
			{
				fileName.endsWith(".$moduleFileExtension") ->
					packageTypeWithExtension
				file.listFiles { f ->
						!f.isDirectory && f.name.endsWith(
							"$fileName.$moduleFileExtension")
					}?.isNotEmpty() ?: false ->
						packageTypeWithoutExtension
				else -> ResourceType.Directory
			}
		}
		else
		{
			if (fileName.startsWith(file.parentFile.name) &&
				fileName.endsWith(".$moduleFileExtension")
			) {
				representativeType
			}
			else
			{
				availModuleResourceType(fileName) ?: ResourceType.Resource
			}
		}
	}

	/**
	 * Answer the appropriate [ResourceType] for the provided [ZipEntry].
	 *
	 * @param rootNameInZip
	 *   The name of the root to extract as it exists in the zip file.
	 * @param entries
	 *   The [ZipEntry]s from the zip file where the root is.
	 * @return
	 *   The map of to its [AvailRoot] path relative name to the corresponding
	 *   [AvailRootFileMetadata].
	 */
	fun <Entry: ZipEntry>getAvailRootFilesMetadata (
		rootName: String,
		rootNameInZip: String,
		entries: Enumeration<Entry>,
		digests: Map<String, ByteArray>?
	): Map<String, AvailRootFileMetadata>
	{
		val prefix = "${AvailArtifact.artifactRootDirectory}/$rootNameInZip/" +
			"${AvailArtifact.availSourcesPathInArtifact}/"

		// The list of deferred ZipEntry name suffixes (without the prefix) that
		// cannot have their ResourceType determined on the first pass. These
		// will either be a Package or Directory.
		val deferred = mutableListOf<Pair<String, ZipEntry>>()

		val metadataMap = mutableMapOf<String, AvailRootFileMetadata>()

		val qualifiedName = { entryName: String ->
			entryName.split("/")
				.joinToString("/", prefix = "/$rootName/")
				{
					it.removeSuffix(".$moduleFileExtension")
				}
		}

		for (entry in entries)
		{
			var entryName = entry.name.replace("\\", "/")
			if (!entryName.startsWith(prefix)) continue
			entryName = entryName.removePrefix(prefix)
			if (entryName.isEmpty()) continue
			val type = when
			{
				entry.isDirectory &&
					entryName.endsWith(".$moduleFileExtension/") ->
						packageTypeWithExtension

				entry.isDirectory ->
				{
					// Might be a package or a directory, which cannot be
					// determined right now, so defer its ResourceType
					// calculation
					deferred.add(Pair(entryName, entry))
					continue
				}
				else ->
				{
					assert(!entry.isDirectory)
					val parts = entryName.split("/")
					val parentName =
						if (parts.size >= 2) parts[parts.size - 2]
						else ""
					val extensionlessName =
						parts.last().removeSuffix(".$moduleFileExtension")
						if (parts.last() == parentName ||
							parentName.removePrefix(extensionlessName).isEmpty()
						){
							representativeType
						}
						else
						{
							availModuleResourceType(entryName) ?:
									ResourceType.Resource
						}
				}
			}
			metadataMap[entryName] =
				AvailRootFileMetadata(
					entryName,
					type,
					qualifiedName(entryName),
					type.mimeType,
					entry.lastModifiedTime.toMillis(),
					entry.size,
					digests?.get(entryName))
		}
		deferred.forEach { (entryName, entry) ->
			val packageRepName =
				"$entryName${entryName.split("/").last()}." +
					moduleFileExtension
			val type  =
				if(metadataMap.containsKey(packageRepName))
				{
					packageTypeWithoutExtension
				}
				else
				{
					ResourceType.Directory
				}
			metadataMap[entryName] =
				AvailRootFileMetadata(
					entryName,
					type,
					qualifiedName(entryName),
					type.mimeType,
					entry.lastModifiedTime.toMillis(),
					entry.size,
					digests?.get(entryName))
		}
		return metadataMap
	}

	override fun writeTo(writer: JSONWriter)
	{
		writer.writeObject {
			at(::moduleFileExtension.name) { write(moduleFileExtension) }
			at(::headerlessExtensions.name) {
				writeArray {
					headerlessExtensions.forEach { it.writeTo(this) }
				}
			}
		}
	}

	/**
	 * Represents the file extensions for [ResourceType.HeaderlessModule]s and
	 * their associated [ResourceType.HeaderModule].
	 *
	 * @author Richard Arriaga
	 *
	 * @property headerlessExtension
	 *   The file extension for an Avail [ResourceType.HeaderlessModule].
	 * @property headerExtension
	 *   The file extension for the Avail [ResourceType.HeaderModule] associated
	 *   with the [headerless module][ResourceType.HeaderlessModule] with the
	 *   file extension, [headerlessExtension]. The common practice is to let
	 *   the header extension to default to the "[headerlessExtension].header".
	 *   Though not required, following this practice makes identifying
	 *   [ResourceType.HeaderModule] files and their associated
	 *   [ResourceType.HeaderlessModule]s easier.
	 * @property headerlessFileIcon
	 *   The [AvailRoot] resource directory relative file name for the file icon
	 *   used to represent the files with the [headerlessExtension] in a file
	 *   system view or `null` if no special icon is set for these files.
	 * @property headerFileIcon
	 *   The [AvailRoot] resource directory relative file name for the file icon
	 *   used to represent the files with the [headerExtension] in a file system
	 *   view or `null` if no special icon is set for these files.
	 */
	data class HeaderlessExtension constructor(
		val headerlessExtension: String,
		val headerExtension: String = "$headerlessExtension.header",
		val headerlessFileIcon: String? = null,
		val headerFileIcon: String? = null,
	): JSONFriendly
	{
		/**
		 * The [ResourceType.HeaderModule] to assign to Avail modules that have
		 * the file extension, [headerExtension].
		 */
		val headerType: ResourceType.HeaderModule =
			ResourceType.HeaderModule(headerExtension, headerFileIcon)

		/**
		 * The [ResourceType.HeaderlessModule] to assign headerless Avail
		 * modules that have the file extension, [headerlessExtension].
		 */
		val headerlessType: ResourceType.HeaderlessModule =
			ResourceType.HeaderlessModule(
				headerlessExtension, headerType, headerlessFileIcon)

		/**
		 * Answer the [ResourceType] based on the provided file name's file
		 * extension.
		 *
		 * @param filename
		 *   The name of the file to determine the [ResourceType] for.
		 * @return
		 *   A [ResourceType] if it has an extension from this
		 *   [HeaderlessExtension]; `null` otherwise.
		 */
		internal fun resourceType (filename: String): ResourceType? =
			when
			{
				filename.endsWith(headerlessExtension) -> headerlessType
				filename.endsWith(headerExtension)-> headerType
				else -> null
			}

		override fun writeTo(writer: JSONWriter)
		{
			writer.writeObject {
				at(::headerlessExtension.name) { write(headerlessExtension) }
				at(::headerExtension.name) { write(headerExtension) }
				at(::headerlessFileIcon.name) { write(headerlessFileIcon) }
				at(::headerFileIcon.name) { write(headerFileIcon) }
			}
		}
		companion object
		{
			/**
			 * Extract an [HeaderlessExtension] from the given [JSONObject].
			 *
			 * @param obj
			 *   The [JSONObject] to extract the data from.
			 * @return
			 *   The extracted [HeaderlessExtension].
			 * @throws AvailArtifactException
			 *   If there is an issue with extracting the [ResourceTypeManager].
			 */
			fun from(
				obj: JSONObject
			): HeaderlessExtension
			{
				val headerlessExtension =
					try
					{
						obj.getString(
							HeaderlessExtension::headerlessExtension.name)
					}
					catch (e: Throwable)
					{
						throw AvailArtifactException(
							"Problem extracting " +
								"ResourceTypeManager.HeaderlessExtension " +
								"headerlessExtension.",
							e)
					}
				val headerExtension =
					try
					{
						obj.getString(
							HeaderlessExtension::headerExtension.name)
					}
					catch (e: Throwable)
					{
						throw AvailArtifactException(
							"Problem extracting " +
								"ResourceTypeManager.HeaderlessExtension " +
								"headerExtension.",
							e)
					}
				val headerFileIcon =
					try
					{
						obj.getStringOrNull(
							HeaderlessExtension::headerFileIcon.name)
					}
					catch (e: Throwable)
					{
						throw AvailArtifactException(
							"Problem extracting " +
								"ResourceTypeManager.HeaderlessExtension " +
								"headerFileIcon.",
							e)
					}
				val headerlessFileIcon =
					try
					{
						obj.getStringOrNull(
							HeaderlessExtension::headerlessFileIcon.name)
					}
					catch (e: Throwable)
					{
						throw AvailArtifactException(
							"Problem extracting " +
								"ResourceTypeManager.HeaderlessExtension " +
								"headerlessFileIcon.",
							e)
					}
				return HeaderlessExtension(
					headerlessExtension,
					headerExtension,
					headerlessFileIcon,
					headerFileIcon)
			}
		}
	}

	companion object
	{
		/**
		 * Extract an [ResourceTypeManager] from the given [JSONObject].
		 *
		 * @param obj
		 *   The [JSONObject] to extract the data from.
		 * @return
		 *   The extracted [ResourceTypeManager].
		 * @throws AvailArtifactException
		 *   If there is an issue with extracting the [ResourceTypeManager].
		 */
		fun from (obj: JSONObject): ResourceTypeManager
		{
			val moduleFileExtension =
				try
				{
					obj.getString(ResourceTypeManager::moduleFileExtension.name)
				}
				catch (e: Throwable)
				{
					throw AvailArtifactException(
						"Problem extracting ResourceTypeManager " +
							"moduleFileExtension.",
						e)
				}
			val headerlessExtensions =
				try
				{
					obj.getArray(
						ResourceTypeManager::headerlessExtensions.name
					).map {
						HeaderlessExtension.from(it as JSONObject)
					}.toMutableSet()
				}
				catch (e: Throwable)
				{
					throw AvailArtifactException(
						"Problem extracting ResourceTypeManager " +
							"headerlessExtensions.",
						e)
				}
			return ResourceTypeManager(
				moduleFileExtension, headerlessExtensions)
		}
	}
}
