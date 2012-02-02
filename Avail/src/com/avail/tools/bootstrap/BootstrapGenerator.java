/**
 * BootstrapGenerator.java
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

package com.avail.tools.bootstrap;

import static com.avail.tools.bootstrap.Resources.*;
import static com.avail.tools.bootstrap.Resources.Key.*;
import static com.avail.descriptor.TypeDescriptor.Types.*;
import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import com.avail.AvailRuntime;
import com.avail.annotations.NotNull;
import com.avail.descriptor.*;
import com.avail.interpreter.*;
import com.avail.interpreter.Primitive.Flag;

/**
 * Generate the Avail system {@linkplain ModuleDescriptor modules} that
 * bind the infallible and fallible {@linkplain Primitive primitives}.
 *
 * @author Todd L Smith &lt;anarakul@gmail.com&gt;
 */
public final class BootstrapGenerator
{
	/** The Avail special objects. */
	private static final @NotNull List<AvailObject> specialObjects;

	/**
	 * A {@linkplain Map map} from the special objects to their indices.
	 */
	private static final
	@NotNull Map<AvailObject, Integer> specialObjectIndexMap;

	/* Capture the special objects. */
	static
	{
		AvailObject.clearAllWellKnownObjects();
		AvailObject.createAllWellKnownObjects();
		specialObjects = AvailRuntime.specialObjects();
		specialObjectIndexMap =
			new HashMap<AvailObject, Integer>(specialObjects.size());
		for (int i = 0; i < specialObjects.size(); i++)
		{
			final AvailObject specialObject = specialObjects.get(i);
			if (specialObject != null)
			{
				specialObjectIndexMap.put(specialObject, i);
			}
		}
	}

	/** The target {@linkplain Locale locale}. */
	private final @NotNull Locale locale;

	/**
	 * The {@linkplain ResourceBundle resource bundle} that contains file
	 * preambleBaseName information.
	 */
	private final @NotNull ResourceBundle preamble;

	/**
	 * The {@linkplain ResourceBundle resource bundle} that contains the Avail
	 * names of the special objects.
	 */
	private final @NotNull ResourceBundle specialObjectBundle;

	/**
	 * The {@linkplain ResourceBundle resource bundle} that contains the Avail
	 * names of the {@linkplain Primitive primitives}.
	 */
	private final @NotNull ResourceBundle primitiveBundle;

	/**
	 * Answer the correct {@linkplain File file name} for the {@linkplain
	 * ModuleDescriptor module} specified by the {@linkplain Key key}.
	 *
	 * @param key The module name key.
	 * @return The file name.
	 */
	private final @NotNull File moduleFileName (final @NotNull Key key)
	{
		return new File(String.format(
			"src/%s/%s/%s.avail/%s.avail",
			generatedPackageName.replace('.', '/'),
			locale.getLanguage(),
			preamble.getString(representativeModuleName.name()),
			preamble.getString(key.name())));
	}

	/**
	 * Answer a textual representation of the specified version {@linkplain
	 * List list} that is satisfactory for use in an Avail {@linkplain
	 * ModuleDescriptor module} header.
	 *
	 * @param versions The versions.
	 * @return The version string.
	 */
	private @NotNull String versionString (
		final @NotNull List<String> versions)
	{
		final StringBuilder builder = new StringBuilder();
		for (final String version : versions)
		{
			builder.append("\n\t\"");
			builder.append(version);
			builder.append("\",");
		}
		final String versionString = builder.toString();
		return versionString.substring(0, versionString.length() - 1);
	}

	/**
	 * Generate the preamble for the pragma-containing module.
	 *
	 * @param versions
	 *        The {@linkplain List list} of version strings supported by the
	 *        module.
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generateOriginModulePreamble (
		final @NotNull List<String> versions,
		final @NotNull PrintWriter writer)
	{
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name()),
			preamble.getString(originModuleName.name()),
			new Date()));
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name()),
			BootstrapGenerator.class.getName(),
			new Date()));
		writer.println(MessageFormat.format(
			preamble.getString(originModuleHeader.name()),
			preamble.getString(originModuleName.name()),
			versionString(versions),
			preamble.getString(bootstrapDefiningMethod.name()),
			preamble.getString(bootstrapSpecialObject.name())));
	}

	/**
	 * A {@linkplain Map map} from localized names to Avail special objects.
	 */
	private final @NotNull Map<String, AvailObject> specialObjectsByName =
		new HashMap<String, AvailObject>(specialObjects.size());

