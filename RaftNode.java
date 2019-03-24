
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import lib.GetStateReply;
import lib.LogEntries;
import lib.Message;
import lib.MessageHandling;
import lib.MessageType;
import lib.RaftUtilities;
import lib.StartReply;
import lib.State;
import lib.TransportLib;

public class RaftNode implements MessageHandling, Runnable {

	private static final int TIMEOUT_LOW = 250;
	private static final int TIMEOUT_HIGH = 500;

	private static final int START_VOTE = 1;

	
	boolean receivedRequest;
	public TransportLib lib;

	private static final int DEFAULT_INDEX = -1;
	private static final int DEFAULT_TERM = -1;

	int nodeId;
	public int numPeers;
	private int port;

	// random method for generating random heart beat timeouts
	Random random = new Random();
	int timeout;

	State nodeState;
	public int numOfVotes;
	public int majorityVotes;
	public ReentrantLock lock;

	public RaftNodeUtility raftNodeUtility;
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
		raftNodeUtility = new RaftNodeUtility();
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

			int lastLogIndex = raftNodeUtility.getLastIndex(this) + 1;

			// append the received command to leader
			this.nodeState.getLog().add(new LogEntries(command, lastLogIndex, 
					this.nodeState.getCurrentTerm()));
			this.nodeState.matchIndex[this.nodeId] = lastLogIndex;

			// send heartBeat
			heartbeatRPC();

			reply = new StartReply(lastLogIndex, this.nodeState.getCurrentTerm(), true);

			this.lock.unlock();

