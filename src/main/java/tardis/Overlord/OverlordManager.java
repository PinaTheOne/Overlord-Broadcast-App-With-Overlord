package tardis.Overlord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.Reconfigure;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.metrics.monitor.Monitor;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.notifications.BroadcastDelivery;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.CollectDataNotification;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.CollectNotification;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.ReceiveAggregatedDataNotification;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.requests.AggregateDataRequest;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.requests.MonitorDataRequest;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.requests.MonitorRequest;
import pt.unl.fct.di.novasys.babel.utils.recordexporter.utils.ExportRecordNotification;
import pt.unl.fct.di.novasys.babel.utils.recordexporter.utils.ReceiveRecord;
import pt.unl.fct.di.novasys.network.data.Host;
import tardis.Overlord.adaptation.RuleEngine;
import tardis.Overlord.adaptation.requests.EvaluateConditionsRequest;
import tardis.Overlord.adaptation.requests.RegisterRuleRequest;
import tardis.Overlord.adaptation.rules.HandleLatencyRule;
import tardis.Overlord.timers.GetMetricsTimer;
import tardis.Overlord.utils.AggregatedStatistics;
import tardis.Overlord.utils.MessageStatistics;
import tardis.Overlord.utils.ReconfigurationsContainer;

import java.io.IOException;
import java.util.*;

public class OverlordManager extends Monitor {

    public final static String PAR_METRIC_COLLECT_PERIOD = "Overlord.MetricCollectPeriod";
    public final static long DEFAULT_METRIC_COLLECT_PERIOD = 60 * 1000; // 10 seconds;

    public static final String PROTO_NAME = "OverlordManager";
    public static final short PROTO_ID = 1100;
    public final static long DEFAULT_MESSAGE_VALIDITY_TIME_MS =  30 * 1000; // 30 seconds
    private final short moncollectProtoId;
    private final short broadcastProtocolID;
    @SuppressWarnings({"unused", "FieldCanBeLocal"}) // TODO: Check
    private final Host myself;
    @SuppressWarnings("FieldCanBeLocal") // TODO: Check
    private long metricCollectionPeriod;
    @SuppressWarnings("FieldCanBeLocal") // TODO: Check
    private boolean overlord;
    private LinkedList<MessageStatistics> timeline;
    private HashMap<UUID, MessageStatistics> stats;
    public long messageValidity;

    /* Statistics */

    public AggregatedStatistics statistics;

    public static final Logger logger = LogManager.getLogger(OverlordManager.class);

