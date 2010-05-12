package gsn.vsensor;

import gsn.beans.DataTypes;
import gsn.beans.StreamElement;
import gsn.utils.Helpers;
import gsn.utils.models.ModelFitting;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.TreeMap;

import static gsn.utils.Helpers.convertTimeFromLongToIso;
import static gsn.utils.models.ModelFitting.getModelIdFromString;


public class DataCleanVirtualSensor extends AbstractVirtualSensor {

    private static final transient Logger logger = Logger.getLogger(BridgeVirtualSensor.class);

    private static final String OPERATOR = "_operator";
    private static final String DEPLOYMENT = "_deployment";
    private static final String STATION = "_station";
    private static final String SENSOR = "_sensor";
    private static final String FROM = "_from";
    private static final String TO = "_to";
    private static final String DIRTINESS = "_dirtiness";

    private static final String xml_template = "<metadata>\n" +
            "\t<deployement>"+DEPLOYMENT+"</deployment>\n" +
            "\t<operator>"+OPERATOR+"</operator>\n" +
            "\t<station>"+STATION+"</station>\n" +
            "\t<sensor>"+SENSOR+"</sensor>\n" +
            "\t<from>"+FROM+"</from>\n" +
            "\t<to>"+TO+"</to>\n" +
            "\t<dirtiness>"+DIRTINESS+"</dirtiness>\n" +
            "</metadata>";

    private static final String PARAM_MODEL = "model";
    private static final String PARAM_ERROR_BOUND = "error_bound";
    private static final String PARAM_WINDOW_SIZE = "window_size";

    // optional parameters
    private static final String PARAM_METADATA_SERVER = "metadata_server_url"; // metadata server for posting metadata, e.g. http://www.example.com/dataclean.php

    private static final String PARAM_METADATA_USERNAME = "user"; // username for metadata server
    private static final String PARAM_METADATA_PASSWORD = "password"; // password for metadata server

    private static final String PARAM_METADATA_OPERATOR = "operator"; // operator for metadata server, typically e-mail
    private static final String PARAM_METADATA_DEPLOYEMENT = "deployment"; // name of deployemnt for metadata server
    private static final String PARAM_METADATA_STATION = "station"; // name of station for metadata server
    private static final String PARAM_METADATA_SENSOR = "sensor"; // name of station for metadata server

    private int model = -1;
    private int window_size = 0;
    private double error_bound = 0;

    private double[] stream;
    private long[] timestamps;
    private double[] processed;
    private double[] dirtiness;

    private String metadata_server_url;
    private String username;
    private String password;
    private String operator;
    private String deployement;
    private String station;
    private String sensor;

    private String prepared_xml_request;

    private boolean publish_to_metadata_server = false;
    private boolean metadata_server_requieres_password = false;


    private int bufferCount = 0;


    public boolean initialize() {

        TreeMap<String, String> params = getVirtualSensorConfiguration().getMainClassInitialParams();

        String model_str = params.get(PARAM_MODEL);

        if (model_str == null) {
            logger.warn("Parameter \"" + PARAM_MODEL + "\" not provided in Virtual Sensor file");
            return false;
        } else {
            model = getModelIdFromString(model_str.trim());
            if (model == -1) {
                logger.warn("Parameter \"" + PARAM_MODEL + "\" incorrect in Virtual Sensor file");
                return false;
            }
        }

        String window_size_str = params.get(PARAM_WINDOW_SIZE);

        if (window_size_str == null) {
            logger.warn("Parameter \"" + PARAM_WINDOW_SIZE + "\" not provided in Virtual Sensor file");
            return false;
        } else try {
            window_size = Integer.parseInt(window_size_str.trim());
        } catch (NumberFormatException e) {
            logger.warn("Parameter \"" + PARAM_WINDOW_SIZE + "\" incorrect in Virtual Sensor file");
            return false;
        }

        if (window_size < 0) {
            logger.warn("Window size should always be positive.");
            return false;
        }

        String error_bound_str = params.get(PARAM_ERROR_BOUND);

        if (error_bound_str == null) {
            logger.warn("Parameter \"" + PARAM_ERROR_BOUND + "\" not provided in Virtual Sensor file");
            return false;
        } else try {
            error_bound = Double.parseDouble(error_bound_str.trim());
        } catch (NumberFormatException e) {
            logger.warn("Parameter \"" + PARAM_ERROR_BOUND + "\" incorrect in Virtual Sensor file");
            return false;
        }

        metadata_server_url = params.get(PARAM_METADATA_SERVER);

        if (metadata_server_url != null) {
            publish_to_metadata_server = true;

            username = params.get(PARAM_METADATA_USERNAME);
            password = params.get(PARAM_METADATA_PASSWORD);

            if (username != null && password != null)
                metadata_server_requieres_password = true;

            operator = params.get(PARAM_METADATA_OPERATOR);
            deployement = params.get(PARAM_METADATA_DEPLOYEMENT);
            station = params.get(PARAM_METADATA_STATION);
            sensor = params.get(PARAM_METADATA_SENSOR);

            if ((operator == null) || (deployement == null) || (station == null) || (sensor == null)) {
                logger.warn("A parameter required for publishing metadata is missing. Couldn't publish to metadata server.");
                publish_to_metadata_server = false;
            } else {
                prepared_xml_request = xml_template.replaceAll(OPERATOR, operator);
                prepared_xml_request = prepared_xml_request.replaceAll(DEPLOYMENT, deployement);
                prepared_xml_request = prepared_xml_request.replaceAll(STATION, station);
                prepared_xml_request = prepared_xml_request.replaceAll(SENSOR, sensor);
            }

        }

        stream = new double[window_size];
        timestamps = new long[window_size];
        processed = new double[window_size];
        dirtiness = new double[window_size];

        return true;
    }

