

import java.rmi.RemoteException;

import lib.LogEntries;
import lib.Message;
import lib.MessageType;
import lib.RaftUtilities;
import lib.RequestVoteArgs;
import lib.RequestVoteReply;
import lib.State;
import lib.State.States; 

public class ElectionThread extends ThreadUtility {
	
	private RequestVoteReply requestVoteReply;


	
	public ElectionThread(RaftNode node, int start, int end, RequestVoteArgs args) {
		
		super(node, start, end);
		this.requestVoteArgs = args; 
	}


	@Override
	public void run() {
		
		byte[] serializeMessage = RaftUtilities.serialize(this.requestVoteArgs);

		this.requestMessage = new Message(MessageType.RequestVoteArgs, this.sourceId, this.destId, serializeMessage);
		
		try {
			replyMessage = this.node.lib.sendMessage(this.requestMessage);
		} catch (RemoteException e) {

			e.printStackTrace();
			terminateThread();
			return;
		}

		if(replyMessage == null){
			terminateThread();
			return;
		}
		
		this.requestVoteReply = (RequestVoteReply)RaftUtilities.deserialize(replyMessage.getBody());
		
		this.node.lock.lock();
		
		if(requestVoteReply.getTerm() > node.nodeState.getCurrentTerm())
		{
			node.nodeState.setCurrentTerm(requestVoteReply.getTerm());
			setFollower();
			
			
		} else if(requestVoteReply.getTerm() < node.nodeState.getCurrentTerm())
		{
			//do nothing
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

}
