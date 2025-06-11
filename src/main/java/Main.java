import java.io.File;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.unl.fct.di.novasys.babel.core.Babel;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.protocols.eagerpush.AdaptiveEagerPushGossipBroadcast;
import pt.unl.fct.di.novasys.babel.protocols.hyparview.HyParView;
import pt.unl.fct.di.novasys.babel.protocols.overlord.moncollect.MonCollect;
import pt.unl.fct.di.novasys.babel.utils.NetworkingUtilities;
import pt.unl.fct.di.novasys.babel.utils.memebership.monitor.AdaptiveReconfigurationMonitor;
import pt.unl.fct.di.novasys.babel.utils.memebership.monitor.MembershipMonitor;
import pt.unl.fct.di.novasys.babel.utils.recordexporter.RecordExporter;
import pt.unl.fct.di.novasys.babel.utils.visualization.VisualizationProtocol;
import pt.unl.fct.di.novasys.network.data.Host;
import tardis.Overlord.OverlordManager;
import tardis.app.DataDisseminationApp;
import tardis.management.Controller;

public class Main {

	// Sets the log4j (logging library) configuration file
	static {
		System.setProperty("log4j.configurationFile", "log4j2.xml");
		System.setProperty("java.net.preferIPv4Stack", "true");
	}

	// Creates the logger object
	private static final Logger logger = LogManager.getLogger(Main.class);

	// Default babel configuration file (can be overridden by the "-config" launch
	// argument)
	private static final String DEFAULT_CONF = "tardis.conf";

	@SuppressWarnings("unused")
	private final DataDisseminationApp app;

	public Main(DataDisseminationApp app) {
		this.app = app;
	}

	public static void main(String[] args) throws Exception {
		logger.info("Starting...");

		// Get the (singleton) babel instance
		Babel babel = Babel.getInstance();
		if (new File(DEFAULT_CONF).exists()) {
			System.err.println("The config file: " + DEFAULT_CONF + " is not accessible.");
			System.exit(1);
		}

		Properties props = Babel.loadConfig(args, DEFAULT_CONF);

		String address = null;

		if (props.containsKey(Babel.PAR_DEFAULT_INTERFACE))
			address = NetworkingUtilities.getAddress(props.getProperty(Babel.PAR_DEFAULT_INTERFACE));
		else if (props.containsKey(Babel.PAR_DEFAULT_ADDRESS))
			address = props.getProperty(Babel.PAR_DEFAULT_ADDRESS);

		int port = -1;

		if (props.containsKey(Babel.PAR_DEFAULT_PORT))
			port = Integer.parseInt(props.getProperty(Babel.PAR_DEFAULT_PORT));

		Host h = null;

		if (address == null || port == -1) {
			System.err.println("Configuration must contain one of '" + Babel.PAR_DEFAULT_INTERFACE + "' or '"
					+ Babel.PAR_DEFAULT_ADDRESS + "' and the '" + Babel.PAR_DEFAULT_PORT + "'");
			System.exit(1);
		}

		h = new Host(InetAddress.getByName(address), port);

		System.out.println("local host is set to: " + h);

		HyParView membershipProtocol = new HyParView("channel.hyparview", props, h);

		// TODO: Isto serve para que? (ambos)
		MembershipMonitor mm = new MembershipMonitor();

		AdaptiveReconfigurationMonitor arm = new AdaptiveReconfigurationMonitor();

		Host gossipHost = new Host(h.getAddress(), h.getPort() + 1);
		AdaptiveEagerPushGossipBroadcast bcast = new AdaptiveEagerPushGossipBroadcast("channel.gossip", props,
				gossipHost);

		Controller controller = new Controller(props, h);

		DataDisseminationApp app = new DataDisseminationApp(gossipHost);

		if (!props.containsKey("Metrics.monitor.address") || !props.containsKey("Metrics.monitor.port")) {
			System.out.println("Missing monitor configuration");
			System.exit(1);
		}

		InetAddress monitorAddress = InetAddress.getByName(props.getProperty("Metrics.monitor.address"));
		int monitorPort = Integer.parseInt(props.getProperty("Metrics.monitor.port"));

		Host monitorHost = new Host(monitorAddress, monitorPort);

		//Record exporter exports metrics to the monitor host (there is only one metrics monitor)
		Host recordExporterHost = new Host(h.getAddress(), h.getPort() + 22);
		RecordExporter recordExporter = new RecordExporter(recordExporterHost, monitorHost);

		VisualizationProtocol visualizationProtocol = null;
		if (props.containsKey("Visualization") && props.getProperty("Visualization").equals("true")) {
			Host visualizationHost = new Host(h.getAddress(), h.getPort() + 23);
			visualizationProtocol = new VisualizationProtocol(visualizationHost);
		}

		MonCollect monCollect = null;
		OverlordManager overlordManager = null;

		if(props.containsKey("MON-Collect.Channel.port")) {
			logger.debug("Overlord is enabled");
			//InetAddress moncollectAddress = InetAddress.getByName(props.getProperty("MON-Collect.Channel.address"));
			int moncollectPort = Integer.parseInt(props.getProperty("MON-Collect.Channel.port"));

			Host monCollectHost = new Host(h.getAddress(), moncollectPort);
			// TODO: Ver isto
			System.out.println(monCollectHost);
			monCollect = new MonCollect(monCollectHost, OverlordManager.PROTO_ID);
			overlordManager = new OverlordManager(MonCollect.PROTO_ID);
		}

		// Solve the dependency between the data dissemination app and the broadcast
		// protocol if omitted from the config
		props.putIfAbsent(DataDisseminationApp.PAR_BCAST_PROTOCOL_ID,
		AdaptiveEagerPushGossipBroadcast.PROTOCOL_ID + "");



		List<GenericProtocol> protocols = new LinkedList<>();
		protocols.add(membershipProtocol);
		protocols.add(mm);
		protocols.add(arm);
		protocols.add(bcast);
		protocols.add(controller);
		//protocols.add(recordExporter);
		protocols.add(visualizationProtocol);
		protocols.add(app);
		protocols.add(monCollect);
		protocols.add(overlordManager);


		for (GenericProtocol protocol : protocols) {
			if (protocol == null) continue;
			babel.registerProtocol(protocol);
			System.out.printf("Loaded: %s %d%n", protocol.getProtoName(), protocol.getProtoId());
		}

		for (GenericProtocol protocol : protocols) {
			if (protocol == null) continue;
			protocol.init(props);
			System.out.printf("Initialized: %s %d%n", protocol.getProtoName(), protocol.getProtoId());
		}

		System.out.println("Setup is complete.");

		babel.start();
		System.out.println("System is running.");

		System.out.println("Asking if I am Overlord");
		if(props.containsKey("Overlord") && Boolean.parseBoolean(props.getProperty("Overlord"))) {
			System.out.println("This is the Overlord, beggining Monitor in 60 secconds");
			//for (; ; ) {
			//	Thread.sleep(60000); // 60 secconds
			//	System.out.println("AAAAAA");
			//	assert overlordManager != null;
			//	overlordManager.beginMonitor();
			//}
		}

	}

}
