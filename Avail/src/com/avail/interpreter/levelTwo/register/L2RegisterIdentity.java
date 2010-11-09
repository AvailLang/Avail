/**
 * interpreter/levelTwo/register/L2RegisterIdentity.java
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

public class L2RegisterIdentity
{
	int _finalIndex = -1;
	long _printId = -1;


	// accessing

	public int finalIndex ()
	{
		//  Answer which (virtual) machine register this conceptual register is mapped to.

		return _finalIndex;
	}

	public void finalIndex (
			final int anInteger)
	{
		//  Set which (virtual) machine register to map this conceptual register to.

		assert _finalIndex == -1 : "Only set the finalIndex of an L2RegisterIdentity once";
		_finalIndex = anInteger;
	}



	// java printing

	public long printId ()
	{
		if (_printId == -1)
		{
			_printId = UniqueId++;
		}
		return _printId;
	}

	public void printOn (
			final StringBuilder aStream)
	{
		aStream.append("Id#");
		aStream.append(_printId);
		if (_finalIndex != -1)
		{
			aStream.append("[");
			aStream.append(_finalIndex);
			aStream.append("]");
		}
	}





	static long UniqueId = 1;

}
