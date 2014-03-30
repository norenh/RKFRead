/*
 * Copyright 2014 Henning Norén
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.norenh.rkfread;

import java.io.IOException;
import android.nfc.TagLostException;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import java.util.Map;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class RKFRead extends Activity {
    private MifareClassic mfc = null;
    private Resources res;
    private TextView topTv;
    private TextView mainTv;
    private Button buttonDebug;
    private Button buttonTicket;
    private Button buttonContract;

    private TextView infoTv1a;
    private TextView infoTv1b;
    private TextView infoTv2a;
    private TextView infoTv2b;

    private Intent oldIntent = null;
    private String debugString = "";  
    private String contractString;
    private String ticketString;
    private String mainString;
    private String topString;
    protected final static String DISPLAY_MESSAGE = "se.norenh.rkfread.DISPLAY_MESSAGE";
    protected final static String DISPLAY_TITLE = "se.norenh.rkfread.DISPLAY_TITLE";

    public static enum CardType {
	GOTLAND,
	JOJO, // Länstrafiken Kronoberg and Skånetrafiken
	NORRBOTTEN,
	SL,
	UNINITIALIZED,
	UNKNOWN,
	VASTTRAFIKEN
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
 
	res = getResources();
	topTv = (TextView) findViewById(R.id.topTextView);
	mainTv = (TextView) findViewById(R.id.mainTextView);
	buttonContract = (Button) findViewById(R.id.button_contract);
	buttonDebug = (Button) findViewById(R.id.button_debug);
	buttonTicket = (Button) findViewById(R.id.button_ticket);

	infoTv1a = (TextView) findViewById(R.id.infoTextView1a);
	infoTv1b = (TextView) findViewById(R.id.infoTextView1b);
	infoTv2a = (TextView) findViewById(R.id.infoTextView2a);
	infoTv2b = (TextView) findViewById(R.id.infoTextView2b);

   }

   @Override
    public void onResume() {
        super.onResume();

	if(oldIntent != getIntent()) {
	    if(NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction())) {
		Tag tag = getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
		mfc = null;
		if(tag != null) {
		    mfc = MifareClassic.get(tag);
		}
		debugString = "";
		if(null != mfc) {
		    readCard();
		}
	    }
	}

	oldIntent = getIntent();
    }

    @Override
    public void onPause() {
	super.onPause();
    }

    public void buttonContract(View View) {
	Intent intent = new Intent(this, DisplayMessage.class);
	intent.putExtra(DISPLAY_MESSAGE, contractString);
	intent.putExtra(DISPLAY_TITLE, res.getString(R.string.contract_title));
	startActivity(intent);
    }

    public void buttonDebug(View View) {
	Intent intent = new Intent(this, DisplayMessage.class);
	intent.putExtra(DISPLAY_MESSAGE, debugString);
	intent.putExtra(DISPLAY_TITLE, res.getString(R.string.debug_title));
	startActivity(intent);
    }

    public void buttonTicket(View View) {
	Intent intent = new Intent(this, DisplayMessage.class);
	intent.putExtra(DISPLAY_MESSAGE, ticketString);
	intent.putExtra(DISPLAY_TITLE, res.getString(R.string.ticket_title));
	startActivity(intent);
    }

    private void readCard() {
	topTv.setText(R.string.reading);
	new ReadCardTask().execute();
    }

    private class ReadCardTask extends AsyncTask<Void, Void, Void> {
	private boolean tagLost = false;
	private CardType cardType = CardType.UNINITIALIZED; 
	private RKFCard card = null;

        @Override
        protected Void doInBackground(Void... arg) {
	    try {
		int bytePos = 0;
		mfc.connect();
		card = new RKFCard();

		if(!detectCardType()) {
		    card = null;
		    return null;
		}
		// only read the first 16 sectors
		for (int sector = 0; (sector < mfc.getSectorCount()) && (sector < 16); sector++) {
		    if(tryUnlock(sector)) {
			int startBlock = mfc.sectorToBlock(sector);
			for (int block = startBlock; block < (startBlock + 3); block++) {
				card.addBlock(sector, (block%4), mfc.readBlock(block));
			}
		    }
		    else {
			debugString += "Failed authenticate sector "+sector+System.getProperty("line.separator");;
		    }
		}
	    }
	    catch (TagLostException e) {
		tagLost = true;
		card = null;
		return null;
	    }
	    catch (IOException e) {
		card = null;
	    }
	    finally {
		try {
		    mfc.close();
		}
		catch (IOException e) {
		    card = null;
		}
	    }
	    return null;
	}

	protected boolean detectCardType() throws IOException {
	    if(mfc.authenticateSectorWithKeyA(1, hexStringToByteArray("434f4d4d4f41"))) {
		cardType = CardType.JOJO;
	    } else if(mfc.authenticateSectorWithKeyA(7, hexStringToByteArray("a64598a77478"))) {
		cardType = CardType.SL;
	    }
	    else if(mfc.authenticateSectorWithKeyA(7, hexStringToByteArray("0297927c0f77"))) {
		cardType = CardType.VASTTRAFIKEN;
	    }
	    else if(mfc.authenticateSectorWithKeyA(7, hexStringToByteArray("54726176656c"))) {
		cardType = CardType.NORRBOTTEN;
	    }
	    else {
		cardType = CardType.UNKNOWN;
		return false;
	    }
	    return true;
	}

	protected boolean tryUnlock(int sector) throws IOException {
	    boolean ret = false;

	    switch(cardType) {
	    case NORRBOTTEN:
		switch(sector) {
		case 0:
		case 1:
		case 2:
		case 3:
		case 4:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("fc00018778f7"));
		    break;
		case 5:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("0297927c0f77"));
		    break;
		case 6:
		case 7:
		case 8:
		case 9:
		case 10:
		case 11:
		case 12:
		case 13:
		case 14:
		case 15:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("54726176656c"));
		    break;
		default:
		    break;
		}
		break;
	    case SL:
		switch(sector) {
		case 0:
		case 1:
		case 2:
		case 3:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("fc00018778f7"));
		    break;
		case 4:
		case 5:
		case 6:
		case 7:
		case 10:
		case 11:
		case 12:
		case 13:
		case 14:
		case 15:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("a64598a77478"));
		    break;
		case 8:
		case 9:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("26940b21ff5d"));
		    break;
		default:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("ffffffffffff"));
		    break;
		}
		break;
	    case JOJO:
		switch(sector) {
		case 0:
		case 1:
		case 2:
		case 3:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("434f4d4d4f41"));
		    break;
		case 4:
		case 5:
		case 6:
		case 7:
		case 8:
		case 9:
		case 10:
		case 11:
		case 14:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("47524f555041"));
		    break;
		case 12:
		case 13:
		case 15:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("505249565441"));
		    break;
		default:
		    break;
		}
		break;
	    case VASTTRAFIKEN:
		switch(sector) {
		case 0:
		case 1:
		case 2:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("fc00018778f7"));
		    break;
		case 3:
		case 4:
		case 5:
		case 6:
		case 7:
		case 8:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("0297927c0f77"));
		    break;
		case 9:
		case 10:
		case 11:
		case 12:
		case 13:
		case 14:
		case 15:
		    ret = mfc.authenticateSectorWithKeyA(sector, hexStringToByteArray("54726176656c"));
		    break;
		default:
		    break;
		}
		break;
	    case UNKNOWN:
		// we could try brute force of known keys here in the future
		break;
	    }
	    return ret;
	}

	private void setTicket() {
	    if(null == card.dynTicket && null == card.specialTicket) {
		buttonTicket.setEnabled(false);
		return;
	    }
	    RKFObject jop = null;  // journey origin place
	    RKFObject jdp = null;  // journey destination place

	    String infoString=res.getString(R.string.last_ticket)+": ";
	    String time="";
	    String passengers = "";
	    String journey = "";
	    String jOrigin = res.getString(R.string.unknown);
	    String jDest = res.getString(R.string.unknown);
	    String cicoStatus = "";
	    String price = "";

	    if(null != card.dynTicket) { // TCTI
		RKFObject valLastTime = null;
		RKFObject valLastDate = null;

		// get the time when ticket was bought for main page and ticket message
		valLastDate = card.dynTicket.get("Validation last date");
		valLastTime = card.dynTicket.get("Validation last time");
		if(valLastDate != null && valLastTime != null) {
		    time = RKFCard.getStringFromDate(RKFCard.getDateFromInt((int)valLastDate.getValue()))+" "+
			RKFCard.getTimeFromInt((int)valLastTime.getValue());
		    infoString += time;
		}
		infoTv1a.setText(infoString);

		// get price, except for JOJO-cards as they seem to always set it to zero
		if(CardType.JOJO != cardType) {
		    RKFObject priceO = card.dynTicket.get("Price");
		    if(null != priceO) {
			price = String.format(res.getString(R.string.price),
					      card.getAmount((int)priceO.getValue()))+
			    System.getProperty("line.separator");
		    }
		}

		// get journey run, if present
		RKFObject jRun = null;
		jRun = card.dynTicket.get("Journey run");
		if(jRun != null) {
		    journey = String.format(res.getString(R.string.journey_run),jRun.getValue())+
			System.getProperty("line.separator");
		}
		
		// get the destination and origin place/zones
		jop = card.dynTicket.get("Journey origin place");
		jdp = card.dynTicket.get("Journey destination place");
		if(jop != null && jdp != null) {
		    jOrigin = jop.getValue()+"";
		    jDest = jdp.getValue()+"";
		}
		// set passengers in ticket-message
		passengers = getPassengers(card.dynTicket);
	    }
	    else { // special Ticket
		RKFObject jOriginDate = null;

		// get the time when ticket start on main page and for the ticket message
		jOriginDate = card.specialTicket.get("Journey origin date");
		if(jOriginDate != null) {
		    time = RKFCard.getStringFromDateTime(RKFCard.getDateTimeFromInt((int)jOriginDate.getValue()));
		    infoString += time;
		}
		infoTv1a.setText(infoString);

		// get price, except for JOJO-cards as they seem to always set it to zero
		if(CardType.JOJO != cardType) {
		    RKFObject priceO = card.specialTicket.get("Price");
		    if(null != priceO) {
			price = String.format(res.getString(R.string.price),
					      card.getAmount((int)priceO.getValue()))+
			    System.getProperty("line.separator");
		    }
		}

		// get the destination and origin place/zones
		jop = card.specialTicket.get("Journey origin place");
		jdp = card.specialTicket.get("Journey destination place");
		if(jop != null && jdp != null) {
		    jOrigin = jop.getValue()+"";
		    jDest = jdp.getValue()+"";
		}

		// get the check in/check out status if validation model is 1 (check in/check out)
		RKFObject cico = null;
		cico = card.specialTicket.get("Validation model");
		if(cico != null && 1 == cico.getValue()) {
		    cico = card.specialTicket.get("Validation status");
		    if(cico != null) {
			String str;
			switch((int)cico.getValue()) {
			case 1:
			    str = res.getString(R.string.cico_open);
			    break;
			case 2:
			    str = res.getString(R.string.cico_closed);
			    break;
			default:
			    str = res.getString(R.string.unspecified);
			    break;
			}
			cicoStatus = String.format(res.getString(R.string.cico), str)+
			    System.getProperty("line.separator");
		    }
		}

		// set passengers in ticket-message
		passengers = getPassengers(card.specialTicket);
	    }

	    // set origin and destination on the main-page info line
	    if(null != jop && null != jdp) {
		infoString = String.format(res.getString(R.string.ticket_orig_dest), jOrigin, jDest);
		infoTv1b.setText(infoString);
	    }

	    // build the full ticket-message string to show
	    ticketString = time+System.getProperty("line.separator")+price+journey+cicoStatus+
		String.format(res.getString(R.string.from_zone),jOrigin)+System.getProperty("line.separator")+
		String.format(res.getString(R.string.to_zone),jDest)+System.getProperty("line.separator")+
		passengers;
	    // enable the ticket button
	    buttonTicket.setEnabled(true);
	}

	private void setContract() {
	    if(card.dynContract == null) {
		// if no dynamic contract exist on the card, make sure contract button is disabled and show nothing
		buttonContract.setEnabled(false);
		return;
	    }
	    // these are the RKFObjects from the card that we will try get info from
	    RKFObject valStartDate = null;
	    RKFObject valStartTime = null;
	    RKFObject valEndDate = null;
	    RKFObject valEndTime = null;
	    RKFObject valZonePlace = null;
	    // strings to build up the final contract-message string
	    String startTime = "";
	    String endTime = "";
	    String zones = "";
	    String passengers = "";
	    // infoString is used for the main page info line
	    String infoString = res.getString(R.string.last_contract)+": ";

	    // set Validity start time and date in contract-message
	    valStartDate = card.dynContract.get("Validity start date");
	    valStartTime = card.dynContract.get("Validity start time");
	    if(valStartDate != null && valStartTime != null) {
		String str = RKFCard.getStringFromDate(RKFCard.getDateFromInt((int)valStartDate.getValue()))+
		    " "+RKFCard.getTimeFromInt((int)valStartTime.getValue());
		
		startTime = String.format(res.getString(R.string.validity_start_time), str)+System.getProperty("line.separator");
	    }

	    // set validity end time and date in contract-message and the main page
	    valEndDate = card.dynContract.get("Validity end date");
	    valEndTime = card.dynContract.get("Validity end time");
	    if(valEndDate != null && valEndTime != null) {
		String str = RKFCard.getStringFromDate(RKFCard.getDateFromInt((int)valEndDate.getValue()))+" "+RKFCard.getTimeFromInt((int)valEndTime.getValue());
		infoString += str;
		endTime =  String.format(res.getString(R.string.validity_end_time), str)+System.getProperty("line.separator");
	    }
	    infoTv2a.setText(infoString);

	    // set zones in contract-message
	    valZonePlace = card.dynContract.get("Validity zone place");    
	    zones = getZones(valZonePlace);

	    // set passengers in contract-message
	    passengers = getPassengers(card.dynContract);

	    // build the full contract-message string to show
	    contractString = startTime+endTime+passengers+zones;
	    // enable the contract button
	    buttonContract.setEnabled(true);
	}

	private String getPassengers(final Map<String,RKFObject> m) {
	    String ret = "";
	    RKFObject o = m.get("Passenger subgroup(1)");
	    if(null != o) {
		ret = parsePassengerVal((int)o.getValue());
	    }
	    o = m.get("Passenger subgroup(2)");
	    if(null != o) {
		ret += parsePassengerVal((int)o.getValue());
	    }
	    o = m.get("Passenger subgroup(3)");
	    if(null != o) {
		ret += parsePassengerVal((int)o.getValue());
	    }
	    return ret;
	}

	private String parsePassengerVal(int i) {
	    String ret = "";
	    int nr = (i&0xFF);
	    if(0 == nr) {
		return "";
	    }

	    if((CardType.JOJO == cardType) && (0x103 == i)) {
		// JOJO cards seems to encode family/duo tickets as 3 Adults
		return String.format(res.getString(R.string.passenger), 1)+" "+ 
		    res.getString(R.string.passenger_jojo_duo)+System.getProperty("line.separator");
	    }

	    ret =  String.format(res.getString(R.string.passenger), nr)+" ";

	    switch (i>>8) {
	    case 0:
		ret += res.getString(R.string.unspecified);
		break;
	    case 1:
		ret += res.getString(R.string.passenger_adult);
		break;
	    case 2:
		ret += res.getString(R.string.passenger_child);
		break;
	    case 3:
		ret += res.getString(R.string.passenger_student);
		break;
	    case 4:
		ret += res.getString(R.string.passenger_pensioner);
		break;
	    default:
		ret += res.getString(R.string.unknown);
		break;
	    }
	    return ret+System.getProperty("line.separator");
	}

	private String getZones(final RKFObject o) {
	    RKFObject curr = o;
	    String ret = "";
	    // build up a string of all the zones in the single-linked list of RKFObjects
	    while(curr != null) {
		ret += res.getString(R.string.zone)+": "+
		    curr.getValue()+System.getProperty("line.separator");
		curr = curr.getNext();
	    }
	    return ret;
	}

	@Override
        protected void onPostExecute(Void result) {
	    if(tagLost) {
		// handle lost tag while reading
		topTv.setText(R.string.tag_lost);
		card = null;
		tagLost = false;
	    } 
	    else {
		// if the tag was not lost during read
		if(card != null) {
		    card.parseCard();
		    // activate and set debug message view after card parsing
		    debugString += card.getDebug();
		    buttonDebug.setEnabled(true);

		    // get vendor and serial number and set the top string of main view
		    if(card.firstSector != null) {
			topString = String.format(res.getString(R.string.top_string), 
						  card.firstSector.get("Serial number").getValue(),
						  RKFCard.getVendor((int)card.firstSector.get("Card provider").getValue()));
			topTv.setText(topString);
		    }
		    // get the purse value and set it as main string of main view
		    if(card.dynPurse != null) {
			mainString = card.getAmount((int)card.dynPurse.get("Value").getValue());
			mainTv.setText(mainString);
		    }

		    // set the ticket and contract buttons and their message views
		    setTicket();
		    setContract();
		}
		else if(CardType.UNKNOWN == cardType) {
		    topTv.setText(R.string.unknown_card);
		}
	    }
	}
    }

    private static byte[] hexStringToByteArray(final String s) {
	int len = s.length();
	byte[] b = new byte[(len/2)];
	for(int i=0;i<len;i+=2) {
	    b[(i/2)] = (byte)((Character.digit(s.charAt(i), 16) << 4) +
			      Character.digit(s.charAt(i+1), 16));
	}
	return b;
    }
}
