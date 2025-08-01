package pt.unl.fct.di.novasys.babel.protocols.eagerpush;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.unl.fct.di.novasys.babel.core.AutoConfigureParameter;
import pt.unl.fct.di.novasys.babel.core.adaptive.AdaptiveProtocol;
import pt.unl.fct.di.novasys.babel.core.adaptive.annotations.Adaptive;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.metrics.Counter;
import pt.unl.fct.di.novasys.babel.metrics.Metric;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.notifications.BroadcastDelivery;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.notifications.IdentifiableMessageNotification;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.requests.BroadcastRequest;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.requests.MissingIdentifiableMessageRequest;
import pt.unl.fct.di.novasys.babel.protocols.eagerpush.messages.GossipMessage;
import pt.unl.fct.di.novasys.babel.protocols.general.notifications.ChannelAvailableNotification;
import pt.unl.fct.di.novasys.babel.protocols.membership.notifications.NeighborDown;
import pt.unl.fct.di.novasys.babel.protocols.membership.notifications.NeighborUp;
import pt.unl.fct.di.novasys.babel.utils.recordexporter.utils.ExportRecordNotification;
import pt.unl.fct.di.novasys.babel.utils.recordexporter.utils.ReceiveRecord;
import pt.unl.fct.di.novasys.babel.metrics.Record;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.InConnectionDown;
import pt.unl.fct.di.novasys.channel.tcp.events.InConnectionUp;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionDown;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionFailed;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionUp;
import pt.unl.fct.di.novasys.network.data.Host;

public class AdaptiveEagerPushGossipBroadcast extends AdaptiveProtocol {

	private static final Logger logger = LogManager.getLogger(AdaptiveEagerPushGossipBroadcast.class);

	public final static short PROTOCOL_ID = 1601;
	public final static String PROTOCOL_NAME = "AdaptiveEagerPushGossipBroadcast";

	public final static String PAR_CHANNEL_ADDRESS = "EagerPushGossipBroadcast.Channel.Address";
	public final static String PAR_CHANNEL_PORT = "EagerPushGossipBroadcast.Channel.Port";

	public final static String PAR_FANOUT = "EagerPushGossipBroadcast.Fanout";
	public final static String DEFAULT_FANOUT = "4";

	public final static String PAR_DELIVERY_TIMEOUT = "EagerPushGossipBroadcast.DeliveredTimeout";
	public final static String DEFAULT_DELIVERY_TIMEOUT = "600000";

	public final static String PAR_SUPPORT_ANTIENTROPHY = "EagerPushGossipBroadcast.SupportAntiEntropy";
	public final static String DEFAULT_SUPPORT_ANTIENTROPHY = "false";

	public final static String PAR_USE_DEFAULT_CONFIG = "EagerPushGossipBroadcast.UseDefaultConfig";
	public final static String DEFAULT_USE_DEFAULT_CONFIG = "true";

	public final static String PAR_DNS_HOST = "EagerPushGossipBroadcast.DNSHost";

	// Metrics Names

	public final static String SENT_MESSAGES_COUNTER = "SentMessages";
	public final static String RECEIVED_MESSAGES_COUNTER = "ReceivedMessages";
	public final static String DUPLICATE_MESSAGES_COUNTER = "DuplicateMessages";
	public final static String DELIVERED_MESSAGES_COUNTER = "DeliveredMessages";
	public final static String SENT_MESSAGES_RECORD = "SentMessagesRecord";
	public final static String RECEIVE_MESSAGES_RECORD = "ReceiveMessagesRecord";
	public final static String DELIVERED_MESSAGES_RECORD = "DeliveredMessagesRecord";

	@Adaptive
	@AutoConfigureParameter
	public int fanout;
	@AutoConfigureParameter
	public long removeTimeWindow;
	@AutoConfigureParameter
	public Boolean supportAntiEntrophy;

	private final Boolean useDefaultConfig;

	protected int channelId;
	public final int networkPort;

	private Set<Host> pending;
	private Set<Host> connectedNeighbors;
	private Set<Host> neighbors;

