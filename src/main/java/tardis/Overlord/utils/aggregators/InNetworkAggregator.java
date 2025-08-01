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
import static tardis.Overlord.utils.DataStructSerializer.mapOfRecordsToString;
import static tardis.Overlord.utils.aggregators.NodeAggregator.*;

public class InNetworkAggregator extends Aggregation {


    public static final Logger logger = LogManager.getLogger(AggregationManager.class);
    public final static String IN_NETWORK_AGGREGATED_METRICS = "InNetworkAggregatedMetrics";
    public final static String SENT_MESSAGES = "SentMessages";
    public final static String RECEIVED_MESSAGES = "ReceivedMessages";
    public final static String DUPLICATE_MESSAGES = "DuplicateMessages";
    public final static String DELIVERED_MESSAGES = "DeliveredMessages";
    public final static String NODE_COUNT = "NodeCount";

    private static final MetricIdentifier[] metricsToAggregate = new MetricIdentifier[] {
            new MetricIdentifier(IN_NETWORK_AGGREGATED_METRICS, OverlordManager.PROTO_ID),
    };
    public static final String MESSAGE_RECORDS = "MessageRecords";
    private int nodeCount;
    private int sentMessagesAccumulator;
    private int receivedMessagesAccumulator;
    private int duplicatedMessagesAccumulator;
    private int deliveredMessagesAccumulator;
    private Map<String, MessageRecord> messageRecords;

    public InNetworkAggregator(){
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
        for(MetricSample s : ai.getSamples(OverlordManager.PROTO_ID, IN_NETWORK_AGGREGATED_METRICS))
            this.addNode(s);
        if(this.nodeCount > 0)
            processStatistics(ar);

        return ar;
    }

    private void processStatistics(AggregationResult ar) {
        Record aggregatedMetrics = getAggregatedMetricsRecord();

        aggregatedMetrics.record(
                mapOfRecordsToString(this.messageRecords),
                String.valueOf(sentMessagesAccumulator),
                String.valueOf(receivedMessagesAccumulator),
                String.valueOf(duplicatedMessagesAccumulator),
                String.valueOf(deliveredMessagesAccumulator),
                String.valueOf(nodeCount));
        logger.info("InNetwork Total Sent Messages: {}", sentMessagesAccumulator);
        logger.info("InNetwork Total Received Messages: {}", receivedMessagesAccumulator);
        logger.info("InNetwork Total Duplicated Messages: {}", duplicatedMessagesAccumulator);
        logger.info("InNetwork Total Delivered Messages: {}", deliveredMessagesAccumulator);
        logger.info("InNetwork Node Count: {}", nodeCount);

        ar.addGlobalMetricToSample(aggregatedMetrics, OverlordManager.PROTO_ID);
    }

    private void addNode(MetricSample metricSample) {
        String mName = metricSample.getMetricName();
        if (mName.equals(IN_NETWORK_AGGREGATED_METRICS)) {
            for (Sample s : metricSample.getSamples()) {
                Map<String, String> v = s.getLabels();
                int count = Integer.parseInt(v.get(NODE_COUNT));
                this.nodeCount += count;
                this.messageRecords = MessageRecord.joinRecordMaps(messageRecords, mapOfRecordsFromString(v.get(MESSAGE_RECORDS)));
                this.sentMessagesAccumulator += Integer.parseInt(v.get(SENT_MESSAGES));
                this.receivedMessagesAccumulator += Integer.parseInt(v.get(RECEIVED_MESSAGES));
                this.duplicatedMessagesAccumulator += Integer.parseInt(v.get(DUPLICATE_MESSAGES));
                this.deliveredMessagesAccumulator += Integer.parseInt(v.get(DELIVERED_MESSAGES));
            }
        } else
            System.out.printf("Ignored Metric: %s\n", metricSample.getMetricName());
    }

    public static Record getAggregatedMetricsRecord(){
        return new Record.Builder(IN_NETWORK_AGGREGATED_METRICS,
                MESSAGE_RECORDS,
                SENT_MESSAGES,
                RECEIVED_MESSAGES,
                DUPLICATE_MESSAGES,
                DELIVERED_MESSAGES,
                NODE_COUNT
        ).description("Has all the metrics already aggregated")
                .build();
    }
}
