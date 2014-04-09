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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.HashMap;

public class RKFCard {
    public byte[] bytes;
    private int unit = 1;
    public String currency = "Not set";
    private int[] dynamicData = null;
    private int[] tcdi = null;
    /** These maps contains the RKFObjects or values of the card with the 
	value name (as taken from the specifications, if it exist) for key.
	If multiple values have the same name, the RKFObjects will form a 
	single linked list */
    public Map<String,RKFObject> firstSector = null;
    public Map<String,RKFObject> directory = null;
    public Map<String,RKFObject> appStatus = null;
    public Map<String,RKFObject> eventLog = null;
    public Map<String,RKFObject> purse = null;
    public Map<String,RKFObject> dynPurse = null;
    public Map<String,RKFObject> dynPurseOld = null;
    public Map<String,RKFObject> ticket = null;
    public Map<String,RKFObject> dynTicket = null;
    public Map<String,RKFObject> dynTicketOld = null;
    public Map<String,RKFObject> contract = null;
    public Map<String,RKFObject> dynContract = null;
    public Map<String,RKFObject> dynContractOld = null;
    public Map<String,RKFObject> discount = null;
    public Map<String,RKFObject> customerProfile = null;
    public Map<String,RKFObject> specialTicket = null;
    private int pos;
    private int cardVersion = -1;
    private Calendar baseTime; // used for relative TimeDate attributes
    private String debugString = "";
    private boolean isSL = false;
    private boolean isRejseKort = false;

    public RKFCard() {
	bytes = new byte[16*48];
    }

    public RKFCard(byte[] b) {
	bytes = b;
	parseCard();
    }

    public void addBlock(int sector, int block, byte[] b) {
	if((sector > 15) || (block > 2)) // skip keyblocks and sectors above 16
	    return;
	int bytePos = (sector*48) + (block*16);
	System.arraycopy(b, 0, bytes, bytePos, 16);
    }

    public boolean parseCard() {
	if(bytes == null) {
	    return false;
	}
	pos = 0;

	debug("--- Start parsing ---");
	debug("-- First Sector");
	parseFirstSector();
	debug("-- TCDI");
	if(isSL) {
	    // SL (or version 4) seems to have two TCDI and from the looks of it
	    // the second one seems to be the valid one
	    parseTCDI(256*3);
	}
	else {
	    parseTCDI(128*3);
	}

	for(int i=2;i<(bytes.length/48);i++) {
	    if(i < 16 && tcdi[i] == 0x01)
	    	continue;
	    parseSector(i);
	}
	debug("--- Finished parsing ---");
	//searchForVal(0, (16*48*8), 16, 627547); 

	return true;
    }

    private void parseSector(int n) {
	int sb = n*48;
	int ident;
	debug("-- Parsing Sector "+n+":");
	pos = sb*8;
	ident = getIntFromPos(8);
	debug("Identifer: "+Integer.toHexString(ident));
	switch (ident) {
	case 0xA0:
	    parseTCAS(sb*8);
	    break;
	case 0xA1:
	    parseTCDB(sb*8);
	    break;
	case 0xA2:
	    parseTCCP(sb*8);
	    break;
	case 0xA3:
	    parseTCST(sb*8);
	    break;
	case 0x84:
	    parseEventLog(sb*8);
	    break;
	case 0x85:
	    parsePurse(sb*8);
	    break;
	case 0x86:
	    parseTCTI(sb*8);
	    break;
	case 0x87:
	    parseTCCO(sb*8);
	    break;
	default:
	    debug("Unknown sector");
	}	
    }

    private void parseFirstSector() {
	long ret;
	firstSector = new HashMap<String,RKFObject>();
	pos = 0;
	addAttribute("Serial number", 32, firstSector);

	// go to next block and skip MAD Info Byte
	pos = 16*9;
	//addAttribute("MAD Info Byte", 16, firstSector);
	cardVersion = (int)addAttribute("Card version", 6, firstSector);
	
	ret = addAttribute("Card provider", 12, firstSector, RKFObject.RKFType.AID);
	if(0x65 == ret) {
	    isSL = true;
	}
	else if(0x7d0 == ret) {
	    isRejseKort = true;
	}

	addAttribute("Card validity end date", 14, firstSector,RKFObject.RKFType.Date);
	addAttribute("Status", 8, firstSector, RKFObject.RKFType.Status);
	ret = addAttribute("Currency unit", 16, firstSector, RKFObject.RKFType.CurrencyUnit);
	setCurrencyUnit((int)ret);
	addAttribute("Event log version", 6, firstSector);
    }

