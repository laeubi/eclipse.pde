package org.eclipse.pde.launching;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamsProxy;

/**
 * @since 3.12
 */
public class RemoteFrameworkProcess implements IProcess {

	private ILaunch launch;

	public RemoteFrameworkProcess(ILaunch launch) {
		this.launch = launch;
		launch.addProcess(this);
		fireEvent(new DebugEvent(this, DebugEvent.CREATE));
	}

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		System.out.println("RemoteFrameworkLaunch.getAdapter() " + adapter);
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean canTerminate() {
		return false;
	}

	@Override
	public boolean isTerminated() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void terminate() throws DebugException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getLabel() {
		return "OSGi Framework";
	}

	@Override
	public ILaunch getLaunch() {
		// TODO Auto-generated method stub
		return launch;
	}

	@Override
	public IStreamsProxy getStreamsProxy() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setAttribute(String key, String value) {
		// TODO Auto-generated method stub

	}

	@Override
	public String getAttribute(String key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getExitValue() throws DebugException {
		// TODO Auto-generated method stub
		return 0;
	}

	private void fireEvent(DebugEvent event) {
		DebugPlugin manager = DebugPlugin.getDefault();
		if (manager != null) {
			manager.fireDebugEventSet(new DebugEvent[] {event});
		}
	}

}
