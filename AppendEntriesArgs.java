
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import lib.ApplyMsg;
import lib.LogEntries;
import lib.State;

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

//	public void checkMessageTerm(RaftNode raftNode) {
//
//		// if RPC request or response contains term
//		// term T > currentTerm : set currentTerm = T
//		// convert to FOLLOWER
//
//		if (getTerm() > raftNode.nodeState.getCurrentTerm()) {
//			raftNode.nodeState.setCurrentTerm(getTerm());
//			raftNode.nodeState.setNodeState(State.States.FOLLOWER);
//		}
//	}
//
//	public boolean checkConsistency(RaftNode raftNode) {
//		LogEntries prevLogEntry = null;
//		boolean lastCommitCheck = false;
//		if (raftNode.nodeState.getLog() != null && raftNode.nodeState.getLog().size() >= getPrevLogIndex()) {
//
//			prevLogEntry = raftNode.nodeState.getLog().get(getPrevLogIndex() - 1);
//
//			if (prevLogEntry.getIndex() == getPrevLogIndex() && prevLogEntry.getTerm() == getPrevLogTerm()) {
//
//				lastCommitCheck = true;
//			}
//		}
//		return lastCommitCheck;
//	}
//
//	/**
//	 * Append missing logs from the leader to follower
//	 * 
//	 * @param raftNode
//	 * 
//	 * 
//	 */
//	public void appendMissingLogs(RaftNode raftNode) {
//
//		for (int i = 0; i < entries.size(); i++) {
//
//			ArrayList<LogEntries> logs = raftNode.nodeState.getLog();
//
//			LogEntries entry = entries.get(i);
//
//			int index = entry.getIndex();
//
//			if (logs.size() >= index) {
//
//				LogEntries logEntry = logs.get(index - 1);
//
//				if (logEntry.getTerm() != entry.getTerm()) {
//
//					// conflicting entry
//					// delete the conflicting entries
//					for (int j = logs.size() - 1; j >= index - 1; j--) {
//						logs.remove(j);
//					}
//
//					logs.add(entry);
//				}
//			} else {
//
//				logs.add(entry);
//			}
//
//		}
//	}
//
//	/**
//	 * Check the index of the leader If leaderCommit > commitIndex, set commitIndex
//	 * = min(leaderCommit, index of last new entry)
//	 *
//	 * @param raftNode
//	 */
//	public void checkLeaderIndex(RaftNode raftNode) {
//
//		int nodeCommitIndex = raftNode.nodeState.getCommitIndex();
//
//		ArrayList<LogEntries> getNodeLogs = raftNode.nodeState.getLog();
//		LogEntries lastEntry = raftNode.nodeState.getLastEntry();
//
//		// If leaderCommit > commitIndex, set commitIndex =
//		// min(leaderCommit, index of last new entry)
//
//		if (this != null && leaderCommit > nodeCommitIndex) {
//
//			if (getNodeLogs != null && lastEntry != null) {
//
//				int commitIndex = Math.min(leaderCommit, raftNode.nodeState.getLastEntry().getIndex());
//
//				while (nodeCommitIndex + 1 <= commitIndex) {
//					LogEntries entry = getNodeLogs.get(nodeCommitIndex);
//
//					ApplyMsg msg = new ApplyMsg(raftNode.nodeId, entry.getIndex(), entry.getCommand(), false, null);
//
//					try {
//						raftNode.lib.applyChannel(msg);
//					} catch (RemoteException e) {
//
//						raftNode.lock.unlock();
//
//						e.printStackTrace();
//						return;
//					}
//
//					raftNode.nodeState.setCommitIndex(nodeCommitIndex + 1);
//					raftNode.nodeState.setLastApplied(nodeCommitIndex + 1);
//					nodeCommitIndex++;
//				}
//			}
//		}
//	}

}
