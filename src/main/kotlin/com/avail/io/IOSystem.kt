/*
 * IOSystem.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of the contributors
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

package com.avail.io

import com.avail.AvailRuntime
import com.avail.AvailThread
import com.avail.descriptor.A_String
import com.avail.descriptor.A_Tuple
import com.avail.descriptor.AtomDescriptor
import com.avail.descriptor.AtomDescriptor.SpecialAtom
import com.avail.descriptor.PojoDescriptor
import com.avail.utility.LRUCache
import com.avail.utility.MutableOrNull
import com.avail.utility.SimpleThreadFactory
import java.io.IOException
import java.nio.channels.AsynchronousChannelGroup
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.file.*
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.util.WeakHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy
import java.util.concurrent.TimeUnit

import com.avail.AvailRuntimeConfiguration.availableProcessors
import com.avail.descriptor.AvailObject.multiplier
import com.avail.utility.Nulls.stripNull
import java.nio.file.attribute.PosixFilePermission.*
import java.util.Collections.synchronizedMap

/**
 * This aggregates socket and file I/O information and behavior specific to an
 * [AvailRuntime].
 *
 * @property runtime
 *   The [AvailRuntime] that this is part of.
 *
 * @constructor
 * Create a new `IOSystem` for the given [AvailRuntime].
 *
 * @param runtime
 *   The [AvailRuntime].
 */
class IOSystem constructor(val runtime: AvailRuntime)
{
	/**
	 * The [thread pool executor][ThreadPoolExecutor] for asynchronous file
	 * operations performed on behalf of this [Avail runtime][AvailRuntime].
	 */
	private val fileExecutor = ThreadPoolExecutor(
		availableProcessors,
		availableProcessors shl 2,
		10L,
		TimeUnit.SECONDS,
		LinkedBlockingQueue(),
		SimpleThreadFactory("AvailFile"),
		CallerRunsPolicy())

	/**
	 * The [thread pool executor][ThreadPoolExecutor] for asynchronous socket
	 * operations performed on behalf of this [Avail runtime][AvailRuntime].
	 */
	private val socketExecutor = ThreadPoolExecutor(
		availableProcessors,
		availableProcessors shl 2,
		10L,
		TimeUnit.SECONDS,
		LinkedBlockingQueue(),
		SimpleThreadFactory("AvailSocket"),
		CallerRunsPolicy())

	/**
	 * The [asynchronous channel group][AsynchronousChannelGroup] that manages
	 * [asynchronous socket][AsynchronousSocketChannel] on behalf of this
	 * [Avail runtime][AvailRuntime].
	 */
	private val socketGroup: AsynchronousChannelGroup

	/**
	 * Maintain an [LRUCache] of file buffers.  This allows us to avoid a trip
	 * to the operating system to re-fetch recently accessed buffers of data,
	 * which is especially powerful since the buffers are shared (immutable and
	 * thread-safe).
	 *
	 *
	 * A miss for this cache doesn't actually read the necessary data from the
	 * operating system.  Instead, it simply creates a [MutableOrNull]
	 * initially.  The client is responsible for reading the actual data that
	 * should be stored into the `MutableOrNull`.
	 */
	private val cachedBuffers = LRUCache<BufferKey, MutableOrNull<A_Tuple>>(
		10000,
		10,
		{ MutableOrNull() },
		{ _, value ->
			// Just clear the mutable's value slot, freeing the actual
			// buffer.
			value!!.value = null
		})

	/**
	 * Schedule the specified [task][Runnable] for eventual execution
	 * by the [thread pool executor][ThreadPoolExecutor] for
	 * asynchronous file operations. The implementation is free to run the task
	 * immediately or delay its execution arbitrarily. The task will not execute
	 * on an [Avail thread][AvailThread].
	 *
	 * @param task
	 *   A task.
	 */
	fun executeFileTask(task: Runnable)
	{
		fileExecutor.execute(task)
	}

	init
	{
		try
		{
			socketGroup =
				AsynchronousChannelGroup.withThreadPool(socketExecutor)
		}
		catch (e: IOException)
		{
			throw RuntimeException(e)
		}

	}

	/**
	 * Schedule the specified [task][Runnable] for eventual execution by the
	 * [thread pool executor][ThreadPoolExecutor] for asynchronous socket
	 * operations. The implementation is free to run the task immediately or
	 * delay its execution arbitrarily. The task will not execute on an
	 * [Avail thread][AvailThread].
	 *
	 * @param task A task.
	 */
	internal fun executeSocketTask(task: Runnable)
	{
		socketExecutor.execute(task)
	}

