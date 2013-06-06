/**
 * JavaOperand.java
 * Copyright © 1993-2013, Mark van Gulik and Todd L Smith.
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

/**
 * {@code JavaOperand} describes a stack operand for the Java virtual machine.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
enum JavaOperand
{
	/** The operand is a {@code reference} index. */
	OBJECTREF (Object.class),

	/** The operand is {@code null}. */
	NULL (Object.class),

	/** The operand is an array reference. */
	ARRAYREF (Object.class),

	/** The operand is an {@code int}. */
	INT (Integer.TYPE),

	/** The operand is an {@code int} -1. */
	INT_M1 (Integer.TYPE),

	/** The operand is an {@code int} 0. */
	INT_0 (Integer.TYPE),

	/** The operand is an {@code int} 1. */
	INT_1 (Integer.TYPE),

	/** The operand is an {@code int} 2. */
	INT_2 (Integer.TYPE),

	/** The operand is an {@code int} 3. */
	INT_3 (Integer.TYPE),

	/** The operand is an {@code int} 4. */
	INT_4 (Integer.TYPE),

	/** The operand is an {@code int} 5. */
	INT_5 (Integer.TYPE),

	/** The operand is an {@code int} index. */
	INDEX (Integer.TYPE),

	/** The operand is an {@code int} count. */
	COUNT (Integer.TYPE),

	/** The operand is an {@code int} array length. */
	LENGTH (Integer.TYPE),

	/** The operand is an {@code int} signum. */
	SIGNUM (Integer.TYPE),

	/** The operand is an {@code int} 0 or 1. */
	ZERO_OR_ONE (Integer.TYPE),

	/** The operand is a {@code long}. */
	LONG (Long.TYPE),

	/** The operand is a {@code long} 0L. */
	LONG_0 (Long.TYPE),

	/** The operand is a {@code long} 1L. */
	LONG_1 (Long.TYPE),

	/** The operand is a {@code float}. */
	FLOAT (Float.TYPE),

	/** The operand is a {@code float} 0.0f. */
	FLOAT_0 (Float.TYPE),

	/** The operand is a {@code float} 1.0f. */
	FLOAT_1 (Float.TYPE),

	/** The operand is a {@code float} 2.0f. */
	FLOAT_2 (Float.TYPE),

	/** The operand is a {@code double}. */
	DOUBLE (Double.TYPE),

	/** The operand is a {@code double} 0.0d. */
	DOUBLE_0 (Double.TYPE),

	/** The operand is a {@code double} 1.0d. */
	DOUBLE_1 (Double.TYPE),

	/**
	 * The operand is a category 1 computational type.
	 *
	 * @see <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-2.html#jvms-2.11.1">
	 *      Types and the Java Virtual Machine</a>
	 */
	CATEGORY_1 (Void.TYPE),

	/**
	 * The operand is a category 2 computational type.
	 *
	 * @see <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-2.html#jvms-2.11.1">
	 *      Types and the Java Virtual Machine</a>
	 */
	CATEGORY_2 (Void.TYPE),

	/** The operand is an arbitrary field. */
	VALUE (Void.TYPE),

	/** The operand is a return address. */
	RETURN_ADDRESS (Void.TYPE),

	/** Represents multiple operands. */
	PLURAL (Void.TYPE);

	/** The representational Java {@linkplain Class type}. */
	private final Class<?> type;

	/**
	 * Answer the representational Java {@linkplain Class type}.
	 *
	 * @return The type.
	 */
	public Class<?> type ()
	{
		return type;
	}

	/**
	 * Construct a new {@link JavaOperand}.
	 *
	 * @param type
	 *        The representational {@linkplain Class Java type}.
	 */
	private JavaOperand (final Class<?> type)
	{
		this.type = type;
	}
}