			return reply;

		}

		// if not a leader return default index and term

		reply = new StartReply(DEFAULT_INDEX, DEFAULT_TERM, false);

		this.lock.unlock();

		return reply;

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

	/**
	 * method to handle requestVote messages from nodes
	 * 
	 * @param requestVoteArgs
	 * @return
	 */
	public RequestVoteReply requestVoteRPC(RequestVoteArgs requestVoteArgs) {

		boolean vote = false;
		RequestVoteReply requestVoteReply = null;
		this.lock.lock();

		if (requestVoteArgs.terms > this.nodeState.getCurrentTerm()) {
			this.nodeState.setCurrentTerm(requestVoteArgs.terms);
			raftNodeUtility.setFollower(this);

		}

		// grant vote if the requesting candidate has more updated term
		// TODO check the error here when indexes do not match
		if (this.nodeState != null && (this.nodeState.getVotedFor() == null
				|| this.nodeState.getVotedFor() == requestVoteArgs.candidateId)) {

			LogEntries lastEntry = this.nodeState.getLastEntry();

			int lastTerms = 0;

			if (lastEntry != null) {
				lastTerms = lastEntry.getTerm();
			}

			if (lastTerms <= requestVoteArgs.lastLogTerm) {
				/* candidate’s log is at least as up-to-date as receiver’s log, grant vote */
				vote = true;
				this.nodeState.setVotedFor(requestVoteArgs.candidateId);
			}

		}
		requestVoteReply = new RequestVoteReply(this.nodeState.getCurrentTerm(), vote);

		raftNodeUtility.unlockCriticalSection(this);
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

			raftNodeUtility.unlockCriticalSection(this);

			return appendEntriesReply;
		}

		// Reply false if log doesn’t contain an entry at prevLogIndex
		// whose term matches prevLogTerm

		raftNodeUtility.checkMessageTerm(this,appendEntriesArgs);

		boolean lastCommitCheck = true;

		if (appendEntriesArgs.getPrevLogIndex() != 0 && appendEntriesArgs.getPrevLogTerm() != 0) {

			lastCommitCheck = raftNodeUtility.checkConsistency(this,appendEntriesArgs);

		}
		if (lastCommitCheck) {

			// consistency check has passed delete the existing entry and
			// all missing entries append entries from start

			success = true;

			raftNodeUtility.appendMissingLogs(this,appendEntriesArgs);

			raftNodeUtility.checkLeaderIndex(this,appendEntriesArgs);

			// send the reply
			appendEntriesReply = new AppendEntriesReply(this.nodeState.getCurrentTerm(), success);

			raftNodeUtility.unlockCriticalSection(this);

			return appendEntriesReply;

		} else {

			// consistency check

			appendEntriesReply = new AppendEntriesReply(this.nodeState.getCurrentTerm(), success);

			raftNodeUtility.unlockCriticalSection(this);

			return appendEntriesReply;
		}
	}

	@Override
	public void run() {

		// Listening socket for followers and leader

		while (true) {

			// wait for heart beat to be received from the designated leader
			if (nodeState != null && nodeState.getNodeState() != State.States.LEADER) {

				// generate random timeout

				timeout = random.nextInt(TIMEOUT_LOW) + (TIMEOUT_HIGH - TIMEOUT_LOW);

				synchronized (this.nodeState) {
					try {
						this.nodeState.wait(timeout);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

				if (nodeState.getNodeState() != State.States.LEADER) {

					if (!receivedRequest) {

						// if follower has not received heartBeat from the leader
						// convert to candidate and start the election
						this.nodeState.setNodeState(State.States.CANDIDATE);
						startElection();

					} else {
						receivedRequest = false;
					}

				}

			} else {
				try {
					// if the node is a leader
					// sleep for 100 ms
					Thread.sleep(100);

				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				this.lock.lock();

				// generate heartbeat RPC

				heartbeatRPC();

				this.lock.unlock();
			}
		}

	}

	/**
	 * start an election for the server
	 */
	public void startElection() {

		int serverNum = 0;

		// before starting an election the candidate increments the current term
		this.nodeState.setCurrentTerm(this.nodeState.getCurrentTerm() + 1);
		// vote for itself
		this.nodeState.setVotedFor(this.nodeId);
		this.numOfVotes = START_VOTE;

		// get the last index and term from its logs
		int lastIndex = 0;
		int lastTerm = 0;
		LogEntries lastEntry = this.nodeState.getLastEntry();
		if (lastEntry != null) {
			lastIndex = lastEntry.getIndex();
			lastTerm = lastEntry.getTerm();
		}

		// and send requestVote to each thread
		while (serverNum < numPeers) {

			// except itself
			if (serverNum != this.nodeId) {

				// generate request Vote arguments
				RequestVoteArgs args = new RequestVoteArgs(nodeState.getCurrentTerm(), nodeId, lastIndex, lastTerm);

				// create a new thread for handling election
				// instead of waiting for replies
				ElectionHandler electionHandler = new ElectionHandler(this, this.nodeId, serverNum, args);

				electionHandler.start();
			}
			serverNum++;
		}
	}

	/**
	 * method for generating hearbeat RPC messages to all the follower nodes
	 * 
	 */
	public void heartbeatRPC() {

		int serverNum = 0;
		// generate heart beat RPC message only if it is a leader
		if (this.nodeState.getNodeState() == State.States.LEADER) {

			// send heart beat message to all the peers
			while (serverNum < this.numPeers) {

				if (serverNum != this.nodeId) {

					// heartbeat message has previous index
					int prevIndex = this.nodeState.nextIndex[serverNum] - 1;

					// log entries for followers to compare
					ArrayList<LogEntries> logEntries = this.nodeState.retrieveLogs(prevIndex);

					// and previous terms
					int prevTerm = 0;
					if (prevIndex != 0) {
						prevTerm = this.nodeState.getLog().get(prevIndex - 1).getTerm();
					}

					// generate leader entries to send as a message
					AppendEntriesArgs entries = new AppendEntriesArgs(nodeState.getCurrentTerm(), this.nodeId,
							prevIndex, prevTerm, logEntries, this.nodeState.getCommitIndex());

					HeartbeatHandler thread = new HeartbeatHandler(this, this.nodeId, serverNum, entries);
					thread.start();

				}
				serverNum++;
			}
		}
	}

}
