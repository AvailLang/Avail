/*
 * IndexedFile.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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
package com.avail.persistence

import com.avail.persistence.IndexedRepositoryManager.Companion.log
import com.avail.utility.Casts.cast
import com.avail.utility.LRUCache
import com.avail.utility.Nulls.stripNull
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Level
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * `IndexedFile`s are record journals. Records may be [added][add], explicitly
 * [committed][commit], and [looked up by record number][get]. A single
 * arbitrary [metaData] metadata section can be [attached][metaData] to an
 * indexed file (and will be replaced by subsequent attachments). Concurrent
 * read access is supported for multiple [threads][Thread], drivers, and [OS
 * processes][Process]. Only one writer is permitted.
 *
 * Only subclasses of `IndexedFile` are intended for direct use. A subclass must
 * implement [headerBytes][headerBytes].
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 * @author Skatje Myers &lt;skatje.myers@gmail.com&gt;
 */
abstract class IndexedFile
{
	/**
	 * The [lock][ReentrantReadWriteLock] that guards against unsafe concurrent
	 * access.
	 */
	private val lock = ReentrantReadWriteLock()

	/** The [file reference][File]. */
	private var fileReference: File? = null

	/** The underlying [file][RandomAccessFile]. */
	private var file: RandomAccessFile? = null

	/**
	 * An open [channel][FileChannel] on the underlying
	 * [file][RandomAccessFile].
	 */
	private var channel: FileChannel? = null

	/**
	 * A lock on the last indexable byte of the file.  Not the last byte of the
	 * actual extant file, but the last indexable byte (2^63-1).  This allows
	 * multiple readers or one writer.
	 */
	private var longTermLock: FileLock? = null

	/**
	 * The page size of the `IndexedFile`. This should be a multiple of the disk
	 * page size for good performance; for best performance, it should be a
	 * common multiple of the disk page size and the memory page size.
	 */
	internal var pageSize: Int = 0

	/**
	 * The minimum number of uncompressed bytes at the virtualized end of an
	 * indexed file. Once this many uncompressed bytes have accumulated, then
	 * they will be compressed. Since uncompressed data must be written to a
	 * master node during a commit, this value should not be too large; but
	 * since compression efficiency improves as block size increases, this value
	 * should not be too small. A small multiple of the [page size][pageSize]
	 * is optimal.
	 */
	internal var compressionBlockSize: Int = 0

	/**
	 * The index node arity of the indexed file. The index node arity is the
	 * maximum number of children that an index node may possess. Higher arity
	 * reduces the depth of an index tree but increases the linear extent of a
	 * master node, i.e. it will require more space dedicated to tracking
	 * orphans at various levels of the index tree.
	 */
	private var fanout: Int = 0

	/** The version of the [indexed file][IndexedFile]. */
	private var version: Int = 0

	/** The current [master node][MasterNode]. */
	private var master: MasterNode? = null

	/**
	 * A master node comprises, in sequence, the following:
	 *
	 *  * A first page containing:
	 *    * 4-byte CRC of rest of data (both pages)
	 *    * 4-byte serial counter (should be other master node's counter ± 1
	 *    * 8-byte fileLimit
	 *    * 4-byte [positionInCompressionBlock] (indicates number of valid
	 *      uncompressed bytes below)
	 *    * 12-byte metadata pointer (8-byte file position + 4-byte position in
	 *      uncompressed data)
	 *    * 4-byte node count N, followed by N entries. Each entry represents a
	 *      record or index node that is not yet reachable from a parent node.
	 *      This information is sufficient to rebuild the
	 *      [MasterNode.orphansByLevel] structure.
	 *    * 1-byte level indicator (1=record, 2=bottom index node, etc).
	 *    * 12-byte pointer (8-byte file position + 4-byte position in
	 *      uncompressed data)
	 *  * A second page containing:
	 *    * The last (partial) committed page of data.  It should be rewritten
	 *      to the file at the indicated position during recovery. Subsequent
	 *      writes will continue at fileLimit, which is somewhere inside this
	 *      page.
	 *    * A compression block of size compressionBlockSize containing
	 *      [positionInCompressionBlock] of valid data.
	 */
	private var masterNodeBuffer: ByteBuffer? = null

	/** The absolute location of the current master node. */
	private var masterPosition: Long = 0

	/** The absolute location of the previous master node. */
	private var previousMasterPosition: Long = 0

	/** A [cache][LRUCache] of uncompressed records. */
	private val blockCache = LRUCache<Long, ByteArray>(
		DEFAULT_SOFT_CACHE_SIZE,
		DEFAULT_STRONG_CACHE_SIZE
	) { argument ->
		try
		{
			val block = fetchSizedFromFile(argument!!)
			val inflater = Inflater()
			inflater.setInput(block)
			val buffers = ArrayList<ByteArray>(10)
			var size = 0
			var bufferPos = -1
			while (!inflater.needsInput())
			{
				val buffer = ByteArray(compressionBlockSize * 3 shr 1)
				bufferPos = inflater.inflate(buffer)
				size += bufferPos
				buffers.add(buffer)
			}
			val inflated = ByteBuffer.wrap(ByteArray(size))
			for (i in 0 until buffers.size - 1)
			{
				inflated.put(buffers[i])
			}
			inflated.put(buffers[buffers.size - 1], 0, bufferPos)
			assert(inflated.position() == inflated.capacity())
			inflated.array()
		}
		catch (e: Exception)
		{
			throw RuntimeException(e)
		}
	}

