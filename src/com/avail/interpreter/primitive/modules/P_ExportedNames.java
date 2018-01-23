/**
 * P_ExportedNames.java
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

package com.avail.interpreter.primitive.modules;

import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import javax.annotation.Nullable;
import java.util.List;

import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.IntegerRangeTypeDescriptor.wholeNumbers;
import static com.avail.descriptor.SetTypeDescriptor.setTypeForSizesContentType;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.interpreter.Primitive.Flag.CanInline;

/**
 * <strong>Primitive:</strong> Answer the {@linkplain
 * A_Module#exportedNames() exported names} of the {@linkplain
 * ModuleDescriptor#currentModule() current module}.
 *
 * @author Todd Smith &lt;todd@availlang.org&gt;
 */
public final class P_ExportedNames
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_ExportedNames().init(
			0, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 0;
		final A_Fiber fiber = interpreter.fiber();
		final @Nullable AvailLoader loader = fiber.availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		final A_Module module = loader.module();
		final A_Set exportedNames = module.exportedNames();
		return interpreter.primitiveSuccess(exportedNames);
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			emptyTuple(),
			setTypeForSizesContentType(wholeNumbers(), ATOM.o()));
	}
}