	/**
	 * A {@linkplain Map map} from Avail special objects to localized names.
	 */
	private final @NotNull Map<AvailObject, String> namesBySpecialObject =
		new HashMap<AvailObject, String>(specialObjects.size());

	/**
	 * Answer the name of the specified special object.
	 *
	 * @param specialObject A special object.
	 * @return The localized name of the special object.
	 */
	private @NotNull String specialObjectName (
		final @NotNull AvailObject specialObject)
	{
		final String name = namesBySpecialObject.get(specialObject);
		assert name != null :
			String.format("no special object for %s", specialObject);
		return name;
	}

	/**
	 * Answer a textual representation of the special objects that is
	 * satisfactory for use in an Avail {@linkplain ModuleDescriptor module}
	 * header.
	 *
	 * @return The "Names" string.
	 */
	private @NotNull String specialObjectsNamesString ()
	{
		final List<String> names = new ArrayList<String>(
			new ArrayList<String>(specialObjectsByName.keySet()));
		Collections.sort(names);
		final StringBuilder builder = new StringBuilder();
		for (final String name : names)
		{
			final AvailObject specialObject = specialObjectsByName.get(name);
			builder.append("\n\t");
			builder.append(String.format(
				"/* %3d */", specialObjectIndexMap.get(specialObject)));
			builder.append(" \"");
			builder.append(name);
			builder.append("\",");
		}
		final String namesString = builder.toString();
		return namesString.substring(0, namesString.length() - 1);
	}

	/**
	 * Generate the preamble for the special object linking module.
	 *
	 * @param versions
	 *        The {@linkplain List list} of version strings supported by the
	 *        module.
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generateSpecialObjectModulePreamble (
		final @NotNull List<String> versions,
		final @NotNull PrintWriter writer)
	{
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name()),
			preamble.getString(specialObjectsModuleName.name()),
			new Date()));
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name()),
			BootstrapGenerator.class.getName(),
			new Date()));
		writer.println(MessageFormat.format(
			preamble.getString(generalModuleHeader.name()),
			preamble.getString(specialObjectsModuleName.name()),
			versionString(versions),
			String.format(
				"\n\t\"%s\"",
				preamble.getString(originModuleName.name())),
			"",
			specialObjectsNamesString()));
	}

	/**
	 * Generate the body of the special object linking {@linkplain
	 * ModuleDescriptor module}.
	 *
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generateSpecialObjectModuleBody (
		final @NotNull PrintWriter writer)
	{
		// Find the length of the longest name.
		int length = 0;
		for (final String name : specialObjectsByName.keySet())
		{
			length = Math.max(length, name.length() + 1);
		}

		// Emit the module constants that capture the special objects.
		for (int i = 0; i < specialObjects.size(); i++)
		{
			if (specialObjects.get(i) != null)
			{
				final String key =
					specialObjectBundle.containsKey(
						specialObjectAlphabeticKey(i))
					? specialObjectAlphabeticKey(i)
					: specialObjectKey(i);
				final String constantName =
					"_" + specialObjectBundle.getString(key).replace(' ', '_');
				final int pad = length - constantName.length();
				final String format = pad > 0
					? "%s%" + pad + "s ::= %s;\n"
					: "%s%s ::= %s;\n";
				writer.printf(
					format,
					constantName,
					"",
					MessageFormat.format(
						preamble.getString(specialObjectUse.name()), i));
			}
		}

		writer.println();
		for (int i = 0; i < specialObjects.size(); i++)
		{
			if (specialObjects.get(i) != null)
			{
				final String commentKey = specialObjectCommentKey(i);
				if (specialObjectBundle.containsKey(commentKey))
				{
					writer.print(specialObjectBundle.getString(commentKey));
				}
				final String key =
					specialObjectBundle.containsKey(
						specialObjectAlphabeticKey(i))
					? specialObjectAlphabeticKey(i)
					: specialObjectKey(i);
				final String constantName =
					"_" + specialObjectBundle.getString(key).replace(' ', '_');
				writer.println(MessageFormat.format(
					preamble.getString(definingMethodUse.name()),
					stringify(
						specialObjectBundle.getString(specialObjectKey(i))),
					String.format("\n[\n\t%s;\n];\n", constantName)));
			}
		}
	}

	/**
	 * Answer the selected {@linkplain Primitive primitives}.
	 *
	 * @param fallible
	 *        {@code true} if the fallible primitives should be answered, {@code
	 *        false} if the infallible primitives should be answered, {@code
	 *        null} if all primitives should be answered.
	 * @return The selected primitives.
	 */
	private @NotNull List<Primitive> primitives (final Boolean fallible)
	{
		final List<Primitive> primitives = new ArrayList<Primitive>();
		for (final Primitive primitive : Primitive.values())
		{
			if (!primitive.hasFlag(Flag.Private) &&
				(fallible == null
				|| primitive.hasFlag(Flag.CannotFail) == !fallible))
			{
				primitives.add(primitive);
			}
		}
		return primitives;
	}

