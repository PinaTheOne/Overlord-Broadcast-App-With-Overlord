package pt.unl.fct.di.novasys.babel.protocols.overlord.adaptation.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.babel.metrics.NodeSample;

import java.util.Map;

public class EvaluateConditionsRequest extends ProtoRequest {

    public static final short REQUEST_ID = 1211;

    private final Map<String, NodeSample> samples;

    public EvaluateConditionsRequest(Map<String, NodeSample> samples){
        super(REQUEST_ID);
        this.samples = samples;
    }

    public Map<String, NodeSample> getSamples(){
        return this.samples;
    }
}
