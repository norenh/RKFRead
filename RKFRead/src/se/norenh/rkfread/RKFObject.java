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

public class RKFObject {
    public enum RKFType {
	AID,
	Amount,
	CurrencyUnit,
	Date,
	DateTime,
	Long,
	RelTime,
	PassSubGroup,
	Status,
	Time,
	TCDIAID,
	ValidationModel,
	ValidationStatus
    }

    private final RKFType type;
    private final long value;
    public RKFObject next;

    public RKFObject(long l, RKFType t, RKFObject o) {
	value = l;
	type = t;
	next = o;
    }

    public RKFObject(long l, RKFType t) {
	value = l;
	type = t;
	next = null;
    }

    public RKFObject(long l, RKFObject o) {
	value = l;
	type = RKFType.Long;
	next = o;
    }

    public RKFObject(long l) {
	value = l;
	type = RKFType.Long;
	next = null;
    }

    public RKFType getType() { return type; }
    public long getValue() { return value; }
    public boolean hasMore() { return (null != next); }
    public RKFObject getNext() { return next; }
}