	/** The client-provided metadata, as a byte array. */
	private var metaData: ByteArray? = null

	/**
	 * `ByteArrayOutputStream` provides direct (unsafe) access to the
	 * backing byte array (without requiring it to be copied).
	 *
	 * @constructor
	 *
	 * Construct a new `ByteArrayOutputStream`.
	 *
	 * @param size
	 *   The initial size of the backing byte array.
	 */
	internal class ByteArrayOutputStream(size: Int)
		: java.io.ByteArrayOutputStream(size)
	{
		/** The backing byte array. Do not copy it. */
		val unsafeBytes: ByteArray
			get() = buf
	}

	/**
	 * `MasterNode` is a simple abstraction for a [indexed file][IndexedFile]
	 * master node.
	 *
	 * @property serialNumber
	 *   The serial number of the current master node. Viewed alternatively, the
	 *   *next* serial number that should be committed to the [indexed
	 *   file][IndexedFile].
	 * @property fileLimit
	 *   The virtual end of file.
	 * @constructor
	 *
	 * Construct a new master node.
	 *
	 * @param serialNumber
	 *   The serial number.
	 * @param fileLimit
	 *   The virtual end of file.
	 */
	internal inner class MasterNode constructor(
		var serialNumber: Int,
		var fileLimit: Long)
	{
		/** The raw bytes of *uncompressed* data. */
		val rawBytes = ByteArrayOutputStream(compressionBlockSize * 3 shr 1)

		/** The *uncompressed* data. */
		val uncompressedData: DataOutputStream

		/** The [coordinates][RecordCoordinates] of the metadata. */
		var metaDataLocation: RecordCoordinates

		/**
		 * The list of orphans, with lists of orphans locations of orphan level
		 * n, stored at index n.
		 */
		var orphansByLevel: MutableList<MutableList<RecordCoordinates>>

		/**
		 * The last (partial) committed page of data. For transactional safety,
		 * we cannot simply append a partial page of data to the end of the
		 * backing store. This partial page will be transactionally written to
		 * the current master node during a commit.
		 */
		var lastPartialBuffer: ByteArray

		init
		{
			this.uncompressedData = DataOutputStream(rawBytes)
			this.lastPartialBuffer = ByteArray(pageSize)
			this.orphansByLevel = ArrayList()
			this.metaDataLocation = RecordCoordinates.origin
		}

		/**
		 * Serialize the master node into the specified [ByteBuffer]. The
		 * [ByteBuffer.position] of the [masterNodeBuffer] will be `0` after the
		 * call returns.
		 *
		 * @param buffer
		 *   The output buffer into which the master node should be serialized.
		 */
		fun writeTo(buffer: ByteBuffer)
		{
			assert(rawBytes.size() < compressionBlockSize)

			// Compute the orphan count.
			var orphanCount = 0
			for (orphans in orphansByLevel)
			{
				orphanCount += orphans.size
			}

			// The first four bytes are the CRC32 of the master node (sans the
			// CRC32 field). Write in a zero for now; we will overwrite this
			// with an actual checksum after we have written the remainder of
			// the current master block.
			buffer.rewind()
			buffer.putInt(0)
			buffer.putInt(serialNumber)
			buffer.putLong(fileLimit)
			buffer.putInt(rawBytes.size())
			buffer.putLong(metaDataLocation.filePosition)
			buffer.putInt(metaDataLocation.blockPosition)
			buffer.putInt(orphanCount)
			for (level in orphansByLevel.indices)
			{
				for (orphanLocation in orphansByLevel[level])
				{
					buffer.put((level + 1).toByte())
					buffer.putLong(orphanLocation.filePosition)
					buffer.putInt(orphanLocation.blockPosition)
				}
			}
			assert(buffer.position() <= pageSize) {
				"Too much index orphan information for a page."
			}
			buffer.put(ByteArray(pageSize - buffer.position()))
			assert(buffer.position() == pageSize)
			buffer.put(lastPartialBuffer)
			assert(buffer.position() == pageSize shl 1)
			buffer.put(rawBytes.unsafeBytes, 0, rawBytes.size())
			buffer.put(ByteArray(compressionBlockSize - rawBytes.size()))
			assert(buffer.position() == buffer.capacity())
			assert(buffer.position() == masterNodeSize)
			// Now write the CRC32 into the first four bytes of the node.
			val encoder = CRC32()
			encoder.update(buffer.array(), 4, buffer.position() - 4)
			buffer.rewind()
			buffer.putInt(encoder.value.toInt())
			buffer.rewind()
		}
	}

	/** The [master node][masterNodeBuffer] size. */
	internal val masterNodeSize: Int
		get() = (pageSize shl 1) + compressionBlockSize

