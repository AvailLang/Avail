/*
 * JVMCodeGenerationUtility.java
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

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A {@code JVMCodeGenerationUtility} is a utility class with helpful static
 * methods for doing code generation.
 *
 * @author Richard A Arriaga &lt;rarriaga@safetyweb.org&gt;
 */
public class JVMCodeGenerationUtility
{
	/**
	 * Emit the effect of loading a constant {@code int} to the specified
	 * {@link MethodVisitor}.
	 *
	 * @param v
	 *        The {@code MethodVisitor}.
	 * @param value
	 *        The {@code int}.
	 */
	public static void emitIntConstant (
		final @NotNull MethodVisitor v,
		final int value)
	{
		switch (value)
		{
			case -1:
				v.visitInsn(Opcodes.ICONST_M1);
				break;
			case 0:
				v.visitInsn(Opcodes.ICONST_0);
				break;
			case 1:
				v.visitInsn(Opcodes.ICONST_1);
				break;
			case 2:
				v.visitInsn(Opcodes.ICONST_2);
				break;
			case 3:
				v.visitInsn(Opcodes.ICONST_3);
				break;
			case 4:
				v.visitInsn(Opcodes.ICONST_4);
				break;
			case 5:
				v.visitInsn(Opcodes.ICONST_5);
				break;
			default:
			{
				if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)
				{
					v.visitIntInsn(Opcodes.BIPUSH, value);
				}
				else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
				{
					v.visitIntInsn(Opcodes.SIPUSH, value);
				}
				else
				{
					v.visitLdcInsn(value);
				}
				break;
			}
		}
	}

	/**
	 * Emit the effect of loading a constant {@code long} to the specified
	 * {@link MethodVisitor}.
	 *
	 * @param v
	 *        The {@code MethodVisitor}.
	 * @param value
	 *        The {@code long}.
	 */
	public static void emitLongConstant (
		final @NotNull MethodVisitor v,
		final long value)
	{
		if (value == 0L)
		{
			v.visitInsn(Opcodes.LCONST_0);
		}
		else if (value == 1L)
		{
			v.visitInsn(Opcodes.LCONST_1);
		}
		else if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE)
		{
			emitIntConstant(v, (int) value);
			// Emit a conversion, so that we end up with a long on the stack.
			v.visitInsn(Opcodes.I2L);
		}
		else
		{
			// This should emit an ldc2_w instruction, whose result type
			// is long; no conversion instruction is required.
			v.visitLdcInsn(value);
		}
	}

	/**
	 * Emit the effect of loading a constant {@code float} to the specified
	 * {@link MethodVisitor}.
	 *
	 * @param v
	 *        The {@code MethodVisitor}.
	 * @param value
	 *        The {@code float}.
	 */
	public static void emitFloatConstant (
		final @NotNull MethodVisitor v,
		final float value)
	{
		if (value == 0.0f)
		{
			v.visitInsn(Opcodes.FCONST_0);
		}
		else if (value == 1.0f)
		{
			v.visitInsn(Opcodes.FCONST_1);
		}
		else if (value == 2.0f)
		{
			v.visitInsn(Opcodes.FCONST_2);
		}
		// This is the largest absolute int value that will fit into the
		// mantissa of a normalized float, and therefore the largest value that
		// can be pushed and converted to float without loss of precision.
		else if (
			value >= -33_554_431 && value <= 33_554_431
			&& value == Math.floor(value))
		{
			emitIntConstant(v, (int) value);
			v.visitInsn(Opcodes.I2F);
		}
		else
		{
			// This should emit an ldc instruction, whose result type is float;
			// no conversion instruction is required.
			v.visitLdcInsn(value);
		}
	}

	/**
	 * Emit the effect of loading a constant {@code float} to the specified
	 * {@link MethodVisitor}.
	 *
	 * @param v
	 *        The {@code MethodVisitor}.
	 * @param value
	 *        The {@code double}.
	 */
	public static void emitDoubleConstant (
		final @NotNull MethodVisitor v,
		final double value)
	{
		if (value == 0.0d)
		{
			v.visitInsn(Opcodes.DCONST_0);
		}
		else if (value == 1.0d)
		{
			v.visitInsn(Opcodes.DCONST_1);
		}
		// This is the largest absolute int value that will fit into the
		// mantissa of a normalized double, and therefore the largest absolute
		// value that can be pushed and converted to double without loss of
		// precision.
		else if (
			value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
				&& value == Math.floor(value))
		{
			emitIntConstant(v, (int) value);
			v.visitInsn(Opcodes.I2D);
		}
		// This is the largest absolute long value that will fit into the
		// mantissa of a normalized double, and therefore the largest absolute
		// value that can be pushed and converted to double without loss of
		// precision.
		else if (
			value >= -18_014_398_509_481_983L
				&& value <= 18_014_398_509_481_983L
				&& value == Math.floor(value))
		{
			emitLongConstant(v, (long) value);
			v.visitInsn(Opcodes.L2D);
		}
		else
		{
			// This should emit an ldc2_w instruction, whose result type is
			// double; no conversion instruction is required.
			v.visitLdcInsn(value);
		}
	}
}
