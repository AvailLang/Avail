/**
 * CompilationContext.java
 * Copyright © 1993-2015, The Avail Foundation, LLC. All rights reserved.
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

import com.avail.AvailRuntime;
import com.avail.AvailTask;
import com.avail.annotations.InnerAccess;
import com.avail.builder.ModuleName;
import com.avail.builder.ResolvedModuleName;
import com.avail.compiler.AvailCompiler.CompilerProgressReporter;
import com.avail.compiler.AvailCompiler.Con;
import com.avail.compiler.AvailCompiler.ParserState;
import com.avail.compiler.problems.CompilerDiagnostics;
import com.avail.compiler.problems.Problem;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.compiler.problems.ProblemType;
import com.avail.descriptor.*;
import com.avail.descriptor.TypeDescriptor.Types;
import com.avail.exceptions.AvailAssertionFailedException;
import com.avail.exceptions.AvailEmergencyExitException;
import com.avail.interpreter.AvailLoader;
import com.avail.interpreter.Interpreter;
import com.avail.interpreter.effects.LoadingEffect;
import com.avail.interpreter.levelOne.L1InstructionWriter;
import com.avail.interpreter.levelOne.L1Operation;
import com.avail.io.TextInterface;
import com.avail.serialization.Serializer;
import com.avail.utility.Generator;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.Continuation1;
import org.jetbrains.annotations.Nullable;

import static com.avail.compiler.problems.ProblemType.*;

import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CompilationContext
{
	/**
	 * A {@link Runnable} which supports a natural ordering (via the {@link
	 * Comparable} interface) which will favor processing of the leftmost
	 * available tasks first.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	static abstract class ParsingTask
		extends AvailTask
	{
		/** The parsing state for this task will operate. */
		private final ParserState state;

		/**
		 * Construct a new {@link ParsingTask}.
		 *
		 * @param state The {@linkplain ParserState parser state} for this task.
		 */
		@InnerAccess ParsingTask (
			final ParserState state)
		{
			super(FiberDescriptor.compilerPriority);
			this.state = state;
		}

		@Override
		public int compareTo (final @Nullable AvailTask o)
		{
			assert o != null;
			final int priorityDelta = Integer.compare(priority, o.priority);
			if (priorityDelta != 0)
			{
				return priorityDelta;
			}
			if (o instanceof ParsingTask)
			{
				final ParsingTask task = (ParsingTask) o;
				return Integer.compare(state.position, task.state.position);
			}
			return priorityDelta;
		}
	}

	/**
	 * The {@link CompilerDiagnostics} that tracks potential errors during
	 * compilation.
	 */
	final CompilerDiagnostics diagnostics;

	/**
	 * The {@link AvailRuntime} for the compiler. Since a compiler cannot
	 * migrate between two runtime environments, it is safe to cache it for
	 * efficient access.
	 */
	@InnerAccess
	final AvailRuntime runtime = AvailRuntime.current();

	/**
	 * The header information for the current module being parsed.
	 */
	final @Nullable ModuleHeader moduleHeader;

	public ModuleHeader getModuleHeader ()
	{
		return moduleHeader;
	}

	/**
	 * The Avail {@linkplain ModuleDescriptor module} undergoing compilation.
	 */
	@InnerAccess
	final A_Module module;

	public A_Module getModule ()
	{
		return module;
	}

	/**
	 * Answer the fully-qualified name of the {@linkplain ModuleDescriptor
	 * module} undergoing compilation.
	 *
	 * @return The module name.
	 */
	@InnerAccess ModuleName moduleName ()
	{
		return new ModuleName(getModule().moduleName().asNativeString());
	}

	/**
	 * The {@linkplain AvailLoader loader} created and operated by this
	 * {@linkplain AvailCompiler compiler} to facilitate the loading of
	 * {@linkplain ModuleDescriptor modules}.
	 */
	@Nullable AvailLoader loader = null;

	public AvailLoader getLoader ()
	{
		return loader;
	}

	public void setLoader (AvailLoader loader)
	{
		this.loader = loader;
	}

	/**
	 * The source text of the Avail {@linkplain ModuleDescriptor module}
	 * undergoing compilation.
	 */
	@InnerAccess final String source;

	String source ()
	{
		return source;
	}

	/**
	 * The {@linkplain TextInterface text interface} for any {@linkplain
	 * A_Fiber fibers} started by this {@linkplain AvailCompiler
	 * compiler}.
	 */
	@InnerAccess
	final TextInterface textInterface;

	public TextInterface getTextInterface ()
	{
		return textInterface;
	}

	/** The number of work units that have been queued. */
	@InnerAccess
	AtomicLong workUnitsQueued = new AtomicLong(0);

	public AtomicLong getWorkUnitsQueued ()
	{
		return workUnitsQueued;
	}

	/** The number of work units that have been completed. */
	@InnerAccess
	AtomicLong workUnitsCompleted = new AtomicLong(0);

	public AtomicLong getWorkUnitsCompleted ()
	{
		return workUnitsCompleted;
	}

	/**
	 * What to do when there are no more work units.
	 */
	@InnerAccess
	volatile @Nullable Continuation0 noMoreWorkUnits = null;

	public Continuation0 getNoMoreWorkUnits ()
	{
		return noMoreWorkUnits;
	}

	public void setNoMoreWorkUnits (Continuation0 noMoreWorkUnits)
	{
		this.noMoreWorkUnits = noMoreWorkUnits;
	}

	/**
	 * The {@linkplain CompilerProgressReporter} that reports compilation
	 * progress at various checkpoints. It accepts the {@linkplain
	 * ResolvedModuleName name} of the {@linkplain ModuleDescriptor module}
	 * undergoing {@linkplain AvailCompiler compilation}, the line
	 * number on which the last complete statement concluded, the position of
	 * the ongoing parse (in bytes), and the size of the module (in bytes).
	 */
	@InnerAccess
	volatile CompilerProgressReporter progressReporter;

	public CompilerProgressReporter getProgressReporter ()
	{
		return progressReporter;
	}

	public void setProgressReporter (CompilerProgressReporter progressReporter)
	{
		this.progressReporter = progressReporter;
	}

	/**
	 * The {@linkplain Continuation1 continuation} that reports success of
	 * compilation.
	 */
	@InnerAccess
	volatile @Nullable Continuation0 successReporter;

	public Continuation0 getSuccessReporter ()
	{
		return successReporter;
	}

	public void setSuccessReporter (Continuation0 successReporter)
	{
		this.successReporter = successReporter;
	}

	/** The output stream on which the serializer writes. */
	public final ByteArrayOutputStream serializerOutputStream =
		new ByteArrayOutputStream(1000);

	/**
	 * The serializer that captures the sequence of bytes representing the
	 * module during compilation.
	 */
	final Serializer serializer = new Serializer(serializerOutputStream);

	public CompilationContext (
		final @Nullable ModuleHeader moduleHeader,
		final A_Module module,
		final String source,
		final TextInterface textInterface,
		final Generator<Boolean> pollForAbort,
		final CompilerProgressReporter progressReporter,
		final ProblemHandler problemHandler)
	{
		this.moduleHeader = moduleHeader;
		this.module = module;
		this.source = source;
		this.textInterface = textInterface;
		this.progressReporter = progressReporter;
		this.diagnostics = new CompilerDiagnostics(
			source, moduleName(), pollForAbort, problemHandler);
	}

	/**
	 * Answer the {@linkplain AvailLoader loader} created and operated by this
	 * {@linkplain AvailCompiler compiler} to facilitate the loading of
	 * {@linkplain ModuleDescriptor modules}.
	 *
	 * @return A loader.
	 */
	@InnerAccess
	AvailLoader loader ()
	{
		final AvailLoader theLoader = loader;
		assert theLoader != null;
		return theLoader;
	}

	/**
	 * Attempt the {@linkplain Continuation0 zero-argument continuation}. The
	 * implementation is free to execute it now or to put it in a bag of
	 * continuations to run later <em>in an arbitrary order</em>. There may be
	 * performance and/or scale benefits to processing entries in FIFO, LIFO, or
	 * some hybrid order, but the correctness is not affected by a choice of
	 * order. The implementation may run the expression in parallel with the
	 * invoking thread and other such expressions.
	 *
	 * @param token
	 *        The {@linkplain TokenDescriptor token} that provides context for
	 *        the continuation.
	 * @param continuation
	 *        What to do at some point in the future.
	 */
	@InnerAccess
	void eventuallyDo (
		final A_Token token,
		final Continuation0 continuation)
	{
		runtime.execute(new AvailTask(FiberDescriptor.compilerPriority)
		{
			@Override
			public void value ()
			{
				try
				{
					continuation.value();
				}
				catch (final Exception e)
				{
					reportInternalProblem(token, e);
				}
			}
		});
	}

	/**
	 * Start a work unit.
	 */
	@InnerAccess
	void startWorkUnit ()
	{
		workUnitsQueued.incrementAndGet();
	}

	/**
	 * Construct and answer a {@linkplain Continuation1 continuation} that
	 * wraps the specified continuation in logic that will increment the
	 * {@linkplain #workUnitsCompleted count of completed work units} and
	 * potentially call the {@linkplain #noMoreWorkUnits unambiguous statement}.
	 *
	 * @param token
	 *        The {@linkplain A_Token token} that provides context for the
	 *        work unit completion.
	 * @param optionalSafetyCheck
	 *        Either {@code null} or an {@link AtomicBoolean} which must
	 *        transition from false to true only once.
	 * @param continuation
	 *        What to do as a work unit.
	 * @return A new continuation. It accepts an argument of some kind, which
	 * will be passed forward to the argument continuation.
	 */
	@InnerAccess
	<ArgType> Continuation1<ArgType> workUnitCompletion (
		final A_Token token,
		final @Nullable AtomicBoolean optionalSafetyCheck,
		final Continuation1<ArgType> continuation)
	{
		assert noMoreWorkUnits != null;
		return new Continuation1<ArgType>()
		{
			AtomicBoolean hasRunSafetyCheck = optionalSafetyCheck != null
				? optionalSafetyCheck
				: new AtomicBoolean(false);

			@Override
			public void value (final @Nullable ArgType value)
			{
				final boolean hadRun = hasRunSafetyCheck.getAndSet(true);
				assert !hadRun;
				try
				{
					// Don't actually run tasks if canceling.
					diagnostics.isShuttingDown |=
						diagnostics.pollForAbort.value();
					if (!diagnostics.isShuttingDown)
					{
						continuation.value(value);
					}
				}
				catch (final Exception e)
				{
					reportInternalProblem(token, e);
				}
				finally
				{
					// We increment and read completed and then read queued.
					// That's because at every moment completed must always
					// be <= queued. No matter how many new tasks are being
					// queued and completed by other threads, that invariant
					// holds.  The other fact is that the moment the
					// counters have the same (nonzero) value, they will
					// forever after have that same value.  Note that we
					// still have to do the fused incrementAndGet so that we
					// know if *we* were the (sole) cause of exhaustion.
					final long completed = workUnitsCompleted.incrementAndGet();
					final long queued = workUnitsQueued.get();
					assert completed <= queued;
					if (completed == queued)
					{
						try
						{
							final Continuation0 noMore = noMoreWorkUnits;
							noMoreWorkUnits = null;
							assert noMore != null;
							noMore.value();
						}
						catch (final Exception e)
						{
							reportInternalProblem(token, e);
						}
					}
				}
			}
		};
	}

	/**
	 * Eventually execute the specified {@linkplain Continuation0 continuation}
	 * as a {@linkplain AvailCompiler compiler} work unit.
	 *
	 * @param continuation
	 *        What to do at some point in the future.
	 * @param where
	 *        Where the parse is happening.
	 */
	void workUnitDo (
		final Continuation0 continuation,
		final ParserState where)
	{
		startWorkUnit();
		final Continuation1<Void> workUnit = workUnitCompletion(
			where.peekToken(),
			null,
			new Continuation1<Void>()
			{
				@Override
				public void value (final @Nullable Void ignored)
				{
					continuation.value();
				}
			});
		runtime.execute(
			new ParsingTask(where)
			{
				@Override
				public void value ()
				{
					workUnit.value(null);
				}
			});
	}

	/**
	 * Wrap the {@linkplain Continuation1 continuation of one argument} inside a
	 * {@linkplain Continuation0 continuation of zero arguments} and record that
	 * as per {@linkplain #workUnitDo(Continuation0, ParserState)}.
	 *
	 * @param <ArgType>
	 *        The type of argument to the given continuation.
	 * @param here
	 *        Where to start parsing when the continuation runs.
	 * @param continuation
	 *        What to execute with the passed argument.
	 * @param argument
	 *        What to pass as an argument to the provided {@linkplain
	 *        Continuation1 one-argument continuation}.
	 */
	@InnerAccess
	<ArgType> void attempt (
		final ParserState here,
		final Continuation1<ArgType> continuation,
		final ArgType argument)
	{
		workUnitDo(
			new Continuation0()
			{
				@Override
				public void value ()
				{
					continuation.value(argument);
				}
			},
			here);
	}

	/**
	 * Evaluate the specified {@linkplain FunctionDescriptor function} in the
	 * module's context; lexically enclosing variables are not considered in
	 * scope, but module variables and constants are in scope.
	 *
	 * @param function
	 *        A function.
	 * @param lineNumber
	 *        The line number at which this function occurs in the module.
	 * @param args
	 *        The arguments to the function.
	 * @param clientParseData
	 *        The map to associate with the {@link
	 *        AtomDescriptor#clientDataGlobalKey()} atom in the fiber.
	 * @param shouldSerialize
	 *        {@code true} if the generated function should be serialized,
	 *        {@code false} otherwise.
	 * @param onSuccess
	 *        What to do with the result of the evaluation.
	 * @param onFailure
	 *        What to do with a terminal {@link Throwable}.
	 */
	@InnerAccess
	void evaluateFunctionThen (
		final A_Function function,
		final int lineNumber,
		final List<? extends A_BasicObject> args,
		final A_Map clientParseData,
		final boolean shouldSerialize,
		final Continuation1<AvailObject> onSuccess,
		final Continuation1<Throwable> onFailure)
	{
		final A_RawFunction code = function.code();
		assert code.numArgs() == args.size();
		final A_Fiber fiber = FiberDescriptor.newLoaderFiber(
			function.kind().returnType(),
			loader(),
			new Generator<A_String>()
			{
				@Override
				public A_String value ()
				{
					return StringDescriptor.format(
						"Eval fn=%s, in %s:%d",
						code.methodName(),
						code.module().moduleName(),
						code.startingLineNumber());
				}
			});
		A_Map fiberGlobals = fiber.fiberGlobals();
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			AtomDescriptor.clientDataGlobalKey(), clientParseData, true);
		fiber.fiberGlobals(fiberGlobals);
		fiber.textInterface(textInterface);
		if (shouldSerialize)
		{
			loader().startRecordingEffects();
		}
		final long before = System.nanoTime();
		final Continuation1<AvailObject> adjustedSuccess =
			shouldSerialize
				? new Continuation1<AvailObject>()
			{
				@Override
				public void value (
					final @Nullable AvailObject successValue)
				{
					final long after = System.nanoTime();
					Interpreter.current().recordTopStatementEvaluation(
						after - before,
						module,
						lineNumber);
					loader().stopRecordingEffects();
					serializeAfterRunning(function);
					onSuccess.value(successValue);
				}
			}
				: onSuccess;
		fiber.resultContinuation(adjustedSuccess);
		fiber.failureContinuation(onFailure);
		Interpreter.runOutermostFunction(runtime, fiber, function, args);
	}

	/**
	 * Serialize either the given function or something semantically equivalent.
	 * The equivalent is expected to be faster, but can only be used if no
	 * triggers had been tripped during execution of the function.  The triggers
	 * include reading or writing shared variables, or executing certain
	 * primitives.
	 *
	 * @param function The function that has already run.
	 */
	@InnerAccess synchronized void serializeAfterRunning (
		final A_Function function)
	{
		if (loader.statementCanBeSummarized())
		{
			final List<LoadingEffect> effects = loader.recordedEffects();
			if (!effects.isEmpty())
			{
				// Output summarized functions instead of what ran.
				final L1InstructionWriter writer =
					new L1InstructionWriter(
						module, function.code().startingLineNumber());
				writer.argumentTypes();
				writer.returnType(Types.TOP.o());
				boolean first = true;
				for (final LoadingEffect effect : effects)
				{
					if (first)
					{
						first = false;
					}
					else
					{
						writer.write(L1Operation.L1_doPop);
					}
					effect.writeEffectTo(writer);
				}
				final A_Function summaryFunction =
					FunctionDescriptor.create(
						writer.compiledCode(),
						TupleDescriptor.empty());
				serializer.serialize(summaryFunction);
			}
		}
		else
		{
			// Can't summarize; write the original function.
			if (AvailLoader.debugUnsummarizedStatements)
			{
				System.out.println(
					module.toString()
						+ ":" + function.code().startingLineNumber()
						+ " -- " + function);
			}
			serializer.serialize(function);
		}
	}

	/**
	 * Report an {@linkplain ProblemType#INTERNAL internal} {@linkplain
	 * Problem problem}.
	 *
	 * @param token
	 *        The {@linkplain A_Token token} that provides context to the
	 *        problem.
	 * @param e
	 *        The unexpected {@linkplain Throwable exception} that is the
	 *        proximal cause of the problem.
	 */
	@InnerAccess
	void reportInternalProblem (
		final A_Token token,
		final Throwable e)
	{
		diagnostics.isShuttingDown = true;
		diagnostics.compilationIsInvalid = true;
		final CharArrayWriter trace = new CharArrayWriter();
		e.printStackTrace(new PrintWriter(trace));
		final Problem problem = new Problem(
			moduleName(),
			token.lineNumber(),
			token.start(),
			INTERNAL,
			"Internal error: {0}\n{1}",
			e.getMessage(),
			trace)
		{
			@Override
			protected void abortCompilation ()
			{
				diagnostics.isShuttingDown = true;
			}
		};
		diagnostics.handleProblem(problem);
	}

	/**
	 * Report an {@linkplain ProblemType#EXECUTION execution} {@linkplain
	 * Problem problem}.
	 *
	 * @param token
	 *        The {@linkplain A_Token token} that provides context to the
	 *        problem.
	 * @param e
	 *        The unexpected {@linkplain Throwable exception} that is the
	 *        proximal cause of the problem.
	 */
	@InnerAccess
	void reportExecutionProblem (
		final A_Token token,
		final Throwable e)
	{
		diagnostics.compilationIsInvalid = true;
		if (e instanceof FiberTerminationException)
		{
			diagnostics.handleProblem(new Problem(
					moduleName(),
					token.lineNumber(),
					token.start(),
					EXECUTION,
					"Execution error: Avail stack reported above.\n")
				{
					@Override
					public void abortCompilation ()
					{
						diagnostics.isShuttingDown = true;
					}
				});
		}
		else
		{
			final CharArrayWriter trace = new CharArrayWriter();
			e.printStackTrace(new PrintWriter(trace));
			diagnostics.handleProblem(new Problem(
					moduleName(),
					token.lineNumber(),
					token.start(),
					EXECUTION,
					"Execution error: {0}\n{1}",
					e.getMessage(),
					trace)
				{
					@Override
					public void abortCompilation ()
					{
						diagnostics.isShuttingDown = true;
					}
				});
		}
	}

	/**
	 * Report an {@linkplain ProblemType#EXECUTION assertion failure}
	 * {@linkplain Problem problem}.
	 *
	 * @param token
	 *        The {@linkplain A_Token token} that provides context to the
	 *        problem.
	 * @param e
	 *        The {@linkplain AvailAssertionFailedException assertion failure}.
	 */
	@InnerAccess
	void reportAssertionFailureProblem (
		final A_Token token,
		final AvailAssertionFailedException e)
	{
		diagnostics.compilationIsInvalid = true;
		diagnostics.handleProblem(new Problem(
			moduleName(),
			token.lineNumber(),
			token.start(),
			EXECUTION,
			"{0}",
			e.getMessage())
		{
			@Override
			public void abortCompilation ()
			{
				diagnostics.isShuttingDown = true;
			}
		});
	}

	/**
	 * Report an {@linkplain ProblemType#EXECUTION emergency exit}
	 * {@linkplain Problem problem}.
	 *
	 * @param token
	 *        The {@linkplain A_Token token} that provides context to the
	 *        problem.
	 * @param e
	 *        The {@linkplain AvailEmergencyExitException emergency exit
	 *        failure}.
	 */
	@InnerAccess
	void reportEmergencyExitProblem (
		final A_Token token,
		final AvailEmergencyExitException e)
	{
		diagnostics.compilationIsInvalid = true;
		diagnostics.handleProblem(new Problem(
			moduleName(),
			token.lineNumber(),
			token.start(),
			EXECUTION,
			"{0}",
			e.getMessage())
		{
			@Override
			public void abortCompilation ()
			{
				diagnostics.isShuttingDown = true;
			}
		});
	}
}