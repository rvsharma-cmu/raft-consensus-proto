package lib;

import java.util.ArrayList;
import java.util.List;

/**
 * This class implements the state of the servers which are updated on the local
 * stable storage before responding to the RPCs
 * 
 * Three states namely: LEADER, CANDIDATE, FOLLOWER
 * 
 *
 */
public class State {

	// list of possible state of each server
	public enum States {
		LEADER, CANDIDATE, FOLLOWER
	};

	// list of persistent states on all servers
	public int currentTerm;
	public Integer votedFor;
	public List<LogEntry> log;

	// volatile state on all servers
	public int commitIndex;
	public int lastApplied;

	// volatile state on the leaders
	public int[] nextIndex;
	public int[] matchIndex;

	// nodestate variable for a server
	public States nodeState;

	/**
	 * Constructor - each node starts up as followers with its variable initialized
	 * to 0
	 * 
	 * @param numPeers number of servers in the cluster
	 */
	public State(int numPeers) {

		nodeState = States.FOLLOWER;
		log = new ArrayList<LogEntry>();
		votedFor = 0;

		nextIndex = new int[numPeers];
		matchIndex = new int[numPeers];

		commitIndex = 0;
		lastApplied = 0;

		currentTerm = 0;
	}

}