	/**
	 * `RecordCoordinates` are the two-dimension coordinates of an uncompressed
	 * record within a [indexed file][IndexedFile]. The first axis is the
	 * absolute position within the indexed file of the compressed block
	 * containing the record. The second axis is the position of the record
	 * within the *uncompressed* block.
	 *
	 * @property filePosition
	 *   The absolute position within the [indexed][IndexedFile] of the
	 *   compressed block containing the record.
	 * @property blockPosition
	 *   The position within the *uncompressed* block of the record.
	 *
	 * @constructor
	 *
	 * Construct a new `RecordCoordinates`.
	 *
	 * @param filePosition
	 *   The absolute position within the [indexed file][IndexedFile] of the
	 *   compressed block containing the record.
	 * @param blockPosition
	 *   The position within the *uncompressed* block of the record.
	 */
	internal class RecordCoordinates constructor(
		val filePosition: Long,
		val blockPosition: Int)
	{
		override fun equals(other: Any?): Boolean
		{
			if (other !is RecordCoordinates)
			{
				return false
			}
			val strongOther = other as RecordCoordinates?
			return filePosition == strongOther!!.filePosition
				&& blockPosition == strongOther.blockPosition
		}

		override fun hashCode(): Int =
			((filePosition xor 0x58FC0112)
				* (blockPosition xor -0x3533880d) + 0x62B02A14).toInt()

		companion object
		{
			/** The origin.  */
			val origin = RecordCoordinates(0L, 0)
		}
	}

	/**
	 * Answer the NUL-terminated header bytes that uniquely identify a
	 * particular usage of the core `IndexedFile indexed file` technology.
	 *
	 * @return
	 *   An array of bytes that uniquely identifies the purpose of the indexed
	 *   file.
	 */
	protected abstract val headerBytes: ByteArray

	/**
	 * Acquire an exclusive [file lock][FileLock] on the last byte of a logical
	 * 64-bit file range. This prevents other conforming [indexed
	 * file][IndexedFile] drivers (operating in other OS processes) from
	 * deciding that they can also write to the file.
	 *
	 * @param wait
	 *   `true` if the lock attempt should block until successful, `false` if
	 *   the lock attempt should fail immediately if unsuccessful.
	 * @return
	 *   The [file lock][FileLock], or `null` if the argument was `true` but the
	 *   file is already locked by another indexed file driver in another
	 *   process.
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	private fun acquireLockForWriting(wait: Boolean = true): FileLock =
		if (wait)
			channel!!.lock(0x7FFFFFFFFFFFFFFEL, 1, false)
		else
			channel!!.tryLock(0x7FFFFFFFFFFFFFFEL, 1, false)

	/**
	 * Insert the given orphan location at the given height of the index tree.
	 *
	 * @param orphanLocation
	 *   The location of the orphan to be added.
	 * @param level
	 *   The level at which to add this orphan.
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	private fun addOrphan(orphanLocation: RecordCoordinates, level: Int)
	{
		val m = master!!
		if (level >= m.orphansByLevel.size)
		{
			m.orphansByLevel.add(ArrayList(fanout))
		}
		val orphans = m.orphansByLevel[level]
		orphans.add(orphanLocation)
		if (orphans.size == fanout)
		{
			val newOrphanLocation = RecordCoordinates(
				m.fileLimit,
				m.rawBytes.size())
			for (orphan in orphans)
			{
				m.uncompressedData.writeLong(orphan.filePosition)
				m.uncompressedData.writeInt(orphan.blockPosition)
			}
			orphans.clear()
			compressAndFlushIfFull()
			addOrphan(newOrphanLocation, level + 1)
		}
	}

	/**
	 * Appends the given bytes to the virtual end of the `IndexedFile`.
	 *
	 * @param bytes
	 *   The byte array to be appended.
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	private fun appendRawBytes(bytes: ByteArray)
	{
		val m = master!!
		var bufferPos = m.fileLimit.toInt() % pageSize
		var start = 0
		val end = bytes.size
		while (start < end)
		{
			val limit = min(
				bufferPos + end - start,
				m.lastPartialBuffer.size)
			val count = limit - bufferPos
			assert(count > 0) {
				"Previous write should have flushed the buffer."
			}
			System.arraycopy(
				bytes,
				start,
				m.lastPartialBuffer,
				bufferPos,
				count)
			start += count
			bufferPos += count
			if (bufferPos >= pageSize)
			{
				assert(bufferPos == pageSize)
				val c = channel!!
				c.position(m.fileLimit / pageSize * pageSize)
				c.write(ByteBuffer.wrap(m.lastPartialBuffer))
				bufferPos = 0
			}
			m.fileLimit += count.toLong()
		}
	}

	/**
	 * Append the 32-bit size and the contents of the specified byte array to
	 * the virtual end of the indexed file.
	 *
	 * @param compressedBytes
	 *   A compressed byte array.
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	private fun appendSizedBytes(compressedBytes: ByteArray)
	{
		val sizePrefix = ByteArray(4)
		sizePrefix[0] = (compressedBytes.size shr 24).toByte()
		sizePrefix[1] = (compressedBytes.size shr 16 and 0xff).toByte()
		sizePrefix[2] = (compressedBytes.size shr 8 and 0xff).toByte()
		sizePrefix[3] = (compressedBytes.size and 0xff).toByte()
		appendRawBytes(sizePrefix)
		appendRawBytes(compressedBytes)
	}

	/**
	 * Answers the block at the given file position.
	 *
	 * @param filePosition
	 *   The absolute position of the file being requested.
	 * @return
	 *   The block, as a byte array.
	 */
	private fun blockAtFilePosition(filePosition: Long): ByteArray
	{
		val m = master!!
		return if (filePosition == m.fileLimit)
		{
			m.rawBytes.unsafeBytes
		}
		else blockCache.getNotNull(filePosition)
	}

