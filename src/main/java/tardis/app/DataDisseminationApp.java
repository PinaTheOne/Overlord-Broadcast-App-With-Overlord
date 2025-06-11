package tardis.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.hash.Hashing;

import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.notifications.BroadcastDelivery;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.requests.BroadcastRequest;

import pt.unl.fct.di.novasys.network.data.Host;
import tardis.app.command.Command;
import tardis.app.data.UserMessage;
import tardis.app.timers.AppWorkloadGenerateTimer;

public class DataDisseminationApp extends GenericProtocol {

	public final static String PROTO_NAME = "TaRDIS Simple App";
	public final static short PROTO_ID = 9999;

	public final static String PAR_GENERATE_WORKLOAD = "app.automatic";
	private boolean generateWorkload;

	public final static String PAR_WORKLOAD_PERIOD = "app.workload.period";
	public final static String PAR_WOKRLOAD_SIZE = "app.workload.payload";

	public final static long DEFAULT_WORKLOAD_PERIOD = 10 * 1000; // 10 seconds
	public final static int DEFAULT_WORKLOAD_SIZE = 63000;

	public final static String PAR_WORKLOAD_PROBABILITY = "app.workload.probability";
	public final static double DEFAULT_WORKLOAD_PROBABILITY = 1.0;

	public final static String PAR_BCAST_PROTOCOL_ID = "app.bcast.id";

	public final static String PAR_BCAST_INIT_ENABLED = "app.bcast.enable";
	public final static boolean DEFAULT_BCAST_INIT_ENABLED = true;

	public final static String PAR_MANAGEMENT_PORT = "app.management.port";
	public final static int DEFAULT_MANAGEMENT_PORT = Command.DEFAULT_MANAGE_PORT;

	private long workloadPeriod;
	private int payloadSize;
	private double generateMessageProbability;

	private short bcastProtoID;

	private final Host myself;

	private Logger logger = LogManager.getLogger(DataDisseminationApp.class);

	private AtomicBoolean executing;

	private String nodeLabel;

	private Thread managementThread;

	public DataDisseminationApp(Host myself) throws HandlerRegistrationException {
		super(DataDisseminationApp.PROTO_NAME, DataDisseminationApp.PROTO_ID);

		this.myself = myself;

		try {
			this.nodeLabel = myself.getAddress().getHostName().split("\\.")[3];
		} catch (Exception e) {
			this.nodeLabel = myself.getAddress().getHostName();
		}

		// Timer for generating messages
		registerTimerHandler(AppWorkloadGenerateTimer.PROTO_ID, this::handleAppWorkloadGenerateTimer);
		// Notificiation for delivery of messages
		subscribeNotification(BroadcastDelivery.NOTIFICATION_ID, this::handleMessageDeliveryEvent);
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		// Getting the broadcast protocol id
		if (props.containsKey(PAR_BCAST_PROTOCOL_ID)) {
			this.bcastProtoID = Short.parseShort(props.getProperty(PAR_BCAST_PROTOCOL_ID));
			logger.debug("DataDisseminationApp is configured to used broadcast protocol with id: " + this.bcastProtoID);
		} else {
			logger.error("The application requires the id of the broadcast protocol being used. Parameter: '"
					+ PAR_BCAST_PROTOCOL_ID + "'");
			System.exit(1);
		}

		// Set parameters of message generation

		this.generateWorkload = props.containsKey(PAR_GENERATE_WORKLOAD);

		if (this.generateWorkload) {
			if (props.containsKey(PAR_WORKLOAD_PERIOD))
				this.workloadPeriod = Long.parseLong(props.getProperty(PAR_WORKLOAD_PERIOD));
			else
				this.workloadPeriod = DEFAULT_WORKLOAD_PERIOD;

			if (props.containsKey(PAR_WOKRLOAD_SIZE))
				this.payloadSize = Integer.parseInt(props.getProperty(PAR_WOKRLOAD_SIZE));
			else
				this.payloadSize = DEFAULT_WORKLOAD_SIZE;

			if (props.containsKey(PAR_WORKLOAD_PROBABILITY))
				this.generateMessageProbability = Double.parseDouble(props.getProperty(PAR_WORKLOAD_PROBABILITY));
			else
				this.generateMessageProbability = DEFAULT_WORKLOAD_PROBABILITY;

			setupPeriodicTimer(new AppWorkloadGenerateTimer(), this.workloadPeriod, this.workloadPeriod);
			logger.debug("DataDisseminationApp has workload generation enabled.");
		} else
			logger.debug("DataDisseminationApp has workload generation disabled.");

		// Set the executing parameter - An ATOMIC BOOLEAN

		boolean b = DEFAULT_BCAST_INIT_ENABLED;

		if (props.containsKey(PAR_BCAST_INIT_ENABLED)) {
			b = Boolean.parseBoolean(props.getProperty(PAR_BCAST_INIT_ENABLED));
		}

		this.executing = new AtomicBoolean(b);

		// Management Port (For management thread) TODO: Assumo eu

		int mPort = Command.DEFAULT_MANAGE_PORT;
		if (props.containsKey(PAR_MANAGEMENT_PORT)) {
			mPort = Integer.parseInt(props.getProperty(PAR_MANAGEMENT_PORT));
		}

		final int managementPort = mPort;
	}

