import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import lib.AppendEntriesArgs;
import lib.AppendEntriesReply;
import lib.ApplyMsg;
import lib.GetStateReply;
import lib.LogEntries;
import lib.Message;
import lib.MessageHandling;
import lib.MessageType;
import lib.RaftUtilities;
import lib.RequestVoteArgs;
import lib.RequestVoteReply;
import lib.StartReply;
import lib.State;
import lib.TransportLib;

public class RaftNode implements MessageHandling, Runnable {

	private static final int TIMEOUT_LOW = 100;
	private static final int TIMEOUT_HIGH = 200;

	boolean receivedRequest;
	public TransportLib lib;

	private static final int DEFAULT_INDEX = -1;
	private static final int DEFAULT_TERM = -1;

	private int nodeId;
	public int numPeers;
	private int port;

	// random method for generating random heart beat timeouts
	Random random = new Random();
	int timeout;

	State nodeState;
	public int numOfVotes;
	public int majorityVotes;
	public ReentrantLock lock;

	private Thread thread;

	public RaftNode(int port, int id_, int numPeers) {

		this.nodeId = id_;
		this.numPeers = numPeers;
		this.port = port;

		lock = new ReentrantLock();

		majorityVotes = this.numPeers / 2;
		receivedRequest = false;
		nodeState = new State(numPeers);

		numOfVotes = 0;
		lib = new TransportLib(this.port, this.nodeId, this);
		thread = new Thread(this);
		thread.start();
	}

	/**
	 * On request receipt from a client append entries to the followers
	 * 
	 * @param command The command received from the clients
	 */
	@Override
	public StartReply start(int command) {

		StartReply reply = null;

		this.lock.lock();

		if (this.nodeState.getNodeState() == State.States.LEADER) {

			// as a leader

			LinkedList<LogEntries> logs = this.nodeState.getLog();

			int lastLogIndex = getLastIndex(logs) + 1;

			// append the received command to leader
			logs.add(new LogEntries(command, lastLogIndex, this.nodeState.getCurrentTerm()));
			this.nodeState.matchIndex[this.nodeId] = lastLogIndex;

			// send heartBeat
			sendHeartbeats();

			reply = new StartReply(lastLogIndex, this.nodeState.getCurrentTerm(), true);

			this.lock.unlock();

			return reply;

		}

		// if not a leader return default index and term

		reply = new StartReply(DEFAULT_INDEX, DEFAULT_TERM, false);

		this.lock.unlock();

		return reply;

	}

	/**
	 * Function that returns the last log entry in the leader logs
	 * 
	 * @param lastEntry
	 * @param logs
	 * @return
	 */
	public int getLastIndex(LinkedList<LogEntries> logs) {

		LogEntries lastEntry = null;

		if (logs != null)
			lastEntry = logs.peekLast();

		int prevLastIndex = 0;
		if (lastEntry != null)
			prevLastIndex = lastEntry.getIndex();
		return prevLastIndex;
	}

	@Override
	public GetStateReply getState() {

		boolean isLeader = false;
		if (this.nodeState.getNodeState() == State.States.LEADER)
			isLeader = true;
		GetStateReply reply = new GetStateReply(this.nodeState.getCurrentTerm(), isLeader);
		return reply;
	}