	private LinkedList<UUID> receivedInOrder;
	private HashMap<UUID, Long> receivedTimestamps;

	private final Random r;

	private final String DNSHost;

	private final Counter sentMessagesCounter;
	private final Counter receivedMessagesCounter;
	private final Counter duplicateMessagesCounter;
	private final Counter deliveredMessagesCounter;
	private final Record sentMessagesRecord;
	private final Record receivedMessagesRecord;
	private final Record deliveredMessagesRecord;


	public AdaptiveEagerPushGossipBroadcast(String channelName, Properties properties, Host myself)
			throws IOException, HandlerRegistrationException {
		super(PROTOCOL_NAME, PROTOCOL_ID);
		setMyself(myself);

		this.useDefaultConfig = Boolean.parseBoolean(properties.getProperty(PAR_USE_DEFAULT_CONFIG,
				DEFAULT_USE_DEFAULT_CONFIG));

		this.pending = new TreeSet<Host>();
		this.connectedNeighbors = new TreeSet<Host>();
		this.neighbors = new TreeSet<Host>();

		this.receivedInOrder = new LinkedList<UUID>();
		this.receivedTimestamps = new HashMap<UUID, Long>();

		this.r = new Random(System.currentTimeMillis());

		this.DNSHost = properties.getProperty(PAR_DNS_HOST, null);

		// Metrics initialization
		this.sentMessagesCounter = registerMetric(new Counter.Builder(SENT_MESSAGES_COUNTER, Metric.Unit.NONE).build());
		this.receivedMessagesCounter = registerMetric(new Counter.Builder(RECEIVED_MESSAGES_COUNTER, Metric.Unit.NONE).build());
		this.duplicateMessagesCounter = registerMetric(new Counter.Builder(DUPLICATE_MESSAGES_COUNTER, Metric.Unit.NONE).build());
		this.deliveredMessagesCounter = registerMetric(new Counter.Builder(DELIVERED_MESSAGES_COUNTER, Metric.Unit.NONE).build());
		this.sentMessagesRecord = registerMetric(new Record.Builder(SENT_MESSAGES_RECORD, "message_id", "node")
				.timestampParam()
				.description("Record with sent messages")
				.build());
		this.receivedMessagesRecord = registerMetric(new Record.Builder(RECEIVE_MESSAGES_RECORD, "message_id", "node","hop_count")
				.timestampParam()
				.description("Record with received messages")
				.build());

		this.deliveredMessagesRecord = registerMetric(new Record.Builder(DELIVERED_MESSAGES_RECORD, "message_id", "node","hop_count")
				.timestampParam()
				.description("Record with delivered messages")
				.build());
		
		String address = null;
		String port = null;

		address = properties.getProperty(AdaptiveEagerPushGossipBroadcast.PAR_CHANNEL_ADDRESS,
				myself.getAddress().getHostAddress().toString());
		port = properties.getProperty(AdaptiveEagerPushGossipBroadcast.PAR_CHANNEL_PORT, myself.getPort() + "");

		this.networkPort = Integer.parseInt(port);

		Properties channelProps = new Properties();
		channelProps.setProperty(TCPChannel.ADDRESS_KEY, address); // The address to bind to
		channelProps.setProperty(TCPChannel.PORT_KEY, port); // The port to bind to
		this.channelId = createChannel(TCPChannel.NAME, channelProps);
		setDefaultChannel(channelId);
		logger.debug("Created new channel with id " + channelId + " and bounded to: " + address + ":" + port);

		setDefaultChannel(channelId);



	}

