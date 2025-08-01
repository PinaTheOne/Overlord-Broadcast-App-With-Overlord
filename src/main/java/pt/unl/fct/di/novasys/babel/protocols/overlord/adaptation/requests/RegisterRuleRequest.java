package pt.unl.fct.di.novasys.babel.protocols.overlord.adaptation.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.babel.protocols.overlord.adaptation.rules.ProtoRule;

public class RegisterRuleRequest extends ProtoRequest {

    public static final short REQUEST_ID = 1210;

    private final ProtoRule rule;

    public RegisterRuleRequest(ProtoRule rule){
        super(REQUEST_ID);
        this.rule = rule;
    }

    public ProtoRule getRule() {
        return this.rule;
    }
}