	/**
	 * Receive the message from raftNode and check the type of the message and reply
	 * to either the requestVote message or AppendEntries message That message will
	 * be delivered to the destination node via the deliverMessage callback you
	 * implemented. Then you need to unpack the Message, get the request, process
	 * it, and return a reply wrapped in the Message.
	 * 
	 * @param message This is a message received from the source raft node
	 * @return Message message to be replied to the source raft node
	 */
	@Override
	public Message deliverMessage(Message message) {

		MessageType type = message.getType();
		int msgSrcId = message.getSrc();

		int msgDstId = message.getDest();

		Object requestArgs = RaftUtilities.deserialize(message.getBody());

		Message replyMessage = null;
		byte[] replyPayload = null;

		if (type == MessageType.AppendEntriesArgs) {

			AppendEntriesReply reply = appendEntryRPC((AppendEntriesArgs) requestArgs);
			replyPayload = RaftUtilities.serialize(reply);
			replyMessage = new Message(MessageType.AppendEntryReply, msgDstId, msgSrcId, replyPayload);

		}

		if (type == MessageType.RequestVoteArgs) {
			RequestVoteReply reply = requestVoteRPC((RequestVoteArgs) requestArgs);
			replyPayload = RaftUtilities.serialize(reply);
			replyMessage = new Message(MessageType.RequestVoteReply, msgDstId, msgSrcId, replyPayload);

		}

		return replyMessage;
	}

