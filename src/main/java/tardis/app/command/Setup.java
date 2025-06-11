package tardis.app.command;

import java.util.Random;

public class Setup extends Command {

	public final static long DEFAULT_RANDOM_SEED = 13051982;
	private final Random r;
	
	public Setup(String args[]) {
		super(args);
		this.r = new Random(DEFAULT_RANDOM_SEED);
	}
	
	@Override
	public String generateCommand(String[] args) {
		int n_nodes = Integer.parseInt(args[0]);
		String index = args[1];
		String keysPernode = args[2];
		
		String inits = "";
		for(int i = 0; i < n_nodes; i++) {
			inits = inits + (i == 0 ? "":" ") + r.nextLong();
		}
		
		return Command.CMD_SETUP + "\n" + index + "\n" + keysPernode + "\n" + inits;
	
	}

	public static void main(String[] args) {
		if(args.length != 3) {
			System.err.println("Incorrect argument count\nUsage:java -cp " + System.getProperty("java.class.path") + " java " +
					Setup.class.getCanonicalName() + " n_nodes index keys_per_node" );
			System.exit(1);
		}
		
		Setup cmd = new Setup(args);
		cmd.executeCommand(args);
	}

}
 