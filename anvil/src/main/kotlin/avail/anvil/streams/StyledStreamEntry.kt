/*
 * BuildOutputStreamEntry.kt
 * Copyright © 1993-2021, The Avail Foundation, LLC.
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

package avail.anvil.streams

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * A singular read from or write to a stream.  This read/write is considered
 * atomic with respect to writes from other threads, and will not have content
 * from other writes interspersed with its characters.
 *
 * @property style
 *   The [StreamStyle] with which to render the string.
 * @property string
 *   The captured [String].
 *
 * @constructor
 * Create an entry that captures a [StreamStyle] and [String] to output.
 *
 * @param style
 *   The [StreamStyle] with which to render the string.
 * @param string
 *   The [String] to output.
 */
internal class StyledStreamEntry constructor(
	val style: StreamStyle,
	val string: String)
{
	/**
	 * Serialize this [StyledStreamEntry] to the provided stream.
	 *
	 * @param stream
	 *   The [ByteArrayOutputStream] to write to.
	 */
	fun serializeTo (stream: ByteArrayOutputStream)
	{
		stream.write(style.ordinal)
		val bytes = string.toByteArray(Charsets.UTF_8)
		stream.write(bytes.size)
		stream.write(bytes)
	}

	/**
	 * Answer the [AnnotatedString] of this [StyledStreamEntry] for the given
	 * theme type.
	 *
	 * @param isDark
	 *   `true` if [StreamStyle.dark] is to be used; `false` indicates
	 *   [StreamStyle.light] is to be used.
	 * @return
	 *   The `AnnotatedString` of this `StyledStreamEntry`.
	 */
	fun annotatedString (isDark: Boolean): AnnotatedString =
		AnnotatedString(
			string,
			listOf(
				AnnotatedString.Range(
					SpanStyle(
						color = if (isDark) { style.dark } else { style.light }),
					0,
					string.length)))

	companion object
	{
		/**
		 * Read a [StyledStreamEntry] from the provided stream.
		 *
		 * @param stream
		 *   The [ByteArrayInputStream] to read from.
		 * @return
		 *   The read [StyledStreamEntry].
		 */
		fun read (stream: ByteArrayInputStream): StyledStreamEntry
		{
			// TODO handle errors
			val style = StreamStyle.values()[stream.read()]
			val size = stream.read()
			val stringBytes = stream.readNBytes(size)
			return StyledStreamEntry(style, String(stringBytes))
		}
	}
}
