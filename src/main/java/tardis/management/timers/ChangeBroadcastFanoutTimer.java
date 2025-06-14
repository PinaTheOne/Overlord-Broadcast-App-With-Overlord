package tardis.management.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class ChangeBroadcastFanoutTimer extends ProtoTimer {

    public static final short TIMER_ID = 7439;

    public ChangeBroadcastFanoutTimer() {
        super(TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }

}
