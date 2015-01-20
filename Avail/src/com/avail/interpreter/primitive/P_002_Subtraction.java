/**
 * P_002_Subtraction.java
 * Copyright © 1993-2014, The Avail Foundation, LLC.
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

import static com.avail.descriptor.InfinityDescriptor.*;
import static com.avail.descriptor.TypeDescriptor.Types.NUMBER;
import static com.avail.exceptions.AvailErrorCode.E_CANNOT_SUBTRACT_LIKE_INFINITIES;
import static com.avail.interpreter.Primitive.Flag.*;
import static com.avail.interpreter.Primitive.Fallibility.*;
import java.util.List;
import com.avail.descriptor.*;
import com.avail.exceptions.ArithmeticException;
import com.avail.interpreter.*;

/**
 * <strong>Primitive 2:</strong> Subtract {@linkplain
 * AbstractNumberDescriptor number} b from a.
 */
public final class P_002_Subtraction
extends Primitive
{
	/**
	 * The sole instance of this primitive class.  Accessed through reflection.
	 */
	public final static Primitive instance =
		new P_002_Subtraction().init(
			2, CanFold, CanInline);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 2;
		final A_Number a = args.get(0);
		final A_Number b = args.get(1);
		try
		{
			return interpreter.primitiveSuccess(a.minusCanDestroy(b, true));
		}
		catch (final ArithmeticException e)
		{
			return interpreter.primitiveFailure(e);
		}
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				NUMBER.o(),
				NUMBER.o()),
			NUMBER.o());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type aType = argumentTypes.get(0);
		final A_Type bType = argumentTypes.get(1);

		try
		{
			if (aType.isEnumeration() && bType.isEnumeration())
			{
				final A_Set aInstances = aType.instances();
				final A_Set bInstances = bType.instances();
				// Compute the Cartesian product as an enumeration if there will
				// be few enough entries.
				if (aInstances.setSize() * (long)bInstances.setSize() < 100)
				{
					A_Set answers = SetDescriptor.empty();
					for (final A_Number aInstance : aInstances)
					{
						for (final A_Number bInstance : bInstances)
						{
							answers = answers.setWithElementCanDestroy(
								aInstance.minusCanDestroy(bInstance, false),
								false);
						}
					}
					return AbstractEnumerationTypeDescriptor.withInstances(
						answers);
				}
			}
			if (aType.isIntegerRangeType() && bType.isIntegerRangeType())
			{
				final A_Number low = aType.lowerBound().minusCanDestroy(
					bType.upperBound(),
					false);
				final A_Number high = aType.upperBound().minusCanDestroy(
					bType.lowerBound(),
					false);
				final boolean includesNegativeInfinity =
					negativeInfinity().isInstanceOf(aType)
					|| positiveInfinity().isInstanceOf(bType);
				final boolean includesInfinity =
					positiveInfinity().isInstanceOf(aType)
					|| negativeInfinity().isInstanceOf(bType);
				return IntegerRangeTypeDescriptor.create(
					low.minusCanDestroy(IntegerDescriptor.one(), false),
					includesNegativeInfinity,
					high.plusCanDestroy(IntegerDescriptor.one(), false),
					includesInfinity);
			}
		}
		catch (final ArithmeticException e)
		{
			// $FALL-THROUGH$
		}
		return super.returnTypeGuaranteedByVM(
			argumentTypes);
	}

	@Override
	public Fallibility fallibilityForArgumentTypes (
		final List<? extends A_Type> argumentTypes)
	{
		final A_Type aType = argumentTypes.get(0);
		final A_Type bType = argumentTypes.get(1);

		final boolean aTypeIncludesNegativeInfinity =
			negativeInfinity().isInstanceOf(aType);
		final boolean aTypeIncludesInfinity =
			positiveInfinity().isInstanceOf(aType);
		final boolean bTypeIncludesNegativeInfinity =
			negativeInfinity().isInstanceOf(bType);
		final boolean bTypeIncludesInfinity =
			positiveInfinity().isInstanceOf(bType);
		if ((aTypeIncludesNegativeInfinity && bTypeIncludesNegativeInfinity)
			|| (aTypeIncludesInfinity && bTypeIncludesInfinity))
		{
			return CallSiteCanFail;
		}
		return CallSiteCannotFail;
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstance(
			E_CANNOT_SUBTRACT_LIKE_INFINITIES.numericCode());
	}
}