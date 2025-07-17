package tardis.Overlord.utils;

import pt.unl.fct.di.novasys.babel.core.adaptive.requests.Reconfigure;
import tardis.Overlord.OverlordManager;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class NetworkReconfigure implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, Serializable> config;

    public NetworkReconfigure(Map<String, Serializable> config) {
        this.config = config;
    }

    public Map<String, ? extends Serializable> getConfig() {
        return config;
    }

    public Reconfigure toReconfigure(){
        Reconfigure.ReconfigureBuilder r = new Reconfigure.ReconfigureBuilder();
        for(String s : config.keySet()){
            r.addProperty(s, config.get(s));
        }
        return r.build();
    }

    public static NetworkReconfigure toNetworkReconfigure(Reconfigure r){
        Map<String, Serializable> map = new HashMap<>();
        Iterator<Map.Entry<String, Object>> it = r.iterator();
        while(it.hasNext()){
            Map.Entry<String, Object> next = it.next();
            if(next.getValue() instanceof Serializable)
                map.put(next.getKey(), (Serializable) next.getValue());
            else{
                OverlordManager.logger.error(" Value is not Serializable {}. Exiting...", next);
                System.exit(-1);
            }
        }
        return new NetworkReconfigure(map);
    }
}
