package bar.f0o.jlisp.xTR;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import bar.f0o.jlisp.lib.ControlPlane.ControlMessage;
import bar.f0o.jlisp.lib.ControlPlane.ControlMessage.AfiType;
import bar.f0o.jlisp.lib.ControlPlane.IPv4Locator;
import bar.f0o.jlisp.lib.ControlPlane.Loc;
import bar.f0o.jlisp.lib.ControlPlane.MapReply;
import bar.f0o.jlisp.lib.ControlPlane.MapRequest;
import bar.f0o.jlisp.lib.ControlPlane.Record;

public class ControlListener implements Runnable {

	private DatagramSocket sock;

	private ArrayList<Record> mapRequestAnswer;
	
	public ControlListener() {
		//EID Count atm 1
		mapRequestAnswer = new ArrayList<>();
		for(int i=0; i<1;i++){
			byte prefixLength = Byte.parseByte(Config.getEIDPrefix()[i].split("/")[1].trim());
			String eid = Config.getEIDPrefix()[i].split("/")[0].trim();
			byte[] eidPrefix =  new byte[4];
			for(int j=0;j<4;j++) eidPrefix[j] = Byte.parseByte(eid.split("\\.")[j]);
			
			ArrayList<Loc> locs = new ArrayList<>();
			for(int j=0;j<Config.getOwnRloc().length;j++){
				//Priority, Weight
				byte prio=1;
				byte weight=100;
				Loc locator = new Loc(prio, weight, prio, weight, true, false, true, AfiType.IPv4, new IPv4Locator(Config.getOwnRloc()[j]));
				locs.add(locator);
			}
			
			Record r = new Record(3600, prefixLength, (byte)0, true, (byte)1, AfiType.IPv4, eidPrefix, locs);
					
					
			mapRequestAnswer.add(r);
		}
	}

	@Override
	public void run() {
		try {
			sock = new DatagramSocket(4341);
			while (true) {
				byte[] buf = new byte[Controller.getMTU()];
				DatagramPacket p = new DatagramPacket(buf, buf.length);
				sock.receive(p);
				byte[] packet = new byte[p.getLength()];
				System.arraycopy(p, 0, packet, 0, packet.length);

				DataInputStream stream = new DataInputStream(new ByteArrayInputStream(packet));

				ControlMessage message = ControlMessage.fromStream(stream);

				if (message instanceof MapRequest) {
					answerMapRequest((MapRequest) message);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void answerMapRequest(MapRequest message) {
		Map<Short, byte[]> replyTo = message.getItrRlocPairs();
		byte[][] send = Config.getOwnRloc();

		if (message.ismFlag()) {
			ArrayList<Record> records = message.getReply().getRecords();
			Cache.getCache().parseRecords(records);
		
		}
		if(message.isSmrBit())
			Cache.getCache().startMapRequest(message.getSourceEIDAddress(), (byte) 2);
	
		Map<Short, byte[]> answerTo = message.getItrRlocPairs();

		//Only v4 at the moment
		
		byte[] rep = new MapReply(message.ispFlag(), true, false, message.getNonce(), this.mapRequestAnswer).toByteArray();
		DatagramPacket packet = new DatagramPacket(rep, rep.length);
		try{
		packet.setAddress(InetAddress.getByAddress(answerTo.get(1)) );
		packet.setPort(4341);
		sock.send(packet);
		}catch(IOException e){e.printStackTrace();}
	
	}

}
