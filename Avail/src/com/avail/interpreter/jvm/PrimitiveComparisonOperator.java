/**
 * PrimitiveComparisonOperator.java
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

import static com.avail.interpreter.jvm.JavaBytecode.*;

/**
 * {@code PrimitiveComparisionOperator} specifies one of the six primitive
 * comparisons supported by the Java virtual machine.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public enum PrimitiveComparisonOperator
{
	/** Specify that the operands should be compared for equality. */
	EQUALS (ifeq)
	{
		@Override
		PrimitiveComparisonOperator inverse ()
		{
			return NOT_EQUALS;
		}
	},

	/** Specify that the operands should be compared for inequality. */
	NOT_EQUALS (ifne)
	{
		@Override
		PrimitiveComparisonOperator inverse ()
		{
			return EQUALS;
		}
	},

	/**
	 * Specify that it should be ascertained whether operand {@code a} is
	 * strictly less than operand {@code b}.
	 */
	LESS_THAN (iflt)
	{
		@Override
		PrimitiveComparisonOperator inverse ()
		{
			return GREATER_THAN_EQUALS;
		}
	},

	/**
	 * Specify that it should be ascertained whether operand {@code a} is
	 * less than or equal to operand {@code b}.
	 */
	LESS_THAN_EQUALS (ifle)
	{
		@Override
		PrimitiveComparisonOperator inverse ()
		{
			return GREATER_THAN;
		}
	},

	/**
	 * Specify that it should be ascertained whether operand {@code a} is
	 * strictly greater than operand {@code b}.
	 */
	GREATER_THAN (ifgt)
	{
		@Override
		PrimitiveComparisonOperator inverse ()
		{
			return LESS_THAN_EQUALS;
		}
	},

	/**
	 * Specify that it should be ascertained whether operand {@code a} is
	 * greater than or equal to operand {@code b}.
	 */
	GREATER_THAN_EQUALS (ifge)
	{
		@Override
		PrimitiveComparisonOperator inverse ()
		{
			return LESS_THAN;
		}
	};

	/**
	 * The {@linkplain JavaBytecode bytecode} that implements the comparison.
	 */
	private final JavaBytecode bytecode;

	/**
	 * Answer the {@linkplain JavaBytecode bytecode} that implements the
	 * comparison.
	 *
	 * @return The bytecode.
	 */
	JavaBytecode bytecode ()
	{
		return bytecode;
	}

	/**
	 * Answer the inverse of the {@linkplain PrimitiveComparisonOperator
	 * comparison operator}.
	 *
	 * @return The inverse comparison operator.
	 */
	abstract PrimitiveComparisonOperator inverse ();

	/**
	 * Answer the {@linkplain JavaBytecode bytecode} that corresponds to the
	 * {@linkplain #inverse() inverse} {@linkplain PrimitiveComparisonOperator
	 * comparison operator}.
	 *
	 * @return The bytecode of the inverse comparison operator.
	 */
	JavaBytecode inverseBytecode ()
	{
		return inverse().bytecode;
	}

	/**
	 * Construct a new {@link PrimitiveComparisonOperator}.
	 *
	 * @param bytecode
	 *        The {@linkplain JavaBytecode bytecode} that implements the
	 *        comparison.
	 */
	private PrimitiveComparisonOperator (final JavaBytecode bytecode)
	{
		this.bytecode = bytecode;
	}
}
