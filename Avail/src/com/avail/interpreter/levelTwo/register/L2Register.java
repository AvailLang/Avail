/**
 * interpreter/levelTwo/register/L2Register.java
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

import com.avail.interpreter.levelTwo.register.L2RegisterIdentity;
import java.lang.Cloneable;

public class L2Register implements Cloneable
{
	final L2RegisterIdentity _identity = new L2RegisterIdentity();
	boolean _isLast = false;
	boolean _knowsIsLast = false;


	// accessing

	public int finalIndex ()
	{
		//  Answer which (virtual) machine register number to use.  This is only valid after the register assignment phase.

		return _identity.finalIndex();
	}

	public L2RegisterIdentity identity ()
	{
		return _identity;
	}

	public boolean isLastUse ()
	{
		return _isLast;
	}

	public void isLastUse (
			final boolean aBoolean)
	{
		_isLast = aBoolean;
		_knowsIsLast = true;
	}



	// java printing

	public void printOn (
			final StringBuilder aStream)
	{
		if ((_identity.finalIndex() != -1))
		{
			aStream.append("Reg[");
			aStream.append(_identity.finalIndex());
			aStream.append("]");
		}
		else
		{
			aStream.append("Reg_");
			aStream.append(_identity.printId());
		}
		if (_knowsIsLast)
		{
			if (_isLast)
			{
				aStream.append(" isLast");
			}
			else
			{
				aStream.append(" notLast");
			}
		}
	}





	@Override
	public L2Register clone () throws CloneNotSupportedException
	{
		return (L2Register)super.clone();
	}

}
