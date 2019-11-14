/*
 * OptionProcessorFactory.kt
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

import java.util.*

/**
 * An `OptionProcessorFactory` enables a client to dynamically specify and
 * assemble an [option processor][OptionProcessor]. In particular, the factory
 * allows a client to flexibly define a particular option processor while
 * ignoring specification and evaluation order dependency. Validation is
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
class OptionProcessorFactory<OptionKeyType : Enum<OptionKeyType>> constructor(
	private val optionKeyType: Class<OptionKeyType>)
{
	/** The [set][Set] of all [options][Option].  */
	private val allOptions = HashSet<Option<OptionKeyType>>()

	/**
	 * Configure the [receiver][OptionProcessorFactory].
	 *
	 * @param configurator
	 *   A function that will configure the receiver.
	 */
	fun configure(
			configurator: OptionProcessorFactory<OptionKeyType>.() -> Unit) =
		configurator()

	/**
	 * Add a new [option][Option].
	 *
	 * @param option
	 *   An [option][Option].
	 */
	fun addOption(option: Option<OptionKeyType>)
	{
		assert(!allOptions.contains(option))
		allOptions.add(option)
	}

	/**
	 * Check that the resulting [option processor][OptionProcessor] will have no
	 * defects. In particular:
	 *
	 * * Every option key uniquely specifies an option.
	 * * Every keyword must uniquely indicate an option key.
	 * * All [options][Option] must have non-empty
	 *   [description][Option.description].
	 * * All option keys must be bound to options.
	 *
	 * @throws ValidationException
	 *   If the specified [option processor][OptionProcessor] fails validation
	 *   for any reason.
	 */
	@Throws(ValidationException::class)
	private fun validate()
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

		val keywords = HashSet<String>()
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
	 * Create an instance of the [option processor][OptionProcessor]
	 * described by the receiver.
	 *
	 * @return
	 *   The new validated [option processor][OptionProcessor].
	 * @throws ValidationException
	 *   If validation fails.
	 */
	@Throws(ValidationException::class)
	fun createOptionProcessor(): OptionProcessor<OptionKeyType>
	{
		validate()
		val keywords = HashMap<String, OptionKeyType>()
		allOptions.forEach { option ->
			option.keywords.forEach { keyword ->
				keywords[keyword] = option.key
			}
		}
		return OptionProcessor(optionKeyType, keywords, allOptions)
	}
}
