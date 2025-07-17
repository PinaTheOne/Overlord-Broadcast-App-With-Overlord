package tardis.Overlord.adaptation.rules;

import org.javatuples.Pair;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.Reconfigure;
import tardis.Overlord.utils.AggregatedStatistics;

import java.util.ArrayList;
import java.util.List;

public class HandleLatencyRule extends ProtoRule {

    private static final short RULE_ID = 1;
    private static final String NAME = "Handle Latency";
    private static final String DESCRIPTION = "If latency is lower than 0.2, increase fanout to 5";
    private final int fanout;
    private final short broadcastProtoId;

    public HandleLatencyRule(int fanout, short broadcastProtoId) {
        super(RULE_ID, NAME, DESCRIPTION);
        this.fanout = fanout;
        this.broadcastProtoId = broadcastProtoId;
    }

    @Override
    public List<Pair<Reconfigure, Short>> evaluate(AggregatedStatistics metrics) {
        List<Pair<Reconfigure, Short>> reconfigurations = new ArrayList<>();
        if(metrics.getAverageLatency() > 0.2) {
            reconfigurations.add(newReconfigurePair(fanout, broadcastProtoId));
        }
        return reconfigurations;
    }

    public Pair<Reconfigure, Short> newReconfigurePair(int fanout, short broadcastProtoId){
        return new Pair<>(new Reconfigure.ReconfigureBuilder().addProperty("fanout", fanout).build(), broadcastProtoId);
    }
}
