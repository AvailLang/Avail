/*
 * OptionProcessor.kt
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

package com.avail.tools.options

import com.avail.tools.options.OptionProcessorFactory.Cardinality
import com.avail.tools.options.OptionProcessorFactory.OptionInvocation
import com.avail.tools.options.OptionProcessorFactory.OptionInvocationWithArgument
import com.avail.utility.CollectionExtensions.populatedEnumMap
import com.avail.utility.MutableInt
import com.avail.utility.ParagraphFormatter
import com.avail.utility.ParagraphFormatterStream
import java.io.IOException
import java.util.*

/**
 * An `OptionProcessor` serves primarily to support command-line argument
 * processing, but may occasionally prove useful in other circumstances.
 *
 * An option processor is parametric on the type of its option keys. The
 * client provides this information for maximum type-safety and code-reuse.
 *
 * The sole public protocol of an option processor is [processOptions]. It
 * accepts an array of [String]s which it treats as containing separate option
 * strings. It extracts a keyword and an optional argument. The keyword is
 * mapped to a client-specified action; the argument will be passed to this
 * action upon positive identification of the option by keyword. `null` will be
 * passed in lieu of an argument if the argument was omitted.
 *
 * Option strings may have the following forms:
 *
 *  * Begins with "-" (hyphen): Each subsequent character is treated as a
 *    distinct short option keyword. Short option keywords associate `null`
 *    arguments with their actions.
 *  * Begins with "--" (double hyphen): The subsequent characters up to an
 *    "=" (equals) are treated collectively as a long option keyword. If no
 *    equals is discovered, then the action associated with the option is
 *    performed with a `null` argument; otherwise any characters following the
 *    equals are treated collectively as the argument of that action.
 *  * Entirety is "--" (double hyphen): Disable special processing of hyphen
 *    prefixes. All further option strings are treated as unprefixed.
 *  * Does not begin with "-" (hyphen): It is treated as the argument of the
 *    action associated with the default option. An option processor supports a
 *    single default option. It is distinguished in that one of its keywords is
 *    the empty string.
 *
 * A new option processor is obtainable via an appropriately parameterized
 * [factory][OptionProcessorFactory]. This allows incremental and
 * arbitrary-order specification of the option processor independent of any
 * runtime assembly constraints.
 *
 * @param OptionKeyType
 *   The type of the option.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `OptionProcessor`.
 *
 * @param optionKeyType
 *   The [type][Class] of option keys.
 * @param keywords
 *   The complete mapping of recognizable keywords to option keys.
 * @param options
 *   The complete [collection][Collection] of [options][Option].
 * @param rules
 *   The rules to run after all options have been processed, if each minimum
 *   [Cardinality] is satisfied.
 */
