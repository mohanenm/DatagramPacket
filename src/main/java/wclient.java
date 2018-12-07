
/*
    WUMP (specifically BUMP) in java. starter file
 */
import java.lang.*;     //pld
import java.net.*;      //pld
import java.io.*;
         // be sure wumppkt.java is in your current directory
import java.io.Externalizable;

// As is, this packet should receive data[1] and time out.
// If you send the ACK to the correct port, you should receive data[2]
// If you update expected_block, you should receive the entire file, for "vanilla"
// If you write the sanity checks, you should receive the entire file in all cases

public class wclient {

    //============================================================
    //============================================================

    static public void main(String args[]) {
        int srcport;
        int destport = wumppkt.SERVERPORT;
        destport = wumppkt.SAMEPORT;		// 4716; server responds from same port
        String filename = "vanilla";
        String desthost = "ulam.cs.luc.edu";
        int winsize = 1;
        int latchport = 0;
        short THEPROTO = wumppkt.BUMPPROTO;
        wumppkt.setproto(THEPROTO);

        if (args.length > 0) filename = args[0];
        if (args.length > 1) winsize = Integer.parseInt(args[1]);
        if (args.length > 2) desthost = args[2];

        DatagramSocket s;
        try {
            s = new DatagramSocket();
        }
        catch (SocketException se) {
            System.err.println("no socket available");
            return;
        }

        try {
            s.setSoTimeout(wumppkt.INITTIMEOUT);       // time in milliseconds
        } catch (SocketException se) {
            System.err.println("socket exception: timeout not set!");
        }

        if (args.length > 3) {
            System.err.println("usage: wclient filename  [winsize [hostname]]");
            //exit(1);
        }

        // DNS lookup
        InetAddress dest;
        System.err.print("Looking up address of " + desthost + "...");
        try {
            dest = InetAddress.getByName(desthost);
        }
        catch (UnknownHostException uhe) {
            System.err.println("unknown host: " + desthost);
            return;
        }
        System.err.println(" got it!");

        wumppkt.REQ req = new wumppkt.REQ(winsize, filename); // ctor for REQ

        System.err.println("req size = " + req.size() + ", filename=" + req.filename());

        DatagramPacket reqDG
                = new DatagramPacket(req.write(), req.size(), dest, destport);

        try {s.send(reqDG);}
        catch (IOException ioe) {
            System.err.println("send() failed");
            return;
        }

        //============================================================

        // now receive the response
        DatagramPacket replyDG            // we don't set the address here!
                = new DatagramPacket(new byte[wumppkt.MAXSIZE] , wumppkt.MAXSIZE);
        DatagramPacket ackDG = new DatagramPacket(new byte[0], 0);
        ackDG.setAddress(dest);
        ackDG.setPort(destport);		// this is wrong for wumppkt.SERVERPORT version

        int expected_block = 1;
        long starttime = System.currentTimeMillis();
        long sendtime = starttime;

        wumppkt.DATA  data  = null;
        wumppkt.ERROR error = null;
        wumppkt.ACK   ack   = new wumppkt.ACK(0);

        int proto;        // for proto of incoming packets
        int opcode;
        int length;
        int blocknum;

        while (true) {
            try {
                s.receive(replyDG);
            }
            catch (SocketTimeoutException ste) {
                System.err.println("hard timeout");

                try {s.send(reqDG); continue;}
                catch (IOException ioe) {
                    System.err.println("send() failed");
                    return;
                }

                // what do you do here??; retransmit of previous packet here
                //return;
                //continue;
            }
            catch (IOException ioe) {
                System.err.println("receive() failed");
                return;
            }

            byte[] replybuf = replyDG.getData();
            proto   = wumppkt.proto(replybuf);
            opcode  = wumppkt.opcode(replybuf);
            length  = replyDG.getLength();
            srcport = replyDG.getPort();


            data = null; error = null;
            blocknum = 0;
            if (  proto == THEPROTO && opcode == wumppkt.DATAop && length >= wumppkt.DHEADERSIZE) {
                data = new wumppkt.DATA(replybuf, length);
                blocknum = data.blocknum();
            } else if ( proto == THEPROTO && opcode == wumppkt.ERRORop && length >= wumppkt.EHEADERSIZE) {
                error = new wumppkt.ERROR(replybuf);
            }

            printInfo(replyDG, data, starttime);


            if (error != null) {
                System.err.println("Error packet rec'd; code " + error.errcode());
                continue;
            }
            if (data == null) continue;

                System.out.write(data.bytes(), 0, data.size() - wumppkt.DHEADERSIZE);


            ack = new wumppkt.ACK(expected_block);
            ackDG.setData(ack.write());
            ackDG.setLength(ack.size());
            try {s.send(ackDG);}
            catch (IOException ioe) {
                System.err.println("send() failed");
                return;
            }
            sendtime = System.currentTimeMillis();

        }
    }

    static public void printInfo(DatagramPacket pkt, wumppkt.DATA data, long starttime) {
        byte[] replybuf = pkt.getData();
        int proto = wumppkt.proto(replybuf);
        int opcode = wumppkt.opcode(replybuf);
        int length = replybuf.length;
        System.err.print("rec'd packet: len=" + length);
        System.err.print("; proto=" + proto);
        System.err.print("; opcode=" + opcode);
        System.err.print("; src=(" + pkt.getAddress().getHostAddress() + "/" + pkt.getPort()+ ")");
        System.err.print("; time=" + (System.currentTimeMillis()-starttime));
        System.err.println();
        if (data==null)
            System.err.println("         packet does not seem to be a data packet");
        else
            System.err.println("         DATA packet blocknum = " + data.blocknum());
    }


    static public int getblock(byte[] buf) {
        return  (((buf[4] & 0xff) << 24) |
                ((buf[5] & 0xff) << 16) |
                ((buf[6] & 0xff) <<  8) |
                ((buf[7] & 0xff)      ) );
    }


}