    // TCAS: Application Status 0xA0
    private void parseTCAS(int n) {
	appStatus = new HashMap<String,RKFObject>();
	dynamicData = new int[16];

	pos = n+8;

	addAttribute("Version", 6, appStatus);
	for(int i=0;i<16;i++) {
	    dynamicData[i] = (int)addAttribute("Sector status("+i+")", 2, appStatus);
	}
	addAttribute("Transaction number", 8, appStatus);
	addAttribute("Event log record number", 4, appStatus);
	addAttribute("Ticket log area sector pointer", 4, appStatus);
	for(int i=0;i<8;i++) {
	    addAttribute("Ticket log sector pointer("+i+")", 4, appStatus);
	}

    }

    // Directory
    private void parseTCDI(int n) {
	directory = new HashMap<String,RKFObject>();
	tcdi = new int[16];

	pos = n;
	int s = 1;
	boolean aid_bool = true;
	for(int i=1;i<31;i++) {
	    if(aid_bool) {
		addAttribute("AID (Sector"+s+")", 12, directory, RKFObject.RKFType.TCDIAID);
	    }
	    else {
		tcdi[s] = (int)addAttribute("PIX (Sector"+s+")", 12, directory);
		s++;
	    }
	    if(i%10 == 0) // skip MAC at end of block
		pos+=8;
	    aid_bool = !aid_bool;
	}
	
    }

    // TCEL: Event Log 0x84
    private void parseEventLog(int n) {
	pos = n + 8;
	eventLog = new HashMap<String,RKFObject>();

	for(int i=0; i<3; i++) {
	    addAttribute("Event Date Stamp", 14, eventLog, RKFObject.RKFType.Date);
	    addAttribute("Event Time Stamp", 16, eventLog, RKFObject.RKFType.Time);
	    addAttribute("AID", 12, eventLog, RKFObject.RKFType.AID);
	    addAttribute("Device", 16, eventLog);
	    addAttribute("Device Transaction Number", 24, eventLog);
	    addAttribute("Event Code", 6, eventLog);
	    addAttribute("Event Data", 24, eventLog);
	    pos += 16; // skip checksum and next identifier
	}
    }

