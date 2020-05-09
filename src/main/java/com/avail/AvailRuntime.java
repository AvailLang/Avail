/*
 * AvailRuntime.java
 * Copyright © 1993-2019, The Avail Foundation, LLC.
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

package com.avail;

import com.avail.AvailRuntimeSupport.Clock;
import com.avail.CallbackSystem.Callback;
import com.avail.annotations.ThreadSafe;
import com.avail.builder.ModuleNameResolver;
import com.avail.builder.ModuleRoots;
import com.avail.builder.ResolvedModuleName;
import com.avail.descriptor.A_Fiber;
import com.avail.descriptor.A_Module;
import com.avail.descriptor.representation.AvailObject;
import com.avail.descriptor.CharacterDescriptor;
import com.avail.descriptor.FiberDescriptor;
import com.avail.descriptor.FiberDescriptor.ExecutionState;
import com.avail.descriptor.FiberDescriptor.TraceFlag;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.descriptor.atoms.A_Atom;
import com.avail.descriptor.atoms.AtomDescriptor;
import com.avail.descriptor.atoms.AtomDescriptor.SpecialAtom;
import com.avail.descriptor.bundles.A_Bundle;
import com.avail.descriptor.functions.A_Function;
import com.avail.descriptor.functions.A_RawFunction;
import com.avail.descriptor.functions.FunctionDescriptor;
import com.avail.descriptor.maps.A_Map;
import com.avail.descriptor.maps.MapDescriptor;
import com.avail.descriptor.maps.MapDescriptor.Entry;
import com.avail.descriptor.methods.*;
import com.avail.descriptor.methods.MethodDescriptor.SpecialMethodAtom;
import com.avail.descriptor.pojos.RawPojoDescriptor;
import com.avail.descriptor.representation.A_BasicObject;
import com.avail.descriptor.sets.A_Set;
import com.avail.descriptor.sets.SetDescriptor;
import com.avail.descriptor.tokens.TokenDescriptor.StaticInit;
import com.avail.descriptor.tokens.TokenDescriptor.TokenType;
import com.avail.descriptor.tuples.A_String;
import com.avail.descriptor.tuples.A_Tuple;
import com.avail.descriptor.tuples.StringDescriptor;
import com.avail.descriptor.tuples.TupleDescriptor;
import com.avail.descriptor.types.A_Type;
import com.avail.descriptor.types.TypeDescriptor;
import com.avail.descriptor.variables.A_Variable;
import com.avail.descriptor.variables.VariableDescriptor;
import com.avail.descriptor.variables.VariableDescriptor.VariableAccessReactor;
import com.avail.exceptions.AvailRuntimeException;
import com.avail.exceptions.MalformedMessageException;
import com.avail.interpreter.execution.AvailLoader;
import com.avail.interpreter.execution.Interpreter;
import com.avail.interpreter.Primitive;
import com.avail.interpreter.levelTwo.L2Chunk;
import com.avail.interpreter.primitive.general.P_EmergencyExit;
import com.avail.interpreter.primitive.general.P_ToString;
import com.avail.io.IOSystem;
import com.avail.io.TextInterface;
import com.avail.optimizer.jvm.CheckedMethod;
import com.avail.optimizer.jvm.ReferencedInGeneratedCode;
import com.avail.performance.Statistic;
import com.avail.utility.evaluation.Continuation0;
import com.avail.utility.evaluation.OnceSupplier;

import javax.annotation.Nullable;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import static com.avail.AvailRuntime.HookType.*;
import static com.avail.AvailRuntimeConfiguration.availableProcessors;
import static com.avail.AvailRuntimeConfiguration.maxInterpreters;
import static com.avail.descriptor.NilDescriptor.nil;
import static com.avail.descriptor.atoms.AtomDescriptor.falseObject;
import static com.avail.descriptor.atoms.AtomDescriptor.trueObject;
import static com.avail.descriptor.functions.CompiledCodeDescriptor.newPrimitiveRawFunction;
import static com.avail.descriptor.functions.FunctionDescriptor.createFunction;
import static com.avail.descriptor.functions.FunctionDescriptor.newCrashFunction;
import static com.avail.descriptor.maps.MapDescriptor.emptyMap;
import static com.avail.descriptor.numbers.DoubleDescriptor.fromDouble;
import static com.avail.descriptor.numbers.InfinityDescriptor.negativeInfinity;
import static com.avail.descriptor.numbers.InfinityDescriptor.positiveInfinity;
import static com.avail.descriptor.numbers.IntegerDescriptor.*;
import static com.avail.descriptor.objects.ObjectTypeDescriptor.*;
import static com.avail.descriptor.parsing.LexerDescriptor.lexerBodyFunctionType;
import static com.avail.descriptor.parsing.LexerDescriptor.lexerFilterFunctionType;
import static com.avail.descriptor.pojos.PojoDescriptor.nullPojo;
import static com.avail.descriptor.pojos.RawPojoDescriptor.identityPojo;
import static com.avail.descriptor.sets.SetDescriptor.emptySet;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.generateObjectTupleFrom;
import static com.avail.descriptor.tuples.ObjectTupleDescriptor.tuple;
import static com.avail.descriptor.tuples.StringDescriptor.stringFrom;
import static com.avail.descriptor.tuples.TupleDescriptor.*;
import static com.avail.descriptor.types.BottomPojoTypeDescriptor.pojoBottom;
import static com.avail.descriptor.types.BottomTypeDescriptor.bottom;
import static com.avail.descriptor.types.BottomTypeDescriptor.bottomMeta;
import static com.avail.descriptor.types.CompiledCodeTypeDescriptor.mostGeneralCompiledCodeType;
import static com.avail.descriptor.types.ContinuationTypeDescriptor.*;
import static com.avail.descriptor.types.EnumerationTypeDescriptor.booleanType;
import static com.avail.descriptor.types.FiberTypeDescriptor.fiberMeta;
import static com.avail.descriptor.types.FiberTypeDescriptor.mostGeneralFiberType;
import static com.avail.descriptor.types.FunctionTypeDescriptor.*;
import static com.avail.descriptor.types.InstanceMetaDescriptor.*;
import static com.avail.descriptor.types.InstanceTypeDescriptor.instanceType;
import static com.avail.descriptor.types.IntegerRangeTypeDescriptor.*;
import static com.avail.descriptor.types.LiteralTokenTypeDescriptor.mostGeneralLiteralTokenType;
import static com.avail.descriptor.types.MapTypeDescriptor.*;
import static com.avail.descriptor.types.PhraseTypeDescriptor.PhraseKind.*;
import static com.avail.descriptor.types.PojoTypeDescriptor.*;
import static com.avail.descriptor.types.SetTypeDescriptor.*;
import static com.avail.descriptor.types.TupleTypeDescriptor.*;
import static com.avail.descriptor.types.TypeDescriptor.Types.*;
import static com.avail.descriptor.types.VariableTypeDescriptor.*;
import static com.avail.exceptions.AvailErrorCode.*;
import static com.avail.optimizer.jvm.CheckedMethod.instanceMethod;
import static com.avail.utility.Nulls.stripNull;
import static com.avail.utility.StackPrinter.trace;
import static java.lang.Math.min;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * An {@code AvailRuntime} comprises the {@linkplain ModuleDescriptor
 * modules}, {@linkplain MethodDescriptor methods}, and {@linkplain
 * #specialObject(int) special objects} that define an Avail system. It also
 * manages global resources, such as file connections.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
public final class AvailRuntime
{
	/**
	 * Answer the Avail runtime associated with the current {@linkplain Thread
	 * thread}.
	 *
	 * @return The Avail runtime of the current thread.
	 */
	public static AvailRuntime currentRuntime ()
	{
		return AvailThread.current().runtime;
	}

	/** The {@link IOSystem} for this runtime. */
	@SuppressWarnings("ThisEscapedInObjectConstruction")
	private final IOSystem ioSystem = new IOSystem(this);

	/**
	 * Answer this runtime's {@link IOSystem}.
	 *
	 * @return An {@link IOSystem}.
	 */
	public IOSystem ioSystem ()
	{
		return ioSystem;
	}

	/** The {@link CallbackSystem} for this runtime. */
	private final CallbackSystem callbackSystem = new CallbackSystem();

	/**
	 * Answer this runtime's {@link CallbackSystem}.
	 *
	 * @return An {@link CallbackSystem}.
	 */
	public CallbackSystem callbackSystem ()
	{
		return callbackSystem;
	}

	/**
	 * A counter from which unique interpreter indices in [0..maxInterpreters)
	 * are allotted.
	 */
	private final AtomicInteger nextInterpreterIndex = new AtomicInteger(0);

	/**
	 * Allocate the next interpreter index in [0..maxInterpreters)
	 * thread-safely.
	 *
	 * @return A new unique interpreter index.
	 */
	public synchronized int allocateInterpreterIndex ()
	{
		final int index = nextInterpreterIndex.getAndIncrement();
		assert index < maxInterpreters;
		return index;
	}

	/**
	 * The {@linkplain ThreadPoolExecutor thread pool executor} for
	 * this {@linkplain AvailRuntime Avail runtime}.
	 */
	@SuppressWarnings("ThisEscapedInObjectConstruction")
	private final ThreadPoolExecutor executor =
		new ThreadPoolExecutor(
			min(availableProcessors, maxInterpreters),
			maxInterpreters,
			10L,
			TimeUnit.SECONDS,
			new PriorityBlockingQueue<>(),
			runnable -> new AvailThread(runnable, new Interpreter(this)),
			new AbortPolicy());

	/**
	 * Schedule the specified {@linkplain AvailTask task} for eventual
	 * execution. The implementation is free to run the task immediately or
	 * delay its execution arbitrarily. The task is guaranteed to execute on an
	 * {@linkplain AvailThread Avail thread}.
	 *
	 * @param task A task.
	 */
	public void execute (final AvailTask task)
	{
		executor.execute(task);
	}

	/**
	 * Schedule the specified {@linkplain AvailTask task} for eventual
	 * execution. The implementation is free to run the task immediately or
	 * delay its execution arbitrarily. The task is guaranteed to execute on an
	 * {@linkplain AvailThread Avail thread}.
	 *
	 * @param priority
	 *        The desired priority, a long tied to milliseconds since the
	 *        current epoch.
	 * @param body
	 *        The {@link Continuation0} to execute for this task.
	 */
	public void execute (
		final int priority,
		final Continuation0 body)
	{
		executor.execute(new AvailTask(priority, body));
	}

	/**
	 * The {@linkplain Timer timer} that managed scheduled {@linkplain
	 * TimerTask tasks} for this {@linkplain AvailRuntime runtime}. The timer
	 * thread is not an {@linkplain AvailThread Avail thread}, and therefore
	 * cannot directly execute {@linkplain FiberDescriptor fibers}. It may,
	 * however, schedule fiber-related tasks.
	 */
	public final Timer timer = new Timer(
		"timer for Avail runtime",
		true);

	/**
	 * The number of clock ticks since this {@linkplain AvailRuntime runtime}
	 * was created.
	 */
	public final Clock clock = new Clock();

	// Schedule a fixed-rate timer task to increment the runtime clock.
	{
		timer.schedule(
			new TimerTask()
			{
				@Override
				public void run ()
				{
					clock.increment();
				}
			},
			10,
			10);
	}

	/**
	 * Perform an integrity check on the parser data structures.  Report the
	 * findings to System.out.
	 */
	public void integrityCheck ()
	{
		System.out.println("Integrity check:");
		runtimeLock.writeLock().lock();
		try
		{
			final Set<A_Atom> atoms = new HashSet<>();
			final Set<A_Definition> definitions = new HashSet<>();
			for (final Entry moduleEntry : modules.mapIterable())
			{
				final A_Module module = moduleEntry.value();
				atoms.addAll(
					toList(
						module.newNames().valuesAsTuple()));
				atoms.addAll(
					toList(
						module.visibleNames().asTuple()));
				A_Tuple atomSets = module.importedNames().valuesAsTuple();
				atomSets = atomSets.concatenateWith(
					module.privateNames().valuesAsTuple(), true);
				for (final A_Set atomSet : atomSets)
				{
					atoms.addAll(
						toList(atomSet.asTuple()));
				}
				for (final A_Definition definition : module.methodDefinitions())
				{
					if (definitions.contains(definition))
					{
						System.out.println(
							"Duplicate definition: " + definition);
					}
					definitions.add(definition);
				}
			}
			final Set<A_Method> methods = new HashSet<>();
			for (final A_Atom atom : atoms)
			{
				final A_Bundle bundle = A_Atom.Companion.bundleOrNil(atom);
				if (!bundle.equalsNil())
				{
					methods.add(A_Bundle.Companion.bundleMethod(bundle));
				}
			}
			for (final A_Method method : methods)
			{
				final Set<A_Definition> bundleDefinitions =
					new HashSet<>(toList(method.definitionsTuple()));
				bundleDefinitions.addAll(
					toList(method.macroDefinitionsTuple()));
				for (final A_Bundle bundle : method.bundles())
				{
					final A_Map bundlePlans = A_Bundle.Companion.definitionParsingPlans(bundle);
					if (bundlePlans.mapSize() != bundleDefinitions.size())
					{
						System.out.println(
							"Mismatched definitions / plans:"
								+ "\n\tbundle = " + bundle
								+ "\n\tdefinitions# = "
								+ bundleDefinitions.size()
								+ "\n\tplans# = " + bundlePlans.mapSize());
					}
				}
			}
			System.out.println("done.");
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * The {@linkplain ModuleNameResolver module name resolver} that this
	 * {@linkplain AvailRuntime runtime} should use to resolve unqualified
	 * {@linkplain ModuleDescriptor module} names.
	 */
	private final ModuleNameResolver moduleNameResolver;

	/**
	 * Answer the {@linkplain ModuleNameResolver module name resolver} that this
	 * runtime should use to resolve unqualified {@linkplain ModuleDescriptor
	 * module} names.
	 *
	 * @return A {@linkplain ModuleNameResolver module name resolver}.
	 */
	public ModuleNameResolver moduleNameResolver ()
	{
		return stripNull(moduleNameResolver);
	}

	/**
	 * Answer the Avail {@linkplain ModuleRoots module roots}.
	 *
	 * @return The Avail {@linkplain ModuleRoots module roots}.
	 */
	@ThreadSafe
	public ModuleRoots moduleRoots ()
	{
		return moduleNameResolver().getModuleRoots();
	}

	/**
	 * The {@linkplain ClassLoader class loader} that should be used to locate
	 * and load Java {@linkplain Class classes}.
	 */
	private final ClassLoader classLoader;

	/**
	 * Answer the {@linkplain ClassLoader class loader} that should be used to
	 * locate and load Java {@linkplain Class classes}.
	 *
	 * @return A class loader.
	 */
	public ClassLoader classLoader ()
	{
		return classLoader;
	}

	/**
	 * Look up the given Java class name, and create a suitable Java POJO {@link
	 * A_Type}.  Capture the classParameters, since the Avail POJO mechanism
	 * doesn't erase generics.
	 *
	 * @param className
	 *        The full name of the Java class.
	 * @param classParameters
	 *        The type parameters for specializing the Java class.
	 * @return An {@link A_Type} representing the specialized Java class.
	 * @throws AvailRuntimeException
	 *         If the class can't be found or can't be parameterized as
	 *         requested, or if it's within the forbidden com.avail namespace.
	 */
	@SuppressWarnings("ThrowsRuntimeException")
	public A_Type lookupJavaType (
		final A_String className,
		final A_Tuple classParameters)
	throws AvailRuntimeException
	{
		final Class<?> rawClass = lookupRawJavaClass(className);
		// Check that the correct number of type parameters have been supplied.
		// Don't bother to check the bounds of the type parameters. Incorrect
		// bounds will cause some method and constructor lookups to fail, but
		// that's fine.
		final TypeVariable<?>[] typeVars = rawClass.getTypeParameters();
		if (typeVars.length != classParameters.tupleSize())
		{
			throw new AvailRuntimeException(E_INCORRECT_NUMBER_OF_ARGUMENTS);
		}
		// Replace all occurrences of the pojo self type atom with actual
		// pojo self types.
		final A_Tuple realParameters = generateObjectTupleFrom(
			classParameters.tupleSize(),
			i ->
			{
				final A_BasicObject parameter = classParameters.tupleAt(i);
				return parameter.equals(pojoSelfType())
					? selfTypeForClass(rawClass)
					: parameter;
			});
		realParameters.makeImmutable();
		// Construct and answer the pojo type.
		return pojoTypeForClassWithTypeArguments(rawClass, realParameters);
	}

	/**
	 * Look up the given Java class name, and create a suitable Java raw POJO
	 * for this raw Java class.  The raw class does not capture type parameters.
	 *
	 * @param className
	 *        The full name of the Java class.
	 * @return The raw Java {@link Class} (i.e., without type parameters bound).
	 * @throws AvailRuntimeException
	 *         If the class can't be found, or if it's within the forbidden
	 *         com.avail namespace.
	 */
	public Class<?> lookupRawJavaClass (final A_String className)
	{
		// Forbid access to the Avail implementation's packages.
		final String nativeClassName = className.asNativeString();
		if (nativeClassName.startsWith("com.avail"))
		{
			throw new AvailRuntimeException(E_JAVA_CLASS_NOT_AVAILABLE);
		}
		// Look up the raw Java class using the interpreter's runtime's
		// class loader.
		final Class<?> rawClass;
		try
		{
			rawClass = Class.forName(
				className.asNativeString(), true, classLoader());
		}
		catch (final ClassNotFoundException e)
		{
			throw new AvailRuntimeException(E_JAVA_CLASS_NOT_AVAILABLE);
		}
		return rawClass;
	}

	/**
	 * The {@linkplain AvailRuntime runtime}'s default {@linkplain
	 * TextInterface text interface}.
	 */
	private TextInterface textInterface = TextInterface.system();

	/**
	 * A {@linkplain RawPojoDescriptor raw pojo} wrapping the {@linkplain
	 * #textInterface default} {@linkplain TextInterface text interface}.
	 */
	private AvailObject textInterfacePojo = identityPojo(textInterface);

	/**
	 * Answer the runtime's default {@linkplain TextInterface text interface}.
	 *
	 * @return The default text interface.
	 */
	@ThreadSafe
	public TextInterface textInterface ()
	{
		runtimeLock.readLock().lock();
		try
		{
			return textInterface;
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Answer the {@linkplain RawPojoDescriptor raw pojo} that wraps the
	 * {@linkplain #textInterface default} {@linkplain TextInterface text
	 * interface}.
	 *
	 * @return The raw pojo holding the default text interface.
	 */
	@ThreadSafe
	public AvailObject textInterfacePojo ()
	{
		runtimeLock.readLock().lock();
		try
		{
			return textInterfacePojo;
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Set the runtime's default {@linkplain TextInterface text interface}.
	 *
	 * @param textInterface
	 *        The new default text interface.
	 */
	@ThreadSafe
	public void setTextInterface (final TextInterface textInterface)
	{
		runtimeLock.writeLock().lock();
		try
		{
			this.textInterface = textInterface;
			this.textInterfacePojo = identityPojo(textInterface);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * A {@code HookType} describes an abstract missing behavior in the virtual
	 * machine, where an actual hook will have to be constructed to hold an
	 * {@link A_Function} within each separate {@code AvailRuntime}.
	 */
	public enum HookType
	{
		/**
		 * The {@code HookType} for a hook that holds the stringification
		 * function.
		 */
		STRINGIFICATION(
			"«stringification»",
			functionType(tuple(ANY.o()), stringType()),
			P_ToString.INSTANCE),

		/**
		 * The {@code HookType} for a hook that holds the function to invoke
		 * whenever an unassigned variable is read.
		 */
		READ_UNASSIGNED_VARIABLE(
			"«cannot read unassigned variable»",
			functionType(emptyTuple(), bottom()),
			null),

		/**
		 * The {@code HookType} for a hook that holds the function to invoke
		 * whenever a returned value disagrees with the expected type.
		 */
		RESULT_DISAGREED_WITH_EXPECTED_TYPE(
			"«return result disagreed with expected type»",
			functionType(
				tuple(
					mostGeneralFunctionType(),
					topMeta(),
					variableTypeFor(ANY.o())),
				bottom()),
			null),

		/**
		 * The {@code HookType} for a hook that holds the function to invoke
		 * whenever an {@link A_Method} send fails for a definitional reason.
		 */
		INVALID_MESSAGE_SEND(
			"«failed method lookup»",
			functionType(
				tuple(
					enumerationWith(
						SetDescriptor.set(
							E_NO_METHOD,
							E_NO_METHOD_DEFINITION,
							E_AMBIGUOUS_METHOD_DEFINITION,
							E_FORWARD_METHOD_DEFINITION,
							E_ABSTRACT_METHOD_DEFINITION)),
					METHOD.o(),
					mostGeneralTupleType()),
				bottom()),
			null),

		/**
		 * The {@code HookType} for a hook that holds the {@link A_Function} to
		 * invoke whenever an {@link A_Variable} with {@linkplain
		 * VariableAccessReactor write reactors} is written to when {@linkplain
		 * TraceFlag#TRACE_VARIABLE_WRITES write tracing} is not enabled.
		 */
		IMPLICIT_OBSERVE(
			"«variable with a write reactor was written without write-tracing»",
			functionType(
				tuple(
					mostGeneralFunctionType(),
					mostGeneralTupleType()),
				TOP.o()),
			null),

		/**
		 * The {@code HookType} for a hook that holds the {@link A_Function} to
		 * invoke when an exception is caught in a Pojo invocation of a Java
		 * method or {@link Callback}.
		 */
		RAISE_JAVA_EXCEPTION_IN_AVAIL(
			"«raise Java exception in Avail»",
			functionType(
				tuple(
					pojoTypeForClass(Throwable.class)),
				bottom()),
			null);

		/** The name to attach to functions plugged into this hook. */
		final A_String hookName;

		/**
		 * The {@link A_Function} {@link A_Type} that hooks of this type use.
		 */
		public final A_Type functionType;

		/**
		 * A {@link Supplier} of a default {@link A_Function} to use for this
		 * hook type.
		 */
		final Supplier<A_Function> defaultFunctionSupplier;

		/**
		 * Create a hook type.
		 *
		 * @param hookName
		 *        The name to attach to the {@link A_Function}s that are plugged
		 *        into hooks of this type.
		 * @param functionType
		 *        The signature of functions that may be plugged into hook of
		 *        this type.
		 * @param primitive
		 *        The {@link Primitive} around which to synthesize a default
		 *        {@link A_Function} for hooks of this type.  If this is {@code
		 *        null}, a function that invokes {@link P_EmergencyExit} will be
		 *        synthesized instead.
		 */
		HookType (
			final String hookName,
			final A_Type functionType,
			final @Nullable Primitive primitive)
		{
			this.hookName = stringFrom(hookName);
			this.functionType = functionType;
			if (primitive == null)
			{
				// Create an invocation of P_EmergencyExit.
				final A_Type argumentsTupleType = functionType.argsTupleType();
				final A_Tuple argumentTypesTuple =
					argumentsTupleType.tupleOfTypesFromTo(
						1,
						argumentsTupleType.sizeRange().upperBound()
							.extractInt());
				this.defaultFunctionSupplier =
					new OnceSupplier<>(
						() -> newCrashFunction(hookName, argumentTypesTuple));
			}
			else
			{
				final A_RawFunction code =
					newPrimitiveRawFunction(primitive, nil, 0);
				code.setMethodName(this.hookName);
				this.defaultFunctionSupplier =
					new OnceSupplier<>(
						() -> createFunction(code, emptyTuple()));
			}
		}

		/**
		 * Extract the current {@link A_Function} for this hook from the given
		 * runtime.
		 *
		 * @param runtime The {@link AvailRuntime} to examine.
		 * @return The {@link A_Function} currently in that hook.
		 */
		public A_Function get (final AvailRuntime runtime)
		{
			return runtime.hooks.get(this).get();
		}

		/**
		 * Set this hook for the given runtime to the given function.
		 *
		 * @param runtime
		 *        The {@link AvailRuntime} to examine.
		 * @param function
		 *        The {@link A_Function} to plug into this hook.
		 */
		public void set (final AvailRuntime runtime, final A_Function function)
		{
			assert function.isInstanceOf(functionType);
			function.code().setMethodName(hookName);
			runtime.hooks.get(this).set(function);
		}
	}

	/** The collection of hooks for this runtime. */
	final EnumMap<HookType, AtomicReference<A_Function>> hooks =
		new EnumMap<>(
			Arrays.stream(HookType.values())
				.collect(
					toMap(
						identity(),
						hookType -> new AtomicReference<>(
							hookType.defaultFunctionSupplier.get()))));

	/**
	 * Answer the {@linkplain FunctionDescriptor function} to invoke whenever
	 * the value produced by a {@linkplain MethodDescriptor method} send
	 * disagrees with the {@linkplain TypeDescriptor type} expected.
	 *
	 * <p>The function takes the function that's attempting to return, the
	 * expected return type, and a new variable holding the actual result being
	 * returned (or unassigned if it was {@code nil}).</p>
	 *
	 * @return The requested function.
	 */
	@ThreadSafe
	@ReferencedInGeneratedCode
	public A_Function resultDisagreedWithExpectedTypeFunction ()
	{
		return RESULT_DISAGREED_WITH_EXPECTED_TYPE.get(this);
	}

	/**
	 * The {@link CheckedMethod} for
	 * {@link #resultDisagreedWithExpectedTypeFunction()}.
	 */
	public static final CheckedMethod
		resultDisagreedWithExpectedTypeFunctionMethod = instanceMethod(
			AvailRuntime.class,
			"resultDisagreedWithExpectedTypeFunction",
			A_Function.class);

	/**
	 * Answer the {@linkplain FunctionDescriptor function} to invoke whenever
	 * a {@linkplain VariableDescriptor variable} with {@linkplain
	 * VariableAccessReactor write reactors} is written when {@linkplain
	 * TraceFlag#TRACE_VARIABLE_WRITES write tracing} is not enabled.
	 *
	 * @return The requested function.
	 */
	@ThreadSafe
	@ReferencedInGeneratedCode
	public A_Function implicitObserveFunction ()
	{
		return IMPLICIT_OBSERVE.get(this);
	}

	/**
	 * The {@link CheckedMethod} for {@link #implicitObserveFunction()}.
	 */
	public static final CheckedMethod implicitObserveFunctionMethod =
		instanceMethod(
			AvailRuntime.class,
			"implicitObserveFunction",
			A_Function.class);

	/**
	 * Answer the {@linkplain FunctionDescriptor function} to invoke whenever a
	 * {@linkplain MethodDescriptor method} send fails for a definitional
	 * reason.
	 *
	 * @return The function to invoke whenever a message send fails dynamically
	 *         because of an ambiguous, invalid, or incomplete lookup.
	 */
	@ThreadSafe
	@ReferencedInGeneratedCode
	public A_Function invalidMessageSendFunction ()
	{
		return INVALID_MESSAGE_SEND.get(this);
	}

	/**
	 * The {@link CheckedMethod} for {@link #invalidMessageSendFunction()}.
	 */
	public static final CheckedMethod invalidMessageSendFunctionMethod =
		instanceMethod(
			AvailRuntime.class,
			"invalidMessageSendFunction",
			A_Function.class);

	/**
	 * Answer the {@link FunctionDescriptor function} to invoke whenever an
	 * unassigned variable is read.
	 *
	 * @return The function to invoke whenever an attempt is made to read an
	 *         unassigned variable.
	 */
	@ThreadSafe
	@ReferencedInGeneratedCode
	public A_Function unassignedVariableReadFunction ()
	{
		return READ_UNASSIGNED_VARIABLE.get(this);
	}

	/**
	 * The {@link CheckedMethod} for {@link #unassignedVariableReadFunction()}.
	 */
	public static final CheckedMethod unassignedVariableReadFunctionMethod =
		instanceMethod(
			AvailRuntime.class,
			"unassignedVariableReadFunction",
			A_Function.class);

	/**
	 * All {@linkplain A_Fiber fibers} that have not yet {@link
	 * ExecutionState#RETIRED retired} <em>or</em> been reclaimed by garbage
	 * collection.
	 */
	private final Set<A_Fiber> allFibers =
		synchronizedSet(newSetFromMap(new WeakHashMap<>()));

	/**
	 * Add the specified {@linkplain A_Fiber fiber} to this {@linkplain
	 * AvailRuntime runtime}.
	 *
	 * @param fiber
	 *        A fiber.
	 */
	public void registerFiber (final A_Fiber fiber)
	{
		allFibers.add(fiber);
	}

	/**
	 * Remove the specified {@linkplain A_Fiber fiber} from this {@linkplain
	 * AvailRuntime runtime}.  This should be done explicitly when a fiber
	 * retires, although the fact that {@link #allFibers} wraps a {@link
	 * WeakHashMap} ensures that fibers that are no longer referenced will still
	 * be cleaned up at some point.
	 *
	 * @param fiber
	 *        A fiber to unregister.
	 */
	void unregisterFiber (final A_Fiber fiber)
	{
		allFibers.remove(fiber);
	}

	/**
	 * Answer a {@link Set} of all {@link A_Fiber}s that have not yet
	 * {@linkplain ExecutionState#RETIRED retired}.  Retired fibers will be
	 * garbage collected when there are no remaining references.
	 *
	 * <p>Note that the result is a set which strongly holds all extant fibers,
	 * so holding this set indefinitely would keep the contained fibers from
	 * being garbage collected.</p>
	 *
	 * @return All fibers belonging to this {@code AvailRuntime}.
	 */
	public Set<A_Fiber> allFibers ()
	{
		return unmodifiableSet(new HashSet<>(allFibers));
	}

	/**
	 * Construct a new {@code AvailRuntime}.
	 *
	 * @param moduleNameResolver
	 *        The {@linkplain ModuleNameResolver module name resolver} that this
	 *        {@code AvailRuntime} should use to resolve unqualified {@linkplain
	 *        ModuleDescriptor module} names.
	 */
	public AvailRuntime (final ModuleNameResolver moduleNameResolver)
	{
		this.moduleNameResolver = moduleNameResolver;
		this.classLoader = AvailRuntime.class.getClassLoader();
	}

	/**
	 * The {@linkplain ReentrantReadWriteLock lock} that protects the
	 * {@linkplain AvailRuntime runtime} data structures against dangerous
	 * concurrent access.
	 */
	private final ReentrantReadWriteLock runtimeLock =
		new ReentrantReadWriteLock();

	/**
	 * The {@linkplain AvailObject special objects} of the {@linkplain
	 * AvailRuntime runtime}.
	 */
	private static final AvailObject[] specialObjects =
		new AvailObject[190];

	/**
	 * An unmodifiable {@link List} of the {@linkplain AvailRuntime runtime}'s
	 * special objects.
	 */
	private static final List<AvailObject> specialObjectsList =
		unmodifiableList(asList(specialObjects));

	/**
	 * Answer the {@linkplain AvailObject special objects} of the {@linkplain
	 * AvailRuntime runtime} as an {@linkplain
	 * Collections#unmodifiableList(List) immutable} {@linkplain List list}.
	 * Some elements may be {@code null}.
	 *
	 * @return The special objects.
	 */
	@ThreadSafe
	public static List<AvailObject> specialObjects ()
	{
		return specialObjectsList;
	}

	/**
	 * Answer the {@linkplain AvailObject special object} with the specified
	 * ordinal.
	 *
	 * @param ordinal
	 *        The {@linkplain AvailObject special object} with the specified
	 *        ordinal.
	 * @return An {@link AvailObject}.
	 */
	@ThreadSafe
	public static AvailObject specialObject (final int ordinal)
	{
		return specialObjects[ordinal];
	}

	/**
	 * The {@linkplain AtomDescriptor special atoms} known to the {@linkplain
	 * AvailRuntime runtime}.  Populated by the anonymous static section below.
	 */
	private static final List<A_Atom> specialAtomsList = new ArrayList<>();

	/**
	 * Answer the {@linkplain AtomDescriptor special atoms} known to the
	 * runtime as an {@linkplain Collections#unmodifiableList(List) immutable}
	 * {@linkplain List list}.
	 *
	 * @return The special atoms list.
	 */
	@ThreadSafe
	public static List<A_Atom> specialAtoms()
	{
		//noinspection AssignmentOrReturnOfFieldWithMutableType
		return specialAtomsList;
	}

	static
	{
		// Set up the special objects.
		final A_BasicObject[] specials = specialObjects;
		specials[1] = ANY.o();
		specials[2] = booleanType();
		specials[3] = CHARACTER.o();
		specials[4] = mostGeneralFunctionType();
		specials[5] = functionMeta();
		specials[6] = mostGeneralCompiledCodeType();
		specials[7] = mostGeneralVariableType();
		specials[8] = variableMeta();
		specials[9] = mostGeneralContinuationType();
		specials[10] = continuationMeta();
		specials[11] = ATOM.o();
		specials[12] = DOUBLE.o();
		specials[13] = extendedIntegers();
		specials[14] = instanceMeta(zeroOrMoreOf(anyMeta()));
		specials[15] = FLOAT.o();
		specials[16] = NUMBER.o();
		specials[17] = integers();
		specials[18] = extendedIntegersMeta();
		specials[19] = mapMeta();
		specials[20] = MODULE.o();
		specials[21] = tupleFromIntegerList(allNumericCodes());
		specials[22] = mostGeneralObjectType();
		specials[23] = mostGeneralObjectMeta();
		specials[24] = exceptionType();
		specials[25] = mostGeneralFiberType();
		specials[26] = mostGeneralSetType();
		specials[27] = setMeta();
		specials[28] = stringType();
		specials[29] = bottom();
		specials[30] = bottomMeta();
		specials[31] = NONTYPE.o();
		specials[32] = mostGeneralTupleType();
		specials[33] = tupleMeta();
		specials[34] = topMeta();
		specials[35] = TOP.o();
		specials[36] = wholeNumbers();
		specials[37] = naturalNumbers();
		specials[38] = characterCodePoints();
		specials[39] = mostGeneralMapType();
		specials[40] = MESSAGE_BUNDLE.o();
		specials[41] = MESSAGE_BUNDLE_TREE.o();
		specials[42] = METHOD.o();
		specials[43] = DEFINITION.o();
		specials[44] = ABSTRACT_DEFINITION.o();
		specials[45] = FORWARD_DEFINITION.o();
		specials[46] = METHOD_DEFINITION.o();
		specials[47] = MACRO_DEFINITION.o();
		specials[48] = zeroOrMoreOf(mostGeneralFunctionType());
		specials[50] = PARSE_PHRASE.mostGeneralType();
		specials[51] = SEQUENCE_PHRASE.mostGeneralType();
		specials[52] = EXPRESSION_PHRASE.mostGeneralType();
		specials[53] = ASSIGNMENT_PHRASE.mostGeneralType();
		specials[54] = BLOCK_PHRASE.mostGeneralType();
		specials[55] = LITERAL_PHRASE.mostGeneralType();
		specials[56] = REFERENCE_PHRASE.mostGeneralType();
		specials[57] = SEND_PHRASE.mostGeneralType();
		specials[58] = instanceMeta(mostGeneralLiteralTokenType());
		specials[59] = LIST_PHRASE.mostGeneralType();
		specials[60] = VARIABLE_USE_PHRASE.mostGeneralType();
		specials[61] = DECLARATION_PHRASE.mostGeneralType();
		specials[62] = ARGUMENT_PHRASE.mostGeneralType();
		specials[63] = LABEL_PHRASE.mostGeneralType();
		specials[64] = LOCAL_VARIABLE_PHRASE.mostGeneralType();
		specials[65] = LOCAL_CONSTANT_PHRASE.mostGeneralType();
		specials[66] = MODULE_VARIABLE_PHRASE.mostGeneralType();
		specials[67] = MODULE_CONSTANT_PHRASE.mostGeneralType();
		specials[68] = PRIMITIVE_FAILURE_REASON_PHRASE.mostGeneralType();
		specials[69] = anyMeta();
		specials[70] = trueObject();
		specials[71] = falseObject();
		specials[72] = zeroOrMoreOf(stringType());
		specials[73] = zeroOrMoreOf(topMeta());
		specials[74] = zeroOrMoreOf(
			setTypeForSizesContentType(wholeNumbers(), stringType()));
		specials[75] = setTypeForSizesContentType(wholeNumbers(), stringType());
		specials[76] = functionType(tuple(naturalNumbers()), bottom());
		specials[77] = emptySet();
		specials[78] = negativeInfinity();
		specials[79] = positiveInfinity();
		specials[80] = mostGeneralPojoType();
		specials[81] = pojoBottom();
		specials[82] = nullPojo();
		specials[83] = pojoSelfType();
		specials[84] = instanceMeta(mostGeneralPojoType());
		specials[85] = instanceMeta(mostGeneralPojoArrayType());
		specials[86] = functionTypeReturning(ANY.o());
		specials[87] = mostGeneralPojoArrayType();
		specials[88] = pojoSelfTypeAtom();
		specials[89] = pojoTypeForClass(Throwable.class);
		specials[90] = functionType(emptyTuple(), TOP.o());
		specials[91] = functionType(emptyTuple(), booleanType());
		specials[92] = variableTypeFor(mostGeneralContinuationType());
		specials[93] = mapTypeForSizesKeyTypeValueType(
			wholeNumbers(), ATOM.o(), ANY.o());
		specials[94] = mapTypeForSizesKeyTypeValueType(
			wholeNumbers(),ATOM.o(), anyMeta());
		specials[95] = tupleTypeForSizesTypesDefaultType(
			wholeNumbers(),
			emptyTuple(),
			tupleTypeForSizesTypesDefaultType(
				singleInt(2), emptyTuple(), ANY.o()));
		specials[96] = emptyMap();
		specials[97] = mapTypeForSizesKeyTypeValueType(
			naturalNumbers(), ANY.o(), ANY.o());
		specials[98] = instanceMeta(wholeNumbers());
		specials[99] = setTypeForSizesContentType(naturalNumbers(), ANY.o());
		specials[100] = tupleTypeForSizesTypesDefaultType(
			wholeNumbers(), emptyTuple(), mostGeneralTupleType());
		specials[101] = nybbles();
		specials[102] = zeroOrMoreOf(nybbles());
		specials[103] = unsignedShorts();
		specials[104] = emptyTuple();
		specials[105] = functionType(tuple(bottom()), TOP.o());
		specials[106] = instanceType(zero());
		specials[107] = functionTypeReturning(topMeta());
		specials[108] = tupleTypeForSizesTypesDefaultType(
			wholeNumbers(), emptyTuple(), functionTypeReturning(topMeta()));
		specials[109] = functionTypeReturning(PARSE_PHRASE.mostGeneralType());
		specials[110] = instanceType(two());
		specials[111] = fromDouble(Math.E);
		specials[112] = instanceType(fromDouble(Math.E));
		specials[113] = instanceMeta(PARSE_PHRASE.mostGeneralType());
		specials[114] = setTypeForSizesContentType(wholeNumbers(), ATOM.o());
		specials[115] = TOKEN.o();
		specials[116] = mostGeneralLiteralTokenType();
		specials[117] = zeroOrMoreOf(anyMeta());
		specials[118] = inclusive(zero(), positiveInfinity());
		specials[119] = zeroOrMoreOf(
			tupleTypeForSizesTypesDefaultType(
				singleInt(2), tuple(ATOM.o()), anyMeta()));
		specials[120] = zeroOrMoreOf(
			tupleTypeForSizesTypesDefaultType(
				singleInt(2), tuple(ATOM.o()), ANY.o()));
		specials[121] = zeroOrMoreOf(PARSE_PHRASE.mostGeneralType());
		specials[122] = zeroOrMoreOf(ARGUMENT_PHRASE.mostGeneralType());
		specials[123] = zeroOrMoreOf(DECLARATION_PHRASE.mostGeneralType());
		specials[124] = variableReadWriteType(TOP.o(), bottom());
		specials[125] = zeroOrMoreOf(EXPRESSION_PHRASE.create(ANY.o()));
		specials[126] = EXPRESSION_PHRASE.create(ANY.o());
		specials[127] =
			functionType(tuple(pojoTypeForClass(Throwable.class)), bottom());
		specials[128] = zeroOrMoreOf(
			setTypeForSizesContentType(wholeNumbers(), ATOM.o()));
		specials[129] = bytes();
		specials[130] = zeroOrMoreOf(zeroOrMoreOf(anyMeta()));
		specials[131] = variableReadWriteType(extendedIntegers(), bottom());
		specials[132] = fiberMeta();
		specials[133] = nonemptyStringType();
		specials[134] = setTypeForSizesContentType(
			wholeNumbers(), exceptionType());
		specials[135] = setTypeForSizesContentType(
			naturalNumbers(), stringType());
		specials[136] = setTypeForSizesContentType(naturalNumbers(), ATOM.o());
		specials[137] = oneOrMoreOf(ANY.o());
		specials[138] = zeroOrMoreOf(integers());
		specials[139] = tupleTypeForSizesTypesDefaultType(
			integerRangeType(fromInt(2), true, positiveInfinity(), false),
			emptyTuple(),
			ANY.o());
		// Some of these entries may need to be shuffled into earlier slots to
		// maintain reasonable topical consistency.
		specials[140] = FIRST_OF_SEQUENCE_PHRASE.mostGeneralType();
		specials[141] = PERMUTED_LIST_PHRASE.mostGeneralType();
		specials[142] = SUPER_CAST_PHRASE.mostGeneralType();
		specials[143] = SpecialAtom.CLIENT_DATA_GLOBAL_KEY.atom;
		specials[144] = SpecialAtom.COMPILER_SCOPE_MAP_KEY.atom;
		specials[145] = SpecialAtom.ALL_TOKENS_KEY.atom;
		specials[146] = int32();
		specials[147] = int64();
		specials[148] = STATEMENT_PHRASE.mostGeneralType();
		specials[149] = SpecialAtom.COMPILER_SCOPE_STACK_KEY.atom;
		specials[150] = EXPRESSION_AS_STATEMENT_PHRASE.mostGeneralType();
		specials[151] = oneOrMoreOf(naturalNumbers());
		specials[152] = zeroOrMoreOf(DEFINITION.o());
		specials[153] = mapTypeForSizesKeyTypeValueType(
			wholeNumbers(), stringType(), ATOM.o());
		specials[154] = SpecialAtom.MACRO_BUNDLE_KEY.atom;
		specials[155] = SpecialAtom.EXPLICIT_SUBCLASSING_KEY.atom;
		specials[156] = variableReadWriteType(mostGeneralMapType(), bottom());
		specials[157] = lexerFilterFunctionType();
		specials[158] = lexerBodyFunctionType();
		specials[159] = SpecialAtom.STATIC_TOKENS_KEY.atom;
		specials[160] = TokenType.END_OF_FILE.atom;
		specials[161] = TokenType.KEYWORD.atom;
		specials[162] = TokenType.LITERAL.atom;
		specials[163] = TokenType.OPERATOR.atom;
		specials[164] = TokenType.COMMENT.atom;
		specials[165] = TokenType.WHITESPACE.atom;
		specials[166] = inclusive(0, (1L << 32) - 1);
		specials[167] = inclusive(0, (1L << 28) - 1);
		specials[168] = inclusive(1L, 4L);
		specials[169] = inclusive(0L, 31L);
		specials[170] =
			continuationTypeForFunctionType(functionTypeReturning(TOP.o()));
		specials[171] = CharacterDescriptor.nonemptyStringOfDigitsType;

		// DO NOT CHANGE THE ORDER OF THESE ENTRIES!  Serializer compatibility
		// depends on the order of this list.
		assert specialAtomsList.isEmpty();
		specialAtomsList.addAll(asList(
			SpecialAtom.ALL_TOKENS_KEY.atom,
			SpecialAtom.CLIENT_DATA_GLOBAL_KEY.atom,
			SpecialAtom.COMPILER_SCOPE_MAP_KEY.atom,
			SpecialAtom.COMPILER_SCOPE_STACK_KEY.atom,
			SpecialAtom.EXPLICIT_SUBCLASSING_KEY.atom,
			SpecialAtom.FALSE.atom,
			SpecialAtom.FILE_KEY.atom,
			SpecialAtom.HERITABLE_KEY.atom,
			SpecialAtom.MACRO_BUNDLE_KEY.atom,
			SpecialAtom.MESSAGE_BUNDLE_KEY.atom,
			SpecialAtom.OBJECT_TYPE_NAME_PROPERTY_KEY.atom,
			SpecialAtom.SERVER_SOCKET_KEY.atom,
			SpecialAtom.SOCKET_KEY.atom,
			SpecialAtom.STATIC_TOKENS_KEY.atom,
			SpecialAtom.TRUE.atom,
			SpecialMethodAtom.ABSTRACT_DEFINER.atom,
			SpecialMethodAtom.ADD_TO_MAP_VARIABLE.atom,
			SpecialMethodAtom.ALIAS.atom,
			SpecialMethodAtom.APPLY.atom,
			SpecialMethodAtom.ATOM_PROPERTY.atom,
			SpecialMethodAtom.CONTINUATION_CALLER.atom,
			SpecialMethodAtom.CRASH.atom,
			SpecialMethodAtom.CREATE_LITERAL_PHRASE.atom,
			SpecialMethodAtom.CREATE_LITERAL_TOKEN.atom,
			SpecialMethodAtom.DECLARE_STRINGIFIER.atom,
			SpecialMethodAtom.FORWARD_DEFINER.atom,
			SpecialMethodAtom.GET_RETHROW_JAVA_EXCEPTION.atom,
			SpecialMethodAtom.GET_VARIABLE.atom,
			SpecialMethodAtom.GRAMMATICAL_RESTRICTION.atom,
			SpecialMethodAtom.MACRO_DEFINER.atom,
			SpecialMethodAtom.METHOD_DEFINER.atom,
			SpecialMethodAtom.ADD_UNLOADER.atom,
			SpecialMethodAtom.PUBLISH_ATOMS.atom,
			SpecialMethodAtom.PUBLISH_ALL_ATOMS_FROM_OTHER_MODULE.atom,
			SpecialMethodAtom.RESUME_CONTINUATION.atom,
			SpecialMethodAtom.RECORD_TYPE_NAME.atom,
			SpecialMethodAtom.CREATE_MODULE_VARIABLE.atom,
			SpecialMethodAtom.SEAL.atom,
			SpecialMethodAtom.SEMANTIC_RESTRICTION.atom,
			SpecialMethodAtom.LEXER_DEFINER.atom,
			SpecialMethodAtom.PUBLISH_NEW_NAME.atom,
			exceptionAtom(),
			stackDumpAtom(),
			pojoSelfTypeAtom(),
			TokenType.END_OF_FILE.atom,
			TokenType.KEYWORD.atom,
			TokenType.LITERAL.atom,
			TokenType.OPERATOR.atom,
			TokenType.COMMENT.atom,
			TokenType.WHITESPACE.atom,
			StaticInit.tokenTypeOrdinalKey));

		for (final A_Atom atom : specialAtomsList)
		{
			assert A_Atom.Companion.isAtomSpecial(atom);
		}

		// Make sure all special objects are shared, and also make sure all
		// special objects that are atoms are also special.
		for (int i = 0; i < specialObjects.length; i++)
		{
			final AvailObject object = specialObjects[i];
			if (object != null)
			{
				specialObjects[i] = object.makeShared();
				assert !object.isAtom() || A_Atom.Companion.isAtomSpecial(object);
			}
		}
	}

	/**
	 * The loaded Avail modules: a {@link A_Map} from {@link A_String} to {@link
	 * A_Module}.
	 */
	private A_Map modules = emptyMap();

	/**
	 * Add the specified {@linkplain ModuleDescriptor module} to the
	 * runtime.
	 *
	 * @param module A {@linkplain ModuleDescriptor module}.
	 */
	@ThreadSafe
	public void addModule (final A_Module module)
	{
		runtimeLock.writeLock().lock();
		try
		{
			assert !includesModuleNamed(module.moduleName());
			modules = modules.mapAtPuttingCanDestroy(
				module.moduleName(), module, true);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Remove the specified {@linkplain ModuleDescriptor module} from this
	 * runtime.  The module's code should already have been removed via {@link
	 * A_Module#removeFrom(AvailLoader, Continuation0)}.
	 *
	 * @param module The module to remove.
	 */
	public void unlinkModule (final A_Module module)
	{
		runtimeLock.writeLock().lock();
		try
		{
			assert includesModuleNamed(module.moduleName());
			modules = modules.mapWithoutKeyCanDestroy(
				module.moduleName(), true);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Does the runtime define a {@linkplain ModuleDescriptor module} with the
	 * specified {@linkplain TupleDescriptor name}?
	 *
	 * @param moduleName A {@linkplain TupleDescriptor name}.
	 * @return {@code true} if the runtime defines a {@linkplain
	 *         ModuleDescriptor module} with the specified {@linkplain
	 *         TupleDescriptor name}, {@code false} otherwise.
	 */
	@ThreadSafe
	public boolean includesModuleNamed (final A_String moduleName)
	{
		assert moduleName.isString();

		runtimeLock.readLock().lock();
		try
		{
			return modules.hasKey(moduleName);
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Answer my current map of modules.  Mark it {@linkplain
	 * A_BasicObject#makeShared() shared} first for safety.
	 *
	 * @return A {@link MapDescriptor map} from resolved module {@linkplain
	 *         ResolvedModuleName names} ({@linkplain StringDescriptor strings})
	 *         to {@link ModuleDescriptor modules}.
	 */
	public A_Map loadedModules ()
	{
		runtimeLock.readLock().lock();
		try
		{
			return modules.makeShared();
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Answer the {@linkplain ModuleDescriptor module} with the specified
	 * {@linkplain TupleDescriptor name}.
	 *
	 * @param moduleName A {@linkplain TupleDescriptor name}.
	 * @return A {@linkplain ModuleDescriptor module}.
	 */
	@ThreadSafe
	public AvailObject moduleAt (final A_String moduleName)
	{
		assert moduleName.isString();

		runtimeLock.readLock().lock();
		try
		{
			assert modules.hasKey(moduleName);
			return modules.mapAt(moduleName);
		}
		finally
		{
			runtimeLock.readLock().unlock();
		}
	}

	/**
	 * Unbind the specified {@linkplain DefinitionDescriptor definition} from
	 * the runtime system.  If no definitions or grammatical restrictions remain
	 * in its {@linkplain MethodDescriptor method}, then remove all of its
	 * bundles.
	 *
	 * @param definition A definition.
	 */
	@ThreadSafe
	public void removeDefinition (
		final A_Definition definition)
	{
		runtimeLock.writeLock().lock();
		try
		{
			final A_Method method = definition.definitionMethod();
			method.removeDefinition(definition);
			if (method.isMethodEmpty())
			{
				for (final A_Bundle bundle : method.bundles())
				{
					// Remove the desiccated message bundle from its atom.
					final A_Atom atom = A_Bundle.Companion.message(bundle);
					A_Atom.Companion.setAtomProperty(
						atom,
						SpecialAtom.MESSAGE_BUNDLE_KEY.atom,
						nil);
				}
			}
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Add a semantic restriction to the method associated with the
	 * given method name.
	 *
	 * @param restriction
	 *            A {@linkplain SemanticRestrictionDescriptor semantic
	 *            restriction} that validates the static types of arguments at
	 *            call sites.
	 */
	public void addSemanticRestriction (
		final A_SemanticRestriction restriction)
	{
		runtimeLock.writeLock().lock();
		try
		{
			final A_Method method = restriction.definitionMethod();
			method.addSemanticRestriction(restriction);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Remove a semantic restriction from the method associated with the
	 * given method name.
	 *
	 * @param restriction
	 *            A {@linkplain SemanticRestrictionDescriptor semantic
	 *            restriction} that validates the static types of arguments at
	 *            call sites.
	 */
	public void removeTypeRestriction (
		final A_SemanticRestriction restriction)
	{
		runtimeLock.writeLock().lock();
		try
		{
			final A_Method method = restriction.definitionMethod();
			method.removeSemanticRestriction(restriction);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Remove a grammatical restriction from the method associated with the
	 * given method name.
	 *
	 * @param restriction
	 *            A {@linkplain A_GrammaticalRestriction grammatical
	 *            restriction} that validates syntactic restrictions at call
	 *            sites.
	 */
	public void removeGrammaticalRestriction (
		final A_GrammaticalRestriction restriction)
	{
		runtimeLock.writeLock().lock();
		try
		{
			final A_Bundle bundle = restriction.restrictedBundle();
			A_Bundle.Companion.removeGrammaticalRestriction(bundle, restriction);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Add a seal to the method associated with the given method name.
	 *
	 * @param methodName
	 *        The method name, an {@linkplain AtomDescriptor atom}.
	 * @param sealSignature
	 *        The tuple of types at which to seal the method.
	 * @throws MalformedMessageException
	 *         If anything is wrong with the method name.
	 */
	public void addSeal (
			final A_Atom methodName,
			final A_Tuple sealSignature)
		throws MalformedMessageException
	{
		assert methodName.isAtom();
		assert sealSignature.isTuple();
		runtimeLock.writeLock().lock();
		try
		{
			final A_Bundle bundle = A_Atom.Companion.bundleOrCreate(methodName);
			final A_Method method = A_Bundle.Companion.bundleMethod(bundle);
			assert method.numArgs() == sealSignature.tupleSize();
			method.addSealedArgumentsType(sealSignature);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * Remove a seal from the method associated with the given method name.
	 *
	 * @param methodName
	 *        The method name, an {@linkplain AtomDescriptor atom}.
	 * @param sealSignature
	 *        The signature at which to unseal the method. There may be other
	 *        seals remaining, even at this very signature.
	 * @throws MalformedMessageException
	 *         If anything is wrong with the method name.
	 */
	public void removeSeal (
			final A_Atom methodName,
			final A_Tuple sealSignature)
		throws MalformedMessageException
	{
		runtimeLock.writeLock().lock();
		try
		{
			final A_Bundle bundle = A_Atom.Companion.bundleOrCreate(methodName);
			final A_Method method = A_Bundle.Companion.bundleMethod(bundle);
			method.removeSealedArgumentsType(sealSignature);
		}
		finally
		{
			runtimeLock.writeLock().unlock();
		}
	}

	/**
	 * The {@linkplain ReentrantLock lock} that guards access to the Level One
	 * {@linkplain #levelOneSafeTasks -safe} and {@linkplain
	 * #levelOneUnsafeTasks -unsafe} queues and counters.
	 *
	 * <p>For example, an {@link L2Chunk} may not be {@linkplain
	 * L2Chunk#invalidate(Statistic) invalidated} while any {@linkplain
	 * FiberDescriptor fiber} is {@linkplain ExecutionState#RUNNING running} a
	 * Level Two chunk.  These two activities are mutually exclusive.</p>
	 */
	private final ReentrantLock levelOneSafeLock = new ReentrantLock();

	/**
	 * The {@linkplain Queue queue} of Level One-safe {@linkplain Runnable
	 * tasks}. A Level One-safe task requires that no {@linkplain
	 * #levelOneUnsafeTasks Level One-unsafe tasks} are running.
	 *
	 * <p>For example, a {@linkplain L2Chunk Level Two chunk} may not be
	 * {@linkplain L2Chunk#invalidate(Statistic) invalidated} while any
	 * {@linkplain FiberDescriptor fiber} is {@linkplain ExecutionState#RUNNING
	 * running} a Level Two chunk. These two activities are mutually exclusive.
	 * </p>
	 */
	private final Queue<AvailTask> levelOneSafeTasks = new ArrayDeque<>();

	/**
	 * The {@linkplain Queue queue} of Level One-unsafe {@linkplain
	 * Runnable tasks}. A Level One-unsafe task requires that no
	 * {@linkplain #levelOneSafeTasks Level One-safe tasks} are running.
	 */
	private final Queue<AvailTask> levelOneUnsafeTasks = new ArrayDeque<>();

	/**
	 * The number of {@linkplain #levelOneSafeTasks Level One-safe tasks} that
	 * have been {@linkplain #executor scheduled for execution} but have not
	 * yet reached completion.
	 */
	private int incompleteLevelOneSafeTasks = 0;

	/**
	 * The number of {@linkplain #levelOneUnsafeTasks Level One-unsafe tasks}
	 * that have been {@linkplain #executor scheduled for execution} but have
	 * not yet reached completion.
	 */
	private int incompleteLevelOneUnsafeTasks = 0;

	/**
	 * Has {@linkplain #whenLevelOneUnsafeDo(int, Continuation0)} Level One
	 * safety} been requested?
	 */
	private volatile boolean levelOneSafetyRequested = false;

	/**
	 * Has {@linkplain #whenLevelOneUnsafeDo(int, Continuation0)} Level One
	 * safety} been requested?
	 *
	 * @return {@code true} if Level One safety has been requested, {@code
	 *         false} otherwise.
	 */
	public boolean levelOneSafetyRequested ()
	{
		return levelOneSafetyRequested;
	}

	/**
	 * Request that the specified {@linkplain Continuation0 continuation} be
	 * executed as a Level One-unsafe task at such a time as there are no Level
	 * One-safe tasks running.
	 *
	 * @param priority
	 *        The priority of the {@link AvailTask} to queue.  It must be in the
	 *        range [0..255].
	 * @param unsafeAction
	 *        The {@link Continuation0} to perform when Level One safety is not
	 *        required.
	 */
	public void whenLevelOneUnsafeDo (
		final int priority,
		final Continuation0 unsafeAction)
	{
		final AvailTask wrapped = new AvailTask(
			priority,
			() ->
			{
				try
				{
					unsafeAction.value();
				}
				catch (final Exception e)
				{
					System.err.println(
						"Exception in level-one-unsafe task:\n"
							+ trace(e));
				}
				finally
				{
					levelOneSafeLock.lock();
					try
					{
						incompleteLevelOneUnsafeTasks--;
						if (incompleteLevelOneUnsafeTasks == 0)
						{
							assert incompleteLevelOneSafeTasks == 0;
							incompleteLevelOneSafeTasks =
								levelOneSafeTasks.size();
							levelOneSafeTasks.forEach(this::execute);
							levelOneSafeTasks.clear();
						}
					}
					finally
					{
						levelOneSafeLock.unlock();
					}
				}
			});
		levelOneSafeLock.lock();
		try
		{
			// Hasten the execution of pending Level One-safe tasks by
			// postponing this task if there are any Level One-safe tasks
			// waiting to run.
			if (incompleteLevelOneSafeTasks == 0
				&& levelOneSafeTasks.isEmpty())
			{
				assert !levelOneSafetyRequested;
				incompleteLevelOneUnsafeTasks++;
				execute(wrapped);
			}
			else
			{
				levelOneUnsafeTasks.add(wrapped);
			}
		}
		finally
		{
			levelOneSafeLock.unlock();
		}
	}

	/**
	 * Request that the specified {@linkplain Continuation0 continuation} be
	 * executed as a Level One-safe task at such a time as there are no Level
	 * One-unsafe tasks running.
	 *
	 * @param priority
	 *        The priority of the {@link AvailTask} to queue.  It must be in the
	 *        range [0..255].
	 * @param safeAction
	 *        The {@link Continuation0} to execute when Level One safety is
	 *        ensured.
	 */
	public void whenLevelOneSafeDo (
		final int priority,
		final Continuation0 safeAction)
	{
		final AvailTask task = new AvailTask(
			priority,
			() ->
			{
				try
				{
					safeAction.value();
				}
				catch (final Exception e)
				{
					System.err.println(
						"Exception in level-one-safe task:\n"
							+ trace(e));
				}
				finally
				{
					levelOneSafeLock.lock();
					try
					{
						incompleteLevelOneSafeTasks--;
						if (incompleteLevelOneSafeTasks == 0)
						{
							assert incompleteLevelOneUnsafeTasks == 0;
							levelOneSafetyRequested = false;
							incompleteLevelOneUnsafeTasks =
								levelOneUnsafeTasks.size();
							for (final AvailTask t : levelOneUnsafeTasks)
							{
								execute(t);
							}
							levelOneUnsafeTasks.clear();
						}
					}
					finally
					{
						levelOneSafeLock.unlock();
					}
				}
			});
		levelOneSafeLock.lock();
		try
		{
			levelOneSafetyRequested = true;
			if (incompleteLevelOneUnsafeTasks == 0)
			{
				incompleteLevelOneSafeTasks++;
				execute(task);
			}
			else
			{
				levelOneSafeTasks.add(task);
			}
		}
		finally
		{
			levelOneSafeLock.unlock();
		}
	}

	/**
	 * Destroy all data structures used by this {@code AvailRuntime}.  Also
	 * disassociate it from the current {@link Thread}'s local storage.
	 */
	public void destroy ()
	{
		timer.cancel();
		executor.shutdownNow();
		ioSystem.destroy();
		callbackSystem.destroy();
		try
		{
			executor.awaitTermination(10, TimeUnit.SECONDS);
		}
		catch (final InterruptedException e)
		{
			// Ignore.
		}
		moduleNameResolver.clearCache();
		moduleNameResolver.destroy();
		modules = nil;
	}
}
