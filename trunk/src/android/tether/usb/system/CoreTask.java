/**
 *  This program is free software; you can redistribute it and/or modify it under 
 *  the terms of the GNU General Public License as published by the Free Software 
 *  Foundation; either version 3 of the License, or (at your option) any later 
 *  version.
 *  You should have received a copy of the GNU General Public License along with 
 *  this program; if not, see <http://www.gnu.org/licenses/>. 
 *  Use this application at your own risk.
 *
 *  Copyright (c) 2009 by Harald Mueller and Seth Lemons.
 */

package android.tether.usb.system;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.zip.GZIPInputStream;

import android.tether.usb.data.ClientData;
import android.util.Log;

public class CoreTask {

	public static final String MSG_TAG = "TETHER -> CoreTask";
	
	public String DATA_FILE_PATH;
	
	private static final String FILESET_VERSION = "25";
	private static final String defaultDNS1 = "208.67.220.220";
	private static final String defaultDNS2 = "208.67.222.222";
	
	private Hashtable<String,String> runningProcesses = new Hashtable<String,String>();
	
	public void setPath(String path){
		this.DATA_FILE_PATH = path;
	}
    
	
    public Hashtable<String,ClientData> getLeases() throws Exception {
        Hashtable<String,ClientData> returnHash = new Hashtable<String,ClientData>();
        
        ClientData clientData;
        
        ArrayList<String> lines = readLinesFromFile(this.DATA_FILE_PATH+"/var/dnsmasq.leases");
        
        for (String line : lines) {
			clientData = new ClientData();
			String[] data = line.split(" ");
			String macAddress = data[1];
			String ipAddress = data[2];
			String clientName = data[3];
			clientData.setClientName(clientName);
			clientData.setIpAddress(ipAddress);
			clientData.setMacAddress(macAddress);
			returnHash.put(macAddress, clientData);
		}
    	return returnHash;
    }
 
    public boolean chmodBin() {
    	if (NativeTask.runCommand("chmod 0755 "+this.DATA_FILE_PATH+"/bin/*") == 0) {
    		return true;
    	}
    	return false;
    }   
    
    public ArrayList<String> readLinesFromFile(String filename) {
    	String line = null;
    	BufferedReader br = null;
    	InputStream ins = null;
    	ArrayList<String> lines = new ArrayList<String>();
    	Log.d(MSG_TAG, "Reading lines from file: " + filename);
    	try {
    		ins = new FileInputStream(new File(filename));
    		br = new BufferedReader(new InputStreamReader(ins), 8192);
    		while((line = br.readLine())!=null) {
    			lines.add(line.trim());
    		}
    	} catch (Exception e) {
    		Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
    	}
    	finally {
    		try {
    			ins.close();
    			br.close();
    		} catch (Exception e) {
    			// Nothing.
    		}
    	}
    	return lines;
    }
    
    public boolean writeLinesToFile(String filename, String lines) {
		OutputStream out = null;
		boolean returnStatus = false;
		Log.d(MSG_TAG, "Writing " + lines.length() + " bytes to file: " + filename);
		try {
			out = new FileOutputStream(filename);
        	out.write(lines.getBytes());
		} catch (Exception e) {
			Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
		}
		finally {
        	try {
        		if (out != null)
        			out.close();
        		returnStatus = true;
			} catch (IOException e) {
				returnStatus = false;
			}
		}
		return returnStatus;
    }
    
    public boolean isNatEnabled() {
    	ArrayList<String> lines = readLinesFromFile("/proc/sys/net/ipv4/ip_forward");
    	return lines.contains("1");
    }
    
    public String getKernelVersion() {
        ArrayList<String> lines = readLinesFromFile("/proc/version");
        String version = lines.get(0).split(" ")[2];
        Log.d(MSG_TAG, "Kernel version: " + version);
        return version;
    }
    
    public synchronized boolean hasKernelFeature(String feature) {
    	try {
			FileInputStream fis = new FileInputStream("/proc/config.gz");
			GZIPInputStream gzin = new GZIPInputStream(fis);
			BufferedReader in = null;
			String line = "";
			in = new BufferedReader(new InputStreamReader(gzin));
			while ((line = in.readLine()) != null) {
				   if (line.startsWith(feature)) {
					    gzin.close();
						return true;
					}
			}
			gzin.close();
    	} catch (IOException e) {
    		//
    		Log.d(MSG_TAG, "Unexpected error - Here is what I know: "+e.getMessage());
    	}
    	return false;
    }