    // TCTI/TCCO: Dynamic Content
    private void parseDynamicTicketContract(int n, int end, boolean tcti, final Map<String,RKFObject> m) {
	pos = n;

	while(pos<(end-24)) {
	    int id = getIntFromPos(8);
	    debug("Identifier: "+id+" (0x"+Integer.toHexString(id)+")");
	    pos += 8;
	    switch(id) {
	    case 0x9F:
		addAttribute("Validation model", 2, m, RKFObject.RKFType.ValidationModel);
		addAttribute("Validation status", 2, m, RKFObject.RKFType.ValidationStatus);
		addAttribute("Validation level", 2, m);
		break;
	    case 0x9C:
		addAttribute("Passenger class", 2, m);
		for(int i=1;i<4;i++) {
		    addAttribute("Passenger subgroup("+i+")", 14, m, RKFObject.RKFType.PassSubGroup);
		}
		break;
	    case 0x9A:
		addAttribute("Validation total issued journeys", 8, m);
		addAttribute("Validation total issued journeys within period", 8, m);
		// Field ends with ValidationLastDate and ValidationLastTime 
		// just like 0x9E, so just continue parsing 0x9E below.
		// Also, 0x9A and 0x9E are specific for TCCO and TCTI
		// respectivly so collisions should not happen
	    case 0x9E:
		addAttribute("Validation last date", 14, m, RKFObject.RKFType.Date);
		addAttribute("Validation last time", 16, m, RKFObject.RKFType.Time);
		break;
	    case 0x99:
		addAttribute("Validity zone AID", 12, m, RKFObject.RKFType.AID);
		addAttribute("Validity zone place", 14, m);
		break;
	    case 0x98:
		addAttribute("Journey route AID", 12, m, RKFObject.RKFType.AID);
		addAttribute("Journey route number", 12, m);
		break;
	    case 0x97:
		addAttribute("Journey origin AID", 12, m, RKFObject.RKFType.AID);
		addAttribute("Journey origin place", 14, m);
		addAttribute("Journey destination AID", 12, m, RKFObject.RKFType.AID);
		addAttribute("Journey destination place", 14, m);
		addAttribute("Journey distance", 12, m);
		addAttribute("Journey run", 12, m);
		addAttribute("Journey via 1 AID", 12, m, RKFObject.RKFType.AID);
		addAttribute("Journey via 1 place", 14, m);
		addAttribute("Journey via 2 AID", 12, m, RKFObject.RKFType.AID);
		addAttribute("Journey via 2 place", 14, m);
		addAttribute("Journey interchange", 6, m);
		break;
	    case 0x96:
		addAttribute("Validity start date", 14, m, RKFObject.RKFType.Date);
		addAttribute("Validity start time", 16, m, RKFObject.RKFType.Time);
		addAttribute("Validity end date", 14, m, RKFObject.RKFType.Date);
		addAttribute("Validity end time", 16, m, RKFObject.RKFType.Time);
		addAttribute("Validity duration", 8, m);
		addAttribute("Validity limit time", 14, m, RKFObject.RKFType.Date);
		addAttribute("Period journeys", 8, m);
		addAttribute("Restrict day", 8, m);
		addAttribute("Restrict time code", 8, m);
		break;
	    case 0x95:
		addAttribute("Purse pointer", 4, m);
		break;
	    case 0x94:
		addAttribute("Price", 20, m, RKFObject.RKFType.Amount);
		break;
	    case 0x93:
		debug("MAC - Skipping the rest");
		//skipPos(24); // skipping MAC
		// Skip to end as it seems like MAC is the last information-element
		pos = end;
		break;
	    case 0x89:
		addAttribute("AID", 12, m, RKFObject.RKFType.AID);
		addAttribute("PIX", 12, m);
		addAttribute("Sale device", 16, m);
		addAttribute("Contract serial number", 32, m);
		addAttribute("Status", 8, m, RKFObject.RKFType.Status);
		break;
	    case 0x88:
		addAttribute("Transaction number", 12, m);
		if(isSL && !tcti) {
		    skipPos(4);
		}
		break;
	    case 0x8A:
		// dynamic information without transaction number. It is only an identifier
		break;
	    default:
		break;
	    }
	}
	
    }

    // TCTI: Ticket 0x86
    private void parseTCTI(int n) {
	int dynStart, dynStartPos, dynBlockLen;
	ticket = new HashMap<String,RKFObject>();
	dynTicket = new HashMap<String,RKFObject>();
	dynTicketOld = new HashMap<String,RKFObject>();
	pos = n + 8;

	addAttribute("Version", 6, ticket);
	
	/** This seems to work when calculating where the dynamic block starts
	 and how long they are for TCTI. However, it is probably incorrect */
	dynStart = (int)skipPos(4);
	dynStartPos = n+(128*dynStart);
	dynBlockLen = (int)skipPos(4) * 128;

	if(dynamicData[n/(48*8)] == 2) {
	    // Latest dynamic data in second field
	    debug("- Old Ticket");
	    parseDynamicTicketContract(dynStartPos, dynStartPos+dynBlockLen, true, dynTicketOld);
	    dynStartPos += dynBlockLen;
	    debug("- Current Ticket");
	    parseDynamicTicketContract(dynStartPos, dynStartPos+dynBlockLen, true, dynTicket);
	}
	else {
	    debug("- Current Ticket");
	    parseDynamicTicketContract(dynStartPos, dynStartPos+dynBlockLen, true, dynTicket);
	    dynStartPos += dynBlockLen;
	    debug("- Old Ticket");
	    parseDynamicTicketContract(dynStartPos, dynStartPos+dynBlockLen, true, dynTicketOld);
	}
    }

