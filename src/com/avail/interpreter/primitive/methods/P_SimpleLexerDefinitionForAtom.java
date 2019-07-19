/*
 * P_SimpleLexerDefinitionForAtom.java
 * Copyright © 1993-2018, The Avail Foundation, LLC.
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

package com.avail.interpreter.primitive.methods;

import com.avail.AvailTask;
import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Lexer;
import com.avail.descriptor.A_Method;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.MethodDescriptor.SpecialMethodAtom;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.effects.LoadingEffectToRunPrimitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;

import javax.annotation.Nullable;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.compiler.splitter.MessageSplitter.possibleErrors;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.LexerDescriptor.*;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.formatString;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.descriptor.TypeDescriptor.Types.TOP;
import static com.avail.exceptions.AvailErrorCode.E_CANNOT_DEFINE_DURING_COMPILATION;
import static com.avail.exceptions.AvailErrorCode.E_LOADING_IS_OVER;
import static com.avail.interpreter.AvailLoader.Phase.EXECUTING_FOR_COMPILE;
import static com.avail.interpreter.Primitive.Flag.CanSuspend;
import static com.avail.interpreter.Primitive.Flag.Unknown;
import static com.avail.interpreter.Primitive.Result.FIBER_SUSPENDED;
import static com.avail.utility.Nulls.stripNull;

/**
 * <strong>Primitive:</strong> Simple lexer definition.  The first argument
 * is the lexer name (an atom).  The second argument is a (stable, pure) filter
 * function which takes a character and answers true if the lexer should run
 * when that character is encountered in the input, otherwise false.  The third
 * argument is the body function, which takes a character, the source string,
 * and the current (one-based) index into it.  It may invoke a primitive to
 * accept zero or more lexings and/or a primitive to reject the lexing with a
 * diagnostic message.
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_SimpleLexerDefinitionForAtom
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_SimpleLexerDefinitionForAtom().init(
			3, CanSuspend, Unknown);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final A_Atom atom = interpreter.argument(0);
		final A_Function filterFunction = interpreter.argument(1);
		final A_Function bodyFunction = interpreter.argument(2);

		final A_Fiber fiber = interpreter.fiber();
		final @Nullable AvailLoader loader = fiber.availLoader();
		if (loader == null)
		{
			return interpreter.primitiveFailure(E_LOADING_IS_OVER);
		}
		if (!loader.phase().isExecuting())
		{
			return interpreter.primitiveFailure(
				E_CANNOT_DEFINE_DURING_COMPILATION);
		}
		final A_Bundle bundle;
		try
		{
			bundle = atom.bundleOrCreate();
		}
		catch (final MalformedMessageException e)
		{
			return interpreter.primitiveFailure(e.errorCode());
		}
		final A_Method method = bundle.bundleMethod();
		final A_Lexer lexer = newLexer(
			filterFunction, bodyFunction, method, loader.module());
		final A_Function primitiveFunction = stripNull(interpreter.function);
		interpreter.primitiveSuspend(primitiveFunction);
		interpreter.runtime().whenLevelOneSafeDo(
			fiber.priority(),
			AvailTask.forUnboundFiber(
				fiber,
				() ->
				{
					filterFunction.code().setMethodName(
						formatString("Filter for lexer %s",
							atom.atomName()));
					bodyFunction.code().setMethodName(
						formatString("Body for lexer %s", atom.atomName()));
					// Only update the loader's lexical scanner if we're
					// actually compiling, NOT if we're loading.  The loader
					// doesn't even have a lexical scanner during loading.
					if (loader.phase() == EXECUTING_FOR_COMPILE)
					{
						loader.lexicalScanner().addLexer(lexer);
					}
					loader.recordEffect(
						new LoadingEffectToRunPrimitive(
							SpecialMethodAtom.LEXER_DEFINER.bundle,
							atom,
							filterFunction,
							bodyFunction));
					Interpreter.resumeFromSuccessfulPrimitive(
						currentRuntime(), fiber, this, nil);
				}));
		return FIBER_SUSPENDED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				ATOM.o(),
				lexerFilterFunctionType(),
				lexerBodyFunctionType()),
			TOP.o());
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_LOADING_IS_OVER,
				E_CANNOT_DEFINE_DURING_COMPILATION
			).setUnionCanDestroy(possibleErrors, true));
	}
}
