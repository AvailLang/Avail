/**
 * CompilationContext.java
 * Copyright © 1993-2017, The Avail Foundation, LLC. All rights reserved.
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
import com.avail.compiler.problems.CompilerDiagnostics;
import com.avail.compiler.problems.Problem;
import com.avail.compiler.problems.ProblemHandler;
import com.avail.compiler.problems.ProblemType;
import com.avail.compiler.scanning.LexingState;
import com.avail.descriptor.*;
import com.avail.descriptor.AtomDescriptor.SpecialAtom;
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
import com.avail.utility.evaluation.Continuation1NotNull;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.avail.compiler.problems.ProblemType.EXECUTION;
import static com.avail.compiler.problems.ProblemType.INTERNAL;
import static com.avail.descriptor.AtomDescriptor.SpecialAtom.CLIENT_DATA_GLOBAL_KEY;
import static com.avail.descriptor.FunctionDescriptor.createFunctionForPhrase;
import static com.avail.descriptor.MapDescriptor.emptyMap;
import static com.avail.descriptor.TupleDescriptor.emptyTuple;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.StackPrinter.trace;
import static java.lang.String.format;

/**
 * A {@code CompilationContext} lasts for a module's entire compilation
 * activity.
 */
public class CompilationContext
{
	/** The {@linkplain Logger logger}. */
	public static final Logger logger = Logger.getLogger(
		CompilationContext.class.getName());

	/**
	 * A {@link Runnable} which supports a natural ordering (via the {@link
	 * Comparable} interface) which will favor processing of the leftmost
	 * available tasks first.
	 *
	 * @author Mark van Gulik &lt;mark@availlang.org&gt;
	 */
	abstract static class ParsingTask
		extends AvailTask
	{
		/**
		 * Construct a new {@code ParsingTask}.
		 */
		@InnerAccess ParsingTask ()
		{
			super(FiberDescriptor.compilerPriority);
		}
	}

	/**
	 * The {@link CompilerDiagnostics} that tracks potential errors during
	 * compilation.
	 */
	public final CompilerDiagnostics diagnostics;

	/**
	 * The {@link AvailRuntime} for the compiler. Since a compiler cannot
	 * migrate between two runtime environments, it is safe to cache it for
	 * efficient access.
	 */
	@InnerAccess final AvailRuntime runtime = AvailRuntime.current();

	/**
	 * The header information for the current module being parsed.
	 */
	final @Nullable ModuleHeader moduleHeader;

	public ModuleHeader getModuleHeader ()
	{
		return stripNull(moduleHeader);
	}

	/**
	 * The Avail {@linkplain ModuleDescriptor module} undergoing compilation.
	 */
	@InnerAccess final A_Module module;

