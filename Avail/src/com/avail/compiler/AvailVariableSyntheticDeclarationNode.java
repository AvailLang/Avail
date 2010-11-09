/**
 * compiler/AvailVariableSyntheticDeclarationNode.java
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

package com.avail.compiler;

import com.avail.compiler.AvailCodeGenerator;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TypeDescriptor;

public class AvailVariableSyntheticDeclarationNode extends AvailVariableDeclarationNode
{
	AvailObject _availVariable;


	// accessing

	public void availVariable (
			final AvailObject anAvailObject)
	{
		//  Set the constant variable.


		assert anAvailObject.isInstanceOfSubtypeOf(TypeDescriptor.container());
		_availVariable = anAvailObject;
	}



	// code generation

	public void emitVariableAssignmentOn (
			final AvailCodeGenerator codeGenerator)
	{
		//  Emit an assignment to this variable.

		codeGenerator.emitSetLiteral(_availVariable);
	}

	public void emitVariableReferenceOn (
			final AvailCodeGenerator codeGenerator)
	{
		//  Emit a reference to this variable.

		codeGenerator.emitPushLiteral(_availVariable);
	}

	public void emitVariableValueOn (
			final AvailCodeGenerator codeGenerator)
	{
		//  Emit the value of this variable (or constant or label or module variable or module constant).

		codeGenerator.emitGetLiteral(_availVariable);
	}



	// testing

	public boolean isSyntheticVariableDeclaration ()
	{
		return true;
	}





}
