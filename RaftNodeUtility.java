import java.rmi.RemoteException;
import java.util.ArrayList;

import lib.ApplyMsg;
import lib.LogEntries;
import lib.State;

public class RaftNodeUtility {

	public void setFollower(RaftNode raftnode) {
		raftnode.nodeState.setNodeState(State.States.FOLLOWER);
		raftnode.nodeState.setVotedFor(null);
	}

	public void unlockCriticalSection(RaftNode raftnode) {
		raftnode.receivedRequest = true;
		raftnode.lock.unlock();
	}

	public int getLastIndex(RaftNode raftnode) {

		LogEntries lastEntry = null;
		lastEntry = raftnode.nodeState.getLastEntry();

		int prevLastIndex = 0;
		if (lastEntry != null)
			prevLastIndex = lastEntry.getIndex();
		return prevLastIndex;
	}

	public void  checkMessageTerm(RaftNode raftNode, AppendEntriesArgs appendEntriesArgs) {

		// if RPC request or response contains term
		// term T > currentTerm : set currentTerm = T
		// convert to FOLLOWER

		if (appendEntriesArgs.getTerm() > raftNode.nodeState.getCurrentTerm()) {
			raftNode.nodeState.setCurrentTerm(appendEntriesArgs.getTerm());
			raftNode.nodeState.setNodeState(State.States.FOLLOWER);
		}
	}

	public boolean checkConsistency(RaftNode raftNode, AppendEntriesArgs appendEntriesArgs) {
		LogEntries prevLogEntry = null;
		boolean lastCommitCheck = false;
		if (raftNode.nodeState.getLog() != null && raftNode.nodeState.getLog().size() >= appendEntriesArgs.getPrevLogIndex()) {

			prevLogEntry = raftNode.nodeState.getLog().get(appendEntriesArgs.getPrevLogIndex() - 1);

			if (prevLogEntry.getIndex() == appendEntriesArgs.getPrevLogIndex() && prevLogEntry.getTerm() == appendEntriesArgs.getPrevLogTerm()) {

				lastCommitCheck = true;
			}
		}
		return lastCommitCheck;
	}

	/**
	 * Append missing logs from the leader to follower
	 * 
	 * @param raftNode
	 * 
	 * 
	 */
	public void appendMissingLogs(RaftNode raftNode, AppendEntriesArgs appendEntriesArgs) {

		for (int i = 0; i < appendEntriesArgs.entries.size(); i++) {

			ArrayList<LogEntries> logs = raftNode.nodeState.getLog();

			LogEntries entry = appendEntriesArgs.entries.get(i);

			int index = entry.getIndex();

			if (logs.size() >= index) {

				LogEntries logEntry = logs.get(index - 1);

				if (logEntry.getTerm() != entry.getTerm()) {

					// conflicting entry
					// delete the conflicting entries
					for (int j = logs.size() - 1; j >= index - 1; j--) {
						logs.remove(j);
					}

					logs.add(entry);
				}
			} else {

				logs.add(entry);
			}

		}
	}

	/**
	 * Check the index of the leader If leaderCommit > commitIndex, set commitIndex
	 * = min(leaderCommit, index of last new entry)
	 *
	 * @param raftNode
	 */
	public void checkLeaderIndex(RaftNode raftNode,AppendEntriesArgs appendEntriesArgs) {

		int nodeCommitIndex = raftNode.nodeState.getCommitIndex();

		ArrayList<LogEntries> getNodeLogs = raftNode.nodeState.getLog();
		LogEntries lastEntry = raftNode.nodeState.getLastEntry();

		// If leaderCommit > commitIndex, set commitIndex =
		// min(leaderCommit, index of last new entry)

		if (this != null && appendEntriesArgs.leaderCommit > nodeCommitIndex) {

			if (getNodeLogs != null && lastEntry != null) {

				int commitIndex = Math.min(appendEntriesArgs.leaderCommit, raftNode.nodeState.getLastEntry().getIndex());

				while (nodeCommitIndex + 1 <= commitIndex) {
					LogEntries entry = getNodeLogs.get(nodeCommitIndex);

					ApplyMsg msg = new ApplyMsg(raftNode.nodeId, entry.getIndex(), entry.getCommand(), false, null);

					try {
						raftNode.lib.applyChannel(msg);
					} catch (RemoteException e) {

						raftNode.lock.unlock();

						e.printStackTrace();
						return;
					}

					raftNode.nodeState.setCommitIndex(nodeCommitIndex + 1);
					raftNode.nodeState.setLastApplied(nodeCommitIndex + 1);
					nodeCommitIndex++;
				}
			}
		}
	}
}