	/**
	 * A {@linkplain Map map} from localized names to Avail {@linkplain
	 * Primitive primitives}.
	 */
	private final @NotNull Map<String, Set<Primitive>> primitiveNameMap =
		new HashMap<String, Set<Primitive>>(specialObjects.size());

	/**
	 * Answer a textual representation of the specified {@linkplain Primitive
	 * primitive} names {@linkplain List list} that is satisfactory for use in
	 * an Avail {@linkplain ModuleDescriptor module} header.
	 *
	 * @param primitives The primitives.
	 * @return The "Names" string.
	 */
	private @NotNull String primitivesNamesString (
		final @NotNull List<Primitive> primitives)
	{
		final Set<Primitive> wanted = new HashSet<Primitive>(primitives);
		final List<String> names = new ArrayList<String>(
			new ArrayList<String>(primitiveNameMap.keySet()));
		Collections.sort(names);
		final StringBuilder builder = new StringBuilder();
		for (final String name : names)
		{
			final Set<Primitive> set =
				new HashSet<Primitive>(primitiveNameMap.get(name));
			set.retainAll(wanted);
			if (!set.isEmpty())
			{
				builder.append("\n\t\"");
				builder.append(name);
				builder.append("\",");
			}
		}
		final String namesString = builder.toString();
		return namesString.substring(0, namesString.length() - 1);
	}

	/**
	 * Generate the preamble for the specified {@linkplain Primitive primitive}
	 * module.
	 *
	 * @param fallible
	 *        {@code true} to indicate the fallible primitives module, {@code
	 *        false} to indicate the infallible primitives module, {@code null}
	 *        to indicate the introductory primitives module.
	 * @param versions
	 *        The {@linkplain List list} of version strings supported by the
	 *        module.
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generatePrimitiveModulePreamble (
		final Boolean fallible,
		final @NotNull List<String> versions,
		final @NotNull PrintWriter writer)
	{
		final Key key;
		if (fallible == null)
		{
			key = primitivesModuleName;
		}
		else
		{
			key = fallible
				? falliblePrimitivesModuleName
				: infalliblePrimitivesModuleName;
		}
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name()),
			preamble.getString(key.name()),
			new Date()));
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name()),
			BootstrapGenerator.class.getName(),
			new Date()));
		final StringBuilder uses = new StringBuilder();
		uses.append("\n\t\"");
		uses.append(preamble.getString(originModuleName.name()));
		uses.append('"');
		if (fallible != null)
		{
			uses.append(",\n\t\"");
			uses.append(preamble.getString(specialObjectsModuleName.name()));
			uses.append("\",\n\t\"");
			uses.append(preamble.getString(primitivesModuleName.name()));
			uses.append("\" =\n\t(");
			uses.append(primitivesNamesString(
				primitives(fallible)).replace("\t", "\t\t"));
			uses.append("\n\t)");
		}
		final StringBuilder names = new StringBuilder();
		if (fallible == null)
		{
			names.append(primitivesNamesString(primitives(fallible)));
		}
		else if (Boolean.TRUE.equals(fallible))
		{
			names.append("\n\t");
			names.append(stringify(preamble.getString(
				primitiveFailureFunctionSetterMethod.name())));
		}
		writer.println(MessageFormat.format(
			preamble.getString(generalModuleHeader.name()),
			preamble.getString(key.name()),
			versionString(versions),
			"",
			uses.toString(),
			names.toString()));
	}

	/**
	 * Answer the method parameter declarations for the specified {@linkplain
	 * Primitive primitive}.
	 *
	 * @param primitive
	 *        A primitive.
	 * @param forSemanticRestriction
	 *        {@code true} if the parameters should be shifted out one type
	 *        level for use by a semantic restriction, {@code false} otherwise.
	 * @return The textual representation of the primitive method's parameters
	 *         (indent=1).
	 */
	private @NotNull String primitiveMethodParameterDeclarations (
		final @NotNull Primitive primitive,
		final boolean forSemanticRestriction)
	{
		final StringBuilder builder = new StringBuilder();
		final AvailObject functionType = primitive.blockTypeRestriction();
		final AvailObject parameterTypes = functionType.argsTupleType();
		final AvailObject parameterCount = parameterTypes.sizeRange();
		assert parameterCount.lowerBound().equals(
				parameterCount.upperBound())
			: String.format(
				"Expected %s to have a fixed parameter count",
				primitive.name());
		for (
			int i = 1, end = parameterCount.lowerBound().extractInt();
			i <= end;
			i++)
		{
			final String argNameKey = primitiveParameterNameKey(primitive, i);
			final String argName;
			if (primitiveBundle.containsKey(argNameKey))
			{
				final String localized = primitiveBundle.getString(argNameKey);
				argName = !localized.isEmpty()
					? localized
					: preamble.getString(parameterPrefix.name()) + i;
			}
			else
			{
				argName = preamble.getString(parameterPrefix.name()) + i;
			}
			final AvailObject type = parameterTypes.typeAtIndex(i);
			final AvailObject paramType = forSemanticRestriction
				? InstanceTypeDescriptor.on(type)
				: type;
			final String typeName = specialObjectName(paramType);
			builder.append('\t');
			builder.append(argName);
			builder.append(" : ");
			builder.append(typeName);
			if (i != end)
			{
				builder.append(',');
			}
			builder.append('\n');
		}
		return builder.toString();
	}