	public A_Module module ()
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
		return new ModuleName(module().moduleName().asNativeString());
	}

	/**
	 * The {@linkplain AvailLoader loader} created and operated by this
	 * {@linkplain AvailCompiler compiler} to facilitate the loading of
	 * {@linkplain ModuleDescriptor modules}.
	 */
	@Nullable AvailLoader loader = null;

	public void setLoader (final AvailLoader loader)
	{
		this.loader = loader;
	}

	/**
	 * Answer the {@linkplain AvailLoader loader} created and operated by this
	 * {@linkplain AvailCompiler compiler} to facilitate the loading of
	 * {@linkplain ModuleDescriptor modules}.
	 *
	 * @return A loader.
	 */
	@InnerAccess public AvailLoader loader ()
	{
		return stripNull(loader);
	}

	/**
	 * The source text of the Avail {@linkplain ModuleDescriptor module}
	 * undergoing compilation.
	 */
	@InnerAccess final A_String source;

	public A_String source ()
	{
		return source;
	}

	/**
	 * The {@linkplain TextInterface text interface} for any {@linkplain
	 * A_Fiber fibers} started by this {@linkplain AvailCompiler
	 * compiler}.
	 */
	@InnerAccess final TextInterface textInterface;

	public TextInterface getTextInterface ()
	{
		return textInterface;
	}

	/** The number of work units that have been queued. */
	@InnerAccess final AtomicLong workUnitsQueued = new AtomicLong(0);

	public AtomicLong getWorkUnitsQueued ()
	{
		return workUnitsQueued;
	}

	/** The number of work units that have been completed. */
	@InnerAccess final AtomicLong workUnitsCompleted = new AtomicLong(0);

	public AtomicLong getWorkUnitsCompleted ()
	{
		return workUnitsCompleted;
	}

	/**
	 * What to do when there are no more work units.
	 */
	@InnerAccess volatile @Nullable Continuation0 noMoreWorkUnits = null;

	public @Nullable Continuation0 getNoMoreWorkUnits ()
	{
		return noMoreWorkUnits;
	}

	public void setNoMoreWorkUnits (final Continuation0 noMoreWorkUnits)
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
	@InnerAccess final CompilerProgressReporter progressReporter;

	public CompilerProgressReporter getProgressReporter ()
	{
		return progressReporter;
	}

	/**
	 * The {@linkplain Continuation0 continuation} that reports success of
	 * compilation.
	 */
	@InnerAccess volatile @Nullable Continuation0 successReporter;

	public Continuation0 getSuccessReporter ()
	{
		return stripNull(successReporter);
	}

	public void setSuccessReporter (final Continuation0 theSuccessReporter)
	{
		this.successReporter = theSuccessReporter;
	}

	/** The output stream on which the serializer writes. */
	public final ByteArrayOutputStream serializerOutputStream =
		new ByteArrayOutputStream(1000);

	/**
	 * The serializer that captures the sequence of bytes representing the
	 * module during compilation.
	 */
	final Serializer serializer = new Serializer(serializerOutputStream);

	/**
	 * The map from position in the source to the {@link LexingState} associated
	 * with that position.  Each {@link LexingState} handles its own
	 * synchronization and laziness.  Because different lexers may have
	 * different ideas about what line number they're at, the key is a long,
	 * computed from (lineNumber << 32) + position.
	 */
	final Map<Long, LexingState> lexingStates = new HashMap<>(100);

	/**
	 * The lock used to control access to the {@link #lexingStates}.
	 */
	final ReadWriteLock lexingStatesLock = new ReentrantReadWriteLock();

	public CompilationContext (
		final @Nullable ModuleHeader moduleHeader,
		final A_Module module,
		final A_String source,
		final TextInterface textInterface,
		final Generator<Boolean> pollForAbort,
		final CompilerProgressReporter progressReporter,
		final ProblemHandler problemHandler)
	{
		this.loader = new AvailLoader(module, textInterface);
		this.moduleHeader = moduleHeader;
		this.module = module;
		this.source = source;
		this.textInterface = textInterface;
		this.progressReporter = progressReporter;
		this.diagnostics = new CompilerDiagnostics(
			source, moduleName(), pollForAbort, problemHandler);
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
	 * @param lexingState
	 *        The {@link LexingState} for which to report problems.
	 * @param continuation
	 *        What to do at some point in the future.
	 */
	public void eventuallyDo (
		final LexingState lexingState,
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
					reportInternalProblem(
						lexingState.lineNumber, lexingState.position, e);
				}
			}
		});
	}

	/**
	 * Start a work unit.
	 */
	public void startWorkUnit ()
	{
		assert noMoreWorkUnits != null;
		workUnitsQueued.incrementAndGet();
		logger.log(
			Level.FINEST,
			format(
				"Started work unit: %d/%d%n",
				workUnitsCompleted.get(),
				workUnitsQueued.get()));
	}

	/**
	 * Construct and answer a {@linkplain Continuation1NotNull continuation}
	 * that wraps the specified continuation in logic that will increment the
	 * {@linkplain #workUnitsCompleted count of completed work units} and
	 * potentially call the {@linkplain #noMoreWorkUnits unambiguous statement}.
	 *
	 * @param lexingState
	 *        The {@link LexingState} for which to report problems.
	 * @param optionalSafetyCheck
	 *        Either {@code null} or an {@link AtomicBoolean} which must
	 *        transition from false to true only once.
	 * @param continuation
	 *        What to do as a work unit.
	 * @return A new continuation. It accepts an argument of some kind, which
	 * will be passed forward to the argument continuation.
	 */
	public <ArgType> Continuation1NotNull<ArgType> workUnitCompletion (
		final LexingState lexingState,
		final @Nullable AtomicBoolean optionalSafetyCheck,
		final Continuation1NotNull<ArgType> continuation)
	{
		assert noMoreWorkUnits != null;
		final AtomicBoolean hasRunSafetyCheck = optionalSafetyCheck != null
			? optionalSafetyCheck
			: new AtomicBoolean(false);
		return value ->
		{
			final boolean hadRun = hasRunSafetyCheck.getAndSet(true);
			assert !hadRun;
			try
			{
				// Don't actually run tasks if canceling.
				if (!diagnostics.isShuttingDown)
				{
					if (diagnostics.pollForAbort.value())
					{
						diagnostics.isShuttingDown = true;
					}
					else
					{
						continuation.value(value);
					}
				}
			}
			catch (final Exception e)
			{
				reportInternalProblem(
					lexingState.lineNumber,
					lexingState.position,
					e);
			}
			finally
			{
				// We increment and read completed and then read queued.  That's
				// because at every moment completed must always be <= queued.
				// No matter how many new tasks are being queued and completed
				// by other threads, that invariant holds.  The other fact is
				// that the moment the counters have the same (nonzero) value,
				// they will forever after have that same value.  Note that we
				// still have to do the fused incrementAndGet so that we know if
				// *we* were the (sole) cause of exhaustion.
				final long completed = workUnitsCompleted.incrementAndGet();
				final long queued = workUnitsQueued.get();
				assert completed <= queued;
				logger.log(
					Level.FINEST,
					format(
						"Completed work unit: %d/%d%n",
						completed,
						queued));
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
						reportInternalProblem(
							lexingState.lineNumber,
							lexingState.position,
							e);
					}
				}
			}
		};
	}

	/**
	 * Eventually execute the specified {@linkplain Continuation0 continuation}
	 * as a {@linkplain AvailCompiler compiler} work unit.
	 *
	 * @param lexingState
	 *        The {@link LexingState} for which to report problems.
	 * @param continuation
	 *        What to do at some point in the future.
	 */
	public void workUnitDo (
		final LexingState lexingState,
		final Continuation0 continuation)
	{
		if (noMoreWorkUnits != null)
		{
			// We're tracking work units, so we have to make sure to account for
			// the new unit being queued, increment the completed count when it
			// completes, and run the noMoreWorkUnits action as soon the
			// counters collide (indicating the last work unit just completed).
			startWorkUnit();
			final Continuation1NotNull<Void> workUnit = workUnitCompletion(
				lexingState, null, ignored -> continuation.value());
			runtime.execute(
				new ParsingTask()
				{
					@Override
					public void value ()
					{
						workUnit.value(null);
					}
				});
		}
		else
		{
			// We're not tracking work units, so just queue it without fiddling
			// with the queued/completed counts.
			runtime.execute(
				new ParsingTask()
				{
					@Override
					public void value ()
					{
						continuation.value();
					}
				});
		}
	}

	/**
	 * Look up the {@link LexingState} at the specified source position (and
	 * lineNumber), creating and recording a new one if necessary.
	 *
	 * <p>Note that a {@link A_Token token} can be created with a successor
	 * {@link LexingState} which is not recorded in the {@link #lexingStates}
	 * map.  This allows a {@link A_Lexer lexer} to produce a sequence of tokens
	 * instead of just one.</p>
	 *
	 * @param position The position in the module's source code.
	 * @return The {@link LexingState} for that position.
	 */
	public LexingState lexingStateAt (final int position, final int lineNumber)
	{
		// First try to read it inside a (shared) read lock.
		final Long key = (((long)lineNumber) << 32) + position;
		lexingStatesLock.readLock().lock();
		LexingState state;
		try
		{
			state = lexingStates.get(key);
		}
		finally
		{
			lexingStatesLock.readLock().unlock();
		}

		if (state == null)
		{
			lexingStatesLock.writeLock().lock();
			try
			{
				// Someone else may have just added it while we held no lock.
				state = lexingStates.computeIfAbsent(
					key,
					k -> new LexingState(this, position, lineNumber));
			}
			finally
			{
				lexingStatesLock.writeLock().unlock();
			}
		}

		assert state.position == position;
		assert state.lineNumber == lineNumber;
		return state;
	}

	/**
	 * Release all recorded information about the {@link LexingState}s at every
	 * position.  This is done between top-level expressions (1) to ensure that
	 * lexers defined by a top-level expression are able to run without caching
	 * stale tokens, and (2) to reduce the memory load.
	 */
	void clearLexingStates ()
	{
		lexingStatesLock.writeLock().lock();
		try
		{
			lexingStates.clear();
		}
		finally
		{
			lexingStatesLock.writeLock().unlock();
		}
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
	 *        SpecialAtom#CLIENT_DATA_GLOBAL_KEY} atom in the fiber.
	 * @param shouldSerialize
	 *        {@code true} if the generated function should be serialized,
	 *        {@code false} otherwise.
	 * @param onSuccess
	 *        What to do with the result of the evaluation.
	 * @param onFailure
	 *        What to do with a terminal {@link Throwable}.
	 */
	@InnerAccess void evaluateFunctionThen (
		final A_Function function,
		final int lineNumber,
		final List<? extends A_BasicObject> args,
		final A_Map clientParseData,
		final boolean shouldSerialize,
		final Continuation1NotNull<AvailObject> onSuccess,
		final Continuation1NotNull<Throwable> onFailure)
	{
		final A_RawFunction code = function.code();
		assert code.numArgs() == args.size();
		final A_Fiber fiber = FiberDescriptor.newLoaderFiber(
			function.kind().returnType(),
			loader(),
			() -> StringDescriptor.format("Eval fn=%s, in %s:%d",
				code.methodName(),
				code.module().moduleName(),
				code.startingLineNumber()));
		A_Map fiberGlobals = fiber.fiberGlobals();
		fiberGlobals = fiberGlobals.mapAtPuttingCanDestroy(
			CLIENT_DATA_GLOBAL_KEY.atom, clientParseData, true);
		fiber.fiberGlobals(fiberGlobals);
		fiber.textInterface(textInterface);
		if (shouldSerialize)
		{
			loader().startRecordingEffects();
		}
		Continuation1NotNull<AvailObject> adjustedSuccess = onSuccess;
		if (shouldSerialize)
		{
			final long before = AvailRuntime.captureNanos();
			adjustedSuccess = successValue ->
			{
				final long after = AvailRuntime.captureNanos();
				Interpreter.current().recordTopStatementEvaluation(
					after - before,
					module,
					lineNumber);
				loader().stopRecordingEffects();
				serializeAfterRunning(function);
				onSuccess.value(successValue);
			};
		}
		fiber.resultContinuation(adjustedSuccess);
		fiber.failureContinuation(onFailure);
		Interpreter.runOutermostFunction(runtime, fiber, function, args);
	}

	/**
	 * Generate a {@linkplain FunctionDescriptor function} from the specified
	 * {@linkplain ParseNodeDescriptor phrase} and evaluate it in the module's
	 * context; lexically enclosing variables are not considered in scope, but
	 * module variables and constants are in scope.
	 *
	 * @param expressionNode
	 *        A {@linkplain ParseNodeDescriptor parse node}.
	 * @param lineNumber
	 *        The line number on which the expression starts.
	 * @param shouldSerialize
	 *        {@code true} if the generated function should be serialized,
	 *        {@code false} otherwise.
	 * @param onSuccess
	 *        What to do with the result of the evaluation.
	 * @param onFailure
	 *        What to do after a failure.
	 */
	public void evaluatePhraseThen (
		final A_Phrase expressionNode,
		final int lineNumber,
		final boolean shouldSerialize,
		final Continuation1NotNull<AvailObject> onSuccess,
		final Continuation1NotNull<Throwable> onFailure)
	{
		evaluateFunctionThen(
			createFunctionForPhrase(
				expressionNode, module(), lineNumber),
			lineNumber,
			Collections.<AvailObject>emptyList(),
			emptyMap(),
			shouldSerialize,
			onSuccess,
			onFailure);
	}

	/**
	 * Evaluate the given parse node.  Pass the result to the continuation,
	 * or if it fails pass the exception to the failure continuation.
	 *
	 * @param lexingState
	 *        The {@link LexingState} at which the phrase starts.
	 * @param expression
	 *        The phrase to evaluate.
	 * @param continuation
	 *        What to do with the result of evaluation.
	 * @param onFailure
	 *        What to do after a failure.
	 */
	void evaluatePhraseAtThen (
		final LexingState lexingState,
		final A_Phrase expression,
		final Continuation1NotNull<AvailObject> continuation,
		final Continuation1NotNull<Throwable> onFailure)
	{
		startWorkUnit();
		final AtomicBoolean hasRunEither = new AtomicBoolean(false);
		evaluatePhraseThen(
			expression,
			lexingState.lineNumber,
			false,
			workUnitCompletion(lexingState, hasRunEither, continuation),
			workUnitCompletion(lexingState, hasRunEither, onFailure));
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
		if (loader().statementCanBeSummarized())
		{
			final List<LoadingEffect> effects = loader().recordedEffects();
			if (!effects.isEmpty())
			{
				// Output summarized functions instead of what ran.  Associate
				// the original phrase with it, which allows the subphrases to
				// be marked up in an editor and stepped in a debugger, even
				// though the top-level phrase itself will be invalid.
				final L1InstructionWriter writer =
					new L1InstructionWriter(
						module,
						function.code().startingLineNumber(),
						function.code().originatingPhrase());
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
						emptyTuple());
				if (AvailLoader.debugUnsummarizedStatements)
				{
					System.out.println(
						module.moduleName().asNativeString()
							+ ":" + function.code().startingLineNumber()
							+ " Summary -- " + function);
				}
				serializer.serialize(summaryFunction);
			}
		}
		else
		{
			// Can't summarize; write the original function.
			if (AvailLoader.debugUnsummarizedStatements)
			{
				System.out.println(
					module
						+ ":" + function.code().startingLineNumber()
						+ " Unsummarized -- " + function);
			}
			serializer.serialize(function);
		}
	}

	/**
	 * Serialize the given function without attempting to summarize it into
	 * equivalent statements.
	 *
	 * @param function The function that has already run.
	 */
	@InnerAccess synchronized void serializeWithoutSummary (
		final A_Function function)
	{
		if (AvailLoader.debugUnsummarizedStatements)
		{
			System.out.println(
				module
					+ ":" + function.code().startingLineNumber()
					+ " Forced -- " + function);
		}
		serializer.serialize(function);
	}

	/**
	 * Report an {@linkplain ProblemType#INTERNAL internal} {@linkplain
	 * Problem problem}.
	 *
	 * @param lineNumber
	 *        The one-based line number on which the problem occurs.
	 * @param position
	 *        The one-based position in the source at which the problem occurs.
	 * @param e
	 *        The unexpected {@linkplain Throwable exception} that is the
	 *        proximal cause of the problem.
	 */
	@InnerAccess void reportInternalProblem (
		final int lineNumber,
		final int position,
		final Throwable e)
	{
		diagnostics.isShuttingDown = true;
		diagnostics.compilationIsInvalid = true;
		final Problem problem = new Problem(
			moduleName(),
			lineNumber,
			position,
			INTERNAL,
			"Internal error: {0}\n{1}",
			e.getMessage(),
			trace(e))
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
	 * @param lineNumber
	 *        The one-based line number on which the problem occurs.
	 * @param position
	 *        The one-based position in the source at which the problem occurs.
	 * @param e
	 *        The unexpected {@linkplain Throwable exception} that is the
	 *        proximal cause of the problem.
	 */
	@InnerAccess void reportExecutionProblem (
		final int lineNumber,
		final int position,
		final Throwable e)
	{
		diagnostics.compilationIsInvalid = true;
		if (e instanceof FiberTerminationException)
		{
			diagnostics.handleProblem(
				new Problem(
					moduleName(),
					lineNumber,
					position,
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
		diagnostics.handleProblem(
			new Problem(
				moduleName(),
				lineNumber,
				position,
				EXECUTION,
				"Execution error: {0}\n{1}",
				e.getMessage(),
				trace(e))
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
	 * @param lineNumber
	 *        The one-based line number on which the problem occurs.
	 * @param position
	 *        The one-based position in the source at which the problem occurs.
	 * @param e
	 *        The {@linkplain AvailAssertionFailedException assertion failure}.
	 */
	@InnerAccess void reportAssertionFailureProblem (
		final int lineNumber,
		final int position,
		final AvailAssertionFailedException e)
	{
		diagnostics.compilationIsInvalid = true;
		diagnostics.handleProblem(new Problem(
			moduleName(),
			lineNumber,
			position,
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
	 * @param lineNumber
	 *        The one-based line number on which the problem occurs.
	 * @param position
	 *        The one-based position in the source at which the problem occurs.
	 * @param e
	 *        The {@linkplain AvailEmergencyExitException emergency exit
	 *        failure}.
	 */
	@InnerAccess void reportEmergencyExitProblem (
		final int lineNumber,
		final int position,
		final AvailEmergencyExitException e)
	{
		diagnostics.compilationIsInvalid = true;
		diagnostics.handleProblem(new Problem(
			moduleName(),
			lineNumber,
			position,
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