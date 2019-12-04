/*
 * FileManager.kt
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail.server.io.files

import com.avail.AvailRuntimeConfiguration
import com.avail.AvailThread
import com.avail.utility.LRUCache
import com.avail.utility.SimpleThreadFactory
import java.io.IOException
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * `FileManager` manages the opened files of the Avail Server. It provides an
 * LRU caching mechanism by which files can be added and removed as needed to
 * control the number of open files in memory.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 */
object FileManager
{
	/**
	 * The [EnumSet] of [StandardOpenOption]s used when opening files.
	 */
	private val fileOptions =
		EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE)

	/**
	 * The [thread pool executor][ThreadPoolExecutor] for asynchronous file
	 * operations performed on behalf of this [FileManager].
	 */
	private val fileExecutor = ThreadPoolExecutor(
		AvailRuntimeConfiguration.availableProcessors,
		AvailRuntimeConfiguration.availableProcessors shl 2,
		10L,
		TimeUnit.SECONDS,
		LinkedBlockingQueue(),
		SimpleThreadFactory("AvailServerFileManager"),
		ThreadPoolExecutor.CallerRunsPolicy())

	/**
	 * Maintain an [LRUCache] of [AvailServerFile]s opened by the Avail server.
	 *
	 * This cache works in conjunction with [pathToIdMap] to maintain links
	 * between
	 */
	private val fileCache = LRUCache<UUID, ServerFileWrapper>(
		10000,
		10,
		{
			var path = ""
			pathToIdMap.forEach { (k, v) ->
				if (v == it)
				{
					path = k
					return@forEach
				}
			}
			// TODO what happens if never found? Should be an error as that
			//  should not happen, hence bad UUID
			val file = openFile(Paths.get(path), fileOptions)
			ServerFileWrapper(path, file)
		},
		{ _, value ->
			value.close()
		})

	/**
	 * A [Map] from the String [Path] location of a [file][AvailServerFile] to
	 * the [UUID] that uniquely identifies that file in the [fileCache].
	 *
	 * This map will never be cleared of values as cached files that have been
	 * removed from the `fileCache` must maintain association with the
	 * server-assigned [UUID] that identifies the file for all interested
	 * clients. If a client requests a file action with a given UUID and it is
	 * not found in the `fileCache`, this map will be used to retrieve the
	 * associated file from disk and placed back in the `fileCache`.
	 */
	private val pathToIdMap = mutableMapOf<String, UUID>()

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

	/** The default [file system][FileSystem].  */
	@JvmStatic
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
	@JvmStatic
	fun followSymlinks(shouldFollow: Boolean): Array<LinkOption> =
		if (shouldFollow) followSymlinks else doNotFollowSymbolicLinks

	/**
	 * The [POSIX file permissions][PosixFilePermission]. *The order of
	 * these elements should not be changed!*
	 */
	@JvmStatic
	val posixPermissions = arrayOf(
		PosixFilePermission.OWNER_READ,
		PosixFilePermission.OWNER_WRITE,
		PosixFilePermission.OWNER_EXECUTE,
		PosixFilePermission.GROUP_READ,
		PosixFilePermission.GROUP_WRITE,
		PosixFilePermission.GROUP_EXECUTE,
		PosixFilePermission.OTHERS_READ,
		PosixFilePermission.OTHERS_WRITE,
		PosixFilePermission.OTHERS_EXECUTE)
}