	/**
	 * If the [compression buffer][MasterNode.uncompressedData] has filled up,
	 * then actually compress its contents and append them to the virtual end of
	 * the indexed file.
	 *
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	private fun compressAndFlushIfFull()
	{
		val m = master!!
		if (m.rawBytes.size() >= compressionBlockSize)
		{
			val compressedStream = ByteArrayOutputStream(compressionBlockSize)
			DeflaterOutputStream(
					compressedStream,
					Deflater(Deflater.BEST_COMPRESSION)).use { stream ->
				stream.write(
					m.rawBytes.unsafeBytes,
					0,
					m.rawBytes.size())
			}
			while (
				m.fileLimit + 4 + compressedStream.size().toLong()
					>= file!!.length())
			{
				channel!!.position(0)
				val delta = (min(
					m.fileLimit,
					(5 shl 20).toLong()) + pageSize - 1) / pageSize * pageSize
				file!!.setLength(file!!.length() + delta)
			}
			appendSizedBytes(compressedStream.toByteArray())
			m.rawBytes.reset()
		}
	}

	/**
	 * Create the physical indexed file. The initial contents are created in
	 * memory and then written to a temporary file. Once the header and master
	 * blocks have been written, the argument action is performed. Finally the
	 * temporary file is renamed to the canonical filename. When the call
	 * returns, [file] and [channel] are live and a write lock is held on the
	 * physical indexed file.
	 *
	 * @param action
	 *   An action to perform after the header and master blocks have been
	 *   written, but before the temporary file is renamed.
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	private fun createFile(action: (() -> Unit)?)
	{
		// Write the header.
		val headerBytes = headerBytes
		previousMasterPosition =
			(headerBytes.size.toLong() + 16L + pageSize.toLong() - 1) /
				pageSize * pageSize
		masterPosition = previousMasterPosition + masterNodeSize
		val fileLimit = masterPosition + masterNodeSize
		val bufferSize = previousMasterPosition + masterNodeSize shl 1
		assert(bufferSize == bufferSize.toInt().toLong())
		val buffer = ByteBuffer.allocateDirect(bufferSize.toInt())
		buffer.order(ByteOrder.BIG_ENDIAN)
		buffer.put(headerBytes)
		buffer.putInt(currentVersion)
		buffer.putInt(pageSize)
		buffer.putInt(compressionBlockSize)
		buffer.putInt(fanout)
		buffer.put(
			ByteArray(previousMasterPosition.toInt() - buffer.position()))
		assert(buffer.position().toLong() == previousMasterPosition)

		// Write the master blocks.
		var m = MasterNode(1, fileLimit)
		val b = stripNull(masterNodeBuffer)
		m.writeTo(b)
		buffer.put(b)
		assert(buffer.position().toLong() == masterPosition)
		m = MasterNode(2, fileLimit)
		m.writeTo(b)
		buffer.put(b)
		assert(buffer.position().toLong() == fileLimit)
		buffer.rewind()
		master = m

		try
		{
			// Transfer the buffer to a temporary file. Perform the nullary
			// action. Close the channel prior to renaming the temporary file.
			val tempFilename = File.createTempFile(
				"new indexed file", null, fileReference!!.parentFile)
			tempFilename.deleteOnExit()
			file = RandomAccessFile(tempFilename, "rw")
			assert(file!!.length() == 0L) { "The file is not empty." }
			file!!.setLength(pageSize * 100L)
			channel = file!!.channel
			longTermLock = acquireLockForWriting()
			channel!!.write(buffer)
			channel!!.force(true)
			action?.invoke()
			longTermLock!!.close()
			channel!!.close()

			// Rename the temporary file to the canonical target name. Reopen
			// the file and reacquire the write lock.
			val fileRef = fileReference!!
			if (!tempFilename.renameTo(fileRef))
			{
				throw IOException("rename failed")
			}
			file = RandomAccessFile(fileRef, "rw")
			channel = file!!.channel
			acquireLockForWriting()
		}
		catch (e: IOException)
		{
			close()
			throw e
		}

	}

	/**
	 * The current version of the indexed file technology used by the class.
	 * This is the version that will be used for new persistent indexed files.
	 */
	protected val currentVersion: Int
		get()
		{
			val myClass = javaClass
			val currentVersion =
				myClass.getAnnotation(IndexedFileVersion::class.java) ?: error(
					"${myClass.name} does not declare current version; add " +
						"@IndexedFileVersion")
			return currentVersion.value
		}

