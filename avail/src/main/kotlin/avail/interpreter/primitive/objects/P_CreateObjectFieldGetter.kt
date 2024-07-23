/*
 * P_CreateObjectFieldGetter.kt
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
package avail.interpreter.primitive.objects

import avail.descriptor.atoms.A_Atom
import avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import avail.descriptor.atoms.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY
import avail.descriptor.functions.A_Function
import avail.descriptor.functions.A_RawFunction
import avail.descriptor.functions.CompiledCodeDescriptor.Companion.newCompiledCode
import avail.descriptor.functions.FunctionDescriptor.Companion.createExceptOuters
import avail.descriptor.functions.FunctionDescriptor.Companion.createWithOuters1
import avail.descriptor.maps.A_Map.Companion.hasKey
import avail.descriptor.objects.ObjectDescriptor
import avail.descriptor.objects.ObjectTypeDescriptor
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import avail.descriptor.representation.A_BasicObject
import avail.descriptor.representation.NilDescriptor.Companion.nil
import avail.descriptor.sets.SetDescriptor.Companion.set
import avail.descriptor.tuples.ObjectTupleDescriptor.Companion.tuple
import avail.descriptor.tuples.StringDescriptor.Companion.stringFrom
import avail.descriptor.tuples.TupleDescriptor.Companion.emptyTuple
import avail.descriptor.types.A_Type
import avail.descriptor.types.A_Type.Companion.fieldTypeMap
import avail.descriptor.types.AbstractEnumerationTypeDescriptor.Companion.enumerationWith
import avail.descriptor.types.BottomTypeDescriptor.Companion.bottom
import avail.descriptor.types.FunctionTypeDescriptor.Companion.functionType
import avail.descriptor.types.InstanceTypeDescriptor.Companion.instanceType
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ANY
import avail.descriptor.types.PrimitiveTypeDescriptor.Types.ATOM
import avail.exceptions.AvailErrorCode
import avail.interpreter.Primitive
import avail.interpreter.Primitive.Flag.CanFold
import avail.interpreter.Primitive.Flag.CanInline
import avail.interpreter.execution.Interpreter
import avail.interpreter.levelOne.L1InstructionWriter
import avail.interpreter.levelOne.L1Operation
import avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import avail.optimizer.L1Translator

/**
 * **Primitive:** Given an [object&#32;type][ObjectTypeDescriptor] and an
 * [A_Atom] that is a field of that type (and not an explicit-subclass atom),
 * create a function that will extract that field from an object satisfying that
 * type.
 *
 * The function should accept only objects of the type that was given to this
 * primitive, and should produce a result that is strongly typed to the field's
 * type in the object type.  The produced function will use the primitive
 * [P_PrivateGetSpecificObjectField], which expects the field atom to be
 * captured as an outer variable of that function.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
@Suppress("unused")
object P_CreateObjectFieldGetter : Primitive(2, CanFold, CanInline)
{
	override fun attempt(interpreter: Interpreter): Result
	{
		interpreter.checkArgumentCount(2)
		val (objectType, fieldAtom) = interpreter.argsBuffer

		val map = objectType.fieldTypeMap
		if (!map.hasKey(fieldAtom))
		{
			// The field is not guaranteed to be part of the object.
			return interpreter.primitiveFailure(AvailErrorCode.E_NO_SUCH_FIELD)
		}
		if (fieldAtom.getAtomProperty(EXPLICIT_SUBCLASSING_KEY.atom).notNil)
		{
			// It's an explicit-subclass field, which has no backing slot, but
			// is considered to have the fieldAtom itself as its value.  Answer
			// a function that returns the fieldAtom itself.
			val newFunction = functionReturningConstant(objectType, fieldAtom)
			return interpreter.primitiveSuccess(newFunction)
		}
		val module = interpreter.availLoaderOrNull()?.module ?: nil
		val returnType = objectType.fieldTypeAt(fieldAtom)
		val rawFunction = newCompiledCode(
			emptyTuple,
			0,
			functionType(tuple(objectType), returnType),
			P_PrivateGetSpecificObjectField,
			bottom,
			emptyTuple(),
			emptyTuple(),
			emptyTuple(),
			tuple(instanceType(fieldAtom)),
			module,
			0,
			emptyTuple,
			-1,
			nil,
			packedDeclarationNamesForGeneratedFunction)
		val newFunction = createWithOuters1(rawFunction, fieldAtom)
		return interpreter.primitiveSuccess(newFunction)
	}

	/**
	 * Answer a strongly-typed function that takes an object of the specified
	 * type and yields the given value.
	 *
	 * @param objectType
	 *   The [A_Type] of [object][ObjectDescriptor] to accept as the sole
	 *   argument of the function.
	 * @param value
	 *   The constant to return from the function.
	 */
	private fun functionReturningConstant(
		objectType: A_Type,
		value: A_BasicObject
	) : A_Function {
		return L1InstructionWriter(nil, 0, nil).run {
			argumentTypes(objectType)
			returnType = instanceType(value)
			returnTypeIfPrimitiveFails = returnType
			write(0, L1Operation.L1_doPushLiteral, addLiteral(value))
			createExceptOuters(compiledCode(), 0)
		}
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralObjectMeta,
				ATOM.o),
			functionType(
				tuple(
					mostGeneralObjectType),
				ANY.o))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(AvailErrorCode.E_NO_SUCH_FIELD))

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		callSiteHelper: L1Translator.CallSiteHelper
	): Boolean {
		// TODO - Generate L2 code to collect statistics on the variants that
		// are encountered, then at the next reoptimization, inline L2
		// instructions that access the field by index.
		return false
	}

	val packedDeclarationNamesForGeneratedFunction = stringFrom("arg1,outer1")
}
