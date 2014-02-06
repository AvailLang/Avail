/**
 * LoadInstruction.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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
 * A {@code LoadInstruction} represents pushing the value in a {@linkplain
 * LocalVariable local variable} onto the operand stack.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class LoadInstruction
extends LocalVariableAccessInstruction
{
	/**
	 * The {@linkplain JavaBytecode bytecodes} for loading {@linkplain
	 * LocalVariable local variables}.
	 */
	private static final JavaBytecode[] loadBytecodes =
	{
		aload_0, aload_1, aload_2, aload_3, aload,
		dload_0, dload_1, dload_2, dload_3, dload,
		fload_0, fload_1, fload_2, fload_3, fload,
		iload_0, iload_1, iload_2, iload_3, iload,
		lload_0, lload_1, lload_2, lload_3, lload
	};

	@Override
	JavaBytecode[] bytecodes ()
	{
		return loadBytecodes;
	}

	/**
	 * Construct a new {@link LoadInstruction}.
	 *
	 * @param local
	 *        The {@linkplain LocalVariable local variable}.
	 */
	LoadInstruction (final LocalVariable local)
	{
		super(local);
	}
}
