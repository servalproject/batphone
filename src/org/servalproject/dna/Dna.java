package org.servalproject.dna;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.servalproject.batman.PeerRecord;
import org.servalproject.dna.OpSimple.Code;

import android.util.Log;

public class Dna {
	
	private DatagramSocket s=null;
	private List<SocketAddress> staticPeers=null;
	private List<PeerRecord> dynamicPeers=null; 
	
	private int timeout=300;
	private int retries=5;
	
	public void addStaticPeer(InetAddress i){
		addStaticPeer(new InetSocketAddress(i,Packet.dnaPort));
	}
	public void addStaticPeer(SocketAddress i){
		if (staticPeers==null)
			staticPeers=new ArrayList<SocketAddress>();
		staticPeers.add(i);
	}
	public void clearPeers(){
		staticPeers=null;
	}
	
	public void setDynamicPeers(List<PeerRecord> batmanPeers){
		dynamicPeers=batmanPeers;
	}
	
	// packets that we may need to (re-)send
	List<PeerConversation> resendQueue=new ArrayList<PeerConversation>();
	// responses that we are still waiting for
	Map<PeerConversation.Id, PeerConversation> awaitingResponse=new HashMap<PeerConversation.Id, PeerConversation>();
	
	private void send(Packet p, InetAddress addr) throws IOException{
		send(p,new InetSocketAddress(addr,Packet.dnaPort));
	}
	private void send(Packet p, SocketAddress addr) throws IOException{
		DatagramPacket dg=p.getDatagram();
		dg.setSocketAddress(addr);
		if (s==null){
			s=new DatagramSocket();
			s.setBroadcast(true);
		}
		s.send(dg);
	}
	
	private void send(PeerConversation pc) throws IOException{
		send(pc.packet, pc.id.addr);
		pc.transmissionTime=System.currentTimeMillis();
		pc.retryCount++;
		
		if (!resendQueue.contains(pc)){
			resendQueue.add(pc);
			awaitingResponse.put(pc.id, pc);
		}
	}
	
	private void send() throws IOException{
		Iterator<PeerConversation> i=resendQueue.iterator();
		while (i.hasNext()){
			PeerConversation pc=i.next();
			if (pc.responseReceived || pc.retryCount>=retries){
				i.remove();
				continue;
			}
			send(pc);
		}
	}
	
	// one packet buffer for all reply's
	DatagramPacket reply=null;
	
	private Packet receivePacket() throws IOException{
		if (awaitingResponse.isEmpty())
			throw new IllegalStateException("No conversations are expecting a response");
		if (reply==null){
			byte[]buffer = new byte[8000];
			reply=new DatagramPacket(buffer, buffer.length);
		}
		s.setSoTimeout(timeout);
		s.receive(reply);
		return Packet.parse(reply, System.currentTimeMillis());
	}		
	
	// if we are expecting replies, wait for one and process it.
	// if the wait times out, re-transmit, and wait some more.
	private boolean processResponse() throws IOException{
		while(!(resendQueue.isEmpty() && awaitingResponse.isEmpty())){
			try{
				while (!awaitingResponse.isEmpty()){
					Packet p=receivePacket();
					PeerConversation.Id id=new PeerConversation.Id(p.transactionId, p.addr);
					
					PeerConversation pc=awaitingResponse.get(id);
					
					if (pc!=null){
						pc.replyTime=p.timestamp;
						pc.processResponse(p);
						if (pc.conversationComplete){
							awaitingResponse.remove(id);
							// break out of this function to give the caller a chance to stop processing
							return true;
						}
					}else{
						Log.d("BatPhone", "Unexpected packet from "+p.addr);
						Log.v("BatPhone", p.toString());
					}
				}
				
			}catch(SocketTimeoutException e){
				// remove conversations we are going to give up on
				Iterator<PeerConversation> i=awaitingResponse.values().iterator();
				while (i.hasNext()){
					PeerConversation pc=i.next();
					if (pc.responseReceived||pc.retryCount>retries)
						i.remove();
				}
				
				// if we're not going to re-send a packet, we can just give up now.
				if (resendQueue.isEmpty())
					return false;
			}
			
			if (!resendQueue.isEmpty())
				send();
		}
		
		return false;
	}
	
