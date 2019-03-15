import java.rmi.RemoteException;

import lib.AppendEntriesArgs;
import lib.AppendEntryReply;
import lib.ApplyMsg;
import lib.Message;
import lib.MessageType; 

public class AppendEntriesThread extends Thread{
	
	RaftNode node; 
	int startId; 
	int endId; 
	Message AppendEntryMessage;
	Message AppendEntryReplyMessage;
	AppendEntriesArgs args; 
	
	public AppendEntriesThread(RaftNode node, int startID, int endID, AppendEntriesArgs arguments) {
		
		this.node = node; 
		this.startId = startID; 
		this.endId = endID; 
		this.args = arguments; 
	}
	
	
}
