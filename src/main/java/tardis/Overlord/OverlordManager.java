package tardis.Overlord;

// Logger
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;
// Babel
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.Reconfigure;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.metrics.*;
import pt.unl.fct.di.novasys.babel.metrics.exporters.CollectOptions;
import pt.unl.fct.di.novasys.babel.metrics.exporters.ExporterCollectOptions;
import pt.unl.fct.di.novasys.babel.metrics.exporters.ProtocolCollectOptions;
import pt.unl.fct.di.novasys.babel.metrics.exporters.ProtocolExporterHelper;
import pt.unl.fct.di.novasys.babel.metrics.monitor.Monitor;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.notifications.BroadcastDelivery;
import pt.unl.fct.di.novasys.babel.protocols.eagerpush.AdaptiveEagerPushGossipBroadcast;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.CollectDataNotification;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.CollectNotification;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.ReceiveAggregatedDataNotification;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.requests.AggregateDataRequest;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.requests.MonitorDataRequest;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.requests.MonitorRequest;
import pt.unl.fct.di.novasys.network.data.Host;
// Overlord
import tardis.Overlord.adaptation.RuleEngine;
import tardis.Overlord.adaptation.requests.EvaluateConditionsRequest;
import tardis.Overlord.adaptation.requests.RegisterRuleRequest;
import tardis.Overlord.adaptation.rules.HandleLatencyRule;
import tardis.Overlord.timers.GetMetricsTimer;
import tardis.Overlord.utils.ReconfigurationsContainer;
import tardis.Overlord.utils.aggregators.CollectAggregator;
import tardis.Overlord.utils.aggregators.InNetworkAggregator;
import tardis.Overlord.utils.aggregators.NodeAggregator;
// Data Struct Serializers
import static tardis.Overlord.utils.DataStructSerializer.deserializeSampleMap;
import static tardis.Overlord.utils.DataStructSerializer.serializeSampleMap;
// Java IO
import java.io.IOException;
// Java Util
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class OverlordManager extends Monitor {

    // Protocol Information
    public static final String PROTO_NAME = "OverlordManager";
    public static final short PROTO_ID = 1100;

    // Parameters Identifiers and Default Values

    public final static String PAR_COLLECT_PERIOD = "Overlord.CollectPeriodMs";
    public final static long DEFAULT_COLLECT_PERIOD = 10 * 60 * 1000; // 10 Minutes;
    public final static String PAR_MESSAGE_VALIDITY_TIME_MS = "Metrics.MessageValidityTimeMs";
    public final static long DEFAULT_MESSAGE_VALIDITY_TIME_MS =  30 * 1000; // 30 seconds
    public static final String IS_OVERLORD = "Overlord.IsOverlord";

    // Metrics
    private final Counter nodeCount;
    public final static String NODE_COUNTER = "NodeCounter";

    private final ProtocolExporterHelper protocolExporterHelper;
    private final short moncollectProtoId;
    private final short broadcastProtocolID;
    @SuppressWarnings({"unused", "FieldCanBeLocal"}) // TODO: Check
    private final Host myself;
    @SuppressWarnings("FieldCanBeLocal") // TODO: Check
    private long metricCollectionPeriod;
    @SuppressWarnings("FieldCanBeLocal") // TODO: Check
    private boolean overlord;
    public long messageValidity;

    public static final Logger logger = LogManager.getLogger(OverlordManager.class);

    public OverlordManager(Host myself, short moncollectProtoId, short broadcastProtocolID) {
        super(PROTO_NAME, PROTO_ID);
        this.myself = myself;
        this.moncollectProtoId = moncollectProtoId;
        this.broadcastProtocolID = broadcastProtocolID;

        // Metrics Initialization
        Map<Short, ProtocolCollectOptions> exporterOptions = getExporterOptions();
        this.protocolExporterHelper = new ProtocolExporterHelper.Builder("exporter").exporterCollectOptions(ExporterCollectOptions.builder().perProtocolCollectOptions(exporterOptions).build()).build();
        this.nodeCount = registerMetric(new Counter.Builder(NODE_COUNTER, Metric.Unit.NONE).build());
    }

    private Map<Short, ProtocolCollectOptions> getExporterOptions() {
        Map<Short, ProtocolCollectOptions> ops = new HashMap<>();

        // Options for Overlord Metrics (specifically, the node counter and the already aggregated Metrics)
        ProtocolCollectOptions overlordOptions = new ProtocolCollectOptions();
        overlordOptions.addCollectOptions(InNetworkAggregator.IN_NETWORK_AGGREGATED_METRICS, new CollectOptions(true));
        overlordOptions.addCollectOptions(CollectAggregator.AGGREGATED_METRICS, new CollectOptions(true));
        overlordOptions.addCollectOptions(OverlordManager.NODE_COUNTER, new CollectOptions(true));
        ops.put(OverlordManager.PROTO_ID, overlordOptions);

        // Options for Broadcast Metrics (specifically, the message counters)
        ProtocolCollectOptions broadcastOptions = new ProtocolCollectOptions();
        broadcastOptions.addCollectOptions(NodeAggregator.SENT_MESSAGES_RECORD, new CollectOptions(true));
        broadcastOptions.addCollectOptions(NodeAggregator.RECEIVE_MESSAGES_RECORD, new CollectOptions(true));
        broadcastOptions.addCollectOptions(NodeAggregator.DELIVERED_MESSAGES_RECORD, new CollectOptions(true));
        ops.put(AdaptiveEagerPushGossipBroadcast.PROTOCOL_ID, broadcastOptions);
        return ops;
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {

        if(props.containsKey(PAR_COLLECT_PERIOD))
            this.metricCollectionPeriod = Long.parseLong(props.getProperty(PAR_COLLECT_PERIOD));
        else
            this.metricCollectionPeriod = DEFAULT_COLLECT_PERIOD;

        if(props.containsKey(PAR_MESSAGE_VALIDITY_TIME_MS))
            this.messageValidity = Long.parseLong(props.getProperty(PAR_MESSAGE_VALIDITY_TIME_MS));
        else
            this.messageValidity = DEFAULT_MESSAGE_VALIDITY_TIME_MS;

        if(props.containsKey(IS_OVERLORD))
            this.overlord = Boolean.parseBoolean(props.getProperty(IS_OVERLORD));
        else
            this.overlord = false;

        if(overlord) {
            registerTimerHandler(GetMetricsTimer.TIMER_ID, this::uponGetMetricsTimer);
            setupPeriodicTimer(new GetMetricsTimer(), this.metricCollectionPeriod, this.metricCollectionPeriod);
        }


        registerRequestHandler(MonitorDataRequest.REQUEST_ID, this::uponMonitorDataRequest);
        registerRequestHandler(AggregateDataRequest.REQUEST_ID, this::uponAggregateDataRequest);

        subscribeNotification(CollectNotification.NOTIFICATION_ID, this::uponCollectNotification);

        // For Reconfiguration of Fanout
        subscribeNotification(BroadcastDelivery.NOTIFICATION_ID, this::uponBroadcastDelivery);

        /* ********************* *
         * *** REGISTER RULES ** *
         * ********************* */
        if(overlord) {
            logger.debug("Sending Request for Registering new rules to Rule Engine");
            HandleLatencyRule rule = new HandleLatencyRule(20, 4, this.broadcastProtocolID);
            sendRequest(new RegisterRuleRequest(rule), RuleEngine.PROTO_ID);
        }

        /* ********************* *
         * ****** METRICS ****** *
         * ********************* */

        this.addAggregation(new NodeAggregator(myself.toString().replace("5555", "5556"), messageValidity));
        if(this.overlord) {
            this.addAggregation(new CollectAggregator());
        }
        else
            this.addAggregation(new InNetworkAggregator() );

    }

    /* ******************************** *
     * ****** FANOUT ADAPTATION ****** *
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

    private void uponMonitorDataRequest(MonitorDataRequest req, short i) {
        this.nodeCount.inc();
        logger.info("Collecting all metrics.");
        NodeSample sample = this.protocolExporterHelper.collectAllMetrics();
        this.addSampleToAggregate(myself.toString(), sample);
        Map<String, NodeSample> aggregatedSamples = this.performAggregations();
        triggerNotification(new CollectDataNotification(serializeSampleMap(aggregatedSamples)));
    }

    private void uponAggregateDataRequest(AggregateDataRequest req, short protoID) {
        logger.info("Received Aggregation Request");
        int i = 0;

        List<byte[]> reqList = req.getData();
        for(byte[] b : reqList){
            Map<String, NodeSample> m = deserializeSampleMap(b);
            for(String s : m.keySet()) {
                if(s.equals(MetricsManager.GLOBAL_HOST_IDENTIFIER)) {
                    this.addSampleToAggregate(s + i++, m.get(s));
                } else {
                    this.addSampleToAggregate(s, m.get(s));
                }
            }
        }
        Map<String, NodeSample> aggregatedData = this.performAggregations();
        triggerNotification(new ReceiveAggregatedDataNotification(serializeSampleMap(aggregatedData)));
    }

    private void uponGetMetricsTimer(GetMetricsTimer timer, long timerId) {
        sendRequest(new MonitorRequest(), moncollectProtoId);
    }

    private void uponCollectNotification(CollectNotification notification, short protoId) {
        byte[] result = notification.getData();
        Map<String, NodeSample> samples = deserializeSampleMap(result);
        sendRequest(new EvaluateConditionsRequest(samples), RuleEngine.PROTO_ID);
    }

}
