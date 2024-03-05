package org.eclipse.pde.launching;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.osgi.framework.Bundle;
import org.osgi.framework.dto.BundleDTO;

/**
 * @since 3.12
 */
public class RemoteFrameworkDebugTarget implements IDebugTarget {

	private static final String MODEL_ID = "pde.osgi.remote.debug"; //$NON-NLS-1$
	private ILaunch launch;
	private IProcess process;
	private Map<Long, RemoteBundle> bundles = new ConcurrentHashMap<>();

	public RemoteFrameworkDebugTarget(ILaunch launch, IProcess process) {
		this.launch = launch;
		this.process = process;
		launch.addDebugTarget(this);
	}

	@Override
	public ILaunch getLaunch() {
		return launch;
	}

	@Override
	public String getModelIdentifier() {
		return MODEL_ID;
	}

	@Override
	public IDebugTarget getDebugTarget() {
		return this;
	}

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		System.out.println("RemoteFrameworkDebugTarget.getAdapter() " + adapter);
		return null;
	}

	@Override
	public boolean canTerminate() {
		return false;
	}

	@Override
	public boolean isTerminated() {
		return false;
	}

	@Override
	public void terminate() throws DebugException {

	}

	@Override
	public boolean canResume() {
		return false;
	}

	@Override
	public boolean canSuspend() {
		return false;
	}

	@Override
	public boolean isSuspended() {
		return false;
	}

	@Override
	public void resume() throws DebugException {

	}

	@Override
	public void suspend() throws DebugException {

	}

	@Override
	public void breakpointAdded(IBreakpoint breakpoint) {

	}

	@Override
	public void breakpointRemoved(IBreakpoint breakpoint, IMarkerDelta delta) {

	}

	@Override
	public void breakpointChanged(IBreakpoint breakpoint, IMarkerDelta delta) {

	}

	@Override
	public boolean canDisconnect() {
		return false;
	}

	@Override
	public void disconnect() throws DebugException {

	}

	@Override
	public boolean isDisconnected() {
		return false;
	}

	@Override
	public boolean supportsStorageRetrieval() {
		return false;
	}

	@Override
	public IMemoryBlock getMemoryBlock(long startAddress, long length) throws DebugException {
		return null;
	}

	@Override
	public IProcess getProcess() {
		return process;
	}

	@Override
	public IThread[] getThreads() throws DebugException {
		return bundles.values().stream().sorted(Comparator.comparingLong(RemoteBundle::getId)).toArray(IThread[]::new);
	}

	@Override
	public boolean hasThreads() throws DebugException {
		return true;
	}

	@Override
	public String getName() throws DebugException {
		return getProcess().getLabel();
	}

	@Override
	public boolean supportsBreakpoint(IBreakpoint breakpoint) {
		return false;
	}

	public void addBundle(BundleDTO bundleDTO) {
		RemoteBundle bundle = bundles.computeIfAbsent(bundleDTO.id, x -> new RemoteBundle(this, bundleDTO));
		bundle.update(bundleDTO);
	}

	private static void fireEvent(DebugEvent event) {
		DebugPlugin manager = DebugPlugin.getDefault();
		if (manager != null) {
			manager.fireDebugEventSet(new DebugEvent[] {event});
		}
	}

	private static final class RemoteBundle implements IThread {

		private RemoteFrameworkDebugTarget target;
		private BundleDTO bundleDTO;

		public RemoteBundle(RemoteFrameworkDebugTarget target, BundleDTO inital) {
			this.target = target;
			this.bundleDTO = inital;
			fireEvent(new DebugEvent(this, DebugEvent.CREATE));
		}

		public void update(BundleDTO bundleDTO) {
			if (Objects.equals(this.bundleDTO, bundleDTO)) {
				return;
			}
			this.bundleDTO = bundleDTO;
			fireEvent(new DebugEvent(this, DebugEvent.CHANGE));
		}

		@Override
		public String getModelIdentifier() {
			return MODEL_ID;
		}

		@Override
		public IDebugTarget getDebugTarget() {
			return target;
		}

		@Override
		public ILaunch getLaunch() {
			return target.getLaunch();
		}

		@Override
		public <T> T getAdapter(Class<T> adapter) {
			System.out.println("RemoteFrameworkDebugTarget.RemoteBundle.getAdapter() " + adapter);
			return null;
		}

		@Override
		public boolean canResume() {
			return false;
		}

		@Override
		public boolean canSuspend() {
			return false;
		}

		@Override
		public boolean isSuspended() {
			return false;
		}

		@Override
		public void resume() throws DebugException {

		}

		@Override
		public void suspend() throws DebugException {

		}

		@Override
		public boolean canStepInto() {
			return false;
		}

		@Override
		public boolean canStepOver() {
			return false;
		}

		@Override
		public boolean canStepReturn() {
			return false;
		}

		@Override
		public boolean isStepping() {
			return false;
		}

		@Override
		public void stepInto() throws DebugException {

		}

		@Override
		public void stepOver() throws DebugException {

		}

		@Override
		public void stepReturn() throws DebugException {

		}

		@Override
		public boolean canTerminate() {
			return false;
		}

		@Override
		public boolean isTerminated() {
			return false;
		}

		@Override
		public void terminate() throws DebugException {

		}

		@Override
		public IStackFrame[] getStackFrames() throws DebugException {
			return new IStackFrame[0];
		}

		@Override
		public boolean hasStackFrames() throws DebugException {
			return false;
		}

		@Override
		public int getPriority() throws DebugException {
			return 0;
		}

		@Override
		public IStackFrame getTopStackFrame() throws DebugException {
			return null;
		}

		@Override
		public String getName() throws DebugException {
			return String.format("[%d]%s %s %s", bundleDTO.id, getState(), bundleDTO.symbolicName, bundleDTO.version); //$NON-NLS-1$
		}

		private String getState() {
			switch (bundleDTO.state) {
				case Bundle.ACTIVE :
					return "[ACTIVE]"; //$NON-NLS-1$
				case Bundle.INSTALLED :
					return "[INSTALLED]"; //$NON-NLS-1$
				case Bundle.RESOLVED :
					return "[RESOLVED]"; //$NON-NLS-1$
				case Bundle.STARTING :
					return "[STARTING]"; //$NON-NLS-1$
				case Bundle.STOPPING :
					return "[STOPPING]"; //$NON-NLS-1$
				case Bundle.UNINSTALLED :
					return "[UNINSTALLED]"; //$NON-NLS-1$
				default :
					return String.valueOf(bundleDTO.state);
			}
		}

		@Override
		public IBreakpoint[] getBreakpoints() {
			return new IBreakpoint[0];
		}

		public long getId() {
			return bundleDTO.id;
		}

	}

}