	/**
	 * Answer the method statements for the specified {@linkplain Primitive
	 * primitive}.
	 *
	 * @param primitive
	 *        A primitive.
	 * @return The textual representation of the primitive's statements
	 *         (indent=1).
	 */
	private @NotNull String primitiveMethodStatements (
		final @NotNull Primitive primitive)
	{
		final StringBuilder builder = new StringBuilder();
		builder.append('\t');
		builder.append(preamble.getString(primitiveKeyword.name()));
		builder.append(' ');
		builder.append(primitive.primitiveNumber);
		if (!primitive.hasFlag(Flag.CannotFail))
		{
			final String varTypeName = specialObjectName(
				primitive.failureVariableType());
			builder.append(" (");
			builder.append(
				preamble.getString(primitiveFailureVariableName.name()));
			builder.append(" : ");
			builder.append(varTypeName);
			builder.append(')');
		}
		builder.append(";\n");
		if (!primitive.hasFlag(Flag.CannotFail))
		{
			builder.append('\t');
			if (primitive.hasFlag(Flag.CatchException))
			{
				final String argNameKey = primitiveParameterNameKey(
					primitive, 1);
				final String argName;
				if (primitiveBundle.containsKey(argNameKey))
				{
					final String localized =
						primitiveBundle.getString(argNameKey);
					argName = !localized.isEmpty()
						? localized
						: preamble.getString(parameterPrefix.name()) + 1;
				}
				else
				{
					argName = preamble.getString(parameterPrefix.name()) + 1;
				}
				builder.append(MessageFormat.format(
					preamble.getString(
						invokePrimitiveFailureFunctionMethodUse.name()),
					argName,
					namesBySpecialObject.get(TupleDescriptor.empty())));
			}
			else
			{
				builder.append(MessageFormat.format(
					preamble.getString(
						invokePrimitiveFailureFunctionMethodUse.name()),
					preamble.getString(primitiveFailureFunctionName.name()),
					preamble.getString(primitiveFailureVariableName.name())));
			}
			builder.append(";\n");
		}
		return builder.toString();
	}

	/**
	 * Answer a block that contains the specified (already formatted) parameter
	 * declarations and (already formatted) statements.
	 *
	 * @param declarations
	 *        The parameter declarations.
	 * @param statements
	 *        The block's statements.
	 * @param returnType
	 *        The return type.
	 * @return A textual representation of the block (indent=0).
	 */
	private @NotNull String block (
		final @NotNull String declarations,
		final @NotNull String statements,
		final AvailObject returnType)
	{
		final StringBuilder builder = new StringBuilder();
		builder.append("\n[\n");
		builder.append(declarations);
		if (!declarations.isEmpty())
		{
			builder.append("|\n");
		}
		builder.append(statements);
		builder.append(']');
		if (returnType != null)
		{
			builder.append(" : ");
			builder.append(specialObjectName(returnType));
		}
		return builder.toString();
	}