    // TCCO: Contract 0x87
    private void parseTCCO(int n) {
	if(null != contract)  // skip any extra contract found (ugly fix for SL-cards with extra contract)
	    return;
	int dynStart, dynStartPos, dynBlockLen;
	contract = new HashMap<String,RKFObject>();
	dynContract = new HashMap<String,RKFObject>();
	dynContractOld = new HashMap<String,RKFObject>();
	pos = n + 8;

	addAttribute("Version", 6, contract);

	/** This seems to work when calculating where the dynamic block starts
	 and how long they are for TCCO. However, it is probably incorrect */
	dynStart = (int)skipPos(4);
	dynStartPos = n+(128*dynStart);
	dynBlockLen = (int)skipPos(4) * 128;

	// This is quite wrong, but if TCDI/dynamic data is not found, assume first field contains latest contract.
	int dynField = 1; 
	if(null != dynamicData) { 
	    dynField = dynamicData[n/(48*8)];
	}

	if(dynField == 2) {
	    // Latest dynamic data in second field
	    debug("-Old Contract");
	    parseDynamicTicketContract(dynStartPos, dynStartPos+dynBlockLen, false, dynContractOld);
	    dynStartPos += dynBlockLen;
	    debug("-Current Contract");
	    parseDynamicTicketContract(dynStartPos, dynStartPos+dynBlockLen, false, dynContract);
	}
	else {
	    debug("-Current Contract");
	    parseDynamicTicketContract(dynStartPos, dynStartPos+dynBlockLen, false, dynContract);
	    dynStartPos += dynBlockLen;
	    debug("-Old Contract");
	    parseDynamicTicketContract(dynStartPos, dynStartPos+dynBlockLen, false, dynContractOld);
	}
    }

    // TCDB: Discount Basis 0xA1
    private void parseTCDB(int n) {
	discount = new HashMap<String,RKFObject>();

	pos = n + 8;

	addAttribute("Version", 6, discount);
	addAttribute("AID", 12, discount, RKFObject.RKFType.AID);
	for(int i=1;i<4;i++) {
	    addAttribute("Discount type("+i+")", 8, discount);
	}
	pos += 78;
	
	// Dynamic Data
	addAttribute("Status", 8, discount, RKFObject.RKFType.Status);
	addAttribute("First month", 8, discount);
	for(int i=1;i<4;i++) {
	    addAttribute("Discount basis block("+i+")", 29, discount);
	}
	pos += 24; //MAC
    }

    // TCCP, Customer Profile 0xA2
    private void parseTCCP(int n) {
        customerProfile = new HashMap<String,RKFObject>();

	pos = n + 8;

	addAttribute("Version", 6, customerProfile);
	addAttribute("AID", 12, customerProfile, RKFObject.RKFType.AID);
	addAttribute("Status", 8, customerProfile, RKFObject.RKFType.Status);
	addAttribute("Customer number", 34, customerProfile);
	addAttribute("Passenger class", 2, customerProfile);

	for(int i=1;i<4;i++) {
	    addAttribute("Passenger subgroup("+i+")", 14, customerProfile, RKFObject.RKFType.PassSubGroup);
	}

	addAttribute("Validation level", 2, customerProfile);
	addAttribute("Birthday", 11, customerProfile);
	addAttribute("Language", 4, customerProfile);
	addAttribute("Dialogue preferences", 8, customerProfile);
	addAttribute("Subscription or credit company", 12, customerProfile);
	addAttribute("Subscription or credit type", 8, customerProfile);

	//pos += 24; // MAC
    }

    // TCST: Special Ticket 0xA3
    private void parseTCST(int n) {
        specialTicket = new HashMap<String,RKFObject>();
	int ret;

	pos = n + 8;

	int version = (int)addAttribute("Version", 6, specialTicket);
	addAttribute("AID", 12, specialTicket, RKFObject.RKFType.AID);
	addAttribute("PIX", 12, specialTicket);
	addAttribute("Status", 8, specialTicket, RKFObject.RKFType.Status);
	addAttribute("Passenger class", 2, specialTicket);
	for(int i=1;i<4;i++) {
	    addAttribute("Passenger subgroup("+i+")", 14, specialTicket, RKFObject.RKFType.PassSubGroup);
	}
	addAttribute("Validation model", 2, specialTicket, RKFObject.RKFType.ValidationModel);
	addAttribute("Validation status", 2, specialTicket, RKFObject.RKFType.ValidationStatus);
	addAttribute("Validation level", 2, specialTicket);
	addAttribute("Price", 20, specialTicket, RKFObject.RKFType.Amount);
	addAttribute("Price modification level", 6, specialTicket);

	if(isSL)
	    skipPos(4);

	addAttribute("Journey origin AID", 12, specialTicket, RKFObject.RKFType.AID);
	addAttribute("Journey origin place", 14, specialTicket);
	ret = (int)addAttribute("Journey origin date", 24, specialTicket, RKFObject.RKFType.DateTime);
	baseTime = getDateTimeFromInt(ret);
	addAttribute("Journey furthest AID", 12, specialTicket, RKFObject.RKFType.AID);
	addAttribute("Journey furthest place", 14, specialTicket);
	addAttribute("Furthest time", 10, specialTicket, RKFObject.RKFType.RelTime);
	addAttribute("Journey destination AID", 12, specialTicket, RKFObject.RKFType.AID);
	addAttribute("Journey destination place", 14, specialTicket);
	addAttribute("Journey destination time", 10, specialTicket, RKFObject.RKFType.RelTime);

	if(version == 1) {
	addAttribute("Supplement status", 2, specialTicket);
	addAttribute("Supplement type", 6, specialTicket);
	addAttribute("Supplement origin AID", 12, specialTicket, RKFObject.RKFType.AID);
	addAttribute("Supplement origin place", 14, specialTicket);
	addAttribute("Supplement distance", 12, specialTicket);
	addAttribute("Latest control AID", 12, specialTicket, RKFObject.RKFType.AID);
	addAttribute("Latest control place", 14, specialTicket);
	addAttribute("Latest control time", 10, specialTicket, RKFObject.RKFType.RelTime);
	}
    }

