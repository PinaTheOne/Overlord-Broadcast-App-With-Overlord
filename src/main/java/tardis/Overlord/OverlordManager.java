package tardis.Overlord;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.metrics.NodeSample;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.CollectNotification;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.requests.MonitorRequest;
import tardis.Overlord.adaptation.RuleEngine;
import tardis.Overlord.adaptation.requests.EvaluateConditionsRequest;
import tardis.Overlord.adaptation.requests.RegisterRuleRequest;
import tardis.Overlord.adaptation.rules.ProtoRule;
import tardis.Overlord.requests.StartOverlordRequest;
import tardis.Overlord.timers.TriggerOverlordTimer;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

public abstract class OverlordManager extends OverlordNodeManager {

    public static final Logger logger = LogManager.getLogger(OverlordManager.class);

    public final static String PAR_COLLECT_PERIOD = "Overlord.TriggerPeriodMs";
    public final static String PAR_COLLECT_TIMER = "Overlord.TriggerTimer";
    public final static boolean DEFAULT_COLLECT_TIMER_VALUE = false;
    public final static long DEFAULT_COLLECT_PERIOD = 10 * 60 * 1000; // 10 Minutes;
    public final static String PAR_MESSAGE_VALIDITY_TIME_MS = "Metrics.MessageValidityTimeMs";
    public final static long DEFAULT_MESSAGE_VALIDITY_TIME_MS =  30 * 1000; // 30 seconds

    public static final String PAR_IS_OVERLORD = "Overlord.IsOverlord";
    private final short moncollectProtoId;

    public OverlordManager(short moncollectProtoId){
        super(moncollectProtoId);
        this.moncollectProtoId = moncollectProtoId;
    }

    @Override
    public void init(Properties props){
        super.init(props);
        try {

            /* PROPERTIES */

            registerRequestHandler(StartOverlordRequest.PROTOCOL_ID, this::uponStartOverlordRequest);

            // For receiving collected metrics from MON-Collect's result
            subscribeNotification(CollectNotification.NOTIFICATION_ID, this::uponCollectNotification_real);

            // For selecting the period between MON-Collect's trigger
            if (props.containsKey(PAR_COLLECT_PERIOD))
                this.metricCollectionPeriod = Long.parseLong(props.getProperty(PAR_COLLECT_PERIOD));
            else
                this.metricCollectionPeriod = DEFAULT_COLLECT_PERIOD;
            logger.debug("  Overlord trigger period: {}", this.metricCollectionPeriod);

            // For selecting if MON-Collect is triggered manually or by a timer
            if((props.containsKey(PAR_COLLECT_TIMER)
                    && Boolean.parseBoolean(props.getProperty(PAR_COLLECT_TIMER))
            || DEFAULT_COLLECT_TIMER_VALUE)) {
                registerTimerHandler(TriggerOverlordTimer.TIMER_ID, this::uponOverlordTimer);
                setupPeriodicTimer(new TriggerOverlordTimer(), this.metricCollectionPeriod, this.metricCollectionPeriod);
            }
            logger.debug("  Overlord timed trigger: {}", props.containsKey(PAR_COLLECT_TIMER) && Boolean.parseBoolean(props.getProperty(PAR_COLLECT_TIMER))  || DEFAULT_COLLECT_TIMER_VALUE);
        } catch (HandlerRegistrationException e){
            logger.error("Could not register handler: {}", e.getMessage());
        }

        /* RULES */

        // For rule registration. You are supposed to register your defined rules using the register rules method.
        logger.info("Rule Registration:");
        Set<ProtoRule> rules =  this.registerRules();
        if(rules.isEmpty())
            logger.warn("No rules were registered. If this is not the intended behaviour, check if the registerRules method is returning a set with no rules.");
        else{
            for(ProtoRule rule : rules){
                logger.info("   Registering Rule {} - {}: {}", rule.getId(), rule.getName(), rule.getDescription());
                sendRequest(new RegisterRuleRequest(rule), RuleEngine.PROTO_ID);
            }
        }
    }

    /* ********************* *
     * *** REGISTER RULES ** *
     * ********************* */

    /**
     * You can instantiate your rules here, put them in a Set and return them.
     * The rules in the returned set will be registered in the RuleEngine.
     * @return A Set containing the rules you want to be registered in the Rule Engine
     */
    protected abstract Set<ProtoRule> registerRules();

    /* ************************************************* *
     * ****** METRICS AND MON-COLLECT INTERACTION ****** *
     * ************************************************* */

    /**
     * When MON-Collect finishes its execution and reaches the Overlord Node, it will
     * send a Collect Notification (different from {@link pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.notifications.CollectDataNotification})
     * This notification contains the aggregated data from all nodes. In this function,
     * you can perform any transformations you wish to the collected data, which
     * your rules will use. If you do not wish to perform any modifications just return
     * the sample map as it is.
     * @param sampleMap The map containing the samples of aggregated data.
     * @return The same map with modifications (if you made any)
     */
    protected abstract  Map<String, NodeSample> uponCollectNotification(Map<String, NodeSample> sampleMap);

    /* Real Requests - DO NOT TOUCH */

    private void uponCollectNotification_real(CollectNotification notification, short protoId) {
        logger.info("Received collect notification from {}.", protoId);
        Map<String, NodeSample> samples = deserializeSampleMap(notification.getData());
        if(samples.isEmpty())
            logger.warn("Collected sample map is empty! Ignoring notification");
        else {
            OverlordNodeManager.logNodeSampleMap("Collected sample map:", samples);
            logger.info("Sending collected metrics to rule Engine for evaluation");
            sendRequest(new EvaluateConditionsRequest(uponCollectNotification(samples)), RuleEngine.PROTO_ID);
        }
    }

    /* ********************* *
     * *** Overlord Timer ** *
     * ********************* */

    private void uponStartOverlordRequest(StartOverlordRequest req, short protoID){
        logger.info("Received a Start Overlord Request from {}", protoID);
        startOverlord();
    }

    private void uponOverlordTimer(TriggerOverlordTimer timer, long timerId) {
        logger.debug("Timer was triggered. Starting overlord and sending new request to MON-Collect");
        startOverlord();
    }

    private void startOverlord(){
        logger.info("Starting Overlord...");
        sendRequest(new MonitorRequest(), moncollectProtoId);
    }

}
