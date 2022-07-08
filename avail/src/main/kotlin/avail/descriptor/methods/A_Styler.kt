/*
 * A_Styler.kt
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
package avail.descriptor.methods

import avail.descriptor.functions.A_Function
import avail.descriptor.module.A_Module
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_BasicObject.Companion.dispatch
import avail.descriptor.representation.AvailObject
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.wholeNumbers
import avail.descriptor.types.MapTypeDescriptor.Companion.mapTypeForSizesKeyTypeValueType
import avail.descriptor.types.PhraseTypeDescriptor.PhraseKind
import avail.descriptor.types.PrimitiveTypeDescriptor.Types
import avail.descriptor.types.TupleTypeDescriptor.Companion.stringType
import avail.descriptor.types.VariableTypeDescriptor.Companion.variableTypeFor

/**
 * `A_Styler` is an interface that specifies the operations specific to
 * [stylers][StylerDescriptor] of a method [definition][A_Definition] in Avail.
 * It's a sub-interface of [A_BasicObject], the interface that defines the
 * behavior that all [AvailObject]s are required to support.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
interface A_Styler : A_BasicObject
{
	companion object
	{
		/**
		 * Answer the [A_Function] that implements this styler's logic.
		 */
		val A_Styler.function: A_Function get() = dispatch { o_Function(it) }

		/**
		 * Answer the [method][A_Method] that this styler is intended to style
		 * invocations of.
		 *
		 * @return
		 *   The method that this styler is attached to.
		 */
		val A_Styler.stylerMethod: A_Method get() =
			dispatch { o_StylerMethod(it) }

		/**
		 * Answer the [module][A_Module] in which this styler was defined.
		 *
		 * @return
		 *   The styler's defining module.
		 */
		val A_Styler.module: A_Module get() = dispatch { o_Module(it) }

		/**
		 * The function type for styler functions.
		 */
		val stylerFunctionType: A_Type = functionType(
			tuple(
				// The phrase being styled.
				PhraseKind.SEND_PHRASE.mostGeneralType,
				// A variable holding the map from phrase to style.
				variableTypeFor(
					mapTypeForSizesKeyTypeValueType(
						wholeNumbers,
						PhraseKind.PARSE_PHRASE.mostGeneralType,
						stringType)),
				// A variable holding the map from token to style.
				variableTypeFor(
					mapTypeForSizesKeyTypeValueType(
						wholeNumbers,
						Types.TOKEN.o,
						stringType)),
				// A variable mapping from variable-use (or other) tokens to
				// declaration (or other) tokens within the same module.
				variableTypeFor(
					mapTypeForSizesKeyTypeValueType(
						wholeNumbers,
						Types.TOKEN.o,
						Types.TOKEN.o))),
			Types.TOP.o)
	}
}
