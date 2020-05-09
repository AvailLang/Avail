/*
 * P_CreateObjectFieldGetter.kt
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
package com.avail.interpreter.primitive.objects

import com.avail.descriptor.NilDescriptor.nil
import com.avail.descriptor.atoms.A_Atom
import com.avail.descriptor.atoms.A_Atom.Companion.getAtomProperty
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom.EXPLICIT_SUBCLASSING_KEY
import com.avail.descriptor.functions.A_Function
import com.avail.descriptor.functions.A_RawFunction
import com.avail.descriptor.functions.CompiledCodeDescriptor.Companion.newCompiledCode
import com.avail.descriptor.functions.FunctionDescriptor.Companion.createExceptOuters
import com.avail.descriptor.functions.FunctionDescriptor.Companion.createWithOuters1
import com.avail.descriptor.objects.ObjectDescriptor
import com.avail.descriptor.objects.ObjectTypeDescriptor
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectMeta
import com.avail.descriptor.objects.ObjectTypeDescriptor.Companion.mostGeneralObjectType
import com.avail.descriptor.representation.A_BasicObject
import com.avail.descriptor.sets.SetDescriptor.set
import com.avail.descriptor.tuples.ObjectTupleDescriptor.emptyTuple
import com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple
import com.avail.descriptor.types.A_Type
import com.avail.descriptor.types.AbstractEnumerationTypeDescriptor.enumerationWith
import com.avail.descriptor.types.FunctionTypeDescriptor.functionType
import com.avail.descriptor.types.InstanceTypeDescriptor.instanceType
import com.avail.descriptor.types.TypeDescriptor.Types.ANY
import com.avail.descriptor.types.TypeDescriptor.Types.ATOM
import com.avail.exceptions.AvailErrorCode
import com.avail.interpreter.execution.Interpreter
import com.avail.interpreter.Primitive
import com.avail.interpreter.Primitive.Flag.CanFold
import com.avail.interpreter.Primitive.Flag.CanInline
import com.avail.interpreter.levelOne.L1InstructionWriter
import com.avail.interpreter.levelOne.L1Operation
import com.avail.interpreter.levelTwo.operand.L2ReadBoxedOperand
import com.avail.optimizer.L1Translator

/**
 * **Primitive:** Given an [object type][ObjectTypeDescriptor] and an [A_Atom]
 * that is a field of that type (and not an explicit-subclass atom), create a
 * function that will extract that field from an object satisfying that type.
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

		val map = objectType.fieldTypeMap()
		if (!map.hasKey(fieldAtom))
		{
			// The field is not guaranteed to be part of the object.
			return interpreter.primitiveFailure(AvailErrorCode.E_NO_SUCH_FIELD)
		}
		val explicitSubclassingProperty =
			fieldAtom.getAtomProperty(EXPLICIT_SUBCLASSING_KEY.atom)
		if (!explicitSubclassingProperty.equalsNil())
		{
			// It's an explicit-subclass field, which has no backing slot, but
			// is considered to have the fieldAtom itself as its value.  Answer
			// a function that returns the fieldAtom itself.
			val newFunction = functionReturningConstant(objectType, fieldAtom)
			return interpreter.primitiveSuccess(newFunction)
		}
		val module = interpreter.availLoaderOrNull()?.module() ?: nil
		val returnType = objectType.fieldTypeAt(fieldAtom)
		val rawFunction = newCompiledCode(
			emptyTuple(),
			0,
			functionType(tuple(objectType), returnType),
			P_PrivateGetSpecificObjectField,
			emptyTuple(),
			emptyTuple(),
			emptyTuple(),
			tuple(instanceType(fieldAtom)),
			module,
			0,
			emptyTuple(),
			nil)
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
		val writer = L1InstructionWriter(nil, 0, nil)
		writer.argumentTypes(objectType)
		writer.returnType = instanceType(value)
		writer.write(0, L1Operation.L1_doPushLiteral, writer.addLiteral(value))
		return createExceptOuters(writer.compiledCode(), 0)
	}

	override fun privateBlockTypeRestriction(): A_Type =
		functionType(
			tuple(
				mostGeneralObjectMeta(),
				ATOM.o()),
			functionType(
				tuple(
					mostGeneralObjectType()),
				ANY.o()))

	override fun privateFailureVariableType(): A_Type =
		enumerationWith(set(AvailErrorCode.E_NO_SUCH_FIELD))

	override fun tryToGenerateSpecialPrimitiveInvocation(
		functionToCallReg: L2ReadBoxedOperand,
		rawFunction: A_RawFunction,
		arguments: List<L2ReadBoxedOperand>,
		argumentTypes: List<A_Type>,
		translator: L1Translator,
		callSiteHelper: L1Translator.CallSiteHelper
	): Boolean {
		// TODO - Generate L2 code to collect statistics on the variants that
		// are encountered, then at the next reoptimization, inline L2
		// instructions that access the field by index.
		return false
	}
}
