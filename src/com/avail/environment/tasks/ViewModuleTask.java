package com.avail.environment.tasks;
import com.avail.builder.ResolvedModuleName;
import com.avail.descriptor.ModuleDescriptor;
import com.avail.environment.AvailWorkbench;
import com.avail.environment.AvailWorkbench.AbstractWorkbenchTask;
import com.avail.environment.viewer.ModuleViewer;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/**
 * A {@code ViewModuleTask} is a {@link AbstractWorkbenchTask} used to open
 * a module in a new window.
 *
 * @author Rich Arriaga &lt;rich@availlang.org&gt;
 */
public class ViewModuleTask
extends AbstractWorkbenchTask
implements WindowListener
{
	@Override
	protected void executeTask () throws Exception
	{
		EventQueue.invokeLater(() ->
		{
			//JFXPanel must be created before scene due to initialization issues
			final JFXPanel fxPanel = new JFXPanel();
			JFrame frame = new JFrame(targetModuleName.localName());
			final ModuleViewer viewer =
				ModuleViewer.moduleViewer(targetModuleName(), workbench);
			fxPanel.setScene(viewer);
			//This must be called to circumvent a bug that won't be fixed
			//See https://bugs.openjdk.java.net/browse/JDK-8090517
			Platform.setImplicitExit(false);
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.setOpaque(true);

			frame.getContentPane().add(BorderLayout.CENTER, panel);
			frame.pack();
			frame.setLocationByPlatform(true);
			frame.setVisible(true);
			frame.setResizable(true);
			frame.add(fxPanel);
			frame.setSize(1000, 600);
			workbench.openedSourceModules.put(targetModuleName, frame);
			frame.addWindowListener(this);
		});
	}

	@Override
	protected void done ()
	{
		workbench.backgroundTask = null;
		reportDone();
		workbench.availBuilder.checkStableInvariants();
		workbench.setEnablements();
		workbench.setCursor(Cursor.getDefaultCursor());
	}

	@Override
	public void windowOpened (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowClosing (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowClosed (final WindowEvent e)
	{
		workbench.openedSourceModules.remove(targetModuleName);
	}

	@Override
	public void windowIconified (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowDeiconified (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowActivated (final WindowEvent e)
	{
		//Do Nothing
	}

	@Override
	public void windowDeactivated (final WindowEvent e)
	{
		//Do Nothing
	}

	/**
	 * Construct a new {@link ViewModuleTask}.
	 *
	 * @param workbench
	 *        The owning {@link AvailWorkbench}.
	 * @param targetModuleName
	 *        The resolved name of the target {@linkplain ModuleDescriptor
	 *        module} to unload, or null to unload all modules.
	 */
	public ViewModuleTask (
		final AvailWorkbench workbench,
		final @Nullable ResolvedModuleName targetModuleName)
	{
		super(workbench, targetModuleName);
	}
}
