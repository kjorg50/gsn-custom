package gsn.wrappers.general;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;
import gsn.beans.AddressBean;
import gsn.beans.DataField;
import gsn.beans.DataTypes;
import gsn.beans.StreamElement;
import gsn.vsensor.Container;
import gsn.wrappers.AbstractStreamProducer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.TooManyListenersException;
import java.util.TreeMap;

import org.apache.log4j.Logger;

/**
 * Modified to used RXTX (http://users.frii.com/jarvi/rxtx/) which a LGPL
 * replacement for javacomm. The Easiest way to install RXTX is from the binary
 * distribution which is available at
 * http://users.frii.com/jarvi/rxtx/download.html Links GSN to a sensor network
 * through serial port. <p/> The only needed parameter is the serial port
 * address, provided through xml. Default connection settings are 9600 8 N 1 (I
 * had some problems with javax.comm Linux when trying to use non-default
 * settings) TODO parametrize connection settings through xml.
 * 
 * @author Ali Salehi (AliS, ali.salehi-at-epfl.ch)<br>
 * @author Jerome Rousselot CSEM<br>
 */
public class SerialWrapper extends AbstractStreamProducer implements SerialPortEventListener {
   
   private static final String    RAW_PACKET    = "RAW_PACKET";
   
   private final transient Logger logger        = Logger.getLogger( SerialWrapper.class );
   
   private boolean                isNew         = false;
   
   private SerialConnection       wnetPort;
   
   private int                    threadCounter = 0;
   
   private static int             MAXBUFFERSIZE = 1024;
   
   // private StringBuffer inputBuffer = new StringBuffer();
   private byte [ ]               inputBuffer;
   
   public InputStream             is;
   
   private AddressBean            addressBean;
   
   private String                 serialPort;
   
   /*
    * @(#)SerialConnectionException.java 1.3 98/06/04 SMI
    * @(#)SerialConnection.java 1.6 98/07/17 SMI Copyright (c) 1998 Sun
    * Microsystems, Inc. All Rights Reserved. Sun grants you ("Licensee") a
    * non-exclusive, royalty free, license to use, modify and redistribute this
    * software in source and binary code form, provided that i) this copyright
    * notice and license appear on all copies of the software; and ii) Licensee
    * does not utilize the software in a manner which is disparaging to Sun.
    * This software is provided "AS IS," without a warranty of any kind. ALL
    * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
    * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
    * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN AND ITS LICENSORS SHALL NOT
    * BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING,
    * MODIFYING OR DISTRIBUTING THE SOFTWARE OR ITS DERIVATIVES. IN NO EVENT
    * WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA,
    * OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE
    * DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING
    * OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN
    * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES. This software is not designed
    * or intended for use in on-line control of aircraft, air traffic, aircraft
    * navigation or aircraft communications; or in the design, construction,
    * operation or maintenance of any nuclear facility. Licensee represents and
    * warrants that it will not use or redistribute the Software for such
    * purposes.
    */

   public class SerialConnectionException extends Exception {
      
      /**
       * Constructs a <code>SerialConnectionException</code> with the
       * specified detail message.
       * 
       * @param str the detail message.
       */
      public SerialConnectionException ( String str ) {
         super( str );
      }
      
      /**
       * Constructs a <code>SerialConnectionException</code> with no detail
       * message.
       */
      public SerialConnectionException ( ) {
         super( );
      }
   }
   
   /**
    * A class that handles the details of the serial connection.
    */
   
   public class SerialConnection {
      
      // private SerialParameters parameters;
      
      protected OutputStream     os;
      
      protected InputStream      is;
      
      private CommPortIdentifier portId;
      
      public SerialPort          sPort;
      
      private String             serialPort;
      
      private boolean            open;
      
      /**
       * Creates a SerialConnection object and initialiazes variables passed in
       * as params.
       * 
       * @param serialPort A SerialParameters object.
       */
      public SerialConnection ( String serialPort ) {
         open = false;
         this.serialPort = serialPort;
      }
      
      /**
       * Attempts to open a serial connection (9600 8N1). If it is unsuccesfull
       * at any step it returns the port to a closed state, throws a
       * <code>SerialConnectionException</code>, and returns. <p/> Gives a
       * timeout of 30 seconds on the portOpen to allow other applications to
       * reliquish the port if have it open and no longer need it.
       */
      public void openConnection ( ) throws SerialConnectionException {
         // parameters = new SerialParameters("/dev/ttyS0", 9600, 0, 0,
         // 8, 1,
         // 1);
         // Obtain a CommPortIdentifier object for the port you want to
         // open.
         try {
            portId = CommPortIdentifier.getPortIdentifier( serialPort );
         } catch ( NoSuchPortException e ) {
            throw new SerialConnectionException( e.getMessage( ) );
         }
         
         // Open the port represented by the CommPortIdentifier object.
         // Give
         // the open call a relatively long timeout of 30 seconds to
         // allow
         // a different application to reliquish the port if the user
         // wants to.
         if ( portId.isCurrentlyOwned( ) ) System.out.println( "port owned by so else" );
         try {
            sPort = ( SerialPort ) portId.open( "GSNSerialConnection" , 30000 );
         } catch ( PortInUseException e ) {
            throw new SerialConnectionException( e.getMessage( ) );
         }
         
         // Open the input and output streams for the connection. If they
         // won't
         // open, close the port before throwing an exception.
         try {
            os = sPort.getOutputStream( );
            is = sPort.getInputStream( );
         } catch ( IOException e ) {
            sPort.close( );
            throw new SerialConnectionException( "Error opening i/o streams" );
         }
         sPort.notifyOnDataAvailable( true );
         sPort.notifyOnBreakInterrupt( false );
         
         // Set receive timeout to allow breaking out of polling loop
         // during
         // input handling.
         try {
            sPort.enableReceiveTimeout( 30 );
         } catch ( UnsupportedCommOperationException e ) {}
         
         open = true;
      }
      
