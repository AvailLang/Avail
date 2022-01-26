/*
 * RepositoryDescriber.kt
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

package avail.persistence.cache

import avail.AvailRuntime
import avail.compiler.ModuleManifestEntry
import avail.descriptor.module.A_Module
import org.availlang.persistence.IndexedFile.Companion.validatedBytesFrom
import avail.persistence.cache.Repository.ModuleCompilation
import avail.persistence.cache.Repository.ModuleVersion
import avail.serialization.DeserializerDescriber
import org.availlang.persistence.MalformedSerialStreamException
import java.io.DataInputStream

/**
 * An `RepositoryDescriber` provides a textual representation of
 * a [Repository], showing the contained [modules][A_Module],
 * [versions][ModuleVersion], and [compilations][ModuleCompilation].
 *
 * @property repository
 *   The open [repository][Repository] to be described.
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Create a describer for the given repository.
 *
 * @param repository
 *   The [repository][Repository] to be described.
 */
class RepositoryDescriber constructor(
	internal val repository: Repository)
{
	/**
	 * Produce a summary of the entire repository.
	 *
	 * @return
	 *   A [String] describing the repository.
	 */
	fun dumpAll(): String
	{
		val builder = StringBuilder()
		val archives = repository.allArchives
		for (archive in archives)
		{
			builder.append(archive.rootRelativeName)
			builder.append('\n')
			val versionMap = archive.allKnownVersions
			versionMap.forEach { (versionKey, version) ->
				builder.append('\t')
				builder.append(versionKey.shortString)
				builder.append('\n')
				version.allCompilations.forEach { compilation ->
					builder.append("\t\tCompilation #")
					builder.append(compilation.recordNumber)
					builder.append("\n\t\tPhrases #")
					builder.append(compilation.recordNumberOfBlockPhrases)
					builder.append("\n\t\tManifest #")
					builder.append(compilation.recordNumberOfManifestEntries)
					builder.append("\n")
				}
			}
		}
		return builder.toString()
	}

	/**
	 * Describe a single compilation from the repository.  The supplied record
	 * number should have been one of the values produced by [dumpAll].
	 *
	 * @param recordNumber
	 *   The record number.
	 * @return
	 *   A description of the serialization in the specified record.
	 */
	fun describeCompilation(recordNumber: Long): String
	{
		val record = repository.repository!![recordNumber]
		return try
		{
			val stream = validatedBytesFrom(record)
			val describer =
				DeserializerDescriber(stream, AvailRuntime.currentRuntime())
			describer.describe()
		}
		catch (e: MalformedSerialStreamException)
		{
			"Serialized record is malformed"
		}
	}

	fun describeManifest(recordNumberOfManifestEntries: Long): String
	{
		val record = repository.repository!![recordNumberOfManifestEntries]
		return buildString {
			val input = DataInputStream(validatedBytesFrom(record))
			while (input.available() > 0)
			{
				ModuleManifestEntry(input).run {
					append("$kind $summaryText\n")
					append("\ttopLevel = $topLevelStartingLine\n")
					append("\tdefBody = $definitionStartingLine\n")
					append("\tbodyPhrase# = $bodyPhraseIndexNumber\n")
				}
			}
		}
	}

	/**
	 * Answer the list of [ModuleManifestEntry]s.
	 *
	 * @param recordNumberOfManifestEntries
	 *   The record number to use to look up the manifest entries.
	 */
	fun manifestEntries(recordNumberOfManifestEntries: Long): List<ModuleManifestEntry>
	{
		val record =
			repository.repository!![recordNumberOfManifestEntries]
		val input = DataInputStream(validatedBytesFrom(record))
		val entries = mutableListOf<ModuleManifestEntry>()
		while (input.available() > 0)
		{
			entries.add(ModuleManifestEntry(input))
		}
		return entries
	}
}
