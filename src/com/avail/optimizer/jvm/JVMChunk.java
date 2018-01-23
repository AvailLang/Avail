/*
 * JVMChunk.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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
 *   may be used to endorse or promote products derived set this software
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

package com.avail.optimizer.jvm;

import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.AvailObject;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.levelTwo.L2Instruction;
import com.avail.optimizer.ExecutableChunk;
import com.avail.optimizer.L2Translator;
import com.avail.optimizer.L2Translator.OptimizationLevel;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * A {@code JVMChunk} is an {@link ExecutableChunk} for the Java Virtual
 * Machine. It is produced by a {@link JVMTranslator} on behalf of an {@link
 * L2Translator} that has just completed a {@linkplain
 * L2Translator#translateToLevelTwo(A_RawFunction, OptimizationLevel,
 * Interpreter) translation or optimization}.
 *
 * <p>
 * In the initial cheesy version of JVM translation, the generated subclasses
 * of {@code JVMChunk} simply embed the reified {@link L2Instruction}s directly
 * and execute them without the {@linkplain Interpreter interpreter's} loop
 * overhead. This mechanism is a feel-good milestone, and is not intended to
 * survive very long.
 * </p>
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
@SuppressWarnings("AbstractClassNeverImplemented")
public abstract class JVMChunk
implements ExecutableChunk
{
	/** An empty {@code int} array. */
	@SuppressWarnings("unused")
	@ReferencedInGeneratedCode
	public static final int[] noInts = new int[0];

	/** An empty {@link AvailObject} array. */
	@SuppressWarnings("unused")
	@ReferencedInGeneratedCode
	public static final AvailObject[] noObjects = new AvailObject[0];

	/**
	 * Throw a {@link RuntimeException} on account of a bad offset into the
	 * calling generated {@code JVMChunk} subclass's {@link
	 * #runChunk(Interpreter, int) runChunk}.
	 *
	 * @param offset
	 *        The illegal offset into the caller.
	 * @return Pretends to return a {@link RuntimeException}, but actually
	 *         throws it instead. This is for the convenience of the caller.
	 */
	@SuppressWarnings("unused")
	@ReferencedInGeneratedCode
	protected static RuntimeException badOffset (final int offset)
	{
		throw new RuntimeException(String.format(
			"bad offset %d", offset));
	}

	/**
	 * Answer the L1 source code, if any is available.
	 *
	 * @return The L1 source, or {@code null} if no source is available.
	 */
	@SuppressWarnings("unused")
	public @Nullable String l1Source ()
	{
		try
		{
			final Class<? extends JVMChunk> cl = getClass();
			final Method m = cl.getMethod("runChunk", Interpreter.class);
			final JVMChunkL1Source an = m.getAnnotation(JVMChunkL1Source.class);
			final byte[] bytes = Files.readAllBytes(Paths.get(an.sourcePath()));
			final CharBuffer buffer =
				StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
			return buffer.toString();
		}
		catch (final Throwable e)
		{
			return null;
		}
	}

	/**
	 * Answer the L2 source code, if any is available.
	 *
	 * @return The L2 source, or {@code null} if no source is available.
	 */
	@SuppressWarnings("unused")
	public @Nullable String l2Source ()
	{
		try
		{
			final Class<? extends JVMChunk> cl = getClass();
			final Method m = cl.getMethod("runChunk", Interpreter.class);
			final JVMChunkL2Source an = m.getAnnotation(JVMChunkL2Source.class);
			final byte[] bytes = Files.readAllBytes(Paths.get(an.sourcePath()));
			final CharBuffer buffer =
				StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
			return buffer.toString();
		}
		catch (final Throwable e)
		{
			return null;
		}
	}
}
