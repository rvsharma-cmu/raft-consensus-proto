
import java.rmi.RemoteException;

import lib.LogEntry;
import lib.Message;
import lib.MessageType;
import lib.RaftUtilities;
import lib.RequestVoteArgs;
import lib.RequestVoteReply;
import lib.State.States; 

public class ElectionThread extends Thread {
	
	private int startId;
	private int endId;

	private RequestVoteArgs requestVoteArgs;
	private RaftNode node;
	private Message requestForVoteMessage;
	private Message replyForVoteMessage;
	private RequestVoteReply requestVoteReply;
	

	
	public ElectionThread(RaftNode node, int start, int end, RequestVoteArgs args) {
		
		this.node = node; 
		this.startId = start; 
		this.endId = end; 
		this.requestVoteArgs = args; 
	}


	@Override
	public void run() {
		// TODO Auto-generated method stub
		byte[] serializeMessage = RaftUtilities.serialize(this.requestVoteArgs);
		this.requestForVoteMessage = new Message(MessageType.RequestVoteArgs, this.startId, this.endId, serializeMessage);
		
		try {
			replyForVoteMessage = this.node.lib.sendMessage(this.requestForVoteMessage);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			try {
				this.join();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return;
		}
		if(replyForVoteMessage == null)
		{
			try {
				this.join();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return;
		}
		this.requestVoteReply = (RequestVoteReply)RaftUtilities.deserialize(replyForVoteMessage.getBody());
		
		this.node.lock.lock();
		
		if(requestVoteReply.getTerm() > node.nodeState.currentTerm)
		{
			node.nodeState.setCurrentTerm(requestVoteReply.getTerm());
			node.nodeState.setNodeState(States.FOLLOWER);
			node.numOfVotes = 0;
			node.nodeState.setVotedFor(null);
			this.node.lock.unlock();
			try {
				this.join();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return;
		}
		if(requestVoteReply.getTerm() < node.nodeState.currentTerm)
		{
			this.node.lock.unlock();
			try {
				this.join();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return;
		}
		synchronized (node.nodeState) {
			if(requestVoteReply.isVoteGranted() && node.nodeState.getNodeState() == lib.State.States.CANDIDATE)
			{
				node.numOfVotes++;
				node.receivedRequest = true;
				
				if(node.numOfVotes>node.majorityVotes)
				{
					node.nodeState.setNodeState(lib.State.States.LEADER);
					int size = node.nodeState.log.size()-1;
					LogEntry logEntry = node.nodeState.log.peekLast();
					int lastIndex = 0;
					if(logEntry!=null)
					{
						lastIndex = logEntry.getIndex();
					}
					for(int i=0;i<node.numPeers;i++)
					{
						node.nodeState.nextIndex[i] = lastIndex+1;
					}
					node.sendHeartbeats();
					node.nodeState.notify();
					this.node.lock.unlock();
					try {
						this.join();
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					return;
				}
			}
			
		}
	
		
	}
	
	

}
