package org.team2180.scoutingServer;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Iterator;

import javax.microedition.io.StreamConnection;
import javax.bluetooth.RemoteDevice;
import org.json.*;

public class ConnectionHandler implements Runnable {
	final StreamConnection connection;
	final JSONObject TEAM_DATA;
	RemoteDevice remDev;
	String deviceIndex;
	public ConnectionHandler(StreamConnection connection,JSONObject TEAM_DATA) {
		this.connection = connection;
		this.TEAM_DATA = TEAM_DATA;
		try {
			this.remDev = RemoteDevice.getRemoteDevice(connection);
			this.deviceIndex = remDev.getFriendlyName(true)+'@'+remDev.getBluetoothAddress();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			this.remDev = null;
			this.deviceIndex = null;
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		
		try {
			System.out.println("Server: Found a friend in '"+remDev.getFriendlyName(true)+"' @ "+remDev.getBluetoothAddress());
			DataOutputStream oS = new DataOutputStream(connection.openOutputStream());
			DataInputStream iS = new DataInputStream(connection.openInputStream()); 
				
			int handshake = iS.readInt();
			
			if(handshake==1) {
				System.out.println(deviceIndex+" will now inform you of TOP SECRET_INFO");
				updateDatabase(remDev, oS, iS);
				int entryCount = TEAM_DATA.getJSONObject(deviceIndex).getInt("entryCount");
			}else if(handshake==2) {
				System.out.println(deviceIndex+" would like to copy your notes");
				updateDevice(remDev, oS, iS);
				connection.close();
			}else if(handshake==3) {
				System.out.println(deviceIndex+" wants to see the teams we are viewing");
				sendTeamList(remDev, oS, iS);
			}else{
				System.out.println(deviceIndex+" said: "+handshake+"; not acceptable code");
				connection.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(deviceIndex+"'s thread is terminating BADLY!");
			try {connection.close();} catch (IOException e1) {e1.printStackTrace();}
			return;
		}
		System.out.println(deviceIndex+"'s thread is terminating!\n-----------------------------------------");
		return;
	}

	public void updateDevice(RemoteDevice remDev, DataOutputStream oS, DataInputStream iS) throws Exception {
		
		Iterator<?> deviceKeys = TEAM_DATA.keys();
		while(deviceKeys.hasNext()) {
			String devKey = (String)deviceKeys.next();
			if(devKey.equals("__TEAMLIST__")) {continue;}
			int devEntryCount = (int)TEAM_DATA.getJSONObject(devKey).getInt("entryCount");
			int i = 0;
			System.out.println(deviceIndex+" is now getting"+devKey+"'s stored data");
			while(i < devEntryCount) {
					oS.writeUTF(TEAM_DATA.getJSONObject(devKey).getString(i+""));
					oS.flush();
					int remReady = iS.readInt();
					if(remReady != 2) {
						connection.close();
						throw new Exception(deviceIndex+" has refused the data at: "+devKey+'['+i+']'+"; responded with:"+remReady);
					}else{
						System.out.println(deviceIndex+" has been sent new data!");
					}
					try {
						TEAM_DATA.getJSONObject(devKey).getString((i+1)+"");
						oS.writeInt(2);
						System.out.println(deviceIndex+": found more info in "+devKey);
					}catch(JSONException e) {
						oS.writeInt(1);
						System.out.println(deviceIndex+": no more info in "+devKey);
					}
					i++;
					oS.flush();
			}
		} 
		oS.writeInt(0);//send term signal
		oS.close();
		System.out.println(deviceIndex+" is informed!");
		connection.close();
	}
	
	public void updateDatabase(RemoteDevice remDev, DataOutputStream oS, DataInputStream iS) throws IOException, JSONException {
		//OK!
		oS.writeInt(2);
		oS.flush();
		String data = iS.readUTF();
		
		if(data!=null) {	
		}
		JSONObject deviceLocalTable;
		
		try{
			deviceLocalTable = TEAM_DATA.getJSONObject(deviceIndex);
		}catch(JSONException e) {
			//e.printStackTrace();
			System.out.println(deviceIndex+" has no home. Sad!");
			//Give the device's data a local home, and put data in there. No need to check for dupilicates
			TEAM_DATA.put(deviceIndex, new JSONObject());
			deviceLocalTable = TEAM_DATA.getJSONObject(deviceIndex);
			deviceLocalTable.put("0", data);
			deviceLocalTable.put("entryCount", 1);
			connection.close();
			return;
		}
		
		int i = 0;
			//Make sure we don't have duplicates!
		int devEntryCount = deviceLocalTable.getInt("entryCount");
		while(i < devEntryCount) {
			String jsonText = deviceLocalTable.getString(i+"");
			if(jsonText.equals(data)) {
				//Don't save duplicates!
				System.out.println(deviceIndex+" had a duplicate at ["+i+"] data! No duplicates!");
				oS.writeInt(1);
				return;
			}
				i++;
			}
		
		connection.close();
		System.out.println(deviceIndex+" had new OC, adding at["+deviceLocalTable.getInt("entryCount")+"]");
		deviceLocalTable.put(Integer.toString(deviceLocalTable.getInt("entryCount")), data);
		deviceLocalTable.put("entryCount", deviceLocalTable.getInt("entryCount")+1);
		return;
	}
	public void sendTeamList(RemoteDevice remDev, DataOutputStream oS, DataInputStream iS) throws Exception{
		JSONArray teamList = TEAM_DATA.getJSONArray("__TEAMLIST__");
		int i = 0;
		while(i<teamList.length()) {
			oS.writeUTF((String)teamList.get(i));
			oS.flush();
				int remReady = iS.readInt();
				if(remReady != 2) {
					connection.close();
					throw new Exception(deviceIndex+" has refused the data at: "+i+"; responded with:"+remReady);
				}else{
				}
				if(i==teamList.length()-1) {
					oS.writeInt(0);//send term signal
				}else {
					oS.writeInt(2);
				}
				i++;
				oS.flush();
		} 
		oS.close();
		System.out.println(deviceIndex+" can into team!");
		connection.close();
	}
}