	private boolean sendParallel(Packet p, boolean waitAll, OpVisitor v) throws IOException{
		if (staticPeers==null&&dynamicPeers==null)
			throw new IllegalStateException("No peers have been set");
		
		List<PeerConversation> convs=new ArrayList<PeerConversation>();
		if (staticPeers!=null){
			for (SocketAddress addr:staticPeers){
				convs.add(new PeerConversation(p, addr, v));
			}
		}
		
		if (dynamicPeers!=null){
			for (PeerRecord peer:dynamicPeers){
				convs.add(new PeerConversation(p, peer.getAddress(), v));
			}
		}
		
		for (PeerConversation pc:convs){
			send(pc);
		}
		
		outerLoop:
		while(processResponse()){
			for (PeerConversation pc:convs){
				if (waitAll&&!pc.conversationComplete) break;
				if (pc.conversationComplete&&!waitAll) break outerLoop;
			}
		}
		
		// forget about sending any more requests
		for (PeerConversation pc:convs){
			awaitingResponse.remove(pc.id);
		}
		
		return false;
	}
	
	public void beaconParallel(Packet p) throws IOException{
		if (staticPeers==null&&dynamicPeers==null)
			throw new IllegalStateException("No peers have been set");
		
		if (staticPeers!=null){
			for (SocketAddress addr:staticPeers){
				send(p, addr);
			}
		}
		
		if (dynamicPeers!=null){
			for (PeerRecord peer:dynamicPeers){
				send(p, peer.getAddress());
			}
		}
		
	}
	
	private boolean sendSerial(PeerConversation pc) throws IOException{
		send(pc);
		// process responses until this conversation completes or times out.
		while (processResponse() && !pc.conversationComplete);
		return pc.conversationComplete;
	}
	
	private boolean sendSerial(Packet p, OpVisitor v) throws IOException{
		if (staticPeers==null&&dynamicPeers==null)
			throw new IllegalStateException("No peers have been set");
		
		if (staticPeers!=null){
			for (SocketAddress addr:staticPeers){
				if (sendSerial(new PeerConversation(p,addr,v)))
					return true;
			}
		}
		if (dynamicPeers!=null){
			for (PeerRecord peer:dynamicPeers){
				if (sendSerial(new PeerConversation(p,peer.getAddress(),v)))
					return true;
			}
		}
		return false;
	}
	
	public SubscriberId requestNewHLR(String did) throws IOException, IllegalAccessException, InstantiationException{
		Packet p=new Packet();
		p.setDid(did);
		p.operations.add(new OpSimple(OpSimple.Code.Create));
		ClientVisitor v=new ClientVisitor();
		if (!sendSerial(p, v)) 
			throw new IllegalStateException("Create request was not handled by any peers");
		return v.sid;
	}
	
	private class ClientVisitor extends OpVisitor{
		SubscriberId sid=null;
		VariableRef reference;
		short varLen;
		ByteBuffer buffer;
		
		@Override
		public boolean onSimpleCode(Packet packet, Code code) {
			switch(code){
			case Ok:
				sid=packet.getSid();
				return true;
			default:
				Log.v("BatPhone","Response: "+code.name());
			}
			return false;
		}

		@Override
		public boolean onData(Packet packet, VariableRef reference, short varLen, ByteBuffer buffer) {
			this.sid=packet.getSid();
			this.reference=reference;
			this.varLen=varLen;
			this.buffer=buffer;
			return true;
		}
		
	};
	
