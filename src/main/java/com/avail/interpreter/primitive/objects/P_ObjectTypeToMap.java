/*
 * P_ObjectTypeToMap.java
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

import com.avail.descriptor.A_Type;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.InstanceTypeDescriptor;
import com.avail.descriptor.MapDescriptor;
import com.avail.descriptor.ObjectTypeDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.MapTypeDescriptor.mapTypeForSizesKeyTypeValueType;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.ObjectTypeDescriptor.mostGeneralObjectMeta;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.exceptions.AvailErrorCode.E_NO_SUCH_FIELD;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Convert an {@linkplain
 * ObjectTypeDescriptor object type} into a {@linkplain MapDescriptor map}
 * from {@linkplain AtomDescriptor fields}' {@linkplain
 * InstanceTypeDescriptor types} to {@linkplain TypeDescriptor types}.
 */
public final class P_ObjectTypeToMap extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ObjectTypeToMap().init(
			1, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Type objectType = interpreter.argument(0);
		if (objectType.isBottom())
		{
			// The correct answer would be a map with *every* atom as a key,
			// and ⊥ as every corresponding value.  It's easier to just fail
			// dynamically for this unrepresentable singularity.
			return interpreter.primitiveFailure(
				E_NO_SUCH_FIELD);
		}
		return interpreter.primitiveSuccess(objectType.fieldTypeMap());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return
			enumerationWith(set(E_NO_SUCH_FIELD));
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				mostGeneralObjectMeta()),
			mapTypeForSizesKeyTypeValueType(
				wholeNumbers(), ATOM.o(), anyMeta()));
	}
}
