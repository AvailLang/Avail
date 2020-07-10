/*
 * Resources.kt
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
package com.avail.tools.bootstrap

import com.avail.exceptions.AvailErrorCode
import com.avail.interpreter.Primitive
import java.text.MessageFormat
import java.util.*
import java.util.regex.Matcher

/**
 * `Resources` centralizes [resource bundle][ResourceBundle] paths and keys.
 *
 * @author Todd L Smith &lt;todd@availlang.org&gt;
 */
internal object Resources
{
	/**
	 * The path to the source directory, relative to the current working
	 * directory, which should be the parent directory of the source directory.
	 */
	const val sourceBaseName = "src/main/kotlin"

	/**
	 * The base name of the [resource bundle][ResourceBundle] that contains the
	 * preamble.
	 */
	@JvmField
	val preambleBaseName = "${Resources::class.java.getPackage().name}.Preamble"

	/**
	 * The name of the package that should contain the generated output.
	 */
	@JvmField
	val generatedPackageName = 
		"${Resources::class.java.getPackage().name}.generated"

	/**
	 * The base name of the [resource bundle][ResourceBundle] that contains the
	 * Avail names of the special objects.
	 */
	@JvmField
	val specialObjectsBaseName = "$generatedPackageName.SpecialObjectNames"

	/**
	 * The base name of the target [resource bundle][ResourceBundle] that
	 * contains the Avail names of the primitives.
	 */
	@JvmField
	val primitivesBaseName = "$generatedPackageName.PrimitiveNames"

	/**
	 * The base name of the target [resource bundle][ResourceBundle] that
	 * contains the Avail names of the [primitive error codes][AvailErrorCode].
	 */
	@JvmField
	val errorCodesBaseName = "$generatedPackageName.ErrorCodeNames"

	/**
	 * Answer the local name of the specified [resource bundle][ResourceBundle]
	 * base name.
	 *
	 * @param bundleName
	 *   A resource bundle base name.
	 * @return
	 *   The local name, e.g. the name following the last period (.).
	 */
	@JvmStatic
	fun localName(bundleName: String) = 
		bundleName.substring(bundleName.lastIndexOf('.') + 1)

	/**
	 * Answer the argument, but embedded in double quotes (").
	 *
	 * @param string
	 *   A [string][String].
	 * @return
	 *   The argument embedded in double quotes (").
	 */
	@JvmStatic
	fun stringify(string: String) = """"$string""""

	/**
	 * Answer the key for the special object name given by `index`.
	 *
	 * @param index
	 *   The special object index.
	 * @return
	 *   A key that may be used to access the Avail name of the special object
	 *   in the appropriate [resource bundle][ResourceBundle].
	 */
	@JvmStatic
	fun specialObjectKey(index: Int) = "specialObject$index"

	/**
	 * Answer the key for the specified special object's comment.
	 *
	 * @param index
	 *   The special object index.
	 * @return
	 *   A key that may be used to access the special object's comment in the
	 *   appropriate [resource bundle][ResourceBundle].
	 */
	@JvmStatic
	fun specialObjectCommentKey(index: Int) = 
		"${specialObjectKey(index)}_comment"

	/**
	 * Answer the key for the specified special object's preferred Stacks
	 * type name.
	 *
	 * @param index
	 *   The special object index.
	 * @return
	 *   A key that may be used to access the special object's preferred Stacks
	 *   `@type` name in the appropriate [resource bundle][ResourceBundle].
	 */
	@JvmStatic
	fun specialObjectTypeKey(index: Int) = "${specialObjectKey(index)}_type"

	/**
	 * Answer the key for the `index`-th parameter name of the specified
	 * [primitive][Primitive].
	 *
	 * @param primitive
	 *   A primitive.
	 * @param index
	 *   The parameter ordinal.
	 * @return
	 *   A key that may be used to access the name of the primitive's `index`-th
	 *   parameter in the appropriate [resource bundle][ResourceBundle].
	 */
	@JvmStatic
	fun primitiveParameterNameKey(primitive: Primitive, index: Int) = 
		"${primitive.javaClass.simpleName}_$index"

