package tardis.Overlord.adaptation.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import tardis.Overlord.utils.AggregatedStatistics;

public class EvaluateConditionsRequest extends ProtoRequest {

    public static final short REQUEST_ID = 1211;

    private final AggregatedStatistics stats;

    public EvaluateConditionsRequest(AggregatedStatistics stats){
        super(REQUEST_ID);
        this.stats = stats;
    }

    public AggregatedStatistics getStats(){
        return this.stats;
    }
}
