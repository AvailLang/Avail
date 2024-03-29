/*
 * StacksErrorLog.kt
 * Copyright © 1993-2022, The Avail Foundation, LLC.
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

package avail.stacks

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * A Stacks log file that contains errors from processing comments in Avail
 * modules.
 *
 * @author Richard A Arriaga &lt;rich@availlang.org&gt;
 */
class StacksErrorLog
/**
 * Construct a new [StacksErrorLog].
 *
 * @param outputPath
 *   The [path][Path] to the output [directory][BasicFileAttributes.isDirectory]
 *   for documentation and data files.
 */
	(
	/**
	 * The [path][Path] to the output
	 * [directory][BasicFileAttributes.isDirectory] for documentation and data
	 * files.
	 */
	internal val outputPath: Path)
{

	/**
	 * The error log file for the malformed comments.
	 */
	private var errorLog: AsynchronousFileChannel? = null

	/**
	 * The amount of errors listed in the file
	 */
	private var errorCount: Int = 0

	/**
	 * File position tracker for error log
	 */
	private var errorFilePosition: Long = 0

	/**
	 * @return the errorFilePosition
	 */
	fun file(): AsynchronousFileChannel?
	{
		return errorLog
	}

	init
	{
		this.errorFilePosition = 0
		this.errorCount = 0
		try
		{
			val errorLogPath = outputPath.resolve("errorlog.html")
			Files.createDirectories(outputPath)
			this.errorLog = AsynchronousFileChannel.open(
				errorLogPath,
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING)

			val openHTML = ByteBuffer.wrap(
				("<!DOCTYPE html>\n<head><meta charset=\"UTF-8\"><style>h3 "
					+ "{text-decoration:underline;}\n "
					+ "strong, em {color:blue;}</style>\n"
					+ "</head>\n<body>\n")
					.toByteArray(StandardCharsets.UTF_8))

			addLogEntry(openHTML, 0)
		}
		catch (e: IOException)
		{
			e.printStackTrace()
		}

	}

	/**
	 * Add a new error log entry to the error error log.
	 * @param buffer
	 * The error log buffer
	 * @param addToErrorCount
	 * The amount of errors added with this log update.
	 */
	@Synchronized fun addLogEntry(
		buffer: ByteBuffer,
		addToErrorCount: Int)
	{
		errorCount += addToErrorCount
		val position = errorFilePosition
		errorFilePosition += buffer.limit().toLong()
		errorLog!!.write(buffer, position)
	}

	/**
	 * @return the errorCount
	 */
	fun errorCount(): Int
	{
		return errorCount
	}
}
