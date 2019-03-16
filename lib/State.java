package lib;

import java.util.ArrayList;
import java.util.LinkedList;
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
	//TODO: convert to private variables and expose via get and put

	// list of possible state of each server
	public enum States {
		LEADER, CANDIDATE, FOLLOWER
	};

	// list of persistent states on all servers
	public int currentTerm;
	public int getCurrentTerm() {
		return currentTerm;
	}

	public void setCurrentTerm(int currentTerm) {
		this.currentTerm = currentTerm;
	}

	public Integer getVotedFor() {
		return votedFor;
	}

	public void setVotedFor(Integer votedFor) {
		this.votedFor = votedFor;
	}

	public LinkedList<LogEntry> getLog() {
		return log;
	}

	public void setLog(LinkedList<LogEntry> log) {
		this.log = log;
	}

	public int getCommitIndex() {
		return commitIndex;
	}

	public void setCommitIndex(int commitIndex) {
		this.commitIndex = commitIndex;
	}

	public int getLastApplied() {
		return lastApplied;
	}

	public void setLastApplied(int lastApplied) {
		this.lastApplied = lastApplied;
	}

	public int getNextIndex(int index) {
		return nextIndex[index];
	}

//	public void setNextIndex(int nextIndex) {
//		this.nextIndex = nextIndex;
//	}

	public int[] getMatchIndex() {
		return matchIndex;
	}

	public void setMatchIndex(int[] matchIndex) {
		this.matchIndex = matchIndex;
	}

	public States getNodeState() {
		return nodeState;
	}

	public void setNodeState(States nodeState) {
		this.nodeState = nodeState;
	}

	public Integer votedFor;
	public LinkedList<LogEntry> log;

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
		log = new LinkedList<LogEntry>();
		votedFor = 0;

		nextIndex = new int[numPeers];
		matchIndex = new int[numPeers];

		commitIndex = 0;
		lastApplied = 0;

		currentTerm = 0;
	}

}
