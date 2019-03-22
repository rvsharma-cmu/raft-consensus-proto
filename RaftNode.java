import java.rmi.RemoteException;
import java.util.ArrayList;
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

	private static final int TIMEOUT_LOW = 250;
	private static final int TIMEOUT_HIGH = 500;
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

		majorityVotes = numPeers / 2;

		lock = new ReentrantLock();

		receivedRequest = false;
		nodeState = new State(numPeers);

		numOfVotes = 0;
		lib = new TransportLib(this.port, this.nodeId, this);
		thread = new Thread(this);
		thread.start();
	}

	@Override
	public StartReply start(int command) {
		this.lock.lock();

		StartReply reply = null;
		if (this.nodeState.getNodeState() != State.States.LEADER) {
			reply = new StartReply(DEFAULT_INDEX, DEFAULT_TERM, false);

			this.lock.unlock();

			return reply;
		}

		LogEntries logEntries = null;
		if(this.nodeState.getLog()!=null)
			logEntries = this.nodeState.getLog().peekLast();
		int prevLastIndex = 0;
		if (logEntries != null)
			prevLastIndex = logEntries.getIndex();

		int lastLogIndex = prevLastIndex + 1;

		for (int i = 0; i < numPeers; i++) {
			this.nodeState.matchIndex[i] = 0;
		}

		LogEntries currentEntry = new LogEntries(command, prevLastIndex + 1, this.nodeState.getCurrentTerm());
		this.nodeState.getLog().add(currentEntry);
		this.nodeState.matchIndex[this.nodeId] = prevLastIndex + 1; // Update it for itself

		sendHeartbeats();

		reply = new StartReply(lastLogIndex, this.nodeState.getCurrentTerm(), true);

		this.lock.unlock();

		return reply;

	}

	@Override
	public GetStateReply getState() {

		GetStateReply reply = new GetStateReply(this.nodeState.getCurrentTerm(),
				(this.nodeState.getNodeState() == State.States.LEADER));
		return reply;
	}

	@Override
	public Message deliverMessage(Message message) {

		Message replyMessage = null;

		MessageType type = message.getType();

		int msgSrcId = message.getSrc();

		int msgDstId = message.getDest();

		Object requestArgs = RaftUtilities.deserialize(message.getBody());

		byte[] replyPayload = null;

		if (type == MessageType.RequestVoteArgs) {
			RequestVoteReply reply = requestVoteHandle((RequestVoteArgs) requestArgs);
			replyPayload = RaftUtilities.serialize(reply);
			replyMessage = new Message(MessageType.RequestVoteReply, msgDstId, msgSrcId, replyPayload);

		} else if (type == MessageType.AppendEntriesArgs) {

			AppendEntriesReply reply = appendEntryRPC((AppendEntriesArgs) requestArgs);
			replyPayload = RaftUtilities.serialize(reply);
			replyMessage = new Message(MessageType.AppendEntryReply, msgDstId, msgSrcId, replyPayload);

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

	public RequestVoteReply requestVoteHandle(RequestVoteArgs requestVoteArgs) {
		RequestVoteReply requestVoteReply;
		boolean ifGranted = false;

		this.lock.lock();

		if (requestVoteArgs.terms < this.nodeState.getCurrentTerm()) {
			requestVoteReply = new RequestVoteReply(this.nodeState.getCurrentTerm(), ifGranted);

			this.receivedRequest = true;
			this.lock.unlock();

			return requestVoteReply;
		}

		if (requestVoteArgs.terms > this.nodeState.getCurrentTerm()) {
			this.nodeState.setCurrentTerm(requestVoteArgs.terms);
			this.nodeState.setNodeState(State.States.FOLLOWER);
			this.nodeState.setVotedFor(null);
			this.numOfVotes = 0;
		}
		if (this.nodeState.getVotedFor() == null || this.nodeState.getVotedFor() == requestVoteArgs.candidateId) {

			LogEntries logEntries = this.nodeState.getLog().peekLast();
			int currentLastIndex = 0;
			int currentLastTerm = 0;
			if (logEntries != null) {
				currentLastIndex = logEntries.getIndex();
				currentLastTerm = logEntries.getTerm();
			} else {
				currentLastIndex = 0;
				currentLastTerm = 0;
			}

			if (currentLastTerm != requestVoteArgs.lastLogTerm) {
				if (currentLastTerm <= requestVoteArgs.lastLogTerm) {
					/* candidate’s log is at least as up-to-date as receiver’s log, grant vote */
					ifGranted = true;
					this.nodeState.setVotedFor(requestVoteArgs.candidateId);
				}
			} else {
				if (currentLastIndex <= requestVoteArgs.lastLogIndex) {
					ifGranted = true;
					this.nodeState.setVotedFor(requestVoteArgs.candidateId);
				}
			}

		}
		requestVoteReply = new RequestVoteReply(this.nodeState.getCurrentTerm(), ifGranted);

		this.receivedRequest = true;
		this.lock.unlock();
		return requestVoteReply;
	}

	/**
	 * Handle AppendEntries RPC request
	 * 
	 * @param appendEntriesArgs AppendEntries RPC's args
	 * @return	The reply message to the invoking method 
	 */
	public AppendEntriesReply appendEntryRPC(AppendEntriesArgs appendEntriesArgs) {
		AppendEntriesReply appendEntriesReply = null;

		boolean success;

		this.lock.lock();

		// Reply false if term < currentTerm (5.1) 
		
		if (appendEntriesArgs.getTerm() < this.nodeState.getCurrentTerm()) {
			
			success = false; 
			
			appendEntriesReply = new AppendEntriesReply(this.nodeState.getCurrentTerm(), success);

			this.receivedRequest = true;
			this.lock.unlock();

			return appendEntriesReply;
		}

		checkMessageTerm(appendEntriesArgs);

		int prevIndex = appendEntriesArgs.getPrevLogIndex();
		int prevTerm = appendEntriesArgs.getPrevLogTerm();
		boolean lastCommitCheck = false;
		LogEntries prevLogEntry = null;
		if (prevIndex == 0 && prevTerm == 0) {
			lastCommitCheck = true;
		} else {

			if (this.nodeState.getLog()!=null && this.nodeState.getLog().size() >= prevIndex) {
				prevLogEntry = this.nodeState.getLog().get(prevIndex - 1);
				if (prevLogEntry.getIndex() == prevIndex
						&& prevLogEntry.getTerm() == prevTerm) {
					lastCommitCheck = true;
				}
			}

		}
		if (!lastCommitCheck) {
			
			success = false; 

			appendEntriesReply = new AppendEntriesReply(this.nodeState.getCurrentTerm(), success);

			this.receivedRequest = true;
			this.lock.unlock();

			return appendEntriesReply;
			
		} else {

			success = true;
			ArrayList<LogEntries> leaderLogs = appendEntriesArgs.entries;

			if (leaderLogs.size() == 0) {
				for (int j = this.nodeState.getLog().size() - 1; j >= prevIndex; j--) {
					this.nodeState.getLog().remove(j);
				}
			}
			//TODO:Raghav to refactor

			for (int i = 0; i < leaderLogs.size(); i++) {
				LogEntries entry = leaderLogs.get(i);
				if (this.nodeState.getLog().size() < entry.getIndex()) {
					this.nodeState.getLog().add(entry);
				} else {
					LogEntries my_entry = this.nodeState.getLog().get(entry.getIndex() - 1);
					if (my_entry.getTerm() == entry.getTerm()) {
						continue;
					} else {
						for (int j = this.nodeState.getLog().size() - 1; j >= entry.getIndex() - 1; j--) {
							this.nodeState.getLog().remove(j);
						}
						this.nodeState.getLog().add(entry);
					}
				}

			}

			
			//TODO:RAGHAV to refactor
			/*
			 * If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of
			 * last new entry)
			 */
			if (appendEntriesArgs.leaderCommit > this.nodeState.getCommitIndex()) {

				if (this.nodeState.getLog()!=null && this.nodeState.getLog().peekLast() != null) {
					int lastIndex = this.nodeState.getLog().getLast().getIndex();// leader_logs.size() == 0 ? 0 :
																		// this.node_state.log.getLast().index;
					int update_commitIndex = Math.min(appendEntriesArgs.leaderCommit, lastIndex);
					for (int i = this.nodeState.getCommitIndex() + 1; i <= update_commitIndex; i++) {
						LogEntries entry = this.nodeState.getLog().get(i - 1);
						ApplyMsg msg = new ApplyMsg(this.nodeId, entry.getIndex(), entry.getCommand(), false, null);
						try {
							this.lib.applyChannel(msg);
						} catch (RemoteException e) {

							this.lock.unlock();

							e.printStackTrace();
						}
						this.nodeState.setCommitIndex(i);
						this.nodeState.setLastApplied(i);
					}
				}
			}
			appendEntriesReply = new AppendEntriesReply(this.nodeState.getCurrentTerm(), success);

			this.receivedRequest = true;

			this.lock.unlock();


			return appendEntriesReply;
		}
	}

	public void checkMessageTerm(AppendEntriesArgs appendEntriesArgs) {
		// if RPC request or response contains term 
		// term T > currentTerm  : set currentTerm = T
		// convert to FOLLOWER
		
		if (appendEntriesArgs.getTerm() > this.nodeState.getCurrentTerm()
				|| this.nodeState.getNodeState() == State.States.CANDIDATE) {
			this.nodeState.setCurrentTerm(appendEntriesArgs.getTerm());
			this.nodeState.setNodeState(State.States.FOLLOWER);
			this.nodeState.setVotedFor(null); 
			this.numOfVotes = 0;
		}
	}

	public ArrayList<LogEntries> retrieveLogs(List<LogEntries> serverEntries, int index) {

		ArrayList<LogEntries> resultLogs = new ArrayList<LogEntries>();

		int logLength = serverEntries.size();
		if (logLength < index)
			return resultLogs;
		for (int i = index - 1; i < logLength; i++) {
			resultLogs.add(serverEntries.get(i));
		}
		return resultLogs;
	}

	@Override
	public void run() {

		while (true) {
			if (nodeState!=null && nodeState.getNodeState() == State.States.LEADER) {

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
			int threadNum = 0;

			for (; threadNum < this.numPeers; threadNum++) {
				if (threadNum == this.nodeId)
					continue;
				int nextIndex = this.nodeState.nextIndex[threadNum];
				ArrayList<LogEntries> logEntries = this.retrieveLogs(nodeState.getLog(), nextIndex);

				int prevIndex = nextIndex - 1;
				int prevTerm;
				if (prevIndex != 0) {
					prevTerm = this.nodeState.getLog().get(prevIndex - 1).getTerm();
				} else {
					prevTerm = 0;
				}
				AppendEntriesArgs entries = new AppendEntriesArgs(nodeState.getCurrentTerm(), this.nodeId, prevIndex, prevTerm,
						logEntries, this.nodeState.getCommitIndex());

				AppendEntriesThread thread = new AppendEntriesThread(this, this.nodeId, threadNum, entries);
				thread.start();

			}
		}
	}

	public void startElection() {

		this.nodeState.setCurrentTerm(this.nodeState.getCurrentTerm()+1);
		int lastIndex = 0;
		int lastTerm = 0;
		int threadNumber = 0;

		nodeState.setVotedFor(this.nodeId);
		numOfVotes = 0;
		numOfVotes++;

		timeout = random.nextInt(TIMEOUT_LOW) + (TIMEOUT_HIGH - TIMEOUT_LOW);

		LogEntries logEntries = this.nodeState.getLog().peekLast();
		if (logEntries != null) {
			lastIndex = logEntries.getIndex();
			lastTerm = logEntries.getTerm();
		} 
		for ( ; threadNumber < numPeers; threadNumber++) {
			if (threadNumber == this.nodeId)
				continue;

			RequestVoteArgs args = new RequestVoteArgs(nodeState.getCurrentTerm(), nodeId, lastIndex, lastTerm);

			ElectionThread electionThread = new ElectionThread(this, this.nodeId, threadNumber, args);

			electionThread.start();
		}
	}
}