    public void dataAvailable(String inputStreamName, StreamElement data) {

        if (bufferCount < window_size) {
            timestamps[bufferCount] = data.getTimeStamp();
            stream[bufferCount] = (Double) data.getData()[0];
            bufferCount++;
        } else {
            ModelFitting.FitAndMarkDirty(model, error_bound, window_size, stream, timestamps, processed, dirtiness);

            for (int j = 0; j < processed.length; j++) {
                StreamElement se = new StreamElement(new String[]{"stream", "processed", "dirtiness"},
                        new Byte[]{DataTypes.DOUBLE, DataTypes.DOUBLE, DataTypes.DOUBLE},
                        new Serializable[]{stream[j], processed[j], dirtiness[j]},
                        timestamps[j]);
                dataProduced(se);
                if ((dirtiness[j] > 0) && publish_to_metadata_server) {
                    try {
                        //postToWiki(metadata_server_url);// + "?request=" + outputAsXML(stream[j], processed[j], dirtiness[j], timestamps[j], timestamps[j]));
                        System.out.println("?request=" + outputAsXML(stream[j], processed[j], dirtiness[j], timestamps[j], timestamps[j]));
                    } catch (Exception e) {
                        logger.warn("Error while trying to post to metadata server. "+e.getMessage()+e);
                    }
                }
            }
            bufferCount = 0;
        }
    }


    public String postToWiki(String url) {

        String httpAddress = url;

        DefaultHttpClient client = new DefaultHttpClient();


        if (metadata_server_requieres_password) {
            client.getCredentialsProvider().setCredentials(
                    new AuthScope(metadata_server_url, 80),
                    new UsernamePasswordCredentials(username, password)
            );
        }

        logger.warn("Querying server: " + httpAddress);
        HttpGet get = new HttpGet(httpAddress);

        try {
            // execute the GET, getting string directly
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            String responseBody = client.execute(get, responseHandler);

        }
        catch (HttpResponseException e) {
            logger.warn(new StringBuilder("HTTP 401 Authentication Needed for : ")
                    .append(httpAddress));
        }

        catch (UnknownHostException e) {
            logger.warn(new StringBuilder("Unknown host: ")
                    .append(httpAddress));
        }

        catch (ConnectException e) {
            logger.warn(new StringBuilder("Connection refused to host: ")
                    .append(httpAddress));
        }
        catch (ClientProtocolException e) {
            logger.warn(new StringBuilder("Error for: ")
                    .append(httpAddress).append(e));

        } catch (IOException e) {
            logger.warn(new StringBuilder("Error for: ")
                    .append(httpAddress).append(e));
        } finally {
            // release any connection resources used by the method
            client.getConnectionManager().shutdown();
        }
        return null;
    }

    /*
   * Returns an xml string from the given parameters
   * */
    public String outputAsXML(double stream, double processed, double dirtiness, long from_date, long to_date) throws Exception {
        String output = prepared_xml_request.replaceAll(DIRTINESS, Double.toString(dirtiness));
        output = output.replaceAll(FROM, Helpers.convertTimeFromLongToIso(from_date,"yyyy-MM-dd'T'HH:mm:ss.SSSZZ"));
        output = output.replaceAll(TO, Helpers.convertTimeFromLongToIso(to_date,"yyyy-MM-dd'T'HH:mm:ss.SSSZZ"));

        return output;
    }

    public void dispose() {

    }


}