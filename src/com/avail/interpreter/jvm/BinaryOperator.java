/**
 * BinaryOperator.java
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
 * {@code BinaryOperator} represents Java's non-{@code boolean} valued binary
 * operators. Each value of the {@code enum} is capable of emitting onto an
 * {@linkplain InstructionWriter instruction stream} the {@linkplain
 * JavaInstruction instructions} that implement the corresponding semantic
 * operation for a given {@linkplain Class#isPrimitive() primitive type}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public enum BinaryOperator
{
	/** Specifies addition. */
	ADDITION
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {dadd, fadd, iadd, ladd};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			assert type.isPrimitive();
			return bytecodes[typeIndex(type)];
		}
	},

	/** Specifies subtraction. */
	SUBTRACTION
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {dsub, fsub, isub, lsub};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			assert type.isPrimitive();
			return bytecodes[typeIndex(type)];
		}
	},

	/** Specifies multiplication. */
	MULTIPLICATION
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {dmul, fmul, imul, lmul};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			assert type.isPrimitive();
			return bytecodes[typeIndex(type)];
		}
	},

	/** Specifies division. */
	DIVISION
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {ddiv, fdiv, idiv, ldiv};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			assert type.isPrimitive();
			return bytecodes[typeIndex(type)];
		}
	},

	/** Specifies modulus. */
	MODULUS
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {drem, frem, irem, lrem};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			assert type.isPrimitive();
			return bytecodes[typeIndex(type)];
		}
	},

	/** Specifies bitwise {@code AND}. */
	AND
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {iand, land};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			return bytecodes[typeIndex(type) - 2];
		}
	},

	/** Specifies bitwise {@code OR}. */
	OR
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {ior, lor};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			return bytecodes[typeIndex(type) - 2];
		}
	},

	/** Specifies bitwise {@code XOR}. */
	XOR
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {ixor, lxor};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			return bytecodes[typeIndex(type) - 2];
		}
	},

	/** Specifies left shift. */
	LEFT_SHIFT
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {ishl, lshl};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			return bytecodes[typeIndex(type) - 2];
		}
	},

	/** Specifies arithmetic right shift. */
	RIGHT_SHIFT_ARITHMETIC
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {ishr, lshr};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			return bytecodes[typeIndex(type) - 2];
		}
	},

	/** Specifies logical right shift. */
	RIGHT_SHIFT_LOGICAL
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {iushr, lushr};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			return bytecodes[typeIndex(type) - 2];
		}
	},

	/**
	 * Specifies comparison. If the operand is a {@code float} or {@code
	 * double}, then the semantics of {@link JavaBytecode#fcmpg fcmpg} and
	 * {@link JavaBytecode#dcmpg dcmpg} are used.
	 */
	COMPARISON
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {fcmpg, dcmpg, null, lcmp};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			assert type.isPrimitive();
			final JavaBytecode bytecode = bytecodes[typeIndex(type)];
			if (bytecode == null)
			{
				throw new NullPointerException();
			}
			return bytecode;
		}
	},

	/**
	 * Specifies comparison. If the operand is a {@code float} or {@code
	 * double}, then the semantics of {@link JavaBytecode#fcmpl fcmpl} and
	 * {@link JavaBytecode#dcmpl dcmpl} are used.
	 */
	COMPARISON_L
	{
		/** The possible {@linkplain JavaBytecode bytecodes}. */
		private final JavaBytecode[] bytecodes = {fcmpl, dcmpl, null, lcmp};

		@Override
		JavaBytecode bytecode (final Class<?> type)
		{
			assert type.isPrimitive();
			final JavaBytecode bytecode = bytecodes[typeIndex(type)];
			if (bytecode == null)
			{
				throw new NullPointerException();
			}
			return bytecode;
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
