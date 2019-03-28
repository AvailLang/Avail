/*
 * P_CreateRestrictedSendExpression.java
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

package com.avail.interpreter.primitive.phrases;

import com.avail.AvailRuntime;
import com.avail.compiler.AvailAcceptedParseException;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.A_Atom;
import com.avail.descriptor.A_Bundle;
import com.avail.descriptor.A_Definition;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Function;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.A_Phrase;
import com.avail.descriptor.A_RawFunction;
import com.avail.descriptor.A_SemanticRestriction;
import com.avail.descriptor.A_Set;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Tuple;
import com.avail.descriptor.A_Type;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.ListPhraseDescriptor;
import com.avail.descriptor.PhraseTypeDescriptor.PhraseKind;
import com.avail.descriptor.SendPhraseDescriptor;
import com.avail.descriptor.TypeDescriptor;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.utility.Mutable;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.avail.AvailRuntime.currentRuntime;
import static com.avail.compiler.splitter.MessageSplitter.possibleErrors;
import static com.avail.descriptor.AbstractEnumerationTypeDescriptor.enumerationWith;
import static com.avail.descriptor.FiberDescriptor.currentFiber;
import static com.avail.descriptor.FiberDescriptor.newFiber;
import static com.avail.descriptor.FunctionTypeDescriptor.functionType;
import static com.avail.descriptor.InstanceMetaDescriptor.topMeta;
import static com.avail.descriptor.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.LIST_PHRASE;
import static com.avail.descriptor.PhraseTypeDescriptor.PhraseKind.SEND_PHRASE;
import static com.avail.descriptor.SendPhraseDescriptor.newSendNode;
import static com.avail.descriptor.SetDescriptor.set;
import static com.avail.descriptor.StringDescriptor.stringFrom;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.descriptor.TupleDescriptor.toList;
import static com.avail.descriptor.TypeDescriptor.Types.ATOM;
import static com.avail.exceptions.AvailErrorCode.E_INCORRECT_NUMBER_OF_ARGUMENTS;
import static com.avail.exceptions.AvailErrorCode.E_NO_METHOD_DEFINITION;
import static com.avail.interpreter.Interpreter.resumeFromFailedPrimitive;
import static com.avail.interpreter.Interpreter.resumeFromSuccessfulPrimitive;
import static com.avail.interpreter.Interpreter.runOutermostFunction;
import static com.avail.interpreter.Primitive.Flag.CanSuspend;
import static com.avail.interpreter.Primitive.Flag.Unknown;
import static com.avail.interpreter.Primitive.Result.FIBER_SUSPENDED;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.Strings.increaseIndentation;

/**
 * <strong>Primitive CreateRestrictedSendExpression</strong>: Create a
 * {@linkplain SendPhraseDescriptor send phrase} from the specified {@linkplain
 * A_Bundle message bundle}, {@linkplain ListPhraseDescriptor list phrase} of
 * {@linkplain PhraseKind#EXPRESSION_PHRASE argument expressions}, and
 * {@linkplain TypeDescriptor return type}.  In addition, run all semantic
 * restrictions in separate fibers.  The resulting send phrase's return type
 * will be the intersection of the supplied type, the return types produced by
 * the semantic restrictions, and the applicable method definitions' return
 * types.
 *
 * <p>In the event that one or more semantic restrictions should fail, their
 * failure reasons will be captured and combined into a suitable composite
 * string.  This primitive will then fail with the composite string as the
 * failure value.  It is expected that the Avail primitive failure code will
 * simply invoke {@link P_RejectParsing} with that string to report the
 * encountered problems within the original fiber.</p>
 *
 * <p>The primitive may also fail (with a suitable string) if the number of
 * arguments is incorrect, but no further checking is performed.  If there are
 * no applicable method definitions for the supplied types, they will simply not
 * contribute to the strengthened return type.</p>
 *
 * @author Mark van Gulik &lt;mark@availlang.org&gt;
 */