      /**
       * Close the port and clean up associated elements.
       */
      public void closeConnection ( ) {
         // If port is alread closed just return.
         if ( !open ) { return; }
         // Check to make sure sPort has reference to avoid a NPE.
         if ( sPort != null ) {
            try {
               os.close( );
               is.close( );
            } catch ( IOException e ) {
               System.err.println( e );
            }
            sPort.close( );
         }
         open = false;
      }
      
      /**
       * Send a one second break signal.
       */
      public void sendBreak ( ) {
         sPort.sendBreak( 1000 );
      }
      
      /**
       * Reports the open status of the port.
       * 
       * @return true if port is open, false if port is closed.
       */
      public boolean isOpen ( ) {
         return open;
      }
      
      public void addEventListener ( SerialPortEventListener listener ) throws SerialConnectionException {
         try {
            sPort.addEventListener( listener );
            
         } catch ( TooManyListenersException e ) {
            sPort.close( );
            throw new SerialConnectionException( "too many listeners added" );
         }
      }
      
      /**
       * Send a byte.
       */
      public void sendByte ( int i ) {
         try {
            os.write( i );
         } catch ( IOException e ) {
            System.err.println( "OutputStream write error: " + e );
         }
      }
      
      public InputStream getInputStream ( ) {
         return is;
      }
      
      public OutputStream getOutputStream ( ) {
         return os;
      }
      
      /**
       * Send a string.
       */
      public void sendString ( String s ) {
         try {
            os.write( s.getBytes( ) );
         } catch ( IOException e ) {
            System.err.println( "OutputStream write error: " + e );
         }
      }
   }
   
   /*
    * Needs the following information from XML file : serialport : the name of
    * the serial port (/dev/ttyS0...)
    */
   public boolean initialize ( TreeMap context ) {
      if ( !super.initialize( context ) ) return false;
      setName( "WNetSerialWrapper-Thread" + ( ++threadCounter ) );
      addressBean = ( AddressBean ) context.get( Container.STREAM_SOURCE_ACTIVE_ADDRESS_BEAN );
      serialPort = addressBean.getPredicateValue( "serialport" );
      // TASK : TRYING TO CONNECT USING THE ADDRESS
      wnetPort = new SerialConnection( serialPort );
      try {
         wnetPort.openConnection( );
         if ( wnetPort.isOpen( ) ) {
            wnetPort.addEventListener( this );
            is = wnetPort.getInputStream( );
            if ( logger.isDebugEnabled( ) ) logger.debug( "Serial port wrapper successfully opened port and registered itself as listener." );
            
         }
         
      } catch ( SerialConnectionException e ) {
         System.err.println( "Serial Port Connection Exception : " + e.getMessage( ) );
         logger.warn( "Serial port wrapper couldn't connect to serial port : " + e.getMessage( ) );
         return false;
      }
      inputBuffer = new byte [ MAXBUFFERSIZE ];
      return true;
   }
   
   public void run ( ) {
      while ( isActive( ) ) {
         if ( listeners.isEmpty( ) || !isNew ) {
            continue;
         }
         StreamElement streamElement = new StreamElement( new String [ ] { RAW_PACKET } , new Integer [ ] { DataTypes.BINARY } , new Serializable [ ] { inputBuffer } , System.currentTimeMillis( ) );
         publishData( streamElement );
         isNew = false;
      }
   }
   
   public Collection < DataField > getProducedStreamStructure ( ) {
      ArrayList < DataField > dataField = new ArrayList < DataField >( );
      dataField.add( new DataField( RAW_PACKET , "BINARY" , "The packet contains raw data from a sensor network." ) );
      return dataField;
   }
   
   public void finalize ( HashMap context ) {
      super.finalize( context );
      threadCounter--;
   }
   
   public void serialEvent ( SerialPortEvent e ) {
      
      if ( logger.isDebugEnabled( ) ) logger.debug( "Serial wrapper received a serial port event, reading..." );
      // Determine type of event.
      switch ( e.getEventType( ) ) {
         
         // Read data until -1 is returned.
         case SerialPortEvent.DATA_AVAILABLE :
            /*
             * int index = 0; while (newData != -1) { try { if (is == null) { if
             * (logger.isDebugEnabled ()) logger.debug("SerialWrapper: Warning,
             * is == null !"); is = wnetPort.getInputStream(); } else newData =
             * is.read(); if (newData > -1 && newData < 256) {
             * inputBuffer[index++] = (byte) newData; } } catch (IOException ex) {
             * System.err.println(ex); return; } }
             */
            try {
               is.read( inputBuffer );
            } catch ( IOException ex ) {
               logger.warn( "Serial port wrapper couldn't read data : " + ex );
               return;
            }
            isNew = true;
            break;
         
         // If break event append BREAK RECEIVED message.
         case SerialPortEvent.BI :
            // messageAreaIn.append("\n--- BREAK RECEIVED ---\n");
      }
      if ( logger.isDebugEnabled( ) ) logger.debug( "Serial port wrapper processed a serial port event, stringbuffer is now : " + new String( inputBuffer ) );
   }
   
}
