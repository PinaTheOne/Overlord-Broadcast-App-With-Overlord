package tardis.Overlord.adaptation.rules;

import org.apache.logging.log4j.LogManager;
import org.javatuples.Pair;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.Reconfigure;
import pt.unl.fct.di.novasys.babel.metrics.*;
import tardis.Overlord.OverlordManager;
import tardis.Overlord.utils.aggregators.CollectAggregator;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HandleLatencyRule extends ProtoRule {

    private static final short RULE_ID = 1;
    private static final String RULE_NAME = "Handle Latency";
    public static final Logger logger = LogManager.getLogger("Rule-" + RULE_ID + "-" + RULE_NAME); // TODO: Make this automatic instead of User-made
    private static final String DESCRIPTION = "If latency is higher than %.2f, increase fanout to %d";
    private final int fanout;
    private final float maxLatency;
    private final short broadcastProtoId;

    public HandleLatencyRule(float maxLatency, int fanout, short broadcastProtoId) {
        super(RULE_ID, RULE_NAME, String.format(DESCRIPTION, maxLatency, fanout));
        this.fanout = fanout;
        this.maxLatency = maxLatency;
        this.broadcastProtoId = broadcastProtoId;
    }

    @Override
    public List<Pair<Reconfigure, Short>> evaluate(Map<String, NodeSample> samples) {
        List<Pair<Reconfigure, Short>> reconfigurations = new ArrayList<>();
        NodeSample nodeSample = samples.get(MetricsManager.GLOBAL_HOST_IDENTIFIER);
        ProtocolSample protoSample = nodeSample.getProtocolSample(OverlordManager.PROTO_ID);
        MetricSample metricSample = protoSample.getMetricSample(CollectAggregator.AGGREGATED_METRICS);
        Sample[] allSamples = metricSample.getSamples();
        if(allSamples.length > 1){
            logger.error("Got more than one value for Aggregated metrics!!! Aborting");
            System.exit(-1);
        }
        double latency = 0;
        for(Sample s : allSamples){
            Map<String, String> v = s.getLabels();
            latency = Double.parseDouble(v.get(CollectAggregator.AVERAGE_LATENCY));
        }
        if(latency > maxLatency) {
            logger.info(String.format("Latency is %.2f, which is higher than %.2f. Changing fanout to %d", latency, maxLatency, fanout));
            reconfigurations.add(newReconfigurePair(fanout, broadcastProtoId));
        } else{
            logger.info(String.format("Latency is %.2f. No reconfiguration required", latency));
        }
        return reconfigurations;
    }

    public Pair<Reconfigure, Short> newReconfigurePair(int fanout, short broadcastProtoId){
        return new Pair<>(new Reconfigure.ReconfigureBuilder().addProperty("fanout", fanout).build(), broadcastProtoId);
    }
}
