package lib;

import java.util.ArrayList;
import java.util.Vector;

public class State { 
	
	// list of possible state of each server 
	public enum States {LEADER, CANDIDATE, FOLLOWER};
	public int currentTerm; 
	public Integer votedFor; 
	public ArrayList<LogEntry> log; 
	
	public int commitIndex; 
	public int lastApplied; 
	
	public int[] nextIndex; 
	public int[] matchIndex; 
	
	private States nodeState; 
	
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

	public int getCurrentTerm() {
		return currentTerm;
	}

	public void setCurrentTerm(int currentTerm) {
		this.currentTerm = currentTerm;
	}

	public int getVotedFor() {
		return votedFor;
	}

	public void setVotedFor(int votedFor) {
		this.votedFor = votedFor;
	}

	public ArrayList<LogEntry> getLog() {
		return log;
	}

	public void setLog(ArrayList<LogEntry> log) {
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

	public int getNextIndex(int i) {
		return nextIndex[i];
	}

	public void setNextIndex(int[] nextIndex) {
		this.nextIndex = nextIndex;
	}

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

}
