package tardis.app.command;

import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

public abstract class Command {
	public final static String CMD_SETUP = "setup";
	public final static String CMD_START = "start";
	public final static String CMD_STOP = "stop";
	public final static String CMD_LEAVE = "leave";
	public final static String CMD_FAIL = "fail";

	public final static String CMD_MARK = "mark";

	public final static String MARK_SRTRW = "StartReadWriteRegimen";
	public final static String MARK_ENDRW = "EndReadWriteRegimen";
	public final static String MARK_SRTREAD = "StartReadOnlyRegimen";
	public final static String MARK_ENDREAD = "EndReadOnlyRegimen";
	public final static String MARK_SRTSTABLE = "StartStableRegimen";
	public final static String MARK_ENDSTABLE = "EndStableRegimen";

	public final static String REPLY_SUCESS = "ok";
	public final static String REPLY_FAILURE = "nok";
	public final static String REPLY_DATA = "data";

	public final static int DEFAULT_MANAGE_PORT = 6666;

	private Socket conn;
	private String address;
	private int port;

	public Command(String args[]) {

		address = System.getProperty("address");

		if (address == null) {
			String iface = System.getProperty("interface");
			if (iface != null) {
				try {
					address = NetworkingUtiilities.getAddress(iface);
				} catch (SocketException e) {
					System.err.println(
							"Failed to obtain IP address of interface '" + iface + "' fallback to the default value.");
					address = "127.0.0.1";
				}
			} else {
				address = "127.0.0.1";
			}
		}

		String value = System.getProperty("mport");
		if (value != null) {
			try {
				port = Integer.parseInt(value);
			} catch (Exception e) {
				System.err.println("Failed to optain port from property (value was '" + value
						+ "' fallback to the default value.");
			}
		} else {
			port = DEFAULT_MANAGE_PORT;
		}

	}

	public abstract String generateCommand(String args[]);

	public void processReply(Scanner sc, int nLines) {
		for (int i = 0; i < nLines; i++) {
			System.out.println(sc.nextLine());
		}
	}

	public void executeCommand(String[] args) {
		PrintStream ps = null;
		Scanner sc = null;

		try {
			conn = new Socket(InetAddress.getByName(this.address), this.port);

			ps = new PrintStream(conn.getOutputStream());
			sc = new Scanner(conn.getInputStream());

			ps.println(this.generateCommand(args));

			String reply = sc.nextLine();
			if (reply.equalsIgnoreCase(REPLY_SUCESS)) {
				System.out.println("Command Succeded");
			} else if (reply.equalsIgnoreCase(REPLY_FAILURE)) {
				System.out.println("Command Failed");
			} else if (reply.equalsIgnoreCase(REPLY_DATA)) {
				int n_lines = Integer.parseInt(sc.nextLine());
				this.processReply(sc, n_lines);
				System.out.println("Command Terminated");
			}

		} catch (Exception e) {
			System.err.println("Address that was attempted was: " + this.address + ":" + this.port);
			e.printStackTrace();
			System.exit(1);
		} finally {
			if (ps != null)
				ps.close();
		}
	}

}