	public void writeDid(SubscriberId sid, byte instance, boolean replace, String did) throws IOException{
		writeVariable(sid, VariableType.DIDs, instance, replace, ByteBuffer.wrap(Packet.packDid(did)));
	}
	public void writeLocation(SubscriberId sid, byte instance, boolean replace, String value) throws IOException{
		writeVariable(sid, VariableType.Locations, instance, replace, ByteBuffer.wrap(value.getBytes()));
	}
	public void writeVariable(SubscriberId sid, final VariableType var, byte instance, boolean replace, String value) throws IOException{
		writeVariable(sid, var, instance, replace, ByteBuffer.wrap(var==VariableType.DIDs?Packet.packDid(value):value.getBytes()));
	}
	public void writeVariable(SubscriberId sid, final VariableType var, byte instance, boolean replace, ByteBuffer value) throws IOException{
		OutputStream s = beginWriteVariable(sid, var, instance, replace);
		s.write(value.array(),value.arrayOffset(),value.remaining());
		s.close();
	}
	
	// write variables with an output stream so we can store large values as they are created
	// TODO, send packets asynchronously on calls to write, blocking only when flush() or close() is called.
	public OutputStream beginWriteVariable(SubscriberId sid, VariableType var, byte instance, boolean replace){
		return new WriteOutputStream(sid, var, instance, replace);
	}
	
	class WriteOutputStream extends OutputStream{
		SubscriberId sid;
		VariableType var;
		byte instance;
		short offset=0;
		OpSet.Flag flag;
		ByteBuffer buffer=ByteBuffer.allocate(256);
		
		public WriteOutputStream(SubscriberId sid, VariableType var, byte instance, boolean replace) {
			if (sid==null)
				throw new IllegalArgumentException("Subscriber ID cannot be null");
			if (var==null)
				throw new IllegalArgumentException("Variable type cannot be null");
			
			this.sid=sid;
			this.var=var;
			this.instance=instance;
			this.flag=(replace?OpSet.Flag.Replace:OpSet.Flag.NoReplace);
		}

		@Override
		public void close() throws IOException {flush();}

		@Override
		public void flush() throws IOException {
			buffer.flip();
			
			Packet p=new Packet();
			p.setSid(sid);
			p.operations.add(new OpSet(var, instance, offset, this.flag,buffer));
			this.flag=OpSet.Flag.Fragment;
			offset+=buffer.remaining();
			if (!sendSerial(p, new OpVisitor(){
				@Override
				public boolean onWrote(Packet packet, VariableRef reference) {
					Log.v("BatPhone", "Wrote "+reference);
					return true;
				}
			}))
				throw new IOException("No peer acknowledged writing fragment.");
			buffer.clear();
		}
		
		@Override
		public void write(byte[] b) throws IOException {write(b,0,b.length);}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			while(len>0){
				int size=len>256?256:len;
				
				buffer.put(b, off, size);
				if (buffer.remaining()==0) flush();
				
				len-=size;
				off+=size;
			}
		}

