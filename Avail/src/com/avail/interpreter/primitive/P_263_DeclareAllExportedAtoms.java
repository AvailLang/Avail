/**
 * P_263_DeclareAllExportedAtoms.java
 * Copyright © 1993-2012, Mark van Gulik and Todd L Smith.
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

package com.avail.interpreter.primitive;

import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 263</strong>: This private primitive is used to ensure that
 * a module can deserialize correctly.  It forces the given set of atoms to be
 * included in the current module's {@linkplain
 * com.avail.descriptor.ModuleDescriptor.ObjectSlots#NAMES public names} or
 * {@linkplain com.avail.descriptor.ModuleDescriptor.ObjectSlots#PRIVATE_NAMES
 * private names}, depending on the value of the supplied {@linkplain
 * EnumerationTypeDescriptor#booleanObject() boolean} ({@link
 * AtomDescriptor#trueObject() true} for public, {@link
 * AtomDescriptor#falseObject() false} for private).
 *
 * @author Mark van Gulik &lt;ghoul137@gmail.com&gt;
 */
public final class P_263_DeclareAllExportedAtoms
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_263_DeclareAllExportedAtoms().init(2, CannotFail, Private);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter)
	{
		assert args.size() == 2;
		final AvailObject names = args.get(0);
		final AvailObject isPublic = args.get(1);
		final AvailObject module = interpreter.module();
		if (isPublic.extractBoolean())
		{
			for (final AvailObject name : names)
			{
				module.atNameAdd(name.name(), name);
			}
		}
		else
		{
			for (final AvailObject name : names)
			{
				module.atPrivateNameAdd(name.name(), name);
			}
		}
		return interpreter.primitiveSuccess(NullDescriptor.nullObject());
	}

	@Override
	protected AvailObject privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				SetTypeDescriptor.setTypeForSizesContentType(
					IntegerRangeTypeDescriptor.wholeNumbers(),
					ATOM.o()),
				EnumerationTypeDescriptor.booleanObject()),
			TOP.o());
	}
}