    // TCPU: Purse 0x85
    private void parsePurse(int n) {
	if(null != purse)  // skip any extra purse found (ugly fix for rejsekort with backup purse)
	    return;
	purse = new HashMap<String,RKFObject>();
	dynPurse = new HashMap<String,RKFObject>();
	dynPurseOld = new HashMap<String,RKFObject>();

	pos = n + 8;

	int version = (int)addAttribute("Version", 6, purse);
	addAttribute("AID", 12, purse, RKFObject.RKFType.AID);

	if(version < 6) {
	    addAttribute("Serial Number", 32, purse);
	    addAttribute("Start Date", 14, purse, RKFObject.RKFType.Date);
	}

	int dynField = 1;
	int blockLen = 128;
	if(isRejseKort) {
	    blockLen = 256;
	}

	if(null != dynamicData) { 
	    dynField = dynamicData[n/(48*8)];
	}
	else {
	    pos = n+blockLen;
	    // determine latest by checking the higher TX number
	    int txnr1 = getIntFromPos(16);
	    pos += blockLen;
	    int txnr2 = getIntFromPos(16);
	    if(txnr2 > txnr1) {
		dynField = 2;
	    }
	}
	pos = n+blockLen;
	// skip to dynamic data
	if(dynField == 2) {
	    // Latest data in second field
	    parseDynPurse(dynPurseOld, version);
	    pos = n+(blockLen*2);
	    parseDynPurse(dynPurse, version);
	}
	else {
	    parseDynPurse(dynPurse, version);
	    pos = n+(blockLen*2);
	    parseDynPurse(dynPurseOld, version);
	}
    }
   
    private void parseDynPurse(final Map<String,RKFObject> m, int version) {
	addAttribute("Transaction Number", 16, m);
	if(version < 6) {
	    addAttribute("Purse Expiry Date", 14, m, RKFObject.RKFType.Date);
	}
	addAttribute("Value", 24, m, RKFObject.RKFType.Amount);
	if(version == 2) {
	    addAttribute("Status", 8, m, RKFObject.RKFType.Status);
	    addAttribute("Deposit", 20, m, RKFObject.RKFType.Amount);
	}
    }

    private long addAttribute(final String name, int length, 
			      final Map<String,RKFObject> m) {
	long l = getLongFromPos(length);
	
	if(m.containsKey(name)) {
	    m.put(name, new RKFObject(l, m.get(name)));
	}
	else {
	    m.put(name, new RKFObject(l));
	}
	pos += length;
	debug(name+": "+l+" (0x"+Long.toHexString(l)+")");
	return l;
    }

    private long skipPos(int length) {
	long l = getLongFromPos(length);
	debug("Skipping "+length+" bits with value "+l+" (0x"+Long.toHexString(l)+")");
	pos += length;
	return l;
    }

