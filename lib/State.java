package lib;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Persistent & Volatile state on servers
 */
public class State {
    public enum States{FOLLOWER, CANDIDATE, LEADER}
    private States nodeState = States.FOLLOWER;
    private int currentTerm;
    private Integer votedFor = null; // Candidate ID that received vote in $Current Term$, (or null if none<hasn't voted yet>)
    private ArrayList<LogEntries> log; //

    /* Volatile state on all Servers */
    private int commitIndex; // index of highest log entry known to be committed (initialized to 0, increases monotonically)
    private int lastApplied; // index of highest log entry applied to state machine (initialized to 0, increases monotonically)

    /* Volatile state on leader */
    public int[] nextIndex; // for each server, index of the next log entry to send to that server (initialized to leader last log index + 1)
    public int[] matchIndex; //for each server, index of highest log entry known to be replicated on server (initialized to 0, increases monotonically)

    public int getCurrentTerm() {
		return currentTerm;
	}

	public void setCurrentTerm(int currentTerm) {
		this.currentTerm = currentTerm;
	}

	public Integer getVotedFor() {
		return votedFor;
	}
	
	public LogEntries getLastEntry() {
		if(this.log.isEmpty())
			return null;
		else {
			int last = this.log.size() - 1;
			LogEntries lastEntry = this.log.get(last);
			return lastEntry;
		}
	}

	public void setVotedFor(Integer votedFor) {
		this.votedFor = votedFor;
	}

	public ArrayList<LogEntries> getLog() {
		return log;
	}

	public void setLog(ArrayList<LogEntries> log) {
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

	public int[] getNextIndex() {
		return nextIndex;
	}

	

	public int[] getMatchIndex() {
		return matchIndex;
	}

	
	public State(int num_peers){
        this.currentTerm = 0;
        this.log = new ArrayList<LogEntries>();
        this.commitIndex = 0;
        this.lastApplied = 0;
        this.nextIndex = new int[num_peers];
        this.matchIndex = new int[num_peers];
    }

    public States getNodeState(){

        return this.nodeState;
    }

    public void setNodeState(States new_role){

        this.nodeState = new_role;
    }
    
    /**
	 * Retrieve entry logs from the given index
	 * 
	 * @param serverEntries -
	 * @param index         - index from which to retrieve the entry logs
	 * @return
	 */
	public ArrayList<LogEntries> retrieveLogs(int index) {

		List<LogEntries> entries = this.getLog();
		ArrayList<LogEntries> resultLogs = new ArrayList<LogEntries>();

		if (entries.size() > index)
			for (int i = index; i < entries.size(); i++) {
				resultLogs.add(entries.get(i));
			}

		return resultLogs;
	}
}