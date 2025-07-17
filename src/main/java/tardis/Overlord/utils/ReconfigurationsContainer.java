package tardis.Overlord.utils;

import org.javatuples.Pair;
import pt.unl.fct.di.novasys.babel.core.adaptive.requests.Reconfigure;
import tardis.Overlord.OverlordManager;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ReconfigurationsContainer {

    private final List<Pair<NetworkReconfigure, Short>> reconfigurations;

    public ReconfigurationsContainer(List<Pair<Reconfigure, Short>> reconfigurations){
        List<Pair<NetworkReconfigure, Short>> recs = new LinkedList<>();
        for(Pair<Reconfigure, Short> pair : reconfigurations)
            recs.add(new Pair<>(NetworkReconfigure.toNetworkReconfigure(pair.getValue0()), pair.getValue1()));
        this.reconfigurations = recs;
    }

    @SuppressWarnings("unused") // TODO: Check
    public List<Pair<Reconfigure, Short>> getReconfigurations(){
        List<Pair<Reconfigure, Short>> recs = new LinkedList<>();
        for(Pair<NetworkReconfigure, Short> pair : reconfigurations)
            recs.add(new Pair<>(pair.getValue0().toReconfigure(), pair.getValue1()));
        return recs;
    }

    public static byte[] toByteArray(ReconfigurationsContainer container){
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeInt(container.reconfigurations.size()); // Write number of pairs
            for (Pair<NetworkReconfigure, Short> pair : container.reconfigurations) {
                oos.writeObject(pair.getValue0());   // Serialize Reconfigure object
                oos.writeShort(pair.getValue1());  // Write the short
            }
            oos.flush();
            return baos.toByteArray();

        } catch (Exception e) {
            OverlordManager.logger.error("Couldn't Deserialize: {}", e.getMessage());
            OverlordManager.logger.error("{}", (Object) e.getStackTrace());
            System.exit(-1);
        }
        return null;
    }

    public static List<Pair<Reconfigure, Short>> fromByteArray(byte[] data) throws IOException, ClassNotFoundException {
        List<Pair<Reconfigure, Short>> result = new ArrayList<>();

        try (
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bais)) {
            int size = ois.readInt(); // Read the number of pairs

            for (int i = 0; i < size; i++) {
                Reconfigure reconfig = ((NetworkReconfigure) ois.readObject()).toReconfigure(); // Deserialize Reconfigure
                short value = ois.readShort();                         // Read short value
                result.add(new Pair<>(reconfig, value));
            }
        }
        return result;
    }

}
