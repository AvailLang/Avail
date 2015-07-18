/**
 * UnaryOperator.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.interpreter.jvm;

import static com.avail.interpreter.jvm.JavaBytecode.*;
import com.avail.annotations.InnerAccess;

/**
 * {@code UnaryOperator} represents Java's unary operators. Each value of the
 * {@code enum} is capable of emitting onto an {@linkplain InstructionWriter
 * instruction stream} the {@linkplain JavaInstruction instructions} that
 * implement the corresponding semantic operation for a given {@linkplain
 * Class#isPrimitive() primitive type}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public enum UnaryOperator
{
	/** Specifies arithmetic negation. */
	NEGATION_ARITHMETIC
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {dneg, fneg, ineg, lneg};

		@Override
		@InnerAccess JavaBytecode bytecode (final Class<?> type)
		{
			assert type.isPrimitive();
			return bytecodes[typeIndex(type)];
		}
	},

	/** Specifies logical negation. */
	NEGATION_LOGICAL
	{
		@Override
		@InnerAccess JavaBytecode bytecode (final Class<?> type)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		void emitOn (final Class<?> type, final InstructionWriter writer)
		{
			assert type == Boolean.TYPE;
			writer.append(ineg.create());
			writer.append(iconst_1.create());
			writer.append(iadd.create());
		}
	},

	/** Specifies bitwise negation. */
	NEGATION_BITWISE
	{
		@Override
		@InnerAccess JavaBytecode bytecode (final Class<?> type)
		{
			throw new UnsupportedOperationException();
		}

		@Override
		void emitOn (final Class<?> type, final InstructionWriter writer)
		{
			assert type == Integer.TYPE || type == Long.TYPE;
			final JavaBytecode neg = NEGATION_ARITHMETIC.bytecode(type);
			writer.append(neg.create());
			writer.append(iconst_m1.create());
			writer.append(iadd.create());
		}
	};

	/**
	 * Answer an index suitable for looking up a {@linkplain JavaBytecode
	 * bytecode} that operates on operands of the specified {@linkplain
	 * Class#isPrimitive() primitive type}.
	 *
	 * @param type
	 *        A primitive type.
	 * @return A valid index into an array of bytecodes, or {@code -1} if the
	 *         type is invalid.
	 */
	@InnerAccess static int typeIndex (final Class<?> type)
	{
		return type == Double.TYPE ? 0
			: type == Float.TYPE ? 1
			: type == Integer.TYPE ? 2
			: type == Long.TYPE ? 3
			: -1;
	}

	/**
	 * Answer the {@linkplain JavaBytecode bytecode} that implements the
	 * {@linkplain BinaryOperator binary operator} given two operands of the
	 * specified {@linkplain Class#isPrimitive() primitive type}.
	 *
	 * @param type
	 *        A primitive type.
	 * @return The appropriate bytecode.
	 */
	abstract @InnerAccess JavaBytecode bytecode (final Class<?> type);

	/**
	 * Emit the necessary {@linkplain JavaInstruction instructions} to the
	 * specified {@linkplain InstructionWriter instruction stream}.
	 *
	 * @param type
	 *        A {@linkplain Class#isPrimitive() primitive type}.
	 * @param writer
	 *        An instruction stream.
	 */
	void emitOn (
		final Class<?> type,
		final InstructionWriter writer)
	{
		assert type.isPrimitive();
		final JavaBytecode bytecode = bytecode(type);
		writer.append(bytecode.create());
	}
}
