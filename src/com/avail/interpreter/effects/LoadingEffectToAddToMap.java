/**
 * LoadingEffectToAddToMap.java
 * Copyright © 1993-2017, The Avail Foundation, LLC.
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

package com.avail.interpreter.effects;

import com.avail.descriptor.A_BasicObject;
import com.avail.descriptor.A_Variable;
import com.avail.descriptor.MethodDescriptor.SpecialAtom;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;

/**
 * A {@code LoadingEffectToAddToMap} summarizes the addition of a key/value pair
 * to a map within a variable, without looking at anything else inside that
 * variable's map.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public class LoadingEffectToAddToMap extends LoadingEffect
{
	/** The variable containing the map. */
	private final A_Variable mapVariable;

	/** The key to add to the variable's map. */
	private final A_BasicObject key;

	/** The value to store under the key in the variable's map. */
	private final A_BasicObject value;

	/**
	 * Construct a new {@link LoadingEffectToAddToMap}.
	 *
	 * @param mapVariable
	 *        The variable holding the map to be updated.
	 * @param key
	 *        The key to update or add.
	 * @param value
	 *        The value to record in the map under the given key.
	 */
	public LoadingEffectToAddToMap (
		final A_Variable mapVariable,
		final A_BasicObject key,
		final A_BasicObject value)
	{
		assert !mapVariable.isInitializedWriteOnceVariable();
		this.mapVariable = mapVariable;
		this.key = key;
		this.value = value;
	}

	@Override
	public void writeEffectTo (final L1InstructionWriter writer)
	{
		// Push the variable holding the map.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(mapVariable));
		// Push the key.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(key));
		// Push the value.
		writer.write(
			L1Operation.L1_doPushLiteral,
			writer.addLiteral(value));
		// Call the primitive that adds the key/value to the variable's map.
		writer.write(
			L1Operation.L1_doCall,
			writer.addLiteral(SpecialAtom.ADD_TO_MAP_VARIABLE.bundle),
			writer.addLiteral(Types.TOP.o()));
	}
}