@Suppress("MemberVisibilityCanBePrivate")
class OptionProcessor<OptionKeyType : Enum<OptionKeyType>> internal constructor(
	optionKeyType: Class<OptionKeyType>,
	keywords: Map<String, OptionKeyType>,
	options: Collection<Option<OptionKeyType>>,
	val rules: Collection<OptionProcessor<OptionKeyType>.()->Unit>)
{
	/**
	 * A mapping between recognizable keywords and the option keys that they
	 * indicate.
	 */
	private val allKeywords = HashMap(keywords)

	/** A mapping between option keys and [options][Option].  */
	private val allOptions: EnumMap<OptionKeyType, Option<OptionKeyType>> =
		options.associateByTo(EnumMap(optionKeyType)) { it.key }

	/** A mapping from option key to times encountered during processing.  */
	private val timesEncountered: EnumMap<OptionKeyType, MutableInt> =
		populatedEnumMap(optionKeyType) { MutableInt(0) }

	/**
	 * Perform the action associated with the option key bound to the specified
	 * keyword.
	 *
	 * @param keyword
	 *   A potential keyword of the `OptionProcessor receiver`.
	 * @param argument
	 *   The argument associated with the keyword, if any.
	 * @throws UnrecognizedKeywordException
	 *   If the specified keyword was unrecognized.
	 * @throws OptionProcessingException
	 *   If any exception occurs during option processing.
	 */
	@Throws(OptionProcessingException::class)
	private fun performKeywordAction(keyword: String, argument: String? = null)
	{
		val optionKey = allKeywords[keyword]
			?: throw UnrecognizedKeywordException(keyword)
		val option = allOptions[optionKey]!!
		timesEncountered[optionKey]!!.value++
		// Make sure we haven't exceeded the maximum count.
		checkEncountered(optionKey, option.cardinality.max)
		if (option.takesArgument) {
			argument ?: throw MissingArgumentException(keyword)
			with(OptionInvocationWithArgument(this, keyword, argument)) {
				option.action2!!()
			}
		}
		else {
			if (argument !== null)
			{
				throw InvalidArgumentException(keyword)
			}
			with(OptionInvocation(this, keyword)) {
				option.action!!()
			}
		}
	}

	/**
	 * Process the argument as a long keyword, potentially one with an
	 * associated argument.
	 *
	 * @param keyword
	 *   A long keyword -- if this is empty, then it denotes the end of keyword
	 *   processing.
	 * @throws OptionProcessingException
	 *   If any exception occurs during option processing.
	 */
	@Throws(OptionProcessingException::class)
	private fun processLongKeyword(keyword: String)
	{
		// If we have encountered a bare double hyphen, then discontinue keyword
		// processing. All remaining option strings will be passed to the
		// designated default action (and potentially throw exceptions if no
		// such action has been defined).
		if (keyword.isEmpty())
		{
			continueProcessingKeywords.set(false)
		}
		else
		{
			val index = keyword.indexOf('=')
			if (index == -1)
			{
				// If we didn't find an equals, then treat the keyword as having
				// no argument.
				performKeywordAction(keyword, null)
			}
			else
			{
				// If we found an equals, then treat the right-hand side as an
				// argument associated with the keyword.
				performKeywordAction(
					keyword.substring(0, index),
					keyword.substring(index + 1))
			}
		}
	}

	/**
	 * Process the argument as a collection of single-character keywords.
	 *
	 * @param keywords
	 *   The keywords.
	 * @param argumentIterator
	 *   An iterator over the option and argument strings, to consume an
	 *   argument if needed.
	 * @throws OptionProcessingException
	 *   If any exception occurs during option processing.
	 */
	@Throws(OptionProcessingException::class)
	private fun processShortKeywords(
		keywords: String,
		argumentIterator: Iterator<String>
	) {
		for (i in 0..keywords.length - 2) {
			val keyword = keywords.substring(i, i + 1)
			val optionKey = allKeywords[keyword]
				?: throw UnrecognizedKeywordException(keyword)
			val option = allOptions[optionKey]!!
			if (option.takesArgument) {
				throw OptionProcessingException(
					"\"$keyword\" requires an argument, so it may only be at "
						+ "the end of a grouped option set (e.g, the z in "
						+ "'-xyz').  Perhaps two dashes were intended.")
			}
			performKeywordAction(keyword)
		}
		val keyword = keywords.takeLast(1)
		val optionKey = allKeywords[keyword]
			?: throw UnrecognizedKeywordException(keyword)
		val option = allOptions[optionKey]!!
		var argument: String? = null
		if (option.takesArgument)
		{
			// The last single-character keyword in a group (e.g., the z
			// keyword in "-xyz") may have an argument following it,
			// after a space.  We diverge from GNU's awful practice of
			// omitting the space if the argument is mandatory, leading
			// to the need to know whether -f's argument is mandatory to
			// know whether this means "-f ob" or "-f -o -b" (or even
			// "-f -o b").
			if (!argumentIterator.hasNext()) {
				throw MissingArgumentException(keyword)
			}
			argument = argumentIterator.next()
			if (argument.take(1) == "-")
			{
				throw MissingArgumentException(keyword)
			}
		}
		performKeywordAction(keyword, argument)
	}

	/**
	 * Perform the default action (associated with the empty keyword) with the
	 * specified argument.
	 *
	 * @param argument
	 *   The argument for the default action.
	 * @throws OptionProcessingException
	 *   If any exception occurs during option processing.
	 */
	@Throws(OptionProcessingException::class)
	private fun performDefaultAction(argument: String) =
		performKeywordAction("", argument)

	/**
	 * Process the specified string, including any option keyword prefix.
	 *
	 * @param string
	 *   An option string.
	 * @throws OptionProcessingException
	 *   If any exception occurs during option processing.
	 */
	@Throws(OptionProcessingException::class)
	private fun processString(
		string: String,
		argumentIterator: Iterator<String>
	) = when {
		string.take(1) != "-" ->
			// Treat the string as implicitly associated with the default
			// action.
			performDefaultAction(string)
		string == "-" ->
			// If there are no further characters, then the string is malformed.
			throw OptionProcessingException(
				"option syntax error -- bare hyphen encountered")
		string.take(2) == "--" ->
			// Two hyphens introduces a long option keyword.
			processLongKeyword(string.substring(2))
		else ->
			// A single hyphen introduces a set of short option keywords, the
			// last of which may take an argument.
			processShortKeywords(string.substring(1), argumentIterator)
	}

	/**
	 * Treat the specified array as containing option strings. Process each
	 * option string separately, synchronously executing the action associated
	 * with it upon its discovery.
	 *
	 * This operation is safe for concurrent access.
	 *
	 * @param strings
	 *   The option strings.
	 * @throws OptionProcessingException
	 *   If any exception occurs during option processing.
	 */
	@Throws(OptionProcessingException::class)
	fun processOptions(strings: Array<String>)
	{
		// Rather than using monitors to support concurrent access, we use a
		// single ThreadLocal to track keyword processing.
		try
		{
			// First, parse all options (checking maximum cardinalities).
			val iterator = strings.iterator()
			iterator.forEach {
				if (continueProcessingKeywords.get())
				{
					processString(it, iterator)
				}
				else
				{
					performDefaultAction(it)
				}
			}
			// Check the minimum cardinality of each option.
			allOptions.values.forEach {
				val actual = timesEncountered(it.key)
				with(it.cardinality) {
					if (actual < min) {
						val atLeast =
							when (min) { max -> "exactly" else -> "at least " }
						val actualTimes =
							when (actual) { 1 -> "time" else -> "times" }
						val minTimes =
							when (min) { 1 -> "time" else -> "times" }
						throw OptionProcessingException(
							"${it.key}: encountered only $actual $actualTimes, "
								+ "but must occur $atLeast $min $minTimes")
					}
				}
			}
			// Finally, run each rule.
			rules.forEach { this.it() }
		}
		finally
		{
			// Any particular thread is unlikely to process options again, so
			// make the ThreadLocal available for garbage collection.
			continueProcessingKeywords.remove()
		}
	}

	/**
	 * Answer the number of times that the [option][Option] indicated by the
	 * specified key has been processed already.
	 *
	 * @param key
	 *   An option key.
	 * @return
	 *   The number of times that the option has been processed.
	 */
	fun timesEncountered(key: OptionKeyType) = timesEncountered[key]!!.value

	/**
	 * If the specified key was encountered more times than allowed, then throw
	 * an [OptionProcessingException]. A key is considered encountered *before*
	 * running any user supplied action for it.
	 *
	 * @param key
	 *   An option key.
	 * @param timesAllowed
	 *   The maximum number of times that the option key may be specified.
	 * @throws OptionProcessingException
	 *   If `key` was processed more than `timesAllowed` times.
	 */
	@Throws(OptionProcessingException::class)
	fun checkEncountered(key: OptionKeyType, timesAllowed: Int)
	{
		val timesActuallyEncountered = timesEncountered(key)
		if (timesEncountered(key) > timesAllowed)
		{
			throw OptionProcessingException(String.format(
				"%s: encountered %d time(s), but allowed only %d time(s)",
				key,
				timesActuallyEncountered + 1, // Doesn't include latest one.
				timesAllowed + 1))  // Doesn't include latest one.
		}
	}

	/**
	 * Write the [descriptions][Option.description] of the [options][Option]
	 * defined by the `OptionProcessor option processor` into the specified
	 * [Appendable].
	 *
	 * @param appendable
	 *   An [Appendable].
	 * @throws IOException
	 *   If an [I/O&#32;exception][IOException] occurs.
	 */
	@Throws(IOException::class)
	fun writeOptionDescriptions(appendable: Appendable)
	{
		// Write the descriptions of the options onto the specified Appendable
		// in the order in which they are specified in the source file.
		allOptions.values.forEach { option ->
			// Keywords should be left-justified within the 80 char window.
			val keywordFormatter = ParagraphFormatter(80)
			val keywordStream =
				ParagraphFormatterStream(keywordFormatter, appendable)
			// Descriptions should be indented by 4 spaces.
			val descriptionFormatter = ParagraphFormatter(80, 4, 4)
			val descriptionStream =
				ParagraphFormatterStream(descriptionFormatter, appendable)
			val keywords = LinkedHashSet(option.keywords)
			keywords.forEach { keyword ->
				if (option is DefaultOption<*>)
				{
					keywordStream.append("<bareword>\n")
				}
				else
				{
					keywordStream.append(String.format(
						"%s%s%n",
						if (keyword.length == 1) "-" else "--",
						keyword))
				}
			}
			descriptionStream.append(
				String.format("%s%n%n", option.description))
		}
	}

	companion object
	{
		/** Continue processing keywords?  */
		private val continueProcessingKeywords =
			ThreadLocal.withInitial { true }
	}
}
