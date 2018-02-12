/*
 * P_GetObjectTypeField.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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
package com.avail.interpreter.primitive.objects;

import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Map;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.ObjectTypeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import java.util.List;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.InstanceMetaDescriptor.instanceMeta;
import static com.avail.descriptor.ObjectTypeDescriptor.mostGeneralObjectMeta;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.exceptions.AvailErrorCode.E_NO_SUCH_FIELD;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCanFail;
import static com.avail.interpreter.Primitive.Fallibility.CallSiteCannotFail;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Extract the specified {@linkplain
 * AtomDescriptor field}'s type from the {@linkplain ObjectTypeDescriptor
 * object type}.
 */
public final class P_GetObjectTypeField extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_GetObjectTypeField().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_Type objectType = interpreter.argument(0);
		final A_Atom field = interpreter.argument(1);
		final A_Map fieldTypeMap = objectType.fieldTypeMap();
		if (!fieldTypeMap.hasKey(field))
		{
			return interpreter.primitiveFailure(E_NO_SUCH_FIELD);
		}
		return interpreter.primitiveSuccess(fieldTypeMap.mapAt(field));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralObjectMeta(),
				ATOM.o()),
			anyMeta());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type objectMeta = argumentTypes.get(0);
		final A_Type fieldType = argumentTypes.get(1);

		if (objectMeta.isBottom())
		{
			return bottom();
		}
		if (fieldType.isEnumeration())
		{
			final A_Type objectType = objectMeta.instance();
			final A_Map fieldTypeMap = objectType.fieldTypeMap();
			A_Type union = bottom();
			for (final A_Atom possibleField : fieldType.instances())
			{
				if (!fieldTypeMap.hasKey(possibleField))
				{
					// Unknown field, so the field type could be any type.
					return anyMeta();
				}
				union = union.typeUnion(fieldTypeMap.mapAt(possibleField));
			}
			// Shift it up; a primitive invocation will return the field's type.
			return instanceMeta(union);
		}
		return super.returnTypeGuaranteedByVM(rawFunction, argumentTypes);
	}

	@Override
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type objectMeta = argumentTypes.get(0);
		final A_Type fieldType = argumentTypes.get(1);

		if (fieldType.isEnumeration())
		{
			final A_Type objectType = objectMeta.instance();
			final A_Map fieldTypeMap = objectType.fieldTypeMap();
			for (final A_Atom possibleField : fieldType.instances())
			{
				if (!fieldTypeMap.hasKey(possibleField))
				{
					// Unknown field.
					return CallSiteCanFail;
				}
			}
			return CallSiteCannotFail;
		}
		return CallSiteCanFail;
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(E_NO_SUCH_FIELD));
	}
}
