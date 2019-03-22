
import java.rmi.RemoteException;

import lib.LogEntries;
import lib.Message;
import lib.MessageType;
import lib.RaftUtilities;
import lib.RequestVoteArgs;
import lib.RequestVoteReply;
import lib.State.States; 

public class ElectionThread extends Thread {
	
	private int src_id;
	private int dest_id;

	private RequestVoteArgs requestVoteArgs;
	private RaftNode node;
	private Message requestForVoteMessage;
	private Message replyForVoteMessage;
	private RequestVoteReply requestVoteReply;
	

	
	public ElectionThread(RaftNode node, int start, int end, RequestVoteArgs args) {
		
		this.node = node; 
		this.src_id = start; 
		this.dest_id = end; 
		this.requestVoteArgs = args; 
	}


	@Override
	public void run() {
		
		byte[] serializeMessage = RaftUtilities.serialize(this.requestVoteArgs);

		this.requestForVoteMessage = new Message(MessageType.RequestVoteArgs, this.src_id, this.dest_id, serializeMessage);
		
		try {
			replyForVoteMessage = this.node.lib.sendMessage(this.requestForVoteMessage);
		} catch (RemoteException e) {

			e.printStackTrace();
			terminateThread();
			return;
		}

		if(replyForVoteMessage == null){
			terminateThread();
			return;
		}
		
		this.requestVoteReply = (RequestVoteReply)RaftUtilities.deserialize(replyForVoteMessage.getBody());
		
		this.node.lock.lock();
		
		if(requestVoteReply.getTerm() > node.nodeState.getCurrentTerm())
		{
			node.nodeState.setCurrentTerm(requestVoteReply.getTerm());
			node.nodeState.setNodeState(States.FOLLOWER);
			node.nodeState.setVotedFor(null);
			node.numOfVotes = 0;
			
		} else if(requestVoteReply.getTerm() < node.nodeState.getCurrentTerm())
		{
			this.node.lock.unlock();
			terminateThread();
			return;
		} else {
			synchronized (node.nodeState) {
				if(requestVoteReply.isVoteGranted() && node.nodeState.getNodeState() == lib.State.States.CANDIDATE)
				{
					node.numOfVotes++;
					node.receivedRequest = true;
					
					if(node.numOfVotes>node.majorityVotes)
					{
						node.nodeState.setNodeState(lib.State.States.LEADER);
						
						LogEntries logEntries = node.nodeState.getLog().peekLast();
						int lastIndex = 0;
						if(logEntries!=null)
						{
							lastIndex = logEntries.getIndex();
						}
						for(int i=0;i<node.numPeers;i++)
						{
							node.nodeState.nextIndex[i] = lastIndex+1;
						}
						node.sendHeartbeats();
						node.nodeState.notify();
						
						
					}
				}
			}
		}
		this.node.lock.unlock();
		terminateThread();
		return;
	}


	public void terminateThread() {
		try {
			this.join();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	

}
