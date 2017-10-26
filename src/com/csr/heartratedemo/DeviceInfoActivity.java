/******************************************************************************
 *  Copyright (C) Cambridge Silicon Radio Limited 2014
 *
 *  This software is provided to the customer for evaluation
 *  purposes only and, as such early feedback on performance and operation
 *  is anticipated. The software source code is subject to change and
 *  not intended for production. Use of developmental release software is
 *  at the user's own risk. This software is provided "as is," and CSR
 *  cautions users to determine for themselves the suitability of using the
 *  beta release version of this software. CSR makes no warranty or
 *  representation whatsoever of merchantability or fitness of the product
 *  for any particular purpose or use. In no event shall CSR be liable for
 *  any consequential, incidental or special damages whatsoever arising out
 *  of the use of or inability to use this software, even if the user has
 *  advised CSR of the possibility of such damages.
 *
 ******************************************************************************/

package com.csr.heartratedemo;

import com.csr.view.DataView;
import android.os.Bundle;
import android.view.MenuItem;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;

public class DeviceInfoActivity extends Activity {


	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_device_info);
		
		// Display back button in action bar.
		getActionBar().setDisplayHomeAsUpEnabled(true);
		
        // Prevent screen rotation.
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

        final DataView batteryData = (DataView) findViewById(R.id.batteryData);
        final DataView manufacturerData = (DataView) findViewById(R.id.manufacturerData);
        final DataView hwRevData = (DataView) findViewById(R.id.hardwareRevData);
        final DataView swRevData = (DataView) findViewById(R.id.swRevData);
        final DataView fwRevData = (DataView) findViewById(R.id.fwRevData);
        final DataView serialNoData = (DataView) findViewById(R.id.serialNoData);

        Intent intent = getIntent();
        batteryData.setValueText(intent.getExtras().getString(HeartRateActivity.EXTRA_BATTERY));
        manufacturerData.setValueText(intent.getExtras().getString(HeartRateActivity.EXTRA_MANUFACTURER));
        hwRevData.setValueText(intent.getExtras().getString(HeartRateActivity.EXTRA_HARDWARE_REV));
        swRevData.setValueText(intent.getExtras().getString(HeartRateActivity.EXTRA_SW_REV));
        fwRevData.setValueText(intent.getExtras().getString(HeartRateActivity.EXTRA_FW_REV));
        serialNoData.setValueText(intent.getExtras().getString(HeartRateActivity.EXTRA_SERIAL));	
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) { 
	        switch (item.getItemId()) {
	        case android.R.id.home: 
	            // Back button in action bar should have the same behaviour as the phone back button.
	            onBackPressed();
	            return true;
	        }

	    return super.onOptionsItemSelected(item);
	}
}