	// TODO: Assumo que isto serve para descodificar comandos que venham da management thread.
	private void handleManagementCommand(Socket c) {
		try {
			InputStream input = c.getInputStream();
			Scanner sc = new Scanner(input);
			PrintStream out = new PrintStream(c.getOutputStream());

			String cmd = sc.nextLine();
			logger.info("Management Command: " + cmd);

			if (cmd.equalsIgnoreCase(Command.CMD_START)) {
				float probability = Float.parseFloat(sc.nextLine());

				if (updateAndStartMessages(probability)) {
					out.println(Command.REPLY_SUCESS);
				} else {
					out.println(Command.REPLY_FAILURE);
				}
			} else if (cmd.equalsIgnoreCase(Command.CMD_STOP)) {
				if (stopMessages()) {
					out.println(Command.REPLY_SUCESS);
				} else {
					out.println(Command.REPLY_FAILURE);
				}
			} else if (cmd.equalsIgnoreCase(Command.CMD_FAIL)
					|| cmd.equalsIgnoreCase(Command.CMD_LEAVE)) {
				out.println(Command.REPLY_SUCESS);
				out.flush();
				System.exit(0);
			}

			sc.close();
			out.close();
			input.close();
		} catch (Exception e) {
			System.out.println("Client command has failed: " + e.getMessage());
			e.printStackTrace(System.out);
		}
	}

	// Updates message probability and starts execution (if not yet executing)
	public final boolean updateAndStartMessages(float probability) {
		logger.debug("Start messages request with {} probability", probability);

		this.generateMessageProbability = (double) probability;

		if (!this.executing.getAcquire()) {
			this.enableTransmission();
		}
		return true;
	}

	/** Stops sending messages
	 * @return true if already stopped, false if not.
	 */
	public final boolean stopMessages() {
		logger.debug("Stop messages request");

		if (this.executing.getAcquire()) {
			this.disableTransmissions();
			return true;
		} else {
			return false;
		}

	}

	/** When timer triggers, generates a message if relevant
	 *
	 */
	private void handleAppWorkloadGenerateTimer(AppWorkloadGenerateTimer t, long time) {
		// If executing is false, no message is generated
		if (!this.executing.getAcquire())
			return;
		// Generate a random to check if message should be generated or not
		if (this.generateMessageProbability < 1.0 && new Random().nextDouble() > this.generateMessageProbability)
			return; // We have conditioned probability to generate message

		logger.debug("DataDisseminationApp generating workload.");
		// Generate payload (contained in the message)
		String payload = DataDisseminationApp.randomCapitalLetters(this.payloadSize);

		String msg = myself.getAddress().getHostName() + " Memory Usage " + Runtime.getRuntime().totalMemory()
				+ " Available CPUs: " + Runtime.getRuntime().availableProcessors();

		// Generate message
		UserMessage message = new UserMessage(myself.toString(), nodeLabel + "-bot", msg,
				readableOutput(payload) + ".txt", payload.getBytes());
		byte[] data = null;

		// Payload to byteArray
		try {
			data = message.toByteArray();
		} catch (Exception e) {
			logger.error("Failed to serialize UserMessage");
		}

		// Generate and send request
		BroadcastRequest request = new BroadcastRequest(myself, data, PROTO_ID);
		sendRequest(request, bcastProtoID);

		if (message.hasAttachment()) {
			String readable = readableOutput(message.getMessage(), message.getAttachmentName());

			logger.info(myself + " sent message: [" + myself + "::::" + readable + "]");
		} else
			logger.info(myself + " sent message: [" + myself + "::::" + readableOutput(message.getMessage()) + "]");

	}

	// Message is delivered
	private void handleMessageDeliveryEvent(BroadcastDelivery msg, short proto) {
		UserMessage um = null;

		// If payload cannot be decoded.
		try {
			um = UserMessage.fromByteArray(msg.getPayload());
		} catch (Exception e) {
			// Purposefully not dealing with the exception
			// Assuming it means the message was not for me
			return;
		}

		// If message has an attachment (aparently it can have a file attached)
		if (um.hasAttachment()) {
			logger.debug("Delivered a message with attachment");

			String readable = readableOutput(um.getMessage(), um.getAttachmentName());

			logger.info(myself + " recv message: [" + msg.getSender() + "::::" + readable + "]");
		} else {
			logger.debug("Delivered a message");
			logger.info(myself + " recv message: [" + msg.getSender() + "::::" + readableOutput(um.getMessage()) + "]");
		}
	}

	// Simply generates 'length' capital letters
	public static String randomCapitalLetters(int length) {
		int leftLimit = 65; // letter 'A'
		int rightLimit = 90; // letter 'Z'
		Random random = new Random();
		return random.ints(leftLimit, rightLimit + 1).limit(length)
				.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
	}

	public static String readableOutput(String msg, String attachName) {
		return Hashing.sha256().hashString(msg + "::" + attachName, StandardCharsets.UTF_8).toString();
	}

	public static String readableOutput(String msg) {
		if (msg.length() > 32) {
			return Hashing.sha256().hashString(msg, StandardCharsets.UTF_8).toString();
		} else
			return msg;
	}

	/**
	 * This method disables the transmission of more messages after it being
	 * executed...
	 */
	public void disableTransmissions() {
		this.executing.set(false);
	}

	public void enableTransmission() {
		this.executing.set(true);
	}
}
