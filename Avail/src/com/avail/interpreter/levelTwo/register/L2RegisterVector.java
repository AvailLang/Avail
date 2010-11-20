/**
 * interpreter/levelTwo/register/L2RegisterVector.java
 * Copyright (c) 2010, Mark van Gulik.
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

package com.avail.interpreter.levelTwo.register;

import com.avail.interpreter.levelTwo.L2Translator;
import java.util.ArrayList;

public class L2RegisterVector
{
	final ArrayList<L2ObjectRegister> _registers;

	/**
	 * 
	 * Construct a new {@link L2RegisterVector} containing the given registers.
	 *
	 * @param objectRegisters the registers to put in the new vector.
	 */
	public L2RegisterVector (
		final ArrayList<L2ObjectRegister> objectRegisters)
	{
		_registers = objectRegisters;
	}


	// accessing

	public ArrayList<L2ObjectRegister> registers ()
	{
		//  Answer the collection of object registers.

		return _registers;
	}


	// folding

	public boolean allRegistersAreConstantsIn (
			final L2Translator anL2Translator)
	{
		for (int i = 1, _end1 = _registers.size(); i <= _end1; i++)
		{
			if (!anL2Translator.registerHasConstantAt(_registers.get(i - 1)))
			{
				return false;
			}
		}
		return true;
	}





}