	/**
	 * Answer a comment for the specified {@linkplain Primitive primitive}.
	 *
	 * @param primitive
	 *        A primitive.
	 * @return A textual representation of the comment (indent=0).
	 */
	private @NotNull String primitiveComment (
		final @NotNull Primitive primitive)
	{
		final StringBuilder builder = new StringBuilder();
		final String commentKey = primitiveCommentKey(primitive);
		if (primitiveBundle.containsKey(commentKey))
		{
			final Object[] formatArgs = new Object[primitive.argCount()];
			for (int i = 1; i <= formatArgs.length; i++)
			{
				final String argNameKey =
					primitiveParameterNameKey(primitive, i);
				final String argName;
				if (primitiveBundle.containsKey(argNameKey))
				{
					final String localized = primitiveBundle.getString(argNameKey);
					argName = !localized.isEmpty()
						? localized
						: preamble.getString(parameterPrefix.name()) + i;
				}
				else
				{
					argName = preamble.getString(parameterPrefix.name()) + i;
				}
				formatArgs[i - 1] = argName;
			}
			builder.append(MessageFormat.format(
				primitiveBundle.getString(commentKey),
				formatArgs));
		}
		return builder.toString();
	}

	/**
	 * Generate a method from the specified name and block.
	 *
	 * @param name
	 *        The (already localized) method name.
	 * @param block
	 *        The textual block (indent=0).
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generateMethod (
		final @NotNull String name,
		final @NotNull String block,
		final @NotNull PrintWriter writer)
	{
		writer.print(MessageFormat.format(
			preamble.getString(definingMethodUse.name()),
			stringify(name),
			block));
		writer.println(';');
		writer.println();
	}

	/**
	 * Generate the bootstrap {@linkplain Primitive primitive} failure method.
	 * This will be invoked if any primitive fails during the compilation of the
	 * bootstrap modules.
	 *
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generatePrimitiveFailureMethod (
		final @NotNull PrintWriter writer)
	{
		final Primitive primitive = Primitive.prim256_EmergencyExit;
		final StringBuilder statements = new StringBuilder();
		statements.append('\t');
		statements.append(preamble.getString(primitiveKeyword.name()));
		statements.append(' ');
		statements.append(primitive.primitiveNumber);
		statements.append(";\n");
		final String block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			statements.toString(),
			primitive.blockTypeRestriction().returnType());
		generateMethod(
			preamble.getString(primitiveFailureMethod.name()),
			block,
			writer);
	}

	/**
	 * Generate the {@linkplain Primitive primitive} failure function.
	 *
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generatePrimitiveFailureFunction (
		final @NotNull PrintWriter writer)
	{
		final AvailObject functionType = FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				IntegerRangeTypeDescriptor.naturalNumbers()),
			BottomTypeDescriptor.bottom());
		writer.print(
			preamble.getString(primitiveFailureFunctionName.name()));
		writer.print(" : ");
		writer.print(specialObjectName(functionType));
		writer.println(" :=");
		writer.println("\t[");
		writer.print("\t\t");
		writer.print(preamble.getString(parameterPrefix.name()));
		writer.print(1);
		writer.print(" : ");
		writer.println(specialObjectName(ANY.o()));
		writer.println("\t|");
		writer.print("\t\t");
		writer.print(MessageFormat.format(
			preamble.getString(primitiveFailureMethodUse.name()),
			preamble.getString(parameterPrefix.name()) + 1));
		writer.println(";");
		writer.print("\t] : ");
		writer.print(specialObjectName(BottomTypeDescriptor.bottom()));
		writer.println(';');
		writer.println();
	}

	/**
	 * Generate the {@linkplain Primitive primitive} failure function setter.
	 *
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generatePrimitiveFailureFunctionSetter (
		final @NotNull PrintWriter writer)
	{
		final String argName = preamble.getString(parameterPrefix.name()) + 1;
		final StringBuilder declarations = new StringBuilder();
		declarations.append('\t');
		declarations.append(argName);
		declarations.append(" : ");
		final AvailObject functionType = FunctionTypeDescriptor.create(
			TupleDescriptor.from(
				IntegerRangeTypeDescriptor.naturalNumbers()),
			BottomTypeDescriptor.bottom());
		declarations.append(specialObjectName(functionType));
		declarations.append('\n');
		final StringBuilder statements = new StringBuilder();
		statements.append('\t');
		statements.append(
			preamble.getString(primitiveFailureFunctionName.name()));
		statements.append(" := ");
		statements.append(argName);
		statements.append(";\n");
		final String block = block(
			declarations.toString(),
			statements.toString(),
			TOP.o());
		generateMethod(
			preamble.getString(primitiveFailureFunctionSetterMethod.name()),
			block,
			writer);
	}

	/**
	 * Generate the bootstrap function application method that the exported
	 * {@linkplain Primitive primitives} use to invoke the primitive failure
	 * function.
	 *
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generateInvokePrimitiveFailureFunctionMethod (
		final @NotNull PrintWriter writer)
	{
		final Primitive primitive = Primitive.prim40_InvokeWithTuple;
		final StringBuilder statements = new StringBuilder();
		statements.append('\t');
		statements.append(preamble.getString(primitiveKeyword.name()));
		statements.append(' ');
		statements.append(primitive.primitiveNumber);
		statements.append(" (");
		statements.append(
			preamble.getString(primitiveFailureVariableName.name()));
		statements.append(" : ");
		statements.append(specialObjectName(primitive.failureVariableType()));
		statements.append(')');
		statements.append(";\n");
		statements.append('\t');
		statements.append(MessageFormat.format(
			preamble.getString(primitiveFailureMethodUse.name()),
			preamble.getString(primitiveFailureVariableName.name())));
		statements.append(";\n");
		final String block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			statements.toString(),
			TOP.o());
		generateMethod(
			preamble.getString(invokePrimitiveFailureFunctionMethod.name()),
			block,
			writer);
	}

	/**
	 * Generate the bootstrap semantic restriction application method that the
	 * bootstrap code uses to provide type-safe usage of the bootstrap function
	 * application method. Also generate the actual application of the semantic
	 * restriction.
	 *
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generatePrivateSemanticRestrictionMethod (
		final @NotNull PrintWriter writer)
	{
		final Primitive primitive = Primitive.prim248_AddSemanticRestriction;
		StringBuilder statements = new StringBuilder();
		statements.append('\t');
		statements.append(preamble.getString(primitiveKeyword.name()));
		statements.append(' ');
		statements.append(primitive.primitiveNumber);
		statements.append(" (");
		statements.append(
			preamble.getString(primitiveFailureVariableName.name()));
		statements.append(" : ");
		statements.append(specialObjectName(primitive.failureVariableType()));
		statements.append(')');
		statements.append(";\n");
		statements.append('\t');
		statements.append(MessageFormat.format(
			preamble.getString(primitiveFailureMethodUse.name()),
			preamble.getString(primitiveFailureVariableName.name())));
		statements.append(";\n");
		String block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			statements.toString(),
			TOP.o());
		generateMethod(
			preamble.getString(primitiveSemanticRestriction.name()),
			block,
			writer);
		statements = new StringBuilder();
		statements.append('\t');
		statements.append(specialObjectName(BottomTypeDescriptor.bottom()));
		statements.append(";\n");
		block = block(
			primitiveMethodParameterDeclarations(
				Primitive.prim40_InvokeWithTuple, true),
			statements.toString(),
			null);
		writer.append(MessageFormat.format(
			preamble.getString(primitiveSemanticRestrictionUse.name()),
			stringify(preamble.getString(
				invokePrimitiveFailureFunctionMethod.name())),
			block));
		writer.println(";\n");
	}

	/**
	 * Generate a linkage method for the specified {@linkplain Primitive
	 * primitive}.
	 *
	 * @param primitive
	 *        A primitive.
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generatePrimitiveMethod (
		final @NotNull Primitive primitive,
		final @NotNull PrintWriter writer)
	{
		final String name = primitive.name();
		if (!primitiveBundle.containsKey(name)
			|| primitiveBundle.getString(name).isEmpty())
		{
			System.err.println("missing key/value: " + name);
			return;
		}

		final String comment = primitiveComment(primitive);
		final String block = block(
			primitiveMethodParameterDeclarations(primitive, false),
			primitiveMethodStatements(primitive),
			primitive.blockTypeRestriction().returnType());
		writer.print(comment);
		generateMethod(primitiveBundle.getString(name), block, writer);
	}

	/**
	 * Generate the body of the specified {@linkplain Primitive primitive}
	 * module.
	 *
	 * @param fallible
	 *        {@code true} to indicate the fallible primitives module, {@code
	 *        false} to indicate the infallible primitives module.
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generatePrimitiveModuleBody (
		final Boolean fallible,
		final @NotNull PrintWriter writer)
	{
		// Generate the module variable that holds the primitive failure
		// function.
		if (Boolean.TRUE.equals(fallible))
		{
			generatePrimitiveFailureMethod(writer);
			generatePrimitiveFailureFunction(writer);
			generatePrimitiveFailureFunctionSetter(writer);
			generateInvokePrimitiveFailureFunctionMethod(writer);
			generatePrivateSemanticRestrictionMethod(writer);
		}

		// Generate the primitive methods.
		if (fallible != null)
		{
			final List<Primitive> primitives = primitives(fallible);
			for (final Primitive primitive : primitives)
			{
				if (!primitive.hasFlag(Flag.Private))
				{
					generatePrimitiveMethod(primitive, writer);
				}
			}
		}
	}

	/**
	 * Generate the preamble for the representative {@linkplain
	 * ModuleDescriptor module}.
	 *
	 * @param versions
	 *        The {@linkplain List list} of version strings supported by the
	 *        module.
	 * @param writer
	 *        The {@linkplain PrintWriter output stream}.
	 */
	private void generateRepresentativeModulePreamble (
		final @NotNull List<String> versions,
		final @NotNull PrintWriter writer)
	{
		writer.println(MessageFormat.format(
			preamble.getString(availCopyright.name()),
			preamble.getString(representativeModuleName.name()),
			new Date()));
		writer.println(MessageFormat.format(
			preamble.getString(generatedModuleNotice.name()),
			BootstrapGenerator.class.getName(),
			new Date()));
		final Key[] keys =
		{
			originModuleName,
			specialObjectsModuleName,
			primitivesModuleName,
			infalliblePrimitivesModuleName,
			falliblePrimitivesModuleName
		};
		final StringBuilder extended = new StringBuilder();
		for (final Key key : keys)
		{
			extended.append("\n\t\"");
			extended.append(preamble.getString(key.name()));
			extended.append("\",");
		}
		String extendedString = extended.toString();
		extendedString = extendedString.substring(
			0, extendedString.length() - 1);
		writer.println(MessageFormat.format(
			preamble.getString(generalModuleHeader.name()),
			preamble.getString(representativeModuleName.name()),
			versionString(versions),
			extendedString,
			"",
			""));
	}

