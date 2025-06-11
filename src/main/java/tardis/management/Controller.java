package tardis.management;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.core.adaptive.AdaptiveMembershipProtocol;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.GetAdaptiveFieldsReply;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.GetAdaptiveFieldsRequest;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.Reconfigure;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.Reconfigure.ReconfigureBuilder;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.notifications.BroadcastDelivery;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.requests.BroadcastRequest;
import pt.unl.fct.di.novasys.babel.protocols.eagerpush.AdaptiveEagerPushGossipBroadcast;
import pt.unl.fct.di.novasys.network.data.Host;
import tardis.management.timers.GetAdaptiveFieldsTimer;

public class Controller extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(Controller.class);

    public final static short PROTOCOL_ID = 13237;
    public final static String PROTOCOL_NAME = "Tardis-Controller";

    public final static String PAR_MEMBERSHIP_PROTO_ID = "Controller.membership.id";
    public final static String PAR_BROADCAST_PROTO_ID = "Controller.broadcast.id";

    public final static String PROP_CHANGE_BROADCAST_FANOUT_TYPE = "ChangeBroadcastFanout.type";

    public final static String PROP_AGENT_ADDRESS = "Agent.address";
    public final static String PROP_AGENT_PORT = "Agent.port";

    public final static String PROP_FANOUT = "Controller.fanout";

    private short membershipProtocolID;
    private short membershipNeighborCapacity;
    private short broadcastProtocolID;
    private int broadcastFanout;

    private Host myself;

    public Controller(Properties props, Host myself) throws HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.membershipProtocolID = 400;
        this.membershipNeighborCapacity = -1;
        this.broadcastProtocolID = 1601;
        this.myself = myself;

        registerTimerHandler(GetAdaptiveFieldsTimer.TIMER_ID, this::uponGetAdaptiveFieldsTimer);
        registerReplyHandler(GetAdaptiveFieldsReply.REPLY_ID, this::uponGetAdaptiveFieldsReply);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {
        System.out.println(props);

        /* SETTING THE PROTOCOL IDS FOR BOTH MEMBERSHIP AND BROADCAST PROTOCOLS */

        if (props.containsKey(PAR_MEMBERSHIP_PROTO_ID))
            this.membershipProtocolID = Short.parseShort(props.getProperty(PAR_MEMBERSHIP_PROTO_ID));
        if (props.containsKey(PAR_BROADCAST_PROTO_ID))
            this.broadcastProtocolID = Short.parseShort(props.getProperty(PAR_BROADCAST_PROTO_ID));

        /* Broadcast Fannout (tries to retrieve it, if it can't, will use the default value) */

        this.broadcastFanout = Integer.parseInt(props.getProperty(AdaptiveEagerPushGossipBroadcast.PAR_FANOUT,
                AdaptiveEagerPushGossipBroadcast.DEFAULT_FANOUT));

        // Handle agent configuration TODO: What is this?
        String changeBroadcastFanoutType = props.getProperty(PROP_CHANGE_BROADCAST_FANOUT_TYPE, "none");

        final String agentAddress = props.getProperty(PROP_AGENT_ADDRESS);
        final int agentPort = Integer.parseInt(props.getProperty(PROP_AGENT_PORT, "-1"));

        /* Agent Setup & Error verification*/

        if (changeBroadcastFanoutType.equals("agent")) {
            if (agentAddress == null || agentPort == -1) {
                logger.error("Missing address or port for agent communication");
                System.exit(1);
            }

            logger.debug("Setting up an agent listener for changing broadcast fanout");
            Thread thread = new Thread(() -> subscribeToBroadcastReconfiguration(agentAddress, agentPort));
            thread.start();
        }

        // TODO: Isto é suposto estar assim?
        // subscribeNotification(BroadcastDelivery.NOTIFICATION_ID,
        // this::uponBroadcastDelivery);
    }

    // TODO: Pergunto-me porque é que isto usa ZMQ e não o babel em si
    // Subscribe to Agent that is responsible for altering the fanout parameter. It listens to the agent,
    // waiting for the new fanout value. When it receives, it triggers the function
    // --reconfigureBroadcast(receivedFanout)--
    private void subscribeToBroadcastReconfiguration(String address, int port) {
        try (ZContext context = new ZContext()) {
            ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            subscriber.connect(String.format("tcp://%s:%d", address, port));
            subscriber.subscribe(new byte[0]);

            while (!Thread.currentThread().isInterrupted()) {
                byte[] message = subscriber.recv(0);
                int receivedFanout = ByteBuffer.wrap(message).getInt();

                logger.debug("Received fanout {} from agent", receivedFanout);

                reconfigureBroadcast(receivedFanout);
            }
        } catch (Exception e) {
            // Presently not expecting any specific exceptions
            e.printStackTrace();
            logger.warn("Failed to subscribe to fanout", e);
        }
    }

    // Reconfigures the fanout of the broadcast.
    // It creates a Reconfigure Request, and sends it to the Broadcast ID
    // (Broadcast protocol is prepared to receive new fanout parameters)
    // The function --subscribeToBroadcastReconfiguration-- establishes a
    // thread that listens for incoming fanout alterations. When it receives one, it triggers this function,
    private void reconfigureBroadcast(int fanout) {
        Reconfigure r = new ReconfigureBuilder().addProperty("fanout", fanout).build();
        sendRequest(r, this.broadcastProtocolID);
    }

    @SuppressWarnings("unused")
    // TODO: Se isto existe, para que o uso de ZMQ? Faz o mesmo penso eu... mas em vez de uma
    //  thread sempre a ouvir, usa o sistema de eventos do babel.
    private void uponBroadcastDelivery(BroadcastDelivery n, short proto) {
        ChangeBroadcastFanoutMessage fm = null;
        // Ignore message if message can't be decoded
        try {
            fm = ChangeBroadcastFanoutMessage.fromByteArray(n.getPayload());
        } catch (IOException e) {
            // Purposefully not dealing with the exception
            // Assuming it means the message was not for me
            return;
        }

        logger.debug("Current broadcast fanout is {}", this.broadcastFanout);
        logger.debug("Received broadcast fanout {} from {} ", fm.getNewFanout(), fm.getSender());

        // No adjustments needed. Received fanout is the same as mine
        if (this.broadcastFanout == fm.getNewFanout()) {
            logger.debug("No need to adjust broadcast fanout");
            return;
        }

        this.broadcastFanout = fm.getNewFanout();

        // Ideally we would check if reconfiguration succeeded
        reconfigureBroadcast(fm.getNewFanout());
    }

    @SuppressWarnings("unused")
    // TODO: Isto não é usado at all,
    private void propagateBroadcastFanout(int fanout) {
        ChangeBroadcastFanoutMessage m = new ChangeBroadcastFanoutMessage(this.myself.toString(), fanout);

        try {
            BroadcastRequest br = new BroadcastRequest(myself, m.toByteArray(), PROTOCOL_ID);
            sendRequest(br, this.broadcastProtocolID);
        } catch (IOException e) {
            logger.error("Failed to serialize ChangeFanoutMessage");
        }
    }

    // Requests the needed adaptive fields when the timer triggers.
    private void uponGetAdaptiveFieldsTimer(GetAdaptiveFieldsTimer timer, long timerId) {
        // TODO: Pelo que vi, os valores não são repostos a -1, por isso isto nao devia significar que ja
        //  foram recebidos. A não ser que isto seja uma one time function
        if (membershipNeighborCapacity != -1 && broadcastFanout != -1) {
            logger.debug("Adaptive fields already received");
            cancelTimer(timerId);
            return;
        }
        logger.debug("Requesting adaptive fields to {} and {}", membershipProtocolID, broadcastProtocolID);

        // TODO: E se forem os dois -1???
        // Sends a request to either protocol for their parameter's values
        if (membershipProtocolID != -1 && membershipNeighborCapacity == -1) {
            logger.debug("Missing number of neighbors");
            sendRequest(new GetAdaptiveFieldsRequest(), membershipProtocolID);
        }

        if (broadcastProtocolID != -1 && broadcastFanout == -1) {
            logger.debug("Missing broadcast fanout");
            sendRequest(new GetAdaptiveFieldsRequest(), broadcastProtocolID);
        }
    }

    // Receives a reply to fields request. Can be from either membership neighbors or fanout
    private void uponGetAdaptiveFieldsReply(GetAdaptiveFieldsReply reply, short sourceProto) {
        logger.debug("Received adaptive fields: {}", reply.fields);
        if (sourceProto == membershipProtocolID) {
            Long neighbors = (Long) reply.fields.get(AdaptiveMembershipProtocol.NUMBER_OF_NEIGHBORS);
            if (neighbors != null) {
                this.membershipNeighborCapacity = neighbors.shortValue();
                logger.debug("Received number of neighbors: " + membershipNeighborCapacity);
            }
        } else if (sourceProto == broadcastProtocolID) {
            Integer fanout = (Integer) reply.fields.get("fanout");
            if (fanout != null) {
                this.broadcastFanout = fanout;
                logger.debug("Received broadcast fanout: " + broadcastFanout);
            }
        }
    }

}