public final class P_CreateRestrictedSendExpression
extends Primitive
{
	/**
	 * The sole instance of this primitive class. Accessed through reflection.
	 */
	@ReferencedInGeneratedCode
	public static final Primitive instance =
		new P_CreateRestrictedSendExpression().init(
			3, CanSuspend, Unknown);

	@Override
	public Result attempt (
		final Interpreter interpreter)
	{
		interpreter.checkArgumentCount(3);
		final A_Atom messageName = interpreter.argument(0);
		final A_Phrase argsListNode = interpreter.argument(1);
		final A_Type returnType = interpreter.argument(2);

		final A_Tuple argExpressions = argsListNode.expressionsTuple();
		final int argsCount = argExpressions.tupleSize();
		final A_Bundle bundle;
		try
		{
			bundle = messageName.bundleOrCreate();
			final MessageSplitter splitter = bundle.messageSplitter();
			if (splitter.numberOfArguments() != argsCount)
			{
				return interpreter.primitiveFailure(
					stringFrom(
						"Incorrect number of arguments supplied for "
							+ messageName));
			}
		}
		catch (final MalformedMessageException e)
		{
			return interpreter.primitiveFailure(
				stringFrom(
					"Malformed message name: "
						+ messageName
						+ "("
						+ e.describeProblem()
						+ ")"));
		}
		final A_Type argsTupleType = argsListNode.expressionType();
		final A_Tuple argTypesTuple =
			argsTupleType.tupleOfTypesFromTo(1, argsCount);
		final List<A_Type> argTypesList = toList(argTypesTuple);
		// Compute the intersection of the supplied type, applicable definition
		// return types, and semantic restriction types.  Start with the
		// supplied type.
		final A_Fiber originalFiber = currentFiber();
		final AvailLoader loader = stripNull(originalFiber.availLoader());
		final A_Module currentModule = loader.module();
		final A_Set allVisibleModules = currentModule.allAncestors();
		final Mutable<A_Type> intersection = new Mutable<>(returnType);
		// Merge in the applicable (and visible) definition return types.
		boolean anyDefinitionsApplicable = false;
		for (final A_Definition definition :
			bundle.bundleMethod().filterByTypes(argTypesList))
		{
			final A_Module definitionModule = definition.definitionModule();
			if (definition.equalsNil()
				|| allVisibleModules.hasElement(definitionModule))
			{
				intersection.value = intersection.value.typeIntersection(
					definition.bodySignature().returnType());
				anyDefinitionsApplicable = true;
			}
		}
		if (!anyDefinitionsApplicable)
		{
			return interpreter.primitiveFailure(E_NO_METHOD_DEFINITION);
		}
		final List<A_SemanticRestriction> applicableRestrictions =
			new ArrayList<>();
		for (final A_SemanticRestriction restriction :
			bundle.bundleMethod().semanticRestrictions())
		{
			if (allVisibleModules.hasElement(restriction.definitionModule()))
			{
				// The semantic restriction takes the *types* as arguments.
				if (restriction.function().kind().acceptsListOfArgValues(
					argTypesList))
				{
					applicableRestrictions.add(restriction);
				}
			}
		}
		final int restrictionsSize = applicableRestrictions.size();
		if (restrictionsSize == 0)
		{
			// No semantic restrictions.  Trivial success.
			return interpreter.primitiveSuccess(
				newSendNode(
					emptyTuple(), bundle, argsListNode, intersection.value));
		}

		// Merge in the (non-empty list of) semantic restriction results.
		final AvailRuntime runtime = currentRuntime();
		final A_Function primitiveFunction = stripNull(interpreter.function);
		assert primitiveFunction.code().primitive() == this;
		interpreter.primitiveSuspend(primitiveFunction);
		final List<AvailObject> copiedArgs =
			new ArrayList<>(interpreter.argsBuffer);
		final AtomicInteger countdown = new AtomicInteger(restrictionsSize);
		final List<A_String> problems = new ArrayList<>();
		final Continuation0 decrement = () ->
		{
			if (countdown.decrementAndGet() != 0)
			{
				// We're not last to decrement, so don't do the epilogue.
				return;
			}
			// We're last to report.  Either succeed or fail the
			// primitive within the original fiber.
			if (problems.isEmpty())
			{
				// There were no problems.  Succeed the primitive with a
				// send phrase yielding the intersection type.
				resumeFromSuccessfulPrimitive(
					runtime,
					originalFiber,
					this,
					newSendNode(
						emptyTuple(),
						bundle,
						argsListNode,
						intersection.value));
			}
			else
			{
				// There were problems.  Fail the primitive with a string
				// describing them all.
				// assert problems.size() > 0;
				final StringBuilder builder = new StringBuilder();
				if (problems.size() == 1)
				{
					builder.append(problems.get(0).asNativeString());
				}
				else
				{
					builder.append(
						"send phrase creation primitive not to have "
							+ "encountered multiple problems in semantic "
							+ "restrictions:");
					for (final A_String problem : problems)
					{
						builder.append("\n\t");
						builder.append(
							increaseIndentation(problem.asNativeString(), 1));
					}
				}
				final A_String problemReport = stringFrom(builder.toString());
				resumeFromFailedPrimitive(
					runtime,
					originalFiber,
					problemReport,
					primitiveFunction,
					copiedArgs);
			}
		};
		final Continuation1NotNull<AvailObject> success = resultType ->
		{
			if (resultType.isType())
			{
				synchronized (intersection)
				{
					intersection.value =
						intersection.value.typeIntersection(resultType);
				}
			}
			else
			{
				synchronized (problems)
				{
					problems.add(
						stringFrom(
							"Semantic restriction failed to produce "
								+ "a type, and instead produced: "
								+ resultType));
				}
			}
			decrement.value();
		};
		int fiberCount = 1;
		for (final A_SemanticRestriction restriction : applicableRestrictions)
		{
			final int finalCount = fiberCount++;
			final A_Fiber forkedFiber = newFiber(
				topMeta(),
				originalFiber.priority(),
				() -> stringFrom(
					"Semantic restriction checker (#"
						+ finalCount
						+ ") for primitive "
						+ this.getClass().getSimpleName()));
			forkedFiber.availLoader(originalFiber.availLoader());
			forkedFiber.heritableFiberGlobals(
				originalFiber.heritableFiberGlobals());
			forkedFiber.textInterface(originalFiber.textInterface());
			forkedFiber.setSuccessAndFailureContinuations(
				success,
				throwable ->
				{
					if (throwable instanceof AvailRejectedParseException)
					{
						final AvailRejectedParseException rejected =
							(AvailRejectedParseException) throwable;
						final A_String string = rejected.rejectionString();
						synchronized (problems)
						{
							problems.add(string);
						}
					}
					else if (!(throwable instanceof AvailAcceptedParseException))
					{
						synchronized (problems)
						{
							problems.add(stringFrom(
								"evaluation of macro body not to raise an "
									+ "unhandled exception:\n\t"
									+ throwable));
						}
					}
					// Success without type narrowing – do nothing.
					// Now that we've fully dealt with it,
				});
			runOutermostFunction(
				runtime, forkedFiber, restriction.function(), argTypesList);
		}
		return FIBER_SUSPENDED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return functionType(
			tuple(
				ATOM.o(),
				LIST_PHRASE.mostGeneralType(),
				topMeta()),
			SEND_PHRASE.mostGeneralType());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final A_RawFunction rawFunction,
		final List<? extends A_Type> argumentTypes)
	{
		assert argumentTypes.size() == 3;
//		final A_Type messageNameType = argumentTypes.get(0);
//		final A_Type argsListNodeType = argumentTypes.get(1);
		final A_Type returnTypeType = argumentTypes.get(2);

		final A_Type returnType = returnTypeType.instance();
		return SEND_PHRASE.create(returnType);
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return enumerationWith(
			set(
				E_INCORRECT_NUMBER_OF_ARGUMENTS,
				E_NO_METHOD_DEFINITION
			).setUnionCanDestroy(possibleErrors, true));
	}
}