	/**
	 * Generate the {@linkplain ModuleDescriptor module} that contains the
	 * pragmas.
	 *
	 * @param versions
	 *        The supported versions.
	 * @throws IOException
	 *         If the source module could not be written.
	 */
	private void generateOriginModule (
			final @NotNull List<String> versions)
		throws IOException
	{
		final File fileName = moduleFileName(originModuleName);
		assert fileName.getPath().endsWith(".avail");
		final PrintWriter writer = new PrintWriter(fileName, "UTF-8");
		generateOriginModulePreamble(versions, writer);
		writer.close();
	}

	/**
	 * Generate the {@linkplain ModuleDescriptor module} that binds the special
	 * objects to Avail names.
	 *
	 * @param versions
	 *        The supported versions.
	 * @throws IOException
	 *         If the source module could not be written.
	 */
	private void generateSpecialObjectsModule (
			final @NotNull List<String> versions)
		throws IOException
	{
		final File fileName = moduleFileName(specialObjectsModuleName);
		assert fileName.getPath().endsWith(".avail");
		final PrintWriter writer = new PrintWriter(fileName, "UTF-8");
		generateSpecialObjectModulePreamble(versions, writer);
		generateSpecialObjectModuleBody(writer);
		writer.close();
	}

	/**
	 * Generate the specified primitive {@linkplain ModuleDescriptor module}.
	 *
	 * @param fallible
	 *        {@code true} to indicate the fallible primitives module, {@code
	 *        false} to indicate the infallible primitives module, {@code null}
	 *        to indicate the introductory primitives module.
	 * @param versions
	 *        The {@linkplain List list} of version strings supported by the
	 *        module.
	 * @throws IOException
	 *         If the source module could not be written.
	 */
	private void generatePrimitiveModule (
			final Boolean fallible,
			final @NotNull List<String> versions)
		throws IOException
	{
		final Key key;
		if (fallible == null)
		{
			key = primitivesModuleName;
		}
		else
		{
			key = fallible
				? falliblePrimitivesModuleName
				: infalliblePrimitivesModuleName;
		}
		final File fileName = moduleFileName(key);
		assert fileName.getPath().endsWith(".avail");
		final PrintWriter writer = new PrintWriter(fileName, "UTF-8");
		generatePrimitiveModulePreamble(fallible, versions, writer);
		generatePrimitiveModuleBody(fallible, writer);
		writer.close();
	}

