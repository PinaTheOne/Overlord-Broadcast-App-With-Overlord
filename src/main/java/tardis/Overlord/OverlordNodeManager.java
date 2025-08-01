package tardis.Overlord;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.Reconfigure;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.metrics.MetricsManager;
import pt.unl.fct.di.novasys.babel.metrics.NodeSample;
import pt.unl.fct.di.novasys.babel.metrics.exporters.ExporterCollectOptions;
import pt.unl.fct.di.novasys.babel.metrics.exporters.ProtocolExporterHelper;
import pt.unl.fct.di.novasys.babel.metrics.monitor.Monitor;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.notifications.BroadcastDelivery;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.CollectDataNotification;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.ReceiveAggregatedDataNotification;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.requests.AggregateDataRequest;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.requests.MonitorDataRequest;
import tardis.Overlord.utils.ReconfigurationsContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class OverlordNodeManager extends Monitor {

    public static final Logger logger = LogManager.getLogger(OverlordNodeManager.class);

    // Protocol Information
    public static final String PROTO_NAME = "OverlordManager";
    public static final short PROTO_ID = 1100;
    private ProtocolExporterHelper protocolExporterHelper;
    protected long metricCollectionPeriod;
    protected long messageValidity;
    protected final long moncollectProtoId;

    public OverlordNodeManager(short moncollectProtoId){
        super(PROTO_NAME, PROTO_ID);
        this.moncollectProtoId = moncollectProtoId;
        this.protocolExporterHelper = new ProtocolExporterHelper.Builder("OverlordNodeExporter").build();
    }

    @Override
    public void init(Properties props) {

        if(props.containsKey(OverlordManager.PAR_MESSAGE_VALIDITY_TIME_MS))
            this.messageValidity = Long.parseLong(props.getProperty(OverlordManager.PAR_MESSAGE_VALIDITY_TIME_MS));
        else
            this.messageValidity = OverlordManager.DEFAULT_MESSAGE_VALIDITY_TIME_MS;

        // For reception of reconfiguration of parameters
        try {
            registerRequestHandler(MonitorDataRequest.REQUEST_ID, this::uponMonitorDataRequest_real);
            registerRequestHandler(AggregateDataRequest.REQUEST_ID, this::uponAggregateDataRequest_real);
            subscribeNotification(BroadcastDelivery.NOTIFICATION_ID, this::uponBroadcastDelivery);
        } catch (HandlerRegistrationException e){
            logger.error("Could not Register Handler: {}", e.getMessage());
        }
    }

    /* ******************************** *
     * *** PROTOCOL EXPORTER METHODS ** *
     * ******************************** */

    protected void setExporterOptions(ExporterCollectOptions opts){
        this.protocolExporterHelper = new ProtocolExporterHelper.Builder("OverlordNodeExporter").exporterCollectOptions(opts).build();
    }

    protected ProtocolExporterHelper getProtocolExporter(){ return this.protocolExporterHelper; }

    /* ******************************** *
     * ***** PARAMETER ADAPTATION ***** *
     * ******************************** */

    /**
     * Used to receive fanout order changes. It receives every message, and if it contains a new fanout message
     * changes the fanout value to the new one in the broadcast algorithm.
     * @param n Delivered message (that contains a ChangeBroadcastFanoutMessage if it is for this protocol).
     * @param proto The ID of the protocol that delivered the message.
     */
    private void uponBroadcastDelivery(BroadcastDelivery n, short proto) {
        logger.info("Received a Broadcast Delivery from protocol: {}", proto);
        List<Pair<Reconfigure, Short>> reconfigures;
        // Ignore message if message can't be decoded
        try {
            reconfigures = ReconfigurationsContainer.fromByteArray(n.getPayload());
            logger.debug("Delivered Message is a Reconfiguration List, proceeding with the reconfigurations");
        } catch (IOException | ClassNotFoundException e) {
            // Purposefully not dealing with the exception
            // Assuming it means the message was not for me
            logger.debug("Delivered Message is not for me, ignoring it...");
            return;
        }
        logger.info("Sending Reconfigurations:");
        int i = 0;
        for(Pair<Reconfigure, Short> r : reconfigures){
            logger.info("   {} - Reconfiguration {} to protocol {}", i++, r.getValue0(), r.getValue1());
            sendRequest(r.getValue0(), r.getValue1());
        }
    }

    /* ************************************************* *
     * ****** METRICS AND MON-COLLECT INTERACTION ****** *
     * ************************************************* */

    /**
     * MON-Collect will send a monitor data request to each node. Here you must collect all the metrics you wish
     * to be aggregated and sent to the overlord. Overlord uses babel metrics, so you must return a
     * {@code Map<String, NodeSample>}, which is what the method this.performAggregations returns, in case
     * you want to aggregate data of a single node before it is aggregated with the rest of the nodes.
     * If you do not wish to aggregate a single node just place the result of this.collectAllMetrics() in a Map like
     * {@code map.put(this.myself, this.collectAllMetrics())}
     * @return The data that is to be passed through MON-Collect.
     */
    protected abstract Map<String, NodeSample> uponMonitorDataRequest();


    /**
     * MON-Collect will send an aggregate data request to each node. You must aggregate the data as you see fit using
     * the provided tools in babel (located in {@link pt.unl.fct.di.novasys.babel.metrics},
     * using {@link pt.unl.fct.di.novasys.babel.metrics.monitor.Aggregation}).
     * MON-Collect gives you a {@code List<Map<String, NodeSample>>}, where the string is the host
     * that sent the sample. For each value in each Map, do all the transformations you wish, and do
     * this.addSampleToAggregate. Finally, do this.performAggregations() which will give you the map with all
     * aggregations already performed.
     * @param data The request object, containing the List of the collected data by other nodes;
     * @return The aggregated data that is to be passed through MON-Collect.
     */
    protected abstract Map<String, NodeSample> uponAggregateDataRequest(List<Map<String, NodeSample>> data);

    /* Real Requests - DO NOT TOUCH */

    private void uponMonitorDataRequest_real(MonitorDataRequest req, short protoID){
        logger.info("Received Monitor Data Request");
        Map<String, NodeSample> data = this.uponMonitorDataRequest();
        triggerNotification(new CollectDataNotification(serializeSampleMap(data)));
    }

    private void uponAggregateDataRequest_real(AggregateDataRequest req, short protoID){
        List<byte[]> reqList = req.getData();
        List<Map<String, NodeSample>> sampleList = new ArrayList<>();
        for(byte[] b : reqList)
            sampleList.add(deserializeSampleMap(b));
        logger.info("Received Aggregation Request");
        Map<String, NodeSample> data = this.uponAggregateDataRequest(sampleList);
        triggerNotification(new ReceiveAggregatedDataNotification(serializeSampleMap(data)));
    }

    /* ***************************** *
     * ******** SAMPLE MAPS ******** *
     * ***************************** */

    public static byte[] serializeSampleMap(Map<String, NodeSample> aggregatedData) {
        ByteBuf out = Unpooled.buffer();
        out.writeInt(aggregatedData.size());
        for(String h : aggregatedData.keySet()){
            byte[] strBytes = h.getBytes(StandardCharsets.UTF_8);
            out.writeInt(strBytes.length);
            out.writeBytes(strBytes);
            byte[] arr = aggregatedData.get(h).toByteArray();
            out.writeInt(arr.length);
            out.writeBytes(arr);
        }
        return out.array();
    }

    public static Map<String, NodeSample> deserializeSampleMap(byte[] b) {
        ByteBuf in = Unpooled.wrappedBuffer(b);
        Map<String, NodeSample> map = new HashMap<>();
        int size = in.readInt();
        for(int i = 0; i < size; i++){
            byte[] stringBytes = new byte[in.readInt()];
            in.readBytes(stringBytes);
            String h    = new String(stringBytes, StandardCharsets.UTF_8);
            byte[] bytes = new byte[in.readInt()];
            in.readBytes(bytes);
            NodeSample data = NodeSample.fromByteArray(bytes);
            map.put(h, data);
        }
        return map;
    }
}
