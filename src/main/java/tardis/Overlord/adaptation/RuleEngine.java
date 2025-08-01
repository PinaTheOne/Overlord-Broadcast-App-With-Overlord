package tardis.Overlord.adaptation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Pair;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.Reconfigure;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.metrics.NodeSample;
import pt.unl.fct.di.novasys.babel.protocols.dissemination.requests.BroadcastRequest;
import pt.unl.fct.di.novasys.babel.protocols.eagerpush.AdaptiveEagerPushGossipBroadcast;
import pt.unl.fct.di.novasys.network.data.Host;
import tardis.Overlord.OverlordManager;
import tardis.Overlord.adaptation.requests.EvaluateConditionsRequest;
import tardis.Overlord.adaptation.requests.RegisterRuleRequest;
import tardis.Overlord.adaptation.requests.UnregisterRuleRequest;
import tardis.Overlord.adaptation.rules.ProtoRule;
import tardis.Overlord.utils.ReconfigurationsContainer;
import java.util.*;

public class RuleEngine extends GenericProtocol {

    public static final String PROTO_NAME = "Rule Engine";
    public static final short PROTO_ID = 1200;

    public static final Logger logger = LogManager.getLogger(RuleEngine.class);

    private final Map<Short, ProtoRule> registeredRules;
    private final Host myself;

    public RuleEngine(Host myself) {
        super(PROTO_NAME, PROTO_ID);
        this.registeredRules = new HashMap<>();
        this.myself =  myself;
        try {
            registerRequestHandler(RegisterRuleRequest.REQUEST_ID, this::uponRegisterRuleRequest);
            registerRequestHandler(UnregisterRuleRequest.REQUEST_ID, this::uponUnregisterRuleRequest);
            registerRequestHandler(EvaluateConditionsRequest.REQUEST_ID, this::uponEvaluateConditionsRequest);
        } catch (HandlerRegistrationException e) {
            logger.error("Couldn't Register Request Handler! Exiting...", e);
            System.exit(1);
        }
    }

    @Override
    public void init(Properties props) {
        // Nothing yet
    }

    /* ********************* *
     * ****** REQUESTS ***** *
     * ********************* */

    private void uponRegisterRuleRequest(RegisterRuleRequest req, short protoID) {
        logger.info("Received RegisterRuleRequest from protocol {}", protoID);
        registerRule(req.getRule());
    }

    private void uponUnregisterRuleRequest(UnregisterRuleRequest req, short protoID){
        logger.info("Received EvaluateConditionsRequest from protocol {}", protoID);
        unregisterRule(req.getID());
    }

    private void uponEvaluateConditionsRequest(EvaluateConditionsRequest req, short protoID) {
        logger.info("Received EvaluateConditionsRequest from protocol {}", protoID);
        evaluateRules(req.getSamples());
    }

    /* **************************************** *
     * ****** RECONFIGURATIONS BROADCAST ****** *
     * **************************************** */

    private void sendRuleBroadcastRequest(List<Pair<Reconfigure, Short>> reconfigurations) {
        ReconfigurationsContainer container = new ReconfigurationsContainer(reconfigurations);
        sendRequest(new BroadcastRequest(myself, ReconfigurationsContainer.toByteArray(container), OverlordManager.PROTO_ID), AdaptiveEagerPushGossipBroadcast.PROTOCOL_ID);
    }

    /* ********************* *
     * ** RULE EVALUATION ** *
     * ********************* */

    private void evaluateRules(Map<String, NodeSample> stats) {
        logger.info("Received an Evaluate Rules Request, proceeding with evaluation.");
        if (logger.isDebugEnabled()) {
            logger.debug("Registered Rules are:");
            int i = 0;
            for (short key : registeredRules.keySet()) {
                logger.debug("  {}: {}", i++, registeredRules.get(key).toString());
            }
        }
        List<Pair<Reconfigure, Short>> fullReconfigurations = new LinkedList<>();
        for (short key : registeredRules.keySet()) {
            try {
                ProtoRule rule = registeredRules.get(key);
                List<Pair<Reconfigure, Short>> reconfigurations = rule.evaluate(stats);
                fullReconfigurations.addAll(reconfigurations);
            } catch (Exception e) {
                logger.error("Couldn't Evaluate Rule: {}", e.getMessage());
            }
        }
        logger.debug("Finished evaluation, proceeding with reconfiguration.");
        if (fullReconfigurations.isEmpty()) {
            logger.debug("Reconfigurations is empty, no reconfigurations to send.");
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Reconfigurations is not empty, broadcasting them.");
            logger.debug("Reconfigurations contains:");
            int i = 0;
            for (Pair<Reconfigure, Short> pair : fullReconfigurations)
                logger.debug("  {}: {} For Protocol with ID: {}", i++, pair.getValue0(), pair.getValue1());
        }
        sendRuleBroadcastRequest(fullReconfigurations);
    }

    /* ********************* *
     * * RULE REGISTRATION * *
     * ********************* */

    private void registerRule(ProtoRule rule) {
        logger.debug("Registering Rule {}", rule.toString());
        if(registeredRules.containsKey(rule.getId())) {
            logger.error("Rule {} is already registered {}", rule.getId(), registeredRules.get(rule.getId()));
            logger.error("The rule you are trying to register is: {}", rule.toString());
        } else {
            registeredRules.put(rule.getId(), rule);
        }
    }

    private void unregisterRule(short id) {
        logger.debug("Removing Rule {}", id);
        ProtoRule r = registeredRules.remove(id);
        if (r == null)
            logger.error("Rule {} does not exist, and therefore cannot be unregistered", id);
        else
            logger.info("Successfully unregistered Rule {}", r.toString());
    }
}