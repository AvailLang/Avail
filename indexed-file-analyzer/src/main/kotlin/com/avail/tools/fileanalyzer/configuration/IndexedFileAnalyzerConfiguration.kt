/*
 * CompilerConfiguration.kt
 * Copyright © 1993-2020, The Avail Foundation, LLC.
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

package com.avail.tools.fileanalyzer.configuration

import com.avail.persistence.IndexedFile
import com.avail.tools.fileanalyzer.IndexedFileAnalyzer
import com.avail.utility.configuration.Configuration
import java.io.File


/**
 * An `IndexedFileAnalyzerConfiguration` instructs an [IndexedFileAnalyzer] on
 * the analysis of an [IndexedFile].
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
class IndexedFileAnalyzerConfiguration : Configuration
{
	/**
	 * `true` iff the record numbers should be written, or in the absence of
	 * either [binary] or [text], whether an undecorated count of the number of
	 * eligible records should be written. `false` by default.
	 */
	internal var counts = false

	/**
	 * `true` iff the size of each record should be written prior to its body.
	 * `false` by default.
	 */
	internal var sizes = false

	/**
	 * `true` iff records should be written as hexadecimal bytes.  Can be
	 * combined with [text], which will show hexadecimal on the left ane the
	 * corresponding ASCII values on the right, substituting '.' if the byte is
	 * outside the range 0x20-0x7E.  `false` by default.
	 */
	internal var binary = false

	/**
	 * `true` iff records should be treated as UTF-8 encoded Unicode strings.
	 * If combined with [binary], only printable ASCII values (0x20-0x7E) are
	 * shown to the right of the hex bytes.  `false` by default.
	 */
	internal var text = false

	/**
	 * Either null if not specified, or the directory to create or use for
	 * dumping the content of each record, and optionally metadata, into
	 * separate files.
	 */
	internal var explodeDirectory: File? = null

	/**
	 * The directory, containing records and optionally metadata, to assemble
	 * into an indexed file.
	 */
	internal var implodeDirectory: File? = null

	/**
	 * The header string that indicates the kind of [IndexedFile] to create.
	 * Must be present iff [implodeDirectory] is present.
	 */
	internal var implodeHeader: String? = null

	/**
	 * The output [File] for an implosion from some [implodeDirectory].
	 */
	internal var implodeOutput: File? = null

	/**
	 * `true` iff metadata should be processed as well.  `false` by default.
	 */
	internal var metadata = false

	/** The lowest zero-based [Long] record number that may be processed. */
	internal var lower: Long? = null

	/** The highest zero-based [Long] record number that may be processed. */
	internal var upper: Long? = null

	/** The name of the [IndexedFile] to analyze. */
	internal var inputFile: File? = null

	/** The destination file if stripping UTF-8 encoding from records. */
	internal var patchOutputFile: File? = null
}
