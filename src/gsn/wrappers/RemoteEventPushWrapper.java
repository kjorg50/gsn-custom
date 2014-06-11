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
* File: src/gsn/wrappers/RemoteEventPushWrapper.java
*
* The class is a wrapper for the Android phone.
* 
* @author Nevena Golubovic
*
*/

package gsn.wrappers;

import gsn.beans.AddressBean;
import gsn.beans.DataField;
import gsn.beans.DataTypes;
import gsn.beans.StreamElement;
import gsn.http.rest.EventQueue;
import gsn.utils.ParamParser;

import java.io.Serializable;
import java.util.NoSuchElementException;

import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class RemoteEventPushWrapper extends AbstractWrapper{

	private static final int            DEFAULT_SAMPLING_RATE       = 1000;
	private int                         samplingRate                = DEFAULT_SAMPLING_RATE;
	private final transient Logger      logger                      = Logger.getLogger(RemoteEventPushWrapper.class);
	private static int                  threadCounter               = 0;
	private final static String 		ACCELEROMETER_X				= "Accelerometer_x";
	private final static String 		ACCELEROMETER_Y				= "Accelerometer_y";
	private final static String 		ACCELEROMETER_Z				= "Accelerometer_z";
	private final static String 		PRESSURE_X					= "Pressure_x";
	private final static String 		LIGHT_X						= "Light_x";
	private transient DataField[]       outputStructureCache        = new DataField[]{
						new DataField(ACCELEROMETER_X, "double", "Accelerometer coordinate x"),
						new DataField(ACCELEROMETER_Y, "double", "Accelerometer coordinate y"),
						new DataField(ACCELEROMETER_Z, "double", "Accelerometer coordinate z"),
						new DataField(PRESSURE_X, "double", "Pressure coordinate x"),
						new DataField(LIGHT_X, "double", "Light coordinate x"),};

	@Override
	public DataField[] getOutputFormat() {
		return outputStructureCache;
	}

	@Override
	public boolean initialize() {		
		setName("RemoteEventPushWrapper-Thread" + (++threadCounter));
		AddressBean addressBean = getActiveAddressBean();
		if(addressBean.getPredicateValue("sampling-rate") != null){
			samplingRate = ParamParser.getInteger(addressBean.getPredicateValue("sampling-rate"), DEFAULT_SAMPLING_RATE);
		}
		if(samplingRate <= 0){
			logger.warn("Sampling rate for RemoteEvent should be greather than 0.");
			samplingRate = DEFAULT_SAMPLING_RATE;
		}
		return true;
	}

	public void run(){
		JSONObject data = new JSONObject();
		while(isActive()){
            try{
                Thread.sleep(samplingRate);            	
            }catch (InterruptedException e){
                logger.error(e.getMessage(), e);
            }
            
            if(EventQueue.getInstance().isEmpty()){
            	 logger.error("New data from the sensor is missing.");
            }else{
            	try {
            		data = EventQueue.getInstance().removeData();
                	StreamElement streamElement = jsonToStreamElemet(data);
					// TODO change to debug
                	logger.error("*** not an error *** data: " + data);
                	logger.error("streamElement: " + streamElement.toString());
                    postStreamElement(streamElement);
				} catch (NoSuchElementException e) {
					logger.error("EventQueue is empty.");
				}            	
            }            
        }
	}

	private StreamElement jsonToStreamElemet(JSONObject jo) {
		final String[] field_names; 
		final Byte[] field_bytes;
		final Serializable[] field_values;
		try {
			JSONArray fields = jo.getJSONArray("fields");
			field_names = new String[fields.length()];
			field_bytes = new Byte[fields.length()];
			field_values = new Double[fields.length()];
			for (int i = 0; i < fields.length(); i++){
				JSONObject field = fields.getJSONObject(i);
				field_names[i] = field.get("name").toString();
				field_bytes[i] = DataTypes.DOUBLE;
				field_values[i] = (Double)field.get("value");				
			}

			return new StreamElement(field_names, field_bytes, field_values, System.currentTimeMillis());

		} catch (JSONException e) {
			logger.error("could not parse JSONObject " + jo.toString());
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void dispose() {
		threadCounter--;		
	}

	@Override
	public String getWrapperName() {
		return "Remote Event Push Wrapper";
	}

}
