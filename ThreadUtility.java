import lib.Message;
import lib.RequestVoteArgs;
import lib.State.States;

/*
 * Common utility class for Election Thread and AppendEntriesThread
 */
public class ThreadUtility extends Thread{

	 int sourceId;
	 int destId;

	 RequestVoteArgs requestVoteArgs;
	 RaftNode node;
	 Message requestMessage;
	 Message replyMessage;
	
	 public ThreadUtility(RaftNode node, int startID, int endID) {

			this.node = node;
			this.sourceId = startID;
			this.destId = endID;
	}

	
	
	
	
	public void terminateThread() {
		try {
			this.join();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	public void setFollower() {
		node.nodeState.setNodeState(States.FOLLOWER);
		node.nodeState.setVotedFor(null);
		node.numOfVotes = 0;
	}
	
	
}
