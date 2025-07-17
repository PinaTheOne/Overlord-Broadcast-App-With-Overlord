package tardis.management;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.core.adaptive.AdaptiveMembershipProtocol;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.GetAdaptiveFieldsReply;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.GetAdaptiveFieldsRequest;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.protocols.eagerpush.AdaptiveEagerPushGossipBroadcast;
import pt.unl.fct.di.novasys.network.data.Host;

public class Controller extends GenericProtocol {

    private static final Logger logger = LogManager.getLogger(Controller.class);

    public final static short PROTOCOL_ID = 13237;
    public final static String PROTOCOL_NAME = "Tardis-Controller";

    public final static String PAR_MEMBERSHIP_PROTO_ID = "Controller.membership.id";
    public final static String PAR_BROADCAST_PROTO_ID = "Controller.broadcast.id";
    @SuppressWarnings({"unused", "FieldCanBeLocal"}) // TODO: Check
    private final Host myself;

    private short membershipProtocolID;
    private short membershipNeighborCapacity;
    private short broadcastProtocolID;
    private int broadcastFanout;

    public Controller(Host myself) throws HandlerRegistrationException {
        super(PROTOCOL_NAME, PROTOCOL_ID);
        this.myself = myself;
        this.membershipProtocolID = 400;
        this.membershipNeighborCapacity = -1;
        this.broadcastProtocolID = 1601;

        registerReplyHandler(GetAdaptiveFieldsReply.REPLY_ID, this::uponGetAdaptiveFieldsReply);
    }

    @Override
    public void init(Properties props) {
        System.out.println(props);

        //registerRequestHandler(ManualFanoutChangeRequest.REQUEST_ID, this::uponManualFanoutChangeRequest);

        /* SETTING THE PROTOCOL IDS FOR BOTH MEMBERSHIP AND BROADCAST PROTOCOLS */

        if (props.containsKey(PAR_MEMBERSHIP_PROTO_ID))
            this.membershipProtocolID = Short.parseShort(props.getProperty(PAR_MEMBERSHIP_PROTO_ID));
        if (props.containsKey(PAR_BROADCAST_PROTO_ID))
            this.broadcastProtocolID = Short.parseShort(props.getProperty(PAR_BROADCAST_PROTO_ID));

        /* Broadcast Fannout (tries to retrieve it, if it can't, will use the default value) */

        this.broadcastFanout = Integer.parseInt(props.getProperty(AdaptiveEagerPushGossipBroadcast.PAR_FANOUT,
                AdaptiveEagerPushGossipBroadcast.DEFAULT_FANOUT));

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
