package tardis.app.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class AppWorkloadGenerateTimer extends ProtoTimer {

	public final static short PROTO_ID = 9999;
	
	public AppWorkloadGenerateTimer() {
		super(PROTO_ID);
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}

}
