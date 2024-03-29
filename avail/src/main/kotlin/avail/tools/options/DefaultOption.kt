/*
 * DefaultOption.kt
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

import avail.tools.options.OptionProcessorFactory.Cardinality
import avail.tools.options.OptionProcessorFactory.OptionInvocationWithArgument

/**
 * A `DefaultOption` is the [option][Option] that an
 * [option&#32;processor][OptionProcessor] recognizes bare arguments as being
 * implicitly associated with.
 *
 * @param OptionKeyType
 *   The type of the option.
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 * @author Leslie Schultz &lt;leslie@availlang.org&gt;
 *
 * @constructor
 *
 * Construct a new instance.
 *
 * @param optionKey
 *   The option key.
 * @param cardinality
 *   The basic constraint on the number of occurrences of this option.
 * @param description
 *   A description of the `DefaultOption`.
 * @param action2
 *   The action that should be performed upon setting of this `DefaultOption`.
 */
internal class DefaultOption<OptionKeyType : Enum<OptionKeyType>>(
	optionKey: OptionKeyType,
	cardinality: Cardinality,
	description: String,
	action2: OptionInvocationWithArgument<OptionKeyType>.() -> Unit
) : GenericOption<OptionKeyType>(
	optionKey, listOf(""), cardinality, description, null, action2)
