/**
 * ModuleViewer.java
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

package com.avail.environment.viewer;

import com.avail.builder.ResolvedModuleName;
import com.avail.compiler.ExpectedToken;
import com.avail.compiler.scanning.AvailScanner;
import com.avail.compiler.scanning.AvailScanner.BasicCommentPosition;
import com.avail.compiler.scanning.AvailScannerException;
import com.avail.compiler.scanning.AvailScannerResult;
import com.avail.descriptor.A_String;
import com.avail.descriptor.A_Token;
import com.avail.descriptor.AvailObject;
import com.avail.descriptor.TokenDescriptor.TokenType;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.actions.BuildAction;
import com.avail.environment.tasks.EditModuleTask;

import javafx.beans.NamedArg;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.RichTextChange;
import org.fxmisc.richtext.model.StyledText;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.avail.environment.viewer.ModuleViewerStyle.*;

/**
 * A {@code ModuleViewer} is a {@link Scene} used to open a source module for
 * viewing and editing.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class ModuleViewer
extends Scene
{
	/**
	 * An {@link AvailScannerResult} from scanning the file.
	 */
	private AvailScannerResult scannerResult;

	/**
	 * A reference to the workbench.
	 */
	private final AvailWorkbench workbench;

	/**
	 * The single-permit {@link Semaphore} used as the gateway for allowing the
	 * {@link #scanAndStyle()} of the document on a change.
	 *
	 * <p>
	 * If subsequent updates come in, we don't care to process style changes for
	 * all of them, we just want to try our best to only do the last one.
	 * </p>
	 */
	private final Semaphore semaphore = new Semaphore(1);

	/**
	 * The {@link Consumer} run in the event of a {@link RichTextChange}.
	 */
	private final Consumer<RichTextChange<
			Collection<String>,
			StyledText<Collection<String>>,
			Collection<String>>>
	changeTracker = change ->
	{
		//We only want to scan one at a time, but we don't care about
		//losing opportunities to scan if a lot of typing is happening;
		//we just want to do our best to scan on the last change.
		if (semaphore.tryAcquire())
		{
			scanAndStyle();
			semaphore.release();
		}
	};

	/**
	 * The {@link ResolvedModuleName} for the module being viewed.
	 */
	private final ResolvedModuleName resolvedModuleName;

	/**
	 * The {@link CodeArea} where code is displayed.
	 */
	private final CodeArea codeArea;

	/**
	 * Enable the {@link #changeTracker} on {@link RichTextChange text changes}.
	 */
	private void enableScanOnChange ()
	{
		codeArea.richChanges()
			.filter(ch -> !ch.getInserted().equals(ch.getRemoved()))
			.subscribe(changeTracker);
	}

	/**
	 * Save the module then initiate a build.
	 */
	private void saveAndBuild ()
	{
		writeFile();
		BuildAction.build(resolvedModuleName, workbench);
	}

	/**
	 * Close this {@link ModuleViewer}, throwing away any changes, and open a
	 * fresh copy from the file system.
	 */
	private void reloadModule ()
	{
		final JFrame frame = workbench.openedSourceModules
			.remove(resolvedModuleName);
		if (frame != null)
		{
			frame.setVisible(false);
			frame.dispose();
			new EditModuleTask(workbench, resolvedModuleName)
				.execute();
		}
	}

	/**
	 * Answer a new {@link ModuleViewer}.
	 *
	 * @param module
	 *        The {@link ResolvedModuleName} that represents the module to open.
	 * @return A {@code ModuleViewer}.
	 */
	public static ModuleViewer moduleViewer (
		final ResolvedModuleName module,
		final AvailWorkbench workbench,
		final Rectangle dimensions)
	{
		final CodeArea codeArea = new CodeArea();
		codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
		codeArea.getStyle();

		final VirtualizedScrollPane vsp = new VirtualizedScrollPane<>(codeArea);
		VBox.setVgrow(vsp, Priority.ALWAYS);
		MenuBar menuBar = new MenuBar();
		VBox vbox = new VBox(menuBar, vsp);
		ModuleViewer viewer = new ModuleViewer(
			vbox,
			dimensions.width,
			dimensions.height,
			workbench,
			module,
			codeArea);

		viewer.enableScanOnChange();

		// File menu - new, save, exit
		Menu fileMenu = new Menu("File");
//		MenuItem newMenuItem = new MenuItem("New");
		MenuItem reloadhMenuItem = new MenuItem("Reload");
		reloadhMenuItem.setOnAction(actionEvent -> viewer.reloadModule());
		MenuItem saveMenuItem = new MenuItem("Save");
		saveMenuItem.setOnAction(actionEvent -> viewer.writeFile());

		MenuItem saveAndBuildMenuItem = new MenuItem("Save & Build");
		saveAndBuildMenuItem.setOnAction(actionEvent -> viewer.saveAndBuild());

		fileMenu.getItems().addAll(
			saveMenuItem, saveAndBuildMenuItem, reloadhMenuItem);
		menuBar.getMenus().addAll(fileMenu);

		viewer.getStylesheets().add(ModuleViewer.class.getResource(
			"/workbench/module_viewer_styles.css").toExternalForm());
		viewer.readFile();

		return viewer;
	}

	/**
	 * Write the contents of the {@link ModuleViewer} to disk.
	 *
	 * <p>
	 * This operation blocks until it can acquire the {@link #semaphore}. This
	 * prevents styling during save.
	 * </p>
	 */
	private void writeFile ()
	{
		try
		{
			semaphore.acquireUninterruptibly();
			final List<String> input = new ArrayList<>();
			input.add(codeArea.getText());
			File file = resolvedModuleName.sourceReference();
			file.canWrite();
			Files.write(
				file.toPath(),
				input,
				StandardCharsets.UTF_8);
			semaphore.release();
		}
		catch (IOException e)
		{
			//Do not much
		}
	}

	/**
	 * Read the {@link #resolvedModuleName} {@linkplain File file} from disk
	 * and {@linkplain #scanAndStyle() style} it.
	 */
	private void readFile ()
	{
		File sourceFile = resolvedModuleName.sourceReference();
		try
		{
			StringBuilder sb = new StringBuilder();
			List<String> lines =
				Files.lines(sourceFile.toPath()).collect(Collectors.toList());
			codeArea.clear();

			int size = lines.size();

			for (int i = 0; i < size - 1; i++)
			{
				sb.append(lines.get(i));
				sb.append('\n');
			}

			sb.append(lines.get(size - 1));

			codeArea.replaceText(0, 0, sb.toString());
		}
		catch (IOException e)
		{
			System.err.println("Failed to read file");
		}
	}

	/**
	 * A {@linkplain Map map} from Avail header keywords to the associated
	 * {@link ExpectedToken}s.
	 */
	private static final @NotNull Map<A_String, ExpectedToken> headerKeywords;

	static
	{
		final ExpectedToken[] tokens =
		{
			ExpectedToken.MODULE,
			ExpectedToken.VERSIONS,
			ExpectedToken.PRAGMA,
			ExpectedToken.EXTENDS,
			ExpectedToken.USES,
			ExpectedToken.NAMES,
			ExpectedToken.ENTRIES,
			ExpectedToken.BODY,
			ExpectedToken.OPEN_PARENTHESIS,
			ExpectedToken.CLOSE_PARENTHESIS
		};
		final Map<A_String, ExpectedToken> map = new HashMap<>();
		for (final ExpectedToken t : tokens)
		{
			map.put(t.lexeme().makeShared(), t);
		}
		headerKeywords = map;
	}

	/**
	 * Style the specified {@linkplain A_Token} by giving it the provided
	 * style class.
	 *
	 * @param token
	 *        The token to style.
	 * @param style
	 *        The {@linkplain ModuleViewerStyle style}.
	 */
	private void styleToken (
		final A_Token token,
		final ModuleViewerStyle style)
	{
		codeArea.setStyleClass(
			token.start() - 1,
			token.start() + token.string().tupleSize() - 1,
			style.styleClass);
	}

	/**
	 * The {@linkplain Pattern pattern} that describes Stacks keywords.
	 */
	private static final @NotNull Pattern stacksKeywordPattern =
		Pattern.compile("@\\w+");

	/**
	 * Scan the text in the {@link #codeArea} and style the document
	 * appropriately.
	 */
	private void scanAndStyle ()
	{
		final String source = codeArea.getText();
		try
		{
			// Scan the source module to produce all tokens.
			scannerResult = AvailScanner.scanString(
				source.toString(),
				resolvedModuleName.localName(),
				false);

			// Tag each token of the header with the appropriate style.
			final List<A_Token> outputTokens = scannerResult.outputTokens();
			final int outputTokenCount = outputTokens.size();
			final Map<A_Token, ModuleViewerStyle> tokenStyles =	new HashMap<>();
			final Map<A_String, ModuleViewerStyle> nameStyles = new HashMap<>();
			ModuleViewerStyle activeStyle = null;
			for (int i = 0; i < outputTokenCount; i++)
			{
				final A_Token token = outputTokens.get(i);
				final A_String lexeme = token.string().makeShared();
				final ExpectedToken expected = headerKeywords.get(lexeme);
				if (expected != null && token.tokenType() == TokenType.KEYWORD)
				{
					// This is a header keyword, so set its own style and
					// establish a new active style appropriate for its section.
					tokenStyles.put(token, HEADER_KEYWORD);
					activeStyle = styleFor(expected);
					// There are guaranteed not to be any header keywords after
					// "Body", so stop looking!
					if (expected == ExpectedToken.BODY)
					{
						assert activeStyle == null;
						break;
					}
				}
				else if (expected == ExpectedToken.OPEN_PARENTHESIS)
				{
					// Activate the subsection style.
					activeStyle = activeStyle.subsectionStyleClass;
				}
				else if (expected == ExpectedToken.CLOSE_PARENTHESIS)
				{
					// Deactivate the subsection style.
					activeStyle = activeStyle.supersectionStyleClass;
				}
				else if (token.isLiteralToken())
				{
					// This is a literal token, so give it the active style for
					// its section.
					assert token.literal().isString();
					if (activeStyle != null)
					{
						tokenStyles.put(token, activeStyle);
						if (activeStyle.denotesName())
						{
							// Since this is a name visible in the module
							// header, associate the active style with the
							// lexeme, not just the token. This will give the
							// lexeme the same appearance in the body as in the
							// header. There may be some false positives, but
							// relatively few.
							nameStyles.put(lexeme, activeStyle);
						}
					}
				}
			}

			// Apply the general style broadly.
			codeArea.setStyleClass(
				0,
				source.length(),
				GENERAL.styleClass);

			// Override the general style for each ordinary token.
			for (int i = 0; i < outputTokens.size(); i++)
			{
				final A_Token token = outputTokens.get(i);
				final ModuleViewerStyle tokenStyle = tokenStyles.get(token);
				if (tokenStyle != null)
				{
					styleToken(token, tokenStyle);
				}
				else
				{
					final ModuleViewerStyle nameStyle =
						nameStyles.get(token.string().makeShared());
					if (nameStyle != null)
					{
						styleToken(token, nameStyle);
					}
					else if (token.isLiteralToken())
					{
						AvailObject availObject = token.literal();
						styleToken(
							token,
							availObject.isExtendedInteger()
								? NUMBER
								: availObject.isString()
									? STRING
									: LITERAL);
					}
				}
			}

			// Override the general style for each Stacks comment token.
			final List<A_Token> commentTokens = scannerResult.commentTokens();
			for (int i = 0; i < commentTokens.size(); i++)
			{
				final A_Token token = commentTokens.get(i);
				styleToken(token, STACKS_COMMENT);
				final Matcher matcher = stacksKeywordPattern.matcher(
					token.string().asNativeString());
				while (matcher.find())
				{
					codeArea.setStyleClass(
						token.start() + matcher.start() - 1,
						token.start() + matcher.end() - 1,
						STACKS_KEYWORD.styleClass);
				}
				// TODO: [RAA] I had to disable this because the performance of
				// scanning Stacks comments was way too slow — multi-second
				// delays.
//				final StacksScanner stacksScanner = new StacksScanner(
//					token, resolvedModuleName.qualifiedName());
//				try
//				{
//					stacksScanner.scan();
//					styleToken(token, STACKS_COMMENT);
//					if (stacksScanner.sectionStartLocations.isEmpty())
//					{
//  					// This comment is busted — style it appropriately.
//  					styleToken(token, MALFORMED_STACKS_COMMENT);
//  					continue;
//					}
//				}
//				catch (final @NotNull StacksScannerException e)
//				{
//					// This comment is busted — style it appropriately.
//					styleToken(token, MALFORMED_STACKS_COMMENT);
//					continue;
//				}
//				for (final AbstractStacksToken t : stacksScanner.outputTokens)
//				{
//					if (t instanceof SectionKeywordStacksToken)
//					{
//						// TODO: [RAA] It appears that these tokens are using
//						// line number to record the position.
//						final int position =
//							t.lineNumber() - t.lexeme().length();
//						codeArea.setStyleClass(
//							position - 1,
//							t.lineNumber() - 1,
//							STACKS_KEYWORD.styleClass);
//					}
//					else if (t instanceof BracketedStacksToken)
//					{
//						final int position = t.position() - t.lexeme().length();
//						codeArea.setStyleClass(
//							position,
//							t.position() - 1,
//							STACKS_KEYWORD.styleClass);
//					}
//				}
			}

			// Override the general style for each ordinary comment.
			final List<BasicCommentPosition> positions =
				scannerResult.basicCommentPositions();
			for (int i = 0; i < positions.size(); i++)
			{
				final BasicCommentPosition position = positions.get(i);
				codeArea.setStyleClass(
					position.start(),
					position.start() + position.length(),
					COMMENT.styleClass);
			}
		}
		catch (final @NotNull AvailScannerException e)
		{
			scannerResult = null;
		}
	}

	/**
	 * Construct a {@link ModuleViewer}.
	 *
	 * @param root
	 *        The root node of the {@link Scene} graph.
	 * @param width
	 *        The width of the {@code Scene}.
	 * @param height
	 *        The height of the {@code Scene}.
	 */
	private ModuleViewer (
		@NamedArg("root") final Parent root,
		@NamedArg("width") final double width,
		@NamedArg("height") final double height,
		final AvailWorkbench workbench,
		final ResolvedModuleName resolvedModuleName,
		final CodeArea codeArea)
	{
		super(root, width, height);
		this.resolvedModuleName = resolvedModuleName;
		this.codeArea = codeArea;
		this.workbench = workbench;
	}
}