	/**
	 * Open an [asynchronous file channel][AsynchronousFileChannel] for the
	 * specified [path][Path].
	 *
	 * @param path
	 *   A path.
	 * @param options
	 *   The [open options][OpenOption].
	 * @param attributes
	 *   The [file attributes][FileAttribute] (for newly created files only).
	 * @return
	 *   An asynchronous file channel.
	 * @throws IllegalArgumentException
	 *   If the combination of options is invalid.
	 * @throws UnsupportedOperationException
	 *   If an option is invalid for the specified path.
	 * @throws SecurityException
	 *   If the [security manager][SecurityManager] denies permission to
	 *   complete the operation.
	 * @throws IOException
	 *   If the open fails for any reason.
	 */
	@Throws(
		IllegalArgumentException::class,
		UnsupportedOperationException::class,
		SecurityException::class,
		IOException::class)
	fun openFile(
		path: Path,
		options: Set<OpenOption>,
		vararg attributes: FileAttribute<*>): AsynchronousFileChannel =
			AsynchronousFileChannel.open(
				path, options, fileExecutor, *attributes)

	/**
	 * A `BufferKey` identifies a file buffer in the [ ][cachedBuffers].
	 *
	 * @property fileHandle
	 *   The [file handle][FileHandle] that represents the provenance of the
	 *   associated buffer.
	 * @property startPosition
	 *  The start position of the buffer within the underlying file. This value
	 *  is measured in bytes, and need not be aligned.
	 *
	 * @constructor
	 * Construct a new buffer key, used to identify a buffer in the global
	 * cache.
	 *
	 * @param fileHandle
	 *   The [FileHandle] that represents the provenance of the associated
	 *   buffer.
	 * @param startPosition
	 *   The start position of the buffer within the underlying file. This value
	 *   is measured in bytes, and need not be aligned.
	 */
	class BufferKey constructor(
		private val fileHandle: FileHandle, private val startPosition: Long)
	{
		override fun equals(other: Any?): Boolean
		{
			if (other is BufferKey)
			{
				return fileHandle == other.fileHandle
					&& startPosition == other.startPosition
			}
			return false
		}

		override fun hashCode(): Int
		{
			var h = fileHandle.hashCode()
			h *= multiplier
			h = h xor -0x2e6715dd
			h += (startPosition shr 32).toInt()
			h *= multiplier
			h = h xor -0x6e7088ef
			h += startPosition.toInt()
			h *= multiplier
			h = h xor 0x32AE891D
			return h
		}
	}

	/**
	 * A `FileHandle` is an abstraction which wraps an [AsynchronousFileChannel]
	 * with some additional information like filename and buffer alignment.  It
	 * gets stuffed in a [pojo][PojoDescriptor] in a
	 * [property][SpecialAtom.FILE_KEY] of the [atom][AtomDescriptor] that
	 * serves as Avail's most basic view of a file handle.  Sockets use a
	 * substantially similar technique.
	 *
	 * In addition, the `FileHandle` weakly tracks which buffers need
	 * to be evicted from Avail's [file buffer cache][cachedBuffers].
	 *
	 * @author Mark van Gulik&lt;mark@availlang.org&gt;
	 *
	 * @property filename
	 *   The name of the file.
	 * @property alignment
	 *   The buffer alignment for the file.  Reading is only ever attempted on
	 *   this file at buffer boundaries.  There is a
	 *   [global file buffer cache][getBuffer], which is an
	 *   [LRUCache] of buffers across all open files.  Each buffer in the cache
	 *   has a length exactly equal to that file handle's alignment. A file
	 *   will often have a partial buffer at the end due to its size not being
	 *   an integral multiple of the alignment.  Such a partial buffer is always
	 *   excluded from the global file buffer cache.
	 * @property canRead
	 *   Whether this file can be read.
	 * @property canWrite
	 *   Whether this file can be written.
	 * @property channel
	 *  The underlying [AsynchronousFileChannel] through which input and/or
	 *  output takes place.
	 *
	 * @constructor
	 * Construct a new file handle.
	 *
	 * @param filename
	 *   The [name][A_String] of the file.
	 * @param alignment
	 *   The alignment by which to access the file.
	 * @param canRead
	 *   Whether the file can be read.
	 * @param canWrite
	 *   Whether the file can be written.
	 * @param channel
	 *   The [AsynchronousFileChannel] with which to do reading and writing.
	 */
	class FileHandle constructor(
		val filename: A_String,
		val alignment: Int,
		val canRead: Boolean,
		val canWrite: Boolean,
		val channel: AsynchronousFileChannel)
	{
		/**
		 * A weak set of [BufferKey]s pertaining to this file, for which there
		 * may be entries in the [global][getBuffer].  Since the buffer keys are
		 * specific to each [FileHandle], they are removed from the cache
		 * explicitly when the file is closed.  This weak set allows the cache
		 * removals to happen efficiently.
		 */
		val bufferKeys: MutableMap<BufferKey, Void> =
			synchronizedMap(WeakHashMap<BufferKey, Void>())
	}

