/*
 * P_ContinuationStackData.kt
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
package avail.interpreter.primitive.continuations

import avail.descriptor.functions.ContinuationDescriptor.Companion.nilSubstitute
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.A_Continuation
import avail.descriptor.representation.A_Continuation.Companion.frameAt
import avail.descriptor.representation.A_RawFunction.Companion.numSlots
import avail.descriptor.representation.A_Tuple
import avail.descriptor.representation.A_Type
import avail.descriptor.representation.A_Variable
import avail.descriptor.representation.AvailObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.generateObjectTupleFrom
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.types.BottomTypeDescriptor
import avail.descriptor.types.ContinuationTypeDescriptor.Companion.mostGeneralContinuationType
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.TupleTypeDescriptor.Companion.mostGeneralTupleType
import avail.interpreter.execution.Interpreter
import avail.interpreter.primitive.Primitive.Flag.CanFold
import avail.interpreter.primitive.Primitive.Flag.CanInline
import avail.interpreter.primitive.Primitive.Flag.CannotFail
import avail.interpreter.primitive.Primitive1

/**
 * **Primitive:** Answer a [tuple][A_Tuple] containing the
 * [continuation][A_Continuation]'s stack data. Substitute an unassigned
 * [bottom][BottomTypeDescriptor]-typed [variable][A_Variable]
 * (unconstructible from Avail) for any [nil] values.
 */
@Suppress("unused")
object P_ContinuationStackData : Primitive1(CannotFail, CanFold, CanInline)
{
	override fun Interpreter.attempt1(
		arg1: AvailObject
	): A_BasicObject?
	{
		val con = arg1
		val tuple = generateObjectTupleFrom(con.function().code().numSlots) {
			con.frameAt(it).let { entry ->
				if (entry.isNil) nilSubstitute
				else entry
			}
		}
		tuple.makeSubobjectsImmutable()
		return tuple
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(mostGeneralContinuationType),
			mostGeneralTupleType)
}
