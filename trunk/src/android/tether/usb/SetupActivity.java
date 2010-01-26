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

package android.tether.usb;

import android.R.drawable;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;

public class SetupActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	
	private TetherApplication application = null;
	
	private ProgressDialog progressDialog;
	
	public static final String MSG_TAG = "TETHER -> SetupActivity";
	
	public static final String DEFAULT_LANNETWORK = "192.168.2.0/24";

    private String currentLAN;
    
    private static int ID_DIALOG_RESTARTING = 2;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Init Application
        this.application = (TetherApplication)this.getApplication();
        
        addPreferencesFromResource(R.layout.setupview); 
    }
	
    @Override
    protected void onResume() {
    	Log.d(MSG_TAG, "Calling onResume()");
    	super.onResume();
    	getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        this.updatePreferences();
    }
    
    @Override
    protected void onPause() {
    	Log.d(MSG_TAG, "Calling onPause()");
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);   
    }

    @Override
    protected Dialog onCreateDialog(int id) {
    	if (id == ID_DIALOG_RESTARTING) {
	    	progressDialog = new ProgressDialog(this);
	    	progressDialog.setTitle("Restart Tethering");
	    	progressDialog.setMessage("Please wait while restarting...");
	    	progressDialog.setIndeterminate(false);
	    	progressDialog.setCancelable(true);
	        return progressDialog;
    	}
    	return null;
    }
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    	updateConfiguration(sharedPreferences, key);
    }
    
    Handler restartingDialogHandler = new Handler(){
        public void handleMessage(Message msg) {
        	if (msg.what == 0)
        		SetupActivity.this.showDialog(SetupActivity.ID_DIALOG_RESTARTING);
        	else
        		SetupActivity.this.dismissDialog(SetupActivity.ID_DIALOG_RESTARTING);
        	super.handleMessage(msg);
        	System.gc();
        }
    };
    
   Handler displayToastMessageHandler = new Handler() {
        public void handleMessage(Message msg) {
       		if (msg.obj != null) {
       			SetupActivity.this.application.displayToastMessage((String)msg.obj);
       		}
        	super.handleMessage(msg);
        	System.gc();
        }
    };
    
    private void updateConfiguration(final SharedPreferences sharedPreferences, final String key) {
    	new Thread(new Runnable(){
			public void run(){
			   	String message = null;
		    	if (key.equals("wakelockpref")) {
					try {
						boolean disableWakeLock = sharedPreferences.getBoolean("wakelockpref", false);
						if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/dnsmasq")) {
							if (disableWakeLock){
								SetupActivity.this.application.releaseWakeLock();
								message = "Wake-Lock is now disabled.";
							}
							else{
								SetupActivity.this.application.acquireWakeLock();
								message = "Wake-Lock is now enabled.";
							}
						}
					}
					catch (Exception ex) {
						message = "Unable to save Auto-Sync settings!";
					}
					
					// Send Message
	    			Message msg = new Message();
	    			msg.obj = message;
	    			SetupActivity.this.displayToastMessageHandler.sendMessage(msg);
		    	}
		    	else if (key.equals("lannetworkpref")) {
		    		String lannetwork = sharedPreferences.getString("lannetworkpref", DEFAULT_LANNETWORK);
		    		if (lannetwork.equals(SetupActivity.this.currentLAN) == false) {
		    			
		    			// Updating lan-config
		    			SetupActivity.this.application.coretask.writeLanConf(lannetwork);

		    			// Restarting
						try{
							if (application.coretask.isNatEnabled() && application.coretask.isProcessRunning("bin/dnsmasq")) {
				    			// Show RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(0);
				    			// Restart Tethering
								SetupActivity.this.application.restartTether();
				    			// Dismiss RestartDialog
				    			SetupActivity.this.restartingDialogHandler.sendEmptyMessage(1);
							}
						}
						catch (Exception ex) {
							Log.e(MSG_TAG, "Exception happend while restarting service - Here is what I know: "+ex);
						}

						message = "LAN-network changed to '"+lannetwork+"'.";
						SetupActivity.this.currentLAN = lannetwork;

						// Update preferences with real values
						SetupActivity.this.updatePreferences();
						
		    			// Send Message
		    			Message msg = new Message();
		    			msg.obj = message;
		    			SetupActivity.this.displayToastMessageHandler.sendMessage(msg);
		    		}
		    	}
			}
		}).start();
    }
    
    private void updatePreferences() {
        // LAN configuration
        this.currentLAN = this.application.coretask.getLanIPConf();
        this.application.preferenceEditor.putString("lannetworkpref", this.currentLAN);
        
        // Sync-Status
        this.application.preferenceEditor.commit(); 
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	boolean supRetVal = super.onCreateOptionsMenu(menu);
    	SubMenu installBinaries = menu.addSubMenu(0, 0, 0, getString(R.string.installtext));
    	installBinaries.setIcon(drawable.ic_menu_set_as);
    	return supRetVal;
    }    
    
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
    	boolean supRetVal = super.onOptionsItemSelected(menuItem);
    	Log.d(MSG_TAG, "Menuitem:getId  -  "+menuItem.getItemId()+" -- "+menuItem.getTitle()); 
    	if (menuItem.getItemId() == 0) {
    		this.application.installFiles();
    		this.updatePreferences();
    	}
    	return supRetVal;
    } 
}
