package tardis.Overlord.requests;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class StartOverlordRequest extends ProtoRequest {

    public static final short PROTOCOL_ID = 1101;
    public StartOverlordRequest() {
        super(PROTOCOL_ID);
    }
}
