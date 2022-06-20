/*
 * OptionProcessorFactory.kt
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

package avail.tools.options

import avail.tools.options.OptionProcessorFactory.Cardinality.Companion.OPTIONAL
import avail.utility.configuration.Configuration
import java.util.EnumSet

/**
 * An `OptionProcessorFactory` enables a client to dynamically specify and
 * assemble an [option&#32;processor][OptionProcessor]. In particular, the
 * factory allows a client to flexibly define a particular option processor
 * while ignoring specification and evaluation order dependency. Validation is
 * postponed until final assembly time, at which time a `[ValidationException]`
 * will be thrown in the event of incorrect or incomplete specification;
 * otherwise, the constructed option processor provably reflects the client
 * specification.
 *
 * @param OptionKeyType
 *   The type of the option.
 * @property optionKeyType
 *   The [type][Class] of option keys.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `OptionProcessorFactory`.
 *
 * @param optionKeyType
 *   The [type][Class] of option keys.
 */
class OptionProcessorFactory<OptionKeyType : Enum<OptionKeyType>>
	private constructor (
		private val optionKeyType: Class<OptionKeyType>)
{
	companion object {
		inline fun <reified O : Enum<O>> create(
			noinline setup: OptionProcessorFactory<O>.()->Unit
		): OptionProcessor<O> = privateCreate(O::class.java, setup)

		/**
		 * A helper factory function.  Unfortunately, Kotlin's visibility rules
		 * (effectively inherited from the JVM) for things accessed from inline
		 * function bodies prevents this from being hidden entirely.
		 */
		fun <O : Enum<O>> privateCreate(
			optionKeyType: Class<O>,
			setup: OptionProcessorFactory<O>.()->Unit
		): OptionProcessor<O> =
			with(OptionProcessorFactory(optionKeyType)) {
				setup()
				createOptionProcessor()
			}
	}

	/** The [set][Set] of all [options][Option]. */
	private val allOptions = mutableSetOf<Option<OptionKeyType>>()

	/** The rules to check, in order, for valid options and arguments. */
	private val allRules =
		mutableListOf<OptionProcessor<OptionKeyType>.()->Unit>()

	/** A helper class to make option bodies more directly expressible. */
	data class OptionInvocation<OptionKeyType : Enum<OptionKeyType>>(
		val processor: OptionProcessor<OptionKeyType>,
		val keyword: String)

	/**
	 * A helper class to make bodies of options with arguments more directly
	 * expressible.
	 */
	data class OptionInvocationWithArgument<OptionKeyType : Enum<OptionKeyType>>
	(
		val processor: OptionProcessor<OptionKeyType>,
		val keyword: String,
		val argument: String)

	/**
	 * An optional parameter when defining an [option] or [optionWithArgument],
	 * to indicate how many occurrences of the option are permitted/required.
	 * For more precise control, the option body can use
	 * [OptionProcessor.checkEncountered] during processing, or define a [rule].
	 */
	@Suppress("unused")
	data class Cardinality(val min: Int, val max: Int) {
		companion object {
			/** The option may occur zero or one times. */
			val OPTIONAL = Cardinality(0, 1)

			/** The option must be present. */
			val MANDATORY = Cardinality(1, 1)

			/** The option may occur zero or more times. */
			val ZERO_OR_MORE = Cardinality(0, Int.MAX_VALUE)

			/** The option must occur at least once. */
			val ONE_OR_MORE = Cardinality(1, Int.MAX_VALUE)
		}
	}

	/** Declare a generic option that takes no argument. */
	fun option(
		key: OptionKeyType,
		keywords: Collection<String>,
		description: String,
		cardinality: Cardinality = OPTIONAL,
		action: OptionInvocation<OptionKeyType>.() -> Unit
	) = allOptions.add(
		GenericOption(key, keywords, cardinality, description, action, null))

	/** Declare a generic option that takes an argument. */
	fun optionWithArgument(
		key: OptionKeyType,
		keywords: Collection<String>,
		description: String,
		cardinality: Cardinality = OPTIONAL,
		action2: OptionInvocationWithArgument<OptionKeyType>.() -> Unit
	) = allOptions.add(
		GenericOption(key, keywords, cardinality, description, null, action2))

	/**
	 * Add the default option, which is what to do with bare arguments that have
	 * no argument-requiring option preceding them.
	 */
	fun defaultOption(
		key: OptionKeyType,
		description: String,
		cardinality: Cardinality = OPTIONAL,
		action2: OptionInvocationWithArgument<OptionKeyType>.() -> Unit
	) = allOptions.add(DefaultOption(key, cardinality, description, action2))

	/**
	 * Add the default help option, bound to '-?'.  After the preamble, it
	 * outputs the keywords and description for each option.
	 */
	fun helpOption(
		key: OptionKeyType,
		preamble: String,
		appendable: Appendable
	) = allOptions.add(GenericHelpOption(key, preamble, appendable))

	/**
	 * Add a rule.  All rules run, in the order in which they were defined via
	 * this method.  If a rule discovers an invalid combination of options and
	 * arguments, it should throw a suitable [OptionProcessingException] whose
	 * message text is the [ruleText], which will be reported to the user.
	 *
	 * Each option is first given the chance to report problems related to only
	 * that option, but after that all rules run, allowing consistency checks
	 * between options.
	 *
	 * @param
	 *   The text to display if the rule fails.
	 * @param
	 *   The rule's body, which returns `true` iff the rule is satisfied.
	 */
	@Throws(OptionProcessingException::class)
	fun <C : Configuration> C.rule(
		ruleText: String,
		rule: C.() -> Boolean
	) = allRules.add {
		if (!rule()) throw OptionProcessingException(ruleText)
	}

	/**
	 * Check that the resulting [option&#32;processor][OptionProcessor] will
	 * have no defects. In particular:
	 *
	 * * Every option key uniquely specifies an option.
	 * * Every keyword must uniquely indicate an option key.
	 * * All [options][Option] must have non-empty
	 *   [description][Option.description].
	 * * All option keys must be bound to options.
	 *
	 * @throws ValidationException
	 *   If the specified [option&#32;processor][OptionProcessor] fails
	 *   validation for any reason.
	 */
	@Throws(ValidationException::class)
	private fun validateFactory()
	{
		val optionKeys = EnumSet.noneOf(optionKeyType)
		allOptions.forEach {
			if (optionKeys.contains(it.key))
			{
				throw ValidationException(
					"some option keys of "
					+ optionKeyType.canonicalName
					+ " do not uniquely specify options")
			}
			optionKeys.add(it.key)
		}

		val keywords = mutableSetOf<String>()
		allOptions.forEach { option ->
			option.keywords.forEach { keyword ->
				if (keywords.contains(keyword))
				{
					throw ValidationException(
						"some option keywords do not uniquely indicate " +
						"option keys of ${optionKeyType.canonicalName}")
				}
				keywords.add(keyword)
			}
		}

		allOptions.forEach { option ->
			if (option.description.isEmpty())
			{
				throw ValidationException(
					"some options have empty end-user descriptions")
			}
		}

		if (allOptions.size != optionKeyType.enumConstants.size)
		{
			throw ValidationException(
				"some option keys of "
				+ optionKeyType.canonicalName
				+ " are not bound to options")
		}
	}

	/**
	 * Create an instance of the [option&#32;processor][OptionProcessor]
	 * described by the receiver.
	 *
	 * @return
	 *   The new validated [option&#32;processor][OptionProcessor].
	 * @throws ValidationException
	 *   If validation fails.
	 */
	@Throws(ValidationException::class)
	private fun createOptionProcessor(): OptionProcessor<OptionKeyType>
	{
		validateFactory()
		val keywords = mutableMapOf<String, OptionKeyType>()
		allOptions.forEach { option ->
			option.keywords.forEach { keyword ->
				keywords[keyword] = option.key
			}
		}
		return OptionProcessor(optionKeyType, keywords, allOptions, allRules)
	}
}
