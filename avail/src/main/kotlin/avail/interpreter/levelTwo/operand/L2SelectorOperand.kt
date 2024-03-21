/*
 * L2SelectorOperand.kt
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
package avail.interpreter.levelTwo.operand

import avail.descriptor.atoms.A_Atom.Companion.atomName
import avail.descriptor.bundles.A_Bundle
import avail.descriptor.bundles.A_Bundle.Companion.message
import avail.descriptor.bundles.MessageBundleDescriptor
import avail.descriptor.methods.MethodDefinitionDescriptor
import avail.descriptor.methods.MethodDescriptor
import avail.interpreter.levelTwo.L2OperandDispatcher
import avail.interpreter.levelTwo.L2OperandType
import avail.interpreter.levelTwo.L2OperandType.Companion.SELECTOR

/**
 * An `L2SelectorOperand` is an operand of type [L2OperandType.SELECTOR].  It
 * holds the [message&#32;bundle][MessageBundleDescriptor] that knows the
 * [method][MethodDescriptor] to invoke.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 *
 * @property bundle
 *   The actual [method][MethodDescriptor].
 *
 * @constructor
 * Construct a new `L2SelectorOperand` with the specified
 * [message&#32;bundle][MessageBundleDescriptor].
 *
 * @param bundle
 *   The message bundle that holds the [method][MethodDescriptor] in which to
 *   look up the [method&#32;definition][MethodDefinitionDescriptor] to
 *   ultimately invoke.
 */
class L2SelectorOperand constructor(val bundle: A_Bundle) : L2Operand()
{
	override val operandType: L2OperandType get() = SELECTOR

	override fun dispatchOperand(dispatcher: L2OperandDispatcher) =
		dispatcher.doOperand(this)

	override fun appendTo(builder: StringBuilder)
	{
		builder.append("$").append(bundle.message.atomName)
	}
}