	/**
	 * Construct and answer a [master node][MasterNode] from the data at the
	 * given file position. If the node has a bad CRC, then answer `null`.
	 *
	 * @param nodePosition
	 *   The position within the [file] of the desired master node.
	 * @return
	 *   A master node, or `null` if the data was corrupt.
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	private fun decodeMasterNode(nodePosition: Long): MasterNode?
	{
		// Verify the CRC32.
		channel!!.position(nodePosition)
		val b = stripNull(masterNodeBuffer)
		b.rewind()
		channel!!.read(b)
		val encoder = CRC32()
		encoder.update(b.array(), 4, b.position() - 4)
		b.rewind()
		if (b.int != encoder.value.toInt())
		{
			return null
		}

		// Construct the master node state tuple.
		val node = MasterNode(
			b.int,
			b.long)
		val compressionBlockPosition = b.int
		node.metaDataLocation = RecordCoordinates(
			b.long,
			b.int)
		val orphans = ArrayList<MutableList<RecordCoordinates>>()
		for (left in b.int downTo 1)
		{
			val level = b.get() - 1
			val orphan = RecordCoordinates(
				b.long,
				b.int)
			while (level >= orphans.size)
			{
				orphans.add(ArrayList(fanout))
			}
			orphans[level].add(orphan)
		}
		assert(b.position() <= pageSize) {
			"Too much index orphan information for a page."
		}
		node.orphansByLevel = orphans
		b.position(pageSize)
		val lastPageContents = ByteArray(pageSize)
		b.get(lastPageContents)
		assert(b.position() == pageSize shl 1)
		node.lastPartialBuffer = lastPageContents
		val uncompressed = ByteArray(compressionBlockSize)
		b.get(uncompressed)
		assert(b.position() == b.capacity())
		assert(b.position() == masterNodeSize)
		node.rawBytes.reset()
		node.uncompressedData.write(uncompressed, 0, compressionBlockPosition)
		return node
	}

	/**
	 * Read size-prefixed data from the specified absolute file position.
	 *
	 * @param startFilePosition
	 *   An absolute file position.
	 * @return
	 *   A byte array.
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	internal fun fetchSizedFromFile(startFilePosition: Long): ByteArray
	{
		val sizePrefix = ByteArray(4)
		fillBuffer(sizePrefix, startFilePosition)
		val size = (sizePrefix[0].toInt() and 0xFF shl 24
			or (sizePrefix[1].toInt() and 0xFF shl 16)
			or (sizePrefix[2].toInt() and 0xFF shl 8)
			or (sizePrefix[3].toInt() and 0xFF))
		val content = ByteArray(size)
		fillBuffer(content, startFilePosition + 4)
		return content
	}

	/**
	 * Fills the specified buffer with the bytes at the position given.
	 *
	 * @param bytes
	 *   The byte array to be filled.
	 * @param startFilePosition
	 *   The position in the file at which to begin reading bytes.
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	private fun fillBuffer(bytes: ByteArray, startFilePosition: Long)
	{
		val m = master!!
		val writtenLimit = m.fileLimit / pageSize * pageSize
		val endFilePosition = startFilePosition + bytes.size
		assert(endFilePosition <= m.fileLimit)
		if (startFilePosition < writtenLimit)
		{
			val c = channel!!
			c.position(startFilePosition)
			if (endFilePosition <= writtenLimit)
			{
				// Entirely within the file.
				val bytesRead = c.read(ByteBuffer.wrap(bytes))
				assert(bytesRead == bytes.size)
			}
			else
			{
				// Split between file and unwritten buffer.
				val split = (writtenLimit - startFilePosition).toInt()
				val bytesRead = c.read(ByteBuffer.wrap(bytes, 0, split))
				assert(bytesRead == split)
				System.arraycopy(
					m.lastPartialBuffer,
					0,
					bytes,
					split,
					bytes.size - split)
			}
		}
		else
		{
			// Entirely within the unwritten buffer.
			val startInLastPartialBuffer = startFilePosition - writtenLimit
			assert(startInLastPartialBuffer == startInLastPartialBuffer.toInt().toLong())
			assert(startInLastPartialBuffer + bytes.size <= m.lastPartialBuffer.size)
			System.arraycopy(
				m.lastPartialBuffer,
				startInLastPartialBuffer.toInt(),
				bytes,
				0,
				bytes.size)
		}
	}

	/**
	 * Read the header page from the underlying [file].
	 *
	 * @param versionCheck
	 *   How to check if the file's version is acceptable, relative to the
	 *   code's current version.
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 * @throws IndexedFileException
	 *   If something else goes wrong.
	 */
	@Throws(IOException::class, IndexedFileException::class)
	private fun readHeaderData(versionCheck: (Int, Int) -> Boolean)
	{
		var finished = false
		try
		{
			assert(file!!.length() > 0)
			val expectedHeader = headerBytes
			val bufferSize = expectedHeader.size + 16
			val buffer = ByteBuffer.allocateDirect(bufferSize)
			channel!!.read(buffer)
			val header = ByteArray(expectedHeader.size)
			buffer.rewind()
			buffer.get(header)
			if (!header.contentEquals(expectedHeader))
			{
				throw IndexedFileException(
					"indexed file header is not valid.")
			}
			version = buffer.int
			val okVersion = versionCheck(version, currentVersion)
			if (!okVersion)
			{
				throw IndexedFileException(
					"Unsupported indexed file version: $version")
			}
			pageSize = buffer.int
			compressionBlockSize = buffer.int
			fanout = buffer.int
			previousMasterPosition =
				(bufferSize.toLong() + pageSize - 1) / pageSize * pageSize
			masterPosition = previousMasterPosition + masterNodeSize
			finished = true
		}
		finally
		{
			if (!finished)
			{
				close()
			}
		}
	}

