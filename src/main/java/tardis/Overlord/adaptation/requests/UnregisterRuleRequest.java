package tardis.Overlord.adaptation.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import tardis.Overlord.adaptation.rules.ProtoRule;

public class UnregisterRuleRequest extends ProtoRequest {

    public static final short REQUEST_ID = 1212;

    private final short id;

    public UnregisterRuleRequest(short id){
        super(REQUEST_ID);
        this.id = id;
    }

    public short getID() {
        return this.id;
    }
}
