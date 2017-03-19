/**
 * ReplaceTextTemplate.java
 * Copyright © 1993-2015, The Avail Foundation, LLC.
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

package com.avail.environment.editor;
import com.avail.environment.editor.fx.FilterDropDownDialog;
import javafx.scene.control.ChoiceDialog;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@code ReplaceTextTemplate} is a holder of replacement text for key
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class ReplaceTextTemplate
{
	/**
	 * The map with the options.
	 */
	private final @NotNull Map<String, String> templateMap = new HashMap();

	/**
	 * Answer the template string for the given key.
	 *
	 * @param template
	 *        The template key.
	 * @return A template.
	 */
	public String get(final @NotNull String template)
	{
		return templateMap.get(template);
	}

	/**
	 * The list of choices.
	 */
	public final @NotNull List<String> choiceList;

	/**
	 * Construct a {@link ReplaceTextTemplate}.
	 */
	public ReplaceTextTemplate ()
	{
		templateMap.put("top", "⊤");
		templateMap.put("bottom", "⊥");
		templateMap.put("o1", "①");
		templateMap.put("o2", "②");
		templateMap.put("o3", "③");
		templateMap.put("o4", "④");
		templateMap.put("o5", "⑤");
		templateMap.put("o6", "⑥");
		templateMap.put("o7", "⑦");
		templateMap.put("o8", "⑧");
		templateMap.put("o9", "⑨");
		templateMap.put("union", "∪");
		templateMap.put("uparrow", "↑");
		templateMap.put("yields", "⇒");
		templateMap.put("times", "×");
		templateMap.put("divide", "÷");
		templateMap.put("ceiling", "⎡$EXPR$⎤");
		templateMap.put("floor", "⎣$EXPR$⎦");
		templateMap.put("conjunction", "∧");
		templateMap.put("debugprint",
			"Print: format \"$EXPR$=“①”\n\" with $EXPR$;");
		templateMap.put("disjunction", "∨");
		templateMap.put("doublequestion", "⁇");
		templateMap.put("downarrow", "↓");
		templateMap.put("earlydebugprint",
			"Print: \"$EXPR$=\";\nPrint: “$EXPR$”;\nPrint:\"\n\";");
		templateMap.put("elementof", "∈");
		templateMap.put("emptyset", "∅");
		templateMap.put("enumerationtype", "{$VALUES$}ᵀ");
		templateMap.put("intersection", "∩");
		templateMap.put("leftarrow", "←");
		templateMap.put("rightarrow", "→");
		templateMap.put("subset", "⊆");
		templateMap.put("superset", "⊇");
		templateMap.put("leftguillemet","«");
		templateMap.put("rightguillemet","»");
		templateMap.put("doubledagger","‡");
		templateMap.put("lessthanequal", "≤");
		templateMap.put("greaterthanequal", "≥");
		templateMap.put("leftdoublesmartquote", "“");
		templateMap.put("rightdoublesmartquote", "”");
		templateMap.put("leftsinglesmartquote", "‘");
		templateMap.put("rightsinglesmartquote", "’");
		templateMap.put("sum", "∑");
		templateMap.put("product", "∏");
		templateMap.put("negation", "¬");
		templateMap.put("notequals", "≠");
		templateMap.put("inf", "∞");
		templateMap.put("...", "…");
		templateMap.put("section", "§");


		choiceList = new ArrayList<>(templateMap.keySet());
		Collections.sort(choiceList);
	}

	/**
	 * Answer a {@link ChoiceDialog} with the templates.
	 *
	 * @return A {@link ChoiceDialog}.
	 */
	public @NotNull FilterDropDownDialog<String> dialog ()
	{
		FilterDropDownDialog<String> dialog = new FilterDropDownDialog<>(
			"",
			choiceList,
			(typedText, itemToCompare) ->
				itemToCompare.toLowerCase().contains(typedText.toLowerCase())
					|| itemToCompare.equals(typedText));
		dialog.setTitle("Choose Template");
		dialog.setContentText(null);
		dialog.setHeaderText(null);
		return dialog;
	}
}