	/**
	 * Answers the record located in the given node of the given level, at
	 * the specified index.
	 *
	 * @param startingIndex
	 *   The index within the tree.
	 * @param startingNodePosition
	 *   The search tree.
	 * @param startingLevel
	 *   The height of the search tree (0 for leaves).
	 * @return
	 *   A record.
	 */
	private fun recordAtZeroBasedIndex(
		startingIndex: Long,
		startingNodePosition: RecordCoordinates,
		startingLevel: Int): ByteArray
	{
		var pow = fanout.toDouble().pow(startingLevel.toDouble()).toLong()
		assert(startingIndex < pow) {
			"Arithmetic error traversing perfect tree"
		}
		var node = RecordCoordinates(
			startingNodePosition.filePosition,
			startingNodePosition.blockPosition)
		var buffer = ByteBuffer.wrap(
			blockAtFilePosition(node.filePosition))
		buffer.position(node.blockPosition)
		var zIndex = startingIndex
		var level = startingLevel
		while (level != 0)
		{
			pow /= fanout.toLong()
			val zSubscript = (zIndex / pow).toInt()
			zIndex %= pow
			buffer.position(12 * zSubscript + node.blockPosition)
			node = RecordCoordinates(buffer.long, buffer.int)
			level--
			buffer = ByteBuffer.wrap(blockAtFilePosition(node.filePosition))
			buffer.position(node.blockPosition)
		}
		val result = ByteArray(buffer.int)
		buffer.get(result)
		return result
	}

	/**
	 * Add a portion of the given record to the indexed file. *Do not
	 * [commit][commit] the data.*
	 *
	 * @param record
	 *   The record which contains data that should be added to the indexed
	 *   file.
	 * @param start
	 *   The start position within the record of the source data.
	 * @param length
	 *   The size of the source data, in bytes.
	 * @throws IndexOutOfBoundsException
	 *   If the specified index is not equal to the size of the indexed file.
	 * @throws IndexedFileException
	 *   If something else goes wrong.
	 */
	@Throws(IndexOutOfBoundsException::class, IndexedFileException::class)
	@JvmOverloads
	fun add(record: ByteArray, start: Int = 0, length: Int = record.size)
	{
		lock.write {
			try
			{
				val m = master!!
				val coordinates = RecordCoordinates(
					m.fileLimit, m.rawBytes.size())
				m.uncompressedData.writeInt(length)
				m.uncompressedData.write(record, start, length)
				compressAndFlushIfFull()
				addOrphan(coordinates, 0)
			}
			catch (e: IOException)
			{
				throw IndexedFileException(e)
			}
		}
	}

	/**
	 * Close the indexed file. No further API calls are permitted.
	 */
	fun close()
	{
		log(Level.INFO, "Close: %s", fileReference!!)
		lock.write {
			if (longTermLock !== null)
			{
				try
				{
					longTermLock!!.release()
				}
				catch (e: IOException)
				{
					// Ignore.
				}
				finally
				{
					longTermLock = null
				}
			}

			if (channel !== null)
			{
				try
				{
					channel!!.close()
				}
				catch (e: IOException)
				{
					// Ignore.
				}
				finally
				{
					channel = null
				}
			}

			if (file !== null)
			{
				try
				{
					file!!.close()
				}
				catch (e: IOException)
				{
					// Ignore.
				}
				finally
				{
					file = null
				}
			}

			try
			{
				blockCache.clear()
			}
			catch (e: InterruptedException)
			{
				// Do nothing.
			}
		}
	}

	/**
	 * Commit the indexed file. In particular, write out the current master node
	 * to the underlying [file] and force a synchronization of the file's data
	 * and metadata buffers to disk.
	 *
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	fun commit()
	{
		lock.write {
			val m = master!!
			val c = channel!!
			val b = masterNodeBuffer!!
			c.force(true)
			val exchange = masterPosition
			masterPosition = previousMasterPosition
			previousMasterPosition = exchange
			m.serialNumber++
			m.writeTo(b)
			val shortTermLock = c.lock(
				pageSize.toLong(), masterNodeSize.toLong() shl 1, false)
			try
			{
				c.position(masterPosition)
				c.write(b)
				c.force(true)
			}
			finally
			{
				shortTermLock.release()
			}
		}
	}

	/**
	 * Answer the requested record.
	 *
	 * @param index
	 *   The index of the requested record.
	 * @return
	 *   The record bytes.
	 * @throws IndexOutOfBoundsException
	 *   If the index is out of bounds.
	 * @throws IndexedFileException
	 *   If something else goes wrong.
	 */
	@Throws(IndexOutOfBoundsException::class, IndexedFileException::class)
	operator fun get(index: Long): ByteArray
	{
		lock.read {
			if (index < 0)
			{
				throw IndexOutOfBoundsException()
			}
			val m = master!!
			var residue = index
			var power = fanout.toDouble().pow(
				(m.orphansByLevel.size - 1).toDouble()).toLong()
			for (level in m.orphansByLevel.indices.reversed())
			{
				val orphans = m.orphansByLevel[level]
				val subscript = residue / power
				if (subscript < orphans.size)
				{
					return recordAtZeroBasedIndex(
						residue % power,
						orphans[subscript.toInt()],
						level)
				}
				residue -= orphans.size * power
				power /= fanout.toLong()
			}
			throw IndexOutOfBoundsException()
		}
	}

	/**
	 * Answer the size of the indexed file, in records.
	 *
	 * @return
	 *   The number of records contained in the indexed file.
	 */
	val size: Long
		get() = lock.read {
			val m = master!!
			var power: Long = 1
			var sum: Long = 0
			for (i in m.orphansByLevel.indices)
			{
				sum += m.orphansByLevel[i].size * power
				power *= fanout.toLong()
			}
			sum
		}

