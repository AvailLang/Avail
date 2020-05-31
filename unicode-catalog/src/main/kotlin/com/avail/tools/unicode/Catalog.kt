/*
 * Catalog.kt
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

package com.avail.tools.unicode

import com.avail.utility.IO
import com.avail.utility.json.JSONArray
import com.avail.utility.json.JSONData
import com.avail.utility.json.JSONFriendly
import com.avail.utility.json.JSONWriter
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.regex.Pattern

/**
 * A `Catalog` represents the complete catalog of Unicode characters used
 * by the Avail project.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
internal class Catalog
{
	/**
	 * The [list][List] of all [paths][Path] whose targets should be searched
	 * for Unicode characters.
	 */
	internal val allPaths: MutableList<Path> = LinkedList()

	/**
	 * The [set][Set] of all [Unicode&#32;code&#32;points][CharacterInfo] used
	 * by the Avail project.
	 */
	private var allCodePoints: MutableSet<CharacterInfo>? = null
		@Synchronized
		get()
		{
			var codePoints = field
			if (codePoints === null)
			{
				codePoints = HashSet()
				field = codePoints
				refresh()
				codePoints = TreeSet(codePoints)
				field = codePoints
			}
			return field
		}

	/**
	 * The [set][Set] of all non-ASCII [code&#32;points][CharacterInfo].
	 */
	private var allNonAsciiCodePoints: MutableSet<CharacterInfo>? = null
		@Synchronized
		get()
		{
			var codePoints = field
			if (codePoints === null)
			{
				codePoints = TreeSet()
				field = codePoints
				allCodePoints!!.forEach {
					if (it.codePoint > 127)
					{
						codePoints.add(it)
					}
				}
			}
			return field
		}

	/**
	 * The [set][Set] of all non-ASCII, non-alphanumeric
	 * [code&#32;points][CharacterInfo].
	 */
	private var allSymbolicCodePoints: MutableSet<CharacterInfo>? = null
		@Synchronized
		get()
		{
			var codePoints = field
			if (codePoints === null)
			{
				codePoints = TreeSet()
				field = codePoints
				allNonAsciiCodePoints!!.forEach {
					if (!Character.isLetterOrDigit(it.codePoint))
					{
						codePoints.add(it)
					}
				}
			}
			return field
		}

	/**
	 * Accumulate into [allPaths] every path under the specified root whose file
	 * name extension belongs to [extensions].
	 *
	 * @param root
	 *   The [path][Path] beneath which to search for matching files.
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun computeAllPathsBeneath(root: Path)
	{
		val visitOptions = EnumSet.of(FileVisitOption.FOLLOW_LINKS)
		Files.walkFileTree(
			root,
			visitOptions,
			Integer.MAX_VALUE,
			object : SimpleFileVisitor<Path>()
			{
				override fun visitFile(
					path: Path,
					attrs: BasicFileAttributes): FileVisitResult
				{
					val name = path.fileName.toString()
					val components = name.split("\\.".toRegex())
						.dropLastWhile { it.isEmpty() }
						.toTypedArray()
					try
					{
						val extension = components[components.size - 1]
						if (extensions.contains(extension))
						{
							allPaths.add(path)
						}
					}
					catch (e: ArrayIndexOutOfBoundsException)
					{
						// Do nothing.
					}

					return FileVisitResult.CONTINUE
				}
			})
	}

	/**
	 * Accumulate into [allPaths] every matching path that resides beneath a
	 * [root&#32;path][rootPaths].
	 *
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun computeAllPaths() =
		rootPaths.forEach { computeAllPathsBeneath(it) }

	/**
	 * Accumulate into [allCodePoints] every Unicode code point encountered
	 * within the specified UTF-8 encoded file.
	 *
	 * @param path
	 *   The path to the file that should be scanned.
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun computeAllCodePointsIn(path: Path)
	{
		val codePoints = allCodePoints!!
		val options = EnumSet.of(StandardOpenOption.READ)
		val input = FileChannel.open(path, options)
		val decoder = StandardCharsets.UTF_8.newDecoder()
		val encoded = ByteBuffer.allocateDirect(4096)
		val decoded = CharBuffer.allocate(4096)
		try
		{
			var atEnd = false
			while (!atEnd)
			{
				val bytesRead = input.read(encoded)
				atEnd = bytesRead == -1
				encoded.flip()
				var result = decoder.decode(encoded, decoded, atEnd)
				if (atEnd)
				{
					result = decoder.flush(decoded)
				}
				assert(!result.isOverflow)
				if (result.isError)
				{
					result.throwException()
				}
				if (encoded.hasRemaining())
				{
					val delta = encoded.limit() - encoded.position()
					for (i in 0 until delta)
					{
						val b = encoded.get(encoded.position() + i)
						encoded.put(i, b)
					}
					encoded.limit(encoded.capacity())
					encoded.position(delta)
				}
				else
				{
					encoded.clear()
				}
				decoded.flip()
				val string = decoded.toString()
				decoded.clear()
				// Right! We finally have decoded data as a string, so we can
				// now extract code points and populate the accumulate.
				var codePoint: Int
				var i = 0
				val size = string.length
				while (i < size)
				{
					codePoint = string.codePointAt(i)
					val info = CharacterInfo(codePoint)
					// Don't lose information already accumulated about this
					// code point. (The equals operation only checks the code
					// point, not any ancillary information!)
					codePoints.add(info)
					i += Character.charCount(codePoint)
				}
			}
		}
		finally
		{
			IO.close(input)
		}
	}

	/**
	 * Accumulate into [allCodePoints] every Unicode code point encountered
	 * within a [file&#32;of&#32;interest][allPaths].
	 *
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun computeAllCodePoints() =
		allPaths.forEach { computeAllCodePointsIn(it) }

	/**
	 * Refresh the [catalog][Catalog] from the file system and the Internet.
	 *
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	fun refresh()
	{
		computeAllPaths()
		computeAllCodePoints()
		populateAllCharacterInfo()
	}

	/**
	 * Populate all [character&#32;info][CharacterInfo] using data obtained from
	 * [FileFormat.Info](http://www.fileformat.info). (Thanks, guys!)
	 *
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	private fun populateAllCharacterInfo()
	{
		val codePoints = allCodePoints!!
		codePoints.forEach { info ->
			if (!info.previouslyFetched)
			{
				populateCharacterInfo(info)
				try
				{
					// Don't beat the crap out of the site. Don't do more than
					// 20 requests per second.
					Thread.sleep(50)
				}
				catch (e: InterruptedException)
				{
					// Give up.
					return
				}
			}
		}
	}

	/**
	 * A [JSON-friendly&#32;representative][JSONFriendly] of the
	 * [complete&#32;set&#32;of&#32;code&#32;points][allCodePoints].
	 *
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	val jsonFriendlyCodePoints: JSONFriendly
		@Throws(IOException::class)
		get()
		{
			val codePoints = allCodePoints!!
			return object : JSONFriendly
			{
				override fun writeTo(writer: JSONWriter)
				{
					writer.startArray()
					codePoints.forEach { writer.write(it) }
					writer.endArray()
				}
			}
		}


	/**
	 * A [JSON-friendly&#32;representative][JSONFriendly] of the
	 * [complete&#32;set&#32;of&#32;non-ASCII&#32;code&#32;points][allNonAsciiCodePoints].
	 *
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	val jsonFriendlyNonAsciiCodePoints: JSONFriendly
		@Throws(IOException::class)
		get()
		{
			val codePoints = allNonAsciiCodePoints!!
			return object : JSONFriendly
			{
				override fun writeTo(writer: JSONWriter)
				{
					writer.startArray()
					codePoints.forEach { writer.write(it) }
					writer.endArray()
				}
			}
		}

	/**
	 * Answer a [JSON-friendly&#32;representative][JSONFriendly] of the
	 * [set][allSymbolicCodePoints] of code points that are neither ASCII nor
	 * alpha-numeric.
	 *
	 * @return The representative.
	 * @throws IOException
	 * If an I/O exception occurs.
	 */
	val jsonFriendlySymbolicCodePoints: JSONFriendly
		@Throws(IOException::class)
		get()
		{
			val codePoints = allSymbolicCodePoints!!
			return object : JSONFriendly
			{
				override fun writeTo(writer: JSONWriter)
				{
					writer.startArray()
					codePoints.forEach { writer.write(it) }
					writer.endArray()
				}
			}
		}

	/**
	 * Construct a new [Catalog].
	 *
	 * @param data
	 *   A `JSONData` that contains the catalog data. May be `null`.
	 */
	private constructor(data: JSONData?)
	{
		val set = HashSet<CharacterInfo>()
		(data as? JSONArray)?.forEach { set.add(CharacterInfo.readFrom(it)) }
		allCodePoints = TreeSet(set)
	}

	/**
	 * Construct a new [Catalog]. Populate it with character information.
	 *
	 * @throws IOException
	 *   If an I/O exception occurs.
	 */
	@Throws(IOException::class)
	constructor()
	{
		// Populate all code point information.
		allCodePoints!!
	}

	override fun toString(): String
	{
		// Don't load the catalog!
		val codePoints = allCodePoints ?: return "«empty catalog»"
		val builder = StringBuilder()
		builder.append(String.format("%d code point(s):\n", codePoints.size))
		var first = true
		codePoints.forEach {
			if (!first)
			{
				builder.append(",\n")
			}
			builder.append(it)
			first = false
		}
		builder.append("\n")
		return builder.toString()
	}

	companion object
	{
		/**
		 * The file name extensions of files that should be searched for Unicode
		 * characters.
		 */
		internal val extensions: Set<String> = HashSet(listOf(
			"java",
			"avail",
			"properties"))

		/** The source paths to search for matching files.  */
		internal val rootPaths = listOf(
			// The Java search path.
			Paths.get("src"),
			// The Avail search path.
			Paths.get("distro", "src"))

		/**
		 * Answer the [URL] of the [FileFormat.Info](http://www.fileformat.info)
		 * page that describes the requested code point.
		 *
		 * @param info
		 *   A [code&#32;point][CharacterInfo].
		 * @return
		 *   The appropriate URL.
		 * @throws MalformedURLException
		 *   If the URL is malformed (but this should never happen).
		 */
		@Throws(MalformedURLException::class)
		private fun urlFor(info: CharacterInfo): URL
		{
			val urlString = String.format(
				"http://www.fileformat.info/info/unicode/char/%04x/index.htm",
				info.codePoint)
			return URL(urlString)
		}

		/**
		 * The [pattern][Pattern] for extracting the HTML entity name of a code
		 * point.
		 */
		private val htmlEntityNamePattern = Pattern.compile("(?s)&amp;(\\w+?);")

		/**
		 * Populate the specified [CharacterInfo] with data obtained from
		 * [FileFormat.Info](http://www.fileformat.info). (Thanks, guys!)
		 *
		 * @param info
		 *   The `CharacterInfo` to populate.
		 * @throws IOException
		 *   If an I/O exception occurs.
		 */
		@Throws(IOException::class)
		private fun populateCharacterInfo(info: CharacterInfo)
		{
			val url = urlFor(info)
			System.err.printf("Fetching content from %s…%n", url)
			val connection = url.openConnection()
			var encoding = connection.contentEncoding
			if (encoding === null)
			{
				encoding = "UTF-8"
			}
			val characterSet = Charset.forName(encoding)
			val reader = BufferedReader(
				InputStreamReader(
					connection.getInputStream(),
					characterSet))
			val builder = StringBuilder(4096)
			try
			{
				val buffer = CharBuffer.allocate(4096)
				while (reader.read(buffer) != -1)
				{
					buffer.flip()
					builder.append(buffer)
					buffer.clear()
				}
			}
			finally
			{
				IO.close(reader)
			}
			val content = builder.toString()
			// We have the HTML content now. Let's mine it for the information
			// that we want. First exact the HTML entity name. There may not be
			// one.
			val matcher = htmlEntityNamePattern.matcher(content)
			if (matcher.find())
			{
				val entityName = matcher.group(1)
				info.htmlEntityName = String.format("&%s;", entityName)
			}
		}

		/**
		 * Decode a [Catalog] from the specified [JSONData].
		 *
		 * @param data
		 *   A `JSONData`. May be `null`.
		 * @return A `Catalog`.
		 */
		fun readFrom(data: JSONData?) = Catalog(data)
	}
}
