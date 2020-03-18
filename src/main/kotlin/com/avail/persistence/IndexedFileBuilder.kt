/*
 * IndexedFileBuilder.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice, this
 *     list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice, this
 *     list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
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
package com.avail.persistence

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * [IndexedFileBuilder] is an abstract class whose specializations say how to
 * configure an [IndexedFile] being created or opened.
 *
 * @constructor
 * @param headerString
 *   The [String] to check for at the start of a file implementing this kind of
 *   specialization of an [IndexedFile].
 * @return
 *   An array of bytes that uniquely identifies the purpose of the indexed
 *   file.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Skatje Myers &lt;skatje.myers@gmail.com&gt;
 */
abstract class IndexedFileBuilder protected constructor(headerString: String)
{
	/**
	 * The NUL-terminated header bytes that uniquely identify a particular usage
	 * of the core [IndexedFile] technology.
	 */
	val headerBytes = headerString.toByteArray(Charsets.UTF_8)

	/**
	 * The default page size of the [IndexedFile]. This should be a multiple of
	 * the disk page size for good performance; for best performance, it should
	 * be a common multiple of the disk page size and the memory page size.
	 */
	open val defaultPageSize: Int = 4096

	/**
	 * The default minimum number of uncompressed bytes at the virtualized end
	 * of an indexed file. Once this many uncompressed bytes have accumulated
	 * from complete records, they will be compressed together. Since
	 * uncompressed data must be written to a master node during each commit,
	 * this value should not be too large; but since compression efficiency
	 * improves as block size increases, this value should not be too small. A
	 * small multiple of the [defaultPageSize] is optimal.
	 */
	open val defaultCompressionThreshold = 32768

	/**
	 * The default capacity of the cache of uncompressed records. A number of
	 * records equal to the delta between this value and that of
	 * [defaultStrongCacheSize] will be discarded from the cache by the garbage
	 * collector when a low-water mark is reached.
	 */
	open val defaultSoftCacheSize = 200

	/**
	 * The default memory-insensitive capacity of the cache of uncompressed
	 * records.
	 */
	open val defaultStrongCacheSize = 100

	/**
	 * The default version of the [IndexedFile] being built.  As
	 * representational improvements show up over the years, this value should
	 * be incremented, while maintaining suitable backward compatible for some
	 * time.  Currently, only version 3 is supported.
	 */
	open val defaultVersion: Int = 3

	/** The default function for checking version compatibility. */
	open fun defaultVersionCheck(a: Int, b: Int) = a == b

	/**
	 * Create new file of this type, with type-specific parameters overridden
	 * as specified.
	 */
	@Throws(IOException::class)
	fun openOrCreate(
		fileReference: File,
		forWriting: Boolean,
		setupActionIfNew: (IndexedFile.() -> Unit) = { },
		softCacheSize: Int = defaultSoftCacheSize,
		strongCacheSize: Int = defaultStrongCacheSize,
		pageSize: Int = defaultPageSize,
		compressionBlockSize: Int = defaultCompressionThreshold,
		version: Int = defaultVersion,
		versionCheck: (Int, Int) -> Boolean = ::defaultVersionCheck
	): IndexedFile
	{
		// Note that if forWriting == false, we should really open with use mode
		// "r", but Java stupidly prevents such a RandomAccessFile's channel
		// from acquiring an exclusive lock!  So we do the dance of detecting if
		// the file exists, then opening it with a mode that'll accidentally
		// create an empty one in a race.
		var existed = fileReference.exists()
		val file = when {
			forWriting -> RandomAccessFile(fileReference, "rw")
			existed -> RandomAccessFile(fileReference, "rw") // See above
			else -> throw IndexedFileException("No such index file")
		}
		if (fileReference.length() == 0L)
		{
			// The file was probably created but not initialized in a previous
			// attempt.  Complete the attempt here, if we're allowed to write.
			if (!forWriting) {
				throw IndexedFileException(
					"Indexed file opened for read is zero bytes")
			}
			// Just initialize it like it's new.
			existed = false
		}
		return IndexedFile(
			fileReference,
			file,
			headerBytes,
			forWriting,
			if (existed) null else setupActionIfNew,
			softCacheSize,
			strongCacheSize,
			pageSize,
			compressionBlockSize,
			version,
			versionCheck)
	}
}
