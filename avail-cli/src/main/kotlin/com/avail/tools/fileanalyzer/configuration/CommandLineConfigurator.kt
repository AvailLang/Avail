/*
 * CommandLineConfigurator.kt
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

package com.avail.tools.fileanalyzer.configuration

import com.avail.tools.compiler.configuration.CommandLineConfigurator
import com.avail.tools.fileanalyzer.IndexedFileAnalyzer
import com.avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.*
import com.avail.tools.options.OptionProcessingException
import com.avail.tools.options.OptionProcessor
import com.avail.tools.options.OptionProcessorFactory
import com.avail.tools.options.OptionProcessorFactory.Cardinality
import com.avail.utility.configuration.ConfigurationException
import com.avail.utility.configuration.Configurator
import java.io.File
import java.lang.Long.parseLong

/**
 * Provides the configuration for the [IndexedFileAnalyzer]. Specifies the
 * options that are available as arguments to the analyzer and their effects
 * on the output.
 *
 * @property helpStream
 *   The [appendable][Appendable] to which help text should be written.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `CommandLineConfigurator`.
 *
 * @param configuration
 *   The base [IndexedFileAnalyzerConfiguration].
 * @param commandLineArguments
 *   The command-line arguments.
 * @param helpStream
 *   The [Appendable] to which help text should be written.
 */
