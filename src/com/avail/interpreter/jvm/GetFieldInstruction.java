/**
 * GetFieldInstruction.java
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

import com.avail.interpreter.jvm.ConstantPool.FieldrefEntry;

/**
 * A {@code GetFieldInstruction} represents pushing a value from a {@linkplain
 * Field field} onto the operand stack.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
final class GetFieldInstruction
extends FieldAccessInstruction
{
	@Override
	JavaBytecode staticBytecode ()
	{
		return JavaBytecode.getstatic;
	}

	@Override
	JavaBytecode instanceBytecode ()
	{
		return JavaBytecode.getfield;
	}

	/**
	 * Construct a new {@link GetFieldInstruction}.
	 *
	 * @param fieldrefEntry
	 *        A {@linkplain FieldrefEntry field reference entry} for the
	 *        referenced {@linkplain Field field}.
	 * @param isStatic
	 *        {@code true} if the referenced field is {@code static}, {@code
	 *        false} otherwise.
	 */
	public GetFieldInstruction (
		final FieldrefEntry fieldrefEntry,
		final boolean isStatic)
	{
		super(fieldrefEntry, isStatic);
	}
}
