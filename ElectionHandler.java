


import java.rmi.RemoteException;

import lib.LogEntries;
import lib.Message;
import lib.MessageType;
import lib.RaftUtilities; 

public class ElectionHandler extends ThreadUtility {
	
	private RequestVoteReply requestVoteReply;


	
	public ElectionHandler(RaftNode node, int start, int end, RequestVoteArgs args) {
		
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
		
		if(requestVoteReply.getTerm() > node.nodeState.getCurrentTerm()) {
			// if the server has a smaller term the convert to follower
			
			node.nodeState.setCurrentTerm(requestVoteReply.getTerm());
			
			setFollower();
			
		} else {
			
			synchronized (node.nodeState) {
				if(requestVoteReply.isVoteGranted() 
						&& node.nodeState.getNodeState() == lib.State.States.CANDIDATE){
					node.numOfVotes++;
					node.receivedRequest = true;
					
					if(node.numOfVotes > node.majorityVotes)
					{
						node.nodeState.setNodeState(lib.State.States.LEADER);
						
						LogEntries lastEntry = node.nodeState.getLastEntry();
						int lastIndex = 0;
						if(lastEntry != null)
						{
							lastIndex = lastEntry.getIndex();
						}
						for(int i=0 ;i < node.numPeers; i++){
							node.nodeState.nextIndex[i] = lastIndex+1;
						}
						node.heartbeatRPC();
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