	/**
	 * Generate the {@linkplain ModuleDescriptor module} that represents the
	 * bootstrap package.
	 *
	 * @param versions
	 *        The supported versions.
	 * @throws IOException
	 *         If the source module could not be written.
	 */
	private void generateRepresentativeModule (
			final @NotNull List<String> versions)
		throws IOException
	{
		final File fileName = moduleFileName(representativeModuleName);
		assert fileName.getPath().endsWith(".avail");
		final PrintWriter writer = new PrintWriter(fileName, "UTF-8");
		generateRepresentativeModulePreamble(versions, writer);
		writer.close();
	}

	/**
	 * Generate the target Avail source {@linkplain ModuleDescriptor modules}.
	 *
	 * @param versions
	 *        The supported versions.
	 * @throws IOException
	 *         If any of the source modules could not be written.
	 */
	public void generate (final @NotNull List<String> versions)
		throws IOException
	{
		final File languagePath = new File(String.format(
			"src/%s/%s",
			generatedPackageName.replace('.', '/'),
			locale.getLanguage()));
		languagePath.mkdir();
		final File packageName = new File(String.format(
			"src/%s/%s/%s.avail",
			generatedPackageName.replace('.', '/'),
			locale.getLanguage(),
			preamble.getString(representativeModuleName.name())));
		packageName.mkdir();
		generateOriginModule(versions);
		generateSpecialObjectsModule(versions);
		generatePrimitiveModule(null, versions);
		generatePrimitiveModule(false, versions);
		generatePrimitiveModule(true, versions);
		generateRepresentativeModule(versions);
	}

