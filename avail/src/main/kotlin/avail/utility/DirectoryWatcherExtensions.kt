/*
 * DirectoryWatcherExtensions.kt
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

package avail.utility

import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher as MethvinDirectoryWatcher
import io.methvin.watcher.hashing.FileHasher
import org.slf4j.helpers.NOPLogger
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchKey
import java.nio.file.WatchService
import kotlin.concurrent.thread

/** Interface for watching directory changes. */
interface DirectoryWatcherInterface {
	/** Launch the watcher with the given name and return the interface. */
	fun launch(name: String): DirectoryWatcherInterface

	/** Close the watcher. */
	fun close()
}

/** Directory watcher using Java's native [WatchService]. */
class JvmDirectoryWatcher(
	private val path: Path,
	private val onCreated: (Path) -> Unit = {},
	private val onModified: (Path) -> Unit = {},
	private val onDeleted: (Path) -> Unit = {}
) : DirectoryWatcherInterface {
	private val watchService: WatchService =
		FileSystems.getDefault().newWatchService()
	@Volatile private var running = false

	override fun launch(name: String) = apply {
		path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
		running = true
		thread(isDaemon = true, name = name) {
			while (running) {
				try {
					val key: WatchKey = watchService.take()
					for (event in key.pollEvents()) {
						val kind = event.kind()
						@Suppress("UNCHECKED_CAST")
						val eventPath = path.resolve(
							event.context() as Path)
						when (kind) {
							ENTRY_CREATE -> onCreated(eventPath)
							ENTRY_MODIFY -> onModified(eventPath)
							ENTRY_DELETE -> onDeleted(eventPath)
						}
					}
					key.reset()
				}
				catch (t: Throwable) {
					if (running) {
						// Try again.
					}
				}
			}
		}
	}

	override fun close() {
		running = false
		watchService.close()
	}
}

/** Directory watcher using methvin's directory-watcher (requires JNA). */
class NativeDirectoryWatcher(
	private val path: Path,
	private val onCreated: (Path) -> Unit = {},
	private val onModified: (Path) -> Unit = {},
	private val onDeleted: (Path) -> Unit = {}
) : DirectoryWatcherInterface {
	private val directoryWatcher = MethvinDirectoryWatcher.builder()
		.logger(NOPLogger.NOP_LOGGER)
		.fileHasher(FileHasher.LAST_MODIFIED_TIME)
		.path(path)
		.listener { event ->
			when (event.eventType()!!) {
				DirectoryChangeEvent.EventType.CREATE -> onCreated(event.path())
				DirectoryChangeEvent.EventType.MODIFY -> onModified(event.path())
				DirectoryChangeEvent.EventType.DELETE -> onDeleted(event.path())
				DirectoryChangeEvent.EventType.OVERFLOW -> {}
			}
		}
		.build()

	override fun launch(name: String) = apply {
		thread(isDaemon = true, name = name) {
			while (true) {
				try {
					directoryWatcher.watch()
					break
				}
				catch (t: Throwable) {
					// Try again.
				}
			}
		}
	}

	override fun close() {
		directoryWatcher.close()
	}
}
