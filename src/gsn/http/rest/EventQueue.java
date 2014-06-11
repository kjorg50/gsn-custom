/**
* Global Sensor Networks (GSN) Source Code
* Copyright (c) 2006-2014, Ecole Polytechnique Federale de Lausanne (EPFL)
* 
* This file is part of GSN.
* 
* GSN is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
* 
* GSN is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with GSN.  If not, see <http://www.gnu.org/licenses/>.
* 
* File: src/gsn/http/rest/EventQueue.java
*
* The class is used to queue requests, represented as JSONObjects,
* that are coming from the sensor, in this case an Android phone,
* and will be consumed by the wrapper, in this case RemoteEventPushWrapper.
* 
* @author Nevena Golubovic
*
*/

package gsn.http.rest;

import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;

public class EventQueue {

	private static EventQueue singleton = new EventQueue();
	//private static int QUEUE_SIZE = 1000;
	private final static transient Logger logger = Logger.getLogger(EventQueue.class);
	// JSONObjects containing all the data sent from the sensor
	private LinkedBlockingQueue<JSONObject> data = new LinkedBlockingQueue<JSONObject>(100);

	public static EventQueue getInstance(){
		return singleton;
	}

	public boolean isEmpty(){ 
		return data.isEmpty();
	}

	public void addData(JSONObject data){
		try {
			this.data.add(data);
		} catch (IllegalStateException e) {
			logger.error("EventQueue is full.");
			// this also means data is old. clear it
			this.data.clear();
		}		
	}

	public JSONObject removeData() throws NoSuchElementException{
		return data.remove();
	}


}