	/**
	 * Answer the client-provided metadata, as a byte array.
	 *
	 * @return
	 *   The client-provided metadata, as a byte array, or `null` if no metadata
	 *   has ever been specified.
	 */
	fun metaData(): ByteArray? =
		// Note that it is okay for multiple readers to destructively update the
		// metaData field: they will all write the same answer. This is why we
		// only grab a read lock.
		lock.read {
			val m = master!!
			RecordCoordinates.origin != m.metaDataLocation || return null
			if (metaData === null)
			{
				val block = blockAtFilePosition(m.metaDataLocation.filePosition)
				val buffer = ByteBuffer.wrap(block)
				buffer.position(m.metaDataLocation.blockPosition)
				val size = buffer.int
				metaData = ByteArray(size)
				buffer.get(metaData!!)
			}
			metaData
		}

	/**
	 * Set and write the new [metadata][metaData]. *Do not [commit][commit] the
	 * new metadata.*
	 *
	 * @param newMetaData
	 *   The new client-provided metadata, as a byte array.
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	fun metaData(newMetaData: ByteArray) =
		lock.write {
			val m = master!!
			metaData = newMetaData.clone()
			m.metaDataLocation = RecordCoordinates(
				m.fileLimit, m.rawBytes.size())
			m.uncompressedData.writeInt(newMetaData.size)
			m.uncompressedData.write(newMetaData)
			compressAndFlushIfFull()
		}

	/**
	 * Update the state of the `IndexedFile` driver from the physical contents
	 * of the indexed file.
	 *
	 * @throws IOException
	 *   If an [I/O exception][IOException] occurs.
	 * @throws IndexedFileException
	 *   If something else goes wrong.
	 */
	@Throws(IOException::class, IndexedFileException::class)
	fun refresh() =
		lock.write {
			val fileLock = channel!!.lock(
				pageSize.toLong(), masterNodeSize.toLong() shl 1, false)
			try
			{
				// Determine the newest valid master node.
				val previous = decodeMasterNode(previousMasterPosition)
				var current = decodeMasterNode(masterPosition)
				if (previous === null && current === null)
				{
					throw IndexedFileException(
						"Invalid indexed file -- both master nodes are " +
							"corrupt.")
				}
				var delta: Int? = null
				if (previous !== null && current !== null)
				{
					delta = current.serialNumber - previous.serialNumber
					if (abs(delta) != 1)
					{
						throw IndexedFileException(
							"Invalid indexed file -- master nodes are valid " +
								"but have non-consecutive serial numbers.")
					}
				}
				// Swap the previous and current nodes if necessary.
				if (previous !== null && Integer.valueOf(1) != delta)
				{
					current = previous
					// previous is unused after this point.
					val tempPos = previousMasterPosition
					previousMasterPosition = masterPosition
					masterPosition = tempPos
				}
				if (master !== null &&
					master!!.serialNumber != current!!.serialNumber)
				{
					// Clear the cached metadata if it has changed.
					if (master!!.metaDataLocation != current.metaDataLocation)
					{
						metaData = null
					}
				}
				master = current
			}
			catch (e: IOException)
			{
				close()
				throw e
			}
			catch (e: Throwable)
			{
				close()
				throw IndexedFileException(e)
			}
			finally
			{
				fileLock.release()
			}
		}

	override fun toString(): String =
		"${javaClass.simpleName}[$size] (for $fileReference)"

