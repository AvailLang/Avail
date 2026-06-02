/*
 * ProfileReconstructor.kt
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.interpreter.profile;

import avail.interpreter.profile.ProfileCollector.Event
import avail.interpreter.profile.ProfileCollector.Event.EndCall
import avail.interpreter.profile.ProfileCollector.Event.EndOfFile
import avail.interpreter.profile.ProfileCollector.Event.SwitchContinuation
import avail.interpreter.profile.ProfileCollector.Event.StartCallL1
import avail.interpreter.profile.ProfileCollector.Event.StartCallL2
import avail.interpreter.profile.ProfileCollector.Event.StartCallL2Simple
import avail.interpreter.profile.ProfileCollector.Event.StartFiber
import avail.interpreter.profile.ProfileCollector.Event.StopFiber
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream

/**
 * A [ProfileReconstructor] processes a file previously captured by a
 * [ProfileCollector], reconstructing the call tree and timing information from
 * the recorded events.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class ProfileReconstructor(
	val file: File,
) : Closeable
{
	val bufferedReader = BufferedInputStream(FileInputStream(file), 8192)

	val root = ProfileNode(dummyRootProfileFunction)

	var current = root

	var latestTime = 0L

	val profileFunctions = mutableListOf<ProfileFunction>()

	val modules = mutableListOf<String>()

	fun processFile(): ProfileNode
	{
		while (true)
		{
			val event = Event.entries[readByte()]
			readTime()
			when (event)
			{
				StartFiber ->
				{
					assert(current === root) {
						"Attempting to process a StartFiber event with an " +
							"existing fiber state already active."
					}
					readAndRestoreStack()
				}
				StopFiber -> exitStack()
				// The distinction is ignored for now.
				StartCallL1, StartCallL2Simple, StartCallL2 ->
				{
					val function = readFunction()
					var newNode = current.children[function]
					if (newNode == null)
					{
						newNode = ProfileNode(function, current)
						current.children[function] = newNode
					}
					newNode.invocations++
					newNode.totalTime -= latestTime
					current = newNode
				}
				EndCall ->
				{
					current.totalTime += latestTime
					current = current.parent!!
				}
				SwitchContinuation ->
				{
					exitStack()
					readAndRestoreStack()
				}
				EndOfFile ->
				{
					// It might be the case that the profiling was interrupted
					// while a fiber was running.  We have to exit the stack to
					// ensure the accounting mechanism doesn't leave hanging the
					// pre-subtracted start times from any node totalTimes.
					exitStack()
					return root
				}
			}
		}
	}

	/** Read a single byte as an [Int]. */
	private fun readByte(): Int
	{
		val byte = bufferedReader.read()
		if (byte == -1) throw IllegalStateException("Unexpected EOF")
		return byte
	}

	/**
	 * Read a [ULong] from the file, encoded via [ProfileCollector.writeULong].
	 */
	fun nextULong(): ULong
	{
		val firstByte = readByte()
		return when
		{
			// One byte, 0..127
			firstByte <= 0x7F ->
				firstByte.toULong()
			// Two bytes, 128..0x3FFF
			firstByte <= 0xBF ->
				(firstByte - 0x80 shl 8).toULong() +
					readByte().toULong()
			// Three bytes, 0x4000..0x3D_FFFF
			firstByte <= 0xFD ->
				(firstByte - 0xC0 shl 16).toULong() +
					(readByte().toULong() shr 8) +
					readByte().toULong()
			// Five bytes, 0x3F_0000..0xFFFF_FFFF
			firstByte == 0xFE ->
				(readByte().toULong() shl 24) +
					(readByte().toULong() shl 16) +
					(readByte().toULong() shl 8) +
					readByte().toULong()
			// Nine bytes, 0x0000_0001_0000_0000..0xFFFF_FFFF_FFFF_FFFF
			else ->
				(readByte().toULong() shl 56) +
					(readByte().toULong() shl 48) +
					(readByte().toULong() shl 40) +
					(readByte().toULong() shl 32) +
					(readByte().toULong() shl 24) +
					(readByte().toULong() shl 16) +
					(readByte().toULong() shl 8) +
					readByte().toULong()
		}
	}

	private fun readTime()
	{
		latestTime += nextULong().toLong()
	}

	/** Lookup or construct a [ProfileFunction] from the file. */
	fun readFunction(): ProfileFunction = when (val index = nextULong().toInt())
	{
		// Construct a function from its first encounter.
		0 ->
		{
			val functionName = readString()
			val line = nextULong().toInt()
			val moduleName = readModule()
			val function = ProfileFunction(
				functionName = functionName,
				moduleName = moduleName,
				line = line)
			profileFunctions.add(function)
			function
		}
		// Reuse an existing function.
		else -> profileFunctions[index - 1]
	}

	/** Lookup or construct a module name from the file. */
	fun readModule(): String = when (val index = nextULong().toInt())
	{
		// Construct a module from its first encounter.
		0 ->
		{
			val moduleName = readString()
			modules.add(moduleName)
			moduleName
		}
		// Reuse an existing module name.
		else -> modules[index - 1]
	}

	/** Read a string from the file. */
	fun readString(): String
	{
		val size = nextULong().toInt()
		return buildString(size) {
			repeat(size) { append(readByte().toChar()) }
		}
	}

	fun readAndRestoreStack()
	{
		assert(current == root)
		repeat(nextULong().toInt())
		{
			val function = readFunction()
			var newNode = current.children[function]
			if (newNode == null)
			{
				newNode = ProfileNode(function, current)
				current.children[function] = newNode
			}
			// DO NOT increment the invocation count here.
			newNode.totalTime -= latestTime
			current = newNode
		}
	}

	fun exitStack()
	{
		while (current != root)
		{
			current.totalTime += latestTime
			current = current.parent!!
		}
	}

	/**
	 * Write the [Event.EndOfFile] tag, to indicate the file is complete, then
	 * flush and close it.
	 */
	override fun close()
	{
		bufferedReader.close()
	}

	companion object
	{
		/**
		 * The [ProfileFunction] that will be used as the basis for the dummy
		 * root [ProfileNode] for the call tree.
		 */
		val dummyRootProfileFunction =
			ProfileFunction("<dummy root>", "<dummy module>", -1)
	}
}
