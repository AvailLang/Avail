/*
 * All rights reserved.
 * Copyright © 1993-2018, The Avail Foundation, LLC.
 * IndexedRepositoryManagerDescriber.java
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

package com.avail.persistence;

import com.avail.AvailRuntime;
import com.avail.descriptor.A_Module;
import com.avail.persistence.IndexedRepositoryManager.ModuleArchive;
import com.avail.persistence.IndexedRepositoryManager.ModuleCompilation;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersion;
import com.avail.persistence.IndexedRepositoryManager.ModuleVersionKey;
import com.avail.serialization.DeserializerDescriber;
import com.avail.serialization.MalformedSerialStreamException;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.SortedMap;

import static com.avail.builder.AvailBuilder.validatedBytesFrom;

/**
 * An {@code IndexedRepositoryManagerDescriber} provides a textual
 * representation of an {@link IndexedRepositoryManager}, showing the contained
 * {@link A_Module modules}, {@link ModuleVersion versions}, and {@link
 * ModuleCompilation compilations}.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class IndexedRepositoryManagerDescriber
{
	/** The open {@link IndexedRepositoryManager repository} to be described. */
	final IndexedRepositoryManager repository;

	/**
	 * Create a describer for the given repository.
	 *
	 * @param repository
	 *        The {@link IndexedRepositoryManager repository} to be described.
	 */
	public IndexedRepositoryManagerDescriber (
		final IndexedRepositoryManager repository)
	{
		this.repository = repository;
	}

	/**
	 * Produce a summary of the entire repository.
	 *
	 * @return A {@link String} describing the repository.
	 */
	public String dumpAll ()
	{
		final StringBuilder builder = new StringBuilder();
		final List<ModuleArchive> archives = repository.getAllArchives();
		for (final ModuleArchive archive : archives)
		{
			builder.append(archive.rootRelativeName);
			builder.append('\n');
			final SortedMap<ModuleVersionKey, ModuleVersion> versionMap =
				archive.getAllKnownVersions();
			versionMap.forEach((versionKey, version) ->
			{
				builder.append('\t');
				builder.append(versionKey.shortString());
				builder.append('\n');
				version.allCompilations().forEach(
					compilation ->
					{
						builder.append("\t\t");
						builder.append("Rec #");
						builder.append(compilation.recordNumber);
						builder.append("\n");
					}
				);
			});
		}
		return builder.toString();
	}

	/**
	 * Describe a single compilation from the repository.  The supplied record
	 * number should have been one of the values produced by {@link #dumpAll()}.
	 *
	 * @param recordNumber The record number.
	 * @return A description of the serialization in the specified record.
	 */
	public String describeCompilation (final long recordNumber)
	{
		final byte[] record = repository.repository().get(recordNumber);
		try
		{
			final ByteArrayInputStream stream = validatedBytesFrom(record);
			final DeserializerDescriber describer =
				new DeserializerDescriber(stream, AvailRuntime.currentRuntime());
			return describer.describe();
		}
		catch (final MalformedSerialStreamException e)
		{
			return "Serialized record is malformed";
		}
	}
}
