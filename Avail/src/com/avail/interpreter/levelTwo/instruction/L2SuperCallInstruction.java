/**
 * interpreter/levelTwo/instruction/L2SuperCallInstruction.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.interpreter.levelTwo.instruction;

import static com.avail.interpreter.levelTwo.L2Operation.L2_doSuperSend_argumentsVector_argumentTypesVector_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2SuperCallInstruction} attempts to execute a super call by construing
 * the actual {@linkplain AvailObject arguments} to have the specified
 * {@linkplain TypeDescriptor types} and then querying the {@linkplain
 * ImplementationSetDescriptor implementation set} to find the best matching
 * {@linkplain MethodSignatureDescriptor method}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2SuperCallInstruction
extends L2Instruction
{
	/**
	 * The {@linkplain ImplementationSetDescriptor implementation set} from
	 * which a {@linkplain MethodSignatureDescriptor method} should be selected
	 * and called based on the {@linkplain #argumentsVector arguments} and
	 * {@linkplain #typesVector types}.
	 */
	private final @NotNull AvailObject implementationSet;

	/**
	 * The {@linkplain AvailObject arguments} of the {@linkplain
	 * MethodSignatureDescriptor method} call.
	 */
	private final @NotNull L2RegisterVector argumentsVector;

	/**
	 * The {@linkplain TypeDescriptor types} which the exact {@linkplain
	 * #argumentsVector arguments} should be construed to have.
	 */
	private final @NotNull L2RegisterVector typesVector;

	/**
	 *
	 * Construct a new {@link L2SuperCallInstruction}.
	 *
	 * @param implementationSet
	 *        The {@linkplain ImplementationSetDescriptor implementation set}
	 *        from which a {@linkplain MethodSignatureDescriptor method} should
	 *        be selected and called based on the {@linkplain #argumentsVector
	 *        arguments} and {@linkplain #typesVector types}.
	 * @param argumentsVector
	 *        The {@linkplain AvailObject arguments} of the {@linkplain
	 *        MethodSignatureDescriptor method} call.
	 * @param typesVector
	 *        The {@linkplain TypeDescriptor types} which the exact {@linkplain
	 *        #argumentsVector arguments} should be construed to have.
	 */
	public L2SuperCallInstruction (
		final @NotNull AvailObject implementationSet,
		final @NotNull L2RegisterVector argumentsVector,
		final @NotNull L2RegisterVector typesVector)
	{
		this.implementationSet = implementationSet;
		this.argumentsVector = argumentsVector;
		this.typesVector = typesVector;
	}

	@Override
	public List<L2Register> sourceRegisters ()
	{
		final List<L2Register> result = new ArrayList<L2Register>(
			argumentsVector.registers().size() << 1);
		result.addAll(argumentsVector.registers());
		result.addAll(typesVector.registers());
		return result;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Since a call can clear all registers, we could try to list all
	 * registers as destinations. Instead, we treat calls as the ends of the
	 * basic blocks during flow analysis.</p>
	 */
	@Override
	public @NotNull List<L2Register> destinationRegisters ()
	{
		return Collections.emptyList();
	}

	@Override
	public void emitOn (final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitWord(
			L2_doSuperSend_argumentsVector_argumentTypesVector_.ordinal());
		codeGenerator.emitLiteral(implementationSet);
		codeGenerator.emitVector(argumentsVector);
		codeGenerator.emitVector(typesVector);
	}

	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		translator.restrictPropagationInformationToArchitecturalRegisters();
	}
}
