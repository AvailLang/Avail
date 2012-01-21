/**
 * L2ExtractOuterInstruction.java
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

package com.avail.interpreter.levelTwo.instruction;

import static com.avail.interpreter.levelTwo.L2Operation.L2_doMoveFromOuterVariable_ofFunctionObject_destObject_;
import java.util.*;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.levelTwo.*;
import com.avail.interpreter.levelTwo.register.*;

/**
 * {@code L2ExtractOuterInstruction} extracts a captured {@linkplain AvailObject
 * object} from the source {@linkplain FunctionDescriptor function}.
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class L2ExtractOuterInstruction
extends L2Instruction
{
	/**
	 * The source {@linkplain L2ObjectRegister register} containing the
	 * {@linkplain FunctionDescriptor function}.
	 */
	private final @NotNull L2ObjectRegister sourceRegister;

	/**
	 * The one-based index of the captured {@linkplain AvailObject object}
	 * within the {@linkplain FunctionDescriptor function}.
	 */
	private final int outerIndex;

	/**
	 * The {@linkplain L2ObjectRegister register} into which the captured
	 * {@linkplain AvailObject object} will be written.
	 */
	private final @NotNull L2ObjectRegister destinationRegister;

	/**
	 * Construct a new {@link L2ExtractOuterInstruction}.
	 *
	 * @param sourceRegister
	 *        The source {@linkplain L2ObjectRegister register} containing the
	 *        {@linkplain FunctionDescriptor function}.
	 * @param outerIndex
	 *        The one-based index of the captured {@linkplain AvailObject
	 *        object} within the {@linkplain FunctionDescriptor function}.
	 * @param destinationRegister
	 *        The {@linkplain L2ObjectRegister register} into which the captured
	 *        {@linkplain AvailObject object} will be written.
	 */
	public L2ExtractOuterInstruction (
		final @NotNull L2ObjectRegister sourceRegister,
		final int outerIndex,
		final @NotNull L2ObjectRegister destinationRegister)
	{
		this.sourceRegister = sourceRegister;
		this.outerIndex = outerIndex;
		this.destinationRegister = destinationRegister;
	}

	@Override
	public @NotNull List<L2Register> sourceRegisters ()
	{
		return Collections.<L2Register>singletonList(sourceRegister);
	}

	@Override
	public @NotNull List<L2Register> destinationRegisters ()
	{
		return Collections.<L2Register>singletonList(destinationRegister);
	}

	@Override
	public void emitOn (final @NotNull L2CodeGenerator codeGenerator)
	{
		codeGenerator.emitL2Operation(
			L2_doMoveFromOuterVariable_ofFunctionObject_destObject_);
		codeGenerator.emitImmediate(outerIndex);
		codeGenerator.emitObjectRegister(sourceRegister);
		codeGenerator.emitObjectRegister(destinationRegister);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Outer variables are treated as untyped here, because the same
	 * {@linkplain CompiledCodeDescriptor code object} may occur within multiple
	 * parent code objects. That's because of the (eventual) coalescing garbage
	 * collector, which can merge equal code objects. In different parent code
	 * objects, the child code object may have different type expectations. This
	 * isn't as bad as it seems, as {@linkplain FunctionDescriptor functions} are
	 * very profitable to inline anyhow, greatly reducing occurrences of this
	 * lack of type information.</p>
	 */
	@Override
	public void propagateTypeInfoFor (final @NotNull L2Translator translator)
	{
		translator.removeTypeForRegister(destinationRegister);
		translator.removeConstantForRegister(destinationRegister);
	}
}
