
import java.rmi.RemoteException;

import lib.ApplyMsg;
import lib.LogEntries;
import lib.Message;
import lib.MessageType;
import lib.RaftUtilities;

/**
 * @author abubber,rvsharma
 *
 */
public class HeartbeatHandler extends ThreadUtility {

	AppendEntriesArgs appendEntriesArgs;

	public HeartbeatHandler(RaftNode node, int startID, int endID, AppendEntriesArgs arguments) {

		super(node, startID, endID);
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
			// do nothing
		} else {
			this.node.lock.lock();
			AppendEntriesReply appendEntriesReply = (AppendEntriesReply) RaftUtilities
					.deserialize(replyMessage.getBody());
			if (appendEntriesReply.getTerm() > node.nodeState.getCurrentTerm()) {
				// this is not the right leader
				node.nodeState.setCurrentTerm(appendEntriesReply.getTerm());
				setFollower();

			}
			if (!appendEntriesReply.isSuccess()) {
				node.nodeState.nextIndex[destId] = node.nodeState.nextIndex[destId] - 1;

			} else {
				if (appendEntriesArgs.entries.size() > 0) {
					updateIndex();
				}
			}
			node.lock.unlock();
		}
		terminateThread();
		return;

	}

	
	public void updateIndex() {
		int index = appendEntriesArgs.entries.size() - 1;
		lib.State nodeState = this.node.nodeState;
		nodeState.matchIndex[destId] = appendEntriesArgs.entries.get(index).getIndex();
		nodeState.nextIndex[destId] = appendEntriesArgs.entries.get(index).getIndex() + 1;

		LogEntries lastLogEntry = null;
		if (nodeState.getLog() != null)
			lastLogEntry = nodeState.getLastEntry();
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
			if (count > this.node.majorityVotes
					&& (this.node.nodeState.getLog().get(i - 1).getTerm() == this.node.nodeState.getCurrentTerm())) {

				applyMessage(nodeState, i);
			}
		}
	}

	/**
	 * @param nodeState
	 * @param i
	 */
	public void applyMessage(lib.State nodeState, int i) {
		for (int k = nodeState.getCommitIndex() + 1; k <= i; k++) {
			ApplyMsg applyMsg = new ApplyMsg(this.node.getId(), k, nodeState.getLog().get(k - 1).getCommand(), false,
					null);
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