    public OverlordManager(Host myself, short moncollectProtoId, short broadcastProtocolID) {
        super(PROTO_NAME, PROTO_ID);
        this.myself = myself;
        this.moncollectProtoId = moncollectProtoId;
        this.broadcastProtocolID = broadcastProtocolID;
        this.messageValidity = DEFAULT_MESSAGE_VALIDITY_TIME_MS; // TODO: Make parameter
        this.statistics = new AggregatedStatistics(0,0);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException {

        if(props.containsKey(PAR_METRIC_COLLECT_PERIOD))
            this.metricCollectionPeriod = Long.parseLong(props.getProperty(PAR_METRIC_COLLECT_PERIOD));
        else
            this.metricCollectionPeriod = DEFAULT_METRIC_COLLECT_PERIOD;

        if(props.containsKey("Overlord"))
            this.overlord = Boolean.parseBoolean(props.getProperty("Overlord"));
        else
            this.overlord = false;

        this.timeline = new LinkedList<>();
        this.stats = new HashMap<>();

        if(overlord) {
            registerTimerHandler(GetMetricsTimer.TIMER_ID, this::uponGetMetricsTimer);
            setupPeriodicTimer(new GetMetricsTimer(), this.metricCollectionPeriod, this.metricCollectionPeriod);
        }


        registerRequestHandler(MonitorDataRequest.REQUEST_ID, this::uponMonitorDataRequest);
        registerRequestHandler(AggregateDataRequest.REQUEST_ID, this::uponAggregateDataRequest);

        subscribeNotification(CollectNotification.NOTIFICATION_ID, this::uponCollectNotification);
        subscribeNotification(ExportRecordNotification.ID, this::uponExportRecordNotificaiton);

        // For Reconfiguration of Fanout
        subscribeNotification(BroadcastDelivery.NOTIFICATION_ID, this::uponBroadcastDelivery);

        /*---------------------------------
         * -------- Register Rules --------
         *---------------------------------*/
        if(overlord) {
            logger.debug("Sending Request for Registering new rules to Rule Engine");
            HandleLatencyRule rule = new HandleLatencyRule(5, this.broadcastProtocolID);
            sendRequest(new RegisterRuleRequest(rule), RuleEngine.PROTO_ID);
        }

    }

    /*---------------------------------
     * ------- Fanout Adaptation ------
     *---------------------------------*/

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
            logger.debug("Delivered Message is a Reconfiguration List, proceding with the reconfigurations");
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

    /*------------------------------------------------------
     * -------- Metrics and MON-Collect Interaction --------
     *------------------------------------------------------*/

    /**
     * Executed upon delivery of a message, essentially containing metrics of that message.
     * @param notif The notification containing the record
     * @param protoId The ID of the protocol that sent it
     */
    private void uponExportRecordNotificaiton(ExportRecordNotification notif, short protoId) {
        ReceiveRecord record = notif.getRecord();
        UUID mID = record.getMessageId();

        if (!this.stats.containsKey(mID)) {
            long creationTime = record.getTimestampSent();
            long receivedTime = record.getTimestampRecv();
            int hopCount = record.getHopCount();
            MessageStatistics ms = new MessageStatistics(mID, creationTime, receivedTime, hopCount,
                    record.getNode());
            stats.put(mID, ms);
            timeline.add(ms);
            // Sorted by creation time (if the first message in the
            // timeline isn't mature, others aren't either)
            timeline.sort(Comparator.comparingLong(MessageStatistics::getCreationTime));
        } else {
            long receivedTime = record.getTimestampRecv();
            int hopCount = record.getHopCount();
            MessageStatistics ms = this.stats.get(mID);
            ms.updateStatistics(receivedTime, hopCount, record.getNode());
        }
    }

    private boolean isMature(MessageStatistics message) {
        return message.getCreationTime() + (2 * messageValidity) <= System.currentTimeMillis();
    }

    private void uponMonitorDataRequest(MonitorDataRequest req, short i) {
            // If there are no messages or the first message is not yet mature, return
            if (this.timeline.isEmpty() || !isMature(this.timeline.peek())) {
                logger.debug("No messages are stable yet ({} entries in queue).", this.timeline.size());
                if (!this.timeline.isEmpty()) {
                    logger.debug("{} seconds until first report", ((this.timeline.peek().getCreationTime() + (2 * messageValidity)) - System.currentTimeMillis()) / 1000);
                }
                return;
            }

            float reliabilityAcc = 0;
            float latencyAcc = 0;
            int hopAcc = 0;
            int msgCount = 0;
            int receivedMessages = 0;
            int duplicateMessages = 0;
            float rmrAcc = 0;
            int nodeCount = 0;

        assert this.timeline.peek() != null;
        long start = this.timeline.peek().getCreationTime();
        assert this.timeline.peek() != null;
        long end = this.timeline.peek().getCreationTime();

			/* While there are mature messages, process them
			/* 	Quick Reminder:
			/* 		queue.peek() -> Retrieves, but does not remove, the head
			/* 		queue.poll() -> Retrieves and removes the head
			 */
            while (!this.timeline.isEmpty() && isMature(this.timeline.peek())) {
                MessageStatistics s = timeline.poll();

                assert s != null;
                end = s.getCreationTime();

                // TODO: O que é o membership info?
                //while (this.membershipInfo.size() > 0
                //        && s.getCreationTime() > this.membershipInfo.getFirst().getTimestamp()) {
                //    this.currentWindow = this.membershipInfo.pollFirst();
                //}

                // Establishes the reliability (nDelivered/nTotal)
                //s.computeReliability(1);

                // Computer average of these messages
                msgCount++;

                // TODO: What is happening here?
                if (s.getDeliveryCount() > 1)
                    rmrAcc += (float) s.getReceiveCount() / (s.getDeliveryCount() - 1) - 1;

                reliabilityAcc += s.getReliability();
                latencyAcc += s.getLatency();
                hopAcc += s.getHighestHop();
                receivedMessages += s.getReceiveCount();

                // How many times was this message duplicated?
                duplicateMessages += s.getReceiveCount() - s.getDeliveryCount();

                nodeCount = 1;

                this.stats.remove(s.getMsgID());

            }

            assert msgCount > 0;

            float avgLatency = latencyAcc / msgCount;
            float avgReliability = reliabilityAcc / msgCount;
            float averageHops = (float) hopAcc / msgCount;
            float averageRMR = rmrAcc / msgCount;

            AggregatedStatistics aggregatedStats = new AggregatedStatistics(start, end,
                    /*this.currentWindow*/1, avgLatency, avgReliability, averageHops, averageRMR, receivedMessages,
                    duplicateMessages, /*0,*/ msgCount, nodeCount);
            triggerNotification(new CollectDataNotification(AggregatedStatistics.toByteArray(aggregatedStats)));
    }

    private void uponAggregateDataRequest(AggregateDataRequest req, short protoID) {
        Set<AggregatedStatistics> aggregatedStats = new HashSet<>();
        for( byte[] b : req.getData() ){
            try {
                aggregatedStats.add(AggregatedStatistics.deserialize((b)));
            } catch (Exception e){
                logger.error("Couldn't deserialize data");
                logger.error(e.getStackTrace());
                System.exit(-1);
            }
        }

        float reliabilityAcc = 0;
        float latencyAcc = 0;
        int hopAcc = 0;
        int msgCount = 0;
        int receivedMessages = 0;
        int duplicateMessages = 0;
        @SuppressWarnings("unused") // TODO: Check
        int sentMessages = 0;
        float rmrAcc = 0;
        int nodeCount = 0;

        long start = Long.MAX_VALUE;
        long end = 0;

        for(AggregatedStatistics s : aggregatedStats){

            // TODO: O que é o membership info?
            //while (this.membershipInfo.size() > 0
            //        && s.getCreationTime() > this.membershipInfo.getFirst().getTimestamp()) {
            //    this.currentWindow = this.membershipInfo.pollFirst();
            //}

            // Establishes the reliability (nDelivered/nTotal)
            //s.computeReliability(1);

            // Compute Start and End TODO: Check
            start = Math.min(start, s.getStart());
            end = Math.max(end, s.getEnd());

            // Computer average of these messages

            msgCount+= s.getMsgCount();

            // TODO: What is happening here?
            rmrAcc += (float) s.getAverageRMR();

            reliabilityAcc += (float) s.getAverageReliability();
            latencyAcc += (float) s.getAverageLatency();
            hopAcc += (int) s.getAverageHops();
            receivedMessages += s.getReceivedMessages();
            //sentMessages += s.getSentMessages();
            nodeCount += s.getNodeCount();

            // How many times was this message duplicated?
            duplicateMessages += s.getDuplicateMessages();
        }

        float avgLatency;
        float avgReliability;
        float averageHops;
        float averageRMR;

        if(msgCount == 0){
            avgLatency = 0;
            avgReliability = 0;
            averageHops = (float) 0;
            averageRMR = 0;
        } else {
            avgLatency = latencyAcc / msgCount;
            avgReliability = reliabilityAcc / msgCount;
            averageHops = (float) hopAcc / msgCount;
            averageRMR = rmrAcc / msgCount;
        }

        AggregatedStatistics finalAggregatedStats = new AggregatedStatistics(start, end,
                /*this.currentWindow*/1, avgLatency, avgReliability, averageHops, averageRMR, receivedMessages,
                duplicateMessages, /*sentMessages,*/ msgCount, nodeCount);

        triggerNotification(new ReceiveAggregatedDataNotification(AggregatedStatistics.toByteArray(finalAggregatedStats)));
    }

    private void uponGetMetricsTimer(GetMetricsTimer timer, long timerId) {
        sendRequest(new MonitorRequest(), moncollectProtoId);
    }

    private void uponCollectNotification(CollectNotification notif, short protoId) {
        // TODO: REDO THIS PLEASE
        byte[] b = notif.getData();
        try{
            AggregatedStatistics s = AggregatedStatistics.deserialize(b);
            statistics.addNewStats(s);
            logger.info(statistics.toString());
            sendRequest(new EvaluateConditionsRequest(statistics), RuleEngine.PROTO_ID);
        } catch (Exception e){
            logger.error("Couldn't deserialize data");
            logger.error(e.getStackTrace());
            System.exit(-1);
        }
    }

}
