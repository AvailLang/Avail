/*
 * P_DeclareStringificationAtom.java
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

package com.avail.interpreter.primitive.general;

import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AtomDescriptor;
import com.avail.descriptor.MethodDescriptor;
import com.avail.exceptions.AvailRuntimeException;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;

import static com.avail.descriptor.FunctionDescriptor.createFunction;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.tuple;
import static com.avail.descriptor.TupleTypeDescriptor.stringType;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.Primitive.Flag.*;

/**
 * <strong>Primitive:</strong> Inform the VM of the {@linkplain
 * AtomDescriptor name} of the preferred stringification {@linkplain
 * MethodDescriptor method}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class P_DeclareStringificationAtom
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_DeclareStringificationAtom().init(
			1, CannotFail, HasSideEffect, Private);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(1);
		final A_Atom atom = interpreter.argument(0);
		// Generate a function that will invoke the stringifier method for
		// the specified value.
		final L1InstructionWriter writer = new L1InstructionWriter(
			nil, 0, nil);
		writer.argumentTypes(ANY.o());
		writer.returnType(stringType());
		writer.write(0, L1Operation.L1_doPushLocal, 1);
		try
		{
			writer.write(
				0,
				L1Operation.L1_doCall,
				writer.addLiteral(atom.bundleOrCreate()),
				writer.addLiteral(stringType()));
		}
		catch (final MalformedMessageException e)
		{
			assert false : "This should never happen!";
			throw new AvailRuntimeException(e.errorCode());
		}
		final A_Function function =
			createFunction(writer.compiledCode(), emptyTuple());
		function.makeShared();
		// Set the stringification function.
		interpreter.runtime().setStringificationFunction(function);
		final @Nullable AvailLoader loader = interpreter.availLoaderOrNull();
		if (loader != null)
		{
			loader.statementCanBeSummarized(false);
		}
		return interpreter.primitiveSuccess(nil);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(tuple(ATOM.o()), TOP.o());
	}
}