    public boolean isProcessRunning(String processName) throws Exception {
    	boolean processIsRunning = false;
    	Hashtable<String,String> tmpRunningProcesses = new Hashtable<String,String>();
    	File procDir = new File("/proc");
    	FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                try {
                    Integer.parseInt(name);
                } catch (NumberFormatException ex) {
                    return false;
                }
                return true;
            }
        };
    	File[] processes = procDir.listFiles(filter);
    	for (File process : processes) {
    		String cmdLine = "";
    		// Checking if this is a already known process
    		if (this.runningProcesses.containsKey(process.getAbsoluteFile().toString())) {
    			cmdLine = this.runningProcesses.get(process.getAbsoluteFile().toString());
    		}
    		else {
    			ArrayList<String> cmdlineContent = this.readLinesFromFile(process.getAbsoluteFile()+"/cmdline");
    			if (cmdlineContent != null && cmdlineContent.size() > 0) {
    				cmdLine = cmdlineContent.get(0);
    			}
    		}
    		// Adding to tmp-Hashtable
    		tmpRunningProcesses.put(process.getAbsoluteFile().toString(), cmdLine);
    		
    		// Checking if processName matches
    		if (cmdLine.contains(processName)) {
    			processIsRunning = true;
    		}
    	}
    	// Overwriting runningProcesses
    	this.runningProcesses = tmpRunningProcesses;
    	return processIsRunning;
    }

    public boolean hasRootPermission() {
    	boolean rooted = true;
		try {
			File su = new File("/system/bin/su");
			if (su.exists() == false) {
				rooted = false;
			}
		} catch (Exception e) {
			Log.d(MSG_TAG, "Can't obtain root - Here is what I know: "+e.getMessage());
			rooted = false;
		}
		return rooted;
    }
    
    public boolean runRootCommand(String command) {
		Log.d(MSG_TAG, "Root-Command ==> su -c \""+command+"\"");
    	if (NativeTask.runCommand("su -c \""+command+"\"") == 0) {
			return true;
		}
		return false;
    }
    
    public String getProp(String property) {
    	return NativeTask.getProp(property);
    }
    
    public long[] getDataTraffic(String device) {
    	// Returns traffic usage for all interfaces starting with 'device'.
    	long [] dataCount = new long[] {0, 0};
    	if (device == "")
    		return dataCount;
    	for (String line : readLinesFromFile("/proc/net/dev")) {
    		if (line.startsWith(device) == false)
    			continue;
    		line = line.replace(':', ' ');
    		String[] values = line.split(" +");
    		dataCount[0] += Long.parseLong(values[1]);
    		dataCount[1] += Long.parseLong(values[9]);
    	}
    	Log.d(MSG_TAG, "Data rx: " + dataCount[0] + ", tx: " + dataCount[1]);
    	return dataCount;
    }

    
    public synchronized void updateDnsmasqFilepath() {
    	String dnsmasqConf = this.DATA_FILE_PATH+"/conf/dnsmasq.conf";
    	String newDnsmasq = new String();
    	boolean writeconfig = false;
    	
    	ArrayList<String> lines = readLinesFromFile(dnsmasqConf);
    	
    	for (String line : lines) {
    		if (line.contains("dhcp-leasefile=") && !line.contains(CoreTask.this.DATA_FILE_PATH)){
    			line = "dhcp-leasefile="+CoreTask.this.DATA_FILE_PATH+"/var/dnsmasq.leases";
    			writeconfig = true;
    		}
    		else if (line.contains("pid-file=") && !line.contains(CoreTask.this.DATA_FILE_PATH)){
    			line = "pid-file="+CoreTask.this.DATA_FILE_PATH+"/var/dnsmasq.pid";
    			writeconfig = true;
    		}
    		newDnsmasq += line+"\n";
    	}

    	if (writeconfig == true)
    		writeLinesToFile(dnsmasqConf, newDnsmasq);
    }
    
    public synchronized void updateDnsmasqConf() {
    	String dnsmasqConf = this.DATA_FILE_PATH+"/conf/dnsmasq.conf";
    	String newDnsmasq = new String();
    	// Getting dns-servers
    	String dns[] = new String[2];
    	dns[0] = getProp("net.dns1");
    	dns[1] = getProp("net.dns2");
    	if (dns[0] == null || dns[0].length() <= 0 || dns[0].equals("undefined")) {
    		dns[0] = defaultDNS1;
    	}
    	if (dns[1] == null || dns[1].length() <= 0 || dns[1].equals("undefined")) {
    		dns[1] = defaultDNS2;
    	}
    	boolean writeconfig = false;
    	ArrayList<String> lines = readLinesFromFile(dnsmasqConf);
    	
    	int servercount = 0;
	    for (String s : lines) {
    		if (s.contains("server")) { 
    			if (s.contains(dns[servercount]) == false){
    				s = "server="+dns[servercount];
    				writeconfig = true;
    			}
    			servercount++;
    		}
    		newDnsmasq += s+"\n";
		}

    	if (writeconfig == true) {
			Log.d(MSG_TAG, "Writing new DNS-Servers: "+dns[0]+","+dns[1]);
    		writeLinesToFile(dnsmasqConf, newDnsmasq);
    	}
    	else {
			Log.d(MSG_TAG, "No need to update DNS-Servers: "+dns[0]+","+dns[1]);
    	}
    }
    
    public boolean filesetOutdated(){
    	boolean outdated = true;
    	
    	File inFile = new File(this.DATA_FILE_PATH+"/conf/version");
    	if (inFile.exists() == false) {
    		return false;
    	}
    	ArrayList<String> lines = readLinesFromFile(this.DATA_FILE_PATH+"/conf/version");

    	int linecount = 0;
    	for (String line : lines) {
    		if (line.contains("@Version")){
    			String instVersion = line.split("=")[1];
    			if (instVersion != null && FILESET_VERSION.equals(instVersion.trim()) == true) {
    				outdated = false;
    			}
    			break;
    		}
    		if (linecount++ > 2)
    			break;
    	}
    	return outdated;
    }
    
    public Hashtable<String,String> getWlanConf() {
    	Hashtable<String,String> wlanConf = new Hashtable<String,String>();
    	ArrayList<String> lines = readLinesFromFile(this.DATA_FILE_PATH+"/conf/wifi.conf");
    	for (String line : lines) {
    		String[] pair = line.split("=");
    		if (pair[0] != null && pair[1] != null && pair[0].length() > 0 && pair[1].length() > 0) {
    			wlanConf.put(pair[0].trim(), pair[1].trim());
    		}
    	}
    	return wlanConf;
    }
 
    public synchronized boolean writeWlanConf(String name, String value) {
    	Hashtable<String, String> table = new Hashtable<String, String>();
    	table.put(name, value);
    	return writeWlanConf(table);
    }
    
    public synchronized boolean writeWlanConf(Hashtable<String,String> values) {
    	String filename = this.DATA_FILE_PATH+"/conf/wifi.conf";
    	ArrayList<String> valueNames = Collections.list(values.keys());

    	String fileString = "";
    	
    	ArrayList<String> inputLines = readLinesFromFile(filename);
    	for (String line : inputLines) {
    		for (String name : valueNames) {
        		if (line.contains(name)){
	    			line = name+"="+values.get(name);
	    			break;
	    		}
    		}
    		line+="\n";
    		fileString += line;
    	}
    	return writeLinesToFile(filename, fileString); 	
    }
    
    public long getModifiedDate(String filename) {
    	File file = new File(filename);
    	if (file.exists() == false) {
    		return -1;
    	}
    	return file.lastModified();
    }
    
    public String getLanIPConf() {
    	String returnString = "192.168.2.0/24";
    	String filename = this.DATA_FILE_PATH+"/conf/lan_network.conf";
    	ArrayList<String> inputLines = readLinesFromFile(filename);
    	for (String line : inputLines) {
    		if (line.startsWith("network")) {
    			returnString = (line.split("=")[1])+"/24";
    			break;
    		}
    	}
    	return returnString;
    }
    
    public synchronized boolean writeLanConf(String lanconfString) {
    	boolean writesuccess = false;
    	
    	String filename = null;
    	ArrayList<String> inputLines = null;
    	String fileString = null;
    	
    	// Assemble gateway-string
    	String[] lanparts = lanconfString.split("\\.");
    	String gateway = lanparts[0]+"."+lanparts[1]+"."+lanparts[2]+".254";
    	
    	// Assemble dnsmasq dhcp-range
    	String iprange = lanparts[0]+"."+lanparts[1]+"."+lanparts[2]+".100,"+lanparts[0]+"."+lanparts[1]+"."+lanparts[2]+".105,12h";
    	
    	// Update bin/tether
    	filename = this.DATA_FILE_PATH+"/conf/lan_network.conf";
       	fileString = "network="+lanparts[0]+"."+lanparts[1]+"."+lanparts[2]+".0\n";
       	fileString += "gateway="+gateway;

    	writesuccess = writeLinesToFile(filename, fileString);
    	if (writesuccess == false) {
    		Log.e(MSG_TAG, "Unable to update bin/tether with new lan-configuration.");
    		return writesuccess;
    	}
    	
    	// Update conf/dnsmasq.conf
    	fileString = "";
    	filename = this.DATA_FILE_PATH+"/conf/dnsmasq.conf";
    	inputLines = readLinesFromFile(filename);   
    	for (String line : inputLines) {
    		
    		if (line.contains("dhcp-range")) {
    			line = "dhcp-range="+iprange;
    		}    		
    		fileString += line+"\n";
    	}
    	writesuccess = writeLinesToFile(filename, fileString);
    	if (writesuccess == false) {
    		Log.e(MSG_TAG, "Unable to update conf/dnsmasq.conf with new lan-configuration.");
    		return writesuccess;
    	}    	
    	return writesuccess;
    }
}
