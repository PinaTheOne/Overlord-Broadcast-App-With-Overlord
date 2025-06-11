package tardis.app.command;

import java.util.Random;

public class Start extends Command {

	public final static long DEFAULT_RANDOM_SEED = 13051982;
	
	public Start(String args[]) {
		super(args);
		new Random(DEFAULT_RANDOM_SEED);
	}
	
	@Override
	public String generateCommand(String[] args) {
		float probability = Float.parseFloat(args[0]);
		
		return Command.CMD_START + "\n" + probability;
	
	}

	public static void main(String[] args) {
		if(args.length != 1) {
			System.err.println("Incorrect argument count\nUsage:java -cp " + System.getProperty("java.class.path") + " java " +
					Start.class.getCanonicalName() + " [send probability]" );
			System.exit(1);
		}
		
		Start cmd = new Start(args);
		cmd.executeCommand(args);
	}

}
 