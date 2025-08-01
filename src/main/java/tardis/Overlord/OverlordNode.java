package tardis.Overlord;

import pt.unl.fct.di.novasys.babel.metrics.Counter;
import pt.unl.fct.di.novasys.babel.metrics.Metric;
import pt.unl.fct.di.novasys.babel.metrics.MetricsManager;
import pt.unl.fct.di.novasys.babel.metrics.NodeSample;
import pt.unl.fct.di.novasys.babel.metrics.exporters.CollectOptions;
import pt.unl.fct.di.novasys.babel.metrics.exporters.ExporterCollectOptions;
import pt.unl.fct.di.novasys.babel.metrics.exporters.ProtocolCollectOptions;
import pt.unl.fct.di.novasys.babel.protocols.eagerpush.AdaptiveEagerPushGossipBroadcast;
import pt.unl.fct.di.novasys.babel.protocols.overlord.OverlordManager;
import pt.unl.fct.di.novasys.babel.protocols.overlord.OverlordNodeManager;
import pt.unl.fct.di.novasys.network.data.Host;
import tardis.Overlord.aggregators.CollectAggregator;
import tardis.Overlord.aggregators.InNetworkAggregator;
import tardis.Overlord.aggregators.NodeAggregator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class OverlordNode extends OverlordNodeManager {

    private final Host myself;
    private final Counter nodeCount;
    public final static String NODE_COUNTER = "NodeCounter";

    public OverlordNode(Host myself, short moncollectProtoId){
        super(moncollectProtoId);
        this.myself = myself;
        this.nodeCount = registerMetric(new Counter.Builder(NODE_COUNTER, Metric.Unit.NONE).build());
        this.setExporterOptions(ExporterCollectOptions.builder().perProtocolCollectOptions(getExporterOptions()).build());
    }

    @Override
    public void init(Properties props){
        super.init(props);
        this.addAggregation(new NodeAggregator(myself.toString().replace("5555", "5556"), messageValidity));
        this.addAggregation(new InNetworkAggregator());
    }
    @Override
    protected Map<String, NodeSample> uponMonitorDataRequest() {
        this.nodeCount.inc();
        logger.info("Collecting all metrics.");
        NodeSample sample = getProtocolExporter().collectAllMetrics();
        this.addSampleToAggregate(myself.toString(), sample);
        return this.performAggregations();
    }

    @Override
    protected Map<String, NodeSample> uponAggregateDataRequest(List<Map<String, NodeSample>> sampleList) {
        int i = 0;
        for(Map<String, NodeSample> m : sampleList)
            for(String s : m.keySet())
                if(s.equals(MetricsManager.GLOBAL_HOST_IDENTIFIER))
                    this.addSampleToAggregate(s + i++, m.get(s));
                else
                    this.addSampleToAggregate(s, m.get(s));
        return this.performAggregations();
    }

    public static Map<Short, ProtocolCollectOptions> getExporterOptions() {
        Map<Short, ProtocolCollectOptions> ops = new HashMap<>();

        // Options for Overlord Metrics (specifically, the node counter and the already aggregated Metrics)
        ProtocolCollectOptions overlordOptions = new ProtocolCollectOptions();
        overlordOptions.addCollectOptions(InNetworkAggregator.IN_NETWORK_AGGREGATED_METRICS, new CollectOptions(true));
        overlordOptions.addCollectOptions(CollectAggregator.AGGREGATED_METRICS, new CollectOptions(true));
        overlordOptions.addCollectOptions(OverlordNode.NODE_COUNTER, new CollectOptions(true));
        ops.put(OverlordManager.PROTO_ID, overlordOptions);

        // Options for Broadcast Metrics (specifically, the message counters)
        ProtocolCollectOptions broadcastOptions = new ProtocolCollectOptions();
        broadcastOptions.addCollectOptions(NodeAggregator.SENT_MESSAGES_RECORD, new CollectOptions(true));
        broadcastOptions.addCollectOptions(NodeAggregator.RECEIVE_MESSAGES_RECORD, new CollectOptions(true));
        broadcastOptions.addCollectOptions(NodeAggregator.DELIVERED_MESSAGES_RECORD, new CollectOptions(true));
        ops.put(AdaptiveEagerPushGossipBroadcast.PROTOCOL_ID, broadcastOptions);
        return ops;
    }
}
