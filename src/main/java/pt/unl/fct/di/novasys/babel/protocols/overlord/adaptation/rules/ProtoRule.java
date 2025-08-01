package pt.unl.fct.di.novasys.babel.protocols.overlord.adaptation.rules;

import org.javatuples.Pair;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.Reconfigure;
import pt.unl.fct.di.novasys.babel.metrics.NodeSample;

import java.util.List;
import java.util.Map;

public abstract class ProtoRule {

    private final short id;
    private final String name;
    private final String description;

    public ProtoRule(short id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public short getId() {
        return this.id;
    }
    public String getName(){ return this.name; }
    public String getDescription(){ return this.description; }

    public abstract List<Pair<Reconfigure, Short>> evaluate(Map<String, NodeSample> samples);

    public String toString(){return String.format("(%d) %s - %s;", getId(), getName(), getDescription());}


}