		@Override
		public void write(int arg0) throws IOException {
			buffer.put((byte)arg0);
			if (buffer.remaining()==0) flush();
		}
	}
	
	public boolean sendSms(String did, String message) throws IOException{
		Packet p=new Packet();
		p.setDid(did);
		//FIXME get the actual number of the sender 
		p.operations.add(new OpDT(message, "0123456789", OpDT.DTtype.SMS));
		sendParallel(p, new OpVisitor(){
			@Override
			public boolean onSimpleCode(Packet packet, OpSimple.Code code){
				if (code==OpSimple.Code.Ok){					
				}
				return true;
			}
		});
		return true;
	}
	
	// Send a request to all peers for this variable
	public void readVariable(SubscriberId sid, String did, final VariableType var, final byte instance, final VariableResults results) throws IOException{
		Packet p=new Packet();
		p.setSidDid(sid, did);
		p.operations.add(new OpGet(var, instance, (short)0));
		
		sendParallel(p, true, new OpVisitor(){
			PeerConversation peer;
			
			@Override
			public void onPacketArrived(Packet packet, PeerConversation peer) {
				this.peer=peer;
			}

			@Override
			public boolean onDone(Packet packet, byte count) {
				return true;
			}
			
			@Override
			public boolean onData(Packet packet, VariableRef reference,
					short varLen, ByteBuffer buffer) {
				// inform the caller of this variable, create an input stream for the caller to read the value.
				results.result(peer, packet.getSid(), reference.varType, reference.instance, 
						new ReadInputStream(packet.getSid(), reference.varType, reference.instance, packet.addr, buffer, varLen));
				return false;
			}
		});
	}
	
	//TODO pre-fetch remaining fragments asynchronously
	public class ReadInputStream extends InputStream{
		short offset=0;
		ByteBuffer buffer=null;
		short expectedLen;
		SubscriberId sid;
		VariableType var;
		byte instance;
		SocketAddress peer;
		ClientVisitor v=null;
		
		ReadInputStream(SubscriberId sid, VariableType var, byte instance, SocketAddress peer, ByteBuffer firstChunk, short expectedLen){
			this.sid=sid;
			this.var=var;
			this.instance=instance;
			this.peer=peer;
			this.buffer=firstChunk;
			this.expectedLen=expectedLen;
			if (firstChunk!=null){
				offset=(short)firstChunk.remaining();
			}
		}
		
		private boolean readMore() throws IOException{
			if (offset>=expectedLen){
				// if the last byte of a location is '@', append the ip address of the peer as a string
				if (var==VariableType.Locations && 
						buffer!=null && 
						buffer.position()>0 &&
						buffer.get(buffer.position() -1)=='@'){
					InetSocketAddress inetAddr=(InetSocketAddress) peer;
					String addr=inetAddr.getAddress().toString();
					buffer=ByteBuffer.wrap(addr.getBytes());
					if (buffer.get(0)=='/') buffer.position(1);
					offset+=buffer.remaining();
					return true;
				}
				offset=-1;
			}
			if (offset==-1)
				return false;
			
			Packet p=new Packet();
			p.setSid(sid);
			p.operations.add(new OpGet(var, instance, offset));
			if (v==null)
				v=new ClientVisitor();
			
			PeerConversation pc=new PeerConversation(p, peer, v);
			send(pc);
			// wait for responses until we hear from the peer we're interested in or the request times out
			while(processResponse() && !pc.conversationComplete);
			if (!pc.conversationComplete)
				throw new IOException("Timeout waiting for response for more data.");
			
			expectedLen=v.varLen;
			this.buffer=v.buffer;
			this.offset+=v.reference.len;
			return true;
		}
		
		@Override
		public void close() throws IOException {
			offset=-1;
			buffer=null;
		}

		@Override
		public int read() throws IOException {
			if (offset==-1) return -1;
			if (buffer==null||buffer.remaining()==0)
				if (!readMore()) return-1;
			return ((int)buffer.get())&0xff;
		}

		@Override
		public int available() throws IOException {
			if (buffer==null) return 0;
			return buffer.remaining();
		}

		@Override
		public int read(byte[] bytes, int offset, int len) throws IOException {
			if (offset==-1) return -1;	
			if (len==0) return 0;
			if (buffer==null||buffer.remaining()==0)
				if (!readMore()) return -1;
			
			int size=len>buffer.remaining()?buffer.remaining():len;
			buffer.get(bytes,offset,size);
			return size;
		}

		@Override
		public int read(byte[] bytes) throws IOException {
			return read(bytes,0,bytes.length);
		}

		@Override
		public long skip(long len) throws IOException {
			int size=0;
			if (offset==-1) return -1;
			if (buffer!=null&&buffer.remaining()>0){
				size=(int)(len>buffer.remaining()?buffer.remaining():len);
				buffer.position(buffer.position()+size);
			}
			offset+=len - size;
			if (offset>expectedLen){
				offset=-1;
				return -1;
			}
				
			return len;
		}
	}
	/*
	@SuppressWarnings("unused")
	private void locateSubscriber(SubscriberId sid, String did) throws IOException, IllegalAccessException, InstantiationException{
		Packet p=new Packet();
		p.setSidDid(sid, did);
		p.operations.add(new OpGet(VariableType.Locations, (byte)0, (short)0));
		LocateVisitor v=new LocateVisitor();
		if (!sendParallel(p, v)){
			throw new IllegalStateException("Unable to locate subscriber"); 
		}
		
		// TODO
	}
	
	private class LocateVisitor extends OpVisitor{
		@Override
		public boolean onSimpleCode(Packet packet, Code code) {
			System.out.println("Response: "+code.name());
			return false;
		}
		
	};
	*/
	
	private static void usage(String error){
		if (error!=null)
			System.out.println(error);
			
		System.out.println("usage:");
		
		System.out.println("       -i - Specify the instance of variable to set.");
		System.out.println("       -d - Search by Direct Inward Dial (DID) number.");
		System.out.println("       -s - Search by Subscriber ID (SID) number.");
		System.out.println("       -p - Specify additional DNA node to query.");
		System.out.println("       -t - Specify the request timeout period.");
		System.out.println("       -R - Read a variable value.\n");
		System.out.println("       -U - Write a variable value, updating previous values.\n");
		System.out.println("       -W - Write a variable value, keeping previous values.\n");
		System.out.println("       -C - Request the creation of a new subscriber with the specified DID.");
	}
	
	public static void main(String[] args) {
		try {
			Dna dna = new Dna();
			String did=null;
			SubscriberId sid=null;
			int instance=-1;
			
			for (int i=0;i<args.length;i++){
				if ("-d".equals(args[i])){
					did=args[++i];
					
				}else if ("-s".equals(args[i])){
					sid=new SubscriberId(args[++i]);
					
				}else if ("-p".equals(args[i])){
					String host=args[++i];
					int port=4110;
					if (host.indexOf(':')>=0){
						port=Integer.valueOf(host.substring(host.indexOf(':')));
						host=host.substring(0,host.indexOf(':'));
					}
					dna.addStaticPeer(new InetSocketAddress(host,port));
					
				}else if ("-t".equals(args[i])){
					dna.timeout=Integer.valueOf(args[++i]);
					
				}else if ("-i".equals(args[i])){
					instance=Integer.valueOf(args[++i]);
					if (instance <-1 || instance>255) throw new IllegalArgumentException("Instance value must be between -1 and 255");
					
				}else if ("-C".equals(args[i])){
					if (did==null) throw new IllegalArgumentException("You must specify the DID to register.");
					sid = dna.requestNewHLR(did);
					if (sid!=null)
						System.out.println("Sid returned: "+sid);
					
				}else if ("-U".equals(args[i])){
					VariableType var=VariableType.getVariableType(args[++i]);
					dna.writeVariable(sid, var, (byte)instance, true, args[++i]);
					
				}else if ("-W".equals(args[i])){
					VariableType var=VariableType.getVariableType(args[++i]);
					dna.writeVariable(sid, var, (byte)instance, false, args[++i]);
					
				}else if ("-R".equals(args[i])){
					VariableType var=VariableType.getVariableType(args[++i]);
					dna.readVariable(sid, did, var, (byte) instance, new VariableResults(){
						
						@Override
						public void result(PeerConversation peer, SubscriberId sid, VariableType varType, byte instance, InputStream value) {
							try {
								if (varType==VariableType.DIDs){
									System.out.println(sid+" ("+peer.id.addr+")\n"+varType.name()+"["+instance+"]: "+Packet.unpackDid(value));
								}else{
									StringBuilder sb=new StringBuilder();
									byte[] bytes=new byte[256];
									int len;
									while((len=value.read(bytes))>=0){
										sb.append(new String(bytes,0,len));
									}
									System.out.println(sid+" ("+peer.id.addr+")\n"+varType.name()+"["+instance+"]: "+sb.toString());
								}
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					});
					
				}else{
					throw new IllegalArgumentException("Unhandled argument "+args[i]+".");
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			usage(null);
		}
	}
}
