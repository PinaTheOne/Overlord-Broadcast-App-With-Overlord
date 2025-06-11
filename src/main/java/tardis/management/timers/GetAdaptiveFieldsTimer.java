package tardis.management.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class GetAdaptiveFieldsTimer extends ProtoTimer {

    public static final short TIMER_ID = 7438;

    public GetAdaptiveFieldsTimer() {
        super(TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }

}
