import java.rmi.RemoteException;

import lib.AppendEntriesArgs;
import lib.AppendEntriesReply;
import lib.ApplyMsg;
import lib.LogEntries;
import lib.Message;
import lib.MessageType;
import lib.RaftUtilities;
import lib.State.States;

public class AppendEntriesThread extends ThreadUtility {

	AppendEntriesArgs appendEntriesArgs;

	public AppendEntriesThread(RaftNode node, int startID, int endID, AppendEntriesArgs arguments) {

		this.node = node;
		this.sourceId = startID;
		this.destId = endID;
		this.appendEntriesArgs = arguments;
	}

	@Override
	public void run() {

		byte[] serializeMessage = RaftUtilities.serialize(this.appendEntriesArgs);
		this.requestMessage = new Message(MessageType.AppendEntriesArgs, this.sourceId, this.destId, serializeMessage);

		try {
			replyMessage = this.node.lib.sendMessage(requestMessage);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			terminateThread();
			return;
		}
		if (replyMessage == null) {
			terminateThread();
			return;
		} else {
			this.node.lock.lock();
			AppendEntriesReply appendEntriesReply = (AppendEntriesReply) RaftUtilities
					.deserialize(replyMessage.getBody());
			if (appendEntriesReply.getTerm() > node.nodeState.getCurrentTerm()) {
				// this is not the right leader
				node.nodeState.setCurrentTerm(appendEntriesReply.getTerm());
				node.nodeState.setNodeState(States.FOLLOWER);
				node.nodeState.setVotedFor(null);
				node.numOfVotes = 0;
				node.lock.unlock();
				terminateThread();
				return;

			}
			if (!appendEntriesReply.isSuccess()) {
				node.nodeState.nextIndex[destId] = node.nodeState.nextIndex[destId] - 1;

				node.lock.unlock();
				terminateThread();
				return;
			} else {
				if (appendEntriesArgs.entries.size() > 0) {
					int index = appendEntriesArgs.entries.size() - 1;
					lib.State nodeState = this.node.nodeState;
					nodeState.matchIndex[destId] = appendEntriesArgs.entries.get(index).getIndex();
					nodeState.nextIndex[destId] = appendEntriesArgs.entries.get(index).getIndex() + 1;

					LogEntries lastLogEntry = null;
					if (nodeState.getLog() != null)
						lastLogEntry = nodeState.getLog().peekLast();
					int lastIndexLogged = 0;
					if (lastLogEntry != null) {
						lastIndexLogged = lastLogEntry.getIndex();
					}
					for (int i = this.node.nodeState.getCommitIndex() + 1; i <= lastIndexLogged; i++) {

						int count = 0;

						for (int j = 0; j < this.node.numPeers; j++) {
							if (this.node.nodeState.matchIndex[j] >= i) {
								count++;
							}
						}
						if (count > this.node.majorityVotes && (this.node.nodeState.getLog().get(i - 1)
								.getTerm() == this.node.nodeState.getCurrentTerm())) {

							for (int k = nodeState.getCommitIndex() + 1; k <= i; k++) {
								ApplyMsg applyMsg = new ApplyMsg(this.node.getId(), k,
										nodeState.getLog().get(k - 1).getCommand(), false, null);
								try {
									this.node.lib.applyChannel(applyMsg);
								} catch (RemoteException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
									node.lock.unlock();
									terminateThread();
									return;

								}
							}
							nodeState.setCommitIndex(i);
							nodeState.setLastApplied(i);
						}
					}
				}
			}
		}
		node.lock.unlock();
		terminateThread();
		return;

	}

}
