/**
 * descriptor/SetBinDescriptor.java
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

package com.avail.descriptor;

public abstract class SetBinDescriptor extends Descriptor
{
	byte _level;


	// private-accessing

	byte level ()
	{
		//  Answer what level I am in the hash trie.  The top node is level 0 (using hash bits 0..4),
		//  and the bottom hashed node is level 6 (using hash bits 30..34, the top three of which
		//  are always zero).  There can be a level 7 linear bin, but it represents elements which
		//  all have the same hash value, so it should never be hashed.

		return _level;
	}

	void level (
			final byte anInteger)
	{
		//  Set level in this descriptor instance.

		_level = anInteger;
	}





	//  Descriptor initialization
	Descriptor initDescriptorWithLevel(
			int theId,
			boolean mut,
			int numFixedObjectSlots,
			int numFixedIntegerSlots,
			boolean hasVariableObjectSlots,
			boolean hasVariableIntegerSlots,
			int theLevel)
	{
		initDescriptor(
			theId,
			mut,
			numFixedObjectSlots,
			numFixedIntegerSlots,
			hasVariableObjectSlots,
			hasVariableIntegerSlots);
		_level = (byte) theLevel;
		return this;
	};

}
