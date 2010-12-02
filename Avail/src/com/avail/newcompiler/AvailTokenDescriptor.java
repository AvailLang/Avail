/**
 * compiler/scanner/AvailToken.java
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

package com.avail.newcompiler;

import com.avail.descriptor.AvailObject;
import com.avail.newcompiler.ParseNodeDescriptor.ObjectSlots;

public class AvailTokenDescriptor
{

	enum ObjectSlots
	{
		STRING
	}

	enum IntegerSlots
	{
		START,
		TOKEN_TYPE_CODE
	}


	/**
	 * Setter for field string.
	 */
	@Override
	public void ObjectString (
			final AvailObject object,
			final AvailObject value)
	{
		object.objectSlotPut(ObjectSlots.STRING, value);
	}

	/**
	 * Getter for field string.
	 */
	@Override
	public AvailObject ObjectString (
			final AvailObject object)
	{
		return object.objectSlot(ObjectSlots.STRING);
	}

	/**
	 * Setter for field start.
	 */
	@Override
	public void ObjectStart (
			final AvailObject object,
			final AvailObject value)
	{
		object.integerSlotPut(IntegerSlots.START, value);
	}

	/**
	 * Getter for field start.
	 */
	@Override
	public AvailObject ObjectStart (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.START);
	}

	/**
	 * Setter for field tokenTypeCode.
	 */
	@Override
	public void ObjectTokenTypeCode (
			final AvailObject object,
			final AvailObject value)
	{
		object.integerSlotPut(IntegerSlots.TOKEN_TYPE_CODE, value);
	}

	/**
	 * Getter for field tokenTypeCode.
	 */
	@Override
	public AvailObject ObjectTokenTypeCode (
			final AvailObject object)
	{
		return object.integerSlot(IntegerSlots.TOKEN_TYPE_CODE);
	}



	public enum TokenType
	{
		END_OF_FILE, END_OF_STATEMENT, KEYWORD, LITERAL, OPERATOR;
	}

}
