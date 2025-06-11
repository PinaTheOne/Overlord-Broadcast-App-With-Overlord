package tardis.Overlord.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class GetMetricsTimer extends ProtoTimer {

    public static final short TIMER_ID = 1100;

    public GetMetricsTimer() {
        super(TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }

}
