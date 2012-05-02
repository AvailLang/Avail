/**
 * AvailBuilderRepository.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.builder;

/**
 * This class tracks changes to files to determine the minimum effort required
 * to recompile one or more Avail source files.
 *
 * <p>
 * The key technique is the use of large hashes which have a cosmologically low
 * probability of collision.  Hashing is applied to source code, object code,
 * and collections of other hashes.
 * <p>
 *
 * <p>
 * There are five associative structures that are populated on demand:
 * <ol>
 * <li>{@link #sourceHashes} - the mapping from {@link FileVersion} to the hash
 * of the source code.</li>
 * <li>{@link #imports} - the mapping from source code hash to the list of
 * filenames specified as imports in that source code.</i>
 * <li>{@link #situations} - a map whose keys are
 * </ol>
 *
 *
 *
	-map #1 ("sourceHashes") from <filename, time stamp> to source hash
		-avoids rehashing for unchanged files.
	-map #2 ("imports") from source hash to tuple of imported filenames
		-avoids cost of re-extracting local dependencies.
		-Note: maps #1 and #2 are enough to produce the dependency graph.
	-map #3 ("situations") from <source hash, mixture of imported files' object hashes> to situation hash
		-same source with same imported object files should produce same object file (i.e., is same situation)
	-map #4 ("objectCodeHashes") from situation hash to object hash
		-multiple situations can lead to the same object hash.
	-map #5 ("objectCode") from object hash to actual object code
		-don't store equivalent object code in two places.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public class Repository
{
//	static class FileVersion
//	{
//		final String moduleName;
//		final Calendar timestamp;
//		MessageDigest digest;
//
//		FileVersion (final String moduleName, final Calendar timestamp)
//		{
//			this.moduleName = moduleName;
//			this.timestamp = timestamp;
//		}
//
//		computeHash ()
//		{
//			MessageDigest digest;
//			try
//			{
//				digest = MessageDigest.getInstance("SHA-256");
//			}
//			catch (final NoSuchAlgorithmException e)
//			{
//				throw new RuntimeException(e);
//			}
//			digest.
//		}
//	}
//
//	static class SourceHash
//	{
//		@InnerAccess
//		final byte[] hash;
//	}
//
//	static class
//
//	Map<FileVersion, byte[]> sourceHashes;
//	Map<

//	-map #1 ("sourceHashes") from <filename, timestamp> to source hash
//		-avoids rehashing for unchanged files.
//	-map #2 ("imports") from source hash to tuple of imported filenames
//		-avoids cost of re-extracting local dependencies.
//		-Note: maps #1 and #2 are enough to produce the dependency graph.
//	-map #3 ("situations") from <source hash, mixture of imported files' object hashes> to situation hash
//		-same source with same imported object files should produce same object file (i.e., is same situation)
//	-map #4 ("objectCodeHashes") from situation hash to object hash
//		-multiple situations can lead to the same object hash.
//	-map #5 ("objectCode") from object hash to actual object code
//		-don't store equivalent object code in two places.
}
