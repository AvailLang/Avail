/*
 * StacksOutputFile.kt
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

package com.avail.stacks

import com.avail.AvailRuntime
import com.avail.utility.IO
import com.avail.utility.MutableLong
import com.avail.utility.Nulls

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.EnumSet

/**
 * The way a file is created.
 *
 * @author Richard Arriaga &lt;rich@availlang.org&gt;
 *
 * @property outputPath
 *   The [path][Path] to the output [directory][BasicFileAttributes.isDirectory]
 *   for documentation and data files.
 * @property synchronizer
 *   The [StacksSynchronizer] used to control the creation of Stacks
 *   documentation
 * @property name
 *   The name of the method the file represents as it is represented from the
 *   point of view of the main module being documented.
 *
 * @constructor
 * Construct a new `StacksOutputFile`.
 *
 * @param outputPath
 *   The [path][Path] to the output [directory][BasicFileAttributes.isDirectory]
 *   for documentation and data files.
 * @param fileName
 *   The name of the new file
 * @param synchronizer
 *   The [StacksSynchronizer] used to control the creation of Stacks
 *   documentation
 * @param runtime
 *   An [runtime][AvailRuntime].
 * @param name
 *   The name of the method the file represents as it is represented from the
 *   point of view of the main module being documented.
 * @throws IOException If an [I/O exception][IOException] occurs.
 */
class StacksOutputFile @Throws(IOException::class) constructor(
	val outputPath: Path,
	val synchronizer: StacksSynchronizer,
	fileName: String,
	runtime: AvailRuntime,
	val name: String)
{
	/**
	 * The error log file for the malformed comments.
	 */
	private var outputFile: AsynchronousFileChannel

	init
	{

		val filePath = outputPath.resolve(fileName)
		Files.createDirectories(outputPath)
		this.outputFile = runtime.ioSystem().openFile(
			filePath, EnumSet.of(
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING))
	}

	/**
	 * @return the errorFilePosition
	 */
	fun file(): AsynchronousFileChannel
	{
		return outputFile
	}

	/**
	 * Write text to a file.
	 *
	 * @param outputText
	 * The text to be written to file.
	 */
	@Synchronized fun write(outputText: String)
	{
		val buffer = ByteBuffer.wrap(
			outputText.toByteArray(StandardCharsets.UTF_8))
		val pos = MutableLong(0L)
		outputFile.write<Any>(
			buffer,
			pos.value, null,
			object: CompletionHandler<Int, Any>
			{
				override fun completed(result: Int?, attachment: Any?)
				{
					if (buffer.hasRemaining())
					{
						pos.value += Nulls.stripNull(result).toLong()
						outputFile.write<Any>(buffer, pos.value, null, this)
					}
					else
					{
						IO.close(outputFile)
						synchronizer.decrementWorkCounter()
					}
				}

				override fun failed(exc: Throwable?, attachment: Any?)
				{
					IO.close(outputFile)
					synchronizer.decrementWorkCounter()
				}
			})
	}
}
