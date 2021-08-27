/**
 * 
 */
package muon.app.ssh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import lombok.extern.slf4j.Slf4j;
import muon.app.ui.components.session.SessionInfo;
import net.schmizz.sshj.connection.channel.direct.PTYMode;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;

/**
 * @author subhro
 *
 */
@Slf4j
public class RemoteSessionInstance {
	private SshClient2 ssh;
	private SshFileSystem sshFs;
	private AtomicBoolean closed = new AtomicBoolean(false);

	public RemoteSessionInstance(SessionInfo info, InputBlocker inputBlocker,
			CachedCredentialProvider cachedCredentialProvider) {
		this.ssh = new SshClient2(info, inputBlocker, cachedCredentialProvider);
		this.sshFs = new SshFileSystem(this.ssh);
	}

	public int exec(String command, final ToIntFunction<Command> callback, boolean pty) throws Exception {
		synchronized (this.ssh) {
			if (this.closed.get()) {
				throw new OperationCancelledException();
			}
			try {
				if (!ssh.isConnected()) {
					ssh.connect();
				}
				try (Session session = ssh.openSession()) {
					session.setAutoExpand(true);
					if (pty) {
						session.allocatePTY("vt100", 80, 24, 0, 0, Collections.<PTYMode, Integer>emptyMap());
					}
					try (final Command cmd = session.exec(command)) {
						return callback.applyAsInt(cmd);
					}
				}

			} catch (Exception e) {
				//e.printStackTrace();
				log.error("executing command failed with ", e);
			}
			return 1;
		}
	}

	public int exec(String command, AtomicBoolean stopFlag) throws Exception {
		return exec(command, stopFlag, null, null);
	}

	public int exec(String command, AtomicBoolean stopFlag, StringBuilder output) throws Exception {
		return exec(command, stopFlag, output, null);
	}

	public int exec(String command, AtomicBoolean stopFlag, StringBuilder output, StringBuilder error)
			throws Exception {
		ByteArrayOutputStream bout = output == null ? null : new ByteArrayOutputStream();
		ByteArrayOutputStream berr = error == null ? null : new ByteArrayOutputStream();
		int ret = execBin(command, stopFlag, bout, berr);
		if (output != null) {
			output.append(bout.toString(StandardCharsets.UTF_8));
		}
		if (error != null) {
			error.append(berr.toString(StandardCharsets.UTF_8));
		}
		return ret;
	}

	public int execBin(String command, AtomicBoolean stopFlag, OutputStream bout, OutputStream berr) throws Exception {
		synchronized (this.ssh) {
			if (this.closed.get()) {
				throw new OperationCancelledException();
			}
			log.info("current thread : {} and command : {}", Thread.currentThread().getName(), command);
			if (stopFlag.get()) {
				return -1;
			}
			try {
				if (!ssh.isConnected()) {
					ssh.connect();
				}
				try (Session session = ssh.openSession()) {
					session.setAutoExpand(true);
//				session.allocatePTY(App.getGlobalSettings().getTerminalType(),
//						80, 24, 0, 0, Collections.<PTYMode, Integer>emptyMap());
					try (final Command cmd = session.exec(command)) {
						//System.out.println("Command and Session started");
						log.info("Command and Session started");

						writeCommandToOutputAndError(cmd, stopFlag, bout, berr);

						log.info(cmd.isOpen() + " " + cmd.isEOF() + " " + cmd.getExitStatus());
						log.info("Command and Session closed");

						cmd.close();
						return cmd.getExitStatus();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return 1;
		}
	}


	public int execBin(String command, AtomicBoolean stopFlag, OutputStream bout, OutputStream berr, boolean elavated)
			throws Exception {
		if (!elavated) {
			return execBin(command, stopFlag, bout, berr);
		}
		final String prompt = "hello-114514-1919810:";
		final String fullCommand = "sudo -S -p '" + prompt + "' " + command;

		synchronized (this.ssh) {
			if (this.closed.get()) {
				throw new OperationCancelledException();
			}
			try {
				if (!ssh.isConnected()) {
					ssh.connect();
				}
				try (Session session = ssh.openSession()) {
					session.setAutoExpand(true);

					try (final Command cmd = session.exec(fullCommand)) {
						log.info("Command and Session started");

						char[] buf = new char[8196];

						writeCommandToOutputAndError(cmd, stopFlag, bout, berr);

						log.info(cmd.isOpen() + " " + cmd.isEOF() + " " + cmd.getExitStatus());
						log.info("Command and Session closed");

						cmd.close();
						return cmd.getExitStatus();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return -1;
	}

	private void writeCommandToOutputAndError(final Command command,
											  final AtomicBoolean stop,
											  final OutputStream bout,
											  final OutputStream berr) throws IOException {
		InputStream in = command.getInputStream();
		InputStream err = command.getErrorStream();

		byte[] b = new byte[8192];

		do {
			if (stop.get()) {
				log.info("stop flag here");
				break;
			}

			if (in.available() > 0) {
				int m = in.available();
				while (m > 0) {
					int x = in.read(b, 0, Math.min(m, b.length));
					if (x == -1) {
						break;
					}
					m -= x;
					if (bout != null) {
						bout.write(b, 0, x);
					}

				}
			}

			if (err.available() > 0) {
				int m = err.available();
				while (m > 0) {
					int x = err.read(b, 0, Math.min(m, b.length));
					if (x == -1) {
						break;
					}
					m -= x;
					if (berr != null) {
						berr.write(b, 0, x);
					}

				}
			}

		} while (command.isOpen());
	}

	/**
	 * 
	 */
	public void close() {
		try {
			this.closed.set(true);
			try {
				this.sshFs.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			this.ssh.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @return the sshFs
	 */
	public SshFileSystem getSshFs() {
		return sshFs;

	}

	public boolean isSessionClosed() {
		return closed.get();
	}

}
