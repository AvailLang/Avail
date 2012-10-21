/**
 * P_401_BootstrapLabelMacro.java
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

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import com.avail.annotations.Nullable;
import com.avail.compiler.AbstractAvailCompiler.ParserState;
import com.avail.descriptor.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.interpreter.*;
import com.avail.utility.Continuation1;

/**
 * The {@code P_401_BootstrapLabelMacro} primitive is used for bootstrapping
 * the {@link #LABEL_NODE label declaration} syntax for declaring labels near
 * the start of blocks.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class P_401_BootstrapLabelMacro extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_401_BootstrapLabelMacro().init(2, Unknown, Bootstrap);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final AvailObject labelName = args.get(0);
		final AvailObject type = args.get(1);

		final ParserState parserState = interpreter.currentParserState;
		final AvailObject blockMacroArguments =
			parserState.innermostBlockArguments;
		if (blockMacroArguments == null || blockMacroArguments.equalsNull())
		{
			return interpreter.primitiveFailure(
				E_LABEL_MACRO_MUST_OCCUR_INSIDE_A_BLOCK);
		}
		// For each argument phrase, recurse looking for actual argument
		// declarations.  Stop at block nodes.
		final List<AvailObject> blockArgumentTypes =
			new ArrayList<AvailObject>();
		for (final AvailObject argument : blockMacroArguments)
		{
			argument.childrenDo(new Continuation1<AvailObject>()
			{
				@Override
				public void value (final @Nullable AvailObject phrase)
				{
					assert phrase != null;
					if (phrase.isInstanceOfKind(BLOCK_NODE.mostGeneralType()))
					{
						return;
					}
					if (phrase.isInstanceOfKind(
						ParseNodeKind.ARGUMENT_NODE.mostGeneralType()))
					{
						blockArgumentTypes.add(phrase.declaredType());
						return;
					}
					// Otherwise recurse into the sub-phrases.
					phrase.childrenDo(this);
				}
			});
		}

		final AvailObject continuationType =
			ContinuationTypeDescriptor.forFunctionType(
				FunctionTypeDescriptor.create(
					TupleDescriptor.fromList(blockArgumentTypes),
					type));
		final AvailObject label = DeclarationNodeDescriptor.newLabel(
			labelName,
			continuationType);
		label.makeImmutable();
		return interpreter.primitiveSuccess(label);
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				/* Label name token */
				TOKEN.o(),
				/* Label return type */
				InstanceMetaDescriptor.topMeta()),
			LABEL_NODE.mostGeneralType());
	}

	@Override
	protected AvailObject privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			TupleDescriptor.from(
				E_PRIMITIVE_NOT_SUPPORTED.numericCode(),
				E_LABEL_MACRO_MUST_OCCUR_INSIDE_A_BLOCK.numericCode()
			).asSet());
	}
}
