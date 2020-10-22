/*
 * IndexedFileAnalyzer.kt
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

package com.avail.tools.fileanalyzer

import com.avail.persistence.IndexedFile
import com.avail.persistence.IndexedFileBuilder
import com.avail.tools.fileanalyzer.IndexedFileAnalyzer.ProcessResult.CONFIGURATION_ERROR
import com.avail.tools.fileanalyzer.IndexedFileAnalyzer.ProcessResult.OTHER_ERROR
import com.avail.tools.fileanalyzer.configuration.CommandLineConfigurator
import com.avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey
import com.avail.tools.fileanalyzer.configuration.IndexedFileAnalyzerConfiguration
import com.avail.utility.configuration.ConfigurationException
import java.io.File
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess
import kotlin.text.Charsets.UTF_8
import kotlin.text.RegexOption.IGNORE_CASE

/**
 * The IndexedFileAnalyzer understands options that are specified in detail in
 * [OptionKey], but listed in brief here:
 *
 * -c
 * --counts
 * > Show record counts before each record, or the total count if neither -b nor
 * > -t is specified.
 * -s
 * --sizes
 * > Show record size before each record.
 * -b
 * --binary
 * > Output records contents in hex.
 * -t
 * --text
 * > Decode records as UTF-8 strings.  If combined with -b, show printable ASCII
 * > characters to the right of the hex.
 * -x=<dir>
 * --explode=<dir>
 * > Write each record to a separate file in the specified directory.
 * -m
 * --metadata
 * > Process the file's metadata, if present.
 * -l=<num>
 * --lower=<num>
 * > The zero-based lowest record number to process.  Must be ≥ 0.
 * -u=<num>
 * --upper=<num>
 * > The zero-based upper record number to process.  Must be ≥ -1.
 *
 * -?
 * > Display help text containing a description of the application and an
 * > enumeration of its options.
 *
 * &lt;filename&gt;
 * > The name of the [IndexedFile] to analyze.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
object IndexedFileAnalyzer
{
	/**
	 * Configure the `IndexedFileAnalyzer` to process an [IndexedFile].
	 *
	 * @param args
	 *   The command-line arguments.
	 * @return
	 *   A viable [configuration][IndexedFileAnalyzerConfiguration].
	 * @throws ConfigurationException
	 *   If configuration fails for any reason.
	 */
	@Throws(ConfigurationException::class)
	private fun configure(args: Array<String>): IndexedFileAnalyzerConfiguration
	{
		val configuration = IndexedFileAnalyzerConfiguration()
		// Update the configuration using the command-line arguments.
		val commandLineConfigurator =
			CommandLineConfigurator(configuration, args, System.out)
		commandLineConfigurator.updateConfiguration()
		return configuration
	}

	/**
	 * A helper class capable of reading any variety of [IndexedFile].
	 */
	class ArbitraryIndexedFileBuilder(
		file: File
	): IndexedFileBuilder(parseHeader(file))

	/** Extract the header string from the file with the given name. */
	fun parseHeader(file: File): String =
		file.inputStream().reader(UTF_8).buffered().use {
			buildString {
				while (true) {
					val ch = it.read()
					if (ch == 0) break
					append(ch.toChar())
				}
			}
		}

	/**
	 * Perform the given action with the indices of records in the file that
	 * fall between the optionally specified bounds.
	 */
	private fun indices(): LongRange {
		val low = max(configuration.lower ?: 0, 0)
		val high = min(
			configuration.upper ?: Long.MAX_VALUE, indexedFile.size - 1)
		return low..high
	}

	/**
	 * Output up to 16 bytes of data in hexadecimal, with an optional printable
	 * ASCII column to the right.  Precede it with the hexadecimal start
	 * position within the record.  Follow it with a linefeed ('\n').
	 */
	private fun writeBinaryRow(
		startIndex: Long,
		totalSize: Long,
		bytes: ByteArray,
		output: PrintStream)
	{
		with (output) {
			assert(configuration.binary)
			val format = when {
				totalSize > 0x10000000 -> "%016X: "
				totalSize > 0x1000 -> "%08X: "
				else -> "%04X: "
			}
			format(format, startIndex)
			for (i in 0..15)
			{
				if (i == 8) append(" ")  // River into two groups of 8.
				if (i < bytes.size) format(" %02X", bytes[i].toInt() and 0xFF)
				else append(" --")
			}
			if (configuration.text) {
				append("  ")
				bytes.forEachIndexed { i, b ->
					if (i == 8) append(" ")  // River into two groups of 8.
					append(
						when (b) {
							in 0x20..0x7E -> b.toChar()
							else -> '.'
						})
				}
			}
			append('\n')
		}
	}

	/**
	 * Output the record with the given index.  If -1L is passed, use the
	 * metadata if present.
	 */
	private fun writeRecord(
		recordNumber: Long,
		output: PrintStream)
	{
		val record: ByteArray = when (recordNumber) {
			-1L -> indexedFile.metadata ?: return
			else -> indexedFile[recordNumber]
		}
		with (configuration) {
			if (counts)
			{
				output.append(
					when (recordNumber) {
						-1L -> "Metadata\n"
						else -> "Record=$recordNumber\n"
					})
			}
			if (sizes)
			{
				output.append("Size=${record.size}\n")
			}
			when {
				binary -> {
					for (start in 0..record.size step 16) {
						writeBinaryRow(
							start.toLong(),
							record.size.toLong(),
							record.sliceArray(
								start..min(start + 15, record.size - 1)),
							output)
					}
				}
				text -> output.append(record.toString(UTF_8))
				else -> {}
			}
		}
	}

	/** The configuration of the analyzer. */
	private lateinit var configuration: IndexedFileAnalyzerConfiguration

	/** The open [IndexedFile] to analyze. */
	private lateinit var indexedFile: IndexedFile

	/**
	 * Given a [ByteArray] which accidentally double-encodes a string as UTF-8,
	 * decode one layer, answering the resulting [ByteArray].  In particular,
	 * decode the input bytes as UTF-8, make sure no char in the resulting
	 * string is outside the range U+0000 – U+00FF, then output each char as a
	 * byte in the output.
	 *
	 * @param input
	 *   The input [ByteArray].
	 * @return
	 *   The output [ByteArray].
	 * @throws Exception
	 *   If the input [ByteArray] does not have the expected form.
	 */
	@Throws(Exception::class)
	private fun stripUTF8(input: ByteArray): ByteArray {
		val decoder = UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT)
		val sourceBuffer = ByteBuffer.wrap(input)
		val string = try {
			decoder.decode(sourceBuffer)
		}
		catch (e: Throwable) {
			throw Exception(
				"at position ${sourceBuffer.position()}, ${e.message}")
		}
		// Check that all characters are bytes (U+0000 – U+00FF), and write
		// each character as an individual byte to the outputBuffer.
		val targetBuffer = ByteBuffer.allocate(string.length)
		string.indices.forEach { i ->
			val ch = string[i]
			if (ch > '\u00ff') {
				// Figure out where it was in the input by back-translating
				// the prefix.
				val pos = string.substring(0, i).toByteArray(UTF_8).size
				throw Exception(
					"at position $pos, invalid encoding ($ch).")
			}
			targetBuffer.put(ch.toByte())
		}
		assert(targetBuffer.position() == string.length)
		return targetBuffer.array()
	}

	/** An enumeration of values that can be produced by this program. */
	enum class ProcessResult(val value: Int) {
		/**
		 * The process completed normally.  This is what is automatically
		 * returned by successful completion of Java's main().
		 */
		@Suppress("unused")
		OK(0),

		/**
		 * The process detected a configuration error, and exited before
		 * producing any normal output or side-effect.
		 */
		CONFIGURATION_ERROR(1),

		/** The process failed during execution of the command. */
		OTHER_ERROR(2)
	}

	/**
	 * The entry point for command-line invocation of the indexed file analyzer.
	 *
	 * @param args
	 *   The command-line arguments.
	 */
	@JvmStatic
	fun main(args: Array<String>)
	{
		try {
			// Configure the analyzer according to the command-line arguments
			// and ensure that any supplied paths are syntactically valid.
			configuration = configure(args)
			with(configuration) {
				if (implodeDirectory !== null)
				{
					assert(inputFile === null)
					assert(implodeHeader !== null)
					assert(implodeOutput !== null)
					val dir = implodeDirectory!!
					// The default option file is used as the output.
					val outputFile = implodeOutput!!
					if (outputFile.exists())
					{
						throw Exception(
							"Output file must not already exist")
					}
					val files = dir.list()!!.toMutableList()
					val metadata = when
					{
						files.remove("metadata") -> "metadata"
						files.remove("metadata.txt") -> "metadata.txt"
						else -> null
					}
					val regex = "^(\\d+)(\\.txt)?$".toRegex(IGNORE_CASE)
					files.forEach {
						if (!regex.matches(it))
						{
							throw Exception(
								"Unexpected file '$it'. Implode directory "
									+ "entries must be numeric, or numeric + "
									+ "'.txt', and contiguous, starting at 0. "
									+ "'metadata' or 'metadata.txt' is also "
									+ "supported.")
						}
					}
					files.sortBy { regex.replace(it, "$1").toInt() }
					for (i in files.indices)
					{
						val name = files[i]
						if (regex.replace(name, "$1").toInt() != i)
						{
							throw Exception(
								"Cannot find file '$i' or '$i.txt'.")
						}
					}
					val indexedFileBuilder =
						object : IndexedFileBuilder(implodeHeader!!) { }
					val out = indexedFileBuilder.openOrCreate(
						outputFile, true)
					files.forEach { out.add(dir.resolve(it).readBytes()) }
					metadata?.let {
						out.metadata = dir.resolve(it).readBytes()
					}
					out.commit()
					return
				}
				val builder = ArbitraryIndexedFileBuilder(inputFile!!)
				indexedFile = builder.openOrCreate(inputFile!!, false)
				val indices = indices()
				when {
					patchOutputFile !== null ->
					{
						// Patch the file into an output file by transforming
						// each record, stripping one level of UTF-8 encoding.
						// Fail and delete the destination file if any record
						// decodes to a string with characters outside U+0000 -
						// U+00FF.
						assert(!patchOutputFile!!.exists())
						val outputFile = builder.openOrCreate(
							patchOutputFile!!, true)
						try {
							indices.forEach { recordNumber ->
								val sourceRecord = indexedFile[recordNumber]
								val targetRecord = try {
									stripUTF8(sourceRecord)
								} catch (e: Exception) {
									throw Exception(
										"In record $recordNumber, ${e.message}")
								}
								outputFile.add(targetRecord)
							}
							val metadata = indexedFile.metadata
							if (metadata !== null) {
								outputFile.metadata = metadata
							}
							outputFile.commit()
							outputFile.close()
							// Success!
						} catch (e: Exception) {
							System.err.println(e.message)
							outputFile.close()
							patchOutputFile!!.delete()
							throw e
						}
					}
					explodeDirectory !== null ->
					{
						// Explode records into files.
						val dir = explodeDirectory!!
						val suffix = if (text) ".txt" else ""
						dir.mkdirs()
						indices.forEach {
							dir.resolve("$it$suffix")
								.writeBytes(indexedFile[it])
						}
						if (metadata) {
							val metadataBytes = indexedFile.metadata
							if (metadataBytes !== null) {
								dir.resolve("metadata$suffix")
									.writeBytes(metadataBytes)
							}
						}
					}
					counts && !binary && !text && !sizes ->
					{
						// Output a raw count, a linefeed, and nothing else.
						assert(!metadata)
						println(indices.count())
					}
					counts || sizes || binary || text ->
					{
						// Output a stream of records.
						indices.forEach {
							writeRecord(it, System.out)
						}
						if (metadata) writeRecord(-1L, System.out)
					}
				}
			}
		}
		catch (e: ConfigurationException) {
			// The command-line arguments were malformed, or
			// The arguments specified a missing file.
			System.err.println(e.message)
			exitProcess(CONFIGURATION_ERROR.value)
		}
		catch (e: Exception) {
			System.err.println(e.message)
			exitProcess(OTHER_ERROR.value)
		}
	}
}
