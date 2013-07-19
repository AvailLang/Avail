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

import com.avail.annotations.Nullable;

/**
 * {@code JavaOperand} describes a stack operand for the Java virtual machine.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
enum JavaOperand
{
	/** The operand is a {@code reference} index. */
	OBJECTREF (Object.class)
	{
		@Override
		public JavaOperand computationalCategory ()
		{
			throw new UnsupportedOperationException();
		}
	},

	/** The operand is {@code null}. */
	NULL (OBJECTREF, Object.class)
	{
		@Override
		public JavaOperand computationalCategory ()
		{
			throw new UnsupportedOperationException();
		}
	},

	/** The operand is an array reference. */
	ARRAYREF (OBJECTREF, Object.class)
	{
		@Override
		public JavaOperand computationalCategory ()
		{
			throw new UnsupportedOperationException();
		}
	},

	/** The operand is an {@code int}. */
	INT (Integer.TYPE),

	/** The operand is an {@code int} -1. */
	INT_M1 (INT, Integer.TYPE),

	/** The operand is an {@code int} 0. */
	INT_0 (INT, Integer.TYPE),

	/** The operand is an {@code int} 1. */
	INT_1 (INT, Integer.TYPE),

	/** The operand is an {@code int} 2. */
	INT_2 (INT, Integer.TYPE),

	/** The operand is an {@code int} 3. */
	INT_3 (INT, Integer.TYPE),

	/** The operand is an {@code int} 4. */
	INT_4 (INT, Integer.TYPE),

	/** The operand is an {@code int} 5. */
	INT_5 (INT, Integer.TYPE),

	/** The operand is an {@code int} index. */
	INDEX (INT, Integer.TYPE),

	/** The operand is an {@code int} count. */
	COUNT (INT, Integer.TYPE),

	/** The operand is an {@code int} array length. */
	LENGTH (INT, Integer.TYPE),

	/** The operand is an {@code int} signum. */
	SIGNUM (INT, Integer.TYPE),

	/** The operand is an {@code int} 0 or 1. */
	ZERO_OR_ONE (INT, Integer.TYPE),

	/** The operand is a {@code long}. */
	LONG (Long.TYPE)
	{
		@Override
		public JavaOperand computationalCategory ()
		{
			return CATEGORY_2;
		}
	},

	/** The operand is a {@code long} 0L. */
	LONG_0 (LONG, Long.TYPE)
	{
		@Override
		public JavaOperand computationalCategory ()
		{
			return CATEGORY_2;
		}
	},

	/** The operand is a {@code long} 1L. */
	LONG_1 (LONG, Long.TYPE)
	{
		@Override
		public JavaOperand computationalCategory ()
		{
			return CATEGORY_2;
		}
	},

	/** The operand is a {@code float}. */
	FLOAT (Float.TYPE),

	/** The operand is a {@code float} 0.0f. */
	FLOAT_0 (FLOAT, Float.TYPE),

	/** The operand is a {@code float} 1.0f. */
	FLOAT_1 (FLOAT, Float.TYPE),

	/** The operand is a {@code float} 2.0f. */
	FLOAT_2 (FLOAT, Float.TYPE),

	/** The operand is a {@code double}. */
	DOUBLE (Double.TYPE)
	{
		@Override
		public JavaOperand computationalCategory ()
		{
			return CATEGORY_2;
		}
	},

	/** The operand is a {@code double} 0.0d. */
	DOUBLE_0 (DOUBLE, Double.TYPE)
	{
		@Override
		public JavaOperand computationalCategory ()
		{
			return CATEGORY_2;
		}
	},

	/** The operand is a {@code double} 1.0d. */
	DOUBLE_1 (DOUBLE, Double.TYPE)
	{
		@Override
		public JavaOperand computationalCategory ()
		{
			return CATEGORY_2;
		}
	},

	/**
	 * The operand is a category 1 computational type.
	 *
	 * @see <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-2.html#jvms-2.11.1">
	 *      Types and the Java Virtual Machine</a>
	 */
	CATEGORY_1 (Void.TYPE)
	{
		@Override
		public boolean isCategory ()
		{
			return true;
		}
	},

	/**
	 * The operand is a category 2 computational type.
	 *
	 * @see <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-2.html#jvms-2.11.1">
	 *      Types and the Java Virtual Machine</a>
	 */
	CATEGORY_2 (Void.TYPE)
	{
		@Override
		public boolean isCategory ()
		{
			return true;
		}

		@Override
		public JavaOperand computationalCategory ()
		{
			return CATEGORY_2;
		}
	},

	/** The operand is an arbitrary field. */
	VALUE (Void.TYPE)
	{
		@Override
		public JavaOperand computationalCategory ()
		{
			throw new UnsupportedOperationException();
		}
	},

	/** The operand is a return address. */
	RETURN_ADDRESS (Void.TYPE)
	{
		@Override
		public JavaOperand computationalCategory ()
		{
			throw new UnsupportedOperationException();
		}
	},

	/** Represents multiple operands. */
	PLURAL (Void.TYPE)
	{
		@Override
		public JavaOperand computationalCategory ()
		{
			throw new UnsupportedOperationException();
		}
	};

	/** The base kind of the operand. */
	private final JavaOperand baseOperand;

	/**
	 * Answer the base kind of the {@linkplain JavaOperand operand}. This may be
	 * identical to the operand itself.
	 *
	 * @return The base kind of the operand.
	 */
	public JavaOperand baseOperand ()
	{
		return baseOperand;
	}

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
	 * Does the {@linkplain JavaOperand operand} represent one of the
	 * computational categories?
	 *
	 * @return {@code true} if the operand represents a computational category,
	 *         {@code false} otherwise.
	 * @see #CATEGORY_1
	 * @see #CATEGORY_2
	 */
	public boolean isCategory ()
	{
		return false;
	}

	/**
	 * Answer the computational category of the {@linkplain JavaOperand
	 * operand}.
	 *
	 * @return The computational category.
	 */
	public JavaOperand computationalCategory ()
	{
		return CATEGORY_1;
	}

	/**
	 * Construct a new {@link JavaOperand}.
	 *
	 * @param baseOperand
	 *        The base kind of the operand, or {@code null} if the base kind is
	 *        the same as the operand.
	 * @param type
	 *        The representational {@linkplain Class Java type}.
	 */
	private JavaOperand (
		final @Nullable JavaOperand baseOperand,
		final Class<?> type)
	{
		this.baseOperand = baseOperand == null ? this : baseOperand;
		this.type = type;
	}

	/**
	 * Construct a new {@link JavaOperand}.
	 *
	 * @param type
	 *        The representational {@linkplain Class Java type}.
	 */
	private JavaOperand (final Class<?> type)
	{
		this(null, type);
	}

	/**
	 * Answer the {@linkplain JavaOperand operand} kind for the specified
	 * {@linkplain Class type}.
	 *
	 * @param type
	 *        A type.
	 * @return An operand kind.
	 */
	static JavaOperand forType (final Class<?> type)
	{
		if (type.isPrimitive())
		{
			if (type == Boolean.TYPE
				|| type == Byte.TYPE
				|| type == Short.TYPE
				|| type == Integer.TYPE)
			{
				return INT;
			}
			if (type == Float.TYPE)
			{
				return FLOAT;
			}
			if (type == Double.TYPE)
			{
				return DOUBLE;
			}
			assert type == Long.TYPE;
			return LONG;
		}
		return OBJECTREF;
	}
}
