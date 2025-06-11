package tardis.Overlord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.MonCollect;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.CollectDataNotification;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.CollectNotification;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.ReceiveAggregatedDataNotification;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.requests.AggregateDataRequest;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.requests.MonitorDataRequest;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.requests.MonitorRequest;
import pt.unl.fct.di.novasys.babel.utils.recordexporter.utils.ExportRecordNotification;
import pt.unl.fct.di.novasys.babel.utils.recordexporter.utils.ReceiveRecord;
import tardis.Overlord.timers.GetMetricsTimer;
import tardis.Overlord.utils.AggregatedStatistics;
import tardis.Overlord.utils.MessageStatistics;

import java.util.*;

public class OverlordManager extends GenericProtocol {

    public final static String PAR_METRIC_COLLECT_PERIOD = "Overlord.MetricCollectPeriod";
    public final static long DEFAULT_METRIC_COLLECT_PERIOD = 60 * 1000; // 10 seconds;

    public static final String PROTO_NAME = "OverlordManager";
    public static final short PROTO_ID = 1100;
    public final static long DEFAULT_MESSAGE_VALIDITY_TIME_MS =  30 * 1000; // 30 seconds
    private final short moncollectProtoId;
    private long metricCollectionPeriod;
    private boolean overlord;
    private LinkedList<MessageStatistics> timeline;
    private HashMap<UUID, MessageStatistics> stats;
    public long messageValidity;

    /* Statistics */

    AggregatedStatistics statistics;

    private static final Logger logger = LogManager.getLogger(OverlordManager.class);
    public OverlordManager(short moncollectProtoId) {
        super(PROTO_NAME, PROTO_ID);
        this.moncollectProtoId = moncollectProtoId;
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
    }

    public void beginMonitor(){
        if(this.overlord) {
            sendRequest(new MonitorRequest(), moncollectProtoId);
        }
    }

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

            long start = this.timeline.peek().getCreationTime();
            long end = this.timeline.peek().getCreationTime();

			/* While there are mature messages, process them
			/* 	Quick Reminder:
			/* 		queue.peek() -> Retrieves, but does not remove, the head
			/* 		queue.poll() -> Retrieves and removes the head
			 */
            while (!this.timeline.isEmpty() && isMature(this.timeline.peek())) {
                MessageStatistics s = timeline.poll();

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
        // TODO: Mudar isto
        Set<AggregatedStatistics> aggregatedStats = new HashSet<>();
        Iterator<byte[]> it = req.getData().iterator();
        while(it.hasNext()){
            try {
                byte[] next = it.next();
                aggregatedStats.add(AggregatedStatistics.deserialize((next)));
            } catch (Exception e){
                logger.error("Couldn't deserialize data");
                e.printStackTrace();
                System.exit(-1);
            }
        }

        float reliabilityAcc = 0;
        float latencyAcc = 0;
        int hopAcc = 0;
        int msgCount = 0;
        int receivedMessages = 0;
        int duplicateMessages = 0;
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

        assert msgCount > 0;
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
        //ReceiveRecord rcrd = (ReceiveRecord) notif.getData();
        // TODO: REDO THIS PLEASE
        byte[] b = notif.getData();
        try{
            AggregatedStatistics s = AggregatedStatistics.deserialize(b);
            statistics.addNewStats(s);
            logger.info(statistics.toString());
        } catch (Exception e){
            logger.error("Couldn't deserialize data");
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
