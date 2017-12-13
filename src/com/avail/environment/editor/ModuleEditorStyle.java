/*
 * ModuleViewerStyle.java
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

package com.avail.environment.editor;

import com.avail.compiler.ExpectedToken;
import com.avail.environment.AvailWorkbench;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * {@code ModuleEditorStyle} enumerates the allowed styles for a {@link
 * ModuleEditor}.
 *
 * @author Todd L Smith &lt;tsmith@safetyweb.org&gt;
 */
public enum ModuleEditorStyle
{
	/** This is the general style for arbitrary text. */
	GENERAL,

	/** This is the style for module header keywords. */
	HEADER_KEYWORD,

	/** This is the style for module names. */
	MODULE_NAME (ExpectedToken.MODULE),

	/** This is the style for pragmas. */
	PRAGMA (ExpectedToken.PRAGMA),

	/** This is the style for versions. */
	VERSION (ExpectedToken.VERSIONS),

	/** This is the style for privately imported names. */
	PRIVATE_NAME_IMPORT
	{
		@Override
		public final boolean denotesName ()
		{
			return true;
		}
	},

	/** This is the style for privately imported modules. */
	PRIVATE_MODULE_IMPORT (
		ExpectedToken.USES,
		ModuleEditorStyle.PRIVATE_NAME_IMPORT),

	/** This is the style for publicly imported names. */
	PUBLIC_NAME_IMPORT
	{
		@Override
		public final boolean denotesName ()
		{
			return true;
		}
	},

	/** This is the style for publicly imported modules. */
	PUBLIC_MODULE_IMPORT (
		ExpectedToken.EXTENDS,
		ModuleEditorStyle.PUBLIC_NAME_IMPORT),

	/** This is the style for new names. */
	NEW_NAME (ExpectedToken.NAMES)
	{
		@Override
		public final boolean denotesName ()
		{
			return true;
		}
	},

	/** This is the style for entry point declarations. */
	ENTRY_POINT (ExpectedToken.ENTRIES)
	{
		@Override
		public final boolean denotesName ()
		{
			return true;
		}
	},

	/** This is the style for numeric literals. */
	NUMBER,

	/** This is the style for string literals. */
	STRING,

	/** This is the style for (currently unavailable) generalized literals. */
	LITERAL,

	/** This is the style for Stacks comments. */
	STACKS_COMMENT,

	/** This is the style for Stacks keywords. */
	STACKS_KEYWORD,

	/** This is the style for a busted Stacks comment. */
	MALFORMED_STACKS_COMMENT,

	/** This is the style for general comments. */
	COMMENT;

	/**
	 * The {@linkplain ModuleEditorStyle styles} associated with module header
	 * sections, as a {@linkplain Map map} from {@link ExpectedToken}s to
	 * {@link ModuleEditorStyle}s.
	 */
	private static final Map<ExpectedToken, ModuleEditorStyle>
		stylesByExpectedToken = new EnumMap<>(ExpectedToken.class);

	static
	{
		for (final ModuleEditorStyle style : values())
		{
			final ExpectedToken token = style.expectedToken;
			if (token != null)
			{
				stylesByExpectedToken.put(token, style);
			}
		}
	}

	/**
	 * Answer the {@code ModuleEditorStyle} associated with the module header
	 * section initiated by the specified {@linkplain ExpectedToken token}.
	 *
	 * @param token
	 *        An {@code ExpectedToken}.
	 * @return The appropriate {@code ModuleEditorStyle}, or {@code null} if
	 *         none is appropriate.
	 */
	public static @Nullable ModuleEditorStyle styleFor (
		final ExpectedToken token)
	{
		return stylesByExpectedToken.get(token);
	}

	/**
	 * The CSS style class for the style.
	 */
	public final String styleClass;

	/**
	 * The {@link ExpectedToken} that governs the section associated with this
	 * {@link ModuleEditorStyle}, if any.
	 */
	public final @Nullable ExpectedToken expectedToken;

	/**
	 * The {@link ModuleEditorStyle} that governs the supersection associated
	 * with this {@link ModuleEditorStyle}.
	 */
	public ModuleEditorStyle supersectionStyleClass;

	/**
	 * The {@link ModuleEditorStyle} that governs the subsection associated
	 * with this {@link ModuleEditorStyle}.
	 */
	public final ModuleEditorStyle subsectionStyleClass;

	/**
	 * Does the {@code ModuleEditorStyle} correspond to either an imported name
	 * or a new name?
	 *
	 * @return {@code true} if the receiver denotes a name, {@code false}
	 *         otherwise.
	 */
	public boolean denotesName ()
	{
		return false;
	}

	/**
	 * The style sheet.
	 */
	@SuppressWarnings("StringConcatenationMissingWhitespace")
	public static final String editorStyleSheet =
		ModuleEditor.class.getResource(
			AvailWorkbench.resourcePrefix +
				"module_editor_styles.css").toExternalForm();

	/**
	 * Construct a {@code ModuleEditorStyle}.
	 */
	ModuleEditorStyle ()
	{
		this.styleClass = this.name().replace('_', '-').toLowerCase();
		this.expectedToken = null;
		this.supersectionStyleClass = this;
		this.subsectionStyleClass = this;
	}

	/**
	 * Construct a {@code ModuleEditorStyle} that is associated with the
	 * section governed by the specified {@link ExpectedToken}.
	 *
	 * @param expectedToken
	 *        The appropriate {@code ExpectedToken}.
	 */
	ModuleEditorStyle (final @Nullable ExpectedToken expectedToken)
	{
		this.styleClass = this.name().replace('_', '-').toLowerCase();
		this.expectedToken = expectedToken;
		this.supersectionStyleClass = this;
		this.subsectionStyleClass = this;
	}

	/**
	 * Construct a {@code ModuleEditorStyle} that is associated with the
	 * section governed by the specified {@link ExpectedToken} and uses the
	 * specified {@code ModuleEditorStyle} for subsections.
	 *
	 * @param expectedToken
	 *        The appropriate {@code ExpectedToken}.
	 * @param subsectionStyleClass
	 *        The {@code ModuleEditorStyle} for subsections.
	 */
	ModuleEditorStyle (
		final @Nullable ExpectedToken expectedToken,
		final ModuleEditorStyle subsectionStyleClass)
	{
		this.styleClass = this.name().replace('_', '-').toLowerCase();
		this.expectedToken = expectedToken;
		this.supersectionStyleClass = this;
		this.subsectionStyleClass = subsectionStyleClass;
		subsectionStyleClass.supersectionStyleClass = this;
	}
}
