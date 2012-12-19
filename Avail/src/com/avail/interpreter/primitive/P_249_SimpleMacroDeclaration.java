/**
 * P_249_SimpleMacroDeclaration.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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
package com.avail.interpreter.primitive;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.PARSE_NODE;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.interpreter.Primitive.Flag.Unknown;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.exceptions.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 249:</strong> Simple macro definition.  The first argument
 * is the macro name, and the second argument is a {@linkplain TupleDescriptor
 * tuple} of {@linkplain FunctionDescriptor functions} returning ⊤, one for each
 * occurrence of a {@linkplain StringDescriptor#sectionSign() section sign} (§)
 * in the macro name.  The third argument is the function to invoke for the
 * complete macro.  It is constrained to answer a {@linkplain
 * ParseNodeDescriptor parse node}.
 */
public class P_249_SimpleMacroDeclaration extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance = new P_249_SimpleMacroDeclaration().init(
		3, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 3;
		final AvailObject string = args.get(0);
		final AvailObject prefixFunctions = args.get(1);
		final AvailObject function = args.get(2);
		for (final AvailObject prefixFunction : prefixFunctions)
		{
			if (!prefixFunction.kind().returnType().equals(TOP.o()))
			{
				return interpreter.primitiveFailure(
					AvailErrorCode.E_MACRO_PREFIX_FUNCTIONS_MUST_RETURN_TOP);
			}
		}
		try
		{
			interpreter.addMacroBody(
				interpreter.lookupName(string),
				prefixFunctions,
				function);
			// Even though a macro is always monomorphic and therefore
			// effectively permanent (and probably unlikely to ever be inlined),
			// we may as well do the fixup call here.  Designs change.
			interpreter.fixupForPotentiallyInvalidCurrentChunk();
			for (int i = 1; i <= prefixFunctions.tupleSize(); i++)
			{
				prefixFunctions.tupleAt(i).code().setMethodName(
					StringDescriptor.from(
						String.format(
							"Macro prefix #%d of %s",
							i,
							string)));
			}
			function.code().setMethodName(
				StringDescriptor.from(
					String.format("Macro body of %s", string)));

		}
		catch (final AmbiguousNameException e)
		{
			return interpreter.primitiveFailure(e);
		}
		catch (final SignatureException e)
		{
			return interpreter.primitiveFailure(e);
		}
		return interpreter.primitiveSuccess(NullDescriptor.nullObject());
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				TupleTypeDescriptor.stringTupleType(),
				TupleTypeDescriptor.zeroOrMoreOf(
					FunctionTypeDescriptor.mostGeneralType()),
				FunctionTypeDescriptor.forReturnType(
					PARSE_NODE.mostGeneralType())),
			TOP.o());
	}
}