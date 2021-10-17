/*
 * CommandLineConfigurator.kt
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

package avail.tools.fileanalyzer.configuration

import avail.tools.fileanalyzer.IndexedFileAnalyzer
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.BINARY
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.COUNTS
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.EXPLODE
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.HELP
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.IMPLODE
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.IMPLODE_HEADER
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.IMPLODE_OUTPUT
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.INDEXED_FILE
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.LOWER
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.METADATA
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.PATCH_RECORDS_STRIPPING_UTF8
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.SIZES
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.TEXT
import avail.tools.fileanalyzer.configuration.CommandLineConfigurator.OptionKey.UPPER
import avail.tools.options.OptionProcessingException
import avail.tools.options.OptionProcessor
import avail.tools.options.OptionProcessorFactory
import avail.tools.options.OptionProcessorFactory.Cardinality
import avail.utility.configuration.ConfigurationException
import avail.utility.configuration.Configurator
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
	/** The command line arguments. */
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
		 * Specifies a directory to read, from which records and/or metadata
		 * will be retrieved and written to a new index file.  The directory is
		 * expected to contain files with names '0' or '0.txt', '1' or '1.txt',
		 * etc., and optionally either 'metadata' or 'metadata.txt'.  If the
		 * numbered files do not form a contiguous range starting at 0, or if
		 * there are files in the directory that do not match this pattern, fail
		 * without creating the indexed file.
		 *
		 * This option is mutually exclusive of all others except
		 * [IMPLODE_HEADER] and [IMPLODE_OUTPUT], which are required if
		 * `IMPLODE` occurs.
		 */
		IMPLODE,

		/**
		 * Specifies the header string to encode at the start of the
		 * indexed file being created by an [IMPLODE] command.
		 */
		IMPLODE_HEADER,

		/**
		 * Specifies the output file to be created by the [IMPLODE] command.  It
		 * must not already exist.
		 */
		IMPLODE_OUTPUT,

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

		/**
		 * This option corrects an existing indexed file in-place (by using a
		 * temporary file and renaming).  The adjustment is to strip a layer of
		 * UTF-8 encoding from each record.  In particular, each record has been
		 * double-encoded as UTF-8, meaning the bytes of the record produce a
		 * sequence of Unicode characters which all happen to be in the Latin-1
		 * range (U+0000-U+00FF).  These bytes can themselves be interpreted as
		 * a UTF-8 stream, producing an arbitrary Unicode string.  Strip off the
		 * first encoding, and re-write each record with the correctly UTF-8
		 * encoded string.
		 */
		PATCH_RECORDS_STRIPPING_UTF8,

		/** The (standard) option to show help.  Invoked with '-?'. */
		HELP,

		/** The indexed file to operate on. */
		INDEXED_FILE
	}

	/**
	 * Create an [option&#32;processor][OptionProcessor] suitable for
	 * [updating][updateConfiguration] an
	 * [analyzer&#32;configuration][IndexedFileAnalyzerConfiguration].
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
			optionWithArgument(
				IMPLODE,
				listOf("implode"),
				"Specifies a directory to read, from which records and/or "
				+ "metadata will be retrieved and written to a new index "
				+ "file.  The directory is expected to contain files with "
				+ "names '0' or '0.txt', '1' or '1.txt', etc., and optionally "
				+ "either 'metadata' or 'metadata.txt'.  If the numbered files "
				+ "do not form a contiguous range starting at 0, or if there "
				+ "are files in the directory that do not match this pattern, "
				+ "the command will fail without creating the file.")
			{
				configuration.implodeDirectory = File(argument)
			}
			optionWithArgument(
				IMPLODE_HEADER,
				listOf("implode-header"),
				"Specifies the header string that identifies the type of "
					+ "indexed file that should be created via the 'implode' "
					+ "command.  This option must be provided whenever"
					+ "'implode' is specified, and vice-versa.")
			{
				configuration.implodeHeader = argument
			}
			optionWithArgument(
				IMPLODE_OUTPUT,
				listOf("implode-output"),
				"Specifies the name of the indexed file that should be created "
					+ "by the --implode command.  This option must be provided "
					+ "whenever 'implode' is specified, and vice-versa.")
			{
				configuration.implodeOutput = File(argument)
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
					// Note: parseLong will (also) throw an exception if it
					// tries to parse "" as a result of the illegal use of "="
					// without any items following.
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
					// Note: parseLong will (also) throw an exception if it
					// tries to parse "" as a result of the illegal use of "="
					// without any items following.
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
			optionWithArgument(
				PATCH_RECORDS_STRIPPING_UTF8,
				listOf("patch-utf-8"),
				"TEMPORARY FEATURE – Create a new file, named by the argument, "
				+ "unwrapping a layer of UTF-8 by decoding it as UTF-8 to "
				+ "a string, failing immediately if any character is not "
				+ "Latin-1 (U+0000 - U+00FF).  With each such string, "
				+ "write the characters with Latin-1 encoding, i.e., one "
				+ "byte per character.")
			{
				val file = File(argument)
				configuration.patchOutputFile = file
				if (file.exists()) throw OptionProcessingException(
					"Error - destination file ($argument) already exists.")
			}
			helpOption(
				HELP,
				"The IndexedFileAnalyzer understands these options:",
				helpStream)
			defaultOption(
				INDEXED_FILE,
				"The indexed file to analyze.",
				Cardinality.OPTIONAL)
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
			configuration.rule(
				"Neither an input file nor --implode was specified")
			{
				inputFile !== null || implodeDirectory !== null
			}
			configuration.rule(
				"--explode cannot produce --binary & --text combination")
			{
				// Since bytes are transferred verbatim during an explode, and
				// the text flag only affects the file name, it would be
				// misleading to allow both binary and text to be set.
				explodeDirectory === null || !binary || !text
			}
			configuration.rule(
				"If only --counts are requested (not --sizes, -binary, or "
				+ "--text, then --metadata must not be specified")
			{
				!(counts && !sizes && !binary && !text && metadata)
			}
			configuration.rule(
				"The '--implode' option, the '--implode-header' option, and "
				+ "--implode-output option must be used together, with no "
				+ "other options.")
			{
				when
				{
					implodeDirectory === null
						&& implodeHeader === null
						&& implodeOutput === null -> true
					implodeDirectory === null
						|| implodeHeader === null
						|| implodeOutput === null -> false
					counts || sizes || binary || text || metadata -> false
					lower !== null || upper !== null -> false
					inputFile !== null -> false
					explodeDirectory !== null -> false
					patchOutputFile !== null -> false
					else -> true
				}
			}
			configuration.rule(
				"The '--patch-utf-8' option is incompatible with --counts, "
				+ "--sizes, --binary, --text, --explode, or --metadata.")
			{
				patchOutputFile === null ||
					(!counts
						&& !sizes
						&& !binary
						&& !text
						&& explodeDirectory === null
						&& !metadata)
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