    private long addAttribute(final String name, int length, 
			      final Map<String,RKFObject> m, RKFObject.RKFType type) {
	Calendar d;
	long l = getLongFromPos(length);

	switch(type) {
	case AID:
	    debug(name+": "+getVendor((int)l)+" (0x"+Long.toHexString(l)+")");
	    break;
	case Amount:
	    // MoneyAmount can be either of length 20 or 24 where the latter
	    // can also be negative. 
	    // This will fix negative values if length is 24.
	    if((24 == length) && (0x800000 == (l & 0x800000))) {
		l = (l ^ 0xFFFFFFFFFF000000L);
	    }
	    debug(name+": "+getAmount((int)l)+" (0x"+Long.toHexString(l & 0x0000000000FFFFFFL)+")");
	    break;
	case CurrencyUnit:
	    debug(name+": "+ getCurrency((int)l) + " " + getUnit((int)l)+" (0x"+Long.toHexString(l)+")");
	    break;
	case Date:
	    d=getDateFromInt((int)l);
	    debug(name+": "+getStringFromDate(d)+" (0x"+Long.toHexString(l)+")");
	    break;
	case DateTime:
	    d=getDateTimeFromInt((int)l);
	    debug(name+": "+getStringFromDateTime(d)+" (0x"+Long.toHexString(l)+")");
	    break;
	case PassSubGroup:
	    debug(name+": "+ (l&0xFF) + " passengers of type "+ getPassengerType((int)(l>>8))+ " (0x"+Long.toHexString(l)+")");
	    break;
	case RelTime:
	    debug(name+": "+getRelStringFromDateTime(baseTime, (int)l)+" (0x"+Long.toHexString(l)+")");
	    break;
	case Status:
	    debug(name+": "+getStatus((int)l)+" (0x"+Long.toHexString(l)+")");
	    break;
	case Time:
	    debug(name+": "+getTimeFromInt((int)l)+" (0x"+Long.toHexString(l)+")");
	    break;
	case TCDIAID:
	    debug(name+": "+getTCDIAID((int)l)+" (0x"+Long.toHexString(l)+")");
	    break;
	case ValidationModel:
	    debug(name+": "+getValModel((int)l)+" (0x"+Long.toHexString(l)+")");
	    break;
	case ValidationStatus:
	    debug(name+": "+getValStatus((int)l)+" (0x"+Long.toHexString(l)+")");
	    break;
	default:
	    debug(name+": "+l+" (0x"+Long.toHexString(l)+")");
	    break;
	}

	if(m.containsKey(name)) {
	    m.put(name, new RKFObject(l, type, m.get(name)));
	}
	else {
	    m.put(name, new RKFObject(l, type));
	}
	pos += length;

	return l;
    }

    public static String getTimeFromInt(int i) {
	int h, m, s;
	//debug(i);
	s = (i & 0x1F)*2;
	m = (i >> 5) & 0x3F;
	h = (i >> 11) & 0x1F;
	DecimalFormat d = new DecimalFormat("00");
	return d.format(h)+":"+d.format(m)+":"+d.format(s);
    }

    public static Calendar getDateFromInt(int i) {
	Calendar base = new GregorianCalendar(1997,Calendar.JANUARY,1,0,0,0);
	base.add(Calendar.DATE, i);
	return base;
    }

    public static Calendar getDateTimeFromInt(int i) {
	Calendar base = new GregorianCalendar(2000,Calendar.JANUARY,1,0,0,0);
	base.add(Calendar.MINUTE, i);
	return base;
    }
    

    public static String getStringFromDate(final Calendar date) {
	DecimalFormat d = new DecimalFormat("00");
	return d.format(date.get(Calendar.YEAR))+"/"+d.format(date.get(Calendar.MONTH)+1)+"/"+d.format(date.get(Calendar.DAY_OF_MONTH));
    }

    public static String getStringFromDateTime(final Calendar date) {
	DecimalFormat d = new DecimalFormat("00");
	return d.format(date.get(Calendar.YEAR))+"/"+d.format(date.get(Calendar.MONTH)+1)+"/"+
	    d.format(date.get(Calendar.DAY_OF_MONTH))+" "+d.format(date.get(Calendar.HOUR_OF_DAY))+":"+d.format(date.get(Calendar.MINUTE));
    }

    public static String getRelStringFromDateTime(final Calendar date, int m) {
	DecimalFormat d = new DecimalFormat("00");
	Calendar rdate = (Calendar)date.clone();
	rdate.add(Calendar.MINUTE, m);
	return d.format(rdate.get(Calendar.YEAR))+"/"+d.format(rdate.get(Calendar.MONTH)+1)+"/"+
	    d.format(rdate.get(Calendar.DAY_OF_MONTH))+" "+d.format(rdate.get(Calendar.HOUR_OF_DAY))+":"+d.format(rdate.get(Calendar.MINUTE));
    }

