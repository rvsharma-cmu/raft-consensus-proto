import java.rmi.RemoteException;

import lib.AppendEntriesArgs;
import lib.AppendEntryReply;
import lib.ApplyMsg;
import lib.LogEntry;
import lib.Message;
import lib.MessageType;
import lib.RaftUtilities;
import lib.State.States;

public class AppendEntriesThread extends Thread {

	RaftNode node;
	int startId;
	int endId;
	Message appendEntriesRequestMessage;
	Message appendEntriesReplyMessage;
	AppendEntriesArgs appendEntryArgs;

	public AppendEntriesThread(RaftNode node, int startID, int endID, AppendEntriesArgs arguments) {

		this.node = node;
		this.startId = startID;
		this.endId = endID;
		this.appendEntryArgs = arguments;
	}

	@Override
	public void run() {

		byte[] serializeMessage = RaftUtilities.serialize(this.appendEntryArgs);
		// TODO:check the src id once
		this.appendEntriesRequestMessage = new Message(MessageType.AppendEntriesArgs, this.node.getId(), this.endId,
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
		}
		this.node.lock.lock();
		AppendEntryReply appendEntryReply = (AppendEntryReply) RaftUtilities
				.deserialize(appendEntriesReplyMessage.getBody());
		if (appendEntryReply.term > node.nodeState.currentTerm) {
			// this is not the right leader
			node.nodeState.setCurrentTerm(appendEntryReply.term);
			node.nodeState.setNodeState(States.FOLLOWER);
			node.nodeState.setVotedFor(null);
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
		if (!appendEntryReply.success) {
			node.nodeState.nextIndex[endId] = node.nodeState.nextIndex[endId] - 1;

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
				nodeState.matchIndex[endId] = appendEntryArgs.entries.get(index).getIndex();
				nodeState.nextIndex[endId] = appendEntryArgs.entries.get(index).getIndex() + 1;
				int sizeOfLog = nodeState.log.size() - 1;
				LogEntry lastLogEntry = nodeState.log.peekLast();
				int lastIndexLogged = 0;
				if (lastLogEntry != null) {
					lastIndexLogged = lastLogEntry.getIndex();
				}
				for (int i = nodeState.commitIndex + 1; i <= lastIndexLogged; i++) {
					int count = 0;
					for (int j = 0; j < this.node.numPeers; j++) {
						if (nodeState.matchIndex[j] >= i) {
							count++;
						}
					}
					if (count > this.node.majorityVotes
							&& (nodeState.log.get(i - 1).getTerm() == nodeState.currentTerm)) {
						for (int k = nodeState.commitIndex + 1; k <= i; k++) {
							ApplyMsg applyMsg = new ApplyMsg(this.node.getId(), k, nodeState.log.get(k - 1).command,
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
