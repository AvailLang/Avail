/*
 * P_CurrentLocale.kt
 * Copyright © 1993-2023, The Avail Foundation, LLC.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of the copyright holder nor the names of the contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
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

package avail.interpreter.primitive.general

import avail.descriptor.character.CharacterDescriptor.Companion.fromCodePoint
import avail.descriptor.sets.SetDescriptor.Companion.setFromCollection
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.FunctionTypeDescriptor
import avail.descriptor.types.IntegerRangeTypeDescriptor.Companion.singleInt
import avail.descriptor.types.TupleTypeDescriptor
import avail.descriptor.types.TupleTypeDescriptor.Companion.nonemptyStringType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForSizesTypesDefaultType
import avail.descriptor.types.TupleTypeDescriptor.Companion.tupleTypeForTypes
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.Primitive.Flag.CannotFail
import avail.interpreter.execution.Interpreter
import java.util.Locale

/**
 * **Primitive:** Provides the system's currently set [Locale] information. It
 * provides a [tuple][TupleTypeDescriptor] of four strings:
 *
 * 1. [Locale.getISO3Language]
 * 2. [Locale.getDisplayLanguage]
 * 3. [Locale.getISO3Country]
 * 4. [Locale.getDisplayCountry]
 *
 * @author Richard Arriaga
 */
object P_CurrentLocale : Primitive(0, CannotFail, CanInline, CanFold)
{
	override fun attempt(
		interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(0)
		val locale = Locale.getDefault()
		return interpreter.primitiveSuccess(
			tuple(
				stringFrom(locale.isO3Language),
				stringFrom(locale.displayLanguage),
				stringFrom(locale.isO3Country),
				stringFrom(locale.displayCountry))
		)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		FunctionTypeDescriptor.functionType(
			emptyTuple,
			tupleTypeForTypes(
				tupleTypeForSizesTypesDefaultType(
					singleInt(3),
					emptyTuple,
					enumerationWith(
						setFromCollection(
							('a'..'z')
								.map { fromCodePoint(it.code) }))),
				nonemptyStringType,
				tupleTypeForSizesTypesDefaultType(
					singleInt(3),
					emptyTuple,
					enumerationWith(
						setFromCollection(
							('a'..'z')
								.map { fromCodePoint(it.code) }))),
				nonemptyStringType))
}
