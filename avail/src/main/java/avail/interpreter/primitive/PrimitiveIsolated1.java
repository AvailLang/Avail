/*
 * PrimitiveIsolated1.java
 * Copyright © 1993-2026, The Avail Foundation, LLC.
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

package avail.interpreter.primitive;

import avail.descriptor.representation.A_BasicObject;
import avail.descriptor.representation.AvailObject;
import avail.interpreter.execution.Interpreter;
import avail.optimizer.ExecutableChunk;
import avail.optimizer.jvm.ReferencedInGeneratedCode;

/**
 * An {@link Isolated} {@link Primitive} taking exactly one argument.  It
 * implements {@link #attemptIsolated}, which needs only a {@link
 * PrimitiveContext}, instead of {@link #attempt1}, which needs an {@link
 * Interpreter}, so it can also run in a chunk's {@link ExecutableChunk#freeze
 * freeze} method.
 *
 * <p>This class is written in Java, like {@link Primitive1}, so that a
 * Kotlin subclass may name its parameters for what they mean without the
 * Kotlin compiler warning that they differ from the names here.</p>
 */
public abstract class PrimitiveIsolated1 extends Primitive1
	implements Isolated
{
	/**
	 * Initialize the primitive.
	 *
	 * @param flags
	 *   The {@link Flag flags} that describe how the {@link Interpreter} and
	 *   the optimizer should deal with this primitive.
	 */
	public PrimitiveIsolated1(Primitive.Flag... flags)
	{
		super(flags);
	}

	/**
	 * Attempt to run this primitive, answering its result, or {@code null}
	 * after {@link PrimitiveContext#fail(A_BasicObject) failing}.  Generated
	 * code calls this directly, passing the {@link Interpreter} as the
	 * context, or a {@link FreezePrimitiveContext} in a freeze method.
	 *
	 * @param context
	 *   The {@link PrimitiveContext} through which the primitive may fail.
	 * @param arg1
	 *   The first argument to the primitive.
	 * @return
	 *   The result of the primitive, or {@code null} if it failed.
	 */
	@ReferencedInGeneratedCode
	public abstract A_BasicObject attemptIsolated(
		PrimitiveContext context,
		AvailObject arg1);

	@Override
	public final A_BasicObject attempt1(
		Interpreter interpreter,
		AvailObject arg1)
	{
		return attemptIsolated(interpreter, arg1);
	}
}
