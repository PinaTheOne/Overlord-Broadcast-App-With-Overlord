package tardis.Overlord.utils.aggregators;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.metrics.MetricSample;
import pt.unl.fct.di.novasys.babel.metrics.Record;
import pt.unl.fct.di.novasys.babel.metrics.Sample;
import pt.unl.fct.di.novasys.babel.metrics.monitor.*;
import tardis.Overlord.OverlordManager;

import java.util.HashMap;
import java.util.Map;

import static tardis.Overlord.utils.DataStructSerializer.mapOfRecordsFromString;
import static tardis.Overlord.utils.aggregators.NodeAggregator.*;

public class CollectAggregator extends Aggregation {

    public static final Logger logger = LogManager.getLogger(AggregationManager.class);
    private int nodeCount;
    private int sentMessagesAccumulator;
    private int receivedMessagesAccumulator;
    private int duplicatedMessagesAccumulator;
    private int deliveredMessagesAccumulator;
    private Map<String, MessageRecord> messageRecords;
    public final static String AGGREGATED_METRICS = "AggregatedMetrics";
    public final static String SENT_MESSAGES = "SentMessages";
    public final static String RECEIVED_MESSAGES = "ReceivedMessages";
    public final static String DUPLICATE_MESSAGES = "DuplicateMessages";
    public static final String DELIVERED_MESSAGES = "DeliveredMessages";
    public final static String NODE_COUNT = "NodeCount";
    public final static String AVERAGE_LATENCY = "AverageLatency";
    public final static String AVERAGE_RELIABILITY = "AverageReliability";
    public final static String AVERAGE_RMR = "AverageRMR";
    public final static String AVERAGE_HOP_COUNT = "AverageHopCount";

    private static final MetricIdentifier[] metricsToAggregate = new MetricIdentifier[] {
            new MetricIdentifier(InNetworkAggregator.IN_NETWORK_AGGREGATED_METRICS, OverlordManager.PROTO_ID)
    };

    public CollectAggregator(){
        super(metricsToAggregate);
        resetDataStructs();
    }

    private void resetDataStructs() {
        this.nodeCount = 0;
        this.sentMessagesAccumulator = 0;
        this.receivedMessagesAccumulator = 0;
        this.duplicatedMessagesAccumulator = 0;
        this.deliveredMessagesAccumulator = 0;
        this.messageRecords = new HashMap<>();
    }



    @Override
    public AggregationResult aggregate(AggregationInput ai, AggregationResult ar) {
        resetDataStructs();
        for(MetricSample s : ai.getSamples(OverlordManager.PROTO_ID, InNetworkAggregator.IN_NETWORK_AGGREGATED_METRICS))
            this.addNode(s);
        if(this.nodeCount > 0)
            processStatistics(ar);

        return ar;
    }

    private void processStatistics(AggregationResult ar) {
        Record aggregatedMetrics = getAggregatedMetricsRecord();

        double reliability;
        if(nodeCount*sentMessagesAccumulator == 0)
            reliability = 0;
        else
            reliability = (double) deliveredMessagesAccumulator / (nodeCount * sentMessagesAccumulator);


        long latencyAccumulator = 0;
        int messageCount = 0;
        int hopCountAccumulator = 0;
        for(String s : messageRecords.keySet()){
            if(!messageRecords.containsKey(s) && messageRecords.get(s).hasNoCreationValue())
                logger.error("Message "+s+" was received but was not created. Ignoring it...");
            else{
                latencyAccumulator += (messageRecords.get(s).getReceptionTime()-messageRecords.get(s).getCreationTime());
                hopCountAccumulator += messageRecords.get(s).getHopCount();
                messageCount++;
            }
        }
        long latency;
        long hopCount;
        float rmr;
        if(messageCount !=0 ) {
            latency = latencyAccumulator / messageCount;
            hopCount = hopCountAccumulator / messageCount;
            if(deliveredMessagesAccumulator != 0)
                rmr = ((float) receivedMessagesAccumulator/deliveredMessagesAccumulator)/messageCount;
            else
                rmr = 0;
        } else {
            latency = 0;
            hopCount = 0;
            rmr = 0;
        }

        aggregatedMetrics.record(
                String.valueOf(sentMessagesAccumulator),
                String.valueOf(receivedMessagesAccumulator),
                String.valueOf(duplicatedMessagesAccumulator),
                String.valueOf(deliveredMessagesAccumulator),
                String.valueOf(nodeCount),
                String.valueOf(latency),
                String.valueOf(reliability),
                String.valueOf(rmr),
                String.valueOf(hopCount)
        );

        logger.info("Collected Total Sent Messages: {}", sentMessagesAccumulator);
        logger.info("Collected Total Received Messages: {}", receivedMessagesAccumulator);
        logger.info("Collected Total Duplicated Messages: {}", duplicatedMessagesAccumulator);
        logger.info("Collected Total Delivered Messages: {}", deliveredMessagesAccumulator);
        logger.info("Collected Node Count: {}", nodeCount);
        logger.info("Collected Average Latency: {} ms", latency);
        logger.info("Collected Average Reliability: {}", reliability);
        logger.info("Collected Average rmr: {}", rmr);
        logger.info("Collected Average Hop Count: {}", hopCount);
        ar.addGlobalMetricToSample(aggregatedMetrics, OverlordManager.PROTO_ID);
    }

    private Record getAggregatedMetricsRecord() {
        return new Record.Builder(AGGREGATED_METRICS,
                SENT_MESSAGES,
                RECEIVED_MESSAGES,
                DUPLICATE_MESSAGES,
                DELIVERED_MESSAGES,
                NODE_COUNT,
                AVERAGE_LATENCY,
                AVERAGE_RELIABILITY,
                AVERAGE_RMR,
                AVERAGE_HOP_COUNT
        ).description("Has all the metrics already aggregated ready to be used for evaluation")
                .build();
    }

    private void addNode(MetricSample metricSample) {
        String mName = metricSample.getMetricName();
        if (mName.equals(InNetworkAggregator.IN_NETWORK_AGGREGATED_METRICS)) {
            for (Sample s : metricSample.getSamples()) {
                Map<String, String> v = s.getLabels();
                int count = Integer.parseInt(v.get(NODE_COUNT));
                this.nodeCount += count;
                this.sentMessagesAccumulator += Integer.parseInt(v.get(SENT_MESSAGES));
                this.receivedMessagesAccumulator += Integer.parseInt(v.get(RECEIVED_MESSAGES));
                this.duplicatedMessagesAccumulator += Integer.parseInt(v.get(DUPLICATE_MESSAGES));
                this.deliveredMessagesAccumulator += Integer.parseInt(v.get(DELIVERED_MESSAGES));
                this.messageRecords = MessageRecord.joinRecordMaps(messageRecords, mapOfRecordsFromString(v.get(InNetworkAggregator.MESSAGE_RECORDS)));
            }
        }
    }
}