	companion object
	{
		/** The preferred page size of a [indexed file][IndexedFile].  */
		private const val DEFAULT_PAGE_SIZE = 4096

		/**
		 * The preferred compression threshold of a [indexed][IndexedFile].
		 */
		private const val DEFAULT_COMPRESSION_THRESHOLD = 32768

		/**
		 * The preferred index node fan-out. The value is small enough that the
		 * orphans all fit on a page (with a trillion records), while large
		 * enough to keep the number of index levels from getting too high. A
		 * fan-out of 2 would have 40 levels (for a trillion records), which is
		 * probably too slow for random access. A fan-out of 100 would be 6
		 * levels high, but at 13 bytes per orphan pointer it would take
		 * 6*100*13=7800 bytes, which is more than the usual (4KB) page size. By
		 * using a fan-out of 32 there are 8 levels (for a trillion), or 8*32*13
		 * = 3328 bytes for tracking orphans.
		 */
		private const val DEFAULT_FANOUT = 32

		/**
		 * The capacity of the [cache][LRUCache] of uncompressed records. A
		 * number of records equal to the delta between this value and that of
		 * [DEFAULT_STRONG_CACHE_SIZE] will be discarded from the cache by the
		 * garbage collector when a low-water mark is passed.
		 */
		private const val DEFAULT_SOFT_CACHE_SIZE = 200

		/**
		 * The memory-insensitive capacity of the [cache][LRUCache] of
		 * uncompressed records.
		 */
		private const val DEFAULT_STRONG_CACHE_SIZE = 100

		/**
		 * Create a new `IndexedFile`. The resultant object is backed by a
		 * physical (i.e., disk-based) indexed file.
		 *
		 * @param F
		 *   The specialization of `IndexedFile` to create.
		 * @param subclass
		 *   The subclass of `IndexedFile` that should be created. This
		 *   indicates the purpose of the indexed file.
		 * @param fileReference
		 *   The location of the backing store.
		 * @param pageSize
		 *   The page size. A good page size is a multiple of both the disk and
		 *   memory page sizes.
		 * @param compressionThreshold
		 *   The compression threshold. A good compression threshold is a
		 *   multiple of the page size.
		 * @param initialMetaData
		 *   Client-provided [metadata][metaData], or `null` for none.
		 * @return
		 *   The new indexed file.
		 * @throws IOException
		 *   If an [I/O exception][IOException] occurs.
		 */
		@Throws(IOException::class)
		fun <F : IndexedFile> newFile(
			subclass: Class<F>,
			fileReference: File,
			pageSize: Int,
			compressionThreshold: Int,
			initialMetaData: ByteArray?): F
		{
			log(Level.INFO, "New: %s", fileReference)
			assert(compressionThreshold % pageSize == 0)
			val indexedFile: IndexedFile
			try
			{
				indexedFile = subclass.newInstance()
			}
			catch (e: InstantiationException)
			{
				assert(false) { "This should never happen!" }
				throw RuntimeException(e)
			}
			catch (e: IllegalAccessException)
			{
				assert(false) { "This should never happen!" }
				throw RuntimeException(e)
			}

			indexedFile.fileReference = fileReference
			indexedFile.version = indexedFile.currentVersion
			indexedFile.pageSize = pageSize
			indexedFile.compressionBlockSize = compressionThreshold
			indexedFile.fanout = DEFAULT_FANOUT
			indexedFile.masterNodeBuffer = ByteBuffer.allocate(
				indexedFile.masterNodeSize)
			indexedFile.createFile {
				if (initialMetaData !== null)
				{
					try
					{
						indexedFile.metaData(initialMetaData)
						indexedFile.commit()
					}
					catch (e: IOException)
					{
						throw IndexedFileException(e)
					}

				}
			}
			return cast(indexedFile)
		}

		/**
		 * Create a new `IndexedFile`, using reasonable defaults for [page
		 * size][DEFAULT_PAGE_SIZE] and [compression
		 * threshold][DEFAULT_COMPRESSION_THRESHOLD]. The resultant object is
		 * backed by a physical (i.e., disk-based) indexed file.
		 *
		 * @param F
		 *   The specialization of `IndexedFile` to create.
		 * @param subclass
		 *   The subclass of `IndexedFile` that should be created. This
		 *   indicates the purpose of the indexed file.
		 * @param fileReference
		 *   The location of the backing store.
		 * @param initialMetaData
		 *   Client-provided [metadata][metaData], or `null` for none.
		 * @return
		 *   The new indexed file.
		 * @throws IOException
		 *   If an [I/O exception][IOException] occurs.
		 */
		@Throws(IOException::class)
		fun <F : IndexedFile> newFile(
				subclass: Class<F>,
				fileReference: File,
				initialMetaData: ByteArray?): F =
			newFile(
				subclass,
				fileReference,
				DEFAULT_PAGE_SIZE,
				DEFAULT_COMPRESSION_THRESHOLD,
				initialMetaData)

		/**
		 * Open the specified `IndexedFile`.
		 *
		 * Note that there may be any number of readers *and* up to one writer
		 * accessing the file safely simultaneously.  Only the master blocks are
		 * ever updated (all other blocks are written exactly once ever), and
		 * those writes occur inside an exclusive lock of that region.  They're
		 * also forced to disk before releasing the lock, to guarantee
		 * coherence.  The reads of the master blocks happen inside a shared
		 * lock of the same region.  A reader uses [refresh] to detect newly
		 * appended records.
		 *
		 * @param F
		 *   The specialization of `IndexedFile` to open.
		 * @param subclass
		 *   The subclass of `IndexedFile` that should be created. This
		 *   indicates the purpose of the indexed file. The [headerBytes]
		 *   contained within the file must agree with that specified by the
		 *   subclass.
		 * @param fileReference
		 *   The location of the indexed file.
		 * @param forWriting
		 *   `true` if the indexed file should be opened for writing, `false`
		 *   otherwise.
		 * @param versionCheck
		 *   How to check for a compatible version.
		 * @return
		 *   The indexed file.
		 * @throws IOException
		 *   If an [I/O exception][IOException] occurs.
		 */
		@Throws(IOException::class)
		fun <F : IndexedFile> openFile(
			subclass: Class<F>,
			fileReference: File,
			forWriting: Boolean,
			versionCheck: (Int, Int) -> Boolean): F
		{
			log(Level.INFO, "Open: {0}", fileReference)
			val strongIndexedFile: F
			val indexedFile: IndexedFile
			try
			{
				strongIndexedFile = subclass.newInstance()
				indexedFile = strongIndexedFile
			}
			catch (e: InstantiationException)
			{
				assert(false) { "This should never happen!" }
				throw RuntimeException(e)
			}
			catch (e: IllegalAccessException)
			{
				assert(false) { "This should never happen!" }
				throw RuntimeException(e)
			}

			indexedFile.fileReference = fileReference
			if (!fileReference.exists())
			{
				throw IndexedFileException("No such index file")
			}
			indexedFile.file = RandomAccessFile(
				fileReference, if (forWriting) "rw" else "r")
			if (fileReference.length() == 0L)
			{
				throw IndexedFileException("Index file has no header (0 bytes)")
			}
			indexedFile.channel = indexedFile.file!!.channel
			indexedFile.readHeaderData(versionCheck)
			indexedFile.masterNodeBuffer = ByteBuffer.allocate(
				indexedFile.masterNodeSize)
			if (forWriting)
			{
				indexedFile.longTermLock = indexedFile.acquireLockForWriting()
			}
			indexedFile.refresh()
			return strongIndexedFile
		}
	}
}
