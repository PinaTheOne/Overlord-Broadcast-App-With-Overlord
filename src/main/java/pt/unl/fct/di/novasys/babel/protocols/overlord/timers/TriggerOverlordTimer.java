package pt.unl.fct.di.novasys.babel.protocols.overlord.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class TriggerOverlordTimer extends ProtoTimer {

    public static final short TIMER_ID = 1100;

    public TriggerOverlordTimer() {
        super(TIMER_ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }

}
