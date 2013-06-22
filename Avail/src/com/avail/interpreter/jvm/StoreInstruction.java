/**
 * StoreInstruction.java
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
 * A {@code StoreInstruction} represents storing the value on top of the operand
 * stack into a {@linkplain LocalVariable local variable}.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class StoreInstruction
extends LocalVariableAccessInstruction
{
	/**
	 * The {@linkplain JavaBytecode bytecodes} for updating {@linkplain
	 * LocalVariable local variables}.
	 */
	private static final JavaBytecode[] storeBytecodes =
	{
		astore_0, astore_1, astore_2, astore_3, astore,
		dstore_0, dstore_1, dstore_2, dstore_3, dstore,
		fstore_0, fstore_1, fstore_2, fstore_3, fstore,
		istore_0, istore_1, istore_2, istore_3, istore,
		lstore_0, lstore_1, lstore_2, lstore_3, lstore
	};

	@Override
	JavaBytecode[] bytecodes ()
	{
		return storeBytecodes;
	}

	/**
	 * Construct a new {@link StoreInstruction}.
	 *
	 * @param local
	 *        The {@linkplain LocalVariable local variable}.
	 */
	StoreInstruction (final LocalVariable local)
	{
		super(local);
	}
}
