
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import lib.ApplyMsg;
import lib.LogEntries;
import lib.State;

/**
 * @author abubber,rvsharma
 *
 */
public class AppendEntriesArgs implements Serializable {

	/**
	 * Arguments to be sent for append entries to all followers by the leader
	 */
	private static final long serialVersionUID = 1L;
	private int term;
	private int leaderId;
	private int prevLogIndex;
	private int prevLogTerm;
	public ArrayList<LogEntries> entries;
	public int leaderCommit;

	/**
	 * @param term         - leader's term
	 * @param leaderId     - so follower's can redirect client
	 * @param prevLogIndex
	 * @param prevLogTerm
	 * @param logEntries
	 * @param leaderCommit
	 */
	public AppendEntriesArgs(int term, int leaderId, int prevLogIndex, int prevLogTerm,
			ArrayList<LogEntries> logEntries, int leaderCommit) {
		super();
		this.term = term;
		this.leaderId = leaderId;
		this.prevLogIndex = prevLogIndex;
		this.prevLogTerm = prevLogTerm;
		this.entries = logEntries;
		this.leaderCommit = leaderCommit;
	}

	public int getTerm() {
		return term;
	}

	public void setTerm(int term) {
		this.term = term;
	}

	public int getLeaderId() {
		return leaderId;
	}

	public void setLeaderId(int leaderId) {
		this.leaderId = leaderId;
	}

	public int getPrevLogIndex() {
		return prevLogIndex;
	}

	public void setPrevLogIndex(int prevLogIndex) {
		this.prevLogIndex = prevLogIndex;
	}

	public int getPrevLogTerm() {
		return prevLogTerm;
	}

	public void setPrevLogTerm(int prevLogTerm) {
		this.prevLogTerm = prevLogTerm;
	}

	public ArrayList<LogEntries> getEntries() {
		return entries;
	}

	public void setEntries(ArrayList<LogEntries> entries) {
		this.entries = entries;
	}

	public int getLeaderCommit() {
		return leaderCommit;
	}

	public void setLeaderCommit(int leaderCommit) {
		this.leaderCommit = leaderCommit;
	}

}
