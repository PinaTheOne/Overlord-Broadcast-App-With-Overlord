package tardis.Overlord;

import pt.unl.fct.di.novasys.babel.metrics.Counter;
import pt.unl.fct.di.novasys.babel.metrics.Metric;
import pt.unl.fct.di.novasys.babel.metrics.MetricsManager;
import pt.unl.fct.di.novasys.babel.metrics.NodeSample;
import pt.unl.fct.di.novasys.babel.metrics.exporters.ExporterCollectOptions;
import pt.unl.fct.di.novasys.network.data.Host;
import tardis.Overlord.adaptation.rules.HandleLatencyRule;
import tardis.Overlord.adaptation.rules.ProtoRule;
import tardis.Overlord.utils.aggregators.CollectAggregator;
import tardis.Overlord.utils.aggregators.NodeAggregator;

import java.util.*;



public class Overlord extends OverlordManager {

    private final Host myself;
    private final Counter nodeCount;
    private final short broadcastProtocolID;
    public final static String NODE_COUNTER = OverlordNode.NODE_COUNTER;

    public Overlord(Host myself, short moncollectProtocolID, short broadcastProtocolID){
        super(moncollectProtocolID);
        this.myself = myself;
        this.nodeCount = registerMetric(new Counter.Builder(NODE_COUNTER, Metric.Unit.NONE).build());
        this.broadcastProtocolID = broadcastProtocolID;
        this.setExporterOptions(ExporterCollectOptions.builder().perProtocolCollectOptions(OverlordNode.getExporterOptions()).build());
    }

    @Override
    public void init(Properties props){
        super.init(props);
        this.addAggregation(new NodeAggregator(myself.toString().replace("5555", "5556"), messageValidity));
        this.addAggregation(new CollectAggregator());
    }

    @Override
    protected Set<ProtoRule> registerRules() {
        Set<ProtoRule> rules = new HashSet<>();
        rules.add(new HandleLatencyRule(20, 4, this.broadcastProtocolID));
        return rules;
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

    @Override
    protected Map<String, NodeSample> uponCollectNotification(Map<String, NodeSample> sampleMap) {
        logger.debug("No Added steps needed for collection");
        return sampleMap;
    }
}
