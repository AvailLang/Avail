/*
 * GenericOption.kt
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

package com.avail.tools.options

import com.avail.tools.options.OptionProcessorFactory.Cardinality
import com.avail.tools.options.OptionProcessorFactory.OptionInvocation
import com.avail.tools.options.OptionProcessorFactory.OptionInvocationWithArgument

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
 *   [option][GenericOption] with no argument. Exactly one of [action] or
 *   [action2] must be non-`null`.
 * @param action2
 *   The action that should be performed upon setting of this `GenericOption`
 *   with an argument. Exactly one of [action] or [action2] must be non-`null`.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
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
 *   No argument must not be provided.  Exactly one of [action] or [action2]
 *   must be non-`null`.
 * @param action2
 *   The action that should be performed upon setting of this `GenericOption`.
 *   An argument must be provided. Exactly one of [action] or [action2] must
 *   be non-`null`.
 */
internal open class GenericOption<OptionKeyType : Enum<OptionKeyType>>
	constructor(
		override val key: OptionKeyType,
		keywords: Collection<String>,
		override val cardinality: Cardinality,
		override val description: String,
		override val action:
			(OptionInvocation<OptionKeyType>.() -> Unit)?,
		override val action2:
			(OptionInvocationWithArgument<OptionKeyType>.() -> Unit)?)
	: Option<OptionKeyType>
{
	/**
	 * The [set][MutableSet] of keywords that indicate this
	 * [option][GenericOption].
	 */
	override val keywords = keywords.toMutableSet()
}
