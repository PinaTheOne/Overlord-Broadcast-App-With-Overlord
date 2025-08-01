package tardis.Overlord.utils.aggregators;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.metrics.*;
import pt.unl.fct.di.novasys.babel.metrics.Record;
import pt.unl.fct.di.novasys.babel.metrics.monitor.*;
import pt.unl.fct.di.novasys.babel.protocols.eagerpush.AdaptiveEagerPushGossipBroadcast;
import tardis.Overlord.OverlordManager;

import java.util.*;

import static tardis.Overlord.utils.DataStructSerializer.mapOfRecordsToString;

/**
 * This class is responsible for aggregation of metrics in one node. it essentially takes in as input
 * records that come from the broadcast protocol, a counter for counting the number of nodes, and
 * returns a single record of AggregatedMetrics.
 */
public class NodeAggregator extends Aggregation {


    // Useful Data Structures
    int nodeCount;
    Map<String, MessageRecord> messageRecords;
    Map<String, List<UUID>> sentMessagesIDs;
    Map<String, Long> sentCreationTimes;
    Map<String, List<UUID>> deliveredMessagesIDs;
    Map<String, Long> deliveryTimes;
    List<UUID> receivedMessagesIDs;
    List<UUID> duplicateMessageIDs;

    // Parameters
    private final long messageValidity;
    String myself;

    // Metrics Names
    public final static String SENT_MESSAGES_RECORD = AdaptiveEagerPushGossipBroadcast.SENT_MESSAGES_RECORD;
    public final static String RECEIVE_MESSAGES_RECORD = AdaptiveEagerPushGossipBroadcast.RECEIVE_MESSAGES_RECORD;
    public final static String DELIVERED_MESSAGES_RECORD  = AdaptiveEagerPushGossipBroadcast.DELIVERED_MESSAGES_RECORD;
    public final static short BCAST_PROTO_ID = AdaptiveEagerPushGossipBroadcast.PROTOCOL_ID;
    public static final Logger logger = LogManager.getLogger(AggregationManager.class);

    private static final MetricIdentifier[] metricsToAggregate = new MetricIdentifier[] {
            new MetricIdentifier(SENT_MESSAGES_RECORD, BCAST_PROTO_ID),
            new MetricIdentifier(RECEIVE_MESSAGES_RECORD, BCAST_PROTO_ID),
            new MetricIdentifier(DELIVERED_MESSAGES_RECORD, BCAST_PROTO_ID),
    };

    public NodeAggregator(String myself, long messageValidity){
        super(metricsToAggregate);
        resetDataStructs();
        this.myself = myself;
        this.messageValidity = messageValidity;
    }

    @SuppressWarnings("unused")
    public boolean isMature(long time) {
        return time + (2 * messageValidity) <= System.currentTimeMillis();
    }

    @Override
    public AggregationResult aggregate(AggregationInput aggregationInput, AggregationResult aggregationResult) {
        resetDataStructs();
        for(MetricSample s : aggregationInput.getSamples(BCAST_PROTO_ID, SENT_MESSAGES_RECORD))
            this.addNode(s);
        for(MetricSample s : aggregationInput.getSamples(BCAST_PROTO_ID, RECEIVE_MESSAGES_RECORD))
            this.addNode(s);
        for(MetricSample s : aggregationInput.getSamples(BCAST_PROTO_ID, DELIVERED_MESSAGES_RECORD))
            this.addNode(s);

        if(nodeCount > 0)
            processStatistics(aggregationResult);

        return aggregationResult;
    }

    private void addNode(MetricSample metricSample){
        String mName = metricSample.getMetricName();
        switch (mName) {
            case SENT_MESSAGES_RECORD -> {
                for (Sample s : metricSample.getSamples()) {
                    Map<String, String> m = s.getLabels();
                    if(sentCreationTimes.containsKey(m.get("message_id"))){
                        System.err.println("ERROR: Message was sent twice! Aborting");
                        System.exit(-1);
                    }
                    sentCreationTimes.put(m.get("message_id"), Long.parseLong(m.get("timestamp")));
                    sentMessagesIDs.computeIfAbsent(m.get("node"), k -> new ArrayList<>());
                    sentMessagesIDs.get(m.get("node")).add(UUID.fromString(m.get("message_id")));

                    MessageRecord mr = this.messageRecords.get(m.get("message_id"));
                    if(mr != null){
                        System.err.println("ERROR: Same Message Created Twice, aborting...");
                        System.exit(-1);
                    }
                    mr = new MessageRecord(m.get("message_id"));
                    mr.newCreationTime(Long.parseLong(m.get("timestamp")));
                    messageRecords.put(m.get("message_id"), mr);
                }
                nodeCount=1;
            } case DELIVERED_MESSAGES_RECORD -> {
                for (Sample s : metricSample.getSamples()) {
                    Map<String, String> m = s.getLabels();

                    if(deliveryTimes.containsKey(m.get("message_id")))
                        deliveryTimes.put(m.get("message_id"), Long.max(Long.parseLong(m.get("timestamp")), deliveryTimes.get("message_id")));
                    else
                        deliveryTimes.put(m.get("message_id"), Long.parseLong(m.get("timestamp")));

                    deliveredMessagesIDs.computeIfAbsent(m.get("node"), k -> new ArrayList<>());
                    deliveredMessagesIDs.get(m.get("node")).add(UUID.fromString(m.get("message_id")));
                    MessageRecord mr = this.messageRecords.get(m.get("message_id"));
                    if(mr == null){
                        mr = new MessageRecord(m.get("message_id"));
                    }
                    mr.newReceptionTime(Long.parseLong(m.get("timestamp")));
                    mr.newHopCount(Integer.parseInt(m.get("hop_count")));
                    messageRecords.put(m.get("message_id"), mr);
                }
                nodeCount=1;
            } case RECEIVE_MESSAGES_RECORD -> {
                for (Sample s : metricSample.getSamples()) {
                    Map<String, String> m = s.getLabels();
                    receivedMessagesIDs.add(UUID.fromString(m.get("message_id")));
                }
                nodeCount=1;
            } default -> System.out.printf("Ignored Metric: %s\n", metricSample.getMetricName());
        }
    }

