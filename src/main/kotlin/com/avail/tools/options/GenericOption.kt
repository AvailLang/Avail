/*
 * GenericOption.kt
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
 * An implementation of [Option] whose accessible state is initialized during
 * construction.
 *
 * @param OptionKeyType
 *   The type of the option.
 * @property key
 *   The option key.
 * @property description
 *   An end-user comprehensible description of the [option][GenericOption].
 * @property action
 *   The action that should be performed upon setting of this
 *   [option][GenericOption].  The second parameter *may* be required to be
 *   `null` or required to be not `null`, depending on how this option was
 *   created.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new `GenericOption` that takes a [String] argument.
 *
 * @param key
 *   The option key.
 * @param keywords
 *   The keywords that indicate this `GenericOption`.
 * @param description
 *   A description of the `GenericOption`.
 * @param action
 *   The action that should be performed upon setting of this `GenericOption`.
 */
open class GenericOption<OptionKeyType : Enum<OptionKeyType>> constructor(
	override val key: OptionKeyType,
	keywords: Collection<String> = emptySet(),
	override val description: String,
	override val action:
		OptionProcessor<OptionKeyType>.(String, String?) -> Unit)
: Option<OptionKeyType>
{
	/**
	 * The [set][LinkedHashSet] of keywords that indicate this
	 * [option][GenericOption].
	 */
	override val keywords: LinkedHashSet<String> = LinkedHashSet(keywords)

	/**
	 * Construct a new [GenericOption] that takes no argument.
	 *
	 * @param key
	 *   The option key.
	 * @param keywords
	 *   The keywords that indicate this `GenericOption`.
	 * @param description
	 *   A description of the `GenericOption`.
	 * @param action
	 *   The action that should be performed upon setting of this
	 *   `GenericOption`.
	 */
	constructor(
		key: OptionKeyType,
		keywords: Collection<String> = emptySet(),
		description: String,
		action: OptionProcessor<OptionKeyType>.(String) -> Unit)
	: this(key, keywords, description, { keyword, forbidden ->
		if (forbidden !== null)
		{
			throw OptionProcessingException(
				"$keyword: An argument was specified, but none are "
				+ "permitted.")
		}
		action(keyword)
	})

	/**
	 * Construct a new {#code GenericOption} that takes no argument.
	 *
	 * @param key
	 *   The option key.
	 * @param keywords
	 *   The keywords that indicate this `GenericOption`.
	 * @param description
	 *   A description of the `GenericOption`.
	 * @param action1
	 *   The action that should be performed upon setting of this
	 *   `GenericOption` with no argument.
	 * @param action2
	 *   The action that should be performed upon setting of this
	 *   `GenericOption` with an argument.
	 */
	constructor(
		key: OptionKeyType,
		keywords: Collection<String> = emptySet(),
		description: String,
		action1: OptionProcessor<OptionKeyType>.(String) -> Unit,
		action2: OptionProcessor<OptionKeyType>.(String, String) -> Unit)
	: this(key, keywords, description, { keyword, optionalArgument ->
		if (optionalArgument === null)
		{
			action1(keyword)
		}
		else
		{
			action2(keyword, optionalArgument)
		}
	})
}