	/**
	 * Answer the key for the specified [primitive][Primitive]'s comment.
	 *
	 * @param primitive
	 *   A primitive.
	 * @return
	 *   A key that may be used to access the primitive method's comment in the
	 *   appropriate [resource bundle][ResourceBundle].
	 */
	@JvmStatic
	fun primitiveCommentKey(primitive: Primitive) = 
		"${primitive.javaClass.simpleName}_comment"

	/**
	 * Answer the key for the specified [primitive error code][AvailErrorCode].
	 *
	 * @param code
	 *   A primitive error code.
	 * @return
	 *   A key that may be used to access the Avail name of the primitive error
	 *   code in the appropriate [resource bundle][ResourceBundle].
	 */
	@JvmStatic
	fun errorCodeKey(code: AvailErrorCode) = "errorCode${code.nativeCode()}"

	/**
	 * Answer the key for the specified [primitive error code][AvailErrorCode]'s
	 * comment.
	 *
	 * @param code
	 *   A primitive error code.
	 * @return
	 *   A key that may be used to access the primitive error code's comment in
	 *   the appropriate [resource bundle][ResourceBundle].
	 */
	@JvmStatic
	fun errorCodeCommentKey(code: AvailErrorCode) = "${errorCodeKey(code)}_comment"

	/**
	 * Escape the string to survive multiple passes through a [MessageFormat].
	 *
	 * @param propertyValue
	 *   A property value that will be written to a properties file.
	 * @return
	 *   An appropriately escaped property value.
	 */
	@JvmStatic
	fun escape(propertyValue: String): String
	{
		var newValue = propertyValue.replace("\n", "\\n\\\n")
		if (newValue.indexOf('\n') != newValue.lastIndexOf('\n'))
		{
			newValue = "\\\n$newValue"
		}
		if (newValue.endsWith("\\n\\\n"))
		{
			newValue = newValue.substring(0, newValue.length - 2)
		}
		newValue = newValue.replace("\n ", "\n\\ ")
		newValue = newValue.replace(
			"\\\\(?![n\\n ])".toRegex(), Matcher.quoteReplacement("\\\\"))
		return newValue
	}

	@Suppress("EnumEntryName")
	enum class Key
	{
		propertiesCopyright,
		generatedPropertiesNotice,
		availCopyright,
		specialObjectCommentTemplate,
		specialObjectCommentTypeTemplate,
		specialObjectCommentValueTemplate,
		methodCommentTemplate,
		methodCommentParameterTemplate,
		methodCommentReturnsTemplate,
		methodCommentRaisesTemplate,
		generatedModuleNotice,
		originModuleName,
		originModuleHeader,
		specialObjectsModuleName,
		primitivesModuleName,
		infalliblePrimitivesModuleName,
		falliblePrimitivesModuleName,
		errorCodesModuleName,
		generalModuleHeader,
		representativeModuleName,
		bootstrapDefiningMethod,
		bootstrapSpecialObject,
		bootstrapDefineSpecialObjectMacro,
		bootstrapMacroNames,
		bootstrapMacros,
		definingSpecialObjectUse,
		definingMethodUse,
		specialObjectUse,
		parameterPrefix,
		primitiveKeyword,
		primitiveFailureMethod,
		primitiveFailureMethodUse,
		primitiveFailureVariableName,
		primitiveFailureFunctionName,
		primitiveFailureFunctionSetterMethod,
		primitiveFailureFunctionGetterMethod,
		invokePrimitiveFailureFunctionMethod,
		invokePrimitiveFailureFunctionMethodUse,
		primitiveSemanticRestriction,
		primitiveSemanticRestrictionUse
	}
}