    private void processStatistics(AggregationResult ar){

        Record aggregatedMetrics = InNetworkAggregator.getAggregatedMetricsRecord();
        int sentMessages = 0;
        if(sentMessagesIDs.get(myself) != null) {
            sentMessages = sentMessagesIDs.get(myself).size();
        }
        int deliveredMessages = 0;
        if(deliveredMessagesIDs.get(myself) != null) {
            deliveredMessages = deliveredMessagesIDs.get(myself).size();
        }

        aggregatedMetrics.record(
                mapOfRecordsToString(messageRecords),
                String.valueOf(sentMessages),
                String.valueOf(receivedMessagesIDs.size()),
                String.valueOf(receivedMessagesIDs.size() - deliveredMessages),
                String.valueOf(deliveredMessages),
                String.valueOf(nodeCount));

        ar.addGlobalMetricToSample(aggregatedMetrics, OverlordManager.PROTO_ID);

    }

    public static class MessageRecord {

        private final String messageID;
        private long creationTimestamp;
        private long receivedTimeStamp;
        private int hopCount;

        public MessageRecord(String messageID, long creationTimestamp, long receivedTimeStamp, int hopCount) {
            this.messageID = messageID;
            this.creationTimestamp = creationTimestamp;
            this.receivedTimeStamp = receivedTimeStamp;
            this.hopCount = hopCount;
        }

        public MessageRecord(String messageID) {
            this.messageID = messageID;
            this.creationTimestamp = -1;
            this.receivedTimeStamp = -1;
            this.hopCount = -1;
        }

        public String getMID(){return this.messageID;}
        public long getCreationTime(){return this.creationTimestamp;}
        public long getReceptionTime(){return this.receivedTimeStamp;}
        public int getHopCount(){return this.hopCount;}

        public void newReceptionTime(long newReceptionTime){
            if(this.receivedTimeStamp < newReceptionTime)
                this.receivedTimeStamp = newReceptionTime;
        }

        public void newCreationTime(long newCreationTime){
            this.creationTimestamp = newCreationTime;
        }

        public void newHopCount(int newHopCount){
            if(this.hopCount < newHopCount)
                this.hopCount = newHopCount;
        }

        @SuppressWarnings("unused")
        public boolean hasNoReceivedValue(){
            return this.receivedTimeStamp == -1;
        }

        public boolean hasNoCreationValue(){
            return this.creationTimestamp == -1;
        }

        public String toString(){
            return messageID + "," + creationTimestamp + "," + receivedTimeStamp + "," + hopCount;
        }

        public static MessageRecord fromString(String str){
            List<String> list = List.of(str.split(","));
            if(list.size() != 4) {
                System.err.println("Can't make a Message Record out of " + str + " because the number of arguments is wrong");
                return null;
            } else {
                return new MessageRecord(list.get(0), Long.parseLong(list.get(1)), Long.parseLong(list.get(2)), Integer.parseInt(list.get(3)));
            }
        }

        public static Map<String, MessageRecord> joinRecordMaps(Map<String, MessageRecord> oldMap, Map<String, MessageRecord> newMap) {
            for(String s : newMap.keySet()){
                if(!oldMap.containsKey(s))
                    oldMap.put(s, newMap.get(s));
                else
                    oldMap.put(s, MessageRecord.joinRecords(oldMap.get(s), newMap.get(s)));
            }
            return oldMap;
        }

        public static MessageRecord joinRecords(MessageRecord oldR, MessageRecord newR) {
            if(newR.getReceptionTime() > oldR.getReceptionTime())
                oldR.newReceptionTime(newR.getReceptionTime());
            if(oldR.getCreationTime() != newR.getCreationTime() && !oldR.hasNoCreationValue() && !newR.hasNoCreationValue())
                System.err.println("ERROR!!! Same message has more than one creation time");
            if(oldR.hasNoCreationValue() && !newR.hasNoCreationValue())
                oldR.newCreationTime(newR.getCreationTime());
            if(oldR.getHopCount() < newR.getHopCount())
                oldR.newHopCount(newR.getHopCount());
            return oldR;
        }

        @Override
        public int hashCode() {
            return this.messageID.hashCode();
        }
    }

    private void resetDataStructs(){
        this.sentCreationTimes = new HashMap<>();
        this.deliveryTimes = new HashMap<>();
        this.sentMessagesIDs = new HashMap<>();
        this.deliveredMessagesIDs = new HashMap<>();
        this.receivedMessagesIDs = new ArrayList<>();
        this.duplicateMessageIDs = new ArrayList<>();
        this.messageRecords = new HashMap<>();
        this.nodeCount = 0;
    }

}