class CommandLineConfigurator constructor(
	override val configuration: IndexedFileAnalyzerConfiguration,
	commandLineArguments: Array<String>,
	private val helpStream: Appendable
) : Configurator<IndexedFileAnalyzerConfiguration>
{
	/** The command line arguments.  */
	private val commandLineArguments = commandLineArguments.clone()

	/** Has the [configurator][CommandLineConfigurator] been run yet? */
	private var isConfigured = false

	/**
	 * `OptionKey` enumerates the valid configuration options.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	internal enum class OptionKey
	{
		/**
		 * Whether to show the record number before each record's content.  If
		 * neither [SIZES] nor [BINARY] nor [TEXT] is specified, only an
		 * undecorated count of the number of eligible records is output.  In
		 * that case, [METADATA] must not be specified.
		 */
		COUNTS,

		/**
		 * Whether to show record size information before each record's content.
		 */
		SIZES,

		/**
		 * Whether to show the record contents as pairs of hexadecimal digits,
		 * 16 bytes to a line.  Can be combined with [TEXT].  If neither
		 * [BINARY] nor [TEXT] nor [EXPLODE] is specified, record contents are
		 * not output.  [COUNTS] and [SIZES] are still legal in that usage.
		 */
		BINARY,

		/**
		 * Whether to treat each record's content as bytes that encode a UTF-8
		 * string, and print that string.
		 *
		 * If combined with [BINARY], it shows 16 bytes of hex on the left, and
		 * the corresponding decoded *ASCII* characters on the right, using a
		 * '.' for anything outside the printable ASCII range (0x20 through
		 * 0x7E).
		 */
		TEXT,

		/**
		 * If specified, this names a directory to use or create.  Each record
		 * will be transferred without interpretation (i.e., the verbatim bytes)
		 * into a file within that directory, using the zero-based record number
		 * as the final component of the file name.
		 *
		 * If [METADATA] is specified, and if the input file contains metadata,
		 * a file "metadata" will also be created within the directory,
		 * containing the uninterpreted metadata bytes.
		 *
		 * Must not be used if [BINARY] and [TEXT] are also both used.  If used
		 * with [TEXT], the records and metadata file names will have ".txt"
		 * appended.
		 */
		EXPLODE,

		/**
		 * Whether to show the metadata content.  The [SIZES], [BINARY], and
		 * [TEXT] flags determine how to present the metadata.  If there is no
		 * metadata in the file, it is treated as though it were an empty
		 * sequence of bytes.
		 */
		METADATA,

		/**
		 * A non-negative [Long] that indicates the lowest zero-based record
		 * number to include in the output.  If there are no records having an
		 * index greater than or equal to this value, no records will be shown.
		 */
		LOWER,

		/**
		 * A [Long] ≥ -1, that indicates the highest zero-based record number to
		 * include in the output.  If there are no records having an index less
		 * than or equal to this value, no records will be shown.  If the
		 * maximum record number in the file is less than this value, no error
		 * will be produced, and that maximum record number will be used as the
		 * upper bound.
		 */
		UPPER,

		/** The (standard) option to show help.  Invoked with '-?'. */
		HELP,

		/** The indexed file to operate on. */
		INDEXED_FILE
	}

	/**
	 * Create an [option&32;processor][OptionProcessor] suitable for
	 * [updating][updateConfiguration] an
	 * [analyzer&32;configuration][IndexedFileAnalyzerConfiguration].
	 *
	 * @return
	 *   An option processor.
	 */
	private fun createOptionProcessor() =
		OptionProcessorFactory.create<OptionKey> {
			option(
				COUNTS,
				listOf("c", "counts"),
				"Whether to show 'Record=', the zero-based record number, and "
				+ "a linefeed prior to each record that is output.  If neither "
				+ "-s nor -b nor -t is specified, only the undecorated count "
				+ "of the number of eligible records is output.  In that case, "
				+ "-m is forbidden.")
				{
					configuration.counts = true
				}
			option(
				SIZES,
				listOf("s", "sizes"),
				"Whether to show record sizes prior to each record.")
				{
					configuration.sizes = true
				}
			option(
				BINARY,
				listOf("b", "binary"),
				"Whether to show each byte of records and/or metadata in "
				+ "hexadecimal.  Can be combined with -t.")
				{
					configuration.binary = true
				}
			option(
				TEXT,
				listOf("t", "text"),
				"Whether to treat each record as a UTF-8 encoded Unicode "
				+ "string, and output it as text.  If combined with -b, the "
				+ "left side contains hex bytes and the right side contains "
				+ "printable ASCII characters (0x20-0x7E) or a '.'.")
				{
					configuration.text = true
				}
			optionWithArgument(
				EXPLODE,
				listOf("x", "explode"),
				"A directory to use or create, into which records and/or "
				+ "metadata will be written in separate files.  If -t is "
				+ "specified, '.txt' will be appended to the filenames.")
				{
					configuration.explodeDirectory = File(argument)
				}
			option(
				METADATA,
				listOf("m", "metadata"),
				"Whether to process metadata of the input file.  If specified, "
				+ "and if the metadata is present and non-empty, the metadata "
				+ "will be processed in the same way as the records, after the "
				+ "last record, if any.")
				{
					configuration.metadata = true
				}
			optionWithArgument(
				LOWER,
				listOf("l", "lower"),
				"The lowest zero-based record number that may be processed.")
				{
					try
					{
						// Note: parseLong will (also) throw an exception if
						// it tries to parse "" as a result of the illegal
						// use of "=" without any items following.
						val value = parseLong(argument)
						if (value < 0L)
						{
							throw OptionProcessingException(
								"$keyword: Argument must be ≥ 0")
						}
						configuration.lower = value
					}
					catch (e: NumberFormatException)
					{
						throw OptionProcessingException(
							"$keyword: Illegal argument.", e)
					}
				}
			optionWithArgument(
				UPPER,
				listOf("u", "upper"),
				"The highest zero-based record number that may be processed.")
				{
					try
					{
						// Note: parseLong will (also) throw an exception if
						// it tries to parse "" as a result of the illegal
						// use of "=" without any items following.
						val value = parseLong(argument)
						if (value < -1L)
						{
							throw OptionProcessingException(
								"$keyword: Argument must be ≥ -1")
						}
						configuration.upper = value
					}
					catch (e: NumberFormatException)
					{
						throw OptionProcessingException(
							"$keyword: Illegal argument.", e)
					}
				}
			helpOption(
				HELP,
				"The IndexedFileAnalyzer understands these options:",
				helpStream)
			defaultOption(
				INDEXED_FILE,
				"The indexed file to analyze.",
				Cardinality.MANDATORY)
				{
					try
					{
						if (!File(argument).isFile)
						{
							throw OptionProcessingException(
								"File not found, or directory was specified")
						}
						configuration.inputFile = File(argument)
					}
					catch (e: OptionProcessingException)
					{
						throw OptionProcessingException(
							"«default»: ${e.message}",
							e)
					}
				}
			configuration.rule("No input file was specified"
			) {
				inputFile != null
			}
			configuration.rule(
				"--explode cannot produce --binary + --text combination"
			) {
				// Since bytes are transferred verbatim during an explode, and
				// the text flag only affects the file name, it would be
				// misleading to allow both binary and text to be set.
				explodeDirectory == null || !binary || !text
			}
			configuration.rule(
				"If only --counts are requested (not --sizes, -binary, or "
				+ "--text, then --metadata must not be specified"
			) {
				!(counts && !sizes && !binary && !text && metadata)
			}
		}

	@Synchronized @Throws(ConfigurationException::class)
	override fun updateConfiguration()
	{
		if (!isConfigured)
		{
			try
			{
				createOptionProcessor().processOptions(commandLineArguments)
				isConfigured = true
			}
			catch (e: Exception)
			{
				throw ConfigurationException(
					"configuration error: " + e.message, e)
			}
		}
	}
}