    public static String getPassengerType(int i) {
	switch (i) {
	case 0:
	    return "Unspecified";
	case 1:
	    return "Adult";
	case 2:
	    return "Child";
	case 3:
	    return "Student";
	case 4:
	    return "Old age pensioner";
	default:
	    return "Unknown - PTA specific";
	}
    }

    private void setCurrencyUnit(int i) {
	switch (i & 0xF000) {
	case 0x0000:
	    unit = 1;
	    break;
	case 0x1000:
	    unit = 10;
	    break;
	case 0x2000:
	    unit = 100;
	    break;
	case 0x9000:
	    unit = 2;
	    break;
	default:
	    unit = 1;
	}

	currency = getCurrency(i);
    }

    public static String getCurrency(int i) {
	// ISO 4217 currency codes in hexadecimal
	switch (i & 0xFFF) {
	case 0x036:
	    return "AUD";
	case 0x124:
	    return "CAD";
	case 0x156:
	    return "CNY";
	case 0x208:
	    return "DKK";
	case 0x348:
	    return "HUF";
	case 0x352:
	    return "ISK";
	case 0x356:
	    return "INR";
	case 0x392:
	    return "JPY";
	case 0x578:
	    return "NOK";
	case 0x643:
	    return "RUB";
	case 0x752:
	    return "SEK";
	case 0x756:
	    return "CHF";
	case 0x826:
	    return "GBP";
	case 0x840:
	    return "USD";
	case 0x951:
	    return "XCD";
	case 0x959:
	    return "Gold";
	case 0x978:
	    return "EUR";
	default:
	    return "Unknown";
	}
    }

    public static String getUnit(int i) {
	switch (i & 0xF000) {
	case 0x0000:
	    return "Main unit";
	case 0x1000:
	    return "Minor unit, 1/10 of main unit";
	case 0x2000:
	    return "Minor unit, 1/100 of main unit";
	case 0x9000:
	    return "Minor unit, 1/2 of main unit";
	default:
	    return "Unknown unit";
	}
    }

    public static String getStatus(int i) {
	switch (i) {
	case 0x01:
	    return "OK";
	case 0x21:
	    return "Disabled/Suspended but action pending";
	case 0x3F:
	    return "Temporarily disabled/suspended";
	case 0x58:
	    return "Not OK, disabled/suspended";
	default:
	    return "Unknown";
	}
    }

    public static String getValModel(int i) {
	switch (i) {
	case 0:
	    return "Undefined";
	case 1:
	    return "Check-in/Check-out)";
	case 2:
	    return "Destination specified at check-in";
	case 3:
	    return "RFU";
	default:
	    return "Invalid model";
	}
    }

    public static String getValStatus(int i) {
	switch (i) {
	case 0:
	    return "Undefined";
	case 1:
	    return "Open (after check-in)";
	case 2:
	    return "Closed (after check-out)";
	case 3:
	    return "RFU";
	default:
	    return "Invalid status";
	}
    }

    private int getIntFromPos(int length) {
	return (int)getLongFromPos(length);
    }

   private long getLongFromPos(int length) {
	int sByte, eByte, cByte, sBit, eBits, mask;
	long r;
	r = 0;
	sByte = pos/8;          // get start byte
	eByte = (pos+length)/8; // get end byte
	cByte = sByte;

	sBit = pos % 8;             // Find what bit to start on
	eBits = (pos + length) % 8; // number of bits to read for last byte


	// process first byte
	mask = (0xFF << sBit) & 0xFF;
	if(sByte == eByte) {
	    mask = (0xFF >> 8-eBits) & mask;
	    return (bytes[sByte] & mask) >> sBit;
	}
	r = (bytes[sByte] & mask) >> sBit;


	// build up long from whole bytes
	int bPos = 1;
	for(int i = sByte+1; i < eByte; i++) {
	    r = ((long)(bytes[i] & 0xFF) << ((bPos*8)-sBit)) | r;
	    bPos++;
	}

	// add last bits
	if(0 == eBits)    // if there is zero bits left to read we are done
	    return r;
	else
	    mask = (0xFF >> (8-eBits));
	r = ((long)(bytes[eByte] & mask) << ((bPos*8)-sBit)) | r;

	return r;
    }

    public String getAmount(int i) {
	double d = ((double)i/unit);
	return new DecimalFormat("0.00").format(d) + " " + currency;
    }