	public static void main(String args[]) throws Exception {
		if (args.length != 3)
			throw new Exception("Need 2 args: <port> <id> <num_peers>");
		RaftNode UN = new RaftNode(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
	}

	public int getId() {
		return this.nodeId;
	}

	public RequestVoteReply requestVoteRPC(RequestVoteArgs requestVoteArgs) {

		boolean vote = false;
		RequestVoteReply requestVoteReply = null;
		this.lock.lock();
		
		

		if (requestVoteArgs.terms > this.nodeState.getCurrentTerm()) {
			this.nodeState.setCurrentTerm(requestVoteArgs.terms);
			setFollower();

		}
		
		// grant vote if the requesting candidate has more updated term 
		//TODO check the error here when indexes do not match 
		if (this.nodeState != null && (requestVoteArgs.terms == this.nodeState.getCurrentTerm())&&
				(this.nodeState.getVotedFor() == null || 
				this.nodeState.getVotedFor() == requestVoteArgs.candidateId)) {

			LogEntries logEntries = this.nodeState.getLog().peekLast();
			
			int lastIndex = 0;
			int lastTerms = 0;
			
			if (logEntries != null) {
				lastIndex = logEntries.getIndex();
				lastTerms = logEntries.getTerm();
			}

			// or whichever is the longer length entry 
			if (lastTerms <= requestVoteArgs.lastLogTerm || 
					lastIndex <= requestVoteArgs.lastLogIndex) {
				
				vote = true;
				this.nodeState.setVotedFor(requestVoteArgs.candidateId);
				
			}


		}
		requestVoteReply = new RequestVoteReply(this.nodeState.getCurrentTerm(), vote);

		unlockCriticalSection();
		return requestVoteReply;
	}

	/**
	 * Handle AppendEntries RPC request
	 * 
	 * @param appendEntriesArgs AppendEntries RPC's args
	 * @return The reply message to the invoking method
	 */
	public AppendEntriesReply appendEntryRPC(AppendEntriesArgs appendEntriesArgs) {
		AppendEntriesReply appendEntriesReply = null;

		boolean success = false;

		this.lock.lock();

		// Reply false if term < currentTerm (5.1)

		if (appendEntriesArgs.getTerm() < this.nodeState.getCurrentTerm()) {

			appendEntriesReply = new AppendEntriesReply(this.nodeState.getCurrentTerm(), success);

			unlockCriticalSection();

			return appendEntriesReply;
		}

		// Reply false if log doesn’t contain an entry at prevLogIndex
		// whose term matches prevLogTerm

		checkMessageTerm(appendEntriesArgs);

		//TODO:Refactor this part
		boolean lastCommitCheck = false;

		if (appendEntriesArgs.getPrevLogIndex() != 0 && appendEntriesArgs.getPrevLogTerm() != 0) {

			lastCommitCheck = checkConsistency(appendEntriesArgs, lastCommitCheck);

		} else {

			lastCommitCheck = true;
		}

		if (lastCommitCheck) {

			// consistency check has passed delete the existing entry and
			// all missing entries append entries from start

			success = true;

			appendMissingLogs(appendEntriesArgs);

			checkLeaderIndex(appendEntriesArgs);

			// send the reply
			appendEntriesReply = new AppendEntriesReply(this.nodeState.getCurrentTerm(), success);

			unlockCriticalSection();

			return appendEntriesReply;

		} else {

			// consistency check

			appendEntriesReply = new AppendEntriesReply(this.nodeState.getCurrentTerm(), success);

			unlockCriticalSection();

			return appendEntriesReply;
		}
	}

	@Override
	public void run() {

		while (true) {
			if (nodeState != null && nodeState.getNodeState() == State.States.LEADER) {

				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				this.lock.lock();
				sendHeartbeats();
				this.lock.unlock();

			} else {

				timeout = random.nextInt(TIMEOUT_LOW) + (TIMEOUT_HIGH - TIMEOUT_LOW);

				synchronized (this.nodeState) {
					try {
						this.nodeState.wait(timeout);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				if (nodeState.getNodeState() != State.States.LEADER) {

					if (receivedRequest) {
						receivedRequest = false;
						continue;
					}
					this.lock.lock();
					this.nodeState.setNodeState(State.States.CANDIDATE);
					startElection();
					this.lock.unlock();

				}
			}
		}

	}

	public void sendHeartbeats() {

		if (this.nodeState.getNodeState() != State.States.LEADER) {
			return;
		} else {
			for (int threadNum = 0; threadNum < this.numPeers; threadNum++) {
				if (threadNum == this.nodeId)
					continue;
				int nextIndex = this.nodeState.nextIndex[threadNum];
				LinkedList<LogEntries> logEntries = this.retrieveLogs(nodeState.getLog(), nextIndex - 1);

				int prevIndex = nextIndex - 1;
				int prevTerm = 0;
				if (prevIndex != 0) {
					prevTerm = this.nodeState.getLog().get(prevIndex - 1).getTerm();
				}
				AppendEntriesArgs entries = new AppendEntriesArgs(nodeState.getCurrentTerm(), this.nodeId, prevIndex,
						prevTerm, logEntries, this.nodeState.getCommitIndex());

				HeartbeatHandler thread = new HeartbeatHandler(this, this.nodeId, threadNum, entries);
				thread.start();

			}
		}
	}

	public void startElection() {

		int lastIndex = 0;
		int lastTerm = 0;

		this.nodeState.setCurrentTerm(this.nodeState.getCurrentTerm() + 1);
		this.nodeState.setVotedFor(this.nodeId);
		this.numOfVotes = 0;
		this.numOfVotes++;

		timeout = random.nextInt(TIMEOUT_LOW) + (TIMEOUT_HIGH - TIMEOUT_LOW);

		LogEntries logEntries = this.nodeState.getLog().peekLast();
		if (logEntries != null) {
			lastIndex = logEntries.getIndex();
			lastTerm = logEntries.getTerm();
		}
		for (int threadNumber = 0; threadNumber < numPeers; threadNumber++) {
			if (threadNumber == this.nodeId)
				continue;

			RequestVoteArgs args = new RequestVoteArgs(nodeState.getCurrentTerm(), nodeId, lastIndex, lastTerm);

			ElectionThread electionThread = new ElectionThread(this, this.nodeId, threadNumber, args);

			electionThread.start();
		}
	}

	/*
	 * Helper functions
	 */

	/**
	 * Set this node as a Follower and reset its vote and votedFor states
	 */
	public void setFollower() {
		this.nodeState.setNodeState(State.States.FOLLOWER);
		this.nodeState.setVotedFor(null);
		// this.numOfVotes = 0;
	}

	/**
	 * Check the index of the leader If leaderCommit > commitIndex, set commitIndex
	 * = min(leaderCommit, index of last new entry)
	 *
	 * @param appendEntriesArgs
	 */
	public void checkLeaderIndex(AppendEntriesArgs appendEntriesArgs) {

		int nodeCommitIndex = this.nodeState.getCommitIndex();

		LinkedList<LogEntries> getNodeLogs = this.nodeState.getLog();

		// If leaderCommit > commitIndex, set commitIndex =
		// min(leaderCommit, index of last new entry)

		if (appendEntriesArgs!=null && appendEntriesArgs.leaderCommit > nodeCommitIndex) {

			if (getNodeLogs != null && getNodeLogs.peekLast() != null) {

				int commitIndex = Math.min(appendEntriesArgs.leaderCommit, getNodeLogs.getLast().getIndex());

				while (nodeCommitIndex + 1 <= commitIndex) {
					LogEntries entry = getNodeLogs.get(nodeCommitIndex);

					ApplyMsg msg = new ApplyMsg(this.nodeId, entry.getIndex(), entry.getCommand(), false, null);

					try {
						this.lib.applyChannel(msg);
					} catch (RemoteException e) {

						this.lock.unlock();

						e.printStackTrace();
						return;
					}

					this.nodeState.setCommitIndex(nodeCommitIndex + 1);
					this.nodeState.setLastApplied(nodeCommitIndex + 1);
					nodeCommitIndex++;
				}
			}
		}
	}

	/**
	 * Append missing logs from the leader to follower
	 * 
	 * @param appendEntriesArgs
	 * 
	 * @param leaderLogs        logs which are passed from the leader to the
	 *                          follower
	 */
	public void appendMissingLogs(AppendEntriesArgs appendEntriesArgs) {

		LinkedList<LogEntries> leaderLogs = appendEntriesArgs.entries;
		for (int i = 0; i < leaderLogs.size(); i++) {
			LogEntries entry = leaderLogs.get(i);
			LinkedList<LogEntries> logs = this.nodeState.getLog();
			if (logs.size() < entry.getIndex()) {
				logs.add(entry);
			} else {
				LogEntries logEntry = logs.get(entry.getIndex() - 1);
				if (logEntry.getTerm() == entry.getTerm()) {
					continue;
				} else {
					for (int j = logs.size() - 1; j >= entry.getIndex() - 1; j--) {
						logs.remove(j);
					}
					logs.add(entry);
				}
			}

		}
	}

	public boolean checkConsistency(AppendEntriesArgs appendEntriesArgs, boolean lastCommitCheck) {
		LogEntries prevLogEntry = null;
		if (this.nodeState.getLog() != null && this.nodeState.getLog().size() >= appendEntriesArgs.getPrevLogIndex()) {

			prevLogEntry = this.nodeState.getLog().get(appendEntriesArgs.getPrevLogIndex() - 1);

			if (prevLogEntry.getIndex() == appendEntriesArgs.getPrevLogIndex()
					&& prevLogEntry.getTerm() == appendEntriesArgs.getPrevLogTerm()) {

				lastCommitCheck = true;
			}
		}
		return lastCommitCheck;
	}

	public void checkMessageTerm(AppendEntriesArgs appendEntriesArgs) {

		// if RPC request or response contains term
		// term T > currentTerm : set currentTerm = T
		// convert to FOLLOWER

		if (appendEntriesArgs.getTerm() > this.nodeState.getCurrentTerm()) {
			this.nodeState.setCurrentTerm(appendEntriesArgs.getTerm());
			this.nodeState.setNodeState(State.States.FOLLOWER);
		}
	}

	/*
	 * Generic class Helper functions
	 */

	/**
	 * Method to unlock the critical section
	 */
	public void unlockCriticalSection() {
		this.receivedRequest = true;
		this.lock.unlock();
	}

	/**
	 * Retrieve entry logs from the given index
	 * 
	 * @param serverEntries -
	 * @param index         - index from which to retrieve the entry logs
	 * @return
	 */
	public LinkedList<LogEntries> retrieveLogs(List<LogEntries> serverEntries, int index) {

		LinkedList<LogEntries> resultLogs = new LinkedList<LogEntries>();

		if (serverEntries.size() > index)
			for (int i = index; i < serverEntries.size(); i++) {
				resultLogs.add(serverEntries.get(i));
			}

		return resultLogs;
	}
}
