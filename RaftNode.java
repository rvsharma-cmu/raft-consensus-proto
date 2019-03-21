import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import lib.AppendEntriesArgs;
import lib.AppendEntriesReply;
import lib.ApplyMsg;
import lib.GetStateReply;
import lib.LogEntry;
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

	boolean receivedRequest;
	public TransportLib lib;

	private int nodeId;
	public int numPeers;
	private int port;

	// random method for generating random heart beat timeouts
	Random random = new Random();
	int timeout;

	// state variable for this node

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

	/*
	 * call back.
	 */
	@Override
	public StartReply start(int command) {
		this.lock.lock();

		StartReply reply = null;
		if (this.nodeState.getNodeState() != State.States.LEADER) {
			reply = new StartReply(-1, -1, false);

			this.lock.unlock();

			return reply;
		}
		System.out.println(System.currentTimeMillis()+"Node "+this.nodeId+" Client Submit Commad "+command);
        
		LogEntry logEntry = this.nodeState.log.peekLast();
		int prevLastIndex = 0;
		if (logEntry != null)
			prevLastIndex = logEntry.getIndex();
		else 
			prevLastIndex = 0;
		int lastLogIndex = prevLastIndex + 1;

		for (int i = 0; i < numPeers; i++) {
			this.nodeState.matchIndex[i] = 0;
		}

		LogEntry cur_entry = new LogEntry(command, prevLastIndex + 1, this.nodeState.currentTerm);
		this.nodeState.log.add(cur_entry);
		this.nodeState.matchIndex[this.nodeId] = prevLastIndex + 1; // Update it for itself

		sendHeartbeats();

		reply = new StartReply(lastLogIndex, this.nodeState.currentTerm, true);

		this.lock.unlock();

		return reply;

	}

	@Override
	public GetStateReply getState() {

		GetStateReply reply = new GetStateReply(this.nodeState.currentTerm,
				(this.nodeState.getNodeState() == State.States.LEADER));
		return reply;
	}

	@Override
	public Message deliverMessage(Message message) {
		
		Message respond_message = null;
		
		MessageType type = message.getType();
		
		int src_id = message.getSrc();
		
		int dest_id = message.getDest(); // This RaftNode's ID
		
		if (type == MessageType.RequestVoteArgs) {
			/* Request For Vote */
			RequestVoteArgs req_args = (RequestVoteArgs) RaftUtilities.deserialize(message.getBody());
			RequestVoteReply reply = requestVoteHandle(req_args);
			byte[] payload = RaftUtilities.serialize(reply);
			respond_message = new Message(MessageType.RequestVoteReply, dest_id, src_id, payload);

		} else if (type == MessageType.AppendEntriesArgs) {

			AppendEntriesArgs append_args = (AppendEntriesArgs) RaftUtilities.deserialize(message.getBody());
			AppendEntriesReply reply = AppendEntriesHandle(append_args);
			byte[] payload = RaftUtilities.serialize(reply);
			respond_message = new Message(MessageType.AppendEntryReply, dest_id, src_id, payload);

		}
		return respond_message;
	}

	// main function
	public static void main(String args[]) throws Exception {
		if (args.length != 3)
			throw new Exception("Need 2 args: <port> <id> <num_peers>");
//		System.out.println(Integer.parseInt(args[0]));
//		System.out.println(Integer.parseInt(args[1]));
//		System.out.println(Integer.parseInt(args[2]));
		RaftNode UN = new RaftNode(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
	}

	public int getId() {
		return this.nodeId;
	}

	public RequestVoteReply requestVoteHandle(RequestVoteArgs req_args) {
		RequestVoteReply req_vote_reply;
		boolean if_granted = false;

		this.lock.lock();

		/*
		 * 1. Reply false if term < currentTerm
		 */
		if (req_args.terms < this.nodeState.currentTerm) {
			req_vote_reply = new RequestVoteReply(this.nodeState.currentTerm, if_granted);

			this.receivedRequest = true;
			this.lock.unlock();

			return req_vote_reply;
		}

		if (req_args.terms > this.nodeState.currentTerm) {
			this.nodeState.currentTerm = req_args.terms;
			this.nodeState.setNodeState(State.States.FOLLOWER);
			this.nodeState.votedFor = null; // New Term has Began
			this.numOfVotes = 0;
		}
		if (this.nodeState.votedFor == null || this.nodeState.votedFor == req_args.candidateId) {

			
			LogEntry logEntry = this.nodeState.log.peekLast();
			int currentLastIndex = 0;
			int currentLastTerm = 0;
			if (logEntry != null) {
				currentLastIndex = logEntry.getIndex();
				currentLastTerm = logEntry.getTerm();
			} else {
				currentLastIndex = 0;
				currentLastTerm = 0;
			}

			if (currentLastTerm != req_args.lastLogTerm) {
				if (currentLastTerm <= req_args.lastLogTerm) {
					/* candidate’s log is at least as up-to-date as receiver’s log, grant vote */
					if_granted = true;
					this.nodeState.votedFor = req_args.candidateId;
				}
			} else {
				if (currentLastIndex <= req_args.lastLogIndex) {
					if_granted = true;
					this.nodeState.votedFor = req_args.candidateId;
				}
			}

		}
		req_vote_reply = new RequestVoteReply(this.nodeState.currentTerm, if_granted);

		this.receivedRequest = true;
		this.lock.unlock();
		return req_vote_reply;
	}

	/**
	 * Handle AppendEntries RPC request
	 * 
	 * @param req_args AppendEntries RPC's args
	 * @return
	 */
	public AppendEntriesReply AppendEntriesHandle(AppendEntriesArgs req_args) {
		AppendEntriesReply append_entry_reply;
		boolean if_success = false;

		this.lock.lock();

		if (req_args.getTerm() < this.nodeState.currentTerm) {
			append_entry_reply = new AppendEntriesReply(this.nodeState.currentTerm, if_success);

			this.receivedRequest = true;
			this.lock.unlock();

			return append_entry_reply;
		}

		if (req_args.getTerm() > this.nodeState.currentTerm
				|| this.nodeState.getNodeState() == State.States.CANDIDATE) {
			this.nodeState.currentTerm = req_args.getTerm();
			this.nodeState.setNodeState(State.States.FOLLOWER);
			this.nodeState.votedFor = null; // New Term has Began
			this.numOfVotes = 0;
		}

		int prevIndex = req_args.getPrevLogIndex();
		int prevTerm = req_args.getPrevLogTerm();
		boolean consistency_check = false;
		if (prevIndex < 0 || prevTerm < 0) { // for Debug
			System.out.println("!!!!!!!! NEGATIVE INDEX OR TERM !!!!!!!!");
		}
		if (prevIndex == 0 && prevTerm == 0) {
			consistency_check = true;
		} else {

			if (this.nodeState.log.size() >= prevIndex) {
				if (this.nodeState.log.get(prevIndex - 1).index == prevIndex
						&& this.nodeState.log.get(prevIndex - 1).term == prevTerm) {
					consistency_check = true;
				}
			}

		}
		if (!consistency_check) {
			if_success = false;

			append_entry_reply = new AppendEntriesReply(this.nodeState.currentTerm, if_success);

			this.receivedRequest = true;
			this.lock.unlock();

			return append_entry_reply;
		} else {

			if_success = true;
			ArrayList<LogEntry> leader_logs = req_args.entries;

			if (leader_logs.size() == 0) {
				for (int j = this.nodeState.log.size() - 1; j >= prevIndex; j--) {
					this.nodeState.log.remove(j);
				}
			}

			for (int i = 0; i < leader_logs.size(); i++) {
				LogEntry entry = leader_logs.get(i);
				if (this.nodeState.log.size() < entry.index) {
					/* Append any new entries not already in the follwer's log */
					this.nodeState.log.add(entry);
				} else {
					LogEntry my_entry = this.nodeState.log.get(entry.index - 1);
					if (my_entry.term == entry.term) {
						/* Same Entry */
						continue;
					} else {
						for (int j = this.nodeState.log.size() - 1; j >= entry.index - 1; j--) {
							this.nodeState.log.remove(j);
						}
						this.nodeState.log.add(entry);
					}
				}

			}

			/*
			 * If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of
			 * last new entry)
			 */
			if (req_args.leaderCommit > this.nodeState.commitIndex) {
				
				if (this.nodeState.log.peekLast() != null) {
					int last_index = this.nodeState.log.getLast().index;// leader_logs.size() == 0 ? 0 :
																				// this.node_state.log.getLast().index;
					int update_commitIndex = Math.min(req_args.leaderCommit, last_index);
					for (int i = this.nodeState.commitIndex + 1; i <= update_commitIndex; i++) {
						LogEntry entry = this.nodeState.log.get(i - 1);
						ApplyMsg msg = new ApplyMsg(this.nodeId, entry.index, entry.command, false, null);
						try {
							this.lib.applyChannel(msg);
						} catch (RemoteException e) {

							this.lock.unlock();

							e.printStackTrace();
						}
						this.nodeState.commitIndex = i;
						this.nodeState.lastApplied = i;
					}
				}
			}
			append_entry_reply = new AppendEntriesReply(this.nodeState.currentTerm, if_success);

			this.receivedRequest = true;

			this.lock.unlock();

//            System.out.println(System.currentTimeMillis()+" Node "+this.id+" Returned Append RPC From Node "+req_args.leaderId+" "+req_args.prevLogIndex + " " + req_args.prevLogTerm+" "+ req_args.entries.size()+" "+req_args.leaderCommit);

			return append_entry_reply;
		}
	}

	public ArrayList<LogEntry> retrieveLogs(List<LogEntry> serverEntries, int index) {

		ArrayList<LogEntry> resultLogs = new ArrayList<LogEntry>();

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
			//if (nodeState != null) {
				if (nodeState.getNodeState() == State.States.LEADER) {

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

					timeout = random.nextInt(450) + (900 - 450);

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
			//}
		}

	}

	public void sendHeartbeats() {

		if (this.nodeState.getNodeState() != State.States.LEADER) {
			return;
		} else {
			int threadNum = 0;

			for (threadNum = 0; threadNum < this.numPeers; threadNum++) {
				if(threadNum == this.nodeId) continue;
				int nextIndex = this.nodeState.nextIndex[threadNum];
				ArrayList<LogEntry> logEntries = this.retrieveLogs(nodeState.log, nextIndex);

				int prevIndex = nextIndex - 1;
				int prevTerm;
				if (prevIndex != 0) {
					prevTerm = this.nodeState.log.get(prevIndex - 1).getTerm();
				} else {
					prevTerm = 0; 
				}
				AppendEntriesArgs entries = new AppendEntriesArgs(nodeState.currentTerm, this.nodeId, prevIndex,
						prevTerm, logEntries, this.nodeState.commitIndex);

				AppendEntriesThread thread = new AppendEntriesThread(this, this.nodeId, threadNum, entries);
				// threadNum++;
				thread.start();

			}
		}
	}

	public void startElection() {

		this.nodeState.currentTerm++;
		int lastIndex, lastTerm;
		int threadNumber;

		nodeState.votedFor = (this.nodeId);
		numOfVotes = 0;
		numOfVotes++;
		
		// reset_election_timer();
		timeout = random.nextInt(450) + (900 - 450);
		
		LogEntry logEntry = this.nodeState.log.peekLast();
		if (logEntry != null) {
			lastIndex = logEntry.getIndex();
			lastTerm = logEntry.getTerm();
		} else {
			lastIndex = 0;
			lastTerm = 0;
		}
		for (threadNumber = 0; threadNumber < numPeers; threadNumber++) {
			if(threadNumber == this.nodeId) 
				continue;

			RequestVoteArgs args = new RequestVoteArgs(nodeState.currentTerm, nodeId, lastIndex, lastTerm);

			ElectionThread electionThread = new ElectionThread(this, this.nodeId, threadNumber, args);
			// threadNumber++;

			electionThread.start();
		}
	}
}