	/**
	 * Construct a new {@link BootstrapGenerator}.
	 *
	 * @param locale The target {@linkplain Locale locale}.
	 */
	public BootstrapGenerator (final @NotNull Locale locale)
	{
		this.locale = locale;
		final UTF8ResourceBundleControl control =
			new UTF8ResourceBundleControl();
		this.preamble = ResourceBundle.getBundle(
			preambleBaseName,
			locale,
			BootstrapGenerator.class.getClassLoader(),
			control);
		this.specialObjectBundle = ResourceBundle.getBundle(
			specialObjectsBaseName,
			locale,
			BootstrapGenerator.class.getClassLoader(),
			control);
		this.primitiveBundle = ResourceBundle.getBundle(
			primitivesBaseName,
			locale,
			BootstrapGenerator.class.getClassLoader(),
			control);

		// Map localized names to the special objects.
		for (int i = 0; i < specialObjects.size(); i++)
		{
			final AvailObject specialObject = specialObjects.get(i);
			if (specialObject != null)
			{
				final String key = specialObjectKey(i);
				final String value = specialObjectBundle.getString(key);
				if (value != null && !value.isEmpty())
				{
					specialObjectsByName.put(value, specialObject);
					namesBySpecialObject.put(specialObject, value);
				}
			}
		}

		// Map localized names to the primitives.
		for (final Primitive primitive : Primitive.values())
		{
			if (!primitive.hasFlag(Flag.Private))
			{
				final String value = primitiveBundle.getString(primitive.name());
				if (value != null && !value.isEmpty())
				{
					Set<Primitive> set = primitiveNameMap.get(value);
					if (set == null)
					{
						set = EnumSet.noneOf(Primitive.class);
						primitiveNameMap.put(value, set);
					}
					set.add(primitive);
				}
			}
		}
	}

	/**
	 * Generate all bootstrap {@linkplain ModuleDescriptor modules}.
	 *
	 * @param args
	 *        The command-line arguments. The first argument is a
	 *        comma-separated list of language codes that broadly specify the
	 *        {@linkplain Locale locales} for which modules should be generated.
	 *        The second argument is a comma-separated list of Avail system
	 *        versions.
	 * @throws Exception
	 *         If anything should go wrong.
	 */
	public static void main (final @NotNull String[] args)
		throws Exception
	{
		final List<String> languages = new ArrayList<String>();
		final List<String> versions = new ArrayList<String>();
		if (args.length < 1)
		{
			languages.add("en");
		}
		else
		{
			final StringTokenizer tokenizer = new StringTokenizer(args[0], ",");
			while (tokenizer.hasMoreTokens())
			{
				languages.add(tokenizer.nextToken());
			}
		}
		if (args.length < 2)
		{
			versions.add("dev");
		}
		else
		{
			final StringTokenizer tokenizer = new StringTokenizer(args[1], ",");
			while (tokenizer.hasMoreTokens())
			{
				versions.add(tokenizer.nextToken());
			}
		}

		for (final String language : languages)
		{
			final BootstrapGenerator generator =
				new BootstrapGenerator(new Locale(language));
			generator.generate(versions);
		}
	}
}
