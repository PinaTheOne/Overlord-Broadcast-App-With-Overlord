package tardis.Overlord.adaptation.rules;

import org.javatuples.Pair;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.Reconfigure;
import tardis.Overlord.utils.AggregatedStatistics;

import java.util.List;

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

    public abstract List<Pair<Reconfigure, Short>> evaluate(AggregatedStatistics metrics);

    public String toString(){return String.format("(%d) %s - %s;", getId(), getName(), getDescription());}

}
