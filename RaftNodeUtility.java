import lib.LogEntries;
import lib.State;

public class RaftNodeUtility {

	
	public static void setFollower(RaftNode raftnode) {
		raftnode.nodeState.setNodeState(State.States.FOLLOWER);
		raftnode.nodeState.setVotedFor(null);
	}
	
	public static void unlockCriticalSection(RaftNode raftnode) {
		raftnode.receivedRequest = true;
		raftnode.lock.unlock();
	}
	
	public static int getLastIndex(RaftNode raftnode) {

		LogEntries lastEntry = null;	
		lastEntry = raftnode.nodeState.getLastEntry();

		int prevLastIndex = 0;
		if (lastEntry != null)
			prevLastIndex = lastEntry.getIndex();
		return prevLastIndex;
	}
}