    public static String getTCDIAID(int i) {
	switch(i) {
	case 0x00:
	    return "Sector free";
	case 0x01:
	    return "Sector defective";
	case 0x02:
	    return "Sector reserved";
	case 0x05:
	    return "Application Status (TCAS)";
	case 0x06:
	    return "Directory (TCDI)";
	case 0x0A:
	    return "Event Log (TCEL)";
	case 0x0B:
	    return "Purse (TCPU)";
	case 0x0C:
	    return "PTA-specific area for Purse (TCPU)";
	default:
	    return getVendor(i);
	}
    }

    public static String getVendor(int i) {
	switch (i) {
	case 0x64:
	    return "Stockholms Läns Landsting";
	case 0x65:
	    return "SL - Storstockholms Lokaltrafik AB";
	case 0x66:
	    return "SL Flygbussar";
	case 0x67:
	    return "WaxHolms Ångfartygs AB";
	case 0x6E:
	    return "Länstrafiken i Västerbotten AB";
	case 0x6F:
	    return "Umeå Lokaltrafik AB";
	case 0x78:
	    return "Länstrafiken i Norrbotten AB";
	case 0x79:
	    return "Luleå Lokaltrafik AB";
	case 0x82:
	    return "Upplands Lokaltrafik AB";
	case 0x83:
	    return "Uppsalabuss AB";
	case 0x8C:
	    return "LTS - Länstrafiken Sörmland AB";
	case 0x96:
	    return "Östgötatrafiken AB";
	case 0x97:
	    return "Norrköpings Kommun";
	case 0xA0:
	    return "Jönköpings Länstrafik AB";
	case 0xAA:
	    return "Länstrafiken Kronoberg";
	case 0xB4:
	    return "Kalmar Läns Trafik AB";
	case 0xBE:
	    return "Gotlands Kommun, Kollektivtrafiken";
	case 0xBF:
	    return "Destination Gotland";
	case 0xC8:
	    return "Blekingetrafiken";
	case 0xDD:
	    return "Helsingborgs Kommun";
	case 0xDE:
	    return "Lunds kommun";
	case 0xE0:
	    return "Skånetrafiken";
	case 0xE6:
	    return "Hallandstrafiken AB";
	case 0xF0:
	    return "Västtrafik";
	case 0x10E:
	    return "Värmlandstrafik AB";
	case 0x10F:
	    return "Karlstads kommun";
	case 0x118:
	    return "LTÖ - Länstrafiken Örebro AB";
	case 0x122:
	    return "Västmanlands Lokaltrafik AB";
	case 0x12C:
	    return "Dalatrafik, AB";
	case 0x136:
	    return "X-Trafik AB";
	case 0x137:
	    return "Gävle Kommun";
	case 0x140:
	    return "Västernorrlands läns Trafik AB";
	case 0x14A:
	    return "Länstrafiken i Jämtlands Län AB";
	case 0x1F4:
	    return "SJ";
	case 0x1F5:
	    return "TIM - Trafik i Mälardalen";
	case 0x3E9:
	    return "AS Oslo Sporveier";
	case 0x3EA:
	    return "Norges Statsbaner";
	case 0x3EB:
	    return "SL - Sotr-Oslo Lokaltrafikk A.S.";
	case 0x7D0:
	    return "Rejsekort A/S";
	case 0x7D1:
	    return "HUR - Hovedstadens Udviklingsråd";
	case 0x7D2:
	    return "DSB";
	case 0x7D3:
	    return "ØSS/Metro";
	case 0x7D4:
	    return "STS";
    	case 0x7D5:
	    return "VT";
	default:
	    return "Unknown";
	}
    }

    private String posToHuman(int i) {
	int s = (i/(48*8));
	int b = ((i%(48*8))/128);
	int bp= ((i%(48*8))%128);
	return "Pos: "+i+", Sector: "+s+", Block: "+b+", Bit position: "+bp;
    }

    private void searchForVal(int start, int end, int length, int value) {
	int save = pos;
	pos = start;
	for(; (pos+length)<end; pos+=1) {
	    if(getIntFromPos(length) == value)
		System.out.println("!found value "+value+" (+"+Integer.toHexString(value)+") at position "+posToHuman(pos));
	}
	pos = save;
    }

    private void debug(final String s) {
	debugString = debugString + System.getProperty("line.separator") + s;
    }

    public String getDebug() { return debugString; }
}