	/**
	 * Answer the [container][MutableOrNull] responsible for the
	 * [buffer][A_Tuple] indicated by the supplied [key][BufferKey].
	 *
	 * @param key
	 *   A key.
	 * @return
	 *   A container for a buffer, possibly empty.
	 */
	fun getBuffer(key: BufferKey): MutableOrNull<A_Tuple> =
		stripNull(cachedBuffers.get(key))

	/**
	 * Discard the [container][MutableOrNull] responsible for the
	 * [buffer][A_Tuple] indicated by the supplied [key][BufferKey].
	 *
	 * @param key
	 *   A key.
	 */
	fun discardBuffer(key: BufferKey)
	{
		cachedBuffers.remove(key)
	}

	/**
	 * Open an [asynchronous server][AsynchronousServerSocketChannel].
	 *
	 * @return
	 *   An asynchronous server socket channel.
	 * @throws IOException
	 *   If the open fails for some reason.
	 */
	@Throws(IOException::class)
	fun openServerSocket(): AsynchronousServerSocketChannel =
		AsynchronousServerSocketChannel.open(socketGroup)

	/**
	 * Open an [asynchronous socket][AsynchronousSocketChannel].
	 *
	 * @return
	 *   An asynchronous socket channel.
	 * @throws IOException
	 *   If the open fails for some reason.
	 */
	@Throws(IOException::class)
	fun openSocket(): AsynchronousSocketChannel =
		AsynchronousSocketChannel.open(socketGroup)

	/**
	 * Destroy all data structures used by this `AvailRuntime`.  Also
	 * disassociate it from the current [Thread]'s local storage.
	 */
	fun destroy()
	{
		fileExecutor.shutdownNow()
		try
		{
			socketGroup.shutdownNow()
		}
		catch (e: IOException)
		{
			// Ignore.
		}

		try
		{
			fileExecutor.awaitTermination(10, TimeUnit.SECONDS)
		}
		catch (e: InterruptedException)
		{
			// Ignore.
		}

		try
		{
			socketGroup.awaitTermination(10, TimeUnit.SECONDS)
		}
		catch (e: InterruptedException)
		{
			// Ignore.
		}

	}

	companion object
	{
		/** The default [file system][FileSystem].  */
		val fileSystem: FileSystem = FileSystems.getDefault()

		/**
		 * The [link options][LinkOption] for following symbolic links.
		 */
		private val followSymlinks = arrayOf<LinkOption>()

		/**
		 * The [link options][LinkOption] for forbidding traversal of
		 * symbolic links.
		 */
		private val doNotFollowSymbolicLinks =
			arrayOf(LinkOption.NOFOLLOW_LINKS)

		/**
		 * Answer the appropriate [link options][LinkOption] for
		 * following, or not following, symbolic links.
		 *
		 * @param shouldFollow
		 * `true` for an array that permits symbolic link traversal,
		 * `false` for an array that forbids symbolic link traversal.
		 * @return An array of link options.
		 */
		fun followSymlinks(shouldFollow: Boolean): Array<LinkOption> =
			if (shouldFollow) followSymlinks else doNotFollowSymbolicLinks

		/**
		 * The [POSIX file permissions][PosixFilePermission]. *The order of
		 * these elements should not be changed!*
		 */
		private val posixPermissions = arrayOf(
			OWNER_READ,
			OWNER_WRITE,
			OWNER_EXECUTE,
			GROUP_READ,
			GROUP_WRITE,
			GROUP_EXECUTE,
			OTHERS_READ,
			OTHERS_WRITE,
			OTHERS_EXECUTE)

		/**
		 * The [POSIX file permissions][PosixFilePermission].
		 *
		 * @return The POSIX file permissions.
		 */
		fun posixPermissions(): Array<PosixFilePermission> =
			posixPermissions
	}
}
