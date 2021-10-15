/*
 * Option.kt
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

package avail.tools.options

import avail.tools.options.OptionProcessorFactory.Cardinality
import avail.tools.options.OptionProcessorFactory.OptionInvocation
import avail.tools.options.OptionProcessorFactory.OptionInvocationWithArgument

/**
 * An `Option` comprises an [enumerated&#32;type][Enum] which defines the domain
 * of the option, the keywords which parsers may use to identify the option, an
 * end-user friendly description of the option, and an action that should be
 * performed each time that the option is set.
 *
 * @param OptionKeyType
 *   The type of the option.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 */
interface Option<OptionKeyType : Enum<OptionKeyType>>
{
	/**
	 * The option key, a member of the [enumeration][Enum] which defines this
	 * option space.
	 */
	val key: OptionKeyType

	/**
	 * The [set][MutableSet] of keywords that indicate this
	 * [option][GenericOption].
	 */
	val keywords: MutableSet<String>

	/**
	 * Answer an end-user comprehensible description of the option.
	 *
	 * @return A description of the option.
	 */
	val description: String

	/**
	 * The [Cardinality] of this option.  There may be stricter conditions that
	 * can be further checked by [OptionProcessor.checkEncountered] or a custom
	 * [OptionProcessorFactory.rule].
	 */
	val cardinality: Cardinality

	/**
	 * The action that should be performed upon setting of this
	 * [option][GenericOption].  This action is provided when no argument is to
	 * be supplied for the option.  Exactly one of [action] or [action2] should
	 * be set to a non-`null` value.
	 */
	val action: (OptionInvocation<OptionKeyType>.() -> Unit)?

	/**
	 * The action that should be performed upon setting of this
	 * [option][GenericOption].  This action is provided when an argument is
	 * required for the option.  Exactly one of [action] or [action2] should be
	 * set to a non-`null` value.
	 */
	val action2: (OptionInvocationWithArgument<OptionKeyType>.() -> Unit)?

	val takesArgument: Boolean get() = action2 !== null
}
