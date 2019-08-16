/*
 * P_CreatePojoType.java
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
package com.avail.interpreter.primitive.pojos;

import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.PojoTypeDescriptor;
import com.avail.exceptions.AvailRuntimeException;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.anyMeta;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PojoTypeDescriptor.canonicalPojoType;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TupleTypeDescriptor.zeroOrMoreOf;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS;
import static com.avail.exceptions.AvailErrorCode.E_JAVA_CLASS_NOT_AVAILABLE;
import static com.avail.interpreter.Primitive.Flag.CanFold;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Answer the {@linkplain A_Type type} that
 * represents the {@linkplain Class Java class} specified by the given
 * fully-qualified name and type parameters. The result is either an Avail
 * {@linkplain A_Type type} or a {@linkplain PojoTypeDescriptor pojo type}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@SuppressWarnings("unused")
public final class P_CreatePojoType extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreatePojoType().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(2);
		final A_String className = interpreter.argument(0);
		final A_Tuple classParameters = interpreter.argument(1);

		try
		{
			final A_Type pojoType = interpreter.runtime().lookupJavaType(
				className, classParameters);
			return interpreter.primitiveSuccess(
				canonicalPojoType(pojoType, true));
		}
		catch (AvailRuntimeException e)
		{
			return interpreter.primitiveFailure(e.numericCode());
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				stringType(),
				zeroOrMoreOf(anyMeta())),
			anyMeta());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(set(
			E_JAVA_CLASS_NOT_AVAILABLE,
			E_INCORRECT_NUMBER_OF_ARGUMENTS));
	}
}
