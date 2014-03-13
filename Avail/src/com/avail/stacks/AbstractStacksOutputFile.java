/**
 * AbstractStacksOutputFile.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

package com.avail.stacks;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * TODO: Document AbstractStacksOutputFile!
 *
 * @author TODO &lt;TODO@availlang.org&gt;
 */
public abstract class AbstractStacksOutputFile
{
	/**
	 * The {@linkplain Path path} to the output {@linkplain
	 * BasicFileAttributes#isDirectory() directory} for documentation and
	 * data files.
	 */
	final Path outputPath;
	/**
	 * Construct a new {@link AbstractStacksOutputFile}.
	 * @param outputPath
	 * 	 The {@linkplain Path path} to the output {@linkplain
	 *   BasicFileAttributes#isDirectory() directory} for documentation and
	 *   data files.
	 *
	 */
	public AbstractStacksOutputFile (final Path outputPath)
	{
		this.outputPath = outputPath;
	}

	/**
	 * Add a new error log entry to the error error log.
	 * @param buffer
	 * 		The error log buffer
	 * @param addToErrorCount
	 * 		The amount of errors added with this log update.
	 */
	public abstract void addLogEntry(final ByteBuffer buffer,
		final int addToErrorCount);
}
