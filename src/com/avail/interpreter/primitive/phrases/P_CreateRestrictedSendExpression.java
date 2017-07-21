/**
 * P_CreateRestrictedSendExpression.java
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

package com.avail.interpreter.primitive.phrases;

import static com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.interpreter.Primitive.Flag.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.avail.AvailRuntime;
import com.avail.compiler.AvailAcceptedParseException;
import com.avail.compiler.AvailRejectedParseException;
import com.avail.compiler.splitter.MessageSplitter;
import com.avail.descriptor.*;
import com.avail.descriptor.ParseNodeTypeDescriptor.ParseNodeKind;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.*;
import com.avail.utility.Generator;
import com.avail.utility.Mutable;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1;

/**
 * <strong>Primitive CreateRestrictedSendExpression</strong>: Create a
 * {@linkplain SendNodeDescriptor send phrase} from the specified {@linkplain
 * A_Bundle message bundle}, {@linkplain ListNodeDescriptor list node} of
 * {@linkplain ParseNodeKind#EXPRESSION_NODE argument expressions}, and
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
	public final static Primitive instance =
		new P_CreateRestrictedSendExpression().init(
			3, Unknown);

	@Override
	public Result attempt (
		final List<AvailObject> args,
		final Interpreter interpreter,
		final boolean skipReturnCheck)
	{
		assert args.size() == 3;
		final A_Atom messageName = args.get(0);
		final A_Phrase argsListNode = args.get(1);
		final A_Type returnType = args.get(2);

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
					StringDescriptor.from(
						"Incorrect number of arguments supplied for "
						+ messageName));
			}
		}
		catch (final MalformedMessageException e)
		{
			return interpreter.primitiveFailure(
				StringDescriptor.from(
					"Malformed message name: "
					+ messageName
					+ "("
					+ e.describeProblem()
					+ ")"));
		}
		final A_Type argsTupleType = argsListNode.expressionType();
		final A_Tuple argTypesTuple =
			argsTupleType.tupleOfTypesFromTo(1, argsCount);
		final List<A_Type> argTypesList =
			TupleDescriptor.toList(argTypesTuple);
		// Compute the intersection of the supplied type, applicable definition
		// return types, and semantic restriction types.  Start with the
		// supplied type.
		final A_Fiber originalFiber = FiberDescriptor.current();
		final AvailLoader loader = originalFiber.availLoader();
		assert loader != null;
		final A_Module currentModule = loader.module();
		final A_Set allVisibleModules = currentModule.allAncestors();
		final Mutable<A_Type> intersection = new Mutable<>(returnType);
		// Merge in the applicable (and visible) definition return types.
		boolean anyDefinitionsApplicable = false;
		for (final A_Definition definition :
			bundle.bundleMethod().filterByTypes(argTypesList))
		{
			if (allVisibleModules.hasElement(definition.definitionModule()))
			{
				intersection.value = intersection.value.typeIntersection(
					definition.bodySignature().returnType());
				anyDefinitionsApplicable = true;
			}
		}
		if (!anyDefinitionsApplicable)
		{
			return interpreter.primitiveFailure(
				E_NO_METHOD_DEFINITION);
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
				SendNodeDescriptor.from(
					TupleDescriptor.empty(),
					bundle,
					argsListNode,
					intersection.value));
		}

		// Merge in the (non-empty list of) semantic restriction results.
		interpreter.primitiveSuspend();
		final AvailRuntime runtime = AvailRuntime.current();
		final A_Function failureFunction =
			interpreter.primitiveFunctionBeingAttempted();
		assert failureFunction.code().primitiveNumber() == primitiveNumber;
		final List<AvailObject> copiedArgs = new ArrayList<>(args);
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
				// send node yielding the intersection type.
				Interpreter.resumeFromSuccessfulPrimitive(
					runtime,
					originalFiber,
					SendNodeDescriptor.from(
						TupleDescriptor.empty(),
						bundle,
						argsListNode,
						intersection.value),
					skipReturnCheck);
			}
			else
			{
				// There were problems.  Fail the primitive with a string
				// describing them all.
				assert problems.size() > 0;
				final StringBuilder builder = new StringBuilder();
				if (problems.size() == 1)
				{
					builder.append(problems.get(0).asNativeString());
				}
				else
				{
					builder.append(
						"send phrase creation primitive not to have "
						+ "encountered multiple problems in "
						+ "semantic restrictions:");
					for (final A_String problem : problems)
					{
						builder.append("\n\t");
						builder.append(
							problem.asNativeString().replace("\n", "\n\t"));
					}
				}
				final A_String problemReport =
					StringDescriptor.from(builder.toString());
				Interpreter.resumeFromFailedPrimitive(
					runtime,
					originalFiber,
					problemReport,
					failureFunction,
					copiedArgs,
					skipReturnCheck);
			}
		};
		final Continuation1<AvailObject> success =
			resultType ->
			{
				assert resultType != null;
				if (resultType.isType())
				{
					synchronized (intersection)
					{
						intersection.value =
							intersection.value.typeIntersection(
								resultType);
					}
				}
				else
				{
					synchronized (problems)
					{
						problems.add(
							StringDescriptor.from(
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
			final A_Fiber forkedFiber = FiberDescriptor.newFiber(
				InstanceMetaDescriptor.topMeta(),
				originalFiber.priority(),
				new Generator<A_String>()
				{
					@Override
					public A_String value ()
					{
						return StringDescriptor.from(
							"Semantic restriction checker (#"
							+ finalCount
							+ ") for primitive "
							+ this.getClass().getSimpleName());
					}
				});
			forkedFiber.availLoader(originalFiber.availLoader());
			forkedFiber.heritableFiberGlobals(
				originalFiber.heritableFiberGlobals());
			forkedFiber.textInterface(originalFiber.textInterface());
			forkedFiber.resultContinuation(success);
			forkedFiber.failureContinuation(throwable ->
			{
				assert throwable != null;
				if (throwable instanceof AvailRejectedParseException)
				{
					final AvailRejectedParseException rejected =
						(AvailRejectedParseException)throwable;
					final A_String string = rejected.rejectionString();
					synchronized (problems)
					{
						problems.add(string);
					}
				}
				else if (throwable instanceof AvailAcceptedParseException)
				{
					//noinspection StatementWithEmptyBody
					// Success without type narrowing – do nothing.
				}
				else
				{
					synchronized (problems)
					{
						problems.add(StringDescriptor.from(
							"evaluation of macro body not to raise an "
							+ "unhandled exception:\n\t"
							+ throwable));
					}
				}
				// Now that we've fully dealt with it,
			});
			Interpreter.runOutermostFunction(
				runtime,
				forkedFiber,
				restriction.function(),
				argTypesList);
		}
		return Result.FIBER_SUSPENDED;
	}

	@Override
	protected A_Type privateBlockTypeRestriction ()
	{
		return FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				ATOM.o(),
				LIST_NODE.mostGeneralType(),
				InstanceMetaDescriptor.topMeta()),
			SEND_NODE.mostGeneralType());
	}

	@Override
	public A_Type returnTypeGuaranteedByVM (
		final List<? extends A_Type> argumentTypes)
	{
		assert argumentTypes.size() == 3;
//		final A_Type messageNameType = argumentTypes.get(0);
//		final A_Type argsListNodeType = argumentTypes.get(1);
		final A_Type returnTypeType = argumentTypes.get(2);

		final A_Type returnType = returnTypeType.instance();
		return SEND_NODE.create(returnType);
	}

	@Override
	protected A_Type privateFailureVariableType ()
	{
		return AbstractEnumerationTypeDescriptor.withInstances(
			SetDescriptor.from(
					E_INCORRECT_NUMBER_OF_ARGUMENTS,
					E_NO_METHOD_DEFINITION)
				.setUnionCanDestroy(MessageSplitter.possibleErrors, true));
	}
}
