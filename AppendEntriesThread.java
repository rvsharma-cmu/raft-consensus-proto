import java.rmi.RemoteException;

import lib.AppendEntriesArgs;
import lib.AppendEntriesReply;
import lib.ApplyMsg;
import lib.LogEntry;
import lib.Message;
import lib.MessageType;
import lib.RaftUtilities;
import lib.State.States;

public class AppendEntriesThread extends Thread {

	RaftNode node;
	int srcId;
	int destId;
	Message appendEntriesRequestMessage;
	Message appendEntriesReplyMessage;
	AppendEntriesArgs appendEntryArgs;

	public AppendEntriesThread(RaftNode node, int startID, int endID, AppendEntriesArgs arguments) {

		this.node = node;
		this.srcId = startID;
		this.destId = endID;
		this.appendEntryArgs = arguments;
	}

	@Override
	public void run() {

		byte[] serializeMessage = RaftUtilities.serialize(this.appendEntryArgs);
		// TODO:check the src id once
		this.appendEntriesRequestMessage = new Message(MessageType.AppendEntriesArgs, this.node.getId(), this.destId,
				serializeMessage);

		try {
			appendEntriesReplyMessage = this.node.lib.sendMessage(appendEntriesRequestMessage);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			// TODO: check this part of code
			try {
				this.join();
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return;
		}
		if (appendEntriesReplyMessage == null) {
			try {
				this.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		} else {
		this.node.lock.lock();
		AppendEntriesReply appendEntriesReply = (AppendEntriesReply) RaftUtilities
				.deserialize(appendEntriesReplyMessage.getBody());
		if (appendEntriesReply.term > node.nodeState.currentTerm) {
			// this is not the right leader
			node.nodeState.currentTerm= (appendEntriesReply.term);
			node.nodeState.setNodeState(States.FOLLOWER);
			node.nodeState.votedFor = null;
			node.numOfVotes = 0;
			node.lock.unlock();
			try {
				this.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;

		}
		if (!appendEntriesReply.success) {
			node.nodeState.nextIndex[destId] = node.nodeState.nextIndex[destId] - 1;

			node.lock.unlock();
			try {
				this.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		} else {
			if (appendEntryArgs.entries.size() > 0) {
				int index = appendEntryArgs.entries.size() - 1;
				lib.State nodeState = this.node.nodeState;
				nodeState.matchIndex[destId] = appendEntryArgs.entries.get(index).getIndex();
				nodeState.nextIndex[destId] = appendEntryArgs.entries.get(index).getIndex() + 1;
				
				LogEntry lastLogEntry = nodeState.log.peekLast();
				int lastIndexLogged = 0;
				if (lastLogEntry != null) {
					lastIndexLogged = lastLogEntry.getIndex();
				} else {
					lastIndexLogged = 0; 
				}
				for (int i = this.node.nodeState.commitIndex + 1; i <= lastIndexLogged; i++) {
					
					int count = 0;
					
					for (int j = 0; j < this.node.numPeers; j++) {
						if (this.node.nodeState.matchIndex[j] >= i) {
							count++;
						}
					}
					if (count > this.node.majorityVotes
							&& (this.node.nodeState.log.get(i - 1).getTerm() 
									== 
							this.node.nodeState.currentTerm)) {
						
						for (int k = nodeState.commitIndex + 1; k <= i; k++) {
							ApplyMsg applyMsg = 
									new ApplyMsg(this.node.getId(), k, nodeState.log.get(k - 1).command,
									false, null);
							try {
								this.node.lib.applyChannel(applyMsg);
							} catch (RemoteException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
								node.lock.unlock();
								try {
									this.join();
								} catch (InterruptedException e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
								}
								return;

							}
						}
						nodeState.commitIndex = i;
						nodeState.lastApplied = i;
					}
				}
			}
		}
		}
		node.lock.unlock();
		try {
			this.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return;

	}

}