	@Override
	public void init(Properties properties) throws HandlerRegistrationException, IOException {
		if (this.useDefaultConfig) { // Falls back to default values if not provided
			this.fanout = Integer
					.parseInt(properties.getProperty(AdaptiveEagerPushGossipBroadcast.PAR_FANOUT, DEFAULT_FANOUT));
			this.removeTimeWindow = Long.parseLong(properties
					.getProperty(AdaptiveEagerPushGossipBroadcast.PAR_DELIVERY_TIMEOUT, DEFAULT_DELIVERY_TIMEOUT));
			this.supportAntiEntrophy = Boolean
					.parseBoolean(properties.getProperty(PAR_SUPPORT_ANTIENTROPHY, DEFAULT_SUPPORT_ANTIENTROPHY));

		} else { // Uses the provided values or invalidates them if not provided
			this.fanout = Integer.parseInt(properties.getProperty(AdaptiveEagerPushGossipBroadcast.PAR_FANOUT, "-1"));
			this.removeTimeWindow = Long
					.parseLong(properties.getProperty(AdaptiveEagerPushGossipBroadcast.PAR_DELIVERY_TIMEOUT, "-1"));
			if (properties.containsKey(PAR_SUPPORT_ANTIENTROPHY))
				this.supportAntiEntrophy = Boolean.parseBoolean(properties.getProperty(PAR_SUPPORT_ANTIENTROPHY));
			else
				this.supportAntiEntrophy = null;
		}

		logger.info("Configured Fanout={}", this.fanout);

		/*-------------------- Register Notification Handlers ------------------------ */
		subscribeNotification(NeighborUp.NOTIFICATION_ID, this::uponNeighborUp);
		subscribeNotification(NeighborDown.NOTIFICATION_ID, this::uponNeighborDown);

		/*-------------------- Register Channel Event ------------------------------- */
		registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
		registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
		registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
		registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
		registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);

	}

	
	/*---------------------------- Gossip Aux Methods----------------------------*/

		private void pushGossipMessage(GossipMessage msg, ArrayList<Host> validTargets, int toSend) {
			while (!validTargets.isEmpty() && toSend > 0) {
				Host target = validTargets.remove(r.nextInt(validTargets.size()));
				sendMessage(msg.clone(), target);
				// TODO: Repor Sent Messages
				toSend--;
	
				logger.debug("Forwarded gossip message " + msg.getMID() + " to " + target + " (valid targets: "
						+ validTargets.size() + " missing: " + toSend + ")");
			}	
		}

	/*---------------------------- Request handlers----------------------------*/

	private void uponBroadcastRequest(BroadcastRequest request, short protoID) {
		GossipMessage msg = new GossipMessage(request.getTimestamp(), getMyself(), request.getPayload(), protoID);

		deliverMessage(msg.clone());
		logger.debug("Broadcasting Message: {}", msg.getMID());
		logger.debug("Received request to broadcast. currently I have " + connectedNeighbors.size() + " targets. Fanout is "
						+ this.fanout);

		ArrayList<Host> validTargets = new ArrayList<Host>(this.connectedNeighbors);

		sentMessagesRecord.record(msg.getMID().toString(), getMyself().toString());
		sentMessagesCounter.inc();

		pushGossipMessage(msg.clone(), validTargets, this.fanout);

		cleanUp();
	}

	private void deliverMessage(GossipMessage msg) {
		this.receivedInOrder.addLast(msg.getMID());
		this.receivedTimestamps.put(msg.getMID(), System.currentTimeMillis());
		logger.debug("Delivered Message: {}", msg.getMID());
		ReceiveRecord record = new ReceiveRecord(this.getMyself(), msg.getMID(), System.currentTimeMillis(),
				msg.getTimestamp().getTime(), msg.getSender(), msg.getHopCount());
		this.receivedMessagesRecord.record(msg.getMID().toString(), getMyself().toString(), String.valueOf(msg.getHopCount()));
		this.receivedMessagesCounter.inc();
		this.deliveredMessagesRecord.record(msg.getMID().toString(), getMyself().toString(), String.valueOf(msg.getHopCount()));
		this.deliveredMessagesCounter.inc();
		triggerNotification(new ExportRecordNotification(record));

		triggerNotification(new BroadcastDelivery(msg.getSender(), msg.getPayload(), msg.getTimestamp()));
		if (this.supportAntiEntrophy)
			triggerNotification(new IdentifiableMessageNotification(msg, AdaptiveEagerPushGossipBroadcast.PROTOCOL_ID));
	}

	private void cleanUp() {
		long now = System.currentTimeMillis();
		while (!this.receivedInOrder.isEmpty()
				&& this.receivedTimestamps.get(this.receivedInOrder.pollFirst()) + this.removeTimeWindow < now)
			this.receivedTimestamps.remove(this.receivedInOrder.removeFirst());
	}

	/*----------------------------Message handlers ----------------------------*/

	private void uponGossipMessage(GossipMessage msg, Host sender, short protoID, int cID) {
		if (!this.receivedTimestamps.containsKey(msg.getMID())) {
			msg.incrementHopCount();

			deliverMessage(msg.clone());

			ArrayList<Host> validTargets = new ArrayList<Host>(this.connectedNeighbors);
			validTargets.remove(sender);
			validTargets.remove(msg.getSender());

			pushGossipMessage(msg, validTargets, this.fanout);

			cleanUp();
		} else {
			// Log it to account for duplicates
			ReceiveRecord record = new ReceiveRecord(this.getMyself(), msg.getMID(), System.currentTimeMillis(),
					msg.getTimestamp().getTime(), msg.getSender(), msg.getHopCount());
			logger.debug("Received Duplicate Message: {}", msg.getMID());
			this.receivedMessagesRecord.record(msg.getMID().toString(), getMyself().toString(), String.valueOf(msg.getHopCount()));
			this.receivedMessagesCounter.inc();
			this.duplicateMessagesCounter.inc();
			triggerNotification(new ExportRecordNotification(record));
		}
	}

	/*----------------------------Notification handlers----------------------------*/

	private void uponNeighborUp(NeighborUp up, short protoID) {
		logger.debug("NeighborUp received: " + up.getPeer());

		Host h = new Host(up.getPeer().getAddress(), this.networkPort); // Transformation of Host to have network port
																		// of this protocol.

		this.neighbors.add(h);

		if (!this.connectedNeighbors.contains(h) && this.pending.add(h))
			openConnection(h);
	}

	private void uponNeighborDown(NeighborDown down, short protoID) {
		Host h = new Host(down.getPeer().getAddress(), this.networkPort); // Transformation of Host to have network port
																			// of this protocol.

		this.neighbors.remove(h);

		if (this.connectedNeighbors.remove(h)) {
			closeConnection(h);
		} else if (this.pending.remove(h)) {
			closeConnection(h);
		}
	}

	private void uponMissingMessageRequest(MissingIdentifiableMessageRequest req, short protoID) {
		logger.debug("Received an anti-entrophy request indicating that " + req.getDestination() + " is missing "
				+ req.getMessage().getMID());

		Host d = null;
		if (this.connectedNeighbors.contains(req.getDestination()) || this.pending.contains(req.getDestination())) {
			d = req.getDestination();
			logger.debug("Destination in requeest is among my connections (active or pending)");
		} else {
			Host tmp = new Host(req.getDestination().getAddress(), this.networkPort);
			if (this.connectedNeighbors.contains(tmp) || this.pending.contains(tmp)) {
				d = tmp;
				logger.debug("Destination was translated to my protocol channel (" + tmp
						+ ") and was found among my connections (active or pending)");
			}
		}

		if (d != null) {
			sendMessage(req.getMessage(), d);
			sentMessagesCounter.inc();
			sentMessagesRecord.record(req.getMessage().getMID().toString(), getMyself().toString());
			logger.info("Recoved message " + req.getMessage().getMID() + " to " + d);
		} else {
			logger.info("Unable to recover message " + req.getMessage().getMID() + " to " + req.getDestination());
		}

	}
	/*
	 * --------------------------------- Channel Events ----------------------------
	 */

	private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
		Host h = event.getNode();
		logger.trace("Host {} is down, cause: {}", h, event.getCause());

		if (this.neighbors.contains(h)) {
			this.connectedNeighbors.remove(h);
			this.pending.add(h);
			openConnection(h);
		} else {
			this.connectedNeighbors.remove(h);
			this.pending.remove(h);
			closeConnection(h);
		}
	}

	private void uponOutConnectionFailed(OutConnectionFailed<?> event, int channelId) {
		Host h = event.getNode();
		logger.trace("Connection to host {} failed, cause: {}", h, event.getCause());

		if (this.neighbors.contains(h)) {
			this.connectedNeighbors.remove(h);
			this.pending.add(h);
			openConnection(h);
		} else {
			this.connectedNeighbors.remove(h);
			this.pending.remove(h);
			closeConnection(h);
		}
	}

	private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
		Host h = event.getNode();
		logger.trace("Host (out) {} is up", h);

		if (this.neighbors.contains(h)) {
			this.pending.remove(h);
			this.connectedNeighbors.add(h);
		} else {
			this.pending.remove(h);
			this.connectedNeighbors.remove(h);
			closeConnection(h);
		}
	}

	private void uponInConnectionUp(InConnectionUp event, int channelId) {
		logger.debug("Host (in) {} is up", event.getNode());
	}

	private void uponInConnectionDown(InConnectionDown event, int channelId) {
		logger.debug("Connection from host {} is down, cause: {}", event.getNode(), event.getCause());
	}

	/*---------------------- Adaptive Setters ----------------------*/

	public void setFanout(Integer fanout) {
		if (fanout == null)
			return;
		logger.debug("Setting adaptive fanout to {} ", fanout);
		this.fanout = fanout;
	}

	/*----------------------SelfConfigurable Protocol Interface----------------------*/

	@Override
	public void start() {
		try {
			if (this.supportAntiEntrophy)
				registerRequestHandler(MissingIdentifiableMessageRequest.REQUEST_ID, this::uponMissingMessageRequest);

			/*---------------------- Register Message Serializers ---------------------- */
			registerMessageSerializer(channelId, GossipMessage.MSG_CODE, GossipMessage.serializer);

			/*---------------------- Register Message Handlers ---------------------- */
			registerMessageHandler(channelId, GossipMessage.MSG_CODE, this::uponGossipMessage);

			/*---------------------- Register Requests Handlers -------------------------- */
			registerRequestHandler(BroadcastRequest.REQUEST_ID, this::uponBroadcastRequest);

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		triggerNotification(
				new ChannelAvailableNotification(PROTOCOL_ID, PROTOCOL_NAME, this.channelId, TCPChannel.NAME,
						getMyself()));
	}

	@Override
	public boolean readyToStart() {
		return fanout != -1 && removeTimeWindow != -1 && supportAntiEntrophy != null;
	}

	@Override
	public boolean needsDiscovery() {
		return false;
	}

	@Override
	public void addContact(Host host) {
	}

	@Override
	public Host getContact() {
		return null;
	}

	public String getFirstFanout() {
		return this.fanout == -1 ? null : this.fanout + "";
	}

	public String getFirstRemoveTimeWindow() {
		return this.removeTimeWindow == -1 ? null : this.removeTimeWindow + "";
	}

	public String getFirstSupportAntiEntrophy() {
		return this.supportAntiEntrophy == null ? null : this.supportAntiEntrophy.toString();
	}

	public void setFirstFanout(String fanout) {
		if (fanout == null)
			return;
		logger.debug("Fanout set to {} on startup", fanout);
		this.fanout = Integer.parseInt(fanout);
	}

	public void setFirstRemoveTimeWindow(String removeTimeWindow) {
		if (removeTimeWindow == null)
			return;
		logger.debug("RemoveTimeWindow set to {} on startup", removeTimeWindow);
		this.removeTimeWindow = Long.parseLong(removeTimeWindow);
	}

	public void setFirstSupportAntiEntrophy(String supportAntiEntrophy) {
		if (supportAntiEntrophy == null)
			return;
		logger.debug("SupportAntiEntrophy set to {} on startup", supportAntiEntrophy);
		this.supportAntiEntrophy = Boolean.parseBoolean(supportAntiEntrophy);
	}

	public String getHost() {
		return DNSHost;
	}
}
