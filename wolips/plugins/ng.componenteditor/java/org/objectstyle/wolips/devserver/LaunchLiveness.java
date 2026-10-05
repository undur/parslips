package org.objectstyle.wolips.devserver;

import java.util.function.IntPredicate;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IThread;

/**
 * Whether a launched app is alive, progressing, or neither — decided fast, so no caller waits
 * out a timeout on an app that's already over.
 *
 * <p><b>Dead.</b> {@link ILaunch#isTerminated()} is true only once the process AND its debug
 * target have both ended. A JVM that dies while its debug target lingers (seen after hot-swap
 * trouble and port fights: {@code /console} read {@code state: running  exit: 1}) left the
 * launch "running" for good — {@code /launch} waited its whole timeout on a corpse, and
 * {@code /status} reported it up. Here a launch is over when its process is.
 *
 * <p><b>Stalled.</b> An app that neither answers its port nor writes anything for
 * {@link #STALL_MILLIS} isn't starting: apps here come up in a second or two, and one doing
 * slow work at startup (migrations, a big model) logs while it does it. A stall is reported,
 * with what the app last said and its threads' state, rather than waited out.
 */
final class LaunchLiveness {

	/** Silence (no port, no output) after which a starting app counts as stalled. */
	static final long STALL_MILLIS = 15_000;

	/** How far past a held port to look for a free one. */
	private static final int PORT_SEARCH_RANGE = 50;

	private LaunchLiveness() {
	}

	/** Whether the launch is over: terminated, or its process has ended (debug target or not). */
	static boolean isOver(ILaunch launch) {
		if (launch.isTerminated()) {
			return true;
		}
		final IProcess[] processes = launch.getProcesses();
		if (processes.length == 0) {
			return false; // not started yet
		}
		for (final IProcess process : processes) {
			if (!process.isTerminated()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Ends what lingers of a launch whose process is gone (its debug target), so the Debug view
	 * and every later check agree it's over. Quietly: there is nothing left to fail.
	 */
	static void settle(ILaunch launch) {
		if (!launch.isTerminated() && isOver(launch) && launch.canTerminate()) {
			try {
				launch.terminate();
			}
			catch (Exception e) {
				// The process is already gone; a debug target that won't terminate changes nothing.
			}
		}
	}

	/**
	 * Whether a starting app has been silent long enough to count as stalled: no output since
	 * {@code lastOutputMillis} (or since it started, when it has said nothing).
	 */
	static boolean isStalled(long startedMillis, long lastOutputMillis, long nowMillis) {
		return nowMillis - Math.max(startedMillis, lastOutputMillis) >= STALL_MILLIS;
	}

	/** {@code [suspended, total]} threads across the launch's debug targets. */
	static int[] threadCounts(ILaunch launch) {
		int suspended = 0;
		int total = 0;
		for (final IDebugTarget target : launch.getDebugTargets()) {
			try {
				if (target.isTerminated() || target.isDisconnected()) {
					continue;
				}
				for (final IThread thread : target.getThreads()) {
					total++;
					if (thread.isSuspended()) {
						suspended++;
					}
				}
			}
			catch (Exception e) {
				// A target that can't list its threads contributes nothing.
			}
		}
		return new int[] { suspended, total };
	}

	/** The first port after {@code port} that isn't held, or -1 when none is within range. */
	static int nextFreePort(int port, IntPredicate held) {
		for (int candidate = port + 1; candidate <= port + PORT_SEARCH_RANGE && candidate < 65536; candidate++) {
			if (!held.test(candidate)) {
				return candidate;
			}
		}
		return -1;
	}
